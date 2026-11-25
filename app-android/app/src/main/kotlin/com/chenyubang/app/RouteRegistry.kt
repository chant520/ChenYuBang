package com.chenyubang.app

enum class RouteOwner { App, Idiom, Example, Mistake, Review, Me, Capture }

enum class Presentation { Root, Push, FullScreen, Sheet, Dialog }

/** 顺序、键和归属严格按开发规范第 3 节 / 第 5.1 节。归属不是 feature 之间的依赖。 */
enum class Route(val key: String, val title: String, val owner: RouteOwner, val presentation: Presentation) {
    Onboarding("onboarding", "考试范围", RouteOwner.App, Presentation.FullScreen),
    Recommend("tab/recommend", "推荐", RouteOwner.Idiom, Presentation.Root),
    Library("library", "成语库", RouteOwner.Idiom, Presentation.Push),
    Search("search", "搜索", RouteOwner.Idiom, Presentation.FullScreen),
    Idiom("idiom/{id}", "成语详情", RouteOwner.Idiom, Presentation.Push),
    Examples("tab/examples", "例题", RouteOwner.Example, Presentation.Root),
    Example("example/{id}", "例题详情", RouteOwner.Example, Presentation.Push),
    Mistakes("tab/mistakes", "错题", RouteOwner.Mistake, Presentation.Root),
    IdiomMistakes("mistakes/idiom/{id}", "某成语下错题", RouteOwner.Mistake, Presentation.Push),
    Mistake("mistake/{id}", "错题详情", RouteOwner.Mistake, Presentation.Push),
    Queue("review/queue", "复习队列", RouteOwner.Review, Presentation.FullScreen),
    Summary("review/summary", "复习小结", RouteOwner.Review, Presentation.FullScreen),
    Me("tab/me", "我的", RouteOwner.Me, Presentation.Root),
    Scope("settings/scope", "考试范围设置", RouteOwner.Me, Presentation.Push),
    Backup("settings/backup", "备份与恢复", RouteOwner.Me, Presentation.Push),
    Pack("settings/pack", "题库版本与更新", RouteOwner.Me, Presentation.Push),
    SelfTest("review/self-test", "成语自测", RouteOwner.Review, Presentation.FullScreen),
    Camera("capture/camera", "拍照", RouteOwner.Capture, Presentation.FullScreen),
    Crop("capture/crop", "裁切", RouteOwner.Capture, Presentation.FullScreen),
    Ocr("capture/ocr", "识别结果", RouteOwner.Capture, Presentation.FullScreen),
    Match("capture/match", "匹配修正", RouteOwner.Capture, Presentation.FullScreen),
    Done("capture/done", "保存成功", RouteOwner.Capture, Presentation.FullScreen),
    ImportMode("sheet/import-mode", "导入方式", RouteOwner.Capture, Presentation.Sheet),
    Clear("dialog/clear", "清空确认", RouteOwner.Capture, Presentation.Dialog),
    ;

    fun path(id: String): String {
        require(id.isNotBlank())
        return key.replace("{id}", java.net.URLEncoder.encode(id, "UTF-8"))
    }
}

enum class AppTab(val root: Route, val label: String) {
    Idiom(Route.Recommend, "成语"),
    Example(Route.Examples, "例题"),
    Mistake(Route.Mistakes, "错题"),
    Me(Route.Me, "我的"),
}

object RouteRegistry {
    val fabWhitelist = AppTab.entries.map { it.root }.toSet()
    val captureSteps = listOf(Route.Camera, Route.Crop, Route.Ocr, Route.Match, Route.Done)
}
