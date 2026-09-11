

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun AppCardLineBorder(
    modifier: Modifier = Modifier,
    shape: Int = 8,
    borderColor: Color = LineBorder,
    borderWidth: Float = 0.5f,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .border(
                width = borderWidth.dp,
                color = borderColor,
                shape = RoundedCornerShape(shape.dp)
            )
    ) {
        content()
    }
}