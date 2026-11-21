import CangyuData
import Foundation
import GRDB
import XCTest

/// P08 / P09 读库口径（规范 §4.2 / 信息架构 v1.4 P08 ⑤、P09 ③）。
///
/// 钉住四件事：总览只数 `state = 待复习`；分组按错题数降序、同数按拼音首字母；
/// 平铺列表的两个排序分支（考试类型未填排最后）；P09 一行一题且取最新错题记录。
final class MistakeLibraryTests: XCTestCase {
    func testOverviewCountsPendingStatesOnly() throws {
        try seededQueue().read { db in
            XCTAssertEqual(try MistakeLibrary.overview(db), MistakeOverview(total: 5, pending: 4))
        }
    }

    func testIdiomGroupsOrderByCountThenInitials() throws {
        // i2 三题、i1 与 i3 各两题并列（abjb 在 bcxc 之前）；m4 / m5 只挂成语不挂录入题，仍计数。
        try seededQueue().read { db in
            let groups = try MistakeLibrary.idiomGroups(db)
            XCTAssertEqual(groups.map(\.idiomID), ["i2", "i1", "i3"])
            XCTAssertEqual(groups.map(\.count), [3, 2, 2])
            XCTAssertEqual(groups.map(\.word), ["墨守成规", "按部就班", "别出心裁"])
        }
    }

    func testRowsByCreatedAtDescCarryIdiomWords() throws {
        try seededQueue().read { db in
            let rows = try MistakeLibrary.rows(db, order: .byCreatedAt)
            XCTAssertEqual(rows.map(\.id), ["m1", "m3", "m2", "m4", "m5"])
            // 关联成语按词面字典序（SQLite BINARY）组装，一题多成语全部带出。
            XCTAssertEqual(rows[0].idiomWords, ["墨守成规", "按部就班"])
            XCTAssertEqual(rows[2].idiomWords, ["别出心裁", "墨守成规"])
            XCTAssertEqual(rows[4].idiomWords, ["别出心裁"])
            XCTAssertNil(rows[3].userExampleID)
            XCTAssertNil(rows[3].examType)
        }
    }

    func testRowsByExamTypePutsMissingTypeLast() throws {
        try seededQueue().read { db in
            let rows = try MistakeLibrary.rows(db, order: .byExamType)
            // 未填考试类型（NULL）排最后，其余按类型文本聚拢、类型内仍按录入时间倒序。
            XCTAssertEqual(rows.map(\.id), ["m3", "m1", "m2", "m4", "m5"])
        }
    }

    func testCapturedExamplesIncludeUnmarkedRowsAndUseLatestMistake() throws {
        try seededQueue().read { db in
            let groups = try MistakeLibrary.capturedExamples(db, idiomID: "i2")
            XCTAssertEqual(groups.map(\.id), ["e1", "e3"])
            XCTAssertEqual(groups.map(\.createdAt), ["2026-11-14T00:00:00Z", "2026-11-10T00:00:00Z"])
            XCTAssertEqual(groups[0].mistakeState, "待复习")
            XCTAssertEqual(groups[0].reason, "望文生义")
            // e3 挂了 m2（已掌握）与 m3（待复习、无错因）两条错题记录，取最新的一条且不重复成行。
            XCTAssertEqual(groups[1].mistakeState, "待复习")
            XCTAssertNil(groups[1].reason)
            XCTAssertTrue(groups[1].isMistake)

            // 未标错的录入题同样在列（信息架构 v1.4 P09 ③），错题记录的有无决定状态位。
            let idioms = try MistakeLibrary.capturedExamples(db, idiomID: "i1")
            XCTAssertEqual(idioms.map(\.id), ["e1", "e2"])
            XCTAssertNil(idioms[1].mistakeState)
            XCTAssertFalse(idioms[1].isMistake)

            // 只按 user_example_idiom 归成语：e4 归 i3，该成语下没有带错题记录的题。
            let last = try MistakeLibrary.capturedExamples(db, idiomID: "i3")
            XCTAssertEqual(last.map(\.id), ["e4"])
            XCTAssertNil(last[0].mistakeState)
        }
    }

    func testCapturedIdiomReadsHeaderAndReturnsNilForUnknownIdiom() throws {
        try seededQueue().read { db in
            let info = try XCTUnwrap(try MistakeLibrary.capturedIdiom(db, idiomID: "i1"))
            XCTAssertEqual(info.word, "按部就班")
            XCTAssertEqual(info.sense, "按：依照；部、班：门类与次序。原指安排事务依照一定条理，后多指照老规矩办事，缺乏变通。")
            XCTAssertNil(try MistakeLibrary.capturedIdiom(db, idiomID: "missing"))
        }
    }

    /// 固定数据集：三条成语、四条录入题、五条错题（含两条 `userExampleId` 为 NULL 的历史行）。
    private func seededQueue() throws -> DatabaseQueue {
        let queue = try DatabaseQueue()
        try ChenYuBangDatabase.makeMigrator().migrate(queue)
        try queue.write { db in
            // 词条释义与感情色彩取 shared/fixtures/idioms.sample.json 原文，不自行改写。
            // swiftlint:disable:next large_tuple
            let idioms: [(String, String, String, String, String, String)] = [
                ("i1", "按部就班", "an bu jiu ban", "abjb", "贬义",
                 "按：依照；部、班：门类与次序。原指安排事务依照一定条理，后多指照老规矩办事，缺乏变通。"),
                ("i2", "墨守成规", "mo shou cheng gui", "mscg", "贬义",
                 "墨守：战国时墨翟善于守城，后指固执旧法；成规：既定的规则。指固执旧法，一成不变。"),
                ("i3", "别出心裁", "bie chu xin cai", "bcxc", "褒义",
                 "心裁：出于自心的创造与裁断。指想出的办法独创一格，与众不同。")
            ]
            for (id, word, pinyin, initials, color, sense) in idioms {
                try db.execute(sql: """
                    INSERT INTO idiom (id, word, pinyin, initials, sense, color, frequency)
                    VALUES (?, ?, ?, ?, ?, ?, 5)
                    """, arguments: [id, word, pinyin, initials, sense, color])
            }
            // swiftlint:disable:next large_tuple
            let examples: [(String, String, Bool, String)] = [
                ("e1", "下列各句中，加点的成语使用不恰当的一项是", true, "2026-11-14T00:00:00Z"),
                ("e2", "填入横线处最恰当的一项：改革不能______", false, "2026-11-09T00:00:00Z"),
                ("e3", "文段中三处成语使用，哪一处不当", true, "2026-11-10T00:00:00Z"),
                ("e4", "下列成语填入括号内最恰当的一项是", false, "2026-11-08T00:00:00Z")
            ]
            for (id, stem, isMistake, createdAt) in examples {
                try db.execute(
                    sql: "INSERT INTO user_example (id, stem, isMistake, createdAt) VALUES (?, ?, ?, ?)",
                    arguments: [id, stem, isMistake, createdAt]
                )
            }
            let exampleLinks = [("e1", "i1"), ("e1", "i2"), ("e2", "i1"), ("e3", "i2"), ("e4", "i3")]
            for (exampleID, idiomID) in exampleLinks {
                try db.execute(
                    sql: "INSERT INTO user_example_idiom (userExampleId, idiomId) VALUES (?, ?)",
                    arguments: [exampleID, idiomID]
                )
            }
            // id、userExampleId、题干、考试类型、掌握状态、错因、录入时间
            // swiftlint:disable:next large_tuple
            let mistakes: [(String, String?, String, String?, String, String?, String)] = [
                ("m1", "e1", "下列各句中，加点的成语使用不恰当的一项是", "国考", "待复习", "望文生义",
                 "2026-11-14T00:00:00Z"),
                ("m2", "e3", "文段中三处成语使用，哪一处不当", "省考", "已掌握", "对象误用",
                 "2026-11-10T00:00:00Z"),
                ("m3", "e3", "文段中三处成语使用，哪一处不当", "事业编", "待复习", nil,
                 "2026-11-12T00:00:00Z"),
                ("m4", nil, "历史导入的错题，没有对应录入题", nil, "待复习", nil,
                 "2026-11-08T00:00:00Z"),
                ("m5", nil, "历史导入的错题，没有对应录入题", nil, "待复习", "褒贬误用",
                 "2026-11-07T00:00:00Z")
            ]
            for (id, exampleID, stem, examType, state, reason, createdAt) in mistakes {
                try db.execute(sql: """
                    INSERT INTO mistake (id, userExampleId, stem, optionA, optionB, optionC, optionD,
                                         examType, reason, state, createdAt)
                    VALUES (?, ?, ?, '', '', '', '', ?, ?, ?, ?)
                    """, arguments: [id, exampleID, stem, examType, reason, state, createdAt])
            }
            let mistakeLinks = [
                ("m1", "i1"), ("m1", "i2"), ("m2", "i2"), ("m2", "i3"),
                ("m3", "i2"), ("m4", "i1"), ("m5", "i3")
            ]
            for (mistakeID, idiomID) in mistakeLinks {
                try db.execute(
                    sql: "INSERT INTO mistake_idiom (mistakeId, idiomId) VALUES (?, ?)",
                    arguments: [mistakeID, idiomID]
                )
            }
        }
        return queue
    }
}
