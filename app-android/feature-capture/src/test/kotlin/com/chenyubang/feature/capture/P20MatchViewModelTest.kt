package com.chenyubang.feature.capture

import com.chenyubang.core.data.IdiomEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** P20 候选状态机单测：三档判定 / 唯一勾选 / 换加删 / 空态回填 / JSON 降级（工作包 2.4 验收样例 A/B/C）。 */
class P20MatchViewModelTest {
    private val anbu = idiom(ID_ANBU, "按部就班", 5, "[\"墨守成规\",\"循规蹈矩\"]")
    private val biechu = idiom(ID_BIECHU, "别出心裁", 4, "[\"独出心裁\",\"标新立异\"]")
    private val moshou = idiom(ID_MOSHOU, "墨守成规", 4, "[\"故步自封\",\"按部就班\"]")
    private val sampleIdioms = listOf(anbu, biechu, moshou)

    @Test
    fun sampleA_threeTiersWithUniqueBest() {
        val draft =
            OcrDraft(
                stem = "下列各句中，加点的成语使用不恰当的一项是……",
                options = listOf("新厂长按部就班，提出了一整套创新方案", "他做事标新立异，从不墨守成规", "", ""),
            )
        val text = P20Matcher.mergedText(draft)
        assertTrue(text.contains("A．新厂长按部就班"))

        val model = P20MatchViewModel().also { it.bootstrap(text, sampleIdioms) }
        val state = model.state.value
        assertEquals(3, state.poolSize)
        assertEquals(3, state.candidates.size)
        assertEquals(ID_ANBU, state.checkedId)
        assertEquals(MatchTier.Best, tierOf(state, ID_ANBU))
        assertEquals(MatchTier.SameQuestion, tierOf(state, ID_MOSHOU))
        assertEquals(MatchTier.Semantic, tierOf(state, ID_BIECHU))
        assertEquals(1, state.candidates.count { it.id == state.checkedId })
    }

    @Test
    fun bestTieBrokenByFrequencyThenWord() {
        // 两条 word 命中同频：按 word 升序取第一条为唯一最匹配。
        val x = idiom("id-y", "乙成语", 4)
        val y = idiom("id-j", "甲成语", 4)
        val model = P20MatchViewModel().also { it.bootstrap("乙成语 和 甲成语", listOf(x, y)) }
        assertEquals("id-y", model.state.value.checkedId) // 乙(U+4E59) < 甲(U+7532)
        assertEquals(MatchTier.Best, tierOf(model.state.value, "id-y"))
        assertEquals(MatchTier.SameQuestion, tierOf(model.state.value, "id-j"))
    }

    @Test
    fun sampleB_emptyThenManualBackfill() {
        val text = "今天天气不错，我们出去散步吧。"
        val model = P20MatchViewModel().also { it.bootstrap(text, sampleIdioms) }
        assertTrue(model.state.value.empty)
        assertNull(model.state.value.checkedId)

        // 不选直接关闭：候选状态不变。
        model.setManualPicker(true)
        assertTrue(model.state.value.showManualPicker)
        model.setManualPicker(false)
        assertFalse(model.state.value.showManualPicker)
        assertTrue(model.state.value.empty)

        // 选中回填：覆盖候选区、默认勾该条、标「手动选择」。
        model.setManualPicker(true)
        model.pickManual(ID_BIECHU)
        val state = model.state.value
        assertFalse(state.showManualPicker)
        assertEquals(1, state.candidates.size)
        assertEquals(ID_BIECHU, state.checkedId)
        assertEquals(MatchTier.Manual, state.candidates.single().tier)
        assertEquals("手动选择", state.candidates.single().tier.label)
    }

    @Test
    fun sampleC_typoDoesNotFuzzyMatch() {
        val model = P20MatchViewModel().also { it.bootstrap("新厂长按步就班，提出了一整套方案", sampleIdioms) }
        assertTrue(model.state.value.empty)
        assertTrue(model.state.value.candidates.isEmpty())
    }

    @Test
    fun synonymsJsonFailureDegradesToEmpty() {
        assertEquals(emptyList<String>(), P20Matcher.synonymsOf("{not json"))
        assertEquals(emptyList<String>(), P20Matcher.synonymsOf("[\"ok\", broken"))
        val broken = idiom("id-broken", "无关词", 3, "{\"标新立异\"}")
        val model = P20MatchViewModel().also { it.bootstrap("他做事标新立异", listOf(broken)) }
        assertTrue(model.state.value.empty)
    }

    @Test
    fun cycleAdvancesByWindowAndWraps() {
        val idioms =
            listOf("甲一", "乙二", "丙三", "丁四", "戊五").mapIndexed { index, word ->
                idiom("id-$index", word, 3)
            }
        val ordered = idioms.sortedBy { it.word }.map { it.word }
        val model = P20MatchViewModel().also { it.bootstrap("甲一 乙二 丙三 丁四 戊五", idioms) }
        assertEquals(ordered.take(3), model.state.value.candidates.map { it.word })
        assertTrue(model.state.value.canCycle)

        model.cycle()
        assertEquals(ordered.drop(3).take(3), model.state.value.candidates.map { it.word })
        model.cycle()
        assertEquals(ordered.drop(1).take(3), model.state.value.candidates.map { it.word })

        // 池 ≤ K 时禁用并在点击时给中性提示。
        val small = P20MatchViewModel().also { it.bootstrap("甲一 乙二 丙三", idioms.take(3)) }
        assertFalse(small.state.value.canCycle)
        small.cycle()
        assertEquals(P20Matcher.NO_MORE_CANDIDATES, small.state.value.info)
    }

    @Test
    fun addAppendsByFrequencyWithoutChangingCheck() {
        val extra = listOf(idiom("id-extra", "额外词", 9), idiom("id-low", "低频词", 1))
        val model = P20MatchViewModel().also { it.bootstrap("按部就班", sampleIdioms + extra) }
        val before = model.state.value.checkedId
        model.add()
        val state = model.state.value
        // 「按部就班」同时是墨守成规的 synonyms，故命中 2 条，加一个后为 3 条。
        assertEquals(3, state.candidates.size)
        assertEquals("额外词", state.candidates.last().word)
        assertEquals(MatchTier.Added, state.candidates.last().tier)
        assertEquals(before, state.checkedId)
    }

    @Test
    fun addAtFullWindowShowsLimit() {
        val many = (0 until 50).map { index -> idiom("id-$index", "词$index", 3) }
        val text = many.joinToString(" ") { it.word }
        val model = P20MatchViewModel().also { it.bootstrap(text, many) }
        repeat(50) { model.add() }
        assertEquals(50, model.state.value.candidates.size)
        assertFalse(model.state.value.canAdd)
        assertEquals(P20Matcher.CANDIDATE_LIMIT, model.state.value.info)
    }

    @Test
    fun deleteRemovesEntryAndReselectsThenEmpty() {
        // 「标新立异」只让别出心裁走 synonyms 档；删掉最匹配后剩下唯一的 word 命中词墨守成规。
        val model = P20MatchViewModel().also { it.bootstrap("按部就班 墨守成规 标新立异", sampleIdioms) }
        model.toggleDeleteMode()
        assertTrue(model.state.value.deleteMode)
        model.remove(ID_ANBU)
        assertFalse(model.state.value.candidates.any { it.id == ID_ANBU })
        assertEquals(ID_MOSHOU, model.state.value.checkedId)

        model.remove(ID_MOSHOU)
        model.remove(ID_BIECHU)
        val state = model.state.value
        assertTrue(state.empty)
        assertNull(state.checkedId)
        assertFalse(state.deleteMode)
    }

    @Test
    fun selectAlwaysKeepsExactlyOne() {
        val model = P20MatchViewModel().also { it.bootstrap("按部就班 墨守成规", listOf(anbu, moshou)) }
        model.select(ID_MOSHOU)
        assertEquals(ID_MOSHOU, model.state.value.checkedId)
        model.select(ID_MOSHOU)
        assertEquals(ID_MOSHOU, model.state.value.checkedId)
        assertEquals(1, model.state.value.candidates.count { it.id == model.state.value.checkedId })
    }

    private fun tierOf(
        state: P20MatchUiState,
        id: String,
    ): MatchTier = state.candidates.first { it.id == id }.tier

    private fun idiom(
        id: String,
        word: String,
        frequency: Int,
        synonyms: String = "[]",
    ) = IdiomEntity(
        id = id,
        word = word,
        pinyin = "pin yin",
        initials = "py",
        sense = "释义。",
        color = "中性",
        synonyms = synonyms,
        frequency = frequency,
    )

    private companion object {
        const val ID_ANBU = "11111111-1111-4111-8111-111111111111"
        const val ID_BIECHU = "22222222-2222-4222-8222-222222222222"
        const val ID_MOSHOU = "33333333-3333-4333-8333-333333333333"
    }
}
