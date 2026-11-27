package com.chenyubang.feature.capture

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chenyubang.core.ui.CBar
import com.chenyubang.core.ui.CBarKind
import com.chenyubang.core.ui.CButton
import com.chenyubang.core.ui.CButtonVariant
import com.chenyubang.core.ui.CCard
import com.chenyubang.core.ui.CImagePlaceholder
import com.chenyubang.core.ui.CInput
import com.chenyubang.core.ui.CLabel
import com.chenyubang.core.ui.CPlaceholderKind
import com.chenyubang.core.ui.CSpinner
import com.chenyubang.core.ui.CTag
import com.chenyubang.core.ui.tokens.Tokens

/** P19 顶部条：形态与 P18 相同（规范 §4.5「顶部条同 §4.4 形态」）。 */
@Composable
internal fun OcrHeader(
    onBack: () -> Unit,
    colors: Tokens.ColorSet,
) {
    CaptureStepHeader(
        title = "识别结果",
        step = "第 3 步 / 共 5 步",
        backDescription = "返回裁切",
        onBack = onBack,
        colors = colors,
    )
}

@Composable
internal fun OcrThumbRow(
    thumbnail: ImageBitmap?,
    lineCount: Int,
    colors: Tokens.ColorSet,
    onZoom: () -> Unit,
) {
    val shape = RoundedCornerShape(Tokens.Radius.r4.dp)
    Row(
        Modifier.fillMaxWidth().padding(bottom = Tokens.Space.ocrRowBottom.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(Tokens.Space.ocrThumb.dp)
                .clip(shape)
                .background(colors.phbg)
                .border(1.dp, colors.phline, shape)
                .clickable(enabled = thumbnail != null, onClick = onZoom)
                .semantics { contentDescription = "裁切图，可点开放大" },
            contentAlignment = Alignment.Center,
        ) {
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.width(Tokens.Space.ocrThumbGap.dp))
        Text(
            "裁切图 · 可点开放大",
            color = colors.text3,
            fontSize = Tokens.FontRole.caption.sp,
            modifier = Modifier.weight(1f),
        )
        CTag("识别到 $lineCount 行")
    }
}

/** 裁切图放大层：点任意处关闭，不改变任何字段。 */
@Composable
internal fun OcrZoomDialog(
    bitmap: ImageBitmap,
    colors: Tokens.ColorSet,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().background(colors.page)
                .clickable(onClick = onDismiss)
                .padding(Tokens.Space.pageX.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                bitmap = bitmap,
                contentDescription = "裁切图放大",
                contentScale = ContentScale.Fit,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            Text("点击任意处关闭", color = colors.text3, fontSize = Tokens.FontRole.caption.sp)
        }
    }
}

@Composable
internal fun OcrScanning(colors: Tokens.ColorSet) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = Tokens.Space.ocrFailGap.dp),
        horizontalArrangement = Arrangement.spacedBy(Tokens.Space.ocrThumbGap.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CSpinner(color = colors.accent, modifier = Modifier.size(Tokens.Icon.n14.dp))
        Text("正在识别…", color = colors.text2, fontSize = Tokens.FontRole.supporting.sp)
    }
}

/** 成功态：可编辑的题干与 A–D 选项，识别有缺漏就地补。 */
@Composable
internal fun OcrFields(
    draft: OcrDraft,
    colors: Tokens.ColorSet,
    onChange: (OcrDraft) -> Unit,
    onRerun: () -> Unit,
    onNext: () -> Unit,
    guessed: Boolean = false,
) {
    val options = draft.paddedOptions()
    CLabel("题干")
    CInput(
        value = draft.stem,
        onValueChange = { onChange(draft.copy(stem = it, options = options)) },
        placeholder = "识别出的题干",
        modifier = Modifier.padding(bottom = Tokens.Space.ocrInputGap.dp),
    )
    CLabel("选项")
    options.forEachIndexed { index, value ->
        CInput(
            value = value,
            onValueChange = { next ->
                val updated = options.toMutableList().also { it[index] = next }
                onChange(draft.copy(options = updated))
            },
            placeholder = "选项 ${OcrOptionKeys[index]}",
            modifier = Modifier.padding(bottom = Tokens.Space.ocrInputGap.dp),
        )
    }
    Text(
        if (guessed) "推测选项，请复核；识别有缺漏就在这里逐字补" else "识别有缺漏就在这里逐字补",
        color = colors.text3,
        fontSize = Tokens.FontRole.caption.sp,
        modifier = Modifier.padding(bottom = Tokens.Space.ocrHintBottom.dp),
    )
    CButton(
        "重新识别",
        onRerun,
        variant = CButtonVariant.Ghost,
        modifier =
            Modifier.fillMaxWidth().padding(
                top = Tokens.Space.ocrGhostTop.dp,
                bottom = Tokens.Space.ocrGhostBottom.dp,
            ),
    )
    CButton("下一步", onNext, modifier = Modifier.fillMaxWidth())
}

/** 失败态：橙只用于占位与警示条两处提示，主行动仍为绿（规范 §4.5）。 */
@Composable
internal fun OcrFailure(
    colors: Tokens.ColorSet,
    actions: OcrScreenActions,
) {
    CImagePlaceholder(
        kind = CPlaceholderKind.Warn,
        modifier = Modifier.fillMaxWidth(),
        text = "图像已拿到，没有识别出文字",
    )
    Spacer(Modifier.height(Tokens.Space.ocrFailGap.dp))
    CBar(
        kind = CBarKind.Warn,
        title = "这张图没识别出文字",
        subtitle = "可以重拍、回上一步重新框选，或直接手动补字。",
    )
    Spacer(Modifier.height(Tokens.Space.ocrFailGap.dp))
    Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.ocrFailGap.dp)) {
        CButton("重拍这张", actions.onRetake, modifier = Modifier.fillMaxWidth())
        CButton(
            "回上一步重新框选",
            actions.onBack,
            variant = CButtonVariant.Ghost,
            modifier = Modifier.fillMaxWidth(),
        )
        CButton("手动补字", actions.onManual, variant = CButtonVariant.Ghost, modifier = Modifier.fillMaxWidth())
    }
    Spacer(Modifier.height(Tokens.Space.ocrFailGap.dp))
    CCard(soft = true) {
        Text(
            "重拍这张：回到拍照页重新拍一张。\n" +
                "回上一步重新框选：只框这一道题，邻题不要。\n" +
                "手动补字：直接进入修正页，以空文本起填。",
            color = colors.text2,
            fontSize = Tokens.FontRole.supporting.sp,
        )
    }
    Text(
        "手动补字不影响已拍的图，之后还能再识别。",
        color = colors.text3,
        fontSize = Tokens.FontRole.caption.sp,
        modifier = Modifier.padding(top = Tokens.Space.ocrNoteTop.dp),
    )
}
