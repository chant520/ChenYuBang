package com.chenyubang.feature.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chenyubang.core.ui.CButton
import com.chenyubang.core.ui.tokens.Tokens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 坏图/大图解码抛错统一收敛到 failed 态，不让 LaunchedEffect 崩掉整页。 */
internal fun loadCropBitmap(
    path: String,
    loader: (String) -> Bitmap? = CropImage::load,
): Bitmap? = runCatching { loader(path) }.getOrNull()

/**
 * P18 裁切与摆正（信息架构 v1.4 §3，第 2 步）：读取 P17 的图片，给一次内容边界建议框，
 * 四角可拖，支持 90° 旋转与 ±15° 摆正微调；「下一步」只导出框内内容，框外不参与识别。
 * [initialCrop] 来自路由：P19 返回时还原上次的框选与摆正（IA 第 29 条）。
 */
@Composable
fun CaptureCropScreen(
    imagePath: String,
    initialCrop: CropFrame?,
    onRetake: () -> Unit,
    onNext: (CropSelection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = remember(imagePath) { CropState(initialCrop) }
    val handleRetake: () -> Unit = {
        CaptureImageStore.delete(imagePath)
        onRetake()
    }
    LaunchedEffect(imagePath) {
        state.applyLoaded(withContext(Dispatchers.IO) { loadCropBitmap(imagePath) })
    }
    Column(modifier.fillMaxSize().background(colors.page)) {
        CropHeader(onBack = handleRetake, colors = colors)
        Column(
            Modifier.fillMaxWidth().weight(1f)
                .padding(start = Tokens.Space.pageX.dp, end = Tokens.Space.pageX.dp, top = Tokens.Space.cropTop.dp),
        ) {
            CropPreview(state, colors, Modifier.fillMaxWidth().weight(1f))
            Text(
                "已自动给出题目边界，拖动四角可调整；只留这一道题，邻题去掉识别更准。",
                color = colors.text3,
                fontSize = Tokens.FontRole.caption.sp,
                modifier =
                    Modifier.padding(
                        top = Tokens.Space.cropHintTop.dp,
                        bottom = Tokens.Space.cropHintBottom.dp,
                    ),
            )
            CropActionRow(
                enabled = state.bitmap != null && !state.exporting,
                onRotate = state::rotate,
                onAdjust = state::toggleAdjust,
                onRetake = handleRetake,
                colors = colors,
            )
            if (state.showAdjust) {
                AdjustPanel(state.fineDegrees, colors, onReset = state::resetAngle, onFineChange = state::setAngle)
            }
            Spacer(Modifier.height(Tokens.Space.cropRowBottom.dp))
            if (state.exportFailed) {
                Text(
                    "裁切失败，请重试或重拍",
                    color = colors.warnfg,
                    fontSize = Tokens.FontRole.supporting.sp,
                    modifier = Modifier.padding(bottom = Tokens.Space.cropAdjustTop.dp),
                )
            }
            CButton(
                text = if (state.exporting) "正在裁切…" else "下一步 · 识别",
                onClick = { state.beginExport(context, scope, onNext) },
                enabled = state.bitmap != null && state.canvasSize.width > 0 && state.canvasSize.height > 0,
                loading = state.exporting,
                modifier = Modifier.fillMaxWidth().padding(bottom = Tokens.Space.pageX.dp),
            )
        }
    }
}

@Composable
private fun CropPreview(
    state: CropState,
    colors: Tokens.ColorSet,
    modifier: Modifier,
) {
    CropCanvasBox(
        view = state.view,
        colors = colors,
        onCropChange = state::applyCrop,
        onSizeChange = state::applyCanvasSize,
        modifier = modifier,
    )
}

/** P18 的图片、框选与导出进度；换图（新拍照）时随 remember(imagePath) 重建。 */
private class CropState(initialCrop: CropFrame?) {
    private val initialFrame = initialCrop
    private var pendingInitialRestore = initialCrop != null
    var bitmap by mutableStateOf<Bitmap?>(null)
    var crop by mutableStateOf(initialCrop?.toRectF())
    var quarterTurns by mutableStateOf(initialCrop?.quarterTurns ?: 0)
    var fineDegrees by mutableStateOf(initialCrop?.fineDegrees ?: 0f)
    var showAdjust by mutableStateOf(false)
    var failed by mutableStateOf(false)
    var exporting by mutableStateOf(false)
    var exportFailed by mutableStateOf(false)
    var canvasSize by mutableStateOf(IntSize.Zero)
    private var pendingSourceCrop: RectF? = null

    val view: CropView get() = CropView(bitmap, crop, quarterTurns, fineDegrees, failed)

    fun applyLoaded(loaded: Bitmap?) {
        bitmap = loaded
        if (crop == null) pendingSourceCrop = loaded?.let { CropImage.suggest(it) }
        failed = loaded == null
        initializeCrop()
    }

    fun applyCanvasSize(size: IntSize) {
        val previous = canvasSize
        canvasSize = size
        val source = bitmap
        if (source != null && size.width > 0 && size.height > 0) {
            val current = crop
            if (current == null) {
                initializeCrop()
            } else if (pendingInitialRestore) {
                initializeCrop()
            } else if (previous.width > 0 && previous.height > 0 && previous != size) {
                crop =
                    CropDisplayGeometry.remapCrop(
                        current,
                        CropViewport(
                            source.width,
                            source.height,
                            previous.width.toFloat(),
                            previous.height.toFloat(),
                            quarterTurns,
                            fineDegrees,
                        ),
                        CropViewport(
                            source.width,
                            source.height,
                            size.width.toFloat(),
                            size.height.toFloat(),
                            quarterTurns,
                            fineDegrees,
                        ),
                    )
            }
        }
    }

    private fun initializeCrop() {
        val source = bitmap
        val size = canvasSize
        if (source == null || size.width <= 0 || size.height <= 0) return
        val canRestore =
            pendingInitialRestore &&
                initialFrame?.let { it.containerWidth > 0 && it.containerHeight > 0 } == true
        if (canRestore) {
            val frame = requireNotNull(initialFrame)
            crop =
                CropDisplayGeometry.remapCrop(
                    requireNotNull(crop),
                    CropViewport(
                        source.width,
                        source.height,
                        frame.containerWidth.toFloat(),
                        frame.containerHeight.toFloat(),
                        quarterTurns,
                        fineDegrees,
                    ),
                    CropViewport(
                        source.width,
                        source.height,
                        size.width.toFloat(),
                        size.height.toFloat(),
                        quarterTurns,
                        fineDegrees,
                    ),
                )
            pendingInitialRestore = false
        } else {
            val suggested = pendingSourceCrop ?: return
            crop =
                CropDisplayGeometry.sourceCropToDisplay(
                    suggested,
                    CropViewport(
                        source.width,
                        source.height,
                        size.width.toFloat(),
                        size.height.toFloat(),
                        quarterTurns,
                        fineDegrees,
                    ),
                )
            pendingSourceCrop = null
        }
    }

    fun applyCrop(rect: RectF) {
        crop = rect
    }

    fun rotate() {
        quarterTurns = (quarterTurns + 1) % ROTATION_QUARTERS
    }

    fun toggleAdjust() {
        showAdjust = !showAdjust
    }

    fun resetAngle() {
        fineDegrees = 0f
    }

    fun setAngle(value: Float) {
        fineDegrees = value
    }

    fun beginExport(
        context: Context,
        scope: CoroutineScope,
        onNext: (CropSelection) -> Unit,
    ) {
        val source = bitmap
        val current = crop
        if (source == null || current == null || exporting) return
        exportFailed = false
        exporting = true
        val frame = CropFrame.of(RectF(current), quarterTurns, fineDegrees, canvasSize)
        val containerWidth = canvasSize.width.toFloat()
        val containerHeight = canvasSize.height.toFloat()
        scope.launch {
            val path =
                withContext(Dispatchers.IO) {
                    val target = CaptureImageStore.newCaptureFile(context)
                    val request = CropExport(source, frame, containerWidth, containerHeight, target)
                    if (CropImage.export(request)) target.absolutePath else null
                }
            exporting = false
            if (path == null) exportFailed = true else onNext(CropSelection(path, frame))
        }
    }
}
