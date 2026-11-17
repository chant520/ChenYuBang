// feature-capture 模块占位：拍照录题链路页面簇。
//
// 负责页面（信息架构 v1.2 §3 页面清单，路由键见开发规范 v1.4 §5.1）：
//  - P17 拍照 / 相册选择（capture/camera，全屏浮层，第 1 步）
//  - P18 裁切与摆正（capture/crop，全屏浮层，第 2 步）
//  - P19 识别结果（capture/ocr，全屏浮层，第 3 步）
//  - P20 成语匹配与修正（capture/match，全屏浮层，第 4 步）
//  - P21 保存成功（capture/done，全屏浮层，第 5 步）
//  - P22 导入方式选择（sheet/import-mode，底部 sheet）
//  - P23 清空数据确认（dialog/clear，居中弹层）
//
// 模块职责：
//  - 拍照链路五步单向推进，步骤文案写死「第 N 步 / 共 5 步」；任一步取消回到触发拍照的那一页，
//    已拍的图与已改的字全部丢弃，不留半成品草稿。
//  - 起点只有四个主标签页（P02 / P06 / P08 / P13）；二级页与详情页不提供相机入口。
//  - 文字识别走端侧离线 OCR 引擎（Vision zh-Hans），零联网；识别结果进 P19 可编辑文本框，缺字走手动补字。
//  - P20 匹配候选默认勾选最匹配一项；候选为空时给「手动挑成语」入口。
//  - P22「覆盖」用红徽标、「合并」用绿标签；P23 红填充按钮且是本页唯一红。
//
// 依赖方向（开发规范 v1.4 §3 硬规则）：
//  - 允许依赖：DesignSystem / Tokens / Data 等 core 层。
//  - 禁止依赖：任何其他 feature 分组——feature 之间不得互相依赖。
//
// swiftlint:disable todo
// TODO(P17–P23): 按路由表逐页接入界面与视图模型；
// 视觉一律取 Tokens 令牌常量，禁止在页面里写颜色 / 字号 / 圆角 / 间距字面量。
// swiftlint:enable todo

/// feature-capture 模块占位类型。真实页面实现后由本类型持有各页的入口。
public enum CaptureFeature {

    /// 本模块负责的页面号，便于路由接线时核对归属（信息架构 v1.2 §3）。
    public static let ownedPages: [String] = ["P17", "P18", "P19", "P20", "P21", "P22", "P23"]
}
