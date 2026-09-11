

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun AppText(
    @StringRes textRes: Int? = null,
    textStr: String? = null,
    modifier: Modifier = Modifier,
    annotatedText: AnnotatedString? = null,
    textColor: Color = TextPrimary,
    style: TextStyle = LocalTextStyle.current,
    fontSize: Int = 14,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE,
    includeFontPadding: Boolean = true,
    textBackgroundColor: Color = Color.Transparent,
    textBackgroundShape: Int = 4,
    textBackgroundVerticalPadding: Int = 0,
    textBackgroundHorizontalPadding: Int = 0,
    onClick: () -> Unit = {},
) {

    val textToDisplay: AnnotatedString = when {
        annotatedText != null -> annotatedText
        textRes != null -> AnnotatedString(LocalContext.current.getString(textRes))
        textStr != null -> AnnotatedString(textStr)
        else -> AnnotatedString("") // Default to empty string if nothing is provided
    }

    val finalStyle =
        style.merge().let { baseStyle ->
            val newPlatformTextStyle = PlatformTextStyle(
                includeFontPadding = includeFontPadding
            )
            baseStyle.copy(platformStyle = newPlatformTextStyle)
        }

    val textComposable = @Composable {
        Text(
            text = textToDisplay,
            modifier = modifier.padding(
                top = textBackgroundVerticalPadding.dp, bottom = textBackgroundVerticalPadding.dp,
                start = textBackgroundHorizontalPadding.dp, end = textBackgroundHorizontalPadding.dp
            ),
            color = textColor,
            style = finalStyle,
            textAlign = textAlign ?: TextAlign.Unspecified,
            overflow = overflow,
            maxLines = maxLines
        )
    }

    if (textBackgroundColor != Color.Transparent) {
        // 如果有背景颜色，使用 Box 来包裹 Text
        val shape = RoundedCornerShape(textBackgroundShape)  // 默认形状
        Box(
            modifier = modifier // 外部 modifier 应用于 Box
                .clip(shape) // 先裁剪，确保背景在形状内
                .background(color = textBackgroundColor, shape = shape) // 然后设置背景
                .then(Modifier.clickable(onClick = onClick)),
            contentAlignment = Alignment.Center // 可以根据需要设为参数
        ) {
            textComposable()
        }
    } else {
        Text(
            modifier = modifier
                .padding(
                    top = textBackgroundVerticalPadding.dp,
                    bottom = textBackgroundVerticalPadding.dp,
                    start = textBackgroundHorizontalPadding.dp,
                    end = textBackgroundHorizontalPadding.dp
                ),
            text = textToDisplay,
            color = textColor,
            style = finalStyle,
            textAlign = textAlign ?: TextAlign.Unspecified,
            overflow = overflow,
            maxLines = maxLines
        )
    }
}

@Composable
fun StartEndTextRow(
    startText: String = "",
    endText: String = "",
    startTextColor: Color = TextPrimary,
    endTextColor: Color = TextPrimary,
    startTextStyle: TextStyle = MaterialTheme.typography.bodySmall,
    endTextStyle: TextStyle = MaterialTheme.typography.bodySmall,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        AppText(
            textStr = startText,
            textColor = startTextColor,
            style = startTextStyle
        )
        AppText(
            textStr = endText,
            textColor = endTextColor,
            style = endTextStyle
        )

    }

}

@Composable
fun AppTagText(
    textStr: String,
    solidColor: Color,
    modifier: Modifier = Modifier,
    solidPartWidth: Dp = 4.dp,
    cornerRadius: Int = 4,
    gradientStartAlpha: Float = 0.2f,
    gradientEndAlpha: Float = 0.0f,
    textColor: Color = Color.White,
    style: TextStyle = LocalTextStyle.current,
    contentPadding: Dp = 4.dp,
    @DrawableRes endIcon: Int? = null,
    endIconTint: Color = textColor,
    endIconRotation: Float = 0f,
    endIconSize: Dp = 16.dp,
    spacingAfterText: Dp = 4.dp
) {
    Row(
        modifier = modifier
            .wrapContentWidth()
            .clip(RoundedCornerShape(cornerRadius.dp))
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(solidPartWidth)
                .fillMaxHeight()
                .background(solidColor)
        )
        Row(
            modifier = Modifier
                .wrapContentWidth()
                .fillMaxHeight()
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            solidColor.copy(alpha = gradientStartAlpha),
                            solidColor.copy(alpha = gradientEndAlpha)
                        )
                    )
                )
                .padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppText(
                textStr = textStr,
                style = style.copy(
                    color = textColor
                ),
            )

            if (endIcon != null) {
                Spacer(modifier = Modifier.width(spacingAfterText)) // 文本和图标之间的间距
                AppIcon(
                    painterResId = endIcon,
                    modifier = Modifier
                        .size(endIconSize)
                        .rotate(endIconRotation),
                    tint = endIconTint
                )
            }
        }
    }
}

@Composable
fun AppTipText(
    modifier: Modifier= Modifier,
    @DrawableRes textRes: Int?,
    topColor: Color,
    backgroundColor: Color,
) {

    Column(
        modifier = modifier.width(IntrinsicSize.Max),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Box(
            modifier = Modifier
                .height(2.dp)
                .background(topColor)
                .fillMaxWidth()
        )
        Box(
            modifier = Modifier.background(backgroundColor),
            contentAlignment = Alignment.Center
        ) {

            AppText(
                textRes = textRes,
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Black
                ),
                textColor = Color.White
            )
        }

        Box(
            modifier = Modifier.size(5.dp),
        ) {
            Canvas(
                modifier = Modifier
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
            ) {
                val path = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(5f, 0f)
                    lineTo(0f, 3f)
                    close()
                }

                drawPath(
                    path = path,
                    color = backgroundColor,
                )
            }
        }
    }

}




