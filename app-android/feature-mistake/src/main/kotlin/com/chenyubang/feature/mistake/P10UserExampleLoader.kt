package com.chenyubang.feature.mistake

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.chenyubang.core.data.AppDatabase
import com.chenyubang.core.data.MistakeReviewState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * P10 读库口子：只用 user_example + 最新 mistake + user_example_idiom ⋈ idiom。
 * 不取系统 example 的 answer / explain——录入题与系统真题两套来源（信息架构 v1.4 P10 行）。
 */
internal suspend fun loadP10Detail(
    database: AppDatabase,
    id: String,
): P10UserExampleDetail? {
    val dao = database.userExampleDao()
    val example = dao.byId(id) ?: return null
    val mark = pickLatestMark(dao.mistakesOf(id))
    val idioms = dao.linkedIdioms(id).map { P10IdiomRef(it.id, it.word) }
    return P10UserExampleDetail(
        id = example.id,
        stem = example.stem.trim(),
        options = listOf(example.optionA, example.optionB, example.optionC, example.optionD),
        myAnswer = example.myAnswer,
        isMistake = mark != null,
        examType = example.examType,
        year = example.year,
        reason = mark?.reason,
        tagNames = mark?.tagNames.orEmpty(),
        idioms = idioms,
        imagePath = example.imagePath,
        mistakeId = mark?.id,
        reviewState = MistakeReviewState.entries.firstOrNull { it.storedValue == mark?.state },
    )
}

/** imagePath 是相对 filesDir 的路径（2.5 保存口径）；读盘放 IO 线程，不在组合阶段解码。 */
internal suspend fun isP10ImageReadable(
    filesDir: File,
    path: String,
): Boolean =
    withContext(Dispatchers.IO) {
        runCatching { File(filesDir, path).let { it.exists() && it.length() > 0 } }.getOrDefault(false)
    }

internal suspend fun decodeP10Image(
    filesDir: File,
    path: String,
): ImageBitmap? =
    withContext(Dispatchers.IO) {
        runCatching { BitmapFactory.decodeFile(File(filesDir, path).absolutePath)?.asImageBitmap() }.getOrNull()
    }
