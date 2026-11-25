@testable import CangyuData
import Foundation
import XCTest

@MainActor
final class PackChannelClientTests: XCTestCase {
    func testSameOriginRedirectSucceedsButCrossOriginAndLoopsFail() async throws {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [PackChannelURLProtocol.self]
        let client = URLPackChannelClient(configuration: configuration)
        let origin = try XCTUnwrap(URL(string: "https://example.com"))

        PackChannelURLProtocol.reset()
        let manifest = try await client.latest(from: origin.appendingPathComponent("same-origin"))
        XCTAssertEqual(manifest.packVersion, "v1.0.0")

        PackChannelURLProtocol.reset()
        do {
            _ = try await client.latest(from: origin.appendingPathComponent("cross-origin"))
            XCTFail("Cross-origin redirect must be rejected")
        } catch {
            XCTAssertEqual(error as? PackChannelError, .network)
        }

        PackChannelURLProtocol.reset()
        do {
            _ = try await client.latest(from: origin.appendingPathComponent("loop"))
            XCTFail("Redirect loop must be rejected")
        } catch {
            XCTAssertEqual(error as? PackChannelError, .network)
        }
        XCTAssertEqual(PackChannelURLProtocol.loopStarts, 6)
    }
}

private final class PackChannelURLProtocol: URLProtocol, @unchecked Sendable {
    private static let lock = NSLock()
    nonisolated(unsafe) private static var storedLoopStarts = 0
    private static let manifestData = Data("""
        {"schemaVersion":1,"packVersion":"v1.0.0","builtAt":"2026-01-01T00:00:00Z",
         "counts":{"idioms":1,"examples":1},"sha256":"0000000000000000000000000000000000000000000000000000000000000000",
         "sizeBytes":1,"packFile":"pack-v1.0.0.zip"}
        """.utf8)

    static var loopStarts: Int {
        lock.lock()
        defer { lock.unlock() }
        return storedLoopStarts
    }

    static func reset() {
        lock.lock()
        storedLoopStarts = 0
        lock.unlock()
    }

    override static func canInit(with request: URLRequest) -> Bool { true }
    override static func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        guard let url = request.url, let client else {
            self.client?.urlProtocolDidFinishLoading(self)
            return
        }
        if url.path == "/loop" {
            Self.lock.lock()
            Self.storedLoopStarts += 1
            Self.lock.unlock()
        }
        let location: String?
        switch url.path {
        case "/same-origin": location = "/latest.json"
        case "/cross-origin": location = "https://other.example/latest.json"
        case "/loop": location = "/loop"
        default: location = nil
        }
        if let location {
            guard let response = HTTPURLResponse(
                url: url, statusCode: 302, httpVersion: nil, headerFields: ["Location": location]
            ), let redirected = URL(string: location, relativeTo: url) else {
                client.urlProtocol(self, didFailWithError: PackChannelError.invalidResponse)
                return
            }
            client.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
            client.urlProtocol(
                self,
                wasRedirectedTo: URLRequest(url: redirected),
                redirectResponse: response
            )
            return
        }
        guard url.path == "/latest.json", request.httpMethod == "GET",
              request.value(forHTTPHeaderField: "Cookie") == nil,
              request.value(forHTTPHeaderField: "Authorization") == nil,
              let response = HTTPURLResponse(
                url: url, statusCode: 200, httpVersion: nil,
                headerFields: ["Content-Type": "application/json", "Content-Length": "\(Self.manifestData.count)"]
              ) else {
            client.urlProtocol(self, didFailWithError: PackChannelError.invalidResponse)
            return
        }
        client.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client.urlProtocol(self, didLoad: Self.manifestData)
        client.urlProtocolDidFinishLoading(self)
    }

    override func stopLoading() {}
}
