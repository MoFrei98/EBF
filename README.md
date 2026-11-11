<p align="center">
  <img src="docs/icon.png" width="112" alt="EBF app icon">
</p>

<h1 align="center">EBF – a book-style PDF reader for Android</h1>

<p align="center">
  Read PDFs like a book: turn real-looking pages, sort your books into folders, find them again with search and filters, read at night in dark mode – fully offline.
</p>

> [!WARNING]
> **This project is vibe coded.** The app, its code, tests, issues and this README were written almost entirely by an AI assistant (Claude) from short prompts, with only light human review. It works for its author's own use, but it has not been audited, tested on many devices or hardened for production. Use it at your own risk, keep backups of your PDFs, and read the code before relying on it.

| Library with folders | Reading | 3D page turn |
| :---: | :---: | :---: |
| <img src="docs/screenshots/library.png" width="220" alt="Library with folders"> | <img src="docs/screenshots/reading.png" width="220" alt="Reading a page"> | <img src="docs/screenshots/page-turn.png" width="220" alt="3D page turn"> |
| **Dark mode** | **Settings** | |
| <img src="docs/screenshots/dark-mode.png" width="220" alt="Dark mode"> | <img src="docs/screenshots/settings.png" width="220" alt="Settings"> | |

The app's interface is in German. The screenshots use a sample book made for this project.

## Features

- **Library with real folders.** Pick one folder on your phone (for example `Documents/EBF`) and EBF mirrors it: subfolders become folders in the app, PDFs inside appear automatically, and sorting a book into a folder moves the file on the device. Folder tiles show the covers of the books inside.
- **Book details.** Every book has a title, author and publication year. EBF fills them in automatically from the PDF's metadata or the file name (e.g. `[Lastname_Firstname]_Title.pdf`), and you can edit them.
- **Search and filters.** Search all folders by title, author, year or file name (case, umlauts and accents don't matter) and filter by *unread*, *started* or *finished*.
- **Book pages.** Pages turn in 3D, with an optional paper-rustle sound. The page bends from where you grab it: grab near the top and the top corner lifts first, near the bottom and the bottom corner does, in the middle the whole edge lifts; then it straightens out as it flips. Prefer something simpler? Switch to **Slideshow**.
- **Paper look.** Each page lies on the table like a sheet of paper, with a soft shadow, shading at the spine and stacked page edges that show how far you are in the book.
- **Bigger text without losing any.** Pick a text size: **Klein** (small, the whole page), **Normal** (half of the white margins trimmed, the default) or **Groß** (large print). With **Groß** the text is reflowed like in an e-book: EBF finds the lines and words on each page and sets them again in larger, shorter lines, keeping the book's font, italics and pictures. Words split by a hyphen at the end of a line are joined again. One PDF page then spans several screen pages and the page count adapts. Nothing on the left or right is ever cut off, in any size. No pinch zoom. The app runs in portrait mode only.
- **Dark mode for app and pages.** Pages are shown as light text on dark paper. Colors keep their hue and picture pages such as covers are only dimmed, not inverted.
- **Remembers where you are.** Every book reopens on the page you left it.
- **"Open with → EBF"** from your file manager, downloads or messengers.
- **Fully offline.** See [Privacy](#privacy-and-offline-use).

## Install on your phone

You need Android 8.0 or newer.

### Easiest: APK from GitHub Releases

1. On your phone, open a browser and sign in to GitHub. The repository is private, so you need to be signed in.
2. Go to **https://github.com/MoFrei98/EBF/releases/latest**.
3. Under **Assets**, tap **`EBF-<version>.apk`** to download it.
4. Open the downloaded file, for example from the download notification or **Files → Downloads**.
5. The first time, Android asks whether your browser may install apps. Tap **Settings → Allow from this source**, go back and tap **Install**.
6. If Google Play Protect warns about an unknown app, tap **More details → Install anyway**. The warning appears because the app does not come from the Play Store.

**Without signing in on the phone:** download the APK on your computer and copy it to the phone with a USB cable (it shows up as a drive in Windows Explorer), then open it on the phone and continue with step 4.

### Alternative: straight from Android Studio

1. Enable developer options on the phone: **Settings → About phone** → tap **Build number** seven times.
2. Turn on **USB debugging** in the developer options and connect the phone via USB.
3. Open the project in Android Studio, select your phone at the top and click **Run ▶**.

### Updating

Install the new APK over the old one. Your library, reading progress and settings are kept.

## How to use it

### First start: choose a library folder

On the empty library screen tap **Bibliotheksordner wählen** (choose library folder). The system folder picker opens in `Documents`. Tap the folder icon at the top to create a folder such as `EBF`, open it, tap **Use this folder** and allow access.

Everything already in that folder shows up in the app. You can also put PDFs and subfolders there from your computer later; pull down on the library to refresh.

You can skip this and just add PDFs. Folders become available once a library folder is set, and you can change it any time in the settings.

### Library

| Action | How |
| --- | --- |
| Add a PDF | Tap **PDF hinzufügen** and pick a file. It lands in the folder you have open and the book opens right away. |
| Open a PDF from another app | Tap the PDF in your file manager, downloads or a messenger and choose **EBF**. |
| Create a folder | **⋮** at the top right → **Neuer Ordner** |
| Sort a book into a folder | **⋮** next to the book (or long-press its cover) → **In Ordner verschieben**. You can also create a new folder from there. |
| Rename or delete a folder | **⋮** next to the folder. Only empty folders can be deleted, so nothing gets lost by accident. |
| Navigate | Tap a folder to open it; the back arrow or back gesture goes up one level. |
| Edit book details | **⋮** next to the book → **Details bearbeiten**: title, author's first and last name, publication year. The dialog also shows the page count and where the file is stored. Your edits are never overwritten automatically. |
| Search | Tap the magnifier and type a title, author, year or file name. Results come from all folders and show which folder they are in. |
| Filter | Tap a chip below the toolbar: *Alle* (all), *Ungelesen* (unread), *Angefangen* (started), *Gelesen* (finished). The filter is remembered; tap *Alle* to turn it off. |
| Sort | **⋮** → *Zuletzt gelesen* (recently read) or *Titel A–Z* |
| Refresh | Pull down, or **⋮** → **Aktualisieren** |
| Remove / delete a book | **⋮** next to the book. Books outside the library folder are only removed from the app. Books inside the library folder are deleted from the device after a confirmation. |

**About moving files:** when you sort a book into a folder, EBF *moves* the PDF into the library folder. If Android doesn't allow deleting the original (for example for some files opened from other apps), EBF copies it instead and tells you.

### Reading

| Gesture | Effect |
| --- | --- |
| Swipe left / right | Next / previous page |
| Tap the right quarter of the screen | Next page |
| Tap the left quarter | Previous page |
| Tap the middle | Show or hide the toolbars |
| Slider at the bottom | Jump to any page |
| Swipe up / down | Scroll when a page is taller than the screen |

The toolbars hide automatically when you turn a page, so you read in full screen. The sun/moon button at the top switches between light and dark mode.

### Settings (gear icon)

| Setting | Options |
| --- | --- |
| Library folder | The folder on your device that EBF mirrors. Tap **Ändern** to choose another one. |
| Design | **System**, **Hell** (light) or **Dunkel** (dark). In dark mode the pages are always darkened. |
| Text size | **Klein** (small, whole page), **Normal** (half the margins trimmed, default) or **Groß** (text reflowed in much larger print, more pages). The first time you open a book in **Groß** it takes a few seconds to prepare; after that it opens right away. Text is never cut off. |
| Page-turn sound | Soft paper rustle when you turn a page. Off by default; silent when the phone is on silent or vibrate. |
| Page turning | **Buchseiten** (3D book pages) or **Slideshow** (pages simply slide) |

## Privacy and offline use

EBF works completely offline and never sends data anywhere:

- The app has **no internet permission**. Android won't let it open any network connection.
- It contains no analytics, ads, crash reporting or other third-party services. All dependencies are AndroidX libraries.
- Android's automatic cloud backup is turned off for the app, so your library list doesn't leave the device either.
- It only accesses the PDFs you pick and the library folder you choose, through Android's system file pickers. It requests no storage permission.
- Your library (titles, authors, reading progress, folders) is kept in a local SQLite database in the app's private storage. SQLite is built into Android, so there's nothing extra to install. The PDFs themselves stay as normal files in your library folder, they are not copied into the database.

This was checked against the final release APK and tested in airplane mode.

## Sounds

The page-turn sounds are synthesized by [`tools/make_page_sounds.py`](tools/make_page_sounds.py) – filtered noise, crackle and a soft thump – and belong to this project. No third-party recordings are used, so there are no license restrictions.

### Project layout

```
app/src/main/java/com/example/ebf/
├── MainActivity.kt          navigation, "Open with", folder picker
├── EbfApp.kt                application, repositories
├── data/                    SQLite database (BookDatabase), library and folder sync (LibraryRepository,
│                            LibraryTree), metadata detection (BookMetadata), settings
├── pdf/                     PdfDocument (rendering), Crop (margin detection)
└── ui/
    ├── library/             library, folder tiles, dialogs
    ├── reader/              reader, page view, paper look, 3D page curl, dark mode filter
    ├── settings/            settings dialog, theme toggle
    └── theme/               colors and theme
```
