

import androidx.annotation.DrawableRes
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource

@Composable
fun AppIcon(
    @DrawableRes painterResId: Int,
    modifier: Modifier = Modifier,
    contentDescription: String = "",
    tint: Color = Color.Unspecified
) {
    Icon(
        painter = painterResource(painterResId),
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier
    )
}