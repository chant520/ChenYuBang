package com.chenyubang.feature.capture

import com.chenyubang.core.data.CaptureSaveInput
import com.chenyubang.core.data.SavedCapture
import com.chenyubang.core.data.SavedIdiom
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 把 P20 识别草稿与表单状态映射成保存输入。 */
internal fun buildInput(
    draft: OcrDraft,
    deps: P20FormDeps,
    state: P20CaptureState,
): CaptureSaveInput {
    val options = draft.paddedOptions()
    return CaptureSaveInput(
        imagePath = deps.imagePath,
        ocrText = P20Matcher.mergedText(draft),
        stem = draft.stem,
        optionA = options[0].ifEmpty { null },
        optionB = options[1].ifEmpty { null },
        optionC = options[2].ifEmpty { null },
        optionD = options[3].ifEmpty { null },
        myAnswer = state.form.answer,
        isMistake = state.form.isMistake,
        reason = state.form.reason,
        tagNames = state.form.tags,
        examType = state.form.examType,
        year = state.form.year,
        idioms = state.checked.map { SavedIdiom(it.id, it.word) },
    )
}

/**
 * 异步保存：进入 saving 态、后台事务落库；失败只留错误条不跳页，取消不吞。
 * 成功回执带 userExampleId 与有序归属，交给路由跳 P21。
 */
internal suspend fun performSave(
    draft: OcrDraft,
    deps: P20FormDeps,
    state: P20CaptureState,
    model: P20FormViewModel,
    onSaved: (String, List<IdiomRef>) -> Unit,
) {
    model.form.setSaveState(saving = true)
    try {
        val saved: SavedCapture = withContext(Dispatchers.IO) { deps.saver.save(buildInput(draft, deps, state)) }
        model.form.setSaveState(saving = false)
        onSaved(saved.userExampleId, saved.idioms.map { IdiomRef(it.id, it.word) })
    } catch (cancelled: CancellationException) {
        model.form.setSaveState(saving = false)
        throw cancelled
    } catch (_: Exception) {
        model.form.setSaveState(saving = false, error = P20Matcher.SAVE_FAILED)
    }
}
