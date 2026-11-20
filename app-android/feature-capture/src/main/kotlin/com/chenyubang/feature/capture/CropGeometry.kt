package com.chenyubang.feature.capture

import android.graphics.RectF
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import kotlin.math.hypot

internal const val CORNER_LEFT_TOP = 0
internal const val CORNER_RIGHT_TOP = 1
internal const val CORNER_LEFT_BOTTOM = 2
internal const val CORNER_RIGHT_BOTTOM = 3

internal fun angleLabel(fineDegrees: Float): String = if (fineDegrees > 0f) "+$fineDegrees°" else "$fineDegrees°"

internal fun cropIsEmpty(crop: RectF): Boolean = crop.width() <= 0f || crop.height() <= 0f

internal fun cornerPoints(
    crop: RectF,
    size: IntSize,
): List<Offset> {
    val left = crop.left * size.width
    val right = crop.right * size.width
    val top = crop.top * size.height
    val bottom = crop.bottom * size.height
    return listOf(
        Offset(left, top),
        Offset(right, top),
        Offset(left, bottom),
        Offset(right, bottom),
    )
}

internal fun nearestCorner(
    crop: RectF,
    size: IntSize,
    point: Offset,
    hitRadius: Float,
): Int? {
    val distances = cornerPoints(crop, size).map { hypot(it.x - point.x, it.y - point.y) }
    val best = distances.indices.minByOrNull { distances[it] } ?: return null
    return if (distances[best] <= hitRadius) best else null
}

internal fun moveCorner(
    crop: RectF,
    corner: Int,
    drag: Offset,
    size: IntSize,
    minSizePx: Float,
): RectF {
    val minWidth = (minSizePx / size.width).coerceAtMost(1f)
    val minHeight = (minSizePx / size.height).coerceAtMost(1f)
    val dx = drag.x / size.width
    val dy = drag.y / size.height
    val moved = RectF(crop)
    when (corner) {
        CORNER_LEFT_TOP -> {
            moved.left = (crop.left + dx).coerceIn(0f, crop.right - minWidth)
            moved.top = (crop.top + dy).coerceIn(0f, crop.bottom - minHeight)
        }
        CORNER_RIGHT_TOP -> {
            moved.right = (crop.right + dx).coerceIn(crop.left + minWidth, 1f)
            moved.top = (crop.top + dy).coerceIn(0f, crop.bottom - minHeight)
        }
        CORNER_LEFT_BOTTOM -> {
            moved.left = (crop.left + dx).coerceIn(0f, crop.right - minWidth)
            moved.bottom = (crop.bottom + dy).coerceIn(crop.top + minHeight, 1f)
        }
        CORNER_RIGHT_BOTTOM -> {
            moved.right = (crop.right + dx).coerceIn(crop.left + minWidth, 1f)
            moved.bottom = (crop.bottom + dy).coerceIn(crop.top + minHeight, 1f)
        }
        else -> Unit
    }
    return moved
}
