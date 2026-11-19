package com.chenyubang.feature.idiom

import com.chenyubang.core.data.IdiomEntity

/** P04 输入分类：与 core-data 的 FTS 路由（汉字/单字 vs 纯 ASCII 拼音）一一对应。 */
enum class SearchInputKind {
    Empty,
    HanSingle,
    HanPhrase,
    Roman,
}

/** P04 结果行：成语名 / 拼音 / 释义首句（信息架构 v1.3 P04 ③）。 */
data class SearchIdiom(
    val id: String,
    val word: String,
    val pinyin: String,
    val senseFirst: String,
)

/**
 * P04 搜索的输入分类、结果映射与「查无结果」判定（纯函数，便于单测）。
 *
 * 查询本身复用 `IdiomDao.search(q)` 的四输入 FTS 能力；这里只做上层可测的判定，
 * 不重复实现 FTS，也不在查无结果时给空白页。
 */
object IdiomSearchUi {
    const val INPUT_HINT = "完整成语 / 单字 / 全拼 / 拼音首字母都行"
    const val NO_RESULT_TITLE = "没有找到这个成语，换个字或拼音试试"
    const val NO_RESULT_HINT = "支持全拼与首字母，比如「bubujiu」或「bbjb」；也可以只输一个字。"

    fun classify(query: String): SearchInputKind {
        val text = query.trim()
        return when {
            text.isEmpty() -> SearchInputKind.Empty
            !containsHan(text) -> SearchInputKind.Roman
            text.codePointCount(0, text.length) == 1 -> SearchInputKind.HanSingle
            else -> SearchInputKind.HanPhrase
        }
    }

    fun from(entities: List<IdiomEntity>): List<SearchIdiom> =
        entities.map { entity ->
            SearchIdiom(
                id = entity.id,
                word = entity.word,
                pinyin = entity.pinyin,
                senseFirst = IdiomDetail.firstSentence(entity.sense),
            )
        }

    /** 有输入且零结果才给空态；空输入不算空态（此时只显示输入方式提示）。 */
    fun showNoResult(
        query: String,
        results: List<SearchIdiom>,
    ): Boolean = query.trim().isNotEmpty() && results.isEmpty()

    private fun containsHan(text: String): Boolean =
        text.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN }
}
