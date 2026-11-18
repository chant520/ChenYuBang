import Foundation

enum RouteOwner: String, CaseIterable, Codable {
    case app, idiom, example, mistake, review, profile, capture
}

enum RoutePresentation: String, Codable {
    case root, push, fullScreen, sheet, dialog
}

/// 键、顺序与开发规范 §5.1 的23行严格一致。归属不是模块间依赖。
enum AppRoute: String, CaseIterable, Codable {
    case onboarding = "onboarding"
    case recommend = "tab/recommend"
    case library = "library"
    case search = "search"
    case idiomDetail = "idiom/{id}"
    case examples = "tab/examples"
    case exampleDetail = "example/{id}"
    case mistakes = "tab/mistakes"
    case idiomMistakes = "mistakes/idiom/{id}"
    case mistakeDetail = "mistake/{id}"
    case queue = "review/queue"
    case summary = "review/summary"
    case profile = "tab/me"
    case scope = "settings/scope"
    case backup = "settings/backup"
    case pack = "settings/pack"
    case camera = "capture/camera"
    case crop = "capture/crop"
    case ocr = "capture/ocr"
    case match = "capture/match"
    case done = "capture/done"
    case importMode = "sheet/import-mode"
    case clear = "dialog/clear"

    var owner: RouteOwner {
        switch self {
        case .onboarding: return .app
        case .recommend, .library, .search, .idiomDetail: return .idiom
        case .examples, .exampleDetail: return .example
        case .mistakes, .idiomMistakes, .mistakeDetail: return .mistake
        case .queue, .summary: return .review
        case .profile, .scope, .backup, .pack: return .profile
        case .camera, .crop, .ocr, .match, .done, .importMode, .clear: return .capture
        }
    }

    var presentation: RoutePresentation {
        switch self {
        case .recommend, .examples, .mistakes, .profile: return .root
        case .library, .idiomDetail, .exampleDetail, .idiomMistakes, .mistakeDetail, .scope, .backup, .pack:
            return .push
        case .importMode: return .sheet
        case .clear: return .dialog
        default: return .fullScreen
        }
    }

    var title: String {
        switch self {
        case .onboarding: return "考试范围"
        case .recommend: return "推荐"
        case .library: return "成语库"
        case .search: return "搜索"
        case .idiomDetail: return "成语详情"
        case .examples: return "例题"
        case .exampleDetail: return "例题详情"
        case .mistakes: return "错题"
        case .idiomMistakes: return "某成语下错题"
        case .mistakeDetail: return "错题详情"
        case .queue: return "复习队列"
        case .summary: return "复习小结"
        case .profile: return "我的"
        case .scope: return "考试范围设置"
        case .backup: return "备份与恢复"
        case .pack: return "题库版本"
        case .camera: return "拍照"
        case .crop: return "裁切"
        case .ocr: return "识别结果"
        case .match: return "匹配修正"
        case .done: return "保存成功"
        case .importMode: return "导入方式"
        case .clear: return "清空确认"
        }
    }
}

enum AppTab: String, CaseIterable, Codable {
    case idiom, example, mistake, profile

    var root: AppRoute {
        switch self {
        case .idiom: return .recommend
        case .example: return .examples
        case .mistake: return .mistakes
        case .profile: return .profile
        }
    }

    var label: String {
        switch self {
        case .idiom: return "成语"
        case .example: return "例题"
        case .mistake: return "错题"
        case .profile: return "我的"
        }
    }
}

enum RouteRegistry {
    static let fabRoutes: Set<AppRoute> = [.recommend, .examples, .mistakes, .profile]
    static let captureSteps: [AppRoute] = [.camera, .crop, .ocr, .match, .done]
}
