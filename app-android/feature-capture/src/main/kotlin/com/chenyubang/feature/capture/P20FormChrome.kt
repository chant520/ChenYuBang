package com.chenyubang.feature.capture

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chenyubang.core.ui.tokens.Tokens

/** P20 页面级外壳：导航头与手选成语全屏弹窗容器（与表单分区拆分以控文件函数数）。 */
@Composable
internal fun FormHeader(
    onBack: () -> Unit,
    colors: Tokens.ColorSet,
) {
    CaptureStepHeader(
        title = "匹配与修正",
        step = "第 4 步 / 共 5 步",
        backDescription = "返回识别结果",
        onBack = onBack,
        colors = colors,
    )
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
