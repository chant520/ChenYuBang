package com.chenyubang.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlin.math.max

/** 解码 / 缩放 / 裁切；像素一律按 ARGB_8888 IntArray 交给张量层。 */
internal object OcrBitmapOps {
    private const val DECODE_MAX_SIDE = 4096

    fun decode(path: String): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > DECODE_MAX_SIDE) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return checkNotNull(BitmapFactory.decodeFile(path, options)) { "unreadable image: $path" }
    }

    fun upscale(source: Bitmap): Bitmap {
        val (width, height) = OcrUpscale.targetSize(source.width, source.height)
        return scaled(source, width, height)
    }

    fun scaled(
        source: Bitmap,
        width: Int,
        height: Int,
    ): Bitmap =
        if (width == source.width && height == source.height) {
            source
        } else {
            Bitmap.createScaledBitmap(source, width, height, true)
        }

    fun pixels(bitmap: Bitmap): IntArray {
        val width = bitmap.width
        val height = bitmap.height
        return IntArray(width * height).also { bitmap.getPixels(it, 0, width, 0, 0, width, height) }
    }

    /**
     * 缩放取像素并在用完后回收临时位图；尺寸一致时 [scaled] 返回原图，此处不回收调用方持有的图。
     */
    inline fun <T> scaledPixels(
        source: Bitmap,
        width: Int,
        height: Int,
        compute: (IntArray) -> T,
    ): T {
        val scaled = scaled(source, width, height)
        return try {
            compute(pixels(scaled))
        } finally {
            if (scaled !== source) scaled.recycle()
        }
    }

    /** 框坐标是上采样图尺度；越界、空框直接返回 null，由调用方丢行。 */
    fun crop(
        source: Bitmap,
        box: OcrDetPostProcess.Box,
    ): Bitmap? {
        val emptySource = source.width <= 0 || source.height <= 0
        val emptyBox = box.width <= 0f || box.height <= 0f
        if (emptySource || emptyBox) return null
        val left = box.left.toInt().coerceIn(0, source.width - 1)
        val top = box.top.toInt().coerceIn(0, source.height - 1)
        val right = box.right.toInt().coerceIn(left + 1, source.width)
        val bottom = box.bottom.toInt().coerceIn(top + 1, source.height)
        return Bitmap.createBitmap(source, left, top, right - left, bottom - top)
    }
}
