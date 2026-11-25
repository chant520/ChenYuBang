package com.chenyubang.feature.idiom

import androidx.compose.runtime.Composable
import com.chenyubang.core.data.IdiomLearningStatus
import com.chenyubang.core.ui.CCheckRow
import com.chenyubang.core.ui.COverlay

/** A6 三选一自评弹层：复用 COverlay + CCheckRow 单选语义，点选即生效并回写顶部标签。 */
@Composable
internal fun StatusPickerOverlay(
    current: IdiomLearningStatus,
    onSelect: (IdiomLearningStatus) -> Unit,
    onCancel: () -> Unit,
) {
    val options = IdiomLearningStatus.entries
    COverlay(title = "学习状态", centered = true, onCancel = onCancel) {
        options.forEachIndexed { index, status ->
            CCheckRow(
                label = statusTag(status).text,
                checked = status == current,
                onCheckedChange = { checked -> if (checked) onSelect(status) },
                showDivider = index != options.lastIndex,
            )
        }
    }
}
