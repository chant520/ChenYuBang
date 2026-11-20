package com.chenyubang.feature.capture

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chenyubang.core.ui.CChromeGlyph
import com.chenyubang.core.ui.CChromeIcon
import com.chenyubang.core.ui.tokens.Tokens
import kotlin.math.hypot
import kotlin.math.roundToInt

internal const val ROTATION_QUARTERS = 4
private const val QUARTER_DEGREES = 90f
private const val ANGLE_LIMIT = 15f
private const val ANGLE_STEP_DIVISIONS = 2f
private const val ANGLE_SLIDER_STEPS = 59
private const val CORNER_LEFT_TOP = 0
private const val CORNER_RIGHT_TOP = 1
private const val CORNER_LEFT_BOTTOM = 2
private const val CORNER_RIGHT_BOTTOM = 3

@Composable
internal fun CropHeader(
    onBack: () -> Unit,
    colors: Tokens.ColorSet,
) {
    Column(Modifier.fillMaxWidth().background(colors.page)) {
        Row(
            Modifier.fillMaxWidth().padding(
                start = Tokens.Space.pageX.dp,
                end = Tokens.Space.pageX.dp,
                top = Tokens.Space.cropHeadTop.dp,
                bottom = Tokens.Space.cropHeadBottom.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(Tokens.Space.hitMin.dp)
                    .clickable(onClick = onBack)
                    .semantics { contentDescription = "返回重拍" },
                contentAlignment = Alignment.Center,
            ) {
                CChromeGlyph(CChromeIcon.Back, colors.text2, Modifier.size(Tokens.Icon.n21.dp))
            }
            Spacer(Modifier.width(Tokens.Space.camTopGap.dp))
            Text(
                "裁切与摆正",
                color = colors.head,
                fontSize = Tokens.Font.n15.sp,
                fontWeight = FontWeight.W700,
                modifier = Modifier.weight(1f),
            )
            Text("第 2 步 / 共 5 步", color = colors.text3, fontSize = Tokens.Font.n11.sp)
        }
        Box(Modifier.fillMaxWidth().height(Tokens.Space.cropEdge.dp).background(colors.line))
    }
}

@Composable
internal fun CropCanvasBox(
    bitmap: Bitmap?,
    crop: RectF?,
    quarterTurns: Int,
    fineDegrees: Float,
    colors: Tokens.ColorSet,
    failed: Boolean,
    onCropChange: (RectF) -> Unit,
    onSizeChange: (IntSize) -> Unit,
    modifier: Modifier,
) {
    Box(
        modifier.clip(RoundedCornerShape(Tokens.Radius.r6.dp)).background(Tokens.Dark.cam)
            .onSizeChanged(onSizeChange),
        contentAlignment = Alignment.Center,
    ) {
        when {
            failed -> Text("图片读取失败，请返回重拍", color = Tokens.Dark.warnfg, fontSize = Tokens.Font.n12.sp)
            bitmap == null || crop == null ->
                Text(
                    "正在读取图片…",
                    color = Tokens.Dark.camhint,
                    fontSize = Tokens.Font.n12.sp,
                )
            else -> CropCanvas(bitmap, crop, quarterTurns, fineDegrees, colors, onCropChange)
        }
    }
}

@Composable
private fun CropCanvas(
    bitmap: Bitmap,
    crop: RectF,
    quarterTurns: Int,
    fineDegrees: Float,
    colors: Tokens.ColorSet,
    onCropChange: (RectF) -> Unit,
) {
    val density = LocalDensity.current
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val latestCrop by rememberUpdatedState(crop)
    var corner by remember { mutableStateOf<Int?>(null) }
    val minSizePx = with(density) { Tokens.Space.cropMin.dp.toPx() }
    val hitRadius = with(density) { Tokens.Space.hitMin.dp.toPx() }
    Canvas(
        Modifier.fillMaxSize().pointerInput(quarterTurns) {
            detectDragGestures(
                onDragStart = { offset -> corner = nearestCorner(latestCrop, size, offset, hitRadius) },
                onDragEnd = { corner = null },
                onDragCancel = { corner = null },
            ) { change, dragAmount ->
                change.consume()
                val current = corner
                if (current != null) onCropChange(moveCorner(latestCrop, current, dragAmount, size, minSizePx))
            }
        },
    ) {
        val scale = CropImage.fitScale(bitmap.width, bitmap.height, size.width, size.height, quarterTurns)
        withTransform({
            translate(size.width / 2f, size.height / 2f)
            rotate(quarterTurns * QUARTER_DEGREES + fineDegrees)
            scale(scale, scale)
            translate(-bitmap.width / 2f, -bitmap.height / 2f)
        }) {
            drawImage(image, filterQuality = FilterQuality.Medium)
        }
        drawCropOverlay(crop, colors)
    }
}

private fun DrawScope.drawCropOverlay(
    crop: RectF,
    colors: Tokens.ColorSet,
) {
    val left = crop.left * size.width
    val top = crop.top * size.height
    val width = crop.width() * size.width
    val height = crop.height() * size.height
    drawRect(
        color = colors.cropedge,
        topLeft = Offset(left, top),
        size = Size(width, height),
        style = Stroke(width = Tokens.Space.cropEdge.dp.toPx()),
    )
    val handle = Tokens.Space.cropHandle.dp.toPx()
    val stroke = Tokens.Space.cropHandleStroke.dp.toPx()
    drawCorner(left, top, 1f, 1f, handle, stroke, colors.warnfg)
    drawCorner(left + width, top, -1f, 1f, handle, stroke, colors.warnfg)
    drawCorner(left, top + height, 1f, -1f, handle, stroke, colors.warnfg)
    drawCorner(left + width, top + height, -1f, -1f, handle, stroke, colors.warnfg)
}

private fun DrawScope.drawCorner(
    x: Float,
    y: Float,
    dx: Float,
    dy: Float,
    length: Float,
    stroke: Float,
    color: Color,
) {
    drawLine(color, Offset(x, y), Offset(x + dx * length, y), stroke, cap = StrokeCap.Square)
    drawLine(color, Offset(x, y), Offset(x, y + dy * length), stroke, cap = StrokeCap.Square)
}

@Composable
internal fun CropActionRow(
    enabled: Boolean,
    onRotate: () -> Unit,
    onAdjust: () -> Unit,
    onRetake: () -> Unit,
    colors: Tokens.ColorSet,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Tokens.Space.cropRowGap.dp)) {
        CropGhostButton("旋转", enabled, onRotate, colors, Modifier.weight(1f))
        CropGhostButton("摆正", enabled, onAdjust, colors, Modifier.weight(1f))
        CropGhostButton("重拍", enabled, onRetake, colors, Modifier.weight(1f))
    }
}

@Composable
private fun CropGhostButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    colors: Tokens.ColorSet,
    modifier: Modifier,
) {
    Box(
        modifier
            .border(Tokens.Space.cropEdge.dp, colors.line, RoundedCornerShape(Tokens.Radius.r5.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (enabled) colors.text2 else colors.disfg,
            fontSize = Tokens.Font.n115.sp,
            modifier = Modifier.padding(vertical = Tokens.Space.ghostY.dp),
        )
    }
}

@Composable
internal fun AdjustPanel(
    fineDegrees: Float,
    colors: Tokens.ColorSet,
    onReset: () -> Unit,
    onFineChange: (Float) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(
            top = Tokens.Space.cropAdjustTop.dp,
            bottom = Tokens.Space.cropAdjustBottom.dp,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("微调", color = colors.text3, fontSize = Tokens.Font.n11.sp)
        Slider(
            value = fineDegrees,
            onValueChange = { onFineChange((it * ANGLE_STEP_DIVISIONS).roundToInt() / ANGLE_STEP_DIVISIONS) },
            valueRange = -ANGLE_LIMIT..ANGLE_LIMIT,
            steps = ANGLE_SLIDER_STEPS,
            modifier = Modifier.weight(1f).padding(horizontal = Tokens.Space.cropRowGap.dp),
            colors =
                SliderDefaults.colors(
                    thumbColor = colors.accent,
                    activeTrackColor = colors.accent,
                    inactiveTrackColor = colors.line,
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent,
                ),
        )
        Text(angleLabel(fineDegrees), color = colors.text3, fontSize = Tokens.Font.n11.sp)
        Spacer(Modifier.width(Tokens.Space.cropRowGap.dp))
        Text(
            "复位",
            color = colors.accent,
            fontSize = Tokens.Font.n11.sp,
            modifier = Modifier.clickable(onClick = onReset),
        )
    }
}

private fun angleLabel(fineDegrees: Float): String = if (fineDegrees > 0f) "+$fineDegrees°" else "$fineDegrees°"

private fun cornerPoints(
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

private fun nearestCorner(
    crop: RectF,
    size: IntSize,
    point: Offset,
    hitRadius: Float,
): Int? {
    val distances = cornerPoints(crop, size).map { hypot(it.x - point.x, it.y - point.y) }
    val best = distances.indices.minByOrNull { distances[it] } ?: return null
    return if (distances[best] <= hitRadius) best else null
}

private fun moveCorner(
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
