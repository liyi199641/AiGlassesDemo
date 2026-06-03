package com.lw.ai.glasses.ui.translate

import com.lw.ai.glasses.ui.base.viewmodel.BaseViewModel
import android.content.Context
import androidx.lifecycle.viewModelScope
import com.blankj.utilcode.util.LogUtils
import com.fission.wear.glasses.sdk.GlassesManage
import com.fission.wear.glasses.sdk.AiAssistantClient
import com.fission.wear.glasses.sdk.constant.GlassesConstant
import com.fission.wear.glasses.sdk.events.AiTranslationEvent
import com.lw.ai.glasses.state.WsConnectionStateManager
import com.fission.wear.glasses.sdk.events.AudioStateEvent
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lw.top.lib_core.data.local.entity.TranslationMessageEntity
import com.lw.top.lib_core.data.local.entity.TranslationSessionEntity
import com.lw.top.lib_core.data.repository.TranslationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.log10
import kotlin.math.sqrt


@HiltViewModel
class TranslatorViewModel @Inject constructor(
    private val repository: TranslationRepository,
    @ApplicationContext private val context: Context,
    private val wsConnectionStateManager: WsConnectionStateManager,
) : BaseViewModel() {

    private val _uiState = MutableStateFlow(TranslatorUiState())
    val uiState = _uiState.asStateFlow()
    private val streamRecorder = StreamAudioRecorder(context)
    private var mediaPlayer: android.media.MediaPlayer? = null
    /** 实时翻译会话是否仍在进行（松手仅暂停，不结束）。 */
    private var realTimeSessionActive = false
    /** 记录 requestId 对应的翻译模式，避免切换 Tab 后写入错误分类。 */
    private val requestIdToMode = mutableMapOf<String, String>()

    init {
        disableOpusStreamPushForTranslation()

        loadLanguages()

        viewModelScope.launch {
            uiState
                .map { it.currentMode.toStorageKey() }
                .distinctUntilChanged()
                .flatMapLatest { mode ->
                    repository.getSessionsWithMessagesByModeFlow(mode)
                }
                .collect { sessionsWithMessages ->
                    _uiState.update { state ->
                        state.copy(history = sessionsWithMessages)
                    }
                }
        }

        observeGlobalWsConnectionState()

        viewModelScope.launch {
            AiAssistantClient.getInstance().aiAgentEventFlow().collect { events ->
                when (events) {
                    AudioStateEvent.StartRecording         -> {//进入录音，对话模式
//                        GlassesManage.stopVadAudio()
                    }
                    is AiTranslationEvent.AiTranslationResult -> {
                        val result = events.data
                        val requestId = result.id ?: return@collect

                        viewModelScope.launch {
                            val translationMode = requestIdToMode[requestId]
                                ?: _uiState.value.currentMode.toStorageKey()
                            val isRealTime =
                                translationMode == TranslationSessionEntity.MODE_REAL_TIME

                            if (!isRealTime && result.messageId.isNullOrBlank()) return@launch

                            // 1. 确保 Session 存在
                            repository.insertSession(
                                TranslationSessionEntity(
                                    requestId = requestId,
                                    sourceLang = uiState.value.srcLang?.name ?: "",
                                    targetLang = uiState.value.targetLang?.name ?: "",
                                    translationMode = translationMode,
                                )
                            )

                            if (isRealTime) {
                                // 实时翻译：仅以 requestId 标识一句话，忽略后台 messageId
                                val segmentMessageId =
                                    TranslationSessionEntity.REAL_TIME_SEGMENT_MESSAGE_ID
                                val existing = mergeRealTimeSegments(
                                    repository.getMessagesByRequestId(requestId),
                                )

                                val newEntity = if (existing != null) {
                                    existing.copy(
                                        messageId = segmentMessageId,
                                        originalText = mergeRealTimeTranslationText(
                                            existing = existing.originalText,
                                            incoming = result.originalText,
                                            incomingIsFinished = result.isFinished,
                                            existingIsFinished = existing.isFinished,
                                        ),
                                        translatedText = mergeRealTimeTranslationText(
                                            existing = existing.translatedText,
                                            incoming = result.translatedText,
                                            incomingIsFinished = result.isFinished,
                                            existingIsFinished = existing.isFinished,
                                        ),
                                        audioPath = result.translatedFileUrl ?: existing.audioPath,
                                        isFinished = result.isFinished || existing.isFinished,
                                    )
                                } else {
                                    TranslationMessageEntity(
                                        messageId = segmentMessageId,
                                        requestId = requestId,
                                        originalText = result.originalText ?: "",
                                        translatedText = result.translatedText ?: "",
                                        audioPath = result.translatedFileUrl,
                                        isFinished = result.isFinished,
                                    )
                                }
                                repository.upsertRealTimeMessage(newEntity)
                            } else {
                                // 对话翻译：保持 (requestId, messageId) 复合主键
                                val msgId = result.messageId!!
                                val existing = repository.getMessageById(requestId, msgId)
                                val newEntity = if (existing != null) {
                                    existing.copy(
                                        originalText = result.originalText ?: existing.originalText,
                                        translatedText = result.translatedText ?: existing.translatedText,
                                        audioPath = result.translatedFileUrl ?: existing.audioPath,
                                        isFinished = result.isFinished,
                                    )
                                } else {
                                    TranslationMessageEntity(
                                        messageId = msgId,
                                        requestId = requestId,
                                        originalText = result.originalText ?: "",
                                        translatedText = result.translatedText ?: "",
                                        audioPath = result.translatedFileUrl,
                                        isFinished = result.isFinished,
                                    )
                                }
                                repository.insertMessage(newEntity)
                            }
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    private fun observeGlobalWsConnectionState() {
        viewModelScope.launch {
            wsConnectionStateManager.state.collect { ws ->
                _uiState.update { it.copy(wsConnection = ws) }
            }
        }
    }

    fun reconnectWebSocket() {
        wsConnectionStateManager.manualReconnect()
    }

    private fun disableOpusStreamPushForTranslation() {
        viewModelScope.launch {
            GlassesManage.setVoiceWakeUp(
                localOfflineEnabled = false,
                opusPushEnabled = false,
            )
        }
    }

    private fun restoreOpusStreamPushAfterTranslation() {
        GlassesManage.setVoiceWakeUp(
            localOfflineEnabled = true,
            opusPushEnabled = true,
        )
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
        when {
            _uiState.value.currentMode == TranslationMode.REAL_TIME &&
                mode == TranslationMode.DIALOGUE -> endRealTimeSession()
            _uiState.value.currentMode == TranslationMode.DIALOGUE &&
                mode == TranslationMode.REAL_TIME -> endDialogueReceivingSession()
        }
        _uiState.update { it.copy(currentMode = mode) }
    }

    /** 切到实时翻译前结束对话翻译 listen，避免未完成分片在实时同传下行被误 supersede。 */
    private fun endDialogueReceivingSession() {
        viewModelScope.launch {
            if (_uiState.value.isRecording) {
                streamRecorder.stop()
                _uiState.update { it.copy(isRecording = false, currentAmplitude = 0f) }
            }
            AiAssistantClient.getInstance().stopReceivingAudio(
                GlassesConstant.AI_ASSISTANT_TYPE_LISTEN_MODE_TRANSLATION,
            )
        }
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
        if (_uiState.value.isRecording) return
        val fileName = "record_${System.currentTimeMillis()}"
        _uiState.update { it.copy(isRecording = true) }

        viewModelScope.launch {
            val modeStr = listenModeFor(_uiState.value.currentMode)
            when (_uiState.value.currentMode) {
                TranslationMode.REAL_TIME -> {
                    if (!realTimeSessionActive) {
                        val requestId = System.currentTimeMillis()
                        registerTranslationRequest(requestId, TranslationMode.REAL_TIME)
                        AiAssistantClient.getInstance().startAiTranslation(
                            uiState.value.srcLang?.langType!!,
                            listOf(uiState.value.targetLang?.langType!!),
                            requestId,
                            audioFormat = GlassesConstant.AI_TRANSLATION_AUDIO_FORMAT_RAW_PCM
                        )
                        realTimeSessionActive = true
                        _uiState.update {
                            it.copy(
                                isRealTimeSessionActive = true,
                                translationAudioPlaybackEnabled = true,
                            )
                        }
                        delay(100)
                    }
                    AiAssistantClient.getInstance().setTranslationAudioPlaybackEnabled(_uiState.value.translationAudioPlaybackEnabled)
                    AiAssistantClient.getInstance().startReceivingAudio(modeStr, 140)
                }
                TranslationMode.DIALOGUE -> {
                    val requestId = System.currentTimeMillis()
                    registerTranslationRequest(requestId, TranslationMode.DIALOGUE)
                    AiAssistantClient.getInstance().startAiTranslation(
                        uiState.value.srcLang?.langType!!,
                        listOf(uiState.value.targetLang?.langType!!),
                        requestId,
                        audioFormat = GlassesConstant.AI_TRANSLATION_AUDIO_FORMAT_RAW_PCM,
                    )
                    delay(100)
                    AiAssistantClient.getInstance().startReceivingAudio(modeStr, 140)
                }
            }

            streamRecorder.start(fileName = fileName) { pcmData ->
                AiAssistantClient.getInstance().sendReceivingAudioData(modeStr, pcmData)
                val amplitude = calculateRMS(pcmData)
                _uiState.update { it.copy(currentAmplitude = amplitude) }
                pcmData
            }
        }
    }

    fun stopRecording() {
        if (!_uiState.value.isRecording) return
        viewModelScope.launch {
            streamRecorder.stop()
            clearSimultaneousCaptureSessionIfNeeded()
            _uiState.update { it.copy(isRecording = false, currentAmplitude = 0f) }

            when (_uiState.value.currentMode) {
                TranslationMode.REAL_TIME -> AiAssistantClient.getInstance().pauseListening()
                TranslationMode.DIALOGUE -> AiAssistantClient.getInstance().stopReceivingAudio(
                    GlassesConstant.AI_ASSISTANT_TYPE_LISTEN_MODE_TRANSLATION
                )
            }
        }
    }

    /** 实时翻译：单击在开始/暂停之间切换。 */
    fun toggleRealTimeRecording() {
        if (_uiState.value.currentMode != TranslationMode.REAL_TIME) return
        if (_uiState.value.isRecording) {
            stopRecording()
        } else {
            startRecording()
        }
    }

    /** 实时翻译：切换译文自动播放。 */
    fun toggleTranslationAudioPlayback() {
        if (_uiState.value.currentMode != TranslationMode.REAL_TIME) return
        val enabled = !_uiState.value.translationAudioPlaybackEnabled
        AiAssistantClient.getInstance().setTranslationAudioPlaybackEnabled(enabled)
        _uiState.update { it.copy(translationAudioPlaybackEnabled = enabled) }
    }

    /** 实时翻译：长按结束整场会话。 */
    fun endRealTimeRecording() {
        if (_uiState.value.currentMode != TranslationMode.REAL_TIME) return
        endRealTimeSession()
    }

    private fun clearSimultaneousCaptureSessionIfNeeded() {
        if (_uiState.value.currentMode == TranslationMode.REAL_TIME) {
            runCatching {
                AiAssistantClient.getInstance().clearSimultaneousInterpretationCaptureSession()
            }
        }
    }

    private fun endRealTimeSession() {
        viewModelScope.launch {
            if (_uiState.value.isRecording) {
                streamRecorder.stop()
                clearSimultaneousCaptureSessionIfNeeded()
            }
            if (realTimeSessionActive) {
                AiAssistantClient.getInstance().stopReceivingAudio(
                    GlassesConstant.AI_ASSISTANT_TYPE_LISTEN_MODE_SIMULTANEOUS_INTERPRETATION
                )
                realTimeSessionActive = false
            }
            _uiState.update {
                it.copy(isRecording = false, currentAmplitude = 0f, isRealTimeSessionActive = false)
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            if (realTimeSessionActive) {
                AiAssistantClient.getInstance().stopReceivingAudio(
                    GlassesConstant.AI_ASSISTANT_TYPE_LISTEN_MODE_SIMULTANEOUS_INTERPRETATION
                )
                realTimeSessionActive = false
                _uiState.update { it.copy(isRealTimeSessionActive = false) }
            }
            repository.clearTranslationsByMode(_uiState.value.currentMode.toStorageKey())
        }
    }

    private fun registerTranslationRequest(requestId: Long, mode: TranslationMode) {
        requestIdToMode[requestId.toString()] = mode.toStorageKey()
    }

    private fun listenModeFor(mode: TranslationMode): String = when (mode) {
        TranslationMode.DIALOGUE -> GlassesConstant.AI_ASSISTANT_TYPE_LISTEN_MODE_TRANSLATION
        TranslationMode.REAL_TIME -> GlassesConstant.AI_ASSISTANT_TYPE_LISTEN_MODE_SIMULTANEOUS_INTERPRETATION
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
        restoreOpusStreamPushAfterTranslation()
        if (_uiState.value.isRecording) {
            runBlocking(Dispatchers.IO) {
                try {
                    streamRecorder.stop()
                    clearSimultaneousCaptureSessionIfNeeded()
                } catch (_: Exception) {
                }
            }
        }
        if (realTimeSessionActive) {
            AiAssistantClient.getInstance().stopReceivingAudio(
                GlassesConstant.AI_ASSISTANT_TYPE_LISTEN_MODE_SIMULTANEOUS_INTERPRETATION
            )
            realTimeSessionActive = false
        }
        super.onCleared()
    }
}
