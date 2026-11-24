package com.chenyubang.feature.idiom

import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.chenyubang.core.data.ExamScopeSelection
import com.chenyubang.core.ui.ChenYuBangTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * P03 成语库页面黄金快照（规范 §11.1）：浅/深各一张。
 * 固定「省考勾选、省份默认全选」这一默认组合，同时覆盖范围卡同排布局、列表、字母索引与末尾计数。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h812dp-xhdpi")
class P03LibrarySnapshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun capture(
        night: Boolean,
        updateFailed: Boolean = false,
    ) {
        composeTestRule.setContent {
            val base = LocalConfiguration.current
            val nightBits = if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            val config = Configuration(base)
            config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightBits
            CompositionLocalProvider(LocalConfiguration provides config) {
                ChenYuBangTheme(darkTheme = night) {
                    P03LibraryContent(state = state(updateFailed))
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    private fun state(updateFailed: Boolean = false): P03LibraryUiState {
        val items = SAMPLE
        return P03LibraryUiState(
            scope =
                ExamScopeSelection(
                    national = true,
                    provincial = true,
                    institution = true,
                    provinces = setOf("北京", "广东", "河南", "贵州"),
                ),
            dimension = LibraryDimension.Scope,
            selectedLetter = null,
            letters = LibraryBrowse.letters(items),
            page = LibraryBrowse.page(items, LibraryDimension.Scope, null),
            loading = false,
            updateFailed = updateFailed,
        )
    }

    private companion object {
        /** 仅用于快照的排版样本，不代表内置题库内容。 */
        val SAMPLE: List<LibraryIdiom> =
            listOf(
                LibraryIdiom("s1", "高屋建瓴", "gāo wū jiàn líng", "GWJL", 5),
                LibraryIdiom("s3", "汗牛充栋", "hàn niú chōng dòng", "HNCD", 3),
                LibraryIdiom("s4", "讳莫如深", "huì mò rú shēn", "HMRS", 2),
                LibraryIdiom("s5", "见微知著", "jiàn wēi zhī zhù", "JWZZ", 4),
                LibraryIdiom("s6", "刻舟求剑", "kè zhōu qiú jiàn", "KZQJ", 2),
            )
    }

    @Test
    fun libraryLight() = capture(night = false)

    @Test
    fun libraryDark() = capture(night = true)

    @Test
    fun libraryUpdateFailedLight() = capture(night = false, updateFailed = true)

    @Test
    fun libraryUpdateFailedDark() = capture(night = true, updateFailed = true)
}
