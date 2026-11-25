package com.chenyubang.feature.idiom

import com.chenyubang.core.data.IdiomLearningStatus
import com.chenyubang.core.ui.CTagStyle

/** P03 / P24 共用的三态文字徽标映射（拍板口径：未学=Disabled、学过=Solid、已掌握=Selected）。 */
data class StatusTag(
    val text: String,
    val style: CTagStyle,
)

fun statusTag(status: IdiomLearningStatus): StatusTag =
    when (status) {
        IdiomLearningStatus.UNLEARNED -> StatusTag("未学", CTagStyle.Disabled)
        IdiomLearningStatus.LEARNED -> StatusTag("学过", CTagStyle.Solid)
        IdiomLearningStatus.MASTERED -> StatusTag("已掌握", CTagStyle.Selected)
    }
