package com.chenyubang.feature.mistake

import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.ImageBitmap
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
 * P10 录入题详情黄金快照（规范 5 态 × 浅/深）：标错默认（遮住）、未标错普通详情、
 * 重做中（已选 C 高亮）、对答案后、原图缺失。基线只增不改，先审阅再 record。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h812dp-xhdpi")
class P10UserExampleSnapshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun capture(
        state: P10UserExampleUiState,
        night: Boolean,
        withImage: Boolean = false,
    ) {
        composeTestRule.setContent {
            val base = LocalConfiguration.current
            val nightBits = if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            val config = Configuration(base)
            config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightBits
            CompositionLocalProvider(LocalConfiguration provides config) {
                P10UserExampleContent(
                    state = state,
                    image = if (withImage) ImageBitmap(200, 120) else null,
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    private fun detail(
        myAnswer: String? = "B",
        isMistake: Boolean = true,
        imagePath: String? = null,
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

    private fun base(
        myAnswer: String? = "B",
        isMistake: Boolean = true,
        imagePath: String? = "images/ue-1.jpg",
    ) = P10UserExampleUiState(detail = detail(myAnswer, isMistake, imagePath), loading = false)

    @Test
    fun mistakeMaskedLight() = capture(base(), night = false, withImage = true)

    @Test
    fun mistakeMaskedDark() = capture(base(), night = true, withImage = true)

    @Test
    fun plainDetailLight() = capture(base(isMistake = false), night = false, withImage = true)

    @Test
    fun plainDetailDark() = capture(base(isMistake = false), night = true, withImage = true)

    @Test
    fun rerollingLight() = capture(base().copy(rerolling = true, picked = 2), night = false, withImage = true)

    @Test
    fun rerollingDark() = capture(base().copy(rerolling = true, picked = 2), night = true, withImage = true)

    @Test
    fun resultLight() =
        capture(base().copy(rerolling = true, picked = 2, checked = true), night = false, withImage = true)

    @Test
    fun resultDark() =
        capture(base().copy(rerolling = true, picked = 2, checked = true), night = true, withImage = true)

    @Test
    fun imageMissingLight() = capture(base(imagePath = "images/ue-1.jpg").copy(imageUnreadable = true), night = false)

    @Test
    fun imageMissingDark() = capture(base(imagePath = "images/ue-1.jpg").copy(imageUnreadable = true), night = true)
}
