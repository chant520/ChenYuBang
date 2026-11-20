package com.chenyubang.feature.capture

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** P20 ViewModel key 回归：同文本不同 capture 实例不复用表单状态，同实例重组 key 稳定。 */
@RunWith(RobolectricTestRunner::class)
class P20FormViewModelKeyTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val owner =
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }

    @Test
    fun sameTextDifferentInstanceDoesNotReuseFormState() {
        val text = "新厂长按部就班，提出了一整套创新方案"
        val instance = mutableStateOf(11L)
        val seen = mutableListOf<P20FormViewModel>()

        composeTestRule.setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                rememberP20FormViewModel(instance.value, text).also { seen.add(it) }
            }
        }
        composeTestRule.runOnIdle {
            seen.single().form.setAnswer("A")
            assertEquals("A", seen.single().state.value.form.answer)
            instance.value = 12L
        }
        composeTestRule.runOnIdle {
            assertEquals(2, seen.size)
            assertNotSame(seen[0], seen[1])
            assertNull(seen[1].state.value.form.answer)
        }
    }

    @Test
    fun keyIsStablePerInstanceAndIsolatedAcrossInstances() {
        val text = "同一段识别文字"
        assertEquals(p20FormViewModelKey(21L, text), p20FormViewModelKey(21L, text))
        assertNotEquals(p20FormViewModelKey(21L, text), p20FormViewModelKey(22L, text))
    }
}
