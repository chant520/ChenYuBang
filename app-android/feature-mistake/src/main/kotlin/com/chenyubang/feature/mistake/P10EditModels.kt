package com.chenyubang.feature.mistake

import com.chenyubang.core.data.ExampleEditInput
import com.chenyubang.core.data.ExampleEditRules

/** P10 编辑草稿：全部为输入原文，落库前才 trim / 归一化（与 UserExampleSaver 同口径）。 */
data class P10EditDraft(
    val stem: String,
    val options: List<String>,
    val answer: String,
    val reason: String,
    val tagDraft: String,
    val tags: List<String>,
    val examType: String,
    val year: String,
    val idiomIds: List<String>,
)

/** 字段级非法：就地标红并把「保存」置灰（入口仍在位）。题干空/归属空属页面级，点保存才提示。 */
data class P10EditFieldErrors(
    val answer: Boolean = false,
    val year: Boolean = false,
) {
    val hasInvalid: Boolean get() = answer || year
}

/** 删除二次确认的两支；可见层只有一处红（弹层中部卡片 + 危险按钮）。 */
enum class P10DeleteTarget { Marked, Unmarked }

/** P10 编辑态 / 保存态 / 提示条 / 确认弹层，纯数据，快照直接喂。 */
data class P10EditState(
    val editing: Boolean = false,
    val draft: P10EditDraft? = null,
    val errors: P10EditFieldErrors = P10EditFieldErrors(),
    val saving: Boolean = false,
    val pageMessage: String? = null,
    val errorMessage: String? = null,
    val deleteConfirm: P10DeleteTarget? = null,
    val deleting: Boolean = false,
    val idiomCandidates: List<P10IdiomRef> = emptyList(),
    val idiomQuery: String = "",
)

/** P10 编辑态纯逻辑：与 core-data 的字段口径同一份判定，不另造规则。 */
object P10EditTransitions {
    private const val OPTION_A = 0
    private const val OPTION_B = 1
    private const val OPTION_C = 2
    private const val OPTION_D = 3
    const val EMPTY_STEM = "题干不能为空"
    const val NO_IDIOM = "至少保留一条归属"
    const val SAVE_FAILED = "保存失败，请重试"
    const val DELETE_FAILED = "删除失败，请重试"
    const val REVIEW_STATE_FAILED = "掌握状态保存失败，请重试"
    const val MISTAKE_NOT_FOUND = "这道错题已不存在，请返回后刷新"
    const val HINT_ANSWER = "我的答案只能是 A/B/C/D"
    const val HINT_YEAR = "年份为 4 位数字"
    const val HINT_TAG = "标签需为 1–8 个字"

    fun draftOf(detail: P10UserExampleDetail) =
        P10EditDraft(
            stem = detail.stem,
            options = detail.options.map { it.orEmpty() },
            answer = detail.myAnswer.orEmpty(),
            reason = detail.reason.orEmpty(),
            tagDraft = "",
            tags = detail.tagNames,
            examType = detail.examType.orEmpty(),
            year = detail.year?.toString().orEmpty(),
            idiomIds = detail.idioms.map { it.id },
        )

    fun fieldErrors(draft: P10EditDraft) =
        P10EditFieldErrors(
            answer = ExampleEditRules.answerInvalid(draft.answer),
            year = ExampleEditRules.yearInvalid(draft.year),
        )

    /** 页面级问题点保存才提示；字段级非法由 errors 在保存前就置灰。 */
    fun pageMessage(draft: P10EditDraft): String? =
        when {
            draft.stem.isBlank() -> EMPTY_STEM
            draft.idiomIds.isEmpty() -> NO_IDIOM
            else -> null
        }

    fun change(
        state: P10EditState,
        edit: (P10EditDraft) -> P10EditDraft,
    ): P10EditState {
        val draft = state.draft ?: return state
        val next = edit(draft)
        return state.copy(draft = next, errors = fieldErrors(next), pageMessage = null, errorMessage = null)
    }

    fun addTag(draft: P10EditDraft): P10EditDraft {
        val tag = draft.tagDraft.trim()
        if (tag.isEmpty() || ExampleEditRules.tagInvalid(tag) || tag in draft.tags) return draft
        return draft.copy(tags = draft.tags + tag, tagDraft = "")
    }

    /** 加入把关：拒收不丢草稿，只给页面级中性提示；合法才追加并清空输入。 */
    fun addTag(state: P10EditState): P10EditState {
        val draft = state.draft ?: return state
        val next = addTag(draft)
        return if (next === draft) {
            state.copy(pageMessage = HINT_TAG, errorMessage = null)
        } else {
            state.copy(draft = next, pageMessage = null)
        }
    }

    fun addIdiom(
        draft: P10EditDraft,
        id: String,
    ): P10EditDraft = if (id.isBlank() || id in draft.idiomIds) draft else draft.copy(idiomIds = draft.idiomIds + id)

    fun nextIdiom(
        draft: P10EditDraft,
        candidates: List<P10IdiomRef>,
    ): P10EditDraft {
        val next = candidates.firstOrNull { it.id !in draft.idiomIds } ?: return draft
        return addIdiom(draft, next.id)
    }

    fun toInput(
        id: String,
        draft: P10EditDraft,
    ) = ExampleEditInput(
        id = id,
        stem = draft.stem.trim(),
        optionA = ExampleEditRules.option(draft.options.getOrNull(OPTION_A)),
        optionB = ExampleEditRules.option(draft.options.getOrNull(OPTION_B)),
        optionC = ExampleEditRules.option(draft.options.getOrNull(OPTION_C)),
        optionD = ExampleEditRules.option(draft.options.getOrNull(OPTION_D)),
        myAnswer = ExampleEditRules.normalizedAnswer(draft.answer),
        examType = draft.examType,
        year = ExampleEditRules.normalizedYear(draft.year),
        reason = draft.reason,
        tagNames = draft.tags,
        idiomIds = draft.idiomIds,
    )
}
