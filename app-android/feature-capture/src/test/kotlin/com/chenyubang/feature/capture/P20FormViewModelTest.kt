package com.chenyubang.feature.capture

import com.chenyubang.core.data.IdiomEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** P20 多选 + 表单状态机单测：增删 / 至少一条 / 手选追加去重 / 错因标签条件化。 */
class P20FormViewModelTest {
    private val anbu = idiom("id-anbu", "按部就班", 5, "[\"墨守成规\"]")
    private val biechu = idiom("id-biechu", "别出心裁", 4, "[\"标新立异\"]")
    private val moshou = idiom("id-moshou", "墨守成规", 4)
    private val sample = listOf(anbu, biechu, moshou)

    @Test
    fun bootstrapChecksOnlyBestAndCanSave() {
        val model = newModel().also { it.candidate.bootstrap("按部就班 墨守成规 别出心裁", sample) }
        assertEquals(listOf("id-anbu"), model.state.value.checked.map { it.id })
        assertTrue(model.state.value.canSave)
    }

    @Test
    fun multiSelectToggleKeepsPoolOrderAndAtLeastOneGatesSave() {
        val model = newModel().also { it.candidate.bootstrap("按部就班 墨守成规 别出心裁", sample) }
        model.candidate.select("id-moshou")
        model.candidate.select("id-biechu")
        assertEquals(listOf("id-biechu", "id-moshou", "id-anbu"), model.state.value.checked.map { it.id })

        model.candidate.select("id-anbu")
        model.candidate.select("id-biechu")
        assertEquals(listOf("id-moshou"), model.state.value.checked.map { it.id })
        assertTrue(model.state.value.canSave)

        model.candidate.select("id-moshou")
        assertTrue(model.state.value.checked.isEmpty())
        assertFalse(model.state.value.canSave)

        // 取消最后一条后不自动补选；点回任一条恢复可保存。
        model.candidate.select("id-biechu")
        assertEquals(listOf("id-biechu"), model.state.value.checked.map { it.id })
        assertTrue(model.state.value.canSave)
    }

    @Test
    fun cycleOnlyMovesWindowWithoutTouchingChecked() {
        val five = (0 until 5).map { idiom("id-$it", "词$it", 3) }
        val model = newModel().also { it.candidate.bootstrap(five.joinToString(" ") { it.word }, five) }
        val before = model.state.value.checked
        model.candidate.cycle()
        assertEquals(before, model.state.value.checked)
    }

    @Test
    fun addAppendsByFrequencyWithoutChangingCheck() {
        val extra = idiom("id-extra", "额外词", 9)
        val low = idiom("id-low", "低频词", 1)
        val model = newModel().also { it.candidate.bootstrap("按部就班 墨守成规 别出心裁", sample + extra + low) }
        val before = model.state.value.checked

        // 三条 sample 全命中入池，未命中的高频与低频词各一条：按 frequency DESC 先取高、再取低，勾选集合不动。
        model.candidate.add()
        assertEquals("id-extra", model.state.value.candidates.last().id)
        assertEquals(MatchTier.Added, model.state.value.candidates.last().tier)
        assertEquals(4, model.state.value.poolSize)
        assertEquals(before, model.state.value.checked)

        model.candidate.add()
        assertEquals("id-low", model.state.value.candidates.last().id)
        assertEquals(before, model.state.value.checked)
    }

    @Test
    fun addAtFullPoolShowsLimit() {
        val many = (0 until 50).map { idiom("id-$it", "词$it", 3) }
        val model = newModel().also { it.candidate.bootstrap(many.joinToString(" ") { it.word }, many) }
        // 命中即占满 50 池：加一个只提示上限，不再追加，池与窗口都不变。
        model.candidate.add()
        assertEquals(50, model.state.value.poolSize)
        assertEquals(P20Matcher.WINDOW_SIZE, model.state.value.candidates.size)
        assertEquals(P20Matcher.CANDIDATE_LIMIT, model.state.value.info)
    }

    @Test
    fun addSkipsPoolMembersAndSurvivesCycle() {
        // 四条命中（窗口 K=3，第四条在窗口外）+ 一条未命中的高频词：
        // 加一个必须跳过池内已有词（含窗口外），且加入的词进池，换一组后不丢。
        val biao = idiom("id-biao", "标新立异", 3)
        val extra = idiom("id-extra", "额外词", 10)
        val model =
            newModel().also {
                it.candidate.bootstrap("按部就班 墨守成规 别出心裁 标新立异", sample + biao + extra)
            }
        assertEquals(4, model.state.value.poolSize)
        assertEquals(P20Matcher.WINDOW_SIZE, model.state.value.candidates.size)

        model.candidate.add()
        assertEquals(5, model.state.value.poolSize)
        assertEquals("id-extra", model.state.value.candidates.last().id)
        assertEquals(
            model.state.value.candidates.size,
            model.state.value.candidates.map { it.id }.toSet().size,
        )

        // 池内（含窗口外）已无空位可加：不得重复挑到窗口外的池内词。
        model.candidate.add()
        assertEquals(5, model.state.value.poolSize)
        assertEquals(P20Matcher.NO_MORE_CANDIDATES, model.state.value.info)

        // 换一组用池重建窗口：手动加的词仍在，不会被冲掉。
        model.candidate.cycle()
        assertTrue(model.state.value.candidates.any { it.id == "id-extra" })
    }

    @Test
    fun manualPickAppendsToCheckedAndDedupesExisting() {
        val model = newModel().also { it.candidate.bootstrap("按部就班 墨守成规", sample) }
        model.candidate.select("id-moshou")
        val before = model.state.value.checked.map { it.id }

        // 手选已在已选集合的词：不重复、不移动顺序。
        model.candidate.setManualPicker(true)
        model.candidate.pickManual("id-moshou")
        assertFalse(model.state.value.showManualPicker)
        assertEquals(before, model.state.value.checked.map { it.id })

        // 手选新词：追加到末尾。
        model.candidate.pickManual("id-biechu")
        assertEquals(listOf("id-moshou", "id-anbu", "id-biechu"), model.state.value.checked.map { it.id })
        assertEquals("id-biechu", model.state.value.checked.last().id)
    }

    @Test
    fun reasonAndTagsOnlyEnabledWhenMistakeAndClearedOnUncheck() {
        val model = newModel()
        assertFalse(model.state.value.form.isMistake)
        model.form.setReason("不该写")
        assertTrue(model.form.addTag().not())
        assertTrue(model.state.value.form.reason.isEmpty())
        assertTrue(model.state.value.form.tags.isEmpty())

        model.form.toggleMistake()
        model.form.setReason("粗心")
        model.form.setTagDraft("审题")
        assertTrue(model.form.addTag())
        assertEquals("粗心", model.state.value.form.reason)
        assertEquals(listOf("审题"), model.state.value.form.tags)

        // 标签规则 1–8 字、去重。
        model.form.setTagDraft("超过八个字的标签名称")
        assertFalse(model.form.addTag())
        model.form.setTagDraft("审题")
        assertFalse(model.form.addTag())
        assertEquals(listOf("审题"), model.state.value.form.tags)

        model.form.toggleMistake()
        assertTrue(model.state.value.form.reason.isEmpty())
        assertTrue(model.state.value.form.tags.isEmpty())
    }

    @Test
    fun answerTogglesOffAndSourceFieldsAreOptional() {
        val model = newModel()
        model.form.setAnswer("b")
        assertEquals("B", model.state.value.form.answer)
        model.form.setAnswer("B")
        assertNull(model.state.value.form.answer)

        model.form.setExamType("省考")
        assertEquals("省考", model.state.value.form.examType)
        model.form.setExamType("教师编")
        assertEquals("教师编", model.state.value.form.examType) // 置灰由 UI 拦截，状态机不拒绝
        model.form.setExamType(null)
        assertNull(model.state.value.form.examType)

        model.form.setYear("2025")
        assertEquals(2025, model.state.value.form.year)
        assertFalse(model.state.value.form.yearInvalid)
        model.form.setYear("19")
        assertEquals("19", model.state.value.form.yearText)
        assertTrue(model.state.value.form.yearInvalid)
    }

    @Test
    fun savingStateGatesSaveAndFailureStaysOnPage() {
        val model = newModel().also { it.candidate.bootstrap("按部就班", sample) }
        model.form.setSaveState(saving = true)
        assertFalse(model.state.value.canSave)
        model.form.setSaveState(saving = false, error = P20Matcher.SAVE_FAILED)
        assertFalse(model.state.value.form.saving)
        assertEquals(P20Matcher.SAVE_FAILED, model.state.value.form.saveError)
        model.form.setSaveState(saving = false)
        assertNull(model.state.value.form.saveError)
        assertTrue(model.state.value.canSave)
    }

    private fun newModel() = P20FormViewModel(CoroutineScope(Dispatchers.Unconfined))

    private fun idiom(
        id: String,
        word: String,
        frequency: Int,
        synonyms: String = "[]",
    ) = IdiomEntity(
        id = id,
        word = word,
        pinyin = "",
        initials = "",
        sense = "",
        color = "",
        synonyms = synonyms,
        frequency = frequency,
    )
}
