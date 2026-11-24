import Foundation
import GRDB

struct ResolvedBackup {
    let payload: BackupDataPayload
    let report: BackupOrphanReport
}

enum BackupOrphans {
    static func resolve(_ payload: BackupDataPayload, db: Database,
                        includeLocalUserReferences: Bool) throws -> ResolvedBackup {
        let idiomIDs = try existingIdiomIDs(db)
        let backupExampleIDs = Set(payload.userExamples?.map(\.id) ?? [])
        let backupMistakeIDs = Set(payload.mistakes.map(\.id))
        let exampleIDs = includeLocalUserReferences
            ? backupExampleIDs.union(try BackupImportWriter.existingIDs(db, "user_example"))
            : backupExampleIDs
        let mistakeIDs = includeLocalUserReferences
            ? backupMistakeIDs.union(try BackupImportWriter.existingIDs(db, "mistake"))
            : backupMistakeIDs

        var skippedMistakes = 0
        var missingMistakeIdioms = 0
        var missingExampleIdioms = 0
        let mistakes = resolveMistakes(
            payload.mistakes, idiomIDs: idiomIDs, exampleIDs: exampleIDs,
            skipped: &skippedMistakes, missingIdioms: &missingMistakeIdioms
        )
        let examples = resolveExamples(
            payload.userExamples ?? [], idiomIDs: idiomIDs, missingIdioms: &missingExampleIdioms
        )
        let reviews = payload.reviews.filter { mistakeIDs.contains($0.mistakeId) }
        return ResolvedBackup(
            payload: payload.copy(
                mistakes: mistakes,
                userExamples: payload.userExamples == nil ? nil : examples,
                reviews: reviews
            ),
            report: BackupOrphanReport(
                skippedMistakes: skippedMistakes,
                missingMistakeIdioms: missingMistakeIdioms,
                missingExampleIdioms: missingExampleIdioms,
                skippedReviews: payload.reviews.count - reviews.count
            )
        )
    }

    private static func resolveMistakes(
        _ rows: [BackupMistakePayload], idiomIDs: Set<String>, exampleIDs: Set<String>,
        skipped: inout Int, missingIdioms: inout Int
    ) -> [BackupMistakePayload] {
        var kept: [BackupMistakePayload] = []
        for row in rows {
            if let exampleID = row.userExampleId, !exampleIDs.contains(exampleID) {
                skipped += 1
                continue
            }
            missingIdioms += row.idiomIds.filter { !idiomIDs.contains($0) }.count
            kept.append(row.copy(idiomIds: row.idiomIds.filter { idiomIDs.contains($0) }))
        }
        return kept
    }

    private static func resolveExamples(
        _ rows: [BackupUserExamplePayload], idiomIDs: Set<String>, missingIdioms: inout Int
    ) -> [BackupUserExamplePayload] {
        rows.map { row in
            missingIdioms += row.idiomIds.filter { !idiomIDs.contains($0) }.count
            return row.copy(idiomIds: row.idiomIds.filter { idiomIDs.contains($0) })
        }
    }

    private static func existingIdiomIDs(_ db: Database) throws -> Set<String> {
        let rows = try Row.fetchAll(db, sql: "SELECT id FROM idiom")
        return Set(rows.map { ($0["id"] as String).lowercased() })
    }
}

private extension BackupDataPayload {
    func copy(mistakes: [BackupMistakePayload], userExamples: [BackupUserExamplePayload]?,
              reviews: [BackupReviewPayload]) -> BackupDataPayload {
        BackupDataPayload(mistakes: mistakes, userExamples: userExamples, reviews: reviews,
                          idiomStates: idiomStates, scopes: scopes, tags: tags)
    }
}

private extension BackupMistakePayload {
    func copy(idiomIds: [String]) -> BackupMistakePayload {
        BackupMistakePayload(id: id, userExampleId: userExampleId, stem: stem, options: options,
                             imagePath: imagePath, ocrText: ocrText, myAnswer: myAnswer, reason: reason,
                             tagNames: tagNames, examType: examType, year: year, region: region,
                             state: state, createdAt: createdAt, lastReviewedAt: lastReviewedAt,
                             reviewCount: reviewCount, idiomIds: idiomIds)
    }
}

private extension BackupUserExamplePayload {
    func copy(idiomIds: [String]) -> BackupUserExamplePayload {
        BackupUserExamplePayload(id: id, imagePath: imagePath, ocrText: ocrText, stem: stem,
                                 optionA: optionA, optionB: optionB, optionC: optionC, optionD: optionD,
                                 myAnswer: myAnswer, isMistake: isMistake, examType: examType, year: year,
                                 region: region, createdAt: createdAt, idiomIds: idiomIds)
    }
}
