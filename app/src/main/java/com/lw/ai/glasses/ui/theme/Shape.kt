
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(10.dp),
    extraLarge = RoundedCornerShape(12.dp)
)

val MinCornerShape: CornerBasedShape = RoundedCornerShape(2.dp)
val extraMediumCornerShape: CornerBasedShape = RoundedCornerShape(16.dp)
val mediumCornerShape: CornerBasedShape = RoundedCornerShape(18.dp)
val maxCornerShape: CornerBasedShape = RoundedCornerShape(20.dp)