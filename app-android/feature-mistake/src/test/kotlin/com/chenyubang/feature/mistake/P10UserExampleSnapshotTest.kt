package com.chenyubang.feature.mistake

import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * P10 录入题详情黄金快照（基线只增不改，先审阅再 record）。
 *
 * 视口帧 5 态 × 浅/深：标错默认（遮住）、已显示答案、重做中（选中 C 高亮）、对答案后、未标错普通详情；
 * 本机不注入二进制图片，每帧图片位都是「原图已不在」橙占位。未标错两帧同时是「本页无底部红动作」的视觉基线
 * （信息架构 v1.4 版内修订第 ④ 条，用户 2026-11-22 拍板）：底部红描边删除行整行不出现，删除入口只留顶部置灰「删除」。
 *
 * 本页是长页，812dp 固定视口帧只排到区块 ⑨ 首行，滚动区外的 ⑦–⑩ 与底部删除行拍不到；
 * 故另出 blocks* 四帧（标错浅/深 + 未标错浅/深），直接喂无滚动的 P10UserExampleBody 按自然高度单帧覆盖整页，
 * 避免交一份与旧基线逐字节相同的半页假覆盖；未标错两帧同时给出「页底无红描边行」的视觉证据。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h812dp-xhdpi")
class P10UserExampleSnapshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    /** fullPage=true 时喂无滚动正文，配合方法级高视口 qualifier 拍自然高度整页帧。 */
    private fun capture(
        state: P10UserExampleUiState,
        night: Boolean,
        fullPage: Boolean = false,
    ) {
        composeTestRule.setContent {
            val base = LocalConfiguration.current
            val nightBits = if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            val config = Configuration(base)
            config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightBits
            CompositionLocalProvider(LocalConfiguration provides config) {
                if (fullPage) {
                    P10UserExampleBody(state = state)
                } else {
                    P10UserExampleContent(state = state)
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    private fun detail(
        myAnswer: String? = "B",
        isMistake: Boolean = true,
        imagePath: String? = "images/ue-1.jpg",
    ) = P10UserExampleDetail(
        id = "ue-1",
        stem = "下列各句中，加点的成语使用不恰当的一项是：做事不可按部就班，应当有新办法。",
        options = listOf("按部就班", "墨守成规", "别出心裁", "标新立异"),
        myAnswer = myAnswer,
        isMistake = isMistake,
        examType = "国考",
        year = 2026,
        reason = "望文生义",
        tagNames = listOf("对象误用", "高频"),
        idioms = listOf(P10IdiomRef("id-anbu", "按部就班"), P10IdiomRef("id-biechu", "别出心裁")),
        imagePath = imagePath,
    )

    /** 显式置 imageUnreadable：快照不注入 Bitmap，保持 14 帧都渲染「原图已不在」橙占位（P13）。 */
    private fun base(
        myAnswer: String? = "B",
        isMistake: Boolean = true,
    ) = P10UserExampleUiState(detail = detail(myAnswer, isMistake), loading = false, imageUnreadable = true)

    @Test
    fun mistakeMaskedLight() = capture(base(), night = false)

    @Test
    fun mistakeMaskedDark() = capture(base(), night = true)

    @Test
    fun revealedLight() = capture(base().copy(revealed = true), night = false)

    @Test
    fun revealedDark() = capture(base().copy(revealed = true), night = true)

    @Test
    fun rerollingLight() = capture(base().copy(rerolling = true, picked = 2), night = false)

    @Test
    fun rerollingDark() = capture(base().copy(rerolling = true, picked = 2), night = true)

    @Test
    fun resultLight() = capture(base().copy(rerolling = true, picked = 2, checked = true), night = false)

    @Test
    fun resultDark() = capture(base().copy(rerolling = true, picked = 2, checked = true), night = true)

    /** 未标错普通详情：整页零红动作，底部删除行不出现（P12 视觉基线）。 */
    @Test
    fun plainDetailLight() = capture(base(isMistake = false), night = false)

    @Test
    fun plainDetailDark() = capture(base(isMistake = false), night = true)

    /** 自然高度整页帧：高视口 qualifier 保证长页不被 812dp 截断，拍到 ⑤–⑩ 与底部删除行。 */
    @Config(sdk = [34], qualifiers = "w360dp-h1800dp-xhdpi")
    @Test
    fun blocksLight() = capture(base().copy(revealed = true), night = false, fullPage = true)

    @Config(sdk = [34], qualifiers = "w360dp-h1800dp-xhdpi")
    @Test
    fun blocksDark() = capture(base().copy(revealed = true), night = true, fullPage = true)

    /** 未标错整页帧：底部删除行在滚动区外，只有整页帧才能给出「零红动作」的视觉证据。 */
    @Config(sdk = [34], qualifiers = "w360dp-h1800dp-xhdpi")
    @Test
    fun blocksUnmarkedLight() = capture(base(isMistake = false), night = false, fullPage = true)

    @Config(sdk = [34], qualifiers = "w360dp-h1800dp-xhdpi")
    @Test
    fun blocksUnmarkedDark() = capture(base(isMistake = false), night = true, fullPage = true)

    // —— 2.8 / C8 新增基线：编辑态整页、字段级非法、写库失败、表单两模式、未标错表单、保存中、两套确认弹层 ——

    private fun editing(
        isMistake: Boolean = true,
        draft: P10EditDraft = P10EditTransitions.draftOf(detail(isMistake = isMistake)),
    ) = base(isMistake = isMistake).copy(edit = P10EditState(editing = true, draft = draft))

    @Config(sdk = [34], qualifiers = "w360dp-h1800dp-xhdpi")
    @Test
    fun editFormLight() = capture(editing(), night = false, fullPage = true)

    @Config(sdk = [34], qualifiers = "w360dp-h1800dp-xhdpi")
    @Test
    fun editFormDark() = capture(editing(), night = true, fullPage = true)

    @Test
    fun editFormViewportLight() = capture(editing(), night = false)

    @Test
    fun editFormViewportDark() = capture(editing(), night = true)

    @Config(sdk = [34], qualifiers = "w360dp-h1800dp-xhdpi")
    @Test
    fun editInvalidLight() =
        capture(
            editing(
                draft =
                    P10EditTransitions.draftOf(detail()).copy(
                        answer = "E",
                        year = "20",
                        tagDraft = "九个字的超长标签内容啊",
                    ),
            ).let { it.copy(edit = it.edit.copy(errors = P10EditTransitions.fieldErrors(it.edit.draft!!))) },
            night = false,
            fullPage = true,
        )

    @Config(sdk = [34], qualifiers = "w360dp-h1800dp-xhdpi")
    @Test
    fun editInvalidDark() =
        capture(
            editing(
                draft =
                    P10EditTransitions.draftOf(detail()).copy(
                        answer = "E",
                        year = "20",
                        tagDraft = "九个字的超长标签内容啊",
                    ),
            ).let { it.copy(edit = it.edit.copy(errors = P10EditTransitions.fieldErrors(it.edit.draft!!))) },
            night = true,
            fullPage = true,
        )

    @Test
    fun editSaveFailedLight() =
        capture(
            editing().let { it.copy(edit = it.edit.copy(errorMessage = P10EditTransitions.SAVE_FAILED)) },
            night = false,
        )

    @Test
    fun editSaveFailedDark() =
        capture(
            editing().let { it.copy(edit = it.edit.copy(errorMessage = P10EditTransitions.SAVE_FAILED)) },
            night = true,
        )

    @Config(sdk = [34], qualifiers = "w360dp-h1800dp-xhdpi")
    @Test
    fun editUnmarkedLight() = capture(editing(isMistake = false), night = false, fullPage = true)

    @Config(sdk = [34], qualifiers = "w360dp-h1800dp-xhdpi")
    @Test
    fun editUnmarkedDark() = capture(editing(isMistake = false), night = true, fullPage = true)

    @Config(sdk = [34], qualifiers = "w360dp-h1800dp-xhdpi")
    @Test
    fun editSavingLight() =
        capture(
            editing().let { it.copy(edit = it.edit.copy(saving = true)) },
            night = false,
            fullPage = true,
        )

    @Config(sdk = [34], qualifiers = "w360dp-h1800dp-xhdpi")
    @Test
    fun editSavingDark() =
        capture(
            editing().let { it.copy(edit = it.edit.copy(saving = true)) },
            night = true,
            fullPage = true,
        )

    @Test
    fun confirmMarkedLight() =
        capture(
            base().copy(edit = P10EditState(deleteConfirm = P10DeleteTarget.Marked)),
            night = false,
        )

    @Test
    fun confirmMarkedDark() =
        capture(
            base().copy(edit = P10EditState(deleteConfirm = P10DeleteTarget.Marked)),
            night = true,
        )

    @Test
    fun confirmUnmarkedLight() =
        capture(
            base(isMistake = false).copy(edit = P10EditState(deleteConfirm = P10DeleteTarget.Unmarked)),
            night = false,
        )

    @Test
    fun confirmUnmarkedDark() =
        capture(
            base(isMistake = false).copy(edit = P10EditState(deleteConfirm = P10DeleteTarget.Unmarked)),
            night = true,
        )
}
