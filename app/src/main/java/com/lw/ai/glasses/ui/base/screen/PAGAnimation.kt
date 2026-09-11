
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.libpag.PAGFile
import org.libpag.PAGScaleMode
import org.libpag.PAGView


@Composable
fun PAGAnimation(
    modifier: Modifier = Modifier,
    pagFile: PAGFile?,
    isPlaying: Boolean = true,
    autoPlay: Boolean = true,
    repeatCount: Int = 0, // 0表示无限循环
    scaleMode: Int = PAGScaleMode.LetterBox,
    onAnimationStart: () -> Unit = {},
    onAnimationEnd: () -> Unit = {},
    onAnimationCancel: () -> Unit = {},
    onAnimationRepeat: () -> Unit = {},
    onAnimationUpdate: () -> Unit = {},
) {

    if (pagFile == null) {
        return
    }

    val pagViewListener = remember {
        object : PAGView.PAGViewListener {
            override fun onAnimationStart(view: PAGView) {
                onAnimationStart()
            }

            override fun onAnimationEnd(view: PAGView) {
                onAnimationEnd()
            }

            override fun onAnimationCancel(view: PAGView) {
                onAnimationCancel()
            }

            override fun onAnimationRepeat(view: PAGView) {
                onAnimationRepeat()
            }

            override fun onAnimationUpdate(view: PAGView?) {
                onAnimationUpdate()

            }
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            PAGView(context).apply {
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
                composition = pagFile
                setScaleMode(scaleMode)
                setRepeatCount(repeatCount)
                flush()
                addListener(pagViewListener)
                if (autoPlay && isPlaying) {
                    play()
                }
            }
        },
        update = { view ->
            if (view.composition != pagFile) {
                view.composition = pagFile
            }
            if (view.scaleMode() != scaleMode) {
                view.setScaleMode(scaleMode)
            }
            if (view.repeatCount() != repeatCount) {
                view.setRepeatCount(repeatCount)
            }
            if (isPlaying && !view.isPlaying) view.play() else if (!isPlaying && view.isPlaying) view.pause()
            // view.setProgress(currentProgress) // 动控制进度
        },
        onRelease = { view ->
            view.stop()
            view.removeListener(pagViewListener)
            view.freeCache()
            view.composition = null
        }
    )

}