import Foundation

public protocol PackChannelClient: Sendable {
    func latest(from url: URL) async throws -> ChannelManifest
    func download(
        _ manifest: ChannelManifest,
        from latestURL: URL,
        progress: @Sendable @escaping (Double) -> Void
    ) async throws -> Data
}

public final class URLPackChannelClient: PackChannelClient, @unchecked Sendable {
    private let configuration: URLSessionConfiguration

    public init(configuration: URLSessionConfiguration = .ephemeral) {
        let configured = configuration.copy() as! URLSessionConfiguration
        configured.timeoutIntervalForRequest = 10
        configured.timeoutIntervalForResource = 30
        configured.requestCachePolicy = .reloadIgnoringLocalCacheData
        configured.httpCookieStorage = nil
        configured.urlCache = nil
        configured.httpShouldSetCookies = false
        self.configuration = configured
    }

    public func latest(from url: URL) async throws -> ChannelManifest {
        let request = try request(url, accept: "application/json")
        let data = try await downloaded(
            for: request,
            maximum: 64 * 1024,
            expectedBytes: nil,
            progress: { _ in }
        )
        return try ChannelManifest.read(data)
    }

    public func download(
        _ manifest: ChannelManifest,
        from latestURL: URL,
        progress: @Sendable @escaping (Double) -> Void
    ) async throws -> Data {
        let url = try manifest.packURL(relativeTo: latestURL)
        let request = try request(url, accept: "application/zip")
        let data = try await downloaded(
            for: request,
            maximum: PackArchive.maximumInput,
            expectedBytes: manifest.sizeBytes,
            progress: progress
        )
        guard data.count == manifest.sizeBytes else { throw PackChannelError.sizeMismatch }
        return data
    }

    private func request(_ url: URL, accept: String) throws -> URLRequest {
        guard url.scheme?.lowercased() == "https", url.host?.isEmpty == false,
              url.user == nil, url.password == nil else {
            throw PackChannelError.invalidResponse
        }
        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.setValue(accept, forHTTPHeaderField: "Accept")
        request.cachePolicy = .reloadIgnoringLocalCacheData
        request.timeoutInterval = 10
        return request
    }

    private func downloaded(
        for request: URLRequest,
        maximum: Int,
        expectedBytes: Int?,
        progress: @Sendable @escaping (Double) -> Void
    ) async throws -> Data {
        let delegate = ChannelSessionDelegate(
            expectedBytes: expectedBytes,
            maximumBytes: Int64(maximum),
            progress: progress
        )
        let session = URLSession(configuration: configuration, delegate: delegate, delegateQueue: nil)
        defer { session.invalidateAndCancel() }
        return try await withCheckedThrowingContinuation { continuation in
            let task = session.downloadTask(with: request) { location, response, error in
                if delegate.exceededMaximum {
                    continuation.resume(throwing: PackChannelError.sizeMismatch)
                    return
                }
                if error != nil {
                    continuation.resume(throwing: PackChannelError.network)
                    return
                }
                guard let location,
                      let response = response as? HTTPURLResponse,
                      (200..<300).contains(response.statusCode) else {
                    continuation.resume(throwing: PackChannelError.invalidResponse)
                    return
                }
                if let expectedBytes,
                   response.expectedContentLength >= 0,
                   response.expectedContentLength != Int64(expectedBytes) {
                    continuation.resume(throwing: PackChannelError.sizeMismatch)
                    return
                }
                guard let data = try? Data(contentsOf: location), data.count <= maximum else {
                    continuation.resume(throwing: PackChannelError.sizeMismatch)
                    return
                }
                if let expectedBytes, data.count != expectedBytes {
                    continuation.resume(throwing: PackChannelError.sizeMismatch)
                    return
                }
                progress(1)
                continuation.resume(returning: data)
            }
            task.resume()
        }
    }
}

private final class ChannelSessionDelegate: NSObject, URLSessionTaskDelegate, @unchecked Sendable {
    private let expectedBytes: Int?
    private let maximumBytes: Int64
    private let progress: @Sendable (Double) -> Void
    private let lock = NSLock()
    private var exceeded = false

    init(
        expectedBytes: Int?,
        maximumBytes: Int64,
        progress: @Sendable @escaping (Double) -> Void
    ) {
        self.expectedBytes = expectedBytes
        self.maximumBytes = maximumBytes
        self.progress = progress
    }

    var exceededMaximum: Bool {
        lock.lock()
        defer { lock.unlock() }
        return exceeded
    }

    func urlSession(
        _ session: URLSession,
        downloadTask: URLSessionDownloadTask,
        didWriteData bytesWritten: Int64,
        totalBytesWritten: Int64,
        totalBytesExpectedToWrite: Int64
    ) {
        lock.lock()
        if totalBytesWritten > maximumBytes { exceeded = true }
        let didExceed = exceeded
        lock.unlock()
        if didExceed {
            progress(1)
            downloadTask.cancel()
            return
        }
        let denominator = expectedBytes.map { Int64($0) } ?? totalBytesExpectedToWrite
        if denominator > 0 {
            progress(min(1, Double(totalBytesWritten) / Double(denominator)))
        }
    }

    func urlSession(
        _ session: URLSession,
        task: URLSessionTask,
        willPerformHTTPRedirection response: HTTPURLResponse,
        newRequest request: URLRequest,
        completionHandler: @escaping (URLRequest?) -> Void
    ) {
        guard let original = task.originalRequest?.url, let redirected = request.url,
              isSameOrigin(original, redirected) else {
            completionHandler(nil)
            return
        }
        completionHandler(request)
    }

    private func isSameOrigin(_ left: URL, _ right: URL) -> Bool {
        left.scheme?.lowercased() == right.scheme?.lowercased()
            && left.host?.lowercased() == right.host?.lowercased()
            && port(left) == port(right)
    }

    private func port(_ url: URL) -> Int {
        url.port ?? (url.scheme?.lowercased() == "https" ? 443 : 0)
    }
}
