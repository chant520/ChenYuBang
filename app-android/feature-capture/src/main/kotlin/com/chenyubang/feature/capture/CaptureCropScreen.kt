package com.chenyubang.feature.capture

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * P18 裁切与摆正（信息架构 v1.4 §3，第 2 步）：读取 P17 的图片，给一次内容边界建议框，
 * 四角可拖，支持 90° 旋转与 ±15° 摆正微调；「下一步」只导出框内内容，框外不参与识别。
 */
@Composable
fun CaptureCropScreen(
    imagePath: String,
    onRetake: () -> Unit,
    onNext: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember(imagePath) { mutableStateOf<Bitmap?>(null) }
    var crop by remember(imagePath) { mutableStateOf<RectF?>(null) }
    var quarterTurns by remember(imagePath) { mutableStateOf(0) }
    var fineDegrees by remember(imagePath) { mutableStateOf(0f) }
    var showAdjust by remember(imagePath) { mutableStateOf(false) }
    var failed by remember(imagePath) { mutableStateOf(false) }
    var exporting by remember(imagePath) { mutableStateOf(false) }
    var exportFailed by remember { mutableStateOf(false) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(imagePath) {
        val loaded = withContext(Dispatchers.IO) { CropImage.load(imagePath) }
        bitmap = loaded
        crop = loaded?.let { CropImage.suggest(it) }
        failed = loaded == null
    }

    Column(modifier.fillMaxSize().background(colors.page)) {
        CropHeader(onBack = onRetake, colors = colors)
        Column(
            Modifier.fillMaxWidth().weight(1f)
                .padding(start = Tokens.Space.pageX.dp, end = Tokens.Space.pageX.dp, top = Tokens.Space.cropTop.dp),
        ) {
            CropCanvasBox(
                bitmap = bitmap,
                crop = crop,
                quarterTurns = quarterTurns,
                fineDegrees = fineDegrees,
                colors = colors,
                failed = failed,
                onCropChange = { crop = it },
                onSizeChange = { canvasSize = it },
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            Text(
                "已自动给出题目边界，拖动四角可调整；只留这一道题，邻题去掉识别更准。",
                color = colors.text3,
                fontSize = Tokens.Font.n12.sp,
                modifier =
                    Modifier.padding(
                        top = Tokens.Space.cropHintTop.dp,
                        bottom = Tokens.Space.cropHintBottom.dp,
                    ),
            )
            CropActionRow(
                enabled = bitmap != null && !exporting,
                onRotate = { quarterTurns = (quarterTurns + 1) % ROTATION_QUARTERS },
                onAdjust = { showAdjust = !showAdjust },
                onRetake = onRetake,
                colors = colors,
            )
            if (showAdjust) {
                AdjustPanel(fineDegrees, colors, onReset = { fineDegrees = 0f }) { fineDegrees = it }
            }
            Spacer(Modifier.height(Tokens.Space.cropRowBottom.dp))
            if (exportFailed) {
                Text(
                    "裁切失败，请重试或重拍",
                    color = colors.warnfg,
                    fontSize = Tokens.Font.n115.sp,
                    modifier = Modifier.padding(bottom = Tokens.Space.cropAdjustTop.dp),
                )
            }
            CButton(
                text = if (exporting) "正在裁切…" else "下一步 · 识别",
                onClick = {
                    val source = bitmap
                    val current = crop
                    if (source != null && current != null && !exporting) {
                        exportFailed = false
                        exporting = true
                        val containerWidth = canvasSize.width.toFloat()
                        val containerHeight = canvasSize.height.toFloat()
                        scope.launch {
                            val path =
                                withContext(Dispatchers.IO) {
                                    val target = CaptureImageStore.newCaptureFile(context)
                                    val exported =
                                        CropImage.export(
                                            source,
                                            quarterTurns,
                                            fineDegrees,
                                            RectF(current),
                                            containerWidth,
                                            containerHeight,
                                            target,
                                        )
                                    if (exported) target.absolutePath else null
                                }
                            exporting = false
                            if (path == null) exportFailed = true else onNext(path)
                        }
                    }
                },
                enabled = bitmap != null && canvasSize.width > 0 && canvasSize.height > 0,
                loading = exporting,
                modifier = Modifier.fillMaxWidth().padding(bottom = Tokens.Space.pageX.dp),
            )
        }
    }
}
