import Foundation

enum BackupSchema {
    private static let uuid = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"
    private static let timestamp = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z$"
    private static let examTypes: Set<String> = ["国考", "省考", "事业编", "教师编"]

    static let manifest: PackRule = .object(
        required: ["schemaVersion", "appVersion", "build", "exportedAt", "counts", "sha256"], properties: [
            "schemaVersion": .integer(minimum: 1, maximum: Int.max),
            "appVersion": pattern("^\\d+\\.\\d+\\.\\d+"),
            "build": .integer(minimum: 1, maximum: Int.max),
            "exportedAt": pattern(timestamp),
            "sha256": pattern("^[0-9a-f]{64}$"),
            "counts": .object(required: ["mistakes", "reviews", "idiomStates", "scopes"], properties: [
                "mistakes": .integer(minimum: 0, maximum: Int.max),
                "userExamples": .integer(minimum: 0, maximum: Int.max),
                "reviews": .integer(minimum: 0, maximum: Int.max),
                "idiomStates": .integer(minimum: 0, maximum: Int.max),
                "scopes": .integer(minimum: 0, maximum: Int.max)
            ])
        ]
    )

    static let data: PackRule = .object(
        required: ["mistakes", "reviews", "idiomStates", "scopes", "tags"], properties: [
            "mistakes": .array(mistake, minimum: 0),
            "userExamples": .array(userExample, minimum: 0),
            "reviews": .array(review, minimum: 0),
            "idiomStates": .array(idiomState, minimum: 0),
            "scopes": .array(scope, minimum: 0),
            "tags": .array(tag, minimum: 0)
        ]
    )

    private static let mistake: PackRule = .object(
        required: ["id", "stem", "options", "state", "createdAt", "reviewCount", "idiomIds"], properties: [
            "id": pattern(uuid), "userExampleId": .nullable(pattern(uuid)),
            "stem": pattern(".{2,}"),
            "options": .object(required: ["A", "B", "C", "D"], properties: [
                "A": plainString, "B": plainString, "C": plainString, "D": plainString
            ]),
            "imagePath": .nullable(plainString), "ocrText": .nullable(plainString),
            "myAnswer": .nullable(enumeration(["A", "B", "C", "D"])),
            "reason": .nullable(plainString),
            "tagNames": .array(pattern(".{1,8}"), minimum: 0),
            "examType": .nullable(enumeration(examTypes)),
            "year": .nullable(.integer(minimum: 2_000, maximum: 2_100)),
            "region": .nullable(plainString),
            "state": enumeration(["待复习", "已掌握"]),
            "createdAt": pattern(timestamp), "lastReviewedAt": .nullable(plainString),
            "reviewCount": .integer(minimum: 0, maximum: Int.max),
            "idiomIds": .array(pattern(uuid), minimum: 1)
        ]
    )

    private static let userExample: PackRule = .object(
        required: ["id", "stem", "isMistake", "createdAt", "idiomIds"], properties: [
            "id": pattern(uuid), "imagePath": .nullable(plainString), "ocrText": .nullable(plainString),
            "stem": pattern(".{2,}"), "optionA": .nullable(plainString), "optionB": .nullable(plainString),
            "optionC": .nullable(plainString), "optionD": .nullable(plainString),
            "myAnswer": .nullable(enumeration(["A", "B", "C", "D"])),
            "isMistake": .boolean,
            "examType": .nullable(enumeration(examTypes)),
            "year": .nullable(.integer(minimum: 2_000, maximum: 2_100)),
            "region": .nullable(plainString), "createdAt": pattern(timestamp),
            "idiomIds": .array(pattern(uuid), minimum: 1)
        ]
    )

    private static let review: PackRule = .object(
        required: ["id", "mistakeId", "result", "at"], properties: [
            "id": pattern(uuid), "mistakeId": pattern(uuid),
            "result": enumeration(["对", "错"]), "at": pattern(timestamp)
        ]
    )

    private static let idiomState: PackRule = .object(
        required: ["idiomId", "status", "favorite"], properties: [
            "idiomId": pattern(uuid), "status": enumeration(["未学", "学过", "已掌握"]),
            "favorite": .boolean
        ]
    )

    private static let scope: PackRule = .object(
        required: ["type", "enabled"], properties: [
            "type": enumeration(examTypes), "province": .nullable(plainString), "enabled": .boolean
        ]
    )

    private static let tag: PackRule = .object(
        required: ["name"], properties: ["name": pattern(".{1,8}")]
    )

    private static var plainString: PackRule { .string(pattern: nil, values: nil) }
    private static func pattern(_ regex: String) -> PackRule { .string(pattern: regex, values: nil) }
    private static func enumeration(_ values: Set<String>) -> PackRule { .string(pattern: nil, values: values) }
}
