
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.blankj.utilcode.util.ToastUtils

@Composable
fun ConfirmationScreen(
    contentData: ConfirmationData,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    onNavigateToService: () -> Unit = {}
) {
    val color1 = PopupGradientColor[0]
    val color2 = PopupGradientColor[1]
    var isChecked by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to color1,
                        0.5f to color2,
                        1.0f to color2
                    )
                )
            )
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Spacer(modifier = Modifier.height(12.dp))

        AppText(
            textStr = contentData.title, style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Black
            )
        )
        Spacer(modifier = Modifier.height(16.dp))

        AppText(
            textStr = contentData.content,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth(),
            textAlign = if (contentData.isCheck) TextAlign.Start else TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        contentData.checkContent?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = isChecked,
                    onCheckedChange = { newCheckedState ->
                        isChecked = newCheckedState
                    },
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary,
                        uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        checkmarkColor = Black
                    )
                )
                Spacer(Modifier.width(8.dp))
                AppText(
                    annotatedText = it,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Start,
                    onClick = {

                    }
                )
            }
            Spacer(Modifier.height(16.dp))
        }


        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {

            AppButton(
                textRes = contentData.leftText,
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Bold
                ),
                buttonType = ButtonType.Custom,
                customColor = LineBorder,
                customPressedColor = TextDisabled,
                shape = 6,
                onClick = onCancel
            )

            Spacer(modifier = Modifier.width(16.dp))

            AppButton(
                textRes = contentData.rightText,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Bold
                ),
                textColors = Black,
                modifier = Modifier.weight(1f),
                shape = 6,
                onClick = if (contentData.checkContent != null) {
                    {
                        if (isChecked) {
                            onConfirm()
                        } else {
                            ToastUtils.showLong("")
                        }
                    }
                } else onConfirm
            )

        }


    }

}