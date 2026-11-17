// GENERATED FILE — DO NOT EDIT.
// Source: shared/tokens/tokens.json (docs/设计规范-v1.2.md)
// Regenerate: python tools/gen_tokens.py

import SwiftUI

/// 设计令牌。数值唯一来源：docs/设计规范-v1.2.md
public struct TokenColors: Sendable {
    public let accent: Color
    public let accent2: Color
    public let btnbg: Color
    public let btnfg: Color
    public let chkfg: Color
    public let cut: Color
    public let disbg: Color
    public let disfg: Color
    public let disline: Color
    public let errbg: Color
    public let errfg: Color
    public let errfill: Color
    public let errfillfg: Color
    public let errline: Color
    public let fab: Color
    public let fabicon: Color
    public let head: Color
    public let hot: Color
    public let inputbg: Color
    public let line: Color
    public let line2: Color
    public let loadbg: Color
    public let loadfg: Color
    public let modal: Color
    public let page: Color
    public let phbg: Color
    public let phline: Color
    public let seg: Color
    public let segon: Color
    public let segonfg: Color
    public let skel: Color
    public let skelhi: Color
    public let surface: Color
    public let surface2: Color
    public let tabbg: Color
    public let text: Color
    public let text2: Color
    public let text3: Color
    public let tintbg: Color
    public let tintfg: Color
    public let tintline: Color
    public let warnbg: Color
    public let warnfg: Color
    public let warnline: Color

    public init(
        accent: Color,
        accent2: Color,
        btnbg: Color,
        btnfg: Color,
        chkfg: Color,
        cut: Color,
        disbg: Color,
        disfg: Color,
        disline: Color,
        errbg: Color,
        errfg: Color,
        errfill: Color,
        errfillfg: Color,
        errline: Color,
        fab: Color,
        fabicon: Color,
        head: Color,
        hot: Color,
        inputbg: Color,
        line: Color,
        line2: Color,
        loadbg: Color,
        loadfg: Color,
        modal: Color,
        page: Color,
        phbg: Color,
        phline: Color,
        seg: Color,
        segon: Color,
        segonfg: Color,
        skel: Color,
        skelhi: Color,
        surface: Color,
        surface2: Color,
        tabbg: Color,
        text: Color,
        text2: Color,
        text3: Color,
        tintbg: Color,
        tintfg: Color,
        tintline: Color,
        warnbg: Color,
        warnfg: Color,
        warnline: Color
    ) {
        self.accent = accent
        self.accent2 = accent2
        self.btnbg = btnbg
        self.btnfg = btnfg
        self.chkfg = chkfg
        self.cut = cut
        self.disbg = disbg
        self.disfg = disfg
        self.disline = disline
        self.errbg = errbg
        self.errfg = errfg
        self.errfill = errfill
        self.errfillfg = errfillfg
        self.errline = errline
        self.fab = fab
        self.fabicon = fabicon
        self.head = head
        self.hot = hot
        self.inputbg = inputbg
        self.line = line
        self.line2 = line2
        self.loadbg = loadbg
        self.loadfg = loadfg
        self.modal = modal
        self.page = page
        self.phbg = phbg
        self.phline = phline
        self.seg = seg
        self.segon = segon
        self.segonfg = segonfg
        self.skel = skel
        self.skelhi = skelhi
        self.surface = surface
        self.surface2 = surface2
        self.tabbg = tabbg
        self.text = text
        self.text2 = text2
        self.text3 = text3
        self.tintbg = tintbg
        self.tintfg = tintfg
        self.tintline = tintline
        self.warnbg = warnbg
        self.warnfg = warnfg
        self.warnline = warnline
    }
}

public enum Tokens {
    public static let light = TokenColors(
        accent: Color(red: 0.0000, green: 0.5020, blue: 0.0000, opacity: 1.0000),
        accent2: Color(red: 0.0000, green: 0.6196, blue: 0.3765, opacity: 1.0000),
        btnbg: Color(red: 0.0000, green: 0.5020, blue: 0.0000, opacity: 1.0000),
        btnfg: Color(red: 1.0000, green: 1.0000, blue: 1.0000, opacity: 1.0000),
        chkfg: Color(red: 1.0000, green: 1.0000, blue: 1.0000, opacity: 1.0000),
        cut: Color(red: 0.7333, green: 0.8627, blue: 0.7882, opacity: 1.0000),
        disbg: Color(red: 0.9020, green: 0.9373, blue: 0.9137, opacity: 1.0000),
        disfg: Color(red: 0.5765, green: 0.6627, blue: 0.6118, opacity: 1.0000),
        disline: Color(red: 0.8627, green: 0.9098, blue: 0.8824, opacity: 1.0000),
        errbg: Color(red: 0.9922, green: 0.9255, blue: 0.9176, opacity: 1.0000),
        errfg: Color(red: 0.7765, green: 0.1569, blue: 0.1569, opacity: 1.0000),
        errfill: Color(red: 0.7765, green: 0.1569, blue: 0.1569, opacity: 1.0000),
        errfillfg: Color(red: 1.0000, green: 1.0000, blue: 1.0000, opacity: 1.0000),
        errline: Color(red: 0.9412, green: 0.7451, blue: 0.7451, opacity: 1.0000),
        fab: Color(red: 0.0000, green: 0.5020, blue: 0.0000, opacity: 1.0000),
        fabicon: Color(red: 1.0000, green: 1.0000, blue: 1.0000, opacity: 1.0000),
        head: Color(red: 0.0706, green: 0.1608, blue: 0.1098, opacity: 1.0000),
        hot: Color(red: 0.0000, green: 0.6196, blue: 0.3765, opacity: 1.0000),
        inputbg: Color(red: 0.9686, green: 0.9843, blue: 0.9725, opacity: 1.0000),
        line: Color(red: 0.8392, green: 0.9176, blue: 0.8667, opacity: 1.0000),
        line2: Color(red: 0.8941, green: 0.9451, blue: 0.9137, opacity: 1.0000),
        loadbg: Color(red: 0.0000, green: 0.5020, blue: 0.0000, opacity: 1.0000),
        loadfg: Color(red: 1.0000, green: 1.0000, blue: 1.0000, opacity: 1.0000),
        modal: Color(red: 0.0471, green: 0.1020, blue: 0.0706, opacity: 0.5490),
        page: Color(red: 0.9529, green: 0.9765, blue: 0.9608, opacity: 1.0000),
        phbg: Color(red: 0.9255, green: 0.9686, blue: 0.9412, opacity: 1.0000),
        phline: Color(red: 0.8118, green: 0.9098, blue: 0.8510, opacity: 1.0000),
        seg: Color(red: 0.9255, green: 0.9686, blue: 0.9412, opacity: 1.0000),
        segon: Color(red: 0.0000, green: 0.5020, blue: 0.0000, opacity: 1.0000),
        segonfg: Color(red: 1.0000, green: 1.0000, blue: 1.0000, opacity: 1.0000),
        skel: Color(red: 0.8627, green: 0.9137, blue: 0.8784, opacity: 1.0000),
        skelhi: Color(red: 0.9451, green: 0.9725, blue: 0.9529, opacity: 1.0000),
        surface: Color(red: 1.0000, green: 1.0000, blue: 1.0000, opacity: 1.0000),
        surface2: Color(red: 0.9294, green: 0.9725, blue: 0.9451, opacity: 1.0000),
        tabbg: Color(red: 1.0000, green: 1.0000, blue: 1.0000, opacity: 1.0000),
        text: Color(red: 0.0941, green: 0.2196, blue: 0.1569, opacity: 1.0000),
        text2: Color(red: 0.3098, green: 0.4196, blue: 0.3569, opacity: 1.0000),
        text3: Color(red: 0.3686, green: 0.4588, blue: 0.4118, opacity: 1.0000),
        tintbg: Color(red: 0.9412, green: 0.9804, blue: 0.9569, opacity: 1.0000),
        tintfg: Color(red: 0.0000, green: 0.5020, blue: 0.0000, opacity: 1.0000),
        tintline: Color(red: 0.7490, green: 0.9020, blue: 0.8000, opacity: 1.0000),
        warnbg: Color(red: 1.0000, green: 0.9529, blue: 0.9020, opacity: 1.0000),
        warnfg: Color(red: 0.7608, green: 0.2549, blue: 0.0471, opacity: 1.0000),
        warnline: Color(red: 0.9529, green: 0.7882, blue: 0.6588, opacity: 1.0000),
    )
    public static let dark = TokenColors(
        accent: Color(red: 0.3137, green: 0.7843, blue: 0.4706, opacity: 1.0000),
        accent2: Color(red: 0.0000, green: 0.6196, blue: 0.3765, opacity: 1.0000),
        btnbg: Color(red: 0.3137, green: 0.7843, blue: 0.4706, opacity: 1.0000),
        btnfg: Color(red: 0.0471, green: 0.1020, blue: 0.0706, opacity: 1.0000),
        chkfg: Color(red: 0.0471, green: 0.1020, blue: 0.0706, opacity: 1.0000),
        cut: Color(red: 0.1804, green: 0.2902, blue: 0.2235, opacity: 1.0000),
        disbg: Color(red: 0.1176, green: 0.2000, blue: 0.1529, opacity: 1.0000),
        disfg: Color(red: 0.4314, green: 0.5412, blue: 0.4706, opacity: 1.0000),
        disline: Color(red: 0.1686, green: 0.2745, blue: 0.2118, opacity: 1.0000),
        errbg: Color(red: 1.0000, green: 0.5412, blue: 0.5020, opacity: 0.1412),
        errfg: Color(red: 1.0000, green: 0.5412, blue: 0.5020, opacity: 1.0000),
        errfill: Color(red: 1.0000, green: 0.5412, blue: 0.5020, opacity: 1.0000),
        errfillfg: Color(red: 0.0471, green: 0.1020, blue: 0.0706, opacity: 1.0000),
        errline: Color(red: 1.0000, green: 0.5412, blue: 0.5020, opacity: 0.4196),
        fab: Color(red: 0.3137, green: 0.7843, blue: 0.4706, opacity: 1.0000),
        fabicon: Color(red: 0.0471, green: 0.1020, blue: 0.0706, opacity: 1.0000),
        head: Color(red: 0.9098, green: 0.9569, blue: 0.9216, opacity: 1.0000),
        hot: Color(red: 0.0000, green: 0.6196, blue: 0.3765, opacity: 1.0000),
        inputbg: Color(red: 0.0824, green: 0.1647, blue: 0.1255, opacity: 1.0000),
        line: Color(red: 0.1686, green: 0.2745, blue: 0.2118, opacity: 1.0000),
        line2: Color(red: 0.1373, green: 0.2235, blue: 0.1725, opacity: 1.0000),
        loadbg: Color(red: 0.3137, green: 0.7843, blue: 0.4706, opacity: 1.0000),
        loadfg: Color(red: 0.0471, green: 0.1020, blue: 0.0706, opacity: 1.0000),
        modal: Color(red: 0.0000, green: 0.0000, blue: 0.0000, opacity: 0.6196),
        page: Color(red: 0.0627, green: 0.1176, blue: 0.0902, opacity: 1.0000),
        phbg: Color(red: 0.0863, green: 0.1608, blue: 0.1216, opacity: 1.0000),
        phline: Color(red: 0.1647, green: 0.2667, blue: 0.2000, opacity: 1.0000),
        seg: Color(red: 0.0863, green: 0.1608, blue: 0.1216, opacity: 1.0000),
        segon: Color(red: 0.3137, green: 0.7843, blue: 0.4706, opacity: 1.0000),
        segonfg: Color(red: 0.0471, green: 0.1020, blue: 0.0706, opacity: 1.0000),
        skel: Color(red: 0.1373, green: 0.2235, blue: 0.1725, opacity: 1.0000),
        skelhi: Color(red: 0.1804, green: 0.2902, blue: 0.2235, opacity: 1.0000),
        surface: Color(red: 0.1020, green: 0.1922, blue: 0.1490, opacity: 1.0000),
        surface2: Color(red: 0.0824, green: 0.1647, blue: 0.1255, opacity: 1.0000),
        tabbg: Color(red: 0.0824, green: 0.1451, blue: 0.1098, opacity: 1.0000),
        text: Color(red: 0.8353, green: 0.9098, blue: 0.8549, opacity: 1.0000),
        text2: Color(red: 0.6510, green: 0.7608, blue: 0.6824, opacity: 1.0000),
        text3: Color(red: 0.4941, green: 0.6078, blue: 0.5333, opacity: 1.0000),
        tintbg: Color(red: 0.3137, green: 0.7843, blue: 0.4706, opacity: 0.1216),
        tintfg: Color(red: 0.3137, green: 0.7843, blue: 0.4706, opacity: 1.0000),
        tintline: Color(red: 0.3137, green: 0.7843, blue: 0.4706, opacity: 0.3804),
        warnbg: Color(red: 1.0000, green: 0.5412, blue: 0.0000, opacity: 0.1412),
        warnfg: Color(red: 1.0000, green: 0.5412, blue: 0.0000, opacity: 1.0000),
        warnline: Color(red: 1.0000, green: 0.5412, blue: 0.0000, opacity: 0.4196),
    )

    public enum Font {
        public static let n30: CGFloat = 30
        public static let n20: CGFloat = 20
        public static let n15: CGFloat = 15
        public static let n14: CGFloat = 14
        public static let n135: CGFloat = 13.5
        public static let n13: CGFloat = 13
        public static let n125: CGFloat = 12.5
        public static let n12: CGFloat = 12
        public static let n115: CGFloat = 11.5
        public static let n11: CGFloat = 11
        public static let n10: CGFloat = 10
    }

    public enum Radius {
        public static let r6: CGFloat = 6
        public static let r5: CGFloat = 5
        public static let r4: CGFloat = 4
        public static let r3: CGFloat = 3
        public static let r2: CGFloat = 2
    }

    public enum Space {
        public static let pageX: CGFloat = 20
        public static let block: CGFloat = 16
        public static let contentTop: CGFloat = 18
        public static let btnY: CGFloat = 13
        public static let dangerY: CGFloat = 12
        public static let ghostY: CGFloat = 10
        public static let topBtnX: CGFloat = 11
        public static let topBtnY: CGFloat = 7
        public static let tagX: CGFloat = 9
        public static let tagY: CGFloat = 4
        public static let badgeX: CGFloat = 6
        public static let badgeY: CGFloat = 2
        public static let fabSize: CGFloat = 52
        public static let fabRight: CGFloat = 20
        public static let fabBottom: CGFloat = 88
        public static let hitMin: CGFloat = 44
    }

    public enum Icon {
        public static let n26: CGFloat = 26
        public static let n24: CGFloat = 24
        public static let n21: CGFloat = 21
        public static let n18: CGFloat = 18
        public static let n15: CGFloat = 15
        public static let n14: CGFloat = 14
        public static let n13: CGFloat = 13
        public static let n12: CGFloat = 12
    }
}
