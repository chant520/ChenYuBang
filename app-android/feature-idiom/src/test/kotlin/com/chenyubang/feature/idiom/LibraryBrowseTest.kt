package com.chenyubang.feature.idiom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P03 筛选 / 排序 / 字母索引分组（A1）。纯函数单测，不依赖 Robolectric 与数据库。
 */
class LibraryBrowseTest {
    private val idioms =
        listOf(
            idiom("1", "墨守成规", "mò shǒu chéng guī", "mscg", 2),
            idiom("2", "按部就班", "àn bù jiù bān", "abjb", 1),
            idiom("3", "别出心裁", "bié chū xīn cái", "bcxc", 1),
            idiom("4", "循序渐进", "xún xù jiàn jìn", "xxjj", 1),
            idiom("5", "望其项背", "wàng qí xiàng bèi", "wqxb", 1),
        )

    @Test
    fun lettersAreDistinctAndAlphabetical() {
        assertEquals(listOf("A", "B", "M", "W", "X"), LibraryBrowse.letters(idioms))
    }

    @Test
    fun lettersPutNonAlphabetLast() {
        val mixed = idioms + idiom("9", "123", "1 2 3", "", 1)

        assertEquals(listOf("A", "B", "M", "W", "X", LibraryBrowse.OTHER_LETTER), LibraryBrowse.letters(mixed))
    }

    @Test
    fun scopeDimensionKeepsAllItemsSortedByPinyin() {
        val page = LibraryBrowse.page(idioms, LibraryDimension.Scope, letter = null)

        assertEquals(5, page.count)
        assertEquals(listOf("按部就班", "别出心裁", "墨守成规", "望其项背", "循序渐进"), page.items.map { it.word })
        assertEquals(1, page.sections.size)
        assertEquals(null, page.sections.first().letter)
    }

    @Test
    fun initialDimensionGroupsByLetterAndFiltersSelection() {
        val grouped = LibraryBrowse.page(idioms, LibraryDimension.Initial, letter = null)
        assertEquals(listOf("A", "B", "M", "W", "X"), grouped.sections.map { it.letter })

        val onlyB = LibraryBrowse.page(idioms, LibraryDimension.Initial, letter = "B")
        assertEquals(1, onlyB.sections.size)
        assertEquals(listOf("别出心裁"), onlyB.items.map { it.word })

        val missing = LibraryBrowse.page(idioms, LibraryDimension.Initial, letter = "Z")
        assertTrue(missing.isEmpty)
    }

    @Test
    fun frequencyDimensionSortsDescendingThenByPinyin() {
        val page = LibraryBrowse.page(idioms, LibraryDimension.Frequency, letter = null)

        assertEquals("墨守成规", page.items.first().word)
        assertEquals(listOf("按部就班", "别出心裁", "望其项背", "循序渐进"), page.items.drop(1).map { it.word })
    }

    @Test
    fun browsingEmptyLibraryIsEmptyNotError() {
        val page = LibraryBrowse.page(emptyList(), LibraryDimension.Scope, letter = null)

        assertTrue(page.isEmpty)
        assertEquals(0, page.count)
        assertTrue(LibraryBrowse.letters(emptyList()).isEmpty())
    }

    @Test
    fun pageFlattensSectionsInOrder() {
        val page = LibraryBrowse.page(idioms, LibraryDimension.Initial, letter = null)

        assertEquals(page.sections.flatMap { section -> section.items }, page.items)
        assertFalse(page.isEmpty)
    }

    private fun idiom(
        id: String,
        word: String,
        pinyin: String,
        initials: String,
        frequency: Int,
    ) = LibraryIdiom(id = id, word = word, pinyin = pinyin, initials = initials, frequency = frequency)
}
