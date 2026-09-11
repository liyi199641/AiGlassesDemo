
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@Composable
fun BaseItemScreen(itemData: AppItemData, onItemClick: (AppItemType) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clickable {
                onItemClick(itemData.type)
            }
            .padding(horizontal = 16.dp)
            .background(itemData.bgColor),
        verticalAlignment = Alignment.CenterVertically
    ) {
        itemData.iconRes?.let {
            AppIcon(
                painterResId = itemData.iconRes,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.size(8.dp))
        }

        AppText(
            textRes = itemData.titleRes,
            style = MaterialTheme.typography.bodyMedium,
            textColor = if (itemData.type == AppItemType.SETTINGS_LOGOUT) TextRed else TextPrimary
        )
        Spacer(modifier = Modifier.weight(1f))

        itemData.badgeText?.let {
            AppText(
                textStr = it,
                style = MaterialTheme.typography.bodyMedium,
                textColor = TextSecondary
            )
            Spacer(modifier = Modifier.size(5.dp))

            itemData.tagText?.let { tag ->
                AppText  (
                    textStr = tag,
                    style = MaterialTheme.typography.bodyMedium,
                    textBackgroundColor=TextRed,
                    textBackgroundShape = 6,
                    textBackgroundHorizontalPadding = 4,
                    textBackgroundVerticalPadding = 2

                )
                Spacer(modifier = Modifier.size(5.dp))
            }
        }
        itemData.trailingPath?.let {
            AppAsyncImage(
                imageUrl = itemData.trailingPath,
                modifier = Modifier.size(30.dp),
                shape = 30
            )
            Spacer(modifier = Modifier.size(5.dp))
        }

        AppIcon(
            painterResId = 0,
            modifier = Modifier.size(16.dp)
        )

    }

}