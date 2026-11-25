package com.chenyubang.feature.review

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SelfTestEngineTest {
    private fun candidate(
        number: Int,
        word: String = "成语$number",
        sense: String = "释义$number",
    ) = SelfTestCandidate("id-$number", word, sense)

    private val library = (1..12).map(::candidate)

    @Test
    fun tenQuestionsAlternateFromSenseToIdiomAndRemainInsideScope() {
        val questions = SelfTestEngine.createRound(library.take(5), library, Random(7))
        assertEquals(10, questions.size)
        assertEquals((1..10).toList(), questions.map { it.number })
        assertEquals(
            List(10) { if (it % 2 == 0) SelfTestQuestionType.SenseToIdiom else SelfTestQuestionType.IdiomToSense },
            questions.map { it.type },
        )
        questions.forEach { question ->
            assertTrue(question.targetId in library.take(5).map { it.id })
            assertEquals(
                if (question.type == SelfTestQuestionType.SenseToIdiom) {
                    library.first { it.id == question.targetId }.sense
                } else {
                    library.first { it.id == question.targetId }.word
                },
                question.prompt,
            )
        }
    }

    @Test
    fun targetSequenceCyclesWhenScopeHasFewerThanTenTargets() {
        val targets = SelfTestEngine.createRound(library.take(3), library, Random(11)).map { it.targetId }
        assertEquals(targets.take(3), targets.drop(3).take(3))
        assertEquals(targets.take(3), targets.drop(6).take(3))
        assertEquals(targets.take(1), targets.drop(9))
    }

    @Test
    fun optionsAreFourUniqueAndExcludeTargetFromDistractors() {
        val questions = SelfTestEngine.createRound(library.take(6), library, Random(19))
        questions.forEach { question ->
            assertEquals(4, question.options.size)
            assertEquals(4, question.options.map { it.idiomId }.toSet().size)
            assertEquals(1, question.options.count { it.idiomId == question.targetId })
            assertTrue(question.options.all { it.idiomId in library.map { idiom -> idiom.id } })
            assertEquals(4, question.options.map { it.text }.toSet().size)
        }
    }

    @Test
    fun trimsBlankAndGloballyDuplicatedWordsOrSenses() {
        val valid = candidate(1, " 按部就班 ", " 按一定次序办理事务 ")
        val duplicateWord = candidate(2, "按部就班", "另一个释义")
        val duplicateSense = candidate(3, "另一个成语", " 按一定次序办理事务 ")
        val blankWord = candidate(4, "   ", "有效释义")
        val blankSense = candidate(5, "有效成语", "\n")
        val blankId = SelfTestCandidate("  ", "空编号成语", "空编号释义")
        val duplicateIdA = SelfTestCandidate("duplicate", "编号成语甲", "编号释义甲")
        val duplicateIdB = SelfTestCandidate("duplicate", "编号成语乙", "编号释义乙")
        val unique = candidate(6, "独有成语", "独有释义")
        val trimmed = candidate(7, " 去空格成语 ", " 去空格释义 ")
        val eligible =
            SelfTestEngine.eligible(
                listOf(
                    valid,
                    duplicateWord,
                    duplicateSense,
                    blankWord,
                    blankSense,
                    blankId,
                    duplicateIdA,
                    duplicateIdB,
                    unique,
                    trimmed,
                ),
            )
        assertEquals(
            listOf(
                unique,
                trimmed.copy(word = "去空格成语", sense = "去空格释义"),
            ),
            eligible,
        )
    }

    @Test
    fun emptyScopeAndInsufficientFullPoolAreExplicitAndNeverFabricate() {
        assertThrows(
            IllegalArgumentException::class.java,
        ) { SelfTestEngine.createRound(emptyList(), library, Random(1)) }
        assertThrows(IllegalArgumentException::class.java) {
            SelfTestEngine.createRound(library.take(1), library.take(3), Random(1))
        }
        assertFalse(SelfTestEngine.createRound(library.take(1), library.take(4), Random(1)).isEmpty())
    }

    @Test
    fun emptyEligibleScopeWinsEvenWhenFullPoolHasEnoughCandidates() {
        val ambiguous = candidate(1, "同词", "释义甲")
        val duplicate = candidate(2, "同词", "释义乙")
        val preparation = SelfTestPreparation.from(listOf(ambiguous), listOf(ambiguous, duplicate) + library.drop(1))
        assertEquals(SelfTestPreparation.EmptyScope, preparation)
    }

    @Test
    fun deterministicRandomSourceProducesSameRound() {
        val first = SelfTestEngine.createRound(library.take(4), library, Random(42))
        val second = SelfTestEngine.createRound(library.take(4), library, Random(42))
        assertEquals(first, second)
    }

    @Test
    fun pickGradesImmediatelyByIdAndLocksDuplicateTaps() {
        val question = SelfTestEngine.createRound(library.take(1), library, Random(3)).first()
        val initial = SelfTestUiState.Question(question)
        val correct = SelfTestFlow.pick(initial, question.targetId)
        assertTrue(correct.answer!!.isCorrect)
        assertEquals(question.targetId, correct.answer!!.selectedIdiomId)
        assertEquals(
            correct,
            SelfTestFlow.pick(correct, question.options.first { it.idiomId != question.targetId }.idiomId),
        )
        val wrongId = question.options.first { it.idiomId != question.targetId }.idiomId
        val wrong = SelfTestFlow.pick(initial, wrongId)
        assertFalse(wrong.answer!!.isCorrect)
        assertEquals(wrong, SelfTestFlow.pick(wrong, question.targetId))
        assertEquals(initial, SelfTestFlow.pick(initial, "missing"))
    }
}
