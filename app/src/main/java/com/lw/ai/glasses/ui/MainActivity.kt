package com.lw.ai.glasses.ui

import AppNavHost
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.lw.ai.glasses.ui.theme.AiGlassesTheme
import com.lw.ai.glasses.ui.theme.Primary
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AiGlassesTheme(
                dynamicColor = false
            ) {
                Surface(modifier = Modifier.Companion.fillMaxSize(), color = Primary) {
                    AppNavHost()
                }
            }
        }
//        lifecycleScope.launch {
//            GlassesManage.eventFlow().collect { event ->
//                when (event) {
//                    is CmdResultEvent.MediaFileCount -> ToastUtils.showLong("当前媒体文件数量为：${event.count}")
//                    else -> {}
//                }
//            }
//        }
    }
}