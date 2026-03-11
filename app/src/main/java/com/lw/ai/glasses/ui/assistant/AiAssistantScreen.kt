package com.lw.ai.glasses.ui.assistant

import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.fission.wear.glasses.sdk.data.model.McpScheduleData
import com.lw.ai.glasses.ui.theme.components.TypewriterText
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

    LaunchedEffect(Unit) {
        viewModel.navigateToCalendar.collectLatest { event ->
            launchCalendarIntent(context, event)
        }
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelAddCalendar() }, // 点击外部关闭
            title = { Text(text = "确认添加日程") },
            text = { Text(text = dialogContent?.event?:"") },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmAddCalendar() }) {
                    Text(text = "确认")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelAddCalendar() }) {
                    Text(text = "取消")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 助手") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    TextButton(onClick = {
                        viewModel.clearAllMessages()
                    }) {
                        Text("清空记录")
                    }
                }
            )
        },
    ) { innerPadding ->
        ConversationList(
            messages = uiState.messages,
            streamingMessageId = uiState.streamingMessageId,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        )
    }
}

@Composable
private fun ConversationList(
    messages: List<AiAssistantEntity>,
    streamingMessageId: Long?,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    if (messages.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("暂无对话，快给眼镜提问吧")
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
                key = { _, message -> message.timestamp }) { index, message ->
//                val enableAnimation = message.id == streamingMessageId
                MessageBubble(
                    message = message,
                    isLatestMessage = true,
                )
            }
        }
    }
}


@Composable
private fun MessageBubble(
    message: AiAssistantEntity,
    isLatestMessage: Boolean
) {

    var displayedQuestionLength by remember(message.id) { mutableStateOf(0) }
    var displayedAnswerLength by remember(message.id) { mutableStateOf(0) }


    Column(modifier = Modifier.fillMaxWidth()) {
        if (message.question.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                MessageContent(
                    content = message.question,
                    type = message.questionType,
                    isQuestion = true,
                    enableAnimation = isLatestMessage,
                    displayedLength = displayedQuestionLength,
                    onAnimationEnd = { displayedQuestionLength = it }
                )
            }
        }

        // ... 答案部分 ...
        if (message.answer.isNotEmpty()) {
            if (message.question.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                MessageContent(
                    content = message.answer,
                    type = message.answerType,
                    isQuestion = false,
                    enableAnimation = isLatestMessage,
                    displayedLength = displayedAnswerLength,
                    onAnimationEnd = { displayedAnswerLength = it }
                )
            }
        }
    }
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
    val backgroundColor = if (isQuestion) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }

    if (type == "image") {
        AsyncImage(
            model = content,
            contentDescription = if (isQuestion) "提问图片" else "回答图片",
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
        android.widget.Toast.makeText(context, "未找到日历应用", android.widget.Toast.LENGTH_SHORT).show()
    }
}