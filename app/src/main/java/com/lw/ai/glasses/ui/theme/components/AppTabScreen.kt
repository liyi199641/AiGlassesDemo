

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@SuppressLint("AutoboxingStateCreation")
@Composable
fun AppTabScreen(
    tabs: List<Int>,
    content: List<@Composable () -> Unit>,
    modifier: Modifier = Modifier,
    selectedTabColor: Color = TextPrimary,    // 选中的Tab的颜色
    unselectedTabColor: Color = TextTertiary, // 未选中的Tab的颜色
    indicatorColor: Color = Primary, // 自定义指示器颜色
    isWeight: Boolean = false
) {
    // 管理当前选择的 Tab 索引
//    val selectedTabIndex = remember { mutableStateOf(0) }

    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val coroutineScope = rememberCoroutineScope()
    val selectedTabIndex = pagerState.currentPage


    Scaffold(
        topBar = {
            if (isWeight) {
                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    modifier = modifier,
                    containerColor = PrimaryBackground, // TabRow 背景色
                    divider = {},
                    indicator = { tabPositions ->
                        // 自定义指示器
                        TabRowDefaults.PrimaryIndicator(
                            modifier = Modifier
                                .tabIndicatorOffset(tabPositions[selectedTabIndex]) // 设置指示器位置
                                .width(12.dp) // 设置指示器宽度
                                .height(3.dp), // 设置指示器高度
                            color = indicatorColor, // 使用 color 参数设置指示器颜色
                        )
                    }
                ) {
                    tabs.forEachIndexed { index, tabTitle ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            },
                            text = {
                                AppText(
                                    textRes = tabTitle,
                                    modifier = Modifier,
                                    includeFontPadding = false,
                                    textColor = if (selectedTabIndex == index) selectedTabColor else unselectedTabColor,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            },
                        )
                    }
                }
            } else {
                ScrollableTabRow(
                    selectedTabIndex = selectedTabIndex,
                    modifier = modifier,
                    edgePadding = 0.dp,
                    containerColor = PrimaryBackground, // TabRow 背景色
                    divider = {},
                    indicator = { tabPositions ->
                        // 自定义指示器
                        TabRowDefaults.PrimaryIndicator(
                            modifier = Modifier
                                .tabIndicatorOffset(tabPositions[selectedTabIndex]) // 设置指示器位置
                                .width(12.dp) // 设置指示器宽度
                                .height(3.dp), // 设置指示器高度

                            color = indicatorColor, // 使用 color 参数设置指示器颜色
                        )
                    }
                ) {
                    tabs.forEachIndexed { index, tabTitle ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(index) // 点击 Tab 时，滚动到对应的页面
                                }
                            },
                            text = {
                                AppText(
                                    textRes = tabTitle,
                                    modifier = Modifier,
                                    textColor = if (selectedTabIndex == index) selectedTabColor else unselectedTabColor,
                                    includeFontPadding = false,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                    )
                                )
                            },
                        )
                    }
                }
            }

        }
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .background(PrimaryBackground)
                .padding(innerPadding),
            beyondViewportPageCount = kotlin.math.max(
                PagerDefaults.BeyondViewportPageCount,
                tabs.size
            )
        ) {
            Box(
                modifier = Modifier.fillMaxSize(), // 让 Box 填满整个页面
                contentAlignment = Alignment.TopStart // 内容从顶部左侧开始对齐 (或者 Alignment.TopCenter, Alignment.TopEnd)
            ) {
                content[it]()
            }

        }

    }
}
