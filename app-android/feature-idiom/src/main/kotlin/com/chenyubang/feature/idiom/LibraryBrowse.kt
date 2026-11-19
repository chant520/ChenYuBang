package com.chenyubang.feature.idiom

import java.util.Locale

/** P03 浏览维度（信息架构 v1.2 P03 ③：按考试范围 / 按拼音首字母 / 按考频高低）。 */
enum class LibraryDimension(
    val label: String,
) {
    Scope("按考试范围"),
    Initial("按首字母"),
    Frequency("按考频"),
}

/** 成语库列表行所需字段，避免页面层直接依赖 core-data 实体。 */
data class LibraryIdiom(
    val id: String,
    val word: String,
    val pinyin: String,
    val initials: String,
    val frequency: Int,
) {
    /** 字母索引归组键：拼音首字母大写；空值归到 `#`。 */
    val letter: String
        get() = initials.take(1).uppercase(Locale.ROOT).ifEmpty { LibraryBrowse.OTHER_LETTER }
}

/** 一个字母分组；[letter] 为 null 表示不分组的整段列表（按范围 / 按考频）。 */
data class LibrarySection(
    val letter: String?,
    val items: List<LibraryIdiom>,
)

data class LibraryPage(
    val sections: List<LibrarySection>,
) {
    val items: List<LibraryIdiom> = sections.flatMap { it.items }
    val count: Int get() = items.size
    val isEmpty: Boolean get() = items.isEmpty()
}

/**
 * P03 的筛选 / 排序 / 字母分组（纯函数，便于单测）。
 *
 * 排序口径：按拼音首字母串升序、同首字母按词面升序（设计稿字母索引条即按此定位）；
 * 按考频降序，同频仍按首字母与词面稳定排序。范围过滤已由 `idiom_scope` 在库层完成，
 * 这里只处理维度切换与字母条件。
 */
object LibraryBrowse {
    const val OTHER_LETTER = "#"

    /** 当前题库里实际出现过的首字母，A–Z 顺序，`#` 排最后。 */
    fun letters(items: List<LibraryIdiom>): List<String> {
        val present = items.map { it.letter }.distinct()
        val alphabet = present.filter { it != OTHER_LETTER }.sorted()
        return alphabet + present.filter { it == OTHER_LETTER }
    }

    fun page(
        items: List<LibraryIdiom>,
        dimension: LibraryDimension,
        letter: String?,
    ): LibraryPage =
        when (dimension) {
            LibraryDimension.Scope -> LibraryPage(listOf(LibrarySection(null, items.sortedWith(BY_PINYIN))))
            LibraryDimension.Initial -> {
                val filtered = if (letter == null) items else items.filter { it.letter == letter }
                val sections =
                    filtered.groupBy { it.letter }
                        .toSortedMap(compareBy { if (it == OTHER_LETTER) "\uFFFF" else it })
                        .map { (key, rows) -> LibrarySection(key, rows.sortedWith(BY_PINYIN)) }
                LibraryPage(sections)
            }
            LibraryDimension.Frequency ->
                LibraryPage(listOf(LibrarySection(null, items.sortedWith(BY_FREQUENCY))))
        }

    private val BY_PINYIN: Comparator<LibraryIdiom> =
        compareBy<LibraryIdiom> { it.initials }.thenBy { it.word }

    private val BY_FREQUENCY: Comparator<LibraryIdiom> =
        compareByDescending<LibraryIdiom> { it.frequency }.then(BY_PINYIN)
}
