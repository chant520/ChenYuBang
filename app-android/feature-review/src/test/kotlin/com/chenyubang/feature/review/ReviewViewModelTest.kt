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
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ReviewViewModelTest {
    private val options = listOf("按部就班", "墨守成规", "别出心裁", "标新立异")

    private fun question(
        id: String = "m-1",
        myAnswer: String? = "B",
    ) = ReviewQuestion(
        mistakeId = id,
        stem = "题干 $id",
        options = options,
        myAnswer = myAnswer,
        idioms = listOf(ReviewIdiom("i-$id", "词$id")),
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
                        if (shouldFail) error("write failed")
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

    @Test
    fun uncomparableSkipsRecordAndComparableRecordsCorrectOrWrong() =
        runBlocking {
            val calls = mutableListOf<Pair<String, String>>()
            val model =
                ReviewViewModel(
                    scopeIdiomId = null,
                    loadQueue = {
                        // 第 1 题无首次作答，不可比对；后两题首次均为 B。
                        listOf(
                            question("m-1", null),
                            question("m-2", "B"),
                            question("m-3", "B"),
                        )
                    },
                    record = { id, result, _ -> calls += id to result },
                )
            model.pick(0) // 不可比对：不落库
            yield()
            assertEquals(0, calls.size)

            model.next()
            model.pick(2) // 首次 B、本次 C：不同＝对
            yield()
            assertEquals(listOf("m-2" to "对"), calls)

            model.next()
            model.pick(1) // 首次 B、本次 B：相同＝错
            yield()
            assertEquals(listOf("m-2" to "对", "m-3" to "错"), calls)
        }
}
