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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** 一次导出所需输入：源图、框选与摆正、预览容器尺寸、目标文件。 */
internal data class CropExport(
    val source: Bitmap,
    val crop: CropFrame,
    val containerWidth: Float,
    val containerHeight: Float,
    val target: File,
)

internal data class CropViewport(
    val imageWidth: Int,
    val imageHeight: Int,
    val containerWidth: Float,
    val containerHeight: Float,
    val quarterTurns: Int,
    val fineDegrees: Float,
)

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
    private const val CONTENT_PADDING = 0.05f
    private const val MIN_CONTENT_FRACTION = 0.08f
    private const val JPEG_QUALITY = 90
    private const val MAX_OUTPUT_DIM = 4096
    private const val STRAIGHT_ANGLE = 90f
    private const val UPSIDE_DOWN_ANGLE = 180f
    private const val TURNED_ANGLE = 270f
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
     * 再向外扩 5% 余量，避免阈值边缘和抗锯齿笔画被裁掉。只是启发式建议，用户可拖动四角调整；识别只看导出图。
     */
    fun suggest(bitmap: Bitmap): RectF {
        val width = ANALYSIS_WIDTH
        val height = max(1, bitmap.height * width / bitmap.width)
        val scaled = Bitmap.createScaledBitmap(bitmap, width, height, false)
        val pixels = IntArray(width * height)
        try {
            scaled.getPixels(pixels, 0, width, 0, 0, width, height)
        } finally {
            // 源宽恰为 ANALYSIS_WIDTH 时 createScaledBitmap 返回源对象，不能回收调用方持有的图。
            if (scaled !== bitmap) scaled.recycle()
        }
        val background = borderMedian(pixels, width, height)
        val threshold = (background - INK_LUMINANCE_DROP).coerceAtLeast(MIN_INK_LUMINANCE)
        val ink = BooleanArray(pixels.size) { index -> luminance(pixels[index]) < threshold }
        val rect = inkBounds(ink, width, height)
        return expand(rect, CONTENT_PADDING)
    }

    /** 导出框内画面；quarterTurns 为 90° 整转，fineDegrees 为「摆正」微调。 */
    fun export(request: CropExport): Boolean {
        val crop = request.crop.toRectF()
        if (cropIsEmpty(crop) || request.containerWidth <= 0f || request.containerHeight <= 0f) {
            return false
        }
        val source = request.source
        val cropWidth = crop.width() * request.containerWidth
        val cropHeight = crop.height() * request.containerHeight
        val outputScale = min(1f, MAX_OUTPUT_DIM / max(cropWidth, cropHeight))
        val outputWidth = (cropWidth * outputScale).roundToInt().coerceAtLeast(1)
        val outputHeight = (cropHeight * outputScale).roundToInt().coerceAtLeast(1)
        val output = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(source, CropDisplayGeometry.exportMatrix(request, outputScale), null)
        val written =
            try {
                request.target.outputStream().use { output.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            } catch (_: IOException) {
                false
            }
        output.recycle()
        return written
    }

    internal fun applyExifOrientation(
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
                    ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(UPSIDE_DOWN_ANGLE)
                    ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(TURNED_ANGLE)
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
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            bitmap.recycle()
            rotated
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
        val hasColumns = left >= 0 && right > left
        val hasRows = top >= 0 && bottom > top
        return if (hasColumns && hasRows) {
            RectF(
                left.toFloat() / width,
                top.toFloat() / height,
                (right + 1).toFloat() / width,
                (bottom + 1).toFloat() / height,
            )
        } else {
            RectF(EDGE_INSET, EDGE_INSET, 1f - EDGE_INSET, 1f - EDGE_INSET)
        }
    }

    private fun expand(
        rect: RectF,
        padding: Float,
    ): RectF {
        val paddingX = rect.width() * padding
        val paddingY = rect.height() * padding
        val expanded =
            RectF(
                (rect.left - paddingX).coerceIn(0f, 1f),
                (rect.top - paddingY).coerceIn(0f, 1f),
                (rect.right + paddingX).coerceIn(0f, 1f),
                (rect.bottom + paddingY).coerceIn(0f, 1f),
            )
        return if (expanded.width() < MIN_CONTENT_FRACTION || expanded.height() < MIN_CONTENT_FRACTION) {
            RectF(EDGE_INSET, EDGE_INSET, 1f - EDGE_INSET, 1f - EDGE_INSET)
        } else {
            expanded
        }
    }

    private fun luminance(color: Int): Float =
        Color.red(color) * LUMINANCE_RED + Color.green(color) * LUMINANCE_GREEN + Color.blue(color) * LUMINANCE_BLUE
}

internal object CropDisplayGeometry {
    private const val STRAIGHT_ANGLE = 90f
    private const val MATRIX_SCALE_X = 0
    private const val MATRIX_SKEW_Y = 1
    private const val MATRIX_TRANSLATE_X = 2
    private const val MATRIX_SKEW_X = 3
    private const val MATRIX_SCALE_Y = 4
    private const val MATRIX_TRANSLATE_Y = 5

    fun fitScale(view: CropViewport): Float {
        val swapped = view.quarterTurns % 2 != 0
        val displayWidth = if (swapped) view.imageHeight.toFloat() else view.imageWidth.toFloat()
        val displayHeight = if (swapped) view.imageWidth.toFloat() else view.imageHeight.toFloat()
        val radians = Math.toRadians(view.fineDegrees.toDouble())
        val cosine = abs(cos(radians)).toFloat()
        val sine = abs(sin(radians)).toFloat()
        val rotatedWidth = displayWidth * cosine + displayHeight * sine
        val rotatedHeight = displayWidth * sine + displayHeight * cosine
        return min(view.containerWidth / rotatedWidth, view.containerHeight / rotatedHeight)
    }

    fun displayMatrix(view: CropViewport): Matrix = Matrix().apply { setValues(displayValues(view)) }

    private fun displayValues(view: CropViewport): FloatArray {
        val scale = fitScale(view)
        val radians = Math.toRadians((view.quarterTurns * STRAIGHT_ANGLE + view.fineDegrees).toDouble())
        val cosine = cos(radians).toFloat()
        val sine = sin(radians).toFloat()
        val a = scale * cosine
        val b = -scale * sine
        val c = scale * sine
        val d = scale * cosine
        return floatArrayOf(
            a,
            b,
            view.containerWidth / 2f - a * view.imageWidth / 2f - c * view.imageHeight / 2f,
            c,
            d,
            view.containerHeight / 2f - b * view.imageWidth / 2f - d * view.imageHeight / 2f,
            0f,
            0f,
            1f,
        )
    }

    fun sourceCropToDisplay(
        crop: RectF,
        view: CropViewport,
    ): RectF {
        val values = displayValues(view)
        val topLeft = mapPoint(crop.left * view.imageWidth, crop.top * view.imageHeight, values)
        val topRight = mapPoint(crop.right * view.imageWidth, crop.top * view.imageHeight, values)
        val bottomLeft = mapPoint(crop.left * view.imageWidth, crop.bottom * view.imageHeight, values)
        val bottomRight = mapPoint(crop.right * view.imageWidth, crop.bottom * view.imageHeight, values)
        val left = minOf(topLeft[0], topRight[0], bottomLeft[0], bottomRight[0])
        val top = minOf(topLeft[1], topRight[1], bottomLeft[1], bottomRight[1])
        val right = maxOf(topLeft[0], topRight[0], bottomLeft[0], bottomRight[0])
        val bottom = maxOf(topLeft[1], topRight[1], bottomLeft[1], bottomRight[1])
        return RectF(
            (left / view.containerWidth).coerceIn(0f, 1f),
            (top / view.containerHeight).coerceIn(0f, 1f),
            (right / view.containerWidth).coerceIn(0f, 1f),
            (bottom / view.containerHeight).coerceIn(0f, 1f),
        )
    }

    private fun mapPoint(
        x: Float,
        y: Float,
        values: FloatArray,
    ): FloatArray =
        floatArrayOf(
            values[MATRIX_SCALE_X] * x + values[MATRIX_SKEW_X] * y + values[MATRIX_TRANSLATE_X],
            values[MATRIX_SKEW_Y] * x + values[MATRIX_SCALE_Y] * y + values[MATRIX_TRANSLATE_Y],
        )

    fun displayedImageBounds(view: CropViewport): RectF = sourceCropToDisplay(RectF(0f, 0f, 1f, 1f), view)

    fun remapCrop(
        crop: RectF,
        from: CropViewport,
        to: CropViewport,
    ): RectF {
        val fromBounds = displayedImageBounds(from)
        val toBounds = displayedImageBounds(to)
        if (!hasArea(fromBounds) || !hasArea(toBounds)) return crop
        val relative =
            RectF(
                ((crop.left - fromBounds.left) / fromBounds.width()).coerceIn(0f, 1f),
                ((crop.top - fromBounds.top) / fromBounds.height()).coerceIn(0f, 1f),
                ((crop.right - fromBounds.left) / fromBounds.width()).coerceIn(0f, 1f),
                ((crop.bottom - fromBounds.top) / fromBounds.height()).coerceIn(0f, 1f),
            )
        return RectF(
            toBounds.left + relative.left * toBounds.width(),
            toBounds.top + relative.top * toBounds.height(),
            toBounds.left + relative.right * toBounds.width(),
            toBounds.top + relative.bottom * toBounds.height(),
        )
    }

    private fun hasArea(bounds: RectF): Boolean = bounds.width() > 0f && bounds.height() > 0f

    internal fun exportMatrix(
        request: CropExport,
        outputScale: Float,
    ): Matrix =
        Matrix().apply {
            val view =
                CropViewport(
                    request.source.width,
                    request.source.height,
                    request.containerWidth,
                    request.containerHeight,
                    request.crop.quarterTurns,
                    request.crop.fineDegrees,
                )
            val values = displayValues(view)
            values[MATRIX_SCALE_X] *= outputScale
            values[MATRIX_SKEW_Y] *= outputScale
            values[MATRIX_SKEW_X] *= outputScale
            values[MATRIX_SCALE_Y] *= outputScale
            values[MATRIX_TRANSLATE_X] =
                (values[MATRIX_TRANSLATE_X] - request.crop.left * request.containerWidth) * outputScale
            values[MATRIX_TRANSLATE_Y] =
                (values[MATRIX_TRANSLATE_Y] - request.crop.top * request.containerHeight) * outputScale
            setValues(values)
        }
}
