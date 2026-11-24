import Foundation
import GRDB

enum BackupImportWriter {
    private struct InsertedCounts {
        var mistakes = 0
        var userExamples = 0
        var reviews = 0
    }

    static func merge(_ payload: BackupDataPayload, db: Database) throws -> BackupImportSummary {
        var resolvedPayload = payload
        var report = BackupOrphanReport(skippedMistakes: 0, missingMistakeIdioms: 0,
                                        missingExampleIdioms: 0, skippedReviews: 0)
        var inserted = InsertedCounts()
        try db.inTransaction {
            let resolved = try BackupOrphans.resolve(payload, db: db, includeLocalUserReferences: true)
            resolvedPayload = resolved.payload
            report = resolved.report
            inserted = try insertAll(resolved.payload, db: db, keepLocalOnConflict: true)
            return .commit
        }
        return summary(.merge, resolvedPayload, inserted: inserted, orphans: report)
    }

    /// 覆盖按规范先写临时库，再在自动留底后以单个事务替换本机用户表。
    static func replace(_ payload: BackupDataPayload,
                        queue: DatabaseQueue) throws -> BackupImportSummary {
        let fileManager = FileManager.default
        let caches = try fileManager.url(for: .cachesDirectory, in: .userDomainMask,
                                         appropriateFor: nil, create: true)
        let stagingURL = caches.appendingPathComponent("backup-import-\(UUID().uuidString).sqlite")
        defer { try? fileManager.removeItem(at: stagingURL) }

        let resolved = try queue.read { db in
            try BackupOrphans.resolve(payload, db: db, includeLocalUserReferences: false)
        }
        let staging = try DatabaseQueue(path: stagingURL.path)
        try ChenYuBangDatabase.makeMigrator().migrate(staging)
        try staging.write { _ = try insertAll(resolved.payload, db: db, keepLocalOnConflict: false) }

        let automaticBackup = try queue.read { db in
            try Backup.export(db: db, to: caches, date: Date(), namePrefix: "backup-before-import-")
        }
        try queue.writeWithoutTransaction { db in
            try db.execute(sql: "ATTACH DATABASE ? AS backup_import",
                           arguments: StatementArguments([stagingURL.path]))
            do {
                try db.inTransaction {
                    try clearUserData(db)
                    try copyStagedData(db)
                    return .commit
                }
            } catch {
                try? db.execute(sql: "DETACH DATABASE backup_import")
                throw error
            }
            try? db.execute(sql: "DETACH DATABASE backup_import")
        }
        return summary(.replace, resolved.payload, automaticBackup: automaticBackup.url, orphans: resolved.report)
    }

    private static func summary(_ mode: BackupImportMode, _ payload: BackupDataPayload,
                                inserted: InsertedCounts? = nil, automaticBackup: URL? = nil,
                                orphans: BackupOrphanReport) -> BackupImportSummary {
        BackupImportSummary(mode: mode,
                            mistakes: inserted?.mistakes ?? payload.mistakes.count,
                            userExamples: inserted?.userExamples ?? payload.userExamples?.count ?? 0,
                            reviews: inserted?.reviews ?? payload.reviews.count,
                            idiomStates: payload.idiomStates.count,
                            scopes: payload.scopes.count,
                            automaticBackup: automaticBackup,
                            orphans: orphans)
    }

    private static func insertAll(_ payload: BackupDataPayload, db: Database,
                                  keepLocalOnConflict: Bool) throws -> InsertedCounts {
        var inserted = InsertedCounts()
        let conflict = keepLocalOnConflict ? " OR IGNORE" : ""
        let localExamples = keepLocalOnConflict ? try existingIDs(db, "user_example") : []
        let localMistakes = keepLocalOnConflict ? try existingIDs(db, "mistake") : []
        var localReviews = keepLocalOnConflict ? try existingIDs(db, "review_log") : []
        for example in payload.userExamples ?? [] {
            guard !localExamples.contains(example.id) else { continue }
            try execute(db, """
                INSERT\(conflict) INTO user_example(
                    id, imagePath, ocrText, stem, optionA, optionB, optionC, optionD, myAnswer,
                    isMistake, examType, year, region, createdAt
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, [
                    example.id, nil, example.ocrText, example.stem,
                    example.optionA, example.optionB, example.optionC, example.optionD,
                    example.myAnswer, example.isMistake, example.examType, example.year,
                    example.region, example.createdAt
                ])
            for idiomID in example.idiomIds {
                try execute(db, "INSERT\(conflict) INTO user_example_idiom(userExampleId, idiomId) VALUES (?, ?)",
                            [example.id, idiomID])
            }
            inserted.userExamples += 1
        }
        for mistake in payload.mistakes {
            guard !localMistakes.contains(mistake.id) else { continue }
            try execute(db, """
                INSERT\(conflict) INTO mistake(
                    id, userExampleId, imagePath, ocrText, stem, optionA, optionB, optionC, optionD,
                    myAnswer, reason, tagNames, examType, year, region, state, createdAt,
                    lastReviewedAt, reviewCount
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, [
                    mistake.id, mistake.userExampleId, nil, mistake.ocrText, mistake.stem,
                    mistake.options.optionA, mistake.options.optionB,
                    mistake.options.optionC, mistake.options.optionD,
                    mistake.myAnswer, mistake.reason, tagJSON(mistake.tagNames ?? []), mistake.examType,
                    mistake.year, mistake.region, mistake.state, mistake.createdAt,
                    mistake.lastReviewedAt, mistake.reviewCount
                ])
            for idiomID in mistake.idiomIds {
                try execute(db, "INSERT\(conflict) INTO mistake_idiom(mistakeId, idiomId) VALUES (?, ?)",
                            [mistake.id, idiomID])
            }
            inserted.mistakes += 1
        }
        var insertedReviews: [String: (count: Int, latest: String)] = [:]
        for review in payload.reviews {
            guard !localMistakes.contains(review.mistakeId), !localReviews.contains(review.id) else { continue }
            try execute(db, "INSERT\(conflict) INTO review_log(id, mistakeId, result, at) VALUES (?, ?, ?, ?)",
                        [review.id, review.mistakeId, review.result, review.reviewedAt])
            localReviews.insert(review.id)
            let previous = insertedReviews[review.mistakeId]
            insertedReviews[review.mistakeId] = (
                count: (previous?.count ?? 0) + 1,
                latest: previous.map { max($0.latest, review.reviewedAt) } ?? review.reviewedAt
            )
            inserted.reviews += 1
        }
        for mistake in payload.mistakes where !localMistakes.contains(mistake.id) {
            let reviews = insertedReviews[mistake.id]
            try execute(db, "UPDATE mistake SET reviewCount = ?, lastReviewedAt = ? WHERE id = ?",
                        [reviews?.count ?? 0, reviews?.latest, mistake.id])
        }
        for state in payload.idiomStates {
            try execute(db, "INSERT\(conflict) INTO idiom_state(idiomId, status, favorite) VALUES (?, ?, ?)",
                        [state.idiomId, state.status, state.favorite])
        }
        try insertScopes(payload.scopes, db: db, keepLocalOnConflict: keepLocalOnConflict)
        return inserted
    }

    private static func insertScopes(_ scopes: [BackupScopePayload], db: Database,
                                     keepLocalOnConflict: Bool) throws {
        var keys = Set<String>()
        if keepLocalOnConflict {
            let rows = try Row.fetchAll(db, sql: "SELECT type, province FROM exam_scope")
            keys = Set(rows.map { row in "\(row["type"] as String)\u{0}\(row["province"] as String? ?? "")" })
        }
        var insertedProvinces = Set<String>()
        for scope in scopes {
            let key = "\(scope.type)\u{0}\(scope.province ?? "")"
            guard !keys.contains(key) else { continue }
            try execute(db, "INSERT INTO exam_scope(id, type, province, enabled) VALUES (?, ?, ?, ?)",
                        [UUID().uuidString, scope.type, scope.province, scope.enabled])
            if scope.type == "省考", scope.enabled, let province = scope.province {
                insertedProvinces.insert(province)
            }
            keys.insert(key)
        }
        if keepLocalOnConflict { try mergeProvinceCache(insertedProvinces, db: db) }
    }

    private static func mergeProvinceCache(_ inserted: Set<String>, db: Database) throws {
        guard !inserted.isEmpty else { return }
        let saved = try String.fetchOne(
            db, sql: "SELECT value FROM settings WHERE key = ?", arguments: [ExamScopeStore.provincesKey]
        )
        let selected: Set<String>
        if let saved {
            selected = Set(try JSONDecoder().decode([String].self, from: Data(saved.utf8)))
        } else {
            let rows = try Row.fetchAll(db, sql: "SELECT province FROM exam_scope WHERE type = '省考' AND enabled = 1")
            selected = Set(rows.compactMap { $0["province"] as String? })
        }
        let data = try JSONEncoder().encode(selected.union(inserted).sorted())
        guard let json = String(data: data, encoding: .utf8) else {
            throw BackupError.format("考试范围省份设置无法编码。")
        }
        try db.execute(sql: "INSERT OR REPLACE INTO settings(key, value) VALUES (?, ?)",
                       arguments: [ExamScopeStore.provincesKey, json])
    }

    private static func clearUserData(_ db: Database) throws {
        try db.execute(sql: "DELETE FROM review_log")
        try db.execute(sql: "DELETE FROM user_example_idiom")
        try db.execute(sql: "DELETE FROM mistake_idiom")
        try db.execute(sql: "DELETE FROM user_example")
        try db.execute(sql: "DELETE FROM mistake")
        try db.execute(sql: "DELETE FROM idiom_state")
        try db.execute(sql: "DELETE FROM idiom_scope")
        try db.execute(sql: "DELETE FROM exam_scope")
        try db.execute(sql: "DELETE FROM settings WHERE key = ?", arguments: [ExamScopeStore.provincesKey])
    }

    private static func copyStagedData(_ db: Database) throws {
        try db.execute(sql: """
            INSERT INTO user_example(
                id, imagePath, ocrText, stem, optionA, optionB, optionC, optionD, myAnswer,
                isMistake, examType, year, region, createdAt
            ) SELECT id, imagePath, ocrText, stem, optionA, optionB, optionC, optionD, myAnswer,
                     isMistake, examType, year, region, createdAt FROM backup_import.user_example
            """)
        try db.execute(sql: """
            INSERT INTO mistake(
                id, userExampleId, imagePath, ocrText, stem, optionA, optionB, optionC, optionD,
                myAnswer, reason, tagNames, examType, year, region, state, createdAt,
                lastReviewedAt, reviewCount
            ) SELECT id, userExampleId, imagePath, ocrText, stem, optionA, optionB, optionC, optionD,
                     myAnswer, reason, tagNames, examType, year, region, state, createdAt,
                     lastReviewedAt, reviewCount FROM backup_import.mistake
            """)
        try db.execute(sql: """
            INSERT INTO review_log(id, mistakeId, result, at)
            SELECT id, mistakeId, result, at FROM backup_import.review_log
            """)
        try db.execute(sql: """
            INSERT INTO idiom_state(idiomId, status, favorite)
            SELECT idiomId, status, favorite FROM backup_import.idiom_state
            """)
        try db.execute(sql: """
            INSERT INTO exam_scope(id, type, province, enabled)
            SELECT id, type, province, enabled FROM backup_import.exam_scope
            """)
        try db.execute(sql: """
            INSERT INTO user_example_idiom(userExampleId, idiomId)
            SELECT userExampleId, idiomId FROM backup_import.user_example_idiom
            """)
        try db.execute(sql: """
            INSERT INTO mistake_idiom(mistakeId, idiomId)
            SELECT mistakeId, idiomId FROM backup_import.mistake_idiom
            """)
    }

    static func existingIDs(_ db: Database, _ table: String) throws -> Set<String> {
        let rows = try Row.fetchAll(db, sql: "SELECT id FROM \(table)")
        return Set(rows.map { row in (row["id"] as String).lowercased() })
    }

    private static func execute(_ db: Database, _ sql: String,
                                _ values: [DatabaseValueConvertible?]) throws {
        try db.execute(sql: sql, arguments: StatementArguments(values))
    }

    private static func tagJSON(_ names: [String]) -> String {
        guard let data = try? JSONSerialization.data(withJSONObject: names),
              let text = String(data: data, encoding: .utf8) else { return "[]" }
        return text
    }
}
