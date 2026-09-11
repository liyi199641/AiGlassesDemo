
import android.annotation.SuppressLint
import androidx.annotation.DrawableRes
import androidx.compose.ui.text.AnnotatedString
import com.lw.ai.glasses.R

@SuppressLint("ResourceType")
data class ConfirmationData(
    val title: String,
    val content: String,
    val isCheck: Boolean = false,
    val checkContent: AnnotatedString? = null,
    @DrawableRes val leftText: Int = R.string.cancel,
    @DrawableRes val rightText: Int = R.string.ok,
    )
