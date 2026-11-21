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

/**
 * P20 快照（规范 §11.1）：基线只增不改。
 * `twoSelected*` 是 2.5 既有基线，对应 iOS `P20MatchSnapshotTests.testFormMain*`；
 * 本轮补候选空态（对应 iOS `testFormEmpty*`）。
 * iOS 另有 `testFormSection*` 单独渲染表单分区：Android 侧该分区没有 testTag，整页基线已含
 * FormFieldsCard，故不单独镜像——对齐的是状态语义，不是文件名一一对应。
 *
 * 两条 2.5 反馈条（候选上限、保存失败）不做整页快照：它们是 `P20FormContent` 里
 * `verticalScroll` 内容的第五、六个子节点，落在 812dp 视口之外，整页图与 `twoSelected*` 逐字节相同
 * （录出来验过），钉不住任何东西；构件外观已由 `CBarSnapshotTest` 的 barInfo 与 barError 两组基线钉住，
 * 「哪个状态出哪条」由 `P20FormViewModelTest` 的 info / saveError 断言钉住。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h812dp-xhdpi")
class P20FormSnapshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun capture(
        state: P20CaptureState,
        night: Boolean,
    ) {
        composeTestRule.setContent {
            val base = LocalConfiguration.current
            val nightBits = if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            val config = Configuration(base)
            config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightBits
            CompositionLocalProvider(LocalConfiguration provides config) {
                ChenYuBangTheme(darkTheme = night) {
                    P20FormContent(state = state, actions = P20CaptureActions())
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

    /**
     * 候选空态：有识别文字但零命中，候选区出「手动挑成语」入口（iOS testFormEmpty* 同口径文案）。
     * 本组基线如实钉住当前渲染，其中两处已知缺陷已写进 `.workbuddy/android-2.5-followup-2026-11-21.md`：
     * ①`CandidateCard` 底部的 `CButton(MANUAL_ACTION)` 无条件渲染，空态下与 `CEmptyState` 自带的动作
     * 按钮叠成两个同名入口（iOS 只在空态渲染一个）；②空态版式最短，首次把表单区滚进视口，暴露
     * 自定义标签行 `Row` 未 `fillMaxWidth` 导致 `CInput` 的 `weight(1f)` 塌成近零宽、占位符逐字断行。
     * WorkBuddy 修掉后按惯例换新测试名，本组 PNG 不覆盖。
     */
    private fun noMatchState(): P20CaptureState =
        P20CaptureState(
            reviewText = "今天天气不错，我们出去散步吧。",
            loading = false,
        )

    @Test
    fun twoSelectedLight() = capture(twoSelectedState(), night = false)

    @Test
    fun twoSelectedDark() = capture(twoSelectedState(), night = true)

    @Test
    fun noMatchLight() = capture(noMatchState(), night = false)

    @Test
    fun noMatchDark() = capture(noMatchState(), night = true)

    private companion object {
        const val ID_ANBU = "11111111-1111-4111-8111-111111111111"
        const val ID_BIECHU = "22222222-2222-4222-8222-222222222222"
        const val ID_MOSHOU = "33333333-3333-4333-8333-333333333333"
    }
}
