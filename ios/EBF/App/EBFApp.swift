import SwiftUI

/// EBF – PDFs lesen wie ein Buch. Komplett offline, ohne Konto und ohne Datenübertragung.
@main
struct EBFApp: App {
    @StateObject private var settings = AppSettings()
    @StateObject private var library = Library()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(settings)
                .environmentObject(library)
                .preferredColorScheme(settings.theme.colorScheme)
                .tint(Theme.accent)
                .onOpenURL { url in
                    // "Öffnen mit EBF" / Teilen aus einer anderen App.
                    Task { await library.openExternal(url) }
                }
        }
    }
}
