
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lw.ai.glasses.R
import com.lw.top.lib_core.data.model.response.ApiResult

@Composable
fun <T> BaseScreen(
    modifier: Modifier = Modifier,
    apiResult: ApiResult<T>, // 直接接收 ApiResult
    onRetry: () -> Unit,
    topBar: @Composable (() -> Unit)? = null,
    bottomBar: @Composable (() -> Unit)? = null,
    successContent: @Composable (data: T) -> Unit,
    emptyContent: @Composable (() -> Unit)? = null,
    loadingContent: @Composable (() -> Unit)? = null,
    errorContent: @Composable ((error: ApiResult.Error) -> Unit)? = null,
    checkEmptyCondition: (T?) -> Boolean = { data -> data == null || (data is Collection<*> && data.isEmpty()) || (data is Map<*, *> && data.isEmpty()) }
) {

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { topBar?.invoke() },
        bottomBar = { bottomBar?.invoke() }
    ) { innerPadding ->
        val statusBarPaddingValues = WindowInsets.statusBars.asPaddingValues()
        val navigationBarPaddingValues = WindowInsets.navigationBars.asPaddingValues()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = innerPadding.calculateStartPadding(LocalLayoutDirection.current),
                    end = innerPadding.calculateEndPadding(LocalLayoutDirection.current)
                )
                .padding(
                    top = if (topBar == null) {
//                        statusBarPaddingValues.calculateTopPadding()
                        0.dp
                    } else {
                        innerPadding.calculateTopPadding()
                    }
                )
                .padding(
                    bottom = if (bottomBar == null) {
//                        navigationBarPaddingValues.calculateBottomPadding()
//                        innerPadding.calculateBottomPadding()
                        0.dp
                    } else {
//                        navigationBarPaddingValues.calculateBottomPadding()
                        innerPadding.calculateBottomPadding()
                    }
                )
        ) {
//            LogUtils.dTag(TAG, apiResult)
            when (apiResult) {
                is ApiResult.Loading -> {
                    loadingContent?.invoke() ?: DefaultLoadingContent()
                }

                is ApiResult.Success -> {
                    if (checkEmptyCondition(apiResult.data)) {
                        emptyContent?.invoke() ?: DefaultEmptyContent(onRetry = onRetry)
                    } else {
                        apiResult.data?.let { successContent(it) }
                            ?: run {
                                DefaultEmptyContent(
                                    message = "Unexpected null data in Success state.",
                                    onRetry = onRetry
                                )
                            }
                    }
                }

                is ApiResult.Error -> {
                    errorContent?.invoke(apiResult) ?: DefaultErrorContent(
                        errorMessage = apiResult.msg ?: apiResult.exception.message
                        ?: "An unknown error occurred.",
                        onRetry = onRetry
                    )
                }

                is ApiResult.Empty -> {
                    emptyContent?.invoke() ?: DefaultEmptyContent(onRetry = onRetry)
                }
            }
        }
    }
}

@Composable
fun DefaultLoadingContent(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
fun DefaultErrorContent(
    modifier: Modifier = Modifier,
    errorMessage: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AppIcon(painterResId = 0, modifier = Modifier.size(115.dp))


        AppText(
            textRes = R.string.data_request_failed,
            style = MaterialTheme.typography.bodyLarge,
            textColor = TextPrimary
        )
        Spacer(modifier = Modifier.height(10.dp))

        AppText(
            textRes = R.string.data_request_hint,
            style = MaterialTheme.typography.bodySmall,
            textColor = TextTertiary,
            modifier = Modifier.fillMaxWidth(0.7f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(30.dp))

        AppButton(
            textRes = R.string.refresh,
            onClick = onRetry,
            height = 44,
            width = 128,
            buttonType = ButtonType.Custom,
            customColor = LineBorder,
            customPressedColor = TextDisabled,
            textStyle = MaterialTheme.typography.bodySmall,
            shape = 8
        )
    }
}

@Composable
fun DefaultEmptyContent(
    modifier: Modifier = Modifier,
    message: String = "",
    onRetry: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Spacer(modifier = Modifier.height(122.dp))
//        AppIcon(painterResId = R.mipmap.ic_order_empty, modifier = Modifier.size(115.dp))

        AppText(
            textRes = R.string.no_data_available,
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(modifier = Modifier.height(10.dp))

        AppText(
            textStr = message,
            modifier = Modifier.fillMaxWidth(0.57f),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            textColor = TextTertiary
        )
    }
}