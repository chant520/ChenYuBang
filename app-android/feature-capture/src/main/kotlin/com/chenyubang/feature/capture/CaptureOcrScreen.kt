package com.chenyubang.feature.capture

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.chenyubang.core.ui.tokens.Tokens
import com.chenyubang.ocr.OcrEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * P19 识别结果（信息架构 v1.4 §3，第 3 步；规范 §4.5）：
 * 对 P18 导出的裁切图跑端侧识别，结果落到可编辑的题干与 A–D 选项；
 * 识别不到文字走失败态三条退路；返回回 P18 且框选范围保留。
 */
@Composable
fun CaptureOcrScreen(
    cropPath: String,
    engine: OcrEngine,
    initialDraft: OcrDraft?,
    actions: OcrScreenActions,
    modifier: Modifier = Modifier,
) {
    val colors = if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    val edit = rememberSaveable(saver = OcrEditSaver) { OcrEdit(initialDraft) }
    var thumbnail by remember { mutableStateOf<ImageBitmap?>(null) }
    var zoom by remember { mutableStateOf(false) }

    LaunchedEffect(cropPath) {
        thumbnail = withContext(Dispatchers.IO) { CropImage.load(cropPath) }?.asImageBitmap()
    }
    LaunchedEffect(cropPath, edit.attempt) { edit.recognize(engine, cropPath) }

    Column(modifier.fillMaxSize().background(colors.page)) {
        OcrHeader(onBack = actions.onBack, colors = colors)
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                .padding(
                    start = Tokens.Space.pageX.dp,
                    end = Tokens.Space.pageX.dp,
                    top = Tokens.Space.ocrTop.dp,
                    bottom = Tokens.Space.pageX.dp,
                ),
        ) {
            OcrThumbRow(thumbnail, edit.lineCount, colors) { zoom = true }
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line2))
            Spacer(Modifier.height(Tokens.Space.ocrRowBottom.dp))
            when {
                edit.scanning -> OcrScanning(colors)
                edit.failed -> OcrFailure(colors, actions)
                else ->
                    OcrFields(
                        draft = edit.draft(),
                        colors = colors,
                        onChange = { edit.apply(it) },
                        onRerun = { edit.rerun() },
                        onNext = { actions.onNext(edit.trimmed()) },
                    )
            }
        }
    }
    val preview = thumbnail
    if (zoom && preview != null) {
        OcrZoomDialog(preview, colors) { zoom = false }
    }
}

/** P19 的可编辑文本与识别进度；跨 P19→P20→返回 由 rememberSaveable 保管。 */
private class OcrEdit(initial: OcrDraft?) {
    var stem by mutableStateOf(initial?.stem.orEmpty())
    var options by mutableStateOf(initial?.paddedOptions() ?: List(OcrOptionKeys.size) { "" })
    var loaded by mutableStateOf(initial != null)
    var attempt by mutableStateOf(0)
    var lineCount by mutableStateOf(0)
    var failed by mutableStateOf(false)
    var scanning by mutableStateOf(false)

    fun draft(): OcrDraft = OcrDraft(stem = stem, options = options)

    fun apply(draft: OcrDraft) {
        stem = draft.stem
        options = draft.paddedOptions()
    }

    fun trimmed(): OcrDraft = OcrDraft(stem = stem.trim(), options = options.map { it.trim() })

    fun rerun() {
        loaded = false
        attempt++
    }

    suspend fun recognize(
        engine: OcrEngine,
        path: String,
    ) {
        if (loaded) return
        scanning = true
        val result = engine.recognize(path)
        lineCount = result.rawLines.size
        failed = result.isEmpty
        if (!result.isEmpty) {
            stem = result.stem.orEmpty()
            options = OcrOptionKeys.map { key -> result.options[key].orEmpty() }
        }
        loaded = true
        scanning = false
    }
}

private const val SAVED_STEM = 0
private const val SAVED_OPTIONS = 1
private const val SAVED_LOADED = 2
private const val SAVED_ATTEMPT = 3
private const val SAVED_LINE_COUNT = 4
private const val SAVED_FAILED = 5

private val OcrEditSaver =
    listSaver<OcrEdit, Any>(
        save = { listOf(it.stem, ArrayList(it.options), it.loaded, it.attempt, it.lineCount, it.failed) },
        restore = { saved ->
            OcrEdit(null).apply {
                stem = saved[SAVED_STEM] as String
                @Suppress("UNCHECKED_CAST")
                options = saved[SAVED_OPTIONS] as List<String>
                loaded = saved[SAVED_LOADED] as Boolean
                attempt = saved[SAVED_ATTEMPT] as Int
                lineCount = saved[SAVED_LINE_COUNT] as Int
                failed = saved[SAVED_FAILED] as Boolean
            }
        },
    )
