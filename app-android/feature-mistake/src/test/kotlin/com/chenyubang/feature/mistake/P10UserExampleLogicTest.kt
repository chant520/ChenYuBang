package com.chenyubang.feature.mistake

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** P10 纯逻辑钉住：结果四文案逐字对齐 iOS、出处行、遮罩与重做状态机、多 mistake 取最新。 */
class P10UserExampleLogicTest {
    private fun detail(
        myAnswer: String? = "B",
        isMistake: Boolean = true,
    ) = P10UserExampleDetail(
        id = "ue-1",
        stem = "题干",
        options = listOf("甲", "乙", "丙", "丁"),
        myAnswer = myAnswer,
        isMistake = isMistake,
        examType = "国考",
        year = 2026,
        reason = "望文生义",
        tagNames = listOf("高频"),
        idioms = listOf(P10IdiomRef("id-anbu", "按部就班")),
        imagePath = null,
    )

    private fun state(
        myAnswer: String? = "B",
        isMistake: Boolean = true,
        rerolling: Boolean = false,
        picked: Int? = null,
        checked: Boolean = false,
        revealed: Boolean = false,
    ) = P10UserExampleUiState(
        detail = detail(myAnswer, isMistake),
        rerolling = rerolling,
        picked = picked,
        checked = checked,
        revealed = revealed,
        loading = false,
    )

    @Test
    fun rerollResultMatchesIosWordingInFourCases() {
        assertEquals("尚未选择", P10Logic.rerollResult(first = 1, picked = null))
        assertEquals(
            "与首次作答一致（B）",
            P10Logic.rerollResult(first = 1, picked = 1),
        )
        assertEquals(
            "与首次作答不同：首次 B，本次 C",
            P10Logic.rerollResult(first = 1, picked = 2),
        )
        assertEquals(
            "本题未记录你的首次作答，无法比对（本次选了 D）",
            P10Logic.rerollResult(first = null, picked = 3),
        )
    }

    @Test
    fun firstPickAcceptsOnlyLettersAD() {
        assertEquals(1, P10Logic.firstPick(" b "))
        assertNull(P10Logic.firstPick("E"))
        assertNull(P10Logic.firstPick(null))
        assertEquals(0, P10Logic.firstPick("a"))
    }

    @Test
    fun sourceLineUsesYearAndExamTypeOnly() {
        assertEquals("2026 · 国考", P10Logic.sourceLine("国考", 2026))
        assertEquals("国考", P10Logic.sourceLine("国考", null))
        assertEquals("未填", P10Logic.sourceLine(null, null))
    }

    @Test
    fun maskHiddenByDefaultAndLockedDuringReroll() {
        val fresh = state()
        assertTrue(fresh.answerHidden)
        assertTrue(fresh.canToggleMask)
        val revealed = P10Transitions.toggleAnswer(fresh)
        assertFalse(revealed.answerHidden)
        val reroll = P10Transitions.startReroll(revealed)
        assertTrue(reroll.answerHidden)
        assertFalse(reroll.canToggleMask)
        assertEquals(reroll, P10Transitions.toggleAnswer(reroll))
    }

    @Test
    fun rerollTransitionsKeepEverythingInPageAndExitRestores() {
        val started = P10Transitions.startReroll(state(revealed = true))
        val picked = P10Transitions.pick(started, 2)
        assertEquals(2, picked.picked)
        assertFalse(picked.checked)
        val checked = P10Transitions.check(picked)
        assertTrue(checked.checked)
        assertEquals("与首次作答不同：首次 B，本次 C", P10Logic.rerollResult(checked.firstPick, checked.picked))
        val exited = P10Transitions.exitReroll(checked)
        assertFalse(exited.rerolling)
        assertFalse(exited.checked)
        assertNull(exited.picked)
        assertTrue(exited.canToggleMask)
    }

    @Test
    fun unmarkedExampleHasNoMaskOrReroll() {
        val plain = state(isMistake = false)
        assertFalse(plain.answerHidden)
        assertFalse(plain.canToggleMask)
        assertEquals(plain, P10Transitions.startReroll(plain))
        assertEquals("B", P10Logic.answerText(plain.detail!!.myAnswer, plain.answerHidden))
        assertEquals("已遮住", P10Logic.answerText("B", hidden = true))
    }

    /** 一处红规则适用范围：底部红描边删除行仅标错渲染，未标错整行不出现（不是置灰）。 */
    @Test
    fun bottomDeleteRowOnlyForMarkedExample() {
        val marked = state(isMistake = true)
        val unmarked = state(isMistake = false)
        assertTrue(marked.showsBottomDeleteRow)
        assertFalse(unmarked.showsBottomDeleteRow)
    }

    /** 原图已不在副句双向钉住：仅标错态带「与重做」，未标错态不带。 */
    @Test
    fun imageMissingSubtitleBranchesOnMistake() {
        assertEquals(
            "文件可能被清理；题干与选项仍可查看与重做。",
            P10Logic.imageMissingSubtitle(isMistake = true),
        )
        assertEquals(
            "文件可能被清理；题干与选项仍可查看。",
            P10Logic.imageMissingSubtitle(isMistake = false),
        )
    }
}
