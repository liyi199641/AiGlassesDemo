

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

@Composable
fun AppAsyncImage(
    modifier: Modifier = Modifier,
    imageUrl: String? = null,
    contentDescription: String = "",
//    painter: Painter = painterResource(R.mipmap.ic_asste_nfr),
    shape: Int = 8,
    contentScale: ContentScale = ContentScale.Crop,
    alpha: Float = DefaultAlpha,
    colorFilter: ColorFilter? = null,
    borderColor: Color? = null,
    borderWidth: Dp = 0.5.dp
) {
    Box(
        modifier = modifier.then(
            if (borderColor != null) {
                modifier
                    .border(borderWidth, borderColor, RoundedCornerShape(shape.dp))
            } else {
                modifier
            }
        )
    ) {
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = contentDescription,
                modifier = modifier.clip(RoundedCornerShape(shape.dp)),
                alpha = alpha,
                colorFilter = colorFilter,
                contentScale = contentScale
            )
        } else {
//            Image(
//                contentDescription = contentDescription,
//                modifier = modifier.clip(RoundedCornerShape(shape.dp)),
//                contentScale = contentScale,
//                alpha = alpha,
//                colorFilter = colorFilter
//            )
        }
    }

}