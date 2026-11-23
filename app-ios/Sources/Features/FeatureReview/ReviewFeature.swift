// feature-review 模块占位：复习页面簇。
//
// 负责页面（信息架构 v1.2 §3 页面清单，路由键见开发规范 v1.4 §5.1）：
//  - P11 复习队列（review/queue，全屏浮层；全局模式与单成语模式共用本页）
//  - P12 复习小结（review/summary，全屏浮层）
//
// 模块职责：
//  - 队列 = 全部 state = 待复习 的错题，按 createdAt 倒序；单成语模式按 idiomId 过滤，无内容时按钮置灰。
//  - 页内「本轮范围」一行区分两种来源（全部待复习 / 只看「某成语」）。
//  - 选完立即判分，口径沿用 P10 重做：与首次「我的答案」不同＝答对、相同＝答错，只写一行 review_log
//    并同事务刷新 reviewCount / lastReviewedAt，不自动改 state（开发规范 v1.7 第 8.2 节）。
//    录入题没有正确答案与解析来源，页面不渲染两者；无法比对的题不写 review_log、只计题量。
//  - 退出中途二次确认；一轮结束进小结，题量 / 判分题数 / 正确数 / 涉及成语列表与操作保持一致。
//  - 连续答对的掌握状态提示（C9）只作进阶预留位，不在 MVP 实现。
//
// 依赖方向（开发规范 v1.4 §3 硬规则）：
//  - 允许依赖：DesignSystem / Tokens / Data 等 core 层。
//  - 禁止依赖：任何其他 feature 分组——feature 之间不得互相依赖。

/// feature-review 模块占位类型。真实页面实现后由本类型持有各页的入口。
public enum ReviewFeature {

    /// 本模块负责的页面号，便于路由接线时核对归属（信息架构 v1.2 §3）。
    public static let ownedPages: [String] = ["P11", "P12"]
}
