@testable import CangyuData
import Foundation
import XCTest

@MainActor
final class PackChannelClientTests: XCTestCase {
    func testClientReadsManifestThroughURLProtocol() async throws {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [PackChannelURLProtocol.self]
        let client = URLPackChannelClient(configuration: configuration)
        let origin = try XCTUnwrap(URL(string: "https://example.com"))

        let manifest = try await client.latest(from: origin.appendingPathComponent("latest.json"))
        XCTAssertEqual(manifest.packVersion, "v1.0.0")
    }

    func testRedirectPolicyAllowsOnlyFiveSameOriginRedirects() throws {
        let configuration = URLSessionConfiguration.ephemeral
        let session = URLSession(configuration: configuration)
        defer { session.finishTasksAndInvalidate() }
        let origin = try XCTUnwrap(URL(string: "https://example.com"))
        let task = session.downloadTask(with: URLRequest(url: origin.appendingPathComponent("latest.json")))
        let delegate = makeDelegate()
        let response = try XCTUnwrap(HTTPURLResponse(
            url: origin, statusCode: 302, httpVersion: nil, headerFields: ["Location": "/next.json"]
        ))
        var decisions: [URLRequest?] = []

        for _ in 0..<6 {
            delegate.urlSession(
                session,
                task: task,
                willPerformHTTPRedirection: response,
                newRequest: URLRequest(url: origin.appendingPathComponent("next.json"))
            ) { decisions.append($0) }
        }

        XCTAssertEqual(decisions.dropLast().compactMap { $0 }.count, 5)
        XCTAssertNil(decisions.last ?? nil)
    }

    func testRedirectPolicyRejectsCrossOriginAndUserInfo() throws {
        let configuration = URLSessionConfiguration.ephemeral
        let session = URLSession(configuration: configuration)
        defer { session.finishTasksAndInvalidate() }
        let origin = try XCTUnwrap(URL(string: "https://example.com"))
        let task = session.downloadTask(with: URLRequest(url: origin.appendingPathComponent("latest.json")))
        let response = try XCTUnwrap(HTTPURLResponse(
            url: origin, statusCode: 302, httpVersion: nil, headerFields: ["Location": "/next.json"]
        ))
        let crossOrigin = try XCTUnwrap(URL(string: "https://other.example/latest.json"))
        let withUserInfo = try XCTUnwrap(URL(string: "https://user@example.com/latest.json"))

        for redirected in [crossOrigin, withUserInfo] {
            let delegate = makeDelegate()
            var decision: URLRequest?
            delegate.urlSession(
                session,
                task: task,
                willPerformHTTPRedirection: response,
                newRequest: URLRequest(url: redirected)
            ) { decision = $0 }
            XCTAssertNil(decision)
        }
    }

    private func makeDelegate() -> ChannelSessionDelegate {
        ChannelSessionDelegate(
            expectedBytes: nil,
            maximumBytes: 64 * 1024,
            progress: { _ in },
            completion: { _ in }
        )
    }
}

private final class PackChannelURLProtocol: URLProtocol, @unchecked Sendable {
    private static let manifestData = Data("""
        {"schemaVersion":1,"packVersion":"v1.0.0","builtAt":"2026-01-01T00:00:00Z",
         "counts":{"idioms":1,"examples":1},"sha256":"0000000000000000000000000000000000000000000000000000000000000000",
         "sizeBytes":1,"packFile":"pack-v1.0.0.zip"}
        """.utf8)

    override static func canInit(with request: URLRequest) -> Bool { true }
    override static func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        guard let url = request.url, let client else {
            self.client?.urlProtocolDidFinishLoading(self)
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
