// feature-example 模块占位：例题页面簇。
//
// 负责页面（信息架构 v1.2 §3 页面清单，路由键见开发规范 v1.4 §5.1）：
//  - P06 例题列表（路由键 tab/examples，一级，底部标签②首屏）
//  - P07 例题详情（example/{id}，二级）
//
// 模块职责：
//  - 按题库顺序浏览真题，条目含题干摘要、出处（年份·地区·考试类型·题型）、关联成语数。
//  - 例题详情呈现题干、四个选项、正确答案、一句解析与出处；题干 / 选项 / 解析里的成语可点，跳 P05。
//  - 一题多成语的关联展示（B3）。
//  - 筛选条、搜索位、做题判分、标记位按信息架构 v1.2 §3.2 只作进阶预留位，不在 MVP 实现。
//
// 依赖方向（开发规范 v1.4 §3 硬规则）：
//  - 允许依赖：DesignSystem / Tokens / Data 等 core 层。
//  - 禁止依赖：任何其他 feature 分组——feature 之间不得互相依赖。
//
// swiftlint:disable todo
// TODO(P06–P07): 按路由表逐页接入界面与视图模型；
// 视觉一律取 Tokens 令牌常量，禁止在页面里写颜色 / 字号 / 圆角 / 间距字面量。
// swiftlint:enable todo

/// feature-example 模块占位类型。真实页面实现后由本类型持有各页的入口。
public enum ExampleFeature {

    /// 本模块负责的页面号，便于路由接线时核对归属（信息架构 v1.2 §3）。
    public static let ownedPages: [String] = ["P06", "P07"]
}
