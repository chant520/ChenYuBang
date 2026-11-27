package com.chenyubang.feature.capture

import android.content.Context
import android.view.Surface
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraInfoUnavailableException
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.chenyubang.core.ui.CChromeGlyph
import com.chenyubang.core.ui.CChromeIcon
import com.chenyubang.core.ui.tokens.Tokens
import java.util.concurrent.ExecutionException

/** P17 取景区 + 快门行：真实 CameraX 预览，快门产出本机 JPEG 路径交给 P18。 */
internal const val PREVIEW_ASPECT_RATIO = 3f / 4f

@Composable
internal fun CameraBody(
    modifier: Modifier,
    onImageReady: (String) -> Unit,
    onPick: () -> Unit,
    onCancel: () -> Unit,
    pickFailed: Boolean,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView =
        remember {
            PreviewView(context).apply {
                scaleType = PreviewView.ScaleType.FIT_CENTER
            }
        }
    val imageCapture = remember { ImageCapture.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3).build() }
    var bindFailed by remember { mutableStateOf(false) }
    var capturing by remember { mutableStateOf(false) }
    var captureFailed by remember { mutableStateOf(false) }

    BindCameraPreview(lifecycleOwner, previewView, imageCapture) { bindFailed = true }

    Column(modifier.fillMaxWidth()) {
        Box(Modifier.weight(1f).fillMaxWidth().background(Tokens.Dark.cam), contentAlignment = Alignment.Center) {
            AndroidView(
                factory = { previewView },
                modifier =
                    Modifier.fillMaxHeight()
                        .aspectRatio(PREVIEW_ASPECT_RATIO),
            )
            CameraHint(
                bindFailed = bindFailed,
                pickFailed = pickFailed,
                captureFailed = captureFailed,
                onPick = onPick,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = Tokens.Space.block.dp),
            )
        }
        ShutterRow(
            capturing = capturing,
            onShoot = {
                if (!capturing && !bindFailed) {
                    capturing = true
                    captureFailed = false
                    capturePhoto(
                        context = context,
                        previewView = previewView,
                        imageCapture = imageCapture,
                        onSaved = { path ->
                            capturing = false
                            onImageReady(path)
                        },
                        onFailed = {
                            capturing = false
                            captureFailed = true
                        },
                    )
                }
            },
            onPick = onPick,
            onCancel = onCancel,
        )
    }
}

@Composable
private fun BindCameraPreview(
    lifecycleOwner: LifecycleOwner,
    previewView: PreviewView,
    imageCapture: ImageCapture,
    onFailed: () -> Unit,
) {
    val context = LocalContext.current
    DisposableEffect(lifecycleOwner) {
        var provider: ProcessCameraProvider? = null
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener(
            {
                try {
                    val cameraProvider = future.get()
                    provider = cameraProvider
                    val preview = Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3).build()
                    preview.surfaceProvider = previewView.surfaceProvider
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageCapture,
                    )
                } catch (_: ExecutionException) {
                    onFailed()
                } catch (_: CameraInfoUnavailableException) {
                    onFailed()
                }
            },
            ContextCompat.getMainExecutor(context),
        )
        onDispose { provider?.unbindAll() }
    }
}

private fun capturePhoto(
    context: Context,
    previewView: PreviewView,
    imageCapture: ImageCapture,
    onSaved: (String) -> Unit,
    onFailed: () -> Unit,
) {
    val file = CaptureImageStore.newCaptureFile(context)
    imageCapture.targetRotation = previewView.display?.rotation ?: Surface.ROTATION_0
    val options = ImageCapture.OutputFileOptions.Builder(file).build()
    imageCapture.takePicture(
        options,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                onSaved(file.absolutePath)
            }

            override fun onError(exception: ImageCaptureException) {
                onFailed()
            }
        },
    )
}

@Composable
private fun CameraHint(
    bindFailed: Boolean,
    pickFailed: Boolean,
    captureFailed: Boolean,
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val message =
        when {
            bindFailed -> "相机不可用，请改用相册"
            pickFailed -> "照片读取失败，请重试"
            captureFailed -> "拍照失败，请重试"
            else -> null
        }
    if (message == null) return
    Row(modifier.padding(horizontal = Tokens.Space.pageX.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(message, color = Tokens.Dark.warnfg, fontSize = Tokens.FontRole.supporting.sp)
        if (bindFailed || pickFailed) {
            Spacer(Modifier.width(Tokens.Space.camTopGap.dp))
            Text(
                "改用相册",
                color = Tokens.Dark.camfg,
                fontSize = Tokens.FontRole.button.sp,
                modifier = Modifier.clickable(onClick = onPick),
            )
        }
    }
}

@Composable
private fun ShutterRow(
    capturing: Boolean,
    onShoot: () -> Unit,
    onPick: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().background(Tokens.Dark.cam)
            .padding(
                start = Tokens.Space.camRowX.dp,
                end = Tokens.Space.camRowX.dp,
                top = Tokens.Space.camRowTop.dp,
                bottom = Tokens.Space.camRowBottom.dp,
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GalleryButton(onPick)
        ShutterButton(capturing, onShoot)
        Text(
            "取消",
            color = Tokens.Dark.camdim,
            fontSize = Tokens.FontRole.caption.sp,
            textAlign = TextAlign.Center,
            modifier =
                Modifier.width(Tokens.Space.camGallery.dp)
                    .clickable(onClick = onCancel)
                    .semantics { contentDescription = "取消拍照" },
        )
    }
}

@Composable
private fun GalleryButton(onPick: () -> Unit) {
    Box(
        Modifier.size(Tokens.Space.camGallery.dp)
            .border(1.dp, Tokens.Dark.camstroke, RoundedCornerShape(Tokens.Radius.r5.dp))
            .clickable(onClick = onPick)
            .semantics { contentDescription = "从相册选择" },
        contentAlignment = Alignment.Center,
    ) {
        CChromeGlyph(CChromeIcon.Gallery, Tokens.Dark.camfg, Modifier.size(Tokens.Icon.n20.dp))
    }
}

@Composable
private fun ShutterButton(
    capturing: Boolean,
    onShoot: () -> Unit,
) {
    Box(
        Modifier.size(Tokens.Space.camShutter.dp)
            .border(Tokens.Space.camRing.dp, Tokens.Dark.camfg, CircleShape)
            .clickable(enabled = !capturing, onClick = onShoot)
            .semantics { contentDescription = "拍照" },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(Tokens.Space.camShutterIn.dp).background(Tokens.Dark.camfill, CircleShape))
    }
}
