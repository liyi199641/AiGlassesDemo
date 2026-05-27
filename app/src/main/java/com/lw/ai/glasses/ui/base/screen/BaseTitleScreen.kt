package com.lw.ai.glasses.ui.base.screen
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.lw.ai.glasses.R
import com.lw.ai.glasses.ui.theme.PrimaryBackground
import com.lw.ai.glasses.ui.theme.components.AppText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BaseTitleScreen(
    title: String = "",
    showBack: Boolean = true,
    backText: String? = null,
    onNavigateBack: () -> Unit = {},
    containerColor: Color = PrimaryBackground,
    actions: @Composable RowScope.() -> Unit = {}
) {
    CenterAlignedTopAppBar(
//        modifier = Modifier.fillMaxWidth().height(44.dp),
        title = {
            AppText(
                textStr = title,
                style = MaterialTheme.typography.titleSmall
            )
        },
        navigationIcon = {
            if (showBack) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { onNavigateBack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = Color.White
                        )
                    }
                    if (backText != null) {
                        AppText(
                            textStr = backText,
                            modifier = Modifier.clickable { onNavigateBack() },
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = containerColor
        )
    )

}