package com.chenyubang.feature.review

import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.chenyubang.core.ui.ChenYuBangTheme
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.flow.first
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.random.Random

/** P24 专项快照：每个业务态均由真实 ViewModel 状态流转到达，不直接伪造 dead fixture。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h812dp-xhdpi")
class P24SelfTestSnapshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val library =
        (1..12).map { SelfTestCandidate("id-$it", "成语$it", "这是第 $it 条用于自测的释义") }

    private fun capture(
        night: Boolean,
        scoped: List<SelfTestCandidate> = library.take(1),
        full: List<SelfTestCandidate> = library,
        prepare: suspend (SelfTestViewModel) -> Unit = {},
        afterContent: (SelfTestViewModel) -> Unit = {},
        showExit: Boolean = false,
    ) {
        compose.setContent {
            val base = LocalConfiguration.current
            val config = Configuration(base)
            config.uiMode =
                (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            CompositionLocalProvider(LocalConfiguration provides config) {
                ChenYuBangTheme(darkTheme = night) {
                    val model = remember { SelfTestViewModel({ scoped to full }, Random(21)) }
                    val state by model.state.collectAsState()
                    var confirmExit by remember { mutableStateOf(showExit) }
                    P24SelfTestContent(
                        state = state,
                        onPick = model::pick,
                        onNext = model::next,
                        onRetry = model::retry,
                        onRetryRound = model::retry,
                        onExit = { confirmExit = true },
                    )
                    if (confirmExit) {
                        SelfTestExitOverlay(
                            onContinue = { confirmExit = false },
                            onConfirm = { confirmExit = false },
                        )
                    }
                    LaunchedRound(model, prepare, afterContent)
                }
            }
        }
        compose.waitForIdle()
        val captureNode = if (showExit) compose.onNode(isDialog()) else compose.onRoot()
        captureNode.captureRoboImage()
    }

    @Test
    fun senseToIdiomUnansweredLight() = capture(false)

    @Test
    fun senseToIdiomUnansweredDark() = capture(true)

    @Test
    fun idiomToSenseUnansweredLight() =
        capture(
            false,
            prepare = {
                answerFirstCorrect(it)
                it.next()
            },
        )

    @Test
    fun idiomToSenseUnansweredDark() =
        capture(
            true,
            prepare = {
                answerFirstCorrect(it)
                it.next()
            },
        )

    @Test
    fun correctLight() = capture(false, afterContent = { answerFirstCorrect(it) })

    @Test
    fun correctDark() = capture(true, afterContent = { answerFirstCorrect(it) })

    @Test
    fun selectedWrongLight() = capture(false, afterContent = { answerFirstWrong(it) })

    @Test
    fun selectedWrongDark() = capture(true, afterContent = { answerFirstWrong(it) })

    @Test
    fun summaryLight() = capture(false, prepare = { completeRound(it, correct = true) })

    @Test
    fun summaryDark() = capture(true, prepare = { completeRound(it, correct = true) })

    @Test
    fun emptyScopeLight() = capture(false, scoped = emptyList())

    @Test
    fun emptyScopeDark() = capture(true, scoped = emptyList())

    @Test
    fun insufficientCandidatesLight() = capture(false, full = library.take(3))

    @Test
    fun insufficientCandidatesDark() = capture(true, full = library.take(3))

    @Test
    fun exitConfirmationLight() = capture(false, showExit = true)

    @Test
    fun exitConfirmationDark() = capture(true, showExit = true)

    private fun answerFirstCorrect(model: SelfTestViewModel) {
        val question = model.state.value as SelfTestUiState.Question
        model.pick(question.current.targetId)
    }

    private fun answerFirstWrong(model: SelfTestViewModel) {
        val question = model.state.value as SelfTestUiState.Question
        model.pick(question.current.options.first { it.idiomId != question.current.targetId }.idiomId)
    }

    private suspend fun completeRound(
        model: SelfTestViewModel,
        correct: Boolean,
    ) {
        repeat(10) {
            val state = model.state.value as SelfTestUiState.Question
            val option =
                if (correct) {
                    state.current.targetId
                } else {
                    state.current.options.first { it.idiomId != state.current.targetId }.idiomId
                }
            model.pick(option)
            model.next()
        }
    }
}

@androidx.compose.runtime.Composable
private fun LaunchedRound(
    model: SelfTestViewModel,
    prepare: suspend (SelfTestViewModel) -> Unit,
    afterContent: (SelfTestViewModel) -> Unit,
) {
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(model) {
        model.state.first { it !is SelfTestUiState.Loading }
        prepare(model)
        ready = true
    }
    LaunchedEffect(ready) {
        if (ready) afterContent(model)
    }
}
