/**
 * @name ImageTranslateUiState
 * @class name：com.lw.ai.glasses.ui.image_trans
 * @author ly
 * @time 2026/1/26 11:51
 * @change
 * @chang time
 * @class describe
 */
package com.lw.ai.glasses.ui.imageocr

import android.graphics.Bitmap
import com.fission.wear.glasses.sdk.data.model.LanguageResult
import java.io.File

data class ImageTranslateUiState(
    // 语言相关
    val allLanguages: List<LanguageResult> = emptyList(),
    val selectedSourceLang: LanguageResult? = null,
    val selectedTargetLang: LanguageResult? = null,
    // 图片相关
    val originalImageFile: File? = null,
    val translatedImageBitmap: Bitmap? = null,
    // 状态相关
    val isLoading: Boolean = false,
    val errorMsg: String = "",
    val isTranslating: Boolean = false
)