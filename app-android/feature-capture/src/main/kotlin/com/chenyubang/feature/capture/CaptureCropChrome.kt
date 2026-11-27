package com.chenyubang.feature.capture

import android.graphics.Bitmap
import android.graphics.Paint
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
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
import kotlin.math.roundToInt

internal const val ROTATION_QUARTERS = 4
private const val QUARTER_TURN_DEGREES = 90f
private const val ANGLE_LIMIT = 15f
private const val ANGLE_STEP_DIVISIONS = 2f
private const val ANGLE_SLIDER_STEPS = 59

/** 画布入参：图片、框选、摆正与失败标记一次传入，避免逐项长参数表。 */
internal data class CropView(
    val bitmap: Bitmap?,
    val crop: RectF?,
    val quarterTurns: Int,
    val fineDegrees: Float,
    val failed: Boolean,
)

/** 四角标记的笔形（长度、线宽、颜色）。 */
private class CornerPen(
    val length: Float,
    val stroke: Float,
    val color: Color,
)

@Composable
internal fun CropHeader(
    onBack: () -> Unit,
    colors: Tokens.ColorSet,
) {
    CaptureStepHeader(
        title = "裁切与摆正",
        step = "第 2 步 / 共 5 步",
        backDescription = "返回重拍",
        onBack = onBack,
        colors = colors,
    )
}

@Composable
internal fun CaptureStepHeader(
    title: String,
    step: String,
    backDescription: String,
    onBack: () -> Unit,
    colors: Tokens.ColorSet,
) {
    val stackStep = LocalDensity.current.fontScale > 1f
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
                    .semantics { contentDescription = backDescription },
                contentAlignment = Alignment.Center,
            ) {
                CChromeGlyph(CChromeIcon.Back, colors.text2, Modifier.size(Tokens.Icon.n21.dp))
            }
            Spacer(Modifier.width(Tokens.Space.camTopGap.dp))
            Text(
                title,
                color = colors.head,
                fontSize = Tokens.FontRole.pageTitle.sp,
                fontWeight = FontWeight.W700,
                modifier = Modifier.weight(1f),
            )
            if (!stackStep) {
                Text(step, color = colors.text3, fontSize = Tokens.FontRole.caption.sp)
            }
        }
        if (stackStep) {
            Text(
                step,
                color = colors.text3,
                fontSize = Tokens.FontRole.caption.sp,
                modifier = Modifier.align(Alignment.End).padding(end = Tokens.Space.pageX.dp),
            )
        }
        Box(Modifier.fillMaxWidth().height(Tokens.Space.cropEdge.dp).background(colors.line))
    }
}

@Composable
internal fun CropCanvasBox(
    view: CropView,
    colors: Tokens.ColorSet,
    onCropChange: (RectF) -> Unit,
    onSizeChange: (IntSize) -> Unit,
    modifier: Modifier,
) {
    Box(
        modifier.clip(RoundedCornerShape(Tokens.Radius.r6.dp)).background(Tokens.Dark.cam)
            .onSizeChanged(onSizeChange),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = view.bitmap
        val crop = view.crop
        when {
            view.failed -> Text("图片读取失败，请返回重拍", color = Tokens.Dark.warnfg, fontSize = Tokens.FontRole.supporting.sp)
            bitmap == null || crop == null ->
                Text(
                    "正在读取图片…",
                    color = Tokens.Dark.camhint,
                    fontSize = Tokens.FontRole.supporting.sp,
                )
            else -> CropCanvas(view, colors, onCropChange)
        }
    }
}

@Composable
private fun CropCanvas(
    view: CropView,
    colors: Tokens.ColorSet,
    onCropChange: (RectF) -> Unit,
) {
    val bitmap = requireNotNull(view.bitmap)
    val crop = requireNotNull(view.crop)
    val density = LocalDensity.current
    val latestCrop = rememberUpdatedState(crop)
    val minSizePx = with(density) { Tokens.Space.cropMin.dp.toPx() }
    val hitRadius = with(density) { Tokens.Space.hitMin.dp.toPx() }
    Canvas(
        Modifier.fillMaxSize().pointerInput(view.quarterTurns, view.fineDegrees) {
            var dragCrop = latestCrop.value
            var dragCorner: Int? = null
            detectDragGestures(
                onDragStart = { offset ->
                    dragCrop = latestCrop.value
                    dragCorner = nearestCorner(dragCrop, size, offset, hitRadius)
                },
                onDragEnd = { dragCorner = null },
                onDragCancel = { dragCorner = null },
            ) { change, dragAmount ->
                change.consume()
                val current = dragCorner
                if (current != null) {
                    val bounds =
                        CropDisplayGeometry.displayedImageBounds(
                            CropViewport(
                                bitmap.width,
                                bitmap.height,
                                size.width.toFloat(),
                                size.height.toFloat(),
                                view.quarterTurns,
                                view.fineDegrees,
                            ),
                        )
                    dragCrop = moveCorner(dragCrop, current, dragAmount, size, minSizePx, bounds)
                    onCropChange(dragCrop)
                }
            }
        },
    ) {
        val displayView =
            CropViewport(
                bitmap.width,
                bitmap.height,
                size.width.toFloat(),
                size.height.toFloat(),
                view.quarterTurns,
                view.fineDegrees,
            )
        drawCropBitmap(bitmap, displayView)
        drawCropOverlay(crop, colors)
    }
}

private fun DrawScope.drawCropBitmap(
    bitmap: Bitmap,
    view: CropViewport,
) {
    val canvas = this.drawContext.canvas.nativeCanvas
    val checkpoint = canvas.save()
    canvas.translate(size.width / 2f, size.height / 2f)
    canvas.rotate(view.quarterTurns * QUARTER_TURN_DEGREES + view.fineDegrees)
    val fitScale = CropDisplayGeometry.fitScale(view)
    canvas.scale(fitScale, fitScale)
    canvas.translate(-bitmap.width / 2f, -bitmap.height / 2f)
    canvas.drawBitmap(bitmap, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
    canvas.restoreToCount(checkpoint)
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
    val pen =
        CornerPen(
            length = Tokens.Space.cropHandle.dp.toPx(),
            stroke = Tokens.Space.cropHandleStroke.dp.toPx(),
            color = colors.warnfg,
        )
    drawCorner(left, top, 1f, 1f, pen)
    drawCorner(left + width, top, -1f, 1f, pen)
    drawCorner(left, top + height, 1f, -1f, pen)
    drawCorner(left + width, top + height, -1f, -1f, pen)
}

private fun DrawScope.drawCorner(
    x: Float,
    y: Float,
    dx: Float,
    dy: Float,
    pen: CornerPen,
) {
    drawLine(pen.color, Offset(x, y), Offset(x + dx * pen.length, y), pen.stroke, cap = StrokeCap.Square)
    drawLine(pen.color, Offset(x, y), Offset(x, y + dy * pen.length), pen.stroke, cap = StrokeCap.Square)
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
            fontSize = Tokens.FontRole.button.sp,
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
        Text("微调", color = colors.text3, fontSize = Tokens.FontRole.caption.sp)
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
        Text(angleLabel(fineDegrees), color = colors.text3, fontSize = Tokens.FontRole.caption.sp)
        Spacer(Modifier.width(Tokens.Space.cropRowGap.dp))
        Text(
            "复位",
            color = colors.accent,
            fontSize = Tokens.FontRole.caption.sp,
            modifier = Modifier.clickable(onClick = onReset),
        )
    }
}
