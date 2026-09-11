package com.lw.ai.glasses.ui.theme.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay

@Composable
fun TypewriterText(
    textToAnimate: String,
    modifier: Modifier = Modifier,
    typingDelay: Long = 50L,
    previousLength: Int = 0,
    onAnimationEnd: (Int) -> Unit
) {
    val safePreviousLength = previousLength.coerceIn(0, textToAnimate.length)
    var displayedLength by remember { mutableIntStateOf(safePreviousLength) }

    SideEffect {
        if (safePreviousLength > displayedLength) {
            displayedLength = safePreviousLength
        }
    }

    LaunchedEffect(textToAnimate) {
        val startIndex = displayedLength.coerceAtMost(textToAnimate.length)
        if (startIndex >= textToAnimate.length) {
            onAnimationEnd(startIndex)
            return@LaunchedEffect
        }

        for (i in startIndex until textToAnimate.length) {
            displayedLength = i + 1
            delay(typingDelay)
        }

        onAnimationEnd(displayedLength)
    }

    Text(
        text = textToAnimate.take(displayedLength.coerceAtMost(textToAnimate.length)),
        modifier = modifier,
    )
}
