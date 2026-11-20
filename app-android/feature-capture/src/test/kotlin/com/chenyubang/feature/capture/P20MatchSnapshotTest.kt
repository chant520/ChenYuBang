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

/** P20 黄金快照（规范 §11.1）：浅/深主态 + 浅/深空态，四张新基线，不覆盖基线 C/D 图。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h812dp-xhdpi")
class P20MatchSnapshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun capture(
        night: Boolean,
        empty: Boolean,
    ) {
        composeTestRule.setContent {
            val base = LocalConfiguration.current
            val nightBits = if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            val config = Configuration(base)
            config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightBits
            CompositionLocalProvider(LocalConfiguration provides config) {
                ChenYuBangTheme(darkTheme = night) {
                    P20MatchContent(
                        state = if (empty) emptyState() else mainState(),
                        actions =
                            P20MatchActions(
                                onEdit = {},
                                onSelect = {},
                                onCycle = {},
                                onAdd = {},
                                onToggleDelete = {},
                                onDelete = {},
                                onOpenManual = {},
                                onDismissManual = {},
                                onManualPick = {},
                                onSave = {},
                                onDismissInfo = {},
                                manualPicker = { _, _ -> },
                            ),
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    private fun mainState(): P20MatchUiState =
        P20MatchUiState(
            reviewText = "下列各句中，加点的成语使用不恰当的一项是……\nA．新厂长按部就班 B．做事标新立异 C．从不墨守成规",
            candidates =
                listOf(
                    MatchCandidate(ID_ANBU, "按部就班", 5, MatchTier.Best),
                    MatchCandidate(ID_MOSHOU, "墨守成规", 4, MatchTier.SameQuestion),
                    MatchCandidate(ID_BIECHU, "别出心裁", 4, MatchTier.Semantic),
                ),
            checkedId = ID_ANBU,
            poolSize = 3,
            loading = false,
        )

    private fun emptyState(): P20MatchUiState =
        P20MatchUiState(
            reviewText = "今天天气不错，我们出去散步吧。",
            loading = false,
        )

    @Test
    fun mainLight() = capture(night = false, empty = false)

    @Test
    fun mainDark() = capture(night = true, empty = false)

    @Test
    fun emptyLight() = capture(night = false, empty = true)

    @Test
    fun emptyDark() = capture(night = true, empty = true)

    private companion object {
        const val ID_ANBU = "11111111-1111-4111-8111-111111111111"
        const val ID_BIECHU = "22222222-2222-4222-8222-222222222222"
        const val ID_MOSHOU = "33333333-3333-4333-8333-333333333333"
    }
}
