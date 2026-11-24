@testable import CangyuData
import Foundation
import GRDB
import XCTest
@testable import ChenYuBang

@MainActor
final class PackUpdateTests: PackDatabaseTestCase {
    func testChannelClientRejectsHTTPBeforeStartingRequest() async {
        let client = URLPackChannelClient()
        do {
            _ = try await client.latest(from: URL(string: "http://example.com/latest.json")!)
            XCTFail("HTTP channel URL must be rejected")
        } catch let error as PackChannelError {
            XCTAssertEqual(error, .invalidResponse)
        } catch {
            XCTFail("Unexpected error: \(error)")
        }
    }

    func testChannelManifestRejectsUnsafeOrMismatchedPackFile() throws {
        let (_, manifest) = try update()
        let data = try manifestData(manifest)
        XCTAssertEqual(try ChannelManifest.read(data), manifest)
        XCTAssertEqual(
            try manifest.packURL(relativeTo: URL(string: "https://example.com/pack/latest.json")!),
            URL(string: "https://example.com/pack/pack-v1.0.1.zip")!
        )

        var object = try jsonObject(data)
        object["packFile"] = "https://other.example/pack-v1.0.1.zip"
        XCTAssertThrowsError(try ChannelManifest.read(try jsonData(object)))
        object["packFile"] = "pack-v9.9.9.zip"
        let mismatched = try ChannelManifest.read(try jsonData(object))
        XCTAssertThrowsError(try mismatched.packURL(
            relativeTo: URL(string: "https://example.com/pack/latest.json")!
        ))
    }

    func testVersionComparisonIsNumericAndDoesNotDowngrade() throws {
        XCTAssertEqual(PackVersionComparison.compare("v1.10.0", "v1.9.99"), 1)
        XCTAssertEqual(PackVersionComparison.compare("v1.0.0", "v1.0.0"), 0)
        XCTAssertEqual(PackVersionComparison.compare("v0.9.9", "v1.0.0"), -1)
        XCTAssertGreaterThan(
            PackVersionComparison.compare("v999999999999999999999.0.0", "v2.0.0"), 0
        )
    }

    func testRemoteEqualOrOlderSkipsZipDownload() async throws {
        let base = try importer.validate(PackFixture.published())
        _ = try importer.importPack(base)
        let (_, update) = try update()
        let client = FakeChannelClient(manifest: update)
        let (model, _) = try makeModel(base, client: client)
        await model.retry()
        XCTAssertEqual(model.presentation.status, .upToDate)
        XCTAssertEqual(model.presentation.version, "v1.0.0")
        let initialDownloadCalls = await client.downloadCalls
        XCTAssertEqual(initialDownloadCalls, 0)

        let older = ChannelManifest(
            schemaVersion: 1, packVersion: "v0.9.0", builtAt: update.builtAt,
            counts: update.counts, sha256: update.sha256, sizeBytes: update.sizeBytes,
            packFile: "pack-v0.9.0.zip"
        )
        let olderClient = FakeChannelClient(manifest: older)
        let (olderModel, _) = try makeModel(base, client: olderClient)
        await olderModel.retry()
        XCTAssertEqual(olderModel.presentation.status, .upToDate)
        let olderDownloadCalls = await olderClient.downloadCalls
        XCTAssertEqual(olderDownloadCalls, 0)
    }

    func testSuccessfulUpdatePersistsAndAtomicallyImports() async throws {
        let base = try importer.validate(PackFixture.published())
        _ = try importer.importPack(base)
        let (zip, manifest) = try update()
        let client = FakeChannelClient(manifest: manifest, zip: zip)
        let (model, directory) = try makeModel(base, client: client)
        await model.retry()
        XCTAssertEqual(model.presentation.status, .updated)
        XCTAssertEqual(model.presentation.version, "v1.0.1")
        XCTAssertEqual(model.installedPack.version, "v1.0.1")
        XCTAssertEqual(try InstalledPackStorage.load(from: directory), zip)
        XCTAssertEqual(try count("idiom"), manifest.counts.idioms)
        let downloadCalls = await client.downloadCalls
        XCTAssertEqual(downloadCalls, 1)
    }

    func testManifestMismatchRetainsInstalledPackAndStorage() async throws {
        let base = try importer.validate(PackFixture.published())
        _ = try importer.importPack(base)
        let (zip, manifest) = try update()
        let wrong = ChannelManifest(
            schemaVersion: 1, packVersion: manifest.packVersion, builtAt: manifest.builtAt,
            counts: manifest.counts, sha256: String(repeating: "0", count: 64),
            sizeBytes: manifest.sizeBytes, packFile: manifest.packFile
        )
        let (model, directory) = try makeModel(base, client: FakeChannelClient(manifest: wrong, zip: zip))
        await model.retry()
        XCTAssertEqual(model.presentation.status, .recoverableFailure)
        XCTAssertEqual(model.presentation.version, "v1.0.0")
        XCTAssertEqual(try InstalledPackStorage.load(from: directory), baseData())
        XCTAssertEqual(try count("idiom"), base.counts.idioms)
    }

    func testImportFailureRestoresPreviousArchiveAndDatabase() async throws {
        let base = try importer.validate(PackFixture.published())
        _ = try importer.importPack(base)
        let (zip, manifest) = try update()
        let (model, directory) = try makeModel(
            base, client: FakeChannelClient(manifest: manifest, zip: zip)
        )
        try queue.write { db in
            try db.execute(sql: """
                CREATE TRIGGER pack_update_abort BEFORE INSERT ON idiom_scope
                BEGIN SELECT RAISE(ABORT, 'pack update rollback test'); END
                """)
        }
        defer { try? queue.write { db in try db.execute(sql: "DROP TRIGGER pack_update_abort") } }
        await model.retry()
        XCTAssertEqual(model.presentation.status, .recoverableFailure)
        XCTAssertEqual(model.presentation.version, "v1.0.0")
        XCTAssertEqual(try InstalledPackStorage.load(from: directory), baseData())
        XCTAssertEqual(try count("idiom"), base.counts.idioms)
    }

    func testNetworkFailureKeepsVersionAndContinueOfflineDoesNotRetry() async throws {
        let base = try importer.validate(PackFixture.published())
        _ = try importer.importPack(base)
        let client = FakeChannelClient(manifest: nil, fails: true)
        let (model, _) = try makeModel(base, client: client)
        await model.retry()
        XCTAssertEqual(model.presentation.status, .recoverableFailure)
        XCTAssertEqual(model.presentation.version, "v1.0.0")
        let calls = await client.latestCalls
        model.continueOffline()
        XCTAssertEqual(model.presentation.status, .offline)
        XCTAssertEqual(model.presentation.version, "v1.0.0")
        let offlineLatestCalls = await client.latestCalls
        XCTAssertEqual(offlineLatestCalls, calls)
    }

    func testConcurrentEntriesShareOneInFlightLatestRequest() async throws {
        let base = try importer.validate(PackFixture.published())
        _ = try importer.importPack(base)
        let (zip, manifest) = try update()
        let client = GatedChannelClient(manifest: manifest, zip: zip)
        let (model, _) = try makeModel(base, client: client)
        let first = Task { await model.onEnterLibrary() }
        await client.waitForLatestCalls(1)
        let second = Task { await model.onEnterLibrary() }
        await Task.yield()
        await client.releaseLatest()
        await first.value
        await second.value
        let latestCalls = await client.latestCalls
        XCTAssertEqual(latestCalls, 1)
        let downloadCalls = await client.downloadCalls
        XCTAssertEqual(downloadCalls, 1)
    }

    func testLibraryPublishesLocalRowsBeforeUpdateCompletes() async throws {
        let base = try importer.validate(PackFixture.published())
        _ = try importer.importPack(base)
        let (zip, manifest) = try update()
        let client = GatedChannelClient(manifest: manifest, zip: zip)
        let (updates, _) = try makeModel(base, client: client)
        let reader = SignalingLibraryReader()
        let library = LibraryViewModel(
            scope: ExamScopeSelection(national: true, provincial: false, institution: false),
            reader: reader,
            refreshTrigger: updates
        )
        let load = Task { await library.load(library.scope) }
        await fulfillment(of: [reader.loaded], timeout: 1)
        XCTAssertFalse(library.loading)
        XCTAssertEqual(library.page.count, 1)
        XCTAssertEqual(updates.presentation.status, .checking)
        await client.releaseLatest()
        await load.value
        XCTAssertEqual(updates.presentation.status, .updated)
        XCTAssertEqual(reader.loadCalls, 2)
    }

    private func makeModel(
        _ base: ValidatedPack,
        client: PackChannelClient
    ) throws -> (model: PackVersionViewModel, directory: URL) {
        let directory = temporaryDirectory()
        try InstalledPackStorage.save(baseData(), to: directory)
        let model = PackVersionViewModel(
            pack: base,
            client: client,
            importer: importer,
            storageDirectory: directory,
            configuration: PackChannelConfiguration(
                latestURL: URL(string: "https://example.com/pack/latest.json")!
            )
        )
        return (model, directory)
    }

    private func update() throws -> (Data, ChannelManifest) {
        var fixture = try PackFixture()
        fixture.manifest["packVersion"] = "v1.0.1"
        let zip = try fixture.zip()
        let pack = try importer.validate(zip)
        return (zip, ChannelManifest(
            schemaVersion: 1,
            packVersion: pack.version,
            builtAt: pack.builtAt,
            counts: pack.counts,
            sha256: pack.sha256,
            sizeBytes: zip.count,
            packFile: "pack-\(pack.version).zip"
        ))
    }

    private func baseData() throws -> Data { try PackFixture.published() }

    private func manifestData(_ manifest: ChannelManifest) throws -> Data {
        try jsonData([
            "schemaVersion": manifest.schemaVersion,
            "packVersion": manifest.packVersion,
            "builtAt": manifest.builtAt,
            "counts": ["idioms": manifest.counts.idioms, "examples": manifest.counts.examples],
            "sha256": manifest.sha256,
            "sizeBytes": manifest.sizeBytes,
            "packFile": manifest.packFile
        ])
    }

    private func jsonObject(_ data: Data) throws -> [String: Any] {
        try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
    }

    private func jsonData(_ object: [String: Any]) throws -> Data {
        try JSONSerialization.data(withJSONObject: object)
    }

    private func temporaryDirectory() -> URL {
        FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)
    }
}

@MainActor
private final class SignalingLibraryReader: IdiomLibraryReading {
    let loaded = XCTestExpectation(description: "local library read")
    private(set) var loadCalls = 0
    private let items = [
        LibraryIdiom(id: "local", word: "步步为营", pinyin: "bu bu wei ying", initials: "bbwy", frequency: 5)
    ]

    init() {
        loaded.assertForOverFulfill = false
    }

    func load() throws -> [LibraryIdiom] {
        loadCalls += 1
        loaded.fulfill()
        return items
    }
}

private actor FakeChannelClient: PackChannelClient {
    private(set) var latestCalls = 0
    private(set) var downloadCalls = 0
    private let manifest: ChannelManifest?
    private let zip: Data?
    private let fails: Bool

    init(manifest: ChannelManifest?, zip: Data? = nil, fails: Bool = false) {
        self.manifest = manifest
        self.zip = zip
        self.fails = fails
    }

    func latest(from url: URL) async throws -> ChannelManifest {
        latestCalls += 1
        if fails { throw PackChannelError.network }
        return try XCTUnwrap(manifest)
    }

    func download(
        _ manifest: ChannelManifest,
        from latestURL: URL,
        progress: @Sendable @escaping (Double) -> Void
    ) async throws -> Data {
        downloadCalls += 1
        progress(1)
        return try XCTUnwrap(zip)
    }
}

private actor GatedChannelClient: PackChannelClient {
    private(set) var latestCalls = 0
    private(set) var downloadCalls = 0
    private let manifest: ChannelManifest
    private let zip: Data
    private var latestWaits: [CheckedContinuation<ChannelManifest, Never>] = []
    private var callWaits: [CheckedContinuation<Void, Never>] = []

    init(manifest: ChannelManifest, zip: Data) {
        self.manifest = manifest
        self.zip = zip
    }

    func latest(from url: URL) async throws -> ChannelManifest {
        latestCalls += 1
        for waiter in callWaits { waiter.resume() }
        callWaits.removeAll()
        return await withCheckedContinuation { latestWaits.append($0) }
    }

    func waitForLatestCalls(_ count: Int) async {
        if latestCalls >= count { return }
        await withCheckedContinuation { callWaits.append($0) }
    }

    func releaseLatest() {
        for waiter in latestWaits { waiter.resume(returning: manifest) }
        latestWaits.removeAll()
    }

    func download(
        _ manifest: ChannelManifest,
        from latestURL: URL,
        progress: @Sendable @escaping (Double) -> Void
    ) async throws -> Data {
        downloadCalls += 1
        progress(1)
        return zip
    }
}
