package com.chenyubang.feature.mistake

import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.chenyubang.core.ui.ChenYuBangTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * P08 错题列表黄金快照：三视图 + 无错题空态 + 待复习已清空态，各浅/深一张。基线只增不改。
 *
 * `*ReviewCount*` 对应「开始复习」按钮带待复习数文案（followup p08-p09 A2，前代 `grouped*`/
 * `byTime*`/`byExamType*`/`toReviewCleared*` 留作修前历史）；`emptyCamIcon*` 对应相机图标改
 * 设计规范 v1.3 三段值（A9，前代 `empty*` 留作修前历史）。
 *
 * toReviewClearedReviewCount* 两帧为构造态：pending == 0 需 3.1 的掌握状态切换写入后才会真实可达，本包（2.6）无路径造出该状态。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h812dp-xhdpi")
class P08MistakeListSnapshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun capture(
        night: Boolean,
        state: P08MistakeListUiState,
        viewMode: MistakeViewMode,
    ) {
        composeTestRule.setContent {
            val base = LocalConfiguration.current
            val nightBits = if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            val config = Configuration(base)
            config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightBits
            CompositionLocalProvider(LocalConfiguration provides config) {
                ChenYuBangTheme(darkTheme = night) {
                    P08MistakeListContent(
                        state = state,
                        viewMode = viewMode,
                        actions =
                            P08MistakeActions(
                                onViewModeChange = {},
                                onOpenIdiom = {},
                                onOpenMistake = {},
                                onCapture = {},
                            ),
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    private fun loaded(): P08MistakeListUiState =
        P08MistakeListUiState(
            total = 12,
            pending = 7,
            groups =
                listOf(
                    MistakeIdiomGroupView("idiom-anbu", "按部就班", 3, "望文生义 2 · 对象误用 1"),
                    MistakeIdiomGroupView("idiom-biechu", "别出心裁", 2, "对象误用 2"),
                    MistakeIdiomGroupView("idiom-moshou", "墨守成规", 1, null),
                ),
            mistakes =
                listOf(
                    MistakeItemView(
                        id = "m-1",
                        stem = "依次填入划横线部分最恰当的一项是：做事不可______，应当有新办法。",
                        recordedAt = "2026-11-14 录入",
                        idiomWords = listOf("按部就班"),
                        examType = "国考",
                    ),
                    MistakeItemView(
                        id = "m-2",
                        stem = "下列句子中加点成语使用不恰当的一项是：他的设计别出心裁，令人眼前一亮。",
                        recordedAt = "2026-11-12 录入",
                        idiomWords = listOf("别出心裁", "标新立异"),
                        examType = "省考",
                    ),
                    MistakeItemView(
                        id = "m-3",
                        stem = "填入最恰当的一项是：面对新问题，一味______只会错失机会。",
                        recordedAt = "2026-11-09 录入",
                        idiomWords = listOf("墨守成规"),
                        examType = null,
                    ),
                ),
            loading = false,
        )

    private fun empty(): P08MistakeListUiState = P08MistakeListUiState(loading = false)

    private fun cleared(): P08MistakeListUiState = loaded().copy(pending = 0)

    @Test
    fun groupedReviewCountLight() = capture(night = false, state = loaded(), viewMode = MistakeViewMode.ByIdiom)

    @Test
    fun groupedReviewCountDark() = capture(night = true, state = loaded(), viewMode = MistakeViewMode.ByIdiom)

    @Test
    fun byTimeReviewCountLight() = capture(night = false, state = loaded(), viewMode = MistakeViewMode.ByTime)

    @Test
    fun byTimeReviewCountDark() = capture(night = true, state = loaded(), viewMode = MistakeViewMode.ByTime)

    @Test
    fun byExamTypeReviewCountLight() = capture(night = false, state = loaded(), viewMode = MistakeViewMode.ByExamType)

    @Test
    fun byExamTypeReviewCountDark() = capture(night = true, state = loaded(), viewMode = MistakeViewMode.ByExamType)

    @Test
    fun emptyCamIconLight() = capture(night = false, state = empty(), viewMode = MistakeViewMode.ByIdiom)

    @Test
    fun emptyCamIconDark() = capture(night = true, state = empty(), viewMode = MistakeViewMode.ByIdiom)

    @Test
    fun toReviewClearedReviewCountLight() =
        capture(night = false, state = cleared(), viewMode = MistakeViewMode.ByIdiom)

    @Test
    fun toReviewClearedReviewCountDark() = capture(night = true, state = cleared(), viewMode = MistakeViewMode.ByIdiom)
}
