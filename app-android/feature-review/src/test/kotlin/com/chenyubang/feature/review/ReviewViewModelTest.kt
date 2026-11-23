package com.chenyubang.feature.review

import com.chenyubang.core.data.ReviewIdiom
import com.chenyubang.core.data.ReviewQuestion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** ReviewViewModel 落库兜底：失败置错误态、可重试、取消不被吞。 */
class ReviewViewModelTest {
    private val options = listOf("按部就班", "墨守成规", "别出心裁", "标新立异")

    private fun question() =
        ReviewQuestion(
            mistakeId = "m-1",
            stem = "依次填入划横线部分最恰当的一项是：做事不可______，应当有新办法。",
            options = options,
            myAnswer = "B",
            idioms = listOf(ReviewIdiom("i-1", "按部就班")),
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun recordFailureSetsErrorStateAndRetryRecovers() =
        runBlocking {
            var shouldFail = true
            var calls = 0
            val model =
                ReviewViewModel(
                    scopeIdiomId = null,
                    loadQueue = { listOf(question()) },
                    record = { _, _, _ ->
                        calls++
                        if (shouldFail) throw RuntimeException("write failed")
                    },
                )
            // 首次 B、选 C＝答对，判分落库抛异常。
            model.pick(2)
            yield()
            assertTrue(model.state.value.recordFailed)
            assertEquals(1, calls)

            shouldFail = false
            model.retryRecord()
            yield()
            assertFalse(model.state.value.recordFailed)
            assertEquals(2, calls)
        }
}
