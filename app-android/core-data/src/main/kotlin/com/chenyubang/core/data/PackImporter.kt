package com.chenyubang.core.data

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.InputStream

class PackImporter(private val database: AppDatabase, context: Context) {
    private val context = context.applicationContext
    private val operations = Mutex()
    private var installedSHA256: String? = null
    private var installedPack: ValidatedPack? = null

    fun validate(input: InputStream): ValidatedPack = ValidatedPack.readValidated(input)

    suspend fun import(pack: ValidatedPack): PackImportResult =
        withContext(Dispatchers.IO) {
            operations.withLock {
                val idioms = PackRows.idioms(pack)
                val examples = PackRows.examples(pack)
                PackVerification.stage(context, pack, idioms, examples)
                val result =
                    database.withTransaction {
                        val sqlite = database.openHelper.writableDatabase
                        PackContent.clear(sqlite)
                        database.idiomDao().upsertAll(idioms.map { it.entity })
                        PackContent.insertExamples(sqlite, examples)
                        database.idiomDao().rebuildIdiomFts()
                        PackContent.restrictSearch(sqlite, idioms)
                        PackContent.refreshScopes(sqlite, idioms, examples)
                        PackVerification.verify(database, pack, idioms, examples)
                        val count = PackVerification.count(sqlite, "SELECT COUNT(*) FROM idiom")
                        require(
                            PackVerification.count(sqlite, "SELECT COUNT(*) FROM idiom_fts") == pack.counts.idioms,
                        ) {
                            "Formal pack FTS row count"
                        }
                        PackImportResult(
                            packVersion = pack.packVersion,
                            schemaVersion = pack.schemaVersion,
                            builtAt = pack.builtAt,
                            counts = pack.counts,
                            sha256 = pack.sha256,
                            retainedIdioms = count - pack.counts.idioms,
                        )
                    }
                installedSHA256 = pack.sha256
                installedPack = pack
                result
            }
        }

    suspend fun refreshScopeLinks(pack: ValidatedPack? = null): Unit =
        withContext(Dispatchers.IO) {
            operations.withLock {
                val current = pack ?: requireNotNull(installedPack) { "No committed pack" }
                require(installedSHA256 == current.sha256) { "Refresh requires this importer's committed pack" }
                val idioms = PackRows.idioms(current)
                val examples = PackRows.examples(current)
                database.withTransaction {
                    PackVerification.verify(database, current, idioms, examples)
                    PackContent.refreshScopes(database.openHelper.writableDatabase, idioms, examples)
                }
            }
        }
}
