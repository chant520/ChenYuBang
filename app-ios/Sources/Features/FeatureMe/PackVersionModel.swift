import CangyuData
import SwiftUI

enum PackVersionStatus: Equatable {
    case builtin
    case checking
    case downloading
    case upToDate
    case updated
    case offline
    case recoverableFailure
    case mock
}

struct PackVersionPresentation: Equatable {
    let status: PackVersionStatus
    let version: String
    let idiomCount: Int
    let exampleCount: Int
    var progress: Double = 0
    var reason: String?

    static func builtin(_ pack: ValidatedPack) -> PackVersionPresentation {
        PackVersionPresentation(status: .builtin, version: pack.version,
                                idiomCount: pack.counts.idioms, exampleCount: pack.counts.examples)
    }

    static func mock(_ pack: ValidatedPack) -> PackVersionPresentation {
        PackVersionPresentation(status: .mock, version: Phase4Copy.testVersion,
                                idiomCount: pack.counts.idioms, exampleCount: pack.counts.examples,
                                reason: "题库未获取成功")
    }
}

@MainActor
final class PackVersionViewModel: ObservableObject, LibraryRefreshTrigger {
    @Published private(set) var presentation: PackVersionPresentation
    private(set) var installedPack: ValidatedPack
    private var usesMock: Bool

    private let client: PackChannelClient
    private let importer: PackImporter
    private let storageDirectory: URL
    private let configuration: PackChannelConfiguration
    private var inFlight: Task<Void, Never>?

    private var displayVersion: String { usesMock ? Phase4Copy.testVersion : installedPack.version }

    var failureMessage: String? {
        presentation.status == .recoverableFailure ? presentation.reason : nil
    }

    init(
        pack: ValidatedPack,
        client: PackChannelClient,
        importer: PackImporter,
        storageDirectory: URL,
        configuration: PackChannelConfiguration = .production,
        usesMock: Bool = false
    ) {
        installedPack = pack
        self.usesMock = usesMock
        presentation = usesMock ? .mock(pack) : .builtin(pack)
        self.client = client
        self.importer = importer
        self.storageDirectory = storageDirectory
        self.configuration = configuration
    }

    nonisolated func onEnterLibrary() async {
        await check(automatic: true)
    }

    func retry() async {
        await check(automatic: false)
    }

    func continueOffline() {
        guard presentation.status == .recoverableFailure else { return }
        presentation = usesMock ? .mock(installedPack) : PackVersionPresentation(
            status: .offline,
            version: displayVersion,
            idiomCount: installedPack.counts.idioms,
            exampleCount: installedPack.counts.examples
        )
    }

    private func check(automatic: Bool) async {
        if let inFlight {
            await inFlight.value
            return
        }
        guard let latestURL = configuration.latestURL else {
            if !automatic { fail(PackChannelError.notConfigured) }
            return
        }
        presentation = PackVersionPresentation(
            status: .checking,
            version: displayVersion,
            idiomCount: installedPack.counts.idioms,
            exampleCount: installedPack.counts.examples
        )
        let task = Task { [weak self] in
            guard let self else { return }
            defer { self.inFlight = nil }
            await self.performCheck(latestURL: latestURL)
        }
        inFlight = task
        await task.value
    }

    private func performCheck(latestURL: URL) async {
        do {
            let manifest = try await client.latest(from: latestURL)
            let newer = PackVersionComparison.compare(manifest.packVersion, installedPack.version)
            guard newer > 0 else {
                if usesMock { throw PackChannelError.manifestMismatch }
                presentation = PackVersionPresentation(
                    status: .upToDate,
                    version: displayVersion,
                    idiomCount: installedPack.counts.idioms,
                    exampleCount: installedPack.counts.examples
                )
                return
            }
            presentation = PackVersionPresentation(
                status: .downloading,
                version: displayVersion,
                idiomCount: installedPack.counts.idioms,
                exampleCount: installedPack.counts.examples
            )
            let data = try await client.download(manifest, from: latestURL) { [weak self] progress in
                Task { @MainActor [weak self] in
                    guard let self, self.presentation.status == .downloading else { return }
                    self.presentation.progress = progress
                }
            }
            let pack = try importer.validate(data)
            guard data.count == manifest.sizeBytes,
                  pack.version == manifest.packVersion,
                  pack.builtAt == manifest.builtAt,
                  pack.sha256 == manifest.sha256,
                  pack.counts == manifest.counts else {
                throw PackChannelError.manifestMismatch
            }
            let previous: Data?
            do {
                previous = try InstalledPackStorage.load(from: storageDirectory)
                try InstalledPackStorage.save(data, to: storageDirectory)
            } catch { throw PackChannelError.storage }
            do {
                _ = try importer.importPack(pack)
            } catch {
                do {
                    try restore(previous)
                } catch {
                    throw PackChannelError.storage
                }
                throw error
            }
            installedPack = pack
            usesMock = false
            presentation = PackVersionPresentation(
                status: .updated,
                version: pack.version,
                idiomCount: pack.counts.idioms,
                exampleCount: pack.counts.examples,
                progress: 1
            )
        } catch {
            fail(error)
        }
    }

    private func restore(_ previous: Data?) throws {
        if let previous {
            try InstalledPackStorage.save(previous, to: storageDirectory)
        } else {
            try InstalledPackStorage.remove(from: storageDirectory)
        }
    }

    private func fail(_ error: Error) {
        let reason: String
        if let channel = error as? PackChannelError {
            reason = channel.message
        } else if let packError = error as? PackError {
            if case .database = packError {
                reason = PackChannelError.database.message
            } else {
                reason = packError == .unsupportedSchema
                    ? PackChannelError.unsupportedSchema.message : PackChannelError.manifestMismatch.message
            }
        } else {
            reason = "题库更新失败，请重试"
        }
        presentation = PackVersionPresentation(
            status: .recoverableFailure,
            version: displayVersion,
            idiomCount: installedPack.counts.idioms,
            exampleCount: installedPack.counts.examples,
            reason: reason
        )
    }
}

@MainActor
struct PackVersionRouteScreen: View {
    @ObservedObject var updates: PackVersionViewModel
    let onBack: () -> Void

    var body: some View {
        PackVersionScreen(
            presentation: updates.presentation,
            onBack: onBack,
            onRetry: { Task { await updates.retry() } },
            onContinueOffline: { updates.continueOffline() }
        )
    }
}
