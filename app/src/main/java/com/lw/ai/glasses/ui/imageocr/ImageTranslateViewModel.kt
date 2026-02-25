/**
 * @name ImageTranslateViewModel
 * @class name：com.lw.ai.glasses.ui.image_trans
 * @author ly
 * @time 2026/1/26 11:51
 * @change
 * @chang time
 * @class describe
 */
package com.lw.ai.glasses.ui.imageocr

import BaseViewModel
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.lifecycle.viewModelScope
import com.fission.wear.glasses.sdk.GlassesManage
import com.fission.wear.glasses.sdk.constant.GlassesConstant
import com.fission.wear.glasses.sdk.data.model.LanguageResult
import com.fission.wear.glasses.sdk.events.CmdResultEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ImageTranslateViewModel @Inject constructor() : BaseViewModel() {
    // 私有可变状态
    private val _uiState = MutableStateFlow(ImageTranslateUiState())
    // 公开不可变状态
    val uiState: StateFlow<ImageTranslateUiState> = _uiState.asStateFlow()

    init {
        observeRepositoryEvents()
        // 初始化：获取语言列表 + 监听事件
        fetchLanguages()
    }

    // ImageTranslateViewModel.kt
    fun clearTranslationResult() {
        _uiState.update { state ->
            state.copy(
                translatedImageBitmap = null, // 清空译文图片
                isLoading = false,             // 重置加载状态
                isTranslating = false,         // 重置翻译中状态
                errorMsg = ""                  // 清空错误提示
            )
        }
    }


    // 获取支持的语言列表
    private fun fetchLanguages() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        viewModelScope.launch {
            GlassesManage.getImageTransLangList(GlassesConstant.ImageTranslateServerType.VOLC_ENGINE)
        }
    }

    // 监听Repository的事件流
    private fun observeRepositoryEvents() {
        viewModelScope.launch {
            GlassesManage.eventFlow().collect { event->
                when (event) {
                    is CmdResultEvent.ImageTransLangListResult       -> {
                        handleLanguageListResult(event.languageList)
                    }
                    is CmdResultEvent.ImageTransResult -> {
                        val imageBytes = Base64.decode(event.imageBase64, Base64.DEFAULT)
                        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

                        _uiState.value = _uiState.value.copy(
                            translatedImageBitmap = bitmap,
                            isTranslating = false,
                            errorMsg = ""
                        )
                    }
                    is CmdResultEvent.ImageTransFailEvent ->{
                        // 翻译失败
                        _uiState.value = _uiState.value.copy(
                            translatedImageBitmap = null,
                            isTranslating = false,
                            errorMsg = event.msg
                        )
                    }
                    else -> {}
                }
            }
        }
    }

    // 处理语言列表返回结果
    private fun handleLanguageListResult(langList: List<LanguageResult>) {
        val sourceLang = langList.firstOrNull { it.supportSource }
        val targetLang = langList.firstOrNull { it.supportTarget }

        _uiState.value = _uiState.value.copy(
            allLanguages = langList,
            selectedSourceLang = sourceLang,
            selectedTargetLang = targetLang,
            isLoading = false
        )
    }


    // ========== 对外暴露的UI操作方法 ==========
    // 设置选中的源语言
    fun setSelectedSourceLang(lang: LanguageResult) {
        _uiState.value = _uiState.value.copy(selectedSourceLang = lang)
    }

    // 设置选中的目标语言
    fun setSelectedTargetLang(lang: LanguageResult) {
        _uiState.value = _uiState.value.copy(selectedTargetLang = lang)
    }

    // 设置原始图片文件
    fun setOriginalImageFile(file: File?) {
        _uiState.value = _uiState.value.copy(
            originalImageFile = file,
            translatedImageBitmap = null, // 清空之前的翻译结果
            errorMsg = ""
        )
    }

    // 开始翻译
    fun startTranslation() {
        val currentState = _uiState.value
        // 前置校验
        when {
            currentState.originalImageFile == null -> {
                _uiState.value = currentState.copy(errorMsg = "请先选择/拍摄图片")
                return
            }
            currentState.selectedSourceLang == null -> {
                _uiState.value = currentState.copy(errorMsg = "请选择源语言")
                return
            }
            currentState.selectedTargetLang == null -> {
                _uiState.value = currentState.copy(errorMsg = "请选择目标语言")
                return
            }
        }

        // 开始翻译
        _uiState.value = currentState.copy(
            isTranslating = true,
            errorMsg = ""
        )

        viewModelScope.launch {
            GlassesManage.imageTrans(
                targetImage = currentState.originalImageFile,
                sourceLanguage = currentState.selectedSourceLang.langType,
                targetLanguage = currentState.selectedTargetLang.langType
            )
        }
    }
}