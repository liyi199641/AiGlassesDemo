/**
 * @name ImageTransScreen
 * @class name：com.lw.ai.glasses.ui.image_trans
 * @author ly
 * @time 2026/1/26 11:42
 * @change
 * @chang time
 * @class describe
 */
package com.lw.ai.glasses.ui.imageocr

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fission.wear.glasses.sdk.GlassesManage
import com.lw.ai.glasses.utils.getFileSize
import com.lw.ai.glasses.utils.getImageDimensions
import com.lw.ai.glasses.utils.uriToFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// 图片限制常量
object ImageConstraints {
    const val MAX_SIZE_MB = 4 // 最大4MB
    const val MAX_SIZE_BYTES = MAX_SIZE_MB * 1024 * 1024 // 4MB转字节
    const val MAX_WIDTH = 4096 // 最大宽度
    const val MAX_HEIGHT = 4096 // 最大高度
    val SUPPORTED_FORMATS = listOf("jpg", "jpeg", "png") // 支持的格式
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageTranslateScreen(
    onNavigateBack: () -> Unit,
    viewModel: ImageTranslateViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()

    // 下拉菜单展开状态
    var sourceExpanded by remember { mutableStateOf(false) }
    var targetExpanded by remember { mutableStateOf(false) }
    // 图片校验错误提示
    var imageCheckError by remember { mutableStateOf("") }
    // 展示译文图片
    var showTranslatedImage by remember { mutableStateOf(true) }

    // 相册选择图片Launcher + 图片校验 + 状态重置
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            // 重新选图时初始化所有相关状态 ==========
            coroutineScope.launch(Dispatchers.Main) {
                viewModel.clearTranslationResult()
                showTranslatedImage = true
                imageCheckError = ""
            }

            // 图片校验和选择逻辑
            coroutineScope.launch(Dispatchers.IO) {
                val checkResult = checkImageConstraints(context, uri)
                if (checkResult.isNotEmpty()) {
                    // 校验失败，更新错误提示
                    withContext(Dispatchers.Main) {
                        imageCheckError = checkResult
                        // 清空之前选择的图片
                        viewModel.setOriginalImageFile(null)
                    }
                } else {
                    // 校验通过，转换为File并更新ViewModel
                    val file = uriToFile(context, uri)
                    withContext(Dispatchers.Main) {
                        imageCheckError = "" // 清空错误提示
                        viewModel.setOriginalImageFile(file)
                        showTranslatedImage = true // 选新图后默认展示译文
                    }
                }
            }
        }
    }

    // 切换图片展示逻辑
    fun toggleImageShowMode() {
        showTranslatedImage = !showTranslatedImage
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ImageSearch, contentDescription = "图片翻译", modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("图片翻译")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp, vertical = 20.dp)
                    .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 加载中提示
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.padding(8.dp))
            }

            // 语言选择区域
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 原文语言选择
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "原文语言",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 4.dp)
                                .padding(start = 2.dp)
                    )
                    ExposedDropdownMenuBox(
                        expanded = sourceExpanded,
                        onExpandedChange = { sourceExpanded = it },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextField(
                            value = uiState.selectedSourceLang?.name ?: "",
                            onValueChange = {},
                            readOnly = true,
                            placeholder = { Text(text = "选择原文语言") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sourceExpanded) },
                            modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth(),
                            singleLine = true
                        )
                        ExposedDropdownMenu(
                            expanded = sourceExpanded,
                            onDismissRequest = { sourceExpanded = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            uiState.allLanguages.filter { it.supportSource }.forEach { language ->
                                DropdownMenuItem(
                                    text = { Text(language.name) },
                                    onClick = {
                                        viewModel.setSelectedSourceLang(language.copy())
                                        sourceExpanded = false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                // 译文语言选择
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "译文语言",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 4.dp)
                                .padding(start = 2.dp)
                    )
                    ExposedDropdownMenuBox(
                        expanded = targetExpanded,
                        onExpandedChange = { targetExpanded = it },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextField(
                            value = uiState.selectedTargetLang?.name ?: "",
                            onValueChange = {},
                            readOnly = true,
                            placeholder = { Text(text = "选择译文语言") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = targetExpanded) },
                            modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth(),
                            singleLine = true
                        )
                        ExposedDropdownMenu(
                            expanded = targetExpanded,
                            onDismissRequest = { targetExpanded = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            uiState.allLanguages.filter { it.supportTarget }.forEach { language ->
                                DropdownMenuItem(
                                    text = { Text(language.name) },
                                    onClick = {
                                        viewModel.setSelectedTargetLang(language.copy())
                                        targetExpanded = false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }

            // 图片选择区域
            Column(modifier = Modifier.fillMaxWidth()) {
                // 选择图片按钮（添加ImageSearch图标）
                Button(
                    onClick = { pickImageLauncher.launch("image/*") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.ImageSearch, contentDescription = "选择图片", modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("选择相册图片")
                }

                // 图片要求说明
                Column(
                    modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, start = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "📌 图片要求：",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "• 支持格式：${ImageConstraints.SUPPORTED_FORMATS.joinToString("、")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "• 大小限制：≤ ${ImageConstraints.MAX_SIZE_MB}MB",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "• 尺寸限制：≤ ${ImageConstraints.MAX_WIDTH}×${ImageConstraints.MAX_HEIGHT} 像素",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 图片校验错误提示（优先级高于通用错误）
            if (imageCheckError.isNotEmpty()) {
                Text(
                    text = imageCheckError,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 8.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            } else if (uiState.errorMsg.isNotEmpty()) {
                // 通用错误提示
                Text(
                    text = uiState.errorMsg,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 8.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // 图片展示区域
            Column(modifier = Modifier.fillMaxWidth()) {
                Card(
                    modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 4.dp,
                        pressedElevation = 2.dp
                    )
                ) {
                    when {
                        // 有译文图片 + 展示译文模式
                        uiState.translatedImageBitmap != null && showTranslatedImage -> {
                            Image(
                                bitmap = uiState.translatedImageBitmap!!.asImageBitmap(),
                                contentDescription = "翻译后的图片",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        // 有原文图片（兜底：无译文/展示原文模式）
                        uiState.originalImageFile != null -> {
                            val bitmap = BitmapFactory.decodeFile(uiState.originalImageFile!!.path)
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "原始图片",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        // 无任何图片
                        else -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "请选择符合要求的相册图片",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // 切换原文/译文图片按钮（仅当有图片时显示）
                if (uiState.originalImageFile != null) {
                    Button(
                        onClick = ::toggleImageShowMode,
                        modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                        // 无译文时禁用按钮
                        enabled = uiState.translatedImageBitmap != null,
                        colors = ButtonDefaults.buttonColors(
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text(
                            text = if (showTranslatedImage) "显示原文图片" else "显示译文图片"
                        )
                    }
                }
            }

            // 开始翻译按钮（添加Translate图标）
            Button(
                onClick = { viewModel.startTranslation() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isTranslating
                          && uiState.originalImageFile != null
                          && uiState.selectedSourceLang != null
                          && uiState.selectedTargetLang != null
                          && imageCheckError.isEmpty(),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
            ) {
                if (uiState.isTranslating) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(4.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("翻译中...")
                    }
                } else {
                    Icon(Icons.Default.ImageSearch, contentDescription = "开始翻译", modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("开始图片翻译")
                }
            }
        }
    }
}

// 校验图片格式、大小、尺寸
private fun checkImageConstraints(context: Context, uri: Uri): String {
    return try {
        // 1. 校验图片格式
        val mimeType = context.contentResolver.getType(uri)
        val fileExtension = mimeType?.split("/")?.last()?.lowercase()
        if (fileExtension !in ImageConstraints.SUPPORTED_FORMATS) {
            return "图片格式不支持！仅支持${ImageConstraints.SUPPORTED_FORMATS.joinToString("、")}格式"
        }

        // 2. 校验图片大小
        val fileSize = getFileSize(context, uri)
        if (fileSize > ImageConstraints.MAX_SIZE_BYTES) {
            val sizeMB = String.format("%.2f", fileSize / (1024 * 1024).toDouble())
            return "图片大小超过限制！当前${sizeMB}MB，最大支持${ImageConstraints.MAX_SIZE_MB}MB"
        }

        // 3. 校验图片尺寸
        val (width, height) = getImageDimensions(context, uri)
        if (width > ImageConstraints.MAX_WIDTH || height > ImageConstraints.MAX_HEIGHT) {
            return "图片尺寸超过限制！当前${width}×${height}，最大支持${ImageConstraints.MAX_WIDTH}×${ImageConstraints.MAX_HEIGHT}"
        }

        // 所有校验通过
        ""
    } catch (e: Exception) {
        "图片信息解析失败：${e.message ?: "未知错误"}"
    }
}
