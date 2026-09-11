

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SimpleSwitchTabs(
    items: List<String>,
    initiallySelectedIndex: Int = 0,
    modifier: Modifier = Modifier,
    selectedTabBackgroundColor: Color = LineBorder,
    unselectedTabBackgroundColor: Color = CardBackground,
    selectedTextColor: Color = TextPrimary,
    unselectedTextColor: Color = TextTertiary,
    tabCornerRadius: Dp = 25.dp,
    tabPadding: Dp = 12.dp,
    spaceBetweenTabs: Dp = 8.dp,
    onTabSelected: (index: Int, title: String) -> Unit
) {
    var selectedIndex by remember { mutableStateOf(initiallySelectedIndex.coerceIn(items.indices)) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(tabCornerRadius))
            .background(CardBackground),
//        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        items.forEachIndexed { index, title ->
            val isSelected = selectedIndex == index
            val backgroundColor =
                if (isSelected) selectedTabBackgroundColor else unselectedTabBackgroundColor
            val textColor = if (isSelected) selectedTextColor else unselectedTextColor

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(tabCornerRadius))
                    .background(backgroundColor)
                    .clickable {
                        if (selectedIndex != index) {
                            selectedIndex = index
                            onTabSelected(index, title)
                        }
                    }
                    .align(alignment = Alignment.CenterVertically),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title,
                    color = textColor,
                    fontSize = 14.sp
                )
            }
        }
    }

}