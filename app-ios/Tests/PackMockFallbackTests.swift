@testable import CangyuData
import Foundation
import XCTest
@testable import ChenYuBang

@MainActor
final class PackMockFallbackTests: PackDatabaseTestCase {
    func testMockFallbackKeepsTestIdentityAndCanRecoverWithRealPack() async throws {
        let mock = try importer.validate(PackFixture.mock())
        XCTAssertEqual(mock.counts, PackCounts(idioms: 3, examples: 2))
        _ = try importer.importPack(mock)
        let failing = FakeChannelClient(manifest: nil, fails: true)
        let (model, _) = try makeModel(mock, client: failing)
        XCTAssertEqual(model.presentation.status, .mock)
        await model.retry()
        XCTAssertEqual(model.presentation.status, .recoverableFailure)
        XCTAssertEqual(model.presentation.version, Phase4Copy.testVersion)
        XCTAssertEqual(model.presentation.idiomCount, 3)
        XCTAssertEqual(model.presentation.exampleCount, 2)
        model.continueOffline()
        XCTAssertEqual(model.presentation.status, .mock)
        XCTAssertEqual(model.presentation.reason, "题库未获取成功")

        let (zip, manifest) = try update()
        let (recovering, directory) = try makeModel(
            mock, client: FakeChannelClient(manifest: manifest, zip: zip)
        )
        await recovering.retry()
        XCTAssertEqual(recovering.presentation.status, .updated)
        XCTAssertEqual(recovering.presentation.version, manifest.packVersion)
        XCTAssertNotEqual(recovering.presentation.version, Phase4Copy.testVersion)
        XCTAssertEqual(try InstalledPackStorage.load(from: directory), zip)
    }

    private func makeModel(
        _ mock: ValidatedPack,
        client: PackChannelClient
    ) throws -> (model: PackVersionViewModel, directory: URL) {
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        let model = PackVersionViewModel(
            pack: mock,
            client: client,
            importer: importer,
            storageDirectory: directory,
            configuration: PackChannelConfiguration(
                latestURL: URL(string: "https://example.com/pack/latest.json")!
            ),
            usesMock: true
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
}
