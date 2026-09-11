

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max

@Composable
fun TopShapeWithDynamicHeightSlant(
    modifier: Modifier = Modifier,
    leftHeightOffsetDp: Dp = 15.dp,
    centralSlantHorizontalLength: Dp = 20.dp,
    centralSlantHorizontalOffsetDp: Dp = 0.dp, // 新增参数：水平偏移量
    gradientColors: List<Color>
) {
    Canvas(modifier = modifier) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        val leftHeightOffsetPx = leftHeightOffsetDp.toPx()
        val slantWidthPx = centralSlantHorizontalLength.toPx()
        val slantHorizontalOffsetPx = centralSlantHorizontalOffsetDp.toPx() // 新增：转换水平偏移量为Px

        val rightHeightPx = canvasHeight
        val leftHeightPx = canvasHeight + leftHeightOffsetPx

        // --- 计算中间斜线段在X轴上的起始和结束坐标 (考虑水平偏移) ---
        // 1. 计算斜线段中心点的理想位置 (无偏移时是画布中心)
        val idealSlantCenterX = canvasWidth / 2f
        // 2. 应用水平偏移量得到实际的斜线段中心点X坐标
        val actualSlantCenterX = idealSlantCenterX + slantHorizontalOffsetPx
        // 3. 根据实际中心点和斜线宽度计算起始和结束X坐标
        val slantStartX = actualSlantCenterX - (slantWidthPx / 2f)
        val slantEndX = actualSlantCenterX + (slantWidthPx / 2f)


        // --- 安全性检查与备用路径 (保持不变，但斜线位置会受新计算影响) ---
        // 额外检查：确保斜线段不会完全移出画布，虽然路径裁剪会自动处理一部分
        if (slantEndX <= 0f || slantStartX >= canvasWidth || // 斜线完全在画布左边或右边
            slantWidthPx <= 0 || leftHeightPx < 0f
        ) {
            // （可选）可以细化备用逻辑，例如如果只是部分移出，是裁剪还是报错
            // 这里简单地使用之前的备用路径逻辑
            val fallbackPath = Path().apply {
                moveTo(0f, 0f)
                lineTo(canvasWidth, 0f)
                lineTo(canvasWidth, rightHeightPx)
                lineTo(0f, if (leftHeightPx >= 0f) leftHeightPx else 0f)
                close()
            }
            drawPath(fallbackPath, color = if (gradientColors.isNotEmpty()) gradientColors.first() else Color.Gray)
            return@Canvas
        }


        // --- 定义主要形状的路径 (路径点会根据新的 slantStartX 和 slantEndX 调整) ---
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(canvasWidth, 0f)

            // 从右下角开始画到底部右侧的水平线段，直到斜线的右端点 (slantEndX)
            // 但要确保不画到画布外部或负坐标
            lineTo(canvasWidth, rightHeightPx) // 到画布右下角
            if (slantEndX < canvasWidth) { // 如果斜线的右端在画布内且不在最右边
                lineTo(slantEndX, rightHeightPx)
            } else {
                // 如果斜线右端超出或等于画布宽度，则右侧没有水平段，或者水平段为0
                // 直接连接到 (canvasWidth, rightHeightPx) 即可，上一步已完成
            }


            // 中间斜线段
            // 我们需要确保斜线绘制在画布内，或者被正确裁剪
            // Path的lineTo会自动处理裁剪，所以我们直接使用计算出的坐标
            lineTo(slantStartX.coerceIn(0f, canvasWidth), leftHeightPx) // 确保X在画布内
            // (上面的 lineTo(slantEndX, rightHeightPx) 应该用 coerceIn 吗? 考虑边缘情况)
            // 修正：上面的 lineTo(slantEndX, rightHeightPx) 应该是从 (canvasWidth, rightHeightPx) 开始的，
            // 所以如果 slantEndX > canvasWidth，它会画到 canvasWidth。如果 slantEndX < 0, 则会画到 0。
            // 让我们重新思考底部轮廓的绘制顺序以更好地处理偏移

            // 重构路径绘制逻辑以更好地处理偏移和边缘情况
            rewind() // 清除之前的路径点

            moveTo(0f, 0f) // 1. 左上角
            lineTo(canvasWidth, 0f) // 2. 右上角
            lineTo(canvasWidth, rightHeightPx) // 3. 画布右下角 (也是右侧水平底部的最右端)

            // 4. 右侧水平底部 (从右到左，直到斜线右端或画布左边缘)
            //    如果 slantEndX 在画布内，并且大于 slantStartX (即斜线有宽度且位置合理)
            if (slantEndX > slantStartX && slantEndX <= canvasWidth) {
                lineTo(slantEndX, rightHeightPx)
            } else if (slantEndX > slantStartX && slantEndX > canvasWidth && slantStartX < canvasWidth) {
                // 斜线部分超出右边界，但起点在画布内，则右侧水平段不存在，直接画到画布右边缘的 rightHeightPx
                // (上一步lineTo(canvasWidth, rightHeightPx)已经处理了这一点)
            }
            // 如果斜线完全在画布左侧或无效，则右侧水平线会一直画到画布最左边（这由后续步骤覆盖）


            // 5. 中间斜线段 (连接右侧水平底和左侧水平底)
            //    确保斜线的两个X锚点都在合理的范围内
            val actualDrawSlantEndX = slantEndX.coerceIn(0f, canvasWidth)
            val actualDrawSlantStartX = slantStartX.coerceIn(0f, canvasWidth)

            // 只有当斜线实际投影宽度大于0时才绘制斜线，否则可能是垂直线或点
            if (actualDrawSlantEndX > actualDrawSlantStartX) {
                // 从 (actualDrawSlantEndX, rightHeightPx) 连接到 (actualDrawSlantStartX, leftHeightPx)
                // 但我们需要确保是从正确的Y值开始。
                // 如果 slantEndX > canvasWidth，那么之前的点是 (canvasWidth, rightHeightPx)
                // 如果 slantEndX < 0, 那么之前的点是 (0, rightHeightPx) [虽然这种情况我们可能想避免]

                // 为了简化，我们假设路径是从(canvasWidth, rightHeightPx)开始向左构建底部
                // Path会从最后一个点连接。
                // 确保我们从正确的 (X, rightHeightPx) 开始斜线
                if (actualDrawSlantEndX <= canvasWidth && actualDrawSlantEndX > 0) { // 确保斜线右端在画布内
                    lineTo(actualDrawSlantEndX, rightHeightPx) // 从 (canvasWidth, rightHeightPx) 到 (actualDrawSlantEndX, rightHeightPx)
                }
                lineTo(actualDrawSlantStartX, leftHeightPx) // 斜向 (actualDrawSlantStartX, leftHeightPx)

            } else if (actualDrawSlantEndX <= actualDrawSlantStartX && actualDrawSlantStartX > 0) {
                // 如果斜线宽度为0或负（由于偏移或长度设置），或者完全在左边
                // 此时可能没有真正的“斜线”，或者它退化成了一条垂直线在 slantStartX
                // 我们从 (actualDrawSlantStartX, rightHeightPx) 画到 (actualDrawSlantStartX, leftHeightPx)
                lineTo(actualDrawSlantStartX, rightHeightPx) // 先到这个X点的右高度
                lineTo(actualDrawSlantStartX, leftHeightPx) // 再到这个X点的左高度
            }


            // 6. 左侧水平底部 (从斜线左端或画布右边缘向左，直到画布左边缘0)
            if (actualDrawSlantStartX >= 0f) {
                lineTo(0f, leftHeightPx)
            }

            close() // 7. 闭合路径回到左上角
        }


        // --- 使用垂直渐变画刷填充形状 (保持不变) ---
        if (gradientColors.isNotEmpty()) {
            val verticalGradientBrush = Brush.verticalGradient(
                colors = gradientColors,
                startY = 0f,
                endY = max(leftHeightPx, rightHeightPx)
            )
            drawPath(
                path = path,
                brush = verticalGradientBrush
            )
        } else {
            drawPath(path = path, color = Color.DarkGray)
        }
    }
}
