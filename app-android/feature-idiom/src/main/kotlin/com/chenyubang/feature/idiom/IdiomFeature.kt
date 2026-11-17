package com.chenyubang.feature.idiom

/**
 * feature-idiom 模块占位：成语学习页面簇。
 *
 * 负责页面（信息架构 v1.2 §3 页面清单，路由键见开发规范 v1.4 §5.1）：
 *  - P02 推荐首页（路由键 `tab/recommend`，一级，底部标签①首屏）
 *  - P03 成语库（`library`，二级）
 *  - P04 搜索（`search`，全屏浮层）
 *  - P05 成语详情（`idiom/{id}`，二级）
 *
 * 模块职责：
 *  - 成语库的浏览与检索（按范围 / 按拼音首字母 / 按考频三种维度，右侧字母索引条）。
 *  - 成语详情的信息呈现（释义、出处、易错点、近义反义、相关真题、我的错题计数、单成语复习入口）。
 *  - 页面级 ViewModel 与本簇内的数据装配，自带 Repository，不跨簇复用数据类。
 *
 * 依赖方向（开发规范 v1.4 §3 硬规则）：
 *  - 允许依赖：`:core-ui`（构件与令牌）、`:core-data`（成语 / 例题数据）。
 *  - 禁止依赖：任何其他 feature 模块——feature 之间不得互相依赖。
 *  - `:core-ui` 不得反向依赖本模块。
 *
 * TODO(P02–P05): 按路由表逐页接入界面与 ViewModel；
 * 视觉一律取 core-ui 令牌常量，禁止在页面里写颜色 / 字号 / 圆角 / 间距字面量。
 */
object IdiomFeature {
    /** 本模块负责的页面号，便于路由接线时核对归属（信息架构 v1.2 §3）。 */
    val OWNED_PAGES: List<String> = listOf("P02", "P03", "P04", "P05")
}
