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
 * P08 空归属导入样本快照 × 深浅：备份导入态零归属错题 idiomIds=[]，平铺行成语位显示
 * 「暂未关联成语」而非空白；行保留 userExampleId，仍可点击进入 P10。基线只增不改。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h812dp-xhdpi")
class P08EmptyAssociationSnapshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun capture(night: Boolean) {
        composeTestRule.setContent {
            val base = LocalConfiguration.current
            val nightBits = if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            val config = Configuration(base)
            config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightBits
            CompositionLocalProvider(LocalConfiguration provides config) {
                ChenYuBangTheme(darkTheme = night) {
                    P08MistakeListContent(
                        state =
                            P08MistakeListUiState(
                                total = 1,
                                pending = 1,
                                groups = emptyList(),
                                mistakes =
                                    listOf(
                                        MistakeItemView(
                                            id = "import-no-idiom-1",
                                            stem = "填入最恰当的一项是：面对新问题，一味______只会错失机会。",
                                            recordedAt = "2026-11-24 导入",
                                            idiomWords = emptyList(),
                                            examType = null,
                                            userExampleId = "ue-import-no-idiom-1",
                                        ),
                                    ),
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
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun emptyAssociationLight() = capture(night = false)

    @Test
    fun emptyAssociationDark() = capture(night = true)
}
