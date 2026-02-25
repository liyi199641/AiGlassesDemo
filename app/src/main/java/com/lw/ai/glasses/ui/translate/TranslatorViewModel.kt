package com.lw.ai.glasses.ui.translate

import BaseViewModel
import android.content.Context
import androidx.lifecycle.viewModelScope
import com.fission.wear.glasses.sdk.GlassesManage
import com.fission.wear.glasses.sdk.constant.GlassesConstant
import com.fission.wear.glasses.sdk.events.AiTranslationEvent
import com.fission.wear.glasses.sdk.events.AudioStateEvent
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lw.top.lib_core.data.local.entity.TranslationMessageEntity
import com.lw.top.lib_core.data.local.entity.TranslationSessionEntity
import com.lw.top.lib_core.data.repository.TranslationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.log10
import kotlin.math.sqrt


@HiltViewModel
class TranslatorViewModel @Inject constructor(
    private val repository: TranslationRepository,
    @ApplicationContext private val context: Context,
) : BaseViewModel() {

    private val _uiState = MutableStateFlow(TranslatorUiState())
    val uiState = _uiState.asStateFlow()
    private val streamRecorder = StreamAudioRecorder(context)
    private var mediaPlayer: android.media.MediaPlayer? = null

    init {
        loadLanguages()

        viewModelScope.launch {
            // 订阅所有会话及消息
            repository.getAllSessionsWithMessagesFlow().collect { sessionsWithMessages ->
                _uiState.update { state ->
                    state.copy(history = sessionsWithMessages)
                }
            }
        }

        viewModelScope.launch {
            GlassesManage.eventFlow().collect { events ->
                when (events) {
                    AudioStateEvent.StartRecording         -> {//进入录音，对话模式
                        GlassesManage.stopVadAudio()
                    }
                    is AiTranslationEvent.AiTranslationResult -> {
                        val result = events.data
                        val requestId = result.id ?: return@collect
                        val msgId = result.messageId ?: return@collect

                        viewModelScope.launch {
                            // 1. 确保 Session 存在
                            repository.insertSession(
                                TranslationSessionEntity(
                                    requestId = requestId,
                                    sourceLang = uiState.value.srcLang?.name ?: "",
                                    targetLang = uiState.value.targetLang?.name ?: ""
                                )
                            )

                            // 2. 获取现有的消息片段（必须同时匹配 requestId 和 messageId）
                            val existing = repository.getMessageById(requestId, msgId)

                            // 3. 构建新的实体：采用非空覆盖逻辑
                            val newEntity = if (existing != null) {
                                existing.copy(
                                    originalText = result.originalText ?: existing.originalText,
                                    translatedText = result.translatedText ?: existing.translatedText,
                                    audioPath = result.translatedFileUrl ?: existing.audioPath,
                                    isFinished = result.isFinished
                                )
                            } else {
                                TranslationMessageEntity(
                                    messageId = msgId,
                                    requestId = requestId,
                                    originalText = result.originalText ?: "",
                                    translatedText = result.translatedText ?: "",
                                    audioPath = result.translatedFileUrl,
                                    isFinished = result.isFinished
                                )
                            }

                            // 4. 插入或流式更新数据库
                            repository.insertMessage(newEntity)
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    private fun loadLanguages() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val jsonString =
                    context.assets.open("languages.json").bufferedReader().use { it.readText() }
                val listType = object : TypeToken<List<Language>>() {}.type
                val languages: List<Language> = Gson().fromJson(jsonString, listType)

                val defaultSrc = languages.find { it.langType == 140 } ?: languages.firstOrNull()
                val defaultTarget = languages.find { it.langType == 47 } ?: languages.lastOrNull()

                _uiState.update {
                    it.copy(
                        allLanguages = languages,
                        srcLang = defaultSrc,
                        targetLang = defaultTarget
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setSourceLanguage(lang: Language) {
        _uiState.update { it.copy(srcLang = lang) }
    }

    fun setTargetLanguage(lang: Language) {
        _uiState.update { it.copy(targetLang = lang) }
    }

    fun setTranslationMode(mode: TranslationMode) {
        _uiState.update { it.copy(currentMode = mode) }
    }

    fun swapLanguages() {
        _uiState.update {
            it.copy(
                srcLang = it.targetLang,
                targetLang = it.srcLang
            )
        }
    }

    fun startRecording() {
        val fileName = "record_${System.currentTimeMillis()}"
        _uiState.update { it.copy(isRecording = true) }

        viewModelScope.launch {
            val requestId = System.currentTimeMillis() // 发起一个新会话 ID
            
            GlassesManage.startAiTranslation(
                uiState.value.srcLang?.langType!!,
                listOf(uiState.value.targetLang?.langType!!),
                requestId
            )

            val modeStr = when (_uiState.value.currentMode) {
                TranslationMode.DIALOGUE -> GlassesConstant.AI_ASSISTANT_TYPE_LISTEN_MODE_TRANSLATION
                TranslationMode.REAL_TIME -> GlassesConstant.AI_ASSISTANT_TYPE_LISTEN_MODE_SIMULTANEOUS_INTERPRETATION
            }

            GlassesManage.startReceivingAudio(modeStr, 140)
            
            streamRecorder.start(fileName) { pcmData ->
                GlassesManage.sendReceivingAudioData(modeStr, pcmData)
                val amplitude = calculateRMS(pcmData)
                _uiState.update { it.copy(currentAmplitude = amplitude) }
            }
        }
    }

    fun stopRecording() {
        viewModelScope.launch {
            streamRecorder.stop()
            _uiState.update { it.copy(isRecording = false, currentAmplitude = 0f) }
            
            val modeStr = when (_uiState.value.currentMode) {
                TranslationMode.DIALOGUE -> GlassesConstant.AI_ASSISTANT_TYPE_LISTEN_MODE_TRANSLATION
                TranslationMode.REAL_TIME -> GlassesConstant.AI_ASSISTANT_TYPE_LISTEN_MODE_SIMULTANEOUS_INTERPRETATION
            }
            GlassesManage.stopReceivingAudio(modeStr)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearAllTranslations()
        }
    }

    fun playAudio(path: String) {
        try {
            mediaPlayer?.release()
            mediaPlayer = android.media.MediaPlayer().apply {
                setDataSource(path)
                prepare()
                start()
                setOnCompletionListener {
                    it.release()
                    mediaPlayer = null
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun calculateRMS(pcmData: ByteArray): Float {
        if (pcmData.isEmpty()) return 0f
        var sum = 0.0
        for (i in 0 until pcmData.size step 2) {
            val sample = ((pcmData[i + 1].toInt() shl 8) or (pcmData[i].toInt() and 0xFF)).toShort()
            sum += sample * sample
        }
        val mean = sum / (pcmData.size / 2)
        val rms = sqrt(mean)
        val maxAmplitude = 32768.0
        val db = if (rms > 0) 20 * log10(rms / maxAmplitude) else -100.0
        val normalized = ((db + 60) / 60).coerceIn(0.0, 1.0)
        return (normalized * 0.9 + 0.1).toFloat()
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            streamRecorder.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }
}
