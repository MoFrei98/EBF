// swift-tools-version:5.9
import PackageDescription

// Plattformunabhängige Logik der iOS-App (nur Foundation): Buchdetails, Suche, Ordnerpfade,
// Ränder, Farben und der Neuumbruch für "Groß". Die Tests laufen auch unter Linux.
let package = Package(
    name: "EBFCore",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [
        .library(name: "EBFCore", targets: ["EBFCore"]),
    ],
    targets: [
        .target(name: "EBFCore"),
        .testTarget(name: "EBFCoreTests", dependencies: ["EBFCore"]),
    ]
)
