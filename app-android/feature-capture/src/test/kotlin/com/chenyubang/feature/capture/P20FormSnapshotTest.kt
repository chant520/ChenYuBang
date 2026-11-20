package com.chenyubang.feature.capture

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

/** P20 多选态快照（规范 §11.1）：新增两选中态基线，不覆盖既有单选态基线。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h812dp-xhdpi")
class P20FormSnapshotTest {
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
                    P20FormContent(state = twoSelectedState(), actions = P20CaptureActions())
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    private fun twoSelectedState(): P20CaptureState =
        P20CaptureState(
            reviewText = "下列各句中，加点的成语使用不恰当的一项是……\nA．新厂长按部就班 B．做事标新立异 C．从不墨守成规",
            candidates =
                listOf(
                    MatchCandidate(ID_BIECHU, "别出心裁", 4, MatchTier.SameQuestion),
                    MatchCandidate(ID_MOSHOU, "墨守成规", 4, MatchTier.Semantic),
                    MatchCandidate(ID_ANBU, "按部就班", 5, MatchTier.Best),
                ),
            checked =
                listOf(
                    IdiomRef(ID_BIECHU, "别出心裁"),
                    IdiomRef(ID_MOSHOU, "墨守成规"),
                ),
            poolSize = 3,
            form =
                P20FormState(
                    isMistake = true,
                    answer = "A",
                    reason = "把贬义词当褒义用了",
                    tags = listOf("褒贬误用", "望文生义"),
                    examType = "省考",
                    yearText = "2025",
                ),
            loading = false,
        )

    @Test
    fun twoSelectedLight() = capture(night = false)

    @Test
    fun twoSelectedDark() = capture(night = true)

    private companion object {
        const val ID_ANBU = "11111111-1111-4111-8111-111111111111"
        const val ID_BIECHU = "22222222-2222-4222-8222-222222222222"
        const val ID_MOSHOU = "33333333-3333-4333-8333-333333333333"
    }
}
