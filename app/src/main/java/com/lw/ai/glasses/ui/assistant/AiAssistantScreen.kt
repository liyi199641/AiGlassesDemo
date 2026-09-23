package com.lw.ai.glasses.ui.assistant

import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.fission.wear.glasses.sdk.data.model.McpScheduleData
import com.lw.ai.glasses.R
import com.lw.ai.glasses.state.StreamState
import com.lw.ai.glasses.ui.common.WsConnectionTopNotification
import com.lw.ai.glasses.ui.theme.components.TypewriterText
import com.lw.ai.glasses.ui.translate.Language
import com.lw.ai.glasses.ui.translate.LanguageSelectionSheet
import com.lw.top.lib_core.data.local.entity.AiAssistantEntity
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistantScreen(
    onNavigateBack: () -> Unit,
    viewModel: AiAssistantViewModel = hiltViewModel()
) {

    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val dialogContent by viewModel.pendingCalendarEvent.collectAsStateWithLifecycle(null)
    val showConfirmDialog by viewModel.showConfirmDialog.collectAsStateWithLifecycle()
    var showLanguageSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.navigateToCalendar.collectLatest { event ->
            launchCalendarIntent(context, event)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.onScreenVisible()
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.onScreenHidden()
        }
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelAddCalendar() }, // 点击外部关闭
            title = { Text(text = stringResource(R.string.add_calendar_confirm_title)) },
            text = { Text(text = dialogContent?.event?:"") },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmAddCalendar() }) {
                    Text(text = stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelAddCalendar() }) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showLanguageSheet) {
        LanguageSelectionSheet(
            languages = uiState.allLanguages,
            onDismissRequest = { showLanguageSheet = false },
            onLanguageSelected = { language ->
                viewModel.setDialogueLanguage(language)
            },
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.ai_assistant_title)) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    },
                    actions = {
                        TextButton(onClick = {
                            viewModel.clearAllMessages()
                        }) {
                            Text(stringResource(R.string.clear_records))
                        }
                    }
                )
            },
            bottomBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .navigationBarsPadding(),
                ) {
                    AiDeviceControlBar(
                        isAiDialogueInProgress = uiState.isAiDialogueInProgress,
                        onStartAi = viewModel::startAiAssistant,
                        onStopAi = viewModel::stopAiAssistant,
                        onInterruptAi = viewModel::interruptAiAssistant,
                    )
                    AssistantBottomBar(
                        selectedLanguage = uiState.selectedLanguage,
                        agentAudioPlaybackEnabled = uiState.agentAudioPlaybackEnabled,
                        onLanguageClick = { showLanguageSheet = true },
                        onToggleAudio = { viewModel.toggleAgentAudioPlayback() },
                    )
                }
            },
        ) { innerPadding ->
            ConversationList(
                messages = uiState.messages,
                streamingMessageId = uiState.streamingMessageId,
                typewriterRevision = uiState.typewriterRevision,
                playingAnswerAudioPath = uiState.playingAnswerAudioPath,
                playingQuestionAudioPath = uiState.playingQuestionAudioPath,
                isAiDialogueInProgress = uiState.isAiDialogueInProgress,
                onPlayAnswerAudio = viewModel::playAnswerAudio,
                onPlayQuestionAudio = viewModel::playQuestionAudio,
                getTypewriterProgress = viewModel::getTypewriterProgress,
                onTypewriterProgressUpdate = viewModel::updateTypewriterProgress,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
        }
        WsConnectionTopNotification(
            state = uiState.wsConnection,
            onReconnect = viewModel::reconnectWebSocket,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

@Composable
private fun AiDeviceControlBar(
    isAiDialogueInProgress: Boolean,
    onStartAi: () -> Unit,
    onStopAi: () -> Unit,
    onInterruptAi: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.ai_assistant_device_control),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (isAiDialogueInProgress) {
            Text(
                text = stringResource(R.string.ai_assistant_dialogue_in_progress),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = onStartAi,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.start_ai))
            }
            OutlinedButton(
                onClick = onStopAi,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.stop_ai))
            }
        }
        Button(
            onClick = onInterruptAi,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.interrupt_ai_chat))
        }
    }
}

@Composable
private fun AssistantBottomBar(
    selectedLanguage: Language?,
    agentAudioPlaybackEnabled: Boolean,
    onLanguageClick: () -> Unit,
    onToggleAudio: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = true,
            onClick = onLanguageClick,
            label = {
                Text(
                    text = selectedLanguage?.name
                        ?: stringResource(R.string.choose_language),
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = stringResource(R.string.ai_dialogue_language),
                    modifier = Modifier.size(18.dp),
                )
            },
        )
        FilterChip(
            selected = agentAudioPlaybackEnabled,
            onClick = onToggleAudio,
            label = {
                Text(
                    text = if (agentAudioPlaybackEnabled) {
                        stringResource(R.string.agent_audio_playback_on)
                    } else {
                        stringResource(R.string.agent_audio_playback_off)
                    },
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = if (agentAudioPlaybackEnabled) {
                        Icons.Default.VolumeUp
                    } else {
                        Icons.Default.VolumeOff
                    },
                    contentDescription = stringResource(R.string.toggle_agent_audio_playback),
                    modifier = Modifier.size(18.dp),
                )
            },
        )
    }
}

@Composable
private fun ConversationList(
    messages: List<AiAssistantEntity>,
    streamingMessageId: Long?,
    typewriterRevision: Int,
    playingAnswerAudioPath: String?,
    playingQuestionAudioPath: String?,
    isAiDialogueInProgress: Boolean,
    onPlayAnswerAudio: (String) -> Unit,
    onPlayQuestionAudio: (String) -> Unit,
    getTypewriterProgress: (Long) -> StreamState,
    onTypewriterProgressUpdate: (Long, Int?, Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val topMessageTimestamp = messages.firstOrNull()?.timestamp

    LaunchedEffect(topMessageTimestamp) {
        if (topMessageTimestamp != null) {
            listState.scrollToItem(0)
        }
    }

    if (messages.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(stringResource(R.string.empty_ai_conversation))
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            reverseLayout = true
        ) {
            itemsIndexed(
                items = messages,
                key = { _, message -> message.timestamp }) { _, message ->
                val isStreamingMessage = streamingMessageId != null &&
                    message.timestamp == streamingMessageId
                val typewriterProgress = remember(message.timestamp, typewriterRevision) {
                    getTypewriterProgress(message.timestamp)
                }
                MessageBubble(
                    message = message,
                    enableTypewriter = isStreamingMessage,
                    typewriterProgress = typewriterProgress,
                    onTypewriterProgressUpdate = { questionLength, answerLength ->
                        onTypewriterProgressUpdate(message.timestamp, questionLength, answerLength)
                    },
                    isQuestionAudioPlaying = playingQuestionAudioPath == message.questionAudioPath,
                    isAnswerAudioPlaying = playingAnswerAudioPath == message.answerAudioPath,
                    isAnswerAudioPlayable = !isAiDialogueInProgress,
                    onPlayQuestionAudio = onPlayQuestionAudio,
                    onPlayAnswerAudio = onPlayAnswerAudio,
                )
            }
        }
    }
}


@Composable
private fun MessageBubble(
    message: AiAssistantEntity,
    enableTypewriter: Boolean,
    typewriterProgress: StreamState,
    onTypewriterProgressUpdate: (questionLength: Int?, answerLength: Int?) -> Unit,
    isQuestionAudioPlaying: Boolean,
    isAnswerAudioPlaying: Boolean,
    isAnswerAudioPlayable: Boolean,
    onPlayQuestionAudio: (String) -> Unit,
    onPlayAnswerAudio: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // 仅在有 ASR 文本时展示问题气泡与录音；纯音频默认不显示。
        val questionAudioPath = message.questionAudioPath
            ?.takeIf { it.isNotBlank() && message.question.isNotEmpty() }
        if (message.question.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                QuestionMessageCard(
                    question = message.question,
                    questionType = message.questionType,
                    questionAudioPath = questionAudioPath,
                    isQuestionAudioPlaying = isQuestionAudioPlaying,
                    isQuestionAudioPlayable = isAnswerAudioPlayable,
                    onPlayQuestionAudio = onPlayQuestionAudio,
                )
            }
        }

        val answerAudioPath = message.answerAudioPath?.takeIf { it.isNotBlank() }
        if (message.answer.isNotEmpty() || answerAudioPath != null) {
            if (message.question.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
            }
            if (message.answerType == "image" && message.answer.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                ) {
                    MessageContent(
                        content = message.answer,
                        type = message.answerType,
                        isQuestion = false,
                        enableAnimation = enableTypewriter,
                        displayedLength = typewriterProgress.displayedAnswerLength,
                        onAnimationEnd = {
                            onTypewriterProgressUpdate(null, it)
                        },
                    )
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    AnswerMessageCard(
                        answer = message.answer,
                        answerAudioPath = answerAudioPath,
                        isAnswerAudioPlaying = isAnswerAudioPlaying,
                        isAnswerAudioPlayable = isAnswerAudioPlayable,
                        enableAnimation = enableTypewriter && message.answer.isNotEmpty(),
                        displayedLength = typewriterProgress.displayedAnswerLength,
                        onAnimationEnd = {
                            onTypewriterProgressUpdate(null, it)
                        },
                        onPlayAnswerAudio = onPlayAnswerAudio,
                    )
                }
            }
        }
    }
}

@Composable
private fun QuestionMessageCard(
    question: String,
    questionType: String,
    questionAudioPath: String?,
    isQuestionAudioPlaying: Boolean,
    isQuestionAudioPlayable: Boolean,
    onPlayQuestionAudio: (String) -> Unit,
) {
    val backgroundColor = MaterialTheme.colorScheme.primaryContainer
    val accentColor = if (isQuestionAudioPlayable) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.38f)
    }
    val hasText = question.isNotEmpty()
    val hasAudio = questionAudioPath != null
    val questionImage = stringResource(R.string.question_image)

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        modifier = Modifier.wrapContentWidth(),
    ) {
        val dividerColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f)
        MaxChildWidthColumn(
            modifier = Modifier,
            showDivider = hasText && hasAudio,
            divider = {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(dividerColor),
                )
            },
            text = {
                if (hasText) {
                    if (questionType == "image") {
                        AsyncImage(
                            model = question,
                            contentDescription = questionImage,
                            modifier = Modifier
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                .widthIn(max = 240.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Fit,
                        )
                    } else {
                        Text(
                            text = question,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        )
                    }
                }
            },
            audio = {
                Column(
                    modifier = Modifier
                        .wrapContentWidth()
                        .then(
                            if (isQuestionAudioPlayable) {
                                Modifier.clickable { onPlayQuestionAudio(questionAudioPath!!) }
                            } else {
                                Modifier
                            },
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = stringResource(R.string.play_audio),
                            tint = accentColor,
                            modifier = Modifier.size(18.dp),
                        )
                        AnswerAudioWaveBars(
                            tint = accentColor,
                            isPlaying = isQuestionAudioPlaying,
                        )
                    }
                    Text(
                        text = questionAudioPath.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.65f),
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .widthIn(max = 220.dp),
                        maxLines = 2,
                    )
                }
            },
            hasText = hasText,
            hasAudio = hasAudio,
        )
    }
}

@Composable
private fun AnswerMessageCard(
    answer: String,
    answerAudioPath: String?,
    isAnswerAudioPlaying: Boolean,
    isAnswerAudioPlayable: Boolean,
    enableAnimation: Boolean,
    displayedLength: Int,
    onAnimationEnd: (Int) -> Unit,
    onPlayAnswerAudio: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = MaterialTheme.colorScheme.secondaryContainer
    val accentColor = if (isAnswerAudioPlayable) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.38f)
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        modifier = modifier.wrapContentWidth(),
    ) {
        val dividerColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.12f)
        val hasText = answer.isNotEmpty()
        val hasAudio = answerAudioPath != null

        MaxChildWidthColumn(
            modifier = Modifier,
            showDivider = hasText && hasAudio,
            divider = {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(dividerColor),
                )
            },
            text = {
                if (enableAnimation && answer.isNotEmpty()) {
                    TypewriterText(
                        textToAnimate = answer,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        previousLength = displayedLength,
                        onAnimationEnd = onAnimationEnd,
                    )
                } else if (hasText) {
                    Text(
                        text = answer,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            },
            audio = {
                Row(
                    modifier = Modifier
                        .wrapContentWidth()
                        .then(
                            if (isAnswerAudioPlayable) {
                                Modifier.clickable { onPlayAnswerAudio(answerAudioPath!!) }
                            } else {
                                Modifier
                            },
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = stringResource(R.string.play_audio),
                        tint = accentColor,
                        modifier = Modifier.size(18.dp),
                    )
                    AnswerAudioWaveBars(
                        tint = accentColor,
                        isPlaying = isAnswerAudioPlaying,
                    )
                }
            },
            hasText = hasText,
            hasAudio = hasAudio,
        )
    }
}

/**
 * 纵向排列文本与音频，宽度取两者中较宽的一个（而非仅随音频或撑满屏幕）。
 */
@Composable
private fun MaxChildWidthColumn(
    modifier: Modifier = Modifier,
    showDivider: Boolean,
    hasText: Boolean,
    hasAudio: Boolean,
    divider: @Composable () -> Unit,
    text: @Composable () -> Unit,
    audio: @Composable () -> Unit,
) {
    Layout(
        modifier = modifier.wrapContentWidth(Alignment.Start),
        content = {
            Box(Modifier.layoutId("text")) {
                if (hasText) text()
            }
            Box(Modifier.layoutId("divider")) {
                if (showDivider) divider()
            }
            Box(Modifier.layoutId("audio")) {
                if (hasAudio) audio()
            }
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0)
        val textPlaceable = measurables.first { it.layoutId == "text" }.measure(loose)
        val audioPlaceable = measurables.first { it.layoutId == "audio" }.measure(loose)
        val contentWidth = maxOf(textPlaceable.width, audioPlaceable.width)
            .coerceIn(0, constraints.maxWidth)

        val dividerPlaceable = if (showDivider) {
            measurables.first { it.layoutId == "divider" }
                .measure(Constraints.fixedWidth(contentWidth))
        } else {
            null
        }

        val totalHeight = textPlaceable.height +
            (dividerPlaceable?.height ?: 0) +
            audioPlaceable.height

        layout(contentWidth, totalHeight) {
            var y = 0
            textPlaceable.placeRelative(0, y)
            y += textPlaceable.height
            dividerPlaceable?.placeRelative(0, y)
            y += dividerPlaceable?.height ?: 0
            audioPlaceable.placeRelative(0, y)
        }
    }
}

private val AnswerAudioWaveStaticFractions = listOf(0.45f, 0.7f, 0.55f, 0.65f)

@Composable
private fun AnswerAudioWaveBars(
    tint: Color,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "answer_audio_wave")
    val bar1 by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(420), RepeatMode.Reverse),
        label = "bar1",
    )
    val bar2 by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(520), RepeatMode.Reverse),
        label = "bar2",
    )
    val bar3 by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(380), RepeatMode.Reverse),
        label = "bar3",
    )
    val bar4 by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(460), RepeatMode.Reverse),
        label = "bar4",
    )
    val barFractions = if (isPlaying) {
        listOf(bar1, bar2, bar3, bar4)
    } else {
        AnswerAudioWaveStaticFractions
    }

    Row(
        modifier = modifier
            .height(16.dp)
            .width(21.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.Start),
        verticalAlignment = Alignment.Bottom,
    ) {
        barFractions.forEach { fraction ->
            AnswerAudioWaveBar(fraction = fraction, tint = tint)
        }
    }
}

@Composable
private fun AnswerAudioWaveBar(fraction: Float, tint: Color) {
    Box(
        modifier = Modifier
            .width(3.dp)
            .fillMaxHeight(fraction.coerceIn(0.25f, 1f))
            .clip(RoundedCornerShape(2.dp))
            .background(tint.copy(alpha = 0.85f)),
    )
}

@Composable
private fun MessageContent(
    content: String,
    type: String,
    isQuestion: Boolean,
    enableAnimation: Boolean,
    displayedLength: Int,
    onAnimationEnd: (Int) -> Unit
) {
    val questionImage = stringResource(R.string.question_image)
    val answerImage = stringResource(R.string.answer_image)
    val backgroundColor = if (isQuestion) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }

    if (type == "image") {
        AsyncImage(
            model = content,
            contentDescription = if (isQuestion) questionImage else answerImage,
            modifier = Modifier
                .widthIn(max = 240.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Fit
        )
    } else {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = backgroundColor)
        ) {
            if (enableAnimation) {
                TypewriterText(
                    textToAnimate = content,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    previousLength = displayedLength,
                    onAnimationEnd = onAnimationEnd,
                )
            } else {
                Text(
                    text = content,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }
    }
}

/**
 * 唤起日历的逻辑
 */
private fun launchCalendarIntent(context: android.content.Context, event: McpScheduleData) {
    val intent = Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, event.event)
            .putExtra(CalendarContract.Events.DESCRIPTION, event.event)
            .putExtra(CalendarContract.Events.EVENT_LOCATION, event.location)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.time * 1000)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.time * 1000 + 1800000)
            .putExtra(CalendarContract.Events.ALL_DAY, false)
            .putExtra(
                CalendarContract.Events.EVENT_TIMEZONE,
                java.util.Calendar.getInstance().timeZone.id
            )
            .putExtra(CalendarContract.Reminders.MINUTES, 30)

    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
    } else {
        // 这里可通过Snackbar提示，需把snackbarHostState传进来
        android.widget.Toast.makeText(context, context.getString(R.string.calendar_app_not_found), android.widget.Toast.LENGTH_SHORT).show()
    }
}