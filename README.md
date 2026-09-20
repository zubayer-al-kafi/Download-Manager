# Modern JavaFX Download Manager

A modern desktop download manager built with **Java, JavaFX, Maven, and SQLite**. It supports multiple downloads, pause/resume, cancellation, download history, YouTube downloads, and persistent settings.

## ✨ Features

* 🚀 **Multi-threaded Downloads** — Supports up to 3 simultaneous downloads.
* ⏸️ **Pause / Resume / Cancel** — Manage active downloads easily.
* 🎥 **YouTube Support** — Automatically detects YouTube URLs and supports quality selection using `yt-dlp` and `FFmpeg`.
* 📋 **Clipboard Monitoring** — Detects copied download URLs.
* 🗄️ **SQLite Database** — Stores download information and history locally.
* 📜 **Download History** — Tracks completed, cancelled, and failed downloads with statistics.
* 📁 **Custom Download Location** — Choose and remember your preferred download folder.
* ⚡ **Speed Limiter** — Control download bandwidth.
* 🖥️ **Modern JavaFX UI** — FXML, Scene Builder, and custom CSS.
* 📂 **File Management** — Show files in their folder, delete downloads, and download again.

## 🛠️ Tech Stack

* **Language:** Java 26
* **GUI:** JavaFX 26, FXML, Scene Builder, CSS
* **Database:** SQLite + JDBC
* **Build Tool:** Maven
* **Networking:** Java `HttpClient`
* **Concurrency:** `ExecutorService`
* **Media Processing:** yt-dlp + FFmpeg

## 📦 Prerequisites

* Java JDK 26+
* Maven
* For YouTube downloads:

  * `yt-dlp.exe`
  * `ffmpeg.exe`
  * `ffprobe.exe`

Place the media tools inside:

```text
tools/
├── yt-dlp.exe
├── ffmpeg.exe
└── ffprobe.exe
```

## 🚀 Installation

### Clone the Repository

```bash
git clone https://github.com/yourusername/download-manager.git
cd download-manager
```

### Build

```bash
mvn clean package
```

### Run

```bash
mvn javafx:run
```

> Using `mvn javafx:run` is recommended for JavaFX runtime configuration.

## 📁 Project Structure

```text
Download Manager/
├── src/main/java/com.downloadmanager/
│   ├── Main.java
│   ├── DownloadManager.java
│   ├── controller/
│   │   ├── MainController.java
│   │   ├── DownloadItemController.java
│   │   ├── HistoryController.java
│   │   └── HistoryItemController.java
│   └── database/
│       ├── Database.java
│       └── DownloadDAO.java
│
├── src/main/resources/com.downloadmanager/
│   ├── main.fxml
│   ├── DownloadItem.fxml
│   ├── History.fxml
│   ├── HistoryItem.fxml
│   └── style.css
│
├── tools/
│   ├── yt-dlp.exe
│   ├── ffmpeg.exe
│   └── ffprobe.exe
│
├── pom.xml
└── README.md
```

## 🧠 Architecture

* **MainController** — Main dashboard, URL handling, download location, clipboard monitoring, and settings.
* **DownloadManager** — Manages concurrent download tasks using `ExecutorService`.
* **DownloadItemController** — Handles individual downloads, progress, pause/resume, cancellation, and YouTube processing.
* **Database** — Initializes and manages the SQLite database connection.
* **DownloadDAO** — Handles database operations and download statistics.
* **HistoryController** — Displays and manages download history.

## 🔄 Download Flow

```text
URL
 │
 ├── YouTube ──→ Quality Selection ──→ yt-dlp ──→ FFmpeg
 │
 └── Normal URL ──→ HTTP Download
                         │
                         ▼
                  Background Thread
                         │
                         ▼
                 Progress / Status
                         │
                         ▼
                    SQLite History
```

## 🗄️ Database

The application automatically creates:

```text
download_manager.db
```

The database stores:

* File name
* URL
* File path
* Status
* Creation time
