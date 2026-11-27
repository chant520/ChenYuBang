package com.chenyubang.feature.mistake

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 用户数据长列表只组合可见行：P08 错题与 P09 录入题均以 id 为 key 的 LazyColumn。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h812dp-xhdpi")
class UserDataLazyListTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun p08DoesNotComposeOffscreenMistakeRows() {
        composeTestRule.setContent {
            P08MistakeListContent(
                state =
                    P08MistakeListUiState(
                        total = 60,
                        pending = 30,
                        mistakes =
                            (1..60).map {
                                MistakeItemView(
                                    id = "m-$it",
                                    stem = "错题题干 $it",
                                    recordedAt = "2026-11-14 录入",
                                    idiomWords = emptyList(),
                                    examType = null,
                                )
                            },
                        loading = false,
                    ),
                viewMode = MistakeViewMode.ByTime,
                actions =
                    P08MistakeActions(
                        onViewModeChange = {},
                        onOpenIdiom = {},
                        onOpenMistake = {},
                        onCapture = {},
                    ),
            )
        }
        assertTrue(composeTestRule.onAllNodesWithText("错题题干 60").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun p09DoesNotComposeOffscreenExampleRows() {
        composeTestRule.setContent {
            P09IdiomMistakesContent(
                state =
                    P09IdiomMistakesUiState(
                        idiomId = "idiom-a",
                        word = "按部就班",
                        rows =
                            (1..60).map {
                                IdiomUserExampleView(
                                    id = "ue-$it",
                                    stem = "录入题干 $it",
                                    recordedAt = "2026-11-14 录入",
                                    mistake = null,
                                )
                            },
                        loading = false,
                    ),
                onBack = {},
                onOpenIdiom = {},
                onRetry = {},
                onOpenExample = {},
            )
        }
        assertTrue(composeTestRule.onAllNodesWithText("录入题干 60").fetchSemanticsNodes().isEmpty())
    }
}
