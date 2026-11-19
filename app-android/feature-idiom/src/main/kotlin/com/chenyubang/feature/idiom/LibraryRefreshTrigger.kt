package com.chenyubang.feature.idiom

/**
 * 进入成语库页时自动尝试拉取题库的触发点。
 *
 * 开发规范 v1.4 §4.4：每次进入 P03 成语库都自动尝试拉取一次（每次进入触发一次，
 * 不做后台定时拉取），失败回落内置包、版号不变、可到 P16 手动重试。
 * 阶段 1 只留接口与调用点，联网获取在**阶段 5**接入 —— 当前实现必须是空操作，
 * 不得在阶段 1 发起任何网络请求（验收第 19 条）。
 */
fun interface LibraryRefreshTrigger {
    suspend fun onEnterLibrary()
}

/** 阶段 1 的空实现：不联网、不改库、不阻断浏览。 */
val NoLibraryRefreshTrigger: LibraryRefreshTrigger =
    LibraryRefreshTrigger {
        // 阶段 5 接入题库获取（两段式 latest.json + 整包校验 + 原子切换）。
    }
