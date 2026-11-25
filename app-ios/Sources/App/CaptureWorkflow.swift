import CangyuData
import Foundation

struct CaptureWorkflow: Equatable, Identifiable {
    let id = UUID()
    let tab: AppTab
    let originEntryID: UUID
    var imageURL: URL?
    var croppedURL: URL?
    var recognizedText: String?
    var savedUserExampleID: String?
    var savedMistakeID: String?
    var savedIdioms: [SavedIdiom] = []
}
