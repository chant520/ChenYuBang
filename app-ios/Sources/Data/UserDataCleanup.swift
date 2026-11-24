import Foundation
import GRDB

public enum ClearDataError: Error, Equatable {
    case database(String)
}

public struct ClearDataCounts: Equatable, Sendable {
    public let mistakes: Int
    public let userExamples: Int
    public let favorites: Int

    public init(mistakes: Int, userExamples: Int, favorites: Int) {
        self.mistakes = mistakes
        self.userExamples = userExamples
        self.favorites = favorites
    }
}

public final class UserDataCleaner: @unchecked Sendable {
    private let queue: DatabaseQueue

    public init(queue: DatabaseQueue) {
        self.queue = queue
    }

    public func counts() throws -> ClearDataCounts {
        try queue.read { db in
            let mistakes = try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM mistake") ?? 0
            let userExamples = try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM user_example") ?? 0
            let favorites = try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM idiom_state WHERE favorite = 1") ?? 0
            return ClearDataCounts(mistakes: mistakes, userExamples: userExamples, favorites: favorites)
        }
    }

    public func clear() throws -> Date {
        let clearedAt = Date()
        do {
            try queue.write { db in
                try db.execute(sql: "DELETE FROM review_log")
                try db.execute(sql: "DELETE FROM user_example_idiom")
                try db.execute(sql: "DELETE FROM mistake_idiom")
                try db.execute(sql: "DELETE FROM user_example")
                try db.execute(sql: "DELETE FROM mistake")
                try db.execute(sql: "DELETE FROM idiom_state")
                let stamp = Backup.stamp(clearedAt)
                try db.execute(
                    sql: "INSERT OR REPLACE INTO settings(key, value) VALUES (?, ?)",
                    arguments: ["lastClearedAt", stamp]
                )
            }
        } catch {
            throw ClearDataError.database(String(describing: error))
        }
        return clearedAt
    }
}
