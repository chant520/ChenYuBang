import CangyuData

extension RouterState {
    @discardableResult
    func prepareBackupImport(_ backup: ValidatedBackup) -> Bool {
        guard currentEntry.route == .backup, overlay == nil else { return false }
        pendingBackup = backup
        backupImportError = nil
        backupImportResult = nil
        overlay = RouteEntry(route: .importMode)
        return true
    }

    @discardableResult
    func beginBackupImport() -> Bool {
        guard pendingBackup != nil, !backupImportInProgress else { return false }
        backupImportInProgress = true
        backupImportError = nil
        return true
    }

    func finishBackupImport(result: Result<String, Phase4MessageError>) {
        backupImportInProgress = false
        switch result {
        case .success(let message):
            backupImportResult = message
            backupImportError = nil
            pendingBackup = nil
            overlay = nil
            bumpUserContent()
        case .failure(let error):
            backupImportError = error.message
        }
    }

    @discardableResult
    func beginClearData() -> Bool {
        guard currentEntry.route == .profile, overlay?.route == .clear, !clearInProgress else { return false }
        clearInProgress = true
        clearError = nil
        return true
    }

    func finishClearData(result: Result<String, Phase4MessageError>) {
        clearInProgress = false
        switch result {
        case .success(let message):
            clearResult = message
            clearError = nil
            overlay = nil
            bumpUserContent()
        case .failure(let error):
            clearError = error.message
        }
    }
}
