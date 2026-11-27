package com.chenyubang.feature.capture

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.chenyubang.core.ui.CButton
import com.chenyubang.core.ui.CChromeGlyph
import com.chenyubang.core.ui.CChromeIcon
import com.chenyubang.core.ui.tokens.Tokens
import kotlinx.coroutines.launch

/**
 * P17 拍照 / 相册选择（信息架构 v1.4 §3）：取景、快门、相册入口、取消，
 * 相机权限被拒时给「去系统设置 / 改用相册」两条退路（D 稿 P17）。
 * 产出仅为一张本机图片路径，交给 P18 裁切；取消即整条丢弃。
 */
@Composable
fun CaptureCameraScreen(
    onCancel: () -> Unit,
    onImageReady: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var granted by remember { mutableStateOf(context.hasCameraPermission()) }
    var requested by rememberSaveable { mutableStateOf(false) }
    var pickFailed by remember { mutableStateOf(false) }

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    val pickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                scope.launch {
                    val file = CaptureImageStore.importFromUri(context, uri)
                    if (file == null) pickFailed = true else onImageReady(file.absolutePath)
                }
            }
        }
    val pickPhoto = {
        pickFailed = false
        pickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    LaunchedEffect(granted) {
        if (!granted && !requested) {
            requested = true
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) granted = context.hasCameraPermission()
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(modifier.fillMaxSize().background(Tokens.Dark.cam)) {
        CameraTopBar(onCancel)
        if (granted) {
            CameraBody(
                modifier = Modifier.weight(1f),
                onImageReady = onImageReady,
                onPick = pickPhoto,
                onCancel = onCancel,
                pickFailed = pickFailed,
            )
        } else {
            CameraDenied(
                modifier = Modifier.weight(1f),
                onOpenSettings = { openAppSettings(context) },
                onPick = pickPhoto,
                onCancel = onCancel,
                pickFailed = pickFailed,
            )
        }
    }
}

@Composable
private fun CameraTopBar(onCancel: () -> Unit) {
    val colors = if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    Row(
        Modifier.fillMaxWidth().background(colors.page)
            .padding(
                start = Tokens.Space.pageX.dp,
                end = Tokens.Space.pageX.dp,
                top = Tokens.Space.contentTop.dp,
                bottom = Tokens.Space.camTopY.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(Tokens.Space.hitMin.dp)
                .clickable(onClick = onCancel)
                .semantics { contentDescription = "取消拍照" },
            contentAlignment = Alignment.Center,
        ) {
            CChromeGlyph(CChromeIcon.Close, colors.text2, Modifier.size(Tokens.Icon.n21.dp))
        }
        Spacer(Modifier.width(Tokens.Space.camTopGap.dp))
        Text("拍照", color = colors.head, fontSize = Tokens.FontRole.pageTitle.sp)
    }
}

@Composable
private fun CameraDenied(
    modifier: Modifier,
    onOpenSettings: () -> Unit,
    onPick: () -> Unit,
    onCancel: () -> Unit,
    pickFailed: Boolean,
) {
    val shape = RoundedCornerShape(Tokens.Radius.r6.dp)
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(
                Modifier.width(Tokens.Space.camDenyW.dp)
                    .background(Tokens.Dark.surface, shape)
                    .border(1.dp, Tokens.Dark.line, shape)
                    .padding(horizontal = Tokens.Space.camDenyPadX.dp, vertical = Tokens.Space.camDenyPadY.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CChromeGlyph(CChromeIcon.CameraOff, Tokens.Dark.warnfg, Modifier.size(Tokens.Space.camDenyIcon.dp))
                Spacer(Modifier.height(Tokens.Space.camDenyHint.dp))
                Text("需要相机权限才能拍照", color = Tokens.Dark.head, fontSize = Tokens.FontRole.sectionTitle.sp)
                Spacer(Modifier.height(Tokens.Space.camDenyTextGap.dp))
                Text(
                    "系统里这项权限被关掉了。可以先去开启，也可以先用相册里的照片。",
                    color = Tokens.Dark.text2,
                    fontSize = Tokens.FontRole.supporting.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Column(
            Modifier.fillMaxWidth().background(Tokens.Dark.cam)
                .padding(
                    start = Tokens.Space.pageX.dp,
                    end = Tokens.Space.pageX.dp,
                    top = Tokens.Space.camRowTop.dp,
                    bottom = Tokens.Space.camRowBottom.dp,
                ),
        ) {
            CButton("去系统设置", onOpenSettings, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(Tokens.Space.camDenyGap.dp))
            CamGhostButton("改用相册", onPick)
            Spacer(Modifier.height(Tokens.Space.camDenyHint.dp))
            Text(
                if (pickFailed) "照片读取失败，请重试" else "取消可回上一页",
                color = Tokens.Dark.camhint,
                fontSize = Tokens.FontRole.caption.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onCancel),
            )
        }
    }
}

/** D 稿 P17「改用相册」：白 30% 描边 + 白 85% 文字的幽灵按钮，底色是相机黑。 */
@Composable
private fun CamGhostButton(
    text: String,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(Tokens.Radius.r5.dp)
    Box(
        Modifier.fillMaxWidth()
            .border(1.dp, Tokens.Dark.camghostline, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = Tokens.Dark.camfg,
            fontSize = Tokens.FontRole.button.sp,
            modifier = Modifier.padding(vertical = Tokens.Space.ghostY.dp),
        )
    }
}

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private fun openAppSettings(context: Context) {
    val intent =
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
