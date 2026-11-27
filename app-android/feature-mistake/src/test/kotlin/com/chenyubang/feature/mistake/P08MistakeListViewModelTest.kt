package com.chenyubang.feature.mistake

import com.chenyubang.core.data.IdiomEntity
import com.chenyubang.core.data.MistakeEntity
import com.chenyubang.core.data.MistakeIdiomGroup
import com.chenyubang.core.data.MistakeOverview
import com.chenyubang.core.data.MistakeWithIdioms
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** P08 buildState 纯逻辑：分组数、错因聚合单行、标签合并计数与错题行映射。 */
class P08MistakeListViewModelTest {
    private fun viewModel() =
        P08MistakeListViewModel(
            loadOverview = { flowOf(MistakeOverview(0, 0)) },
            loadGroups = { flowOf(emptyList()) },
            loadMistakes = { flowOf(emptyList()) },
        )

    private fun idiom(id: String) =
        IdiomEntity(
            id = id,
            word = "词-$id",
            pinyin = "pin",
            initials = "px",
            sense = "释义",
            color = "中性",
            frequency = 5,
        )

    private fun mistake(
        id: String,
        tags: List<String>,
        idiomIds: List<String>,
        examType: String? = null,
        userExampleId: String? = null,
    ) = MistakeWithIdioms(
        mistake =
            MistakeEntity(
                id = id,
                userExampleId = userExampleId,
                stem = "  题干 $id  ",
                optionA = "甲",
                optionB = "乙",
                optionC = "丙",
                optionD = "丁",
                tagNames = tags,
                examType = examType,
                state = MISTAKE_STATE_PENDING,
                createdAt = "2026-11-14T09:20:00Z",
            ),
        idioms = idiomIds.map(::idiom),
    )

    @Test
    fun groupsUsePrebuiltTagMapWithoutNestedScan() {
        val groups =
            listOf(
                MistakeIdiomGroup("idiom-a", "按部就班", 2),
                MistakeIdiomGroup("idiom-b", "别出心裁", 1),
                MistakeIdiomGroup("idiom-c", "墨守成规", 0),
            )
        val mistakes =
            listOf(
                mistake("m-1", listOf("望文生义", "对象误用"), listOf("idiom-a", "idiom-b")),
                mistake("m-2", listOf("望文生义"), listOf("idiom-a")),
            )
        val state = viewModel().buildState(MistakeOverview(12, 7), groups, mistakes)

        assertEquals(3, state.groups.size)
        assertEquals("望文生义 2 · 对象误用 1", state.groups[0].reasonLine)
        assertEquals("对象误用 1 · 望文生义 1", state.groups[1].reasonLine)
        assertNull(state.groups[2].reasonLine)
        assertEquals(12, state.total)
        assertEquals(7, state.pending)
    }

    @Test
    fun duplicateTagNamesAreCountedOncePerOccurrence() {
        val groups = listOf(MistakeIdiomGroup("idiom-a", "按部就班", 1))
        val mistakes =
            listOf(
                mistake("m-1", listOf("望文生义", "望文生义", "对象误用"), listOf("idiom-a")),
            )
        val state = viewModel().buildState(MistakeOverview(1, 1), groups, mistakes)

        assertEquals("望文生义 2 · 对象误用 1", state.groups.single().reasonLine)
    }

    @Test
    fun mistakeRowsAreMappedFromDaoRows() {
        val mistakes =
            listOf(
                mistake(
                    "m-1",
                    listOf("望文生义"),
                    listOf("idiom-a"),
                    examType = "国考",
                    userExampleId = "ue-1",
                ),
            )
        val state = viewModel().buildState(MistakeOverview(1, 1), emptyList(), mistakes)
        val row = state.mistakes.single()

        assertEquals("m-1", row.id)
        assertEquals("题干 m-1", row.stem)
        assertEquals(listOf("词-idiom-a"), row.idiomWords)
        assertEquals("国考", row.examType)
        assertEquals("ue-1", row.userExampleId)
    }
}
