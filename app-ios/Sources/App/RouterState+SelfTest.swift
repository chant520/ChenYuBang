extension RouterState {
    @discardableResult
    func beginSelfTest() -> Bool {
        guard currentEntry.route == .profile, canStartFullScreen else { return false }
        suspendedFullScreenEntries = fullScreenEntries
        fullScreenEntries = [RouteEntry(route: .selfTest)]
        return true
    }
}
