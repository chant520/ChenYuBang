package com.chenyubang.feature.capture

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chenyubang.core.ui.CChromeGlyph
import com.chenyubang.core.ui.CChromeIcon
import com.chenyubang.core.ui.tokens.Tokens

/** P20 页面级外壳：导航头与手选成语全屏弹窗容器（与表单分区拆分以控文件函数数）。 */
@Composable
internal fun FormHeader(
    onBack: () -> Unit,
    colors: Tokens.ColorSet,
) {
    Column(Modifier.fillMaxWidth().background(colors.page)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(
                    start = Tokens.Space.pageX.dp,
                    end = Tokens.Space.pageX.dp,
                    top = Tokens.Space.cropHeadTop.dp,
                    bottom = Tokens.Space.cropHeadBottom.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(Tokens.Space.hitMin.dp)
                    .clickable(onClick = onBack)
                    .semantics { contentDescription = "返回识别结果" },
                contentAlignment = Alignment.Center,
            ) {
                CChromeGlyph(CChromeIcon.Back, colors.text2, Modifier.size(Tokens.Icon.n21.dp))
            }
            Spacer(Modifier.width(Tokens.Space.camTopGap.dp))
            Text(
                "匹配与修正",
                color = colors.head,
                fontSize = Tokens.Font.n15.sp,
                fontWeight = FontWeight.W700,
                modifier = Modifier.weight(1f),
            )
            Text("第 4 步 / 共 5 步", color = colors.text3, fontSize = Tokens.Font.n11.sp)
        }
        Box(Modifier.fillMaxWidth().height(Tokens.Space.cropEdge.dp).background(colors.line))
    }
}

@Composable
internal fun P20FormManualPickerDialog(
    colors: Tokens.ColorSet,
    onDismiss: () -> Unit,
    manualPicker: @Composable (onPick: (String) -> Unit, onDismiss: () -> Unit) -> Unit,
    onPick: (String) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(colors.page)) {
            manualPicker(onPick, onDismiss)
        }
    }
}
