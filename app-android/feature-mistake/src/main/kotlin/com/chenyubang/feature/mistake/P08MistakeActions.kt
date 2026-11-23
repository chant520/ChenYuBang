package com.chenyubang.feature.mistake

/** P08 页面上的导航动作；集中成一个参数，避免过长参数列表。 */
data class P08MistakeActions(
    val onViewModeChange: (MistakeViewMode) -> Unit,
    val onOpenIdiom: (String) -> Unit,
    val onOpenMistake: (String) -> Unit,
    val onCapture: () -> Unit,
    /** 带当前待复习数；队列为空时停在 P08，由调用方判空提示。 */
    val onStartReview: (Int) -> Unit = {},
    /** 点「开始复习」时空队列的页内中性提示条文案；为 null 时不渲染。 */
    val pageMessage: String? = null,
    /** 关闭页内提示条。 */
    val onDismissMessage: () -> Unit = {},
)
