package com.chenyubang.feature.mistake

/** 关联成语（P10 区块 ⑧，每个可点进 P05）。 */
data class P10IdiomRef(
    val id: String,
    val word: String,
)

/** P10 页面全部字段只来自用户表 + 当前错题记录 + 关联表，绝不取系统题库答案。 */
data class P10UserExampleDetail(
    val id: String,
    val stem: String,
    val options: List<String?>,
    val myAnswer: String?,
    val isMistake: Boolean,
    val examType: String?,
    val year: Int?,
    val reason: String?,
    val tagNames: List<String>,
    val idioms: List<P10IdiomRef>,
    val imagePath: String?,
)

/** P10 页面状态（纯数据，快照直接喂；loading/failed/missing 为读取态）。 */
data class P10UserExampleUiState(
    val loading: Boolean = true,
    val failed: Boolean = false,
    val missing: Boolean = false,
    val detail: P10UserExampleDetail? = null,
    val revealed: Boolean = false,
    val rerolling: Boolean = false,
    val picked: Int? = null,
    val checked: Boolean = false,
    val imageUnreadable: Boolean = false,
) {
    val isMistake: Boolean get() = detail?.isMistake == true

    /** 首次作答下标；空值或 A–D 之外为 null（不可比对）。 */
    val firstPick: Int? get() = P10Logic.firstPick(detail?.myAnswer)

    /** 遮罩盖的是「我的答案」；重做中直到对答案前一律遮住。 */
    val answerHidden: Boolean
        get() = isMistake && if (rerolling) !checked else !revealed

    /** 重做中不给看首次作答，遮罩开关置灰。 */
    val canToggleMask: Boolean get() = isMistake && !rerolling

    /**
     * 底部红描边「删除这道错题」仅标错的录入题渲染（信息架构 v1.4 版内修订第 ④ 条，用户 2026-11-22 拍板）：
     * 未标错的普通详情整行不出现（不是置灰），删除入口只留顶部置灰「删除」，随 2.8（C8）定义。
     * 与 canToggleMask 同层，供页面门控渲染与单测双向断言，不在 Composable 里各写判断。
     */
    val showsBottomDeleteRow: Boolean get() = isMistake
}

/** P10 回调打包：编辑/删除本版只占位（2.8 接线），不产生写入。 */
data class P10UserExampleActions(
    val onBack: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onToggleAnswer: () -> Unit = {},
    val onPickOption: (Int) -> Unit = {},
    val onStartReroll: () -> Unit = {},
    val onCheck: () -> Unit = {},
    val onExitReroll: () -> Unit = {},
    val onOpenIdiom: (String) -> Unit = {},
)

private val letters = listOf("A", "B", "C", "D")

/** P10 图片位三态：纯派生量，无 Bitmap / Compose 依赖，Composable 只按此渲染。 */
enum class P10ImageSlotKind { Warn, Skeleton, Image }

/** P10 纯逻辑：文案与比对口径与 iOS MistakeBrowse 逐字一致（双端同源）。 */
object P10Logic {
    const val REROLL_NOTE = "录入题只存你填过的内容，本版未记录正确答案，这里只与你的首次作答比对。"
    const val PLACEHOLDER = "暂无"

    fun firstPick(myAnswer: String?): Int? = letters.indexOf(myAnswer?.trim()?.uppercase()).takeIf { it >= 0 }

    fun rerollResult(
        first: Int?,
        picked: Int?,
    ): String =
        when {
            picked == null || picked !in letters.indices -> "尚未选择"
            first == null || first !in letters.indices -> "本题未记录你的首次作答，无法比对（本次选了 ${letters[picked]}）"
            first == picked -> "与首次作答一致（${letters[picked]}）"
            else -> "与首次作答不同：首次 ${letters[first]}，本次 ${letters[picked]}"
        }

    fun sourceLine(
        examType: String?,
        year: Int?,
    ): String {
        val parts = listOfNotNull(year?.toString(), examType?.trim()?.takeIf { it.isNotEmpty() })
        return parts.joinToString(" · ").ifEmpty { "未填" }
    }

    fun optionText(value: String?): String = value?.takeIf { it.isNotBlank() } ?: PLACEHOLDER

    fun answerText(
        myAnswer: String?,
        hidden: Boolean,
    ): String = if (hidden) "已遮住" else myAnswer?.trim()?.takeIf { it.isNotEmpty() } ?: PLACEHOLDER

    /** 原图已不在的副句：仅标错态带「与重做」，未标错态没有重做入口（信息架构 v1.4 §5.4 第 551 行）。 */
    fun imageMissingSubtitle(isMistake: Boolean): String =
        when (isMistake) {
            true -> "文件可能被清理；题干与选项仍可查看与重做。"
            false -> "文件可能被清理；题干与选项仍可查看。"
        }

    /**
     * 图片位三态派生量（纯逻辑，钉住解码中的中间态）：
     * imagePath 缺失或读不出 → Warn；路径有效但解码未完成 → Skeleton；已解码 → Image。
     */
    fun imageSlotKind(
        imagePath: String?,
        unreadable: Boolean,
        hasImage: Boolean,
    ): P10ImageSlotKind =
        when {
            imagePath == null || unreadable -> P10ImageSlotKind.Warn
            !hasImage -> P10ImageSlotKind.Skeleton
            else -> P10ImageSlotKind.Image
        }
}

/** P10 状态机：同页切换，无写入（不落 review_log、不改掌握状态）。 */
object P10Transitions {
    fun toggleAnswer(state: P10UserExampleUiState) =
        if (state.canToggleMask) state.copy(revealed = !state.revealed) else state

    fun startReroll(state: P10UserExampleUiState) =
        if (state.isMistake) state.copy(rerolling = true, picked = null, checked = false, revealed = false) else state

    fun pick(
        state: P10UserExampleUiState,
        index: Int,
    ) = if (state.rerolling && index in letters.indices) state.copy(picked = index, checked = false) else state

    fun check(state: P10UserExampleUiState) =
        if (state.rerolling && state.picked != null) state.copy(checked = true) else state

    fun exitReroll(state: P10UserExampleUiState) = state.copy(rerolling = false, picked = null, checked = false)
}
