package com.chenyubang.core.data

/** 解析孤儿时可见的本机键：合并模式把本机 mistake id 也计入，避免误判备份 review 为孤儿。 */
internal data class BackupReferences(
    val idiomIds: Set<String>,
    val userExampleIds: Set<String>,
    val mistakeIds: Set<String>,
)

internal data class ResolvedBackup(
    val snapshot: BackupSnapshot,
    val report: OrphanReport,
)

/**
 * 4.2 孤儿判定（规范 §8.3）：
 * - mistake.userExampleId 指向不存在的录入题 → 跳过错题并上报；
 * - idiomIds 指向不存在的成语 → 只跳过该关联、保留整条记录并上报；
 * - review.mistakeId 无对应错题 → 跳过并上报。
 * 不猜测字段、不清理内容表。
 *
 * 备份导入态例外（已拍板，见 review-decisions 五节）：过滤掉失效 idiomIds 后 idiomIds 可为空数组，
 * 备份 schema 已放宽为 minItems=0。继续过滤失效 id、只跳过关联并保留整条错题/录入题本体，不猜 id、不造占位成语；
 * 四类孤儿计数口径不变。该例外仅属备份导入态，正常录入仍须至少一条归属。
 */
internal object BackupOrphans {
    fun resolve(
        backup: ValidatedBackup,
        references: BackupReferences,
    ): ResolvedBackup {
        val (mistakes, missingUser, missingMistakeIdioms) = resolveMistakes(backup.snapshot.mistakes, references)
        val (examples, missingExampleIdioms) = resolveExamples(backup.snapshot.userExamples, references)
        val mistakeIds = references.mistakeIds + mistakes.map { it.id }
        val (reviews, missingReviews) =
            resolveReviews(backup.snapshot.reviews, mistakeIds)
        return ResolvedBackup(
            snapshot =
                backup.snapshot.copy(
                    mistakes = mistakes,
                    userExamples = examples,
                    reviews = reviews,
                ),
            report =
                OrphanReport(missingUser, missingMistakeIdioms, missingExampleIdioms, missingReviews),
        )
    }

    private fun resolveMistakes(
        rows: List<BackupMistake>,
        references: BackupReferences,
    ): Triple<List<BackupMistake>, List<String>, Map<String, List<String>>> {
        val kept = mutableListOf<BackupMistake>()
        val withoutUser = mutableListOf<String>()
        val missing = LinkedHashMap<String, List<String>>()
        rows.forEach { row ->
            if (row.userExampleId != null && row.userExampleId !in references.userExampleIds) {
                withoutUser += row.id
                return@forEach
            }
            // 只跳过失效关联、保留错题本体；备份导入态允许 idiomIds 为空（见类注释）。
            val absent = row.idiomIds.filter { it !in references.idiomIds }
            if (absent.isNotEmpty()) missing[row.id] = absent
            kept += row.copy(idiomIds = row.idiomIds.filter { it in references.idiomIds })
        }
        return Triple(kept, withoutUser, missing)
    }

    private fun resolveExamples(
        rows: List<BackupUserExample>,
        references: BackupReferences,
    ): Pair<List<BackupUserExample>, Map<String, List<String>>> {
        val missing = LinkedHashMap<String, List<String>>()
        val kept =
            rows.map { row ->
                // 只跳过失效关联、不丢弃录入题本体；备份导入态允许 idiomIds 为空（见类注释）。
                val absent = row.idiomIds.filter { it !in references.idiomIds }
                if (absent.isNotEmpty()) missing[row.id] = absent
                row.copy(idiomIds = row.idiomIds.filter { it in references.idiomIds })
            }
        return kept to missing
    }

    private fun resolveReviews(
        rows: List<BackupReview>,
        knownMistakes: Set<String>,
    ): Pair<List<BackupReview>, List<String>> {
        val kept = rows.filter { it.mistakeId in knownMistakes }
        val missing = rows.filter { it.mistakeId !in knownMistakes }.map { it.id }
        return kept to missing
    }
}
