package com.chenyubang.feature.capture

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * P20 表单状态机：答案 / 错因 / 标签 / 是否做错 / 来源 / 保存态。
 * 错因与标签仅标错可用并落库；取消做错时清空。
 */
class P20FormController(
    private val stateFlow: MutableStateFlow<P20FormState>,
) {
    /** 只接受 A/B/C/D 单字母；再点当前答案即取消（→ null）。 */
    fun setAnswer(raw: String) {
        val next = raw.uppercase().filter { it in ANSWER_KEYS }.take(1).ifEmpty { null }
        stateFlow.update { form ->
            form.copy(answer = if (next == form.answer) null else next)
        }
    }

    fun setReason(raw: String) = stateFlow.update { form -> if (form.isMistake) form.copy(reason = raw) else form }

    fun setTagDraft(raw: String) = stateFlow.update { it.copy(tagDraft = raw) }

    /** 标签 1–8 字、不重复；仅标错时可加，返回是否加入成功。 */
    fun addTag(): Boolean {
        val form = stateFlow.value
        val tag = form.tagDraft.trim()
        val accepted = form.isMistake && tag.length in TAG_MIN_LENGTH..TAG_MAX_LENGTH && tag !in form.tags
        if (accepted) {
            stateFlow.update { form -> form.copy(tags = form.tags + tag, tagDraft = "", saveError = null) }
        }
        return accepted
    }

    fun removeTag(tag: String) =
        stateFlow.update { form -> if (form.isMistake) form.copy(tags = form.tags.filterNot { it == tag }) else form }

    /** 取消勾选「做错」时清空错因与标签。 */
    fun toggleMistake() =
        stateFlow.update { form ->
            val next = !form.isMistake
            form.copy(
                isMistake = next,
                reason = if (next) form.reason else "",
                tags = if (next) form.tags else emptyList(),
                tagDraft = if (next) form.tagDraft else "",
                saveError = null,
            )
        }

    fun setExamType(value: String?) = stateFlow.update { it.copy(examType = value, saveError = null) }

    fun setYear(raw: String) =
        stateFlow.update {
            it.copy(
                yearText = raw.filter { digit -> digit.isDigit() }.take(YEAR_TEXT_LENGTH),
                saveError = null,
            )
        }

    /** 单一保存态入口：saving=true 进入加载；saving=false + error 提示失败并留在本页。 */
    fun setSaveState(
        saving: Boolean,
        error: String? = null,
    ) = stateFlow.update { it.copy(saving = saving, saveError = error) }

    private companion object {
        const val ANSWER_KEYS = "ABCD"
        const val TAG_MIN_LENGTH = 1
        const val TAG_MAX_LENGTH = 8
        const val YEAR_TEXT_LENGTH = 4
    }
}
