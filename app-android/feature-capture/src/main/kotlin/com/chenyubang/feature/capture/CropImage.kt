package com.chenyubang.feature.capture

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.RectF
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.IOException
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * P18 图片管线（纯本机，零联网）：读取 P17 交来的 JPEG、按内容给一次初始框、
 * 按旋转/微调/框选导出裁切图。导出图只保留框内内容，交给 P19 识别。
 */
internal object CropImage {
    private const val MAX_DECODE_DIM = 3200
    private const val ANALYSIS_WIDTH = 360
    private const val ANALYSIS_COLUMN_PERCENT = 100
    private const val ANALYSIS_MIN_INK = 2
    private const val INK_LUMINANCE_DROP = 60f
    private const val MIN_INK_LUMINANCE = 40f
    private const val FALLBACK_BACKGROUND = 200f
    private const val EDGE_INSET = 0.06f
    private const val CONTENT_MARGIN = 0.05f
    private const val MIN_CONTENT_FRACTION = 0.08f
    private const val JPEG_QUALITY = 90
    private const val MAX_OUTPUT_DIM = 4096
    private const val STRAIGHT_ANGLE = 90f
    private const val BORDER_SAMPLES = 40
    private const val LUMINANCE_RED = 0.299f
    private const val LUMINANCE_GREEN = 0.587f
    private const val LUMINANCE_BLUE = 0.114f

    fun load(path: String): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > MAX_DECODE_DIM) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = BitmapFactory.decodeFile(path, options) ?: return null
        return applyExifOrientation(path, bitmap)
    }

    /**
     * 初始框建议：按画面亮度找出文字/纸张内容的包围盒（深色像素的行列投影），
     * 再向内留 5% 余量。只是启发式建议，用户可拖动四角调整；识别只看导出图。
     */
    fun suggest(bitmap: Bitmap): RectF {
        val width = ANALYSIS_WIDTH
        val height = max(1, bitmap.height * width / bitmap.width)
        val scaled = Bitmap.createScaledBitmap(bitmap, width, height, false)
        val pixels = IntArray(width * height)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)
        scaled.recycle()
        val background = borderMedian(pixels, width, height)
        val threshold = (background - INK_LUMINANCE_DROP).coerceAtLeast(MIN_INK_LUMINANCE)
        val ink = BooleanArray(pixels.size) { index -> luminance(pixels[index]) < threshold }
        val rect = inkBounds(ink, width, height)
        return inset(rect, CONTENT_MARGIN)
    }

    fun fitScale(
        imageWidth: Int,
        imageHeight: Int,
        containerWidth: Float,
        containerHeight: Float,
        quarterTurns: Int,
    ): Float {
        val swapped = quarterTurns % 2 != 0
        val displayWidth = if (swapped) imageHeight.toFloat() else imageWidth.toFloat()
        val displayHeight = if (swapped) imageWidth.toFloat() else imageHeight.toFloat()
        return min(containerWidth / displayWidth, containerHeight / displayHeight)
    }

    /** 导出框内画面；quarterTurns 为 90° 整转，fineDegrees 为「摆正」微调。 */
    fun export(
        source: Bitmap,
        quarterTurns: Int,
        fineDegrees: Float,
        crop: RectF,
        containerWidth: Float,
        containerHeight: Float,
        target: File,
    ): Boolean {
        if (crop.width() <= 0f || crop.height() <= 0f || containerWidth <= 0f || containerHeight <= 0f) {
            return false
        }
        val scale = fitScale(source.width, source.height, containerWidth, containerHeight, quarterTurns)
        val cropWidth = crop.width() * containerWidth * scale
        val cropHeight = crop.height() * containerHeight * scale
        val shrink = min(1f, MAX_OUTPUT_DIM / max(cropWidth, cropHeight))
        val outputWidth = (cropWidth * shrink).roundToInt().coerceAtLeast(1)
        val outputHeight = (cropHeight * shrink).roundToInt().coerceAtLeast(1)
        val outputScale = scale * shrink
        val output = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.WHITE)
        val matrix = exportMatrix(crop, containerWidth, containerHeight, source, quarterTurns, fineDegrees, outputScale)
        canvas.drawBitmap(source, matrix, null)
        val written =
            try {
                target.outputStream().use { output.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            } catch (_: IOException) {
                false
            }
        output.recycle()
        return written
    }

    private fun exportMatrix(
        crop: RectF,
        containerWidth: Float,
        containerHeight: Float,
        source: Bitmap,
        quarterTurns: Int,
        fineDegrees: Float,
        scale: Float,
    ): Matrix =
        Matrix().apply {
            postTranslate(-source.width / 2f, -source.height / 2f)
            postScale(scale, scale)
            postRotate(quarterTurns * STRAIGHT_ANGLE + fineDegrees)
            postTranslate(containerWidth / 2f * scale, containerHeight / 2f * scale)
            postTranslate(-crop.left * containerWidth * scale, -crop.top * containerHeight * scale)
        }

    private fun applyExifOrientation(
        path: String,
        bitmap: Bitmap,
    ): Bitmap {
        val orientation =
            try {
                ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } catch (_: IOException) {
                ExifInterface.ORIENTATION_NORMAL
            }
        val matrix =
            Matrix().apply {
                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(STRAIGHT_ANGLE)
                    ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(STRAIGHT_ANGLE * 2)
                    ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(STRAIGHT_ANGLE * 3)
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
                    ExifInterface.ORIENTATION_TRANSPOSE -> {
                        postRotate(STRAIGHT_ANGLE)
                        postScale(-1f, 1f)
                    }
                    ExifInterface.ORIENTATION_TRANSVERSE -> {
                        postRotate(STRAIGHT_ANGLE)
                        postScale(1f, -1f)
                    }
                    else -> Unit
                }
            }
        return if (matrix.isIdentity) {
            bitmap
        } else {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }
    }

    private fun borderMedian(
        pixels: IntArray,
        width: Int,
        height: Int,
    ): Float {
        val step = max(1, width / BORDER_SAMPLES)
        val samples = ArrayList<Float>()
        for (x in 0 until width step step) {
            samples.add(luminance(pixels[x]))
            samples.add(luminance(pixels[(height - 1) * width + x]))
        }
        for (y in 0 until height step step) {
            samples.add(luminance(pixels[y * width]))
            samples.add(luminance(pixels[y * width + width - 1]))
        }
        samples.sort()
        return if (samples.isEmpty()) FALLBACK_BACKGROUND else samples[samples.size / 2]
    }

    private fun inkBounds(
        ink: BooleanArray,
        width: Int,
        height: Int,
    ): RectF {
        val columnMin = max(ANALYSIS_MIN_INK, width / ANALYSIS_COLUMN_PERCENT)
        val rowMin = max(ANALYSIS_MIN_INK, height / ANALYSIS_COLUMN_PERCENT)
        val columns = IntArray(width)
        val rows = IntArray(height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (ink[y * width + x]) {
                    columns[x]++
                    rows[y]++
                }
            }
        }
        val left = columns.indexOfFirst { it > columnMin }
        val right = columns.indexOfLast { it > columnMin }
        val top = rows.indexOfFirst { it > rowMin }
        val bottom = rows.indexOfLast { it > rowMin }
        return if (left < 0 || top < 0 || right <= left || bottom <= top) {
            RectF(EDGE_INSET, EDGE_INSET, 1f - EDGE_INSET, 1f - EDGE_INSET)
        } else {
            RectF(
                left.toFloat() / width,
                top.toFloat() / height,
                (right + 1).toFloat() / width,
                (bottom + 1).toFloat() / height,
            )
        }
    }

    private fun inset(
        rect: RectF,
        margin: Float,
    ): RectF {
        val marginX = rect.width() * margin
        val marginY = rect.height() * margin
        val insetRect =
            RectF(
                (rect.left + marginX).coerceIn(0f, 1f),
                (rect.top + marginY).coerceIn(0f, 1f),
                (rect.right - marginX).coerceIn(0f, 1f),
                (rect.bottom - marginY).coerceIn(0f, 1f),
            )
        return if (insetRect.width() < MIN_CONTENT_FRACTION || insetRect.height() < MIN_CONTENT_FRACTION) {
            RectF(EDGE_INSET, EDGE_INSET, 1f - EDGE_INSET, 1f - EDGE_INSET)
        } else {
            insetRect
        }
    }

    private fun luminance(color: Int): Float =
        Color.red(color) * LUMINANCE_RED + Color.green(color) * LUMINANCE_GREEN + Color.blue(color) * LUMINANCE_BLUE
}
