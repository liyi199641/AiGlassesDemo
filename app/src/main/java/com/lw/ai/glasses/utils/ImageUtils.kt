/**
 * @name ImageUtils
 * @class name：com.lw.ai.glasses.utils
 * @author ly
 * @time 2026/1/26 16:52
 * @change
 * @chang time
 * @class describe
 */
package com.lw.ai.glasses.utils

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri


// 辅助函数：获取图片尺寸（宽、高）
 fun getImageDimensions(context: Context, uri: Uri): Pair<Int, Int> {
    context.contentResolver.openInputStream(uri)?.use { inputStream ->
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true // 只解码尺寸，不加载整张图片
        }
        BitmapFactory.decodeStream(inputStream, null, options)
        return Pair(options.outWidth, options.outHeight)
    }
    return Pair(0, 0)
}