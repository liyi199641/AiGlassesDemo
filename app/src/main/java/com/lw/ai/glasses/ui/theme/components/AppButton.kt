

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class ButtonType { Gradient, Normal, Custom }

@Composable
fun AppButton(
    textRes: Int? = null,
    textStr: String? = null,
    textColors: Color = TextPrimary,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    fontWeight: FontWeight = FontWeight.Bold,
    width: Int? = null,
    height: Int? = 48,
    onClick: () -> Unit = {},
    enabled: Boolean = true,
    buttonType: ButtonType = ButtonType.Normal,
    customColor: Color? = null,
    customPressedColor: Color = BtnPressed,
    modifier: Modifier = Modifier,
    shape: Int = 24
) {

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val buttonBrush: Brush = when {
        !enabled -> Brush.horizontalGradient(listOf(BtnDisabled, BtnDisabled))
        isPressed -> Brush.horizontalGradient(listOf(customPressedColor, customPressedColor))
        buttonType == ButtonType.Custom && customColor != null -> Brush.horizontalGradient(
            listOf(
                customColor,
                customColor
            )
        )

        buttonType == ButtonType.Gradient -> Brush.horizontalGradient(GradientColor)
        else -> Brush.horizontalGradient(listOf(BtnNormal, BtnNormal)) // 默认是 Normal
    }

    var sizeModifier = modifier
    width?.let { sizeModifier = sizeModifier.width(it.dp) }
    height?.let { sizeModifier = sizeModifier.height(it.dp) }


    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(shape.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
        modifier = modifier
            .then(sizeModifier)
            .background(buttonBrush, RoundedCornerShape(shape.dp))
            .clipToBounds(),
        contentPadding = PaddingValues(0.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            AppText(
                textRes = textRes,
                textStr = textStr,
                style = textStyle.copy(
                    color = textColors,
                    fontWeight = fontWeight
                ),
                textColor = textColors,
            )
        }
    }
}