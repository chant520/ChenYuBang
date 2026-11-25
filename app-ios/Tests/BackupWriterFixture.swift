import Foundation
import GRDB

extension BackupWriterFixture {
    static func seedUserRows(_ db: Database, local: Bool) throws {
        let prefix = local ? "本机" : "备份"
        let image = local ? "images/local.jpg" : "images/imported.jpg"
        let rows = [
            (sharedExample, sharedMistake, "\(prefix)共享"),
            (local ? localExtraExample : extraExample, local ? localExtraMistake : extraMistake,
             local ? "本机独有" : "备份新增")
        ]
        for (exampleID, mistakeID, label) in rows {
            try db.execute(sql: """
                INSERT INTO user_example(id, imagePath, ocrText, stem, optionA, optionB, optionC, optionD,
                                         myAnswer, isMistake, examType, year, region, createdAt)
                VALUES (?, ?, 'OCR', ?, '甲', '乙', '丙', '丁', 'B', 1, '国考', 2026, '中央',
                        '2026-11-20T00:00:00Z')
                """, arguments: [exampleID, image, "\(label)录入题"])
            try db.execute(sql: """
                INSERT INTO mistake(id, userExampleId, imagePath, ocrText, stem, optionA, optionB, optionC,
                                    optionD, myAnswer, reason, tagNames, examType, year, region, state,
                                    createdAt, lastReviewedAt, reviewCount)
                VALUES (?, ?, ?, 'OCR', ?, '甲', '乙', '丙', '丁', 'B', '望文生义', '["望文生义"]',
                        '国考', 2026, '中央', '待复习', '2026-11-20T00:00:00Z',
                        '2026-11-21T00:00:00Z', 1)
                """, arguments: [mistakeID, exampleID, image, "\(label)错题"])
            let idiomID = exampleID == sharedExample ? (local ? idiomA : idiomB) : idiomA
            try db.execute(sql: "INSERT INTO user_example_idiom(userExampleId, idiomId) VALUES (?, ?)",
                           arguments: [exampleID, idiomID])
            try db.execute(sql: "INSERT INTO mistake_idiom(mistakeId, idiomId) VALUES (?, ?)",
                           arguments: [mistakeID, idiomID])
            if exampleID == extraExample && !local {
                try db.execute(sql: """
                    INSERT INTO user_example_idiom(userExampleId, idiomId) VALUES (?, ?)
                    """, arguments: [exampleID, idiomB])
            }
        }
        let extraReviewID = local ? localExtraReview : extraReview
        let extraMistakeID = local ? localExtraMistake : extraMistake
        for (reviewID, mistakeID) in [(sharedReview, sharedMistake), (extraReviewID, extraMistakeID)] {
            try db.execute(sql: "INSERT INTO review_log(id, mistakeId, result, at) VALUES (?, ?, ?, ?)",
                           arguments: [reviewID, mistakeID, local ? "错" : "对", "2026-11-21T00:00:00Z"])
        }
        try db.execute(sql: "INSERT INTO idiom_state(idiomId, status, favorite) VALUES (?, ?, ?)",
                       arguments: [idiomA, local ? "学过" : "已掌握", local ? 0 : 1])
        try db.execute(sql: "INSERT INTO idiom_state(idiomId, status, favorite) VALUES (?, '学过', 1)",
                       arguments: [idiomB])
        try db.execute(sql: """
            INSERT INTO exam_scope(id, type, province, enabled) VALUES (?, '国考', NULL, ?)
            """, arguments: [local ? "scope-national" : "scope-source-national", local ? 1 : 0])
        try db.execute(sql: """
            INSERT INTO exam_scope(id, type, province, enabled) VALUES (?, '省考', ?, 1)
            """, arguments: [local ? "scope-local-province" : "scope-source-province",
                            local ? "湖北" : "广东"])
    }
}
