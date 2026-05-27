package com.lw.ai.glasses.ui.translate

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lw.ai.glasses.R
import com.lw.top.lib_core.data.local.entity.TranslationMessageEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslatorScreen(
    onNavigateBack: () -> Unit,
    viewModel: TranslatorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }
    var showLanguageSheet by remember { mutableStateOf(false) }
    var isSelectingSource by remember { mutableStateOf(true) }

    // 将历史记录中的所有片段平铺展示
    val allMessages = remember(uiState.history) {
        uiState.history.flatMap { it.messages }.sortedByDescending { it.timestamp }
    }

    // 1. 创建 LazyListState
    val listState = rememberLazyListState()

    // 2. 当消息数量发生变化时，自动滚动到顶部
    LaunchedEffect(allMessages.size) {
        if (allMessages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.clear_records)) },
            text = { Text(stringResource(R.string.clear_translation_history_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearHistory()
                        showClearDialog = false
                    }
                ) {
                    Text(stringResource(R.string.confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showLanguageSheet) {
        LanguageSelectionSheet(
            languages = uiState.allLanguages,
            onDismissRequest = { showLanguageSheet = false },
            onLanguageSelected = { lang ->
                if (isSelectingSource) {
                    viewModel.setSourceLanguage(lang)
                } else {
                    viewModel.setTargetLanguage(lang)
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ai_translate_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { showClearDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.clear))
                    }
                }
            )
        },
        bottomBar = {
            Column(modifier = Modifier.navigationBarsPadding()) {
                RecordControlPanel(
                    isRecording = uiState.isRecording,
                    isRealTimeSessionActive = uiState.isRealTimeSessionActive,
                    currentMode = uiState.currentMode,
                    currentAmplitude = uiState.currentAmplitude,
                    onStartRecording = { viewModel.startRecording() },
                    onStopRecording = { viewModel.stopRecording() },
                    onToggleRealTimeRecording = { viewModel.toggleRealTimeRecording() },
                    onEndRealTimeRecording = { viewModel.endRealTimeRecording() },
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            LanguageTopBar(
                srcLang = uiState.srcLang,
                targetLang = uiState.targetLang,
                onSrcClick = {
                    isSelectingSource = true
                    showLanguageSheet = true
                },
                onTargetClick = {
                    isSelectingSource = false
                    showLanguageSheet = true
                },
                onSwapClick = { viewModel.swapLanguages() }
            )

            // 模式选择器
            ModeSelector(
                currentMode = uiState.currentMode,
                onModeSelected = { viewModel.setTranslationMode(it) }
            )

            LazyColumn(
                state = listState, // 3. 绑定 state
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp)
            ) {
                items(
                    items = allMessages,
                    key = { it.requestId + it.messageId } // 复合主键作为唯一标识
                ) { message ->
                    TranslationItemCard(
                        item = message,
                        onPlayAudio = { audioPath ->
                            viewModel.playAudio(audioPath)
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModeSelector(
    currentMode: TranslationMode,
    onModeSelected: (TranslationMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        FilterChip(
            selected = currentMode == TranslationMode.REAL_TIME,
            onClick = { onModeSelected(TranslationMode.REAL_TIME) },
            label = { Text(stringResource(R.string.real_time_translate)) },
            shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp),
            modifier = Modifier.weight(1f)
        )
        FilterChip(
            selected = currentMode == TranslationMode.DIALOGUE,
            onClick = { onModeSelected(TranslationMode.DIALOGUE) },
            label = { Text(stringResource(R.string.dialogue_translate)) },
            shape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun LanguageTopBar(
    srcLang: Language?,
    targetLang: Language?,
    onSrcClick: () -> Unit,
    onTargetClick: () -> Unit,
    onSwapClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        TextButton(onClick = onSrcClick, modifier = Modifier.weight(1f)) {
            Text(
                srcLang?.name ?: stringResource(R.string.choose_language),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1
            )
        }

        IconButton(onClick = onSwapClick) {
            Icon(
                Icons.Default.SwapHoriz,
                contentDescription = stringResource(R.string.swap),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        TextButton(onClick = onTargetClick, modifier = Modifier.weight(1f)) {
            Text(
                targetLang?.name ?: stringResource(R.string.choose_language),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSelectionSheet(
    languages: List<Language>,
    onDismissRequest: () -> Unit,
    onLanguageSelected: (Language) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredLanguages = remember(searchQuery, languages) {
        if (searchQuery.isBlank()) {
            languages
        } else {
            languages.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                        it.nameEn.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.choose_language), style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = onDismissRequest) {
                    Icon(Icons.Default.Close, null)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.search_language_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn {
                items(filteredLanguages) { language ->
                    ListItem(
                        headlineContent = { Text(language.name) },
                        supportingContent = { Text(language.nameEn) },
                        modifier = Modifier.clickable {
                            onLanguageSelected(language)
                            onDismissRequest()
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
@Composable
fun RecordControlPanel(
    isRecording: Boolean,
    isRealTimeSessionActive: Boolean,
    currentMode: TranslationMode,
    currentAmplitude: Float,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onToggleRealTimeRecording: () -> Unit,
    onEndRealTimeRecording: () -> Unit,
) {
    if (currentMode == TranslationMode.REAL_TIME) {
        RealTimeRecordControlPanel(
            isRecording = isRecording,
            isRealTimeSessionActive = isRealTimeSessionActive,
            currentAmplitude = currentAmplitude,
            onToggleRecording = onToggleRealTimeRecording,
            onEndRecording = onEndRealTimeRecording,
        )
    } else {
        DialogueRecordControlPanel(
            isRecording = isRecording,
            currentAmplitude = currentAmplitude,
            onStartRecording = onStartRecording,
            onStopRecording = onStopRecording,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RealTimeRecordControlPanel(
    isRecording: Boolean,
    isRealTimeSessionActive: Boolean,
    currentAmplitude: Float,
    onToggleRecording: () -> Unit,
    onEndRecording: () -> Unit,
) {
    val pressScale by animateFloatAsState(
        targetValue = if (isRecording) 1.1f else 1.0f,
        label = "pressScale"
    )
    val volumeScale by animateFloatAsState(
        targetValue = if (isRecording) 1.0f + (currentAmplitude * 0.8f) else 1.0f,
        label = "volumeScale"
    )

    val containerColor = when {
        isRecording -> MaterialTheme.colorScheme.error
        isRealTimeSessionActive -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    val iconColor = when {
        isRecording -> MaterialTheme.colorScheme.onError
        isRealTimeSessionActive -> MaterialTheme.colorScheme.onTertiary
        else -> MaterialTheme.colorScheme.onPrimary
    }
    val icon = when {
        isRecording -> Icons.Default.Mic
        isRealTimeSessionActive -> Icons.Default.Pause
        else -> Icons.Default.Mic
    }
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(120.dp)
        ) {
            if (isRecording) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .scale(volumeScale)
                        .background(
                            color = containerColor.copy(alpha = 0.3f),
                            shape = CircleShape
                        )
                )
            }

            Box(
                modifier = Modifier
                    .size(80.dp)
                    .scale(pressScale)
                    .background(containerColor, CircleShape)
                    .combinedClickable(
                        onClick = onToggleRecording,
                        onLongClick = onEndRecording,
                        indication = null,
                        interactionSource = interactionSource,
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = stringResource(R.string.record_audio),
                    tint = iconColor,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = when {
                isRecording -> stringResource(R.string.tap_to_pause_recording)
                isRealTimeSessionActive -> stringResource(R.string.tap_to_resume_recording)
                else -> stringResource(R.string.tap_to_start_recording)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (isRealTimeSessionActive || isRecording) {
            Text(
                text = stringResource(R.string.long_press_to_end_recording),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun DialogueRecordControlPanel(
    isRecording: Boolean,
    currentAmplitude: Float,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val currentStart by rememberUpdatedState(onStartRecording)
    val currentStop by rememberUpdatedState(onStopRecording)

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> currentStart()
                is PressInteraction.Release -> currentStop()
                is PressInteraction.Cancel -> currentStop()
            }
        }
    }

    val pressScale by animateFloatAsState(
        targetValue = if (isRecording) 1.1f else 1.0f,
        label = "pressScale"
    )
    val volumeScale by animateFloatAsState(
        targetValue = if (isRecording) 1.0f + (currentAmplitude * 0.8f) else 1.0f,
        label = "volumeScale"
    )

    val containerColor = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val iconColor = if (isRecording) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(120.dp)
        ) {
            if (isRecording) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .scale(volumeScale)
                        .background(
                            color = containerColor.copy(alpha = 0.3f),
                            shape = CircleShape
                        )
                )
            }

            Box(
                modifier = Modifier
                    .size(80.dp)
                    .scale(pressScale)
                    .background(containerColor, CircleShape)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = {},
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = stringResource(R.string.record_audio),
                    tint = iconColor,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (isRecording) {
                stringResource(R.string.recording_now)
            } else {
                stringResource(R.string.hold_to_talk)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun TranslationItemCard(
    item: TranslationMessageEntity,
    onPlayAudio: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = item.originalText ?: "...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                thickness = 0.5.dp
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = item.translatedText.ifEmpty { stringResource(R.string.translating) },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )

                if (!item.audioPath.isNullOrEmpty() && item.isFinished) {
                    IconButton(
                        onClick = { onPlayAudio(item.audioPath!!) }
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = stringResource(R.string.play_audio),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
