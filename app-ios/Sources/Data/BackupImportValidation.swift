import Foundation

extension BackupDataPayload {
    func validate() throws {
        try validateTagsAndKeys()
        try validateMistakes()
        try validateExamples()
        try validateReviews()
        try validateStatesAndScopes()
    }

    private func validateTagsAndKeys() throws {
        let tagNames = Set(tags.map(\.name))
        guard tagNames.count == tags.count else { throw BackupError.format("备份中有重复标签。") }
        let stateIDs = idiomStates.map(\.idiomId)
        guard Set(stateIDs).count == stateIDs.count else { throw BackupError.format("备份中有重复成语学习状态。") }
        let scopeKeys = scopes.map { "\($0.type)\u{0}\($0.province ?? "")" }
        guard Set(scopeKeys).count == scopeKeys.count else { throw BackupError.format("备份中有重复考试范围。") }
    }

    private func validateMistakes() throws {
        let tagNames = Set(tags.map(\.name))
        let reviewCounts = Dictionary(grouping: reviews, by: \.mistakeId).mapValues(\.count)
        for mistake in mistakes {
            try validateMistake(mistake, tagNames: tagNames, reviewCounts: reviewCounts)
        }
    }

    private func validateMistake(_ mistake: BackupMistakePayload, tagNames: Set<String>,
                                 reviewCounts: [String: Int]) throws {
        try mistake.validate()
        _ = try BackupImport.uniqueIDs(mistake.idiomIds, "错题成语关联")
        let names = mistake.tagNames ?? []
        guard names.allSatisfy({ tagNames.contains($0) }) else {
            throw BackupError.format("错题 \(mistake.id) 的标签不在 tags 投影中。")
        }
        guard reviewCounts[mistake.id] ?? 0 == mistake.reviewCount else {
            throw BackupError.format("错题 \(mistake.id) 的复习条数与复习记录不一致。")
        }
        if let path = mistake.imagePath, !path.hasPrefix("images/") {
            throw BackupError.format("错题 \(mistake.id) 的原图路径不合法。")
        }
    }

    private func validateExamples() throws {
        for example in userExamples ?? [] {
            try example.validate()
            _ = try BackupImport.uniqueIDs(example.idiomIds, "录入题成语关联")
            if let path = example.imagePath, !path.hasPrefix("images/") {
                throw BackupError.format("录入题 \(example.id) 的原图路径不合法。")
            }
        }
    }

    private func validateReviews() throws {
        for review in reviews {
            try BackupValidationRule.require(review.id, "复习记录 id")
            try BackupValidationRule.require(review.mistakeId, "复习记录 mistakeId")
            guard ["对", "错"].contains(review.result) else { throw BackupError.format("复习结果不合法。") }
        }
    }

    private func validateStatesAndScopes() throws {
        for state in idiomStates {
            try BackupValidationRule.require(state.idiomId, "学习状态 idiomId")
            guard ["未学", "学过", "已掌握"].contains(state.status) else {
                throw BackupError.format("成语学习状态不合法。")
            }
        }
        for scope in scopes {
            guard ["国考", "省考", "事业编", "教师编"].contains(scope.type) else {
                throw BackupError.format("考试范围类型不合法。")
            }
        }
        for tag in tags {
            guard (1...8).contains(tag.name.count) else { throw BackupError.format("错因标签长度须为 1–8 字。") }
        }
    }
}

extension BackupMistakePayload {
    fileprivate func validate() throws {
        try BackupValidationRule.require(id, "错题 id")
        guard stem.count >= 2, ["待复习", "已掌握"].contains(state), reviewCount >= 0,
              BackupValidationRule.validAnswer(myAnswer),
              BackupValidationRule.validExamType(examType), BackupValidationRule.validYear(year)
        else { throw BackupError.format("错题 \(id) 字段不符合备份格式。") }
    }
}

extension BackupUserExamplePayload {
    fileprivate func validate() throws {
        try BackupValidationRule.require(id, "录入题 id")
        guard stem.count >= 2, BackupValidationRule.validAnswer(myAnswer),
              BackupValidationRule.validExamType(examType), BackupValidationRule.validYear(year)
        else { throw BackupError.format("录入题 \(id) 字段不符合备份格式。") }
    }
}

enum BackupValidationRule {
    static func require(_ value: String, _ label: String) throws {
        guard value.count == 36, UUID(uuidString: value) != nil, value == value.lowercased() else {
            throw BackupError.format("\(label) 不是小写 UUID。")
        }
    }

    static func validAnswer(_ answer: String?) -> Bool {
        answer == nil || ["A", "B", "C", "D"].contains(answer ?? "")
    }

    static func validExamType(_ type: String?) -> Bool {
        type == nil || ["国考", "省考", "事业编", "教师编"].contains(type ?? "")
    }

    static func validYear(_ year: Int?) -> Bool {
        year == nil || (2_000...2_100).contains(year ?? 0)
    }
}
