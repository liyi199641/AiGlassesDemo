
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lw.ai.glasses.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.libpag.PAGFile

@Composable
fun LoadingScreen() {
    val context = LocalContext.current
    var loadedPAGFile by remember { mutableStateOf<PAGFile?>(null) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            loadedPAGFile = PAGFile.Load(context.assets, "")
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        PAGAnimation(
            pagFile = loadedPAGFile,
            modifier = Modifier.size(66.dp)
        )
        AppText(textRes = R.string.loading, style = MaterialTheme.typography.bodyMedium)
    }
}