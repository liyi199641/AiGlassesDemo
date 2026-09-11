

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun AppCardScreen(
    modifier: Modifier = Modifier,
    backgroundColor: Color = CardBackground, // 默认背景色为灰色
    shape: Int = 8,
    elevation: Dp = 4.dp, // 默认阴影
    content: @Composable ColumnScope.() -> Unit // 卡片内容
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(shape),
        elevation = CardDefaults.cardElevation(elevation),
        colors = CardDefaults.cardColors(containerColor = backgroundColor) // 设置卡片的背景色
    ) {
        content()
    }

}