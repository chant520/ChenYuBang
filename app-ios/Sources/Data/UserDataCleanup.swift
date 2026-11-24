import Foundation
import GRDB

public enum ClearDataError: Error, Equatable {
    case database(String)
}

public final class UserDataCleaner: @unchecked Sendable {
    private let queue: DatabaseQueue

    public init(queue: DatabaseQueue) {
        self.queue = queue
    }

    public func clear() throws -> Date {
        let clearedAt = Date()
        try queue.write { db in
            try db.inTransaction {
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
                return .commit
            }
        } catch {
            throw ClearDataError.database(String(describing: error))
        }
        return clearedAt
    }
}
