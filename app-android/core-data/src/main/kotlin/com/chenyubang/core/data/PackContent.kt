package com.chenyubang.core.data

import androidx.sqlite.db.SupportSQLiteDatabase

internal object PackContent {
    fun clear(database: SupportSQLiteDatabase) {
        database.execSQL("DELETE FROM idiom_scope")
        database.execSQL("DELETE FROM example_idiom")
        database.execSQL("DELETE FROM example")
        // Keep user-referenced orphan rows, but never their former scope links.
        database.execSQL(
            "DELETE FROM idiom WHERE id NOT IN " +
                "(SELECT idiomId FROM mistake_idiom UNION SELECT idiomId FROM idiom_state)",
        )
    }

    fun insertExamples(
        database: SupportSQLiteDatabase,
        examples: List<PackExample>,
    ) {
        examples.forEach { row ->
            val entity = row.entity
            database.execSQL(
                "INSERT INTO example " +
                    "(id, stem, optionA, optionB, optionC, optionD, answer, explain, examType, year, " +
                    "region, questionType, imagePath) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf(
                    entity.id, entity.stem, entity.optionA, entity.optionB, entity.optionC, entity.optionD,
                    entity.answer, entity.explain, entity.examType, entity.year, entity.region, entity.questionType,
                    entity.imagePath,
                ),
            )
            row.idiomIds.forEach { idiomId ->
                database.execSQL(
                    "INSERT INTO example_idiom (exampleId, idiomId) VALUES (?, ?)",
                    arrayOf(entity.id, idiomId),
                )
            }
        }
    }

    fun refreshScopes(
        database: SupportSQLiteDatabase,
        idioms: List<PackIdiom>,
        examples: List<PackExample>,
    ) {
        val provinces = provincialEvidence(examples)
        database.execSQL("DELETE FROM idiom_scope")
        database.query("SELECT id, type, province FROM exam_scope WHERE enabled = ?", arrayOf(1)).use { scopes ->
            while (scopes.moveToNext()) {
                val id = scopes.getString(0)
                val type = scopes.getString(1)
                val province = if (scopes.isNull(2)) null else scopes.getString(2)
                idioms.filter { eligible(it, type, province, provinces) }.forEach { row ->
                    database.execSQL(
                        "INSERT INTO idiom_scope (idiomId, scopeId) VALUES (?, ?)",
                        arrayOf(row.entity.id, id),
                    )
                }
            }
        }
    }

    fun restrictSearch(
        database: SupportSQLiteDatabase,
        idioms: List<PackIdiom>,
    ) {
        val active = idioms.map { it.entity.id }.toSet()
        val retained =
            database.query("SELECT idiomid FROM idiom_fts").use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        val id = cursor.getString(0)
                        if (id !in active) add(id)
                    }
                }
            }
        retained.forEach { id ->
            database.execSQL("DELETE FROM idiom_fts WHERE idiomid = ?", arrayOf(id))
        }
    }

    private fun provincialEvidence(examples: List<PackExample>): Map<String, Set<String>> =
        examples.filter { it.entity.examType == "省考" && it.entity.region.isNotBlank() }
            .groupBy { it.entity.region }
            .mapValues { (_, rows) -> rows.flatMap { it.idiomIds }.toSet() }

    private fun eligible(
        idiom: PackIdiom,
        type: String,
        province: String?,
        evidence: Map<String, Set<String>>,
    ): Boolean =
        type in idiom.scopes &&
            (type != "省考" || (!province.isNullOrBlank() && idiom.entity.id in evidence[province].orEmpty()))
}
