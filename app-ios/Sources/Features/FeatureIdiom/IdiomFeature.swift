// feature-idiom 模块占位：成语学习页面簇。
//
// 负责页面（信息架构 v1.2 §3 页面清单，路由键见开发规范 v1.4 §5.1）：
//  - P02 推荐首页（路由键 tab/recommend，一级，底部标签①首屏）
//  - P03 成语库（library，二级）
//  - P04 搜索（search，全屏浮层）
//  - P05 成语详情（idiom/{id}，二级）
//
// 模块职责：
//  - 成语库的浏览与检索（按范围 / 按拼音首字母 / 按考频三种维度，右侧字母索引条）。
//  - 成语详情的信息呈现（释义、出处、易错点、近义反义、相关真题、我的错题计数、单成语复习入口）。
//  - 页面级 View 与本簇内的数据装配，自带数据层，不跨簇复用数据类。
//
// 依赖方向（开发规范 v1.4 §3 硬规则）：
//  - 允许依赖：DesignSystem / Tokens / Data 等 core 层。
//  - 禁止依赖：任何其他 feature 分组——feature 之间不得互相依赖。
//
// swiftlint:disable todo
// TODO(P02–P05): 按路由表逐页接入界面与视图模型；
// 视觉一律取 Tokens 令牌常量，禁止在页面里写颜色 / 字号 / 圆角 / 间距字面量。
// swiftlint:enable todo

/// feature-idiom 模块占位类型。真实页面实现后由本类型持有各页的入口。
public enum IdiomFeature {

    /// 本模块负责的页面号，便于路由接线时核对归属（信息架构 v1.2 §3）。
    public static let ownedPages: [String] = ["P02", "P03", "P04", "P05"]
}
