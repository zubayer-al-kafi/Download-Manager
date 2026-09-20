package com.downloadmanager.controller;

import com.downloadmanager.database.DownloadDAO;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.VBox;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Optional;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import java.awt.Desktop;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;

public class DownloadItemController {

    @FXML
    private Button deleteButton;
    @FXML
    private VBox downloadItem;

    @FXML
    private Label fileNameLabel;

    @FXML
    private Label urlLabel;

    @FXML
    private Label percentageLabel;

    @FXML
    private ProgressBar progressBar;

    @FXML
    private Button pauseButton;

    @FXML
    private Button cancelButton;

    @FXML
    private Button showFolderButton;
    @FXML
    private Button downloadAgainButton;
    @FXML
    private Label speedLabel;
    @FXML
    private Label timeLabel;
    private Runnable downloadAgainAction;
    private long downloadStartTime;
    private int databaseId;
    public void setDatabaseId(int databaseId) {
        this.databaseId = databaseId;
    }
    public long speedLimitBytes = 0;
    public String speedChoice = "Unlimited";
    private volatile boolean paused = false;
    private volatile boolean cancelled = false;
    private long lastTime;
    private long lastBytes;
    private boolean isRestored = false;
    private Runnable resumeAction;
    public String selectedQuality = "Best Quality (Default)";
    public void setResumeAction(Runnable action) {
        this.resumeAction = action;
    }

    public void setSelectedQuality(String selectedQuality) {
        this.selectedQuality = selectedQuality;
    }
    private Path filePath;
    public void setDownloadAgainAction(
            Runnable action) {

        this.downloadAgainAction = action;
    }
    @FXML
    private void downloadAgain() {
        cancelled = false;
        paused = false;
        isRestored = false; // Reset flag so it knows a new thread is starting

        progressBar.setProgress(0);
        percentageLabel.setText("Downloading");

        speedLabel.setText("Speed: 0 KB/s");
        timeLabel.setText("Time remaining: --");

        pauseButton.setDisable(false);
        pauseButton.setText("Pause"); // Reset the button text

        cancelButton.setDisable(false);
        deleteButton.setDisable(true);
        showFolderButton.setDisable(true);
        downloadAgainButton.setDisable(true);

        DownloadDAO.updateStatus(
                databaseId,
                "Downloading"
        );

        if (downloadAgainAction != null) {
            downloadAgainAction.run();
        }
    }

    @FXML
    private void showDeleteMenu() {

        ContextMenu menu = new ContextMenu();

        MenuItem deleteFromFiles =
                new MenuItem("Delete from files");

        MenuItem removeFromList =
                new MenuItem("Remove from list");


        deleteFromFiles.setOnAction(event ->
                deleteFromFiles()
        );

        removeFromList.setOnAction(event ->
                removeFromList()
        );


        menu.getItems().addAll(
                deleteFromFiles,
                removeFromList
        );


        menu.show(
                deleteButton,
                javafx.geometry.Side.BOTTOM,
                0,
                0
        );
    }

    @FXML
    private void initialize() {

        // Detect double click on download item
        downloadItem.setOnMouseClicked(event -> {

            if (event.getButton() == MouseButton.PRIMARY
                    && event.getClickCount() == 2) {

                openFile();
            }
        });
    }
    private Process youtubeProcess;
    private Runnable removeFromListAction;
    public void setRemoveFromListAction(
            Runnable action) {

        this.removeFromListAction = action;
    }
    private void removeFromList() {

        if (removeFromListAction != null) {

            removeFromListAction.run();
        }
    }
    public void setDownloadInfo(String fileName, String url) {

        fileNameLabel.setText(fileName);
        urlLabel.setText(url);
    }

    private void deleteFromFiles() {

        // We only check if filePath is null, because the main file might not
        // exist yet, but the .part files might!
        if (filePath == null) {
            return;
        }

        try {
            // 1. Delete the main merged file if it exists
            Files.deleteIfExists(filePath);

            // 2. Delete any multi-part chunk files
            for (int i = 0; i < 4; i++) {
                Files.deleteIfExists(Path.of(filePath.toString() + ".part" + i));
            }

            Platform.runLater(() -> {
                percentageLabel.setText("Deleted");

                // File no longer exists, disable folder button
                showFolderButton.setDisable(true);

                // Keep Delete button enabled so "Remove from list" is still available
                deleteButton.setDisable(false);

                // Allow downloading again from scratch
                downloadAgainButton.setDisable(false);
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private String formatSpeed(double bytesPerSecond) {

        if (bytesPerSecond < 1024) {
            return String.format(
                    "%.0f B/s",
                    bytesPerSecond
            );
        }

        if (bytesPerSecond < 1024 * 1024) {
            return String.format(
                    "%.1f KB/s",
                    bytesPerSecond / 1024
            );
        }

        return String.format(
                "%.1f MB/s",
                bytesPerSecond / (1024 * 1024)
        );
    }
    private String formatTime(double seconds) {

        long totalSeconds =
                (long) seconds;

        long hours =
                totalSeconds / 3600;

        long minutes =
                (totalSeconds % 3600) / 60;

        long secs =
                totalSeconds % 60;

        if (hours > 0) {

            return String.format(
                    "%dh %dm",
                    hours,
                    minutes
            );
        }

        if (minutes > 0) {

            return String.format(
                    "%dm %ds",
                    minutes,
                    secs
            );
        }

        return String.format(
                "%ds",
                secs
        );
    }
    public void download(String fileName, String url, Path downloadFolder) {
        // =========================================================
        // YOUTUBE DOWNLOAD
        // =========================================================

        boolean isYouTube =
                url.contains("youtube.com/")
                        || url.contains("youtu.be/");

        if (isYouTube) {
            try {
                Files.createDirectories(downloadFolder);
                String ytDlpPath = "tools/yt-dlp.exe";
                String outputTemplate = downloadFolder.resolve("%(title)s.%(ext)s").toString();

                // 1. Build the dynamic command based on user quality selection
                List<String> command = new java.util.ArrayList<>();
                command.add(ytDlpPath);
                command.add("--no-playlist");
                command.add("--newline");
                command.add("--no-overwrites");
                command.add("-o");
                command.add(outputTemplate);

                if (selectedQuality != null && selectedQuality.equals("Audio Only (MP3)")) {
                    command.add("-f");
                    command.add("bestaudio");
                    command.add("--extract-audio");
                    command.add("--audio-format");
                    command.add("mp3");
                } else if (selectedQuality != null && selectedQuality.equals("1080p")) {
                    command.add("-f");
                    command.add("bestvideo[height<=1080][ext=mp4]+bestaudio[ext=m4a]/best");
                    command.add("--merge-output-format");
                    command.add("mp4");
                } else if (selectedQuality != null && selectedQuality.equals("720p")) {
                    command.add("-f");
                    command.add("bestvideo[height<=720][ext=mp4]+bestaudio[ext=m4a]/best");
                    command.add("--merge-output-format");
                    command.add("mp4");
                } else if (selectedQuality != null && selectedQuality.equals("480p")) {
                    command.add("-f");
                    command.add("bestvideo[height<=480][ext=mp4]+bestaudio[ext=m4a]/best");
                    command.add("--merge-output-format");
                    command.add("mp4");
                } else {
                    // Default Best Quality
                    command.add("-f");
                    command.add("bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best");
                    command.add("--merge-output-format");
                    command.add("mp4");
                }

                command.add(url);

                // 2. Start yt-dlp
                ProcessBuilder builder = new ProcessBuilder(command);
                builder.redirectErrorStream(true);
                Process process = builder.start();
                youtubeProcess = process;

                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                int downloadPhase = 1;

                while ((line = reader.readLine()) != null) {
                    System.out.println("yt-dlp: " + line);

                    // =========================================
                    // CANCEL
                    // =========================================
                    if (cancelled) {
                        process.destroyForcibly();
                        Platform.runLater(() -> {
                            percentageLabel.setText("Cancelled");
                            pauseButton.setDisable(true);
                            cancelButton.setDisable(true);
                            downloadAgainButton.setDisable(false);
                        });
                        DownloadDAO.updateStatus(databaseId, "Cancelled");
                        return;
                    }

                    // =========================================
                    // DETECT MERGING & AUDIO EXTRACTION PHASE
                    // =========================================
                    if (line.contains("[Merger]") || line.contains("Merging formats")) {
                        Platform.runLater(() -> {
                            speedLabel.setText("Merging Video & Audio...");
                            timeLabel.setText("Please wait...");
                            progressBar.setProgress(-1.0);
                            percentageLabel.setText("Processing");
                        });

                        // Try to grab final output path from terminal output
                        if (line.contains("into")) {
                            try {
                                String target = line.substring(line.indexOf("into") + 4).trim().replace("\"", "");
                                this.filePath = Paths.get(target);
                            } catch (Exception ignored) {}
                        }
                        continue;
                    }
                    else if (line.contains("[ExtractAudio]")) {
                        Platform.runLater(() -> {
                            speedLabel.setText("Converting to MP3...");
                            timeLabel.setText("Please wait...");
                            progressBar.setProgress(-1.0);
                            percentageLabel.setText("Processing");
                        });

                        // Try to grab final MP3 destination
                        if (line.contains("Destination:")) {
                            try {
                                String target = line.substring(line.indexOf("Destination:") + 12).trim().replace("\"", "");
                                this.filePath = Paths.get(target);
                            } catch (Exception ignored) {}
                        }
                        continue;
                    }
                    else if (line.contains("[download] Destination:") && (line.endsWith(".mp4") || line.endsWith(".mkv") || line.endsWith(".webm") || line.endsWith(".m4a"))) {
                        // Try to grab single un-merged file destination
                        try {
                            String target = line.substring(line.indexOf("Destination:") + 12).trim().replace("\"", "");
                            this.filePath = Paths.get(target);
                        } catch (Exception ignored) {}
                    }

                    // =========================================
                    // PROGRESS
                    // =========================================
                    if (line.contains("[download]") && line.contains("%")) {

                        if (line.contains("100%") || line.contains("100.0%")) {
                            downloadPhase = 2;
                        }
                        final int currentPhase = downloadPhase;

                        Pattern progressPattern = Pattern.compile("\\[download\\]\\s+(\\d+(?:\\.\\d+)?)%");
                        Matcher progressMatcher = progressPattern.matcher(line);

                        if (progressMatcher.find()) {
                            double percentage = Double.parseDouble(progressMatcher.group(1));
                            double progress = percentage / 100.0;

                            Platform.runLater(() -> {
                                if (!percentageLabel.getText().equals("Processing")) {
                                    progressBar.setProgress(progress);

                                    // Adjust UI text based on if we are doing video or audio-only
                                    String prefix = (currentPhase == 1) ? "Video " : "Audio ";
                                    if (selectedQuality != null && selectedQuality.equals("Audio Only (MP3)")) {
                                        prefix = "Audio ";
                                    }

                                    percentageLabel.setText(prefix + String.format("%.0f%%", percentage));
                                }
                            });
                        }

                        // =====================================
                        // SPEED
                        // =====================================
                        Pattern speedPattern = Pattern.compile("at\\s+([^\\s]+/s)");
                        Matcher speedMatcher = speedPattern.matcher(line);
                        if (speedMatcher.find()) {
                            String speed = speedMatcher.group(1);
                            Platform.runLater(() -> {
                                if (!speedLabel.getText().contains("Merging") && !speedLabel.getText().contains("Converting")) {
                                    speedLabel.setText("Speed: " + speed);
                                }
                            });
                        }

                        // =====================================
                        // ETA
                        // =====================================
                        Pattern etaPattern = Pattern.compile("ETA\\s+([0-9:]+)");
                        Matcher etaMatcher = etaPattern.matcher(line);
                        if (etaMatcher.find()) {
                            String eta = etaMatcher.group(1);
                            Platform.runLater(() -> {
                                if (!timeLabel.getText().contains("wait")) {
                                    timeLabel.setText("Time remaining: " + eta);
                                }
                            });
                        }
                    }
                }

                // Wait until yt-dlp has completely finished
                int exitCode = process.waitFor();
                youtubeProcess = null;
                System.out.println("yt-dlp exit code: " + exitCode);

                // =========================================
                // CANCELLED (Double check after wait)
                // =========================================
                if (cancelled) {
                    Platform.runLater(() -> {
                        percentageLabel.setText("Cancelled");
                        pauseButton.setDisable(true);
                        cancelButton.setDisable(true);
                        downloadAgainButton.setDisable(false);
                    });
                    DownloadDAO.updateStatus(databaseId, "Cancelled");
                    return;
                }

                // =========================================
                // SUCCESS
                // =========================================
                if (exitCode == 0) {
                    // Fallback: If yt-dlp didn't report the path cleanly, grab the newest valid file created in the folder
                    if (filePath == null || !Files.exists(filePath)) {
                        try (var stream = Files.list(downloadFolder)) {
                            filePath = stream
                                    .filter(p -> !Files.isDirectory(p))
                                    .filter(p -> {
                                        String name = p.getFileName().toString().toLowerCase();
                                        return name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".webm") || name.endsWith(".mp3") || name.endsWith(".m4a");
                                    })
                                    .max(java.util.Comparator.comparingLong(p -> p.toFile().lastModified()))
                                    .orElse(filePath);
                        } catch (Exception ignored) {}
                    }

                    DownloadDAO.updateStatus(databaseId, "Completed");

                    Platform.runLater(() -> {
                        progressBar.setProgress(1.0);
                        percentageLabel.setText("Completed");
                        speedLabel.setText("Speed: Completed");
                        timeLabel.setText("Time remaining: 0s");

                        // Update the card title to the actual dynamically generated video/audio file name
                        if (filePath != null && Files.exists(filePath)) {
                            fileNameLabel.setText(filePath.getFileName().toString());
                        }

                        pauseButton.setDisable(true);
                        cancelButton.setDisable(true);
                        showFolderButton.setDisable(false);
                        deleteButton.setDisable(false);

                        // Disable the re-download button on completion
                        downloadAgainButton.setDisable(true);

                        // OS Desktop Notification
                        com.downloadmanager.Main.showNotification(
                                "Download Complete",
                                (filePath != null ? filePath.getFileName().toString() : "YouTube File") + " has finished downloading.",
                                java.awt.TrayIcon.MessageType.INFO
                        );
                    });

                    return;
                }

                // =========================================
                // FAILED
                // =========================================
                DownloadDAO.updateStatus(databaseId, "Failed");
                Platform.runLater(() -> {
                    percentageLabel.setText("Failed");
                    pauseButton.setDisable(true);
                    cancelButton.setDisable(true);
                    downloadAgainButton.setDisable(false);

                    com.downloadmanager.Main.showNotification(
                            "Download Failed",
                            "YouTube Download encountered an error.",
                            java.awt.TrayIcon.MessageType.ERROR
                    );
                });
                return;

            } catch (Exception e) {
                e.printStackTrace();
                DownloadDAO.updateStatus(databaseId, "Failed");
                Platform.runLater(() -> {
                    percentageLabel.setText("Failed");
                    pauseButton.setDisable(true);
                    cancelButton.setDisable(true);
                    downloadAgainButton.setDisable(false);

                    com.downloadmanager.Main.showNotification(
                            "Download Failed",
                            "YouTube Download encountered an error.",
                            java.awt.TrayIcon.MessageType.ERROR
                    );
                });
                return;
            }
        }

        try {
            Files.createDirectories(downloadFolder);
            filePath = downloadFolder.resolve(fileName);

            HttpClient client = HttpClient.newHttpClient();

            // 1. Get total file size to determine chunk sizes
            HttpRequest headRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<Void> headResponse = client.send(headRequest, HttpResponse.BodyHandlers.discarding());
            long totalSize = headResponse.headers().firstValueAsLong("Content-Length").orElse(-1L);

            // If the server doesn't provide a file size, we must fall back to 1 standard connection
            int connections = (totalSize > 1024 * 1024) ? 4 : 1;
            long chunkSize = totalSize > 0 ? (totalSize / connections) : -1;

            ExecutorService chunkExecutor = java.util.concurrent.Executors.newFixedThreadPool(connections);
            List<CompletableFuture<Void>> tasks = new ArrayList<>();
            AtomicLong totalDownloaded = new AtomicLong(0);

            long downloadStartTime = System.currentTimeMillis();

            // 2. Start Download Threads
            for (int i = 0; i < connections; i++) {
                final int chunkId = i;
                long startByte = (chunkSize > 0) ? (i * chunkSize) : 0;
                long endByte = (chunkSize > 0 && i == connections - 1) ? totalSize - 1
                        : (chunkSize > 0) ? (startByte + chunkSize - 1) : -1;

                // Create part files (e.g., file.zip.part0, file.zip.part1)
                Path partPath = (connections > 1)
                        ? downloadFolder.resolve(fileName + ".part" + chunkId)
                        : filePath;

                long existingSize = Files.exists(partPath) ? Files.size(partPath) : 0;
                totalDownloaded.addAndGet(existingSize);

                long newStartByte = startByte + existingSize;

                // Skip starting this thread if the chunk is already fully downloaded
                if (chunkSize > 0 && newStartByte > endByte) {
                    continue;
                }

                CompletableFuture<Void> task = CompletableFuture.runAsync(() -> {
                    try {
                        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder().uri(URI.create(url)).GET();

                        if (totalSize > 0) {
                            reqBuilder.header("Range", "bytes=" + newStartByte + "-" + (endByte > 0 ? endByte : ""));
                        }

                        HttpResponse<InputStream> response = client.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofInputStream());

                        boolean append = (response.statusCode() == 206) && (existingSize > 0);
                        if (response.statusCode() == 200 && existingSize > 0) {
                            // Server rejected the range request, restart chunk
                            totalDownloaded.addAndGet(-existingSize);
                            append = false;
                        }

                        try (InputStream input = response.body();
                             java.io.OutputStream output = Files.newOutputStream(partPath,
                                     append ? java.nio.file.StandardOpenOption.APPEND : java.nio.file.StandardOpenOption.CREATE)) {

                            byte[] buffer = new byte[8192];
                            int bytesRead;

                            while ((bytesRead = input.read(buffer)) != -1) {
                                synchronized (DownloadItemController.this) {
                                    while (paused && !cancelled) {
                                        DownloadItemController.this.wait();
                                    }
                                }
                                if (cancelled) break;

                                output.write(buffer, 0, bytesRead);
                                totalDownloaded.addAndGet(bytesRead);
                            }
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }, chunkExecutor);

                tasks.add(task);
            }

            // 3. Monitor Progress from the main thread
            long lastUpdateBytes = totalDownloaded.get();
            long lastUpdateTime = downloadStartTime;

            while (!tasks.stream().allMatch(CompletableFuture::isDone) && !cancelled) {
                Thread.sleep(500);

                long currentBytes = totalDownloaded.get();
                long currentTime = System.currentTimeMillis();
                long elapsed = currentTime - lastUpdateTime;

                if (elapsed >= 500 && totalSize > 0) {
                    long bytesSinceUpdate = currentBytes - lastUpdateBytes;
                    double speed = bytesSinceUpdate / (elapsed / 1000.0);
                    double progress = (double) currentBytes / totalSize;
                    double remainingSeconds = speed > 0 ? (totalSize - currentBytes) / speed : 0;

                    lastUpdateTime = currentTime;
                    lastUpdateBytes = currentBytes;

                    Platform.runLater(() -> {
                        progressBar.setProgress(progress);
                        percentageLabel.setText(String.format("%.0f%%", progress * 100));
                        speedLabel.setText("Speed: " + formatSpeed(speed));
                        timeLabel.setText("Time remaining: " + formatTime(remainingSeconds));
                    });
                }
            }

            // Await all threads to safely finish and clean up pool
            CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0])).join();
            chunkExecutor.shutdown();

            // 4. Handle Cancellation Cleanup
            if (cancelled) {
                for (int i = 0; i < connections; i++) {
                    Files.deleteIfExists(downloadFolder.resolve(fileName + ".part" + i));
                }
                Files.deleteIfExists(filePath);

                Platform.runLater(() -> {
                    percentageLabel.setText("Cancelled");
                    pauseButton.setDisable(true);
                    cancelButton.setDisable(true);
                    deleteButton.setDisable(false);
                    downloadAgainButton.setDisable(false);
                    showFolderButton.setDisable(true);
                    DownloadDAO.updateStatus(databaseId, "Cancelled");
                });
                return;
            }

            // 5. Merge Chunks into the Final File
            if (connections > 1) {
                Platform.runLater(() -> {
                    percentageLabel.setText("Merging...");
                    speedLabel.setText("Merging parts...");
                    timeLabel.setText("Time remaining: 0s");
                });

                try (java.io.OutputStream out = Files.newOutputStream(filePath, java.nio.file.StandardOpenOption.CREATE)) {
                    for (int i = 0; i < connections; i++) {
                        Path partPath = downloadFolder.resolve(fileName + ".part" + i);
                        if (Files.exists(partPath)) {
                            Files.copy(partPath, out);
                            Files.deleteIfExists(partPath); // Clean up temp file
                        }
                    }
                }
            }

            // 6. Completion
            long totalTime = System.currentTimeMillis() - downloadStartTime;
            double averageSpeed = totalTime > 0 ? totalDownloaded.get() / (totalTime / 1000.0) : 0;
            // Add this line:
            com.downloadmanager.Main.showNotification(
                    "Download Complete",
                    fileName + " has finished downloading.",
                    java.awt.TrayIcon.MessageType.INFO
            );
            Platform.runLater(() -> {
                progressBar.setProgress(1.0);
                percentageLabel.setText("Completed");
                speedLabel.setText("Speed: " + formatSpeed(averageSpeed));
                DownloadDAO.updateStatus(databaseId, "Completed");
                pauseButton.setDisable(true);
                cancelButton.setDisable(true);
                showFolderButton.setDisable(false);
                deleteButton.setDisable(false);


            });
        } catch (Exception e) {
            e.printStackTrace();
            DownloadDAO.updateStatus(databaseId, "Failed");
            Platform.runLater(() -> {
                percentageLabel.setText("Failed");
                pauseButton.setDisable(true);
                cancelButton.setDisable(true);
                downloadAgainButton.setDisable(false);

                // Add this line:
                com.downloadmanager.Main.showNotification(
                        "Download Failed",
                        fileName + " encountered an error.",
                        java.awt.TrayIcon.MessageType.ERROR
                );
            });
        }
    }
    // ==========================================
    // OPEN FILE
    // ==========================================

    private void openFile() {

        // Don't do anything if download isn't complete
        if (filePath == null ||
                !Files.exists(filePath)) {

            return;
        }


        try {

            Desktop.getDesktop().open(
                    filePath.toFile()
            );

        } catch (Exception e) {

            e.printStackTrace();
        }
    }


    // ==========================================
    // SHOW FILE IN FOLDER
    // ==========================================

    @FXML
    private void showInFolder() {
        if (filePath == null || !Files.exists(filePath)) {
            return;
        }

        String os = System.getProperty("os.name").toLowerCase();
        try {
            if (os.contains("win")) {
                // Windows: Opens Explorer and highlights the specific file
                new ProcessBuilder("explorer.exe", "/select,", filePath.toString()).start();
            } else if (os.contains("mac")) {
                // macOS: Opens Finder and highlights the specific file
                new ProcessBuilder("open", "-R", filePath.toString()).start();
            } else {
                // Linux: Opens the parent directory in the default file manager
                new ProcessBuilder("xdg-open", filePath.getParent().toString()).start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    // ==========================================
    // PAUSE / RESUME
    // ==========================================

    @FXML
    private void pauseDownload() {
        synchronized (this) {
            paused = !paused;

            if (paused) {
                pauseButton.setText("Resume");
                percentageLabel.setText("Paused");
            } else {
                pauseButton.setText("Pause");
                percentageLabel.setText("Resuming...");

                // If it was restored from the database, start a new thread
                if (isRestored) {
                    isRestored = false;
                    if (resumeAction != null) {
                        resumeAction.run();
                    }
                } else {
                    // If it was just paused during this session, wake the sleeping thread
                    notifyAll();
                }
            }
        }
    }


    // ==========================================
    // CANCEL
    // ==========================================

    @FXML
    private void cancelDownload() {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Cancel Download");
        confirmation.setHeaderText("Cancel this download?");
        confirmation.setContentText("This will stop the download and permanently delete the partial file.");

        Optional<ButtonType> result = confirmation.showAndWait();

        if (result.isPresent() && result.get() == ButtonType.OK) {
            cancelled = true;

            // Immediately disable buttons to prevent double-clicks
            pauseButton.setDisable(true);
            cancelButton.setDisable(true);
            showFolderButton.setDisable(true);

            if (isRestored) {
                try {
                    if (filePath != null) {
                        // Delete the main file if it exists
                        Files.deleteIfExists(filePath);

                        // Delete any multi-part chunk files
                        for (int i = 0; i < 4; i++) {
                            Files.deleteIfExists(Path.of(filePath.toString() + ".part" + i));
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                percentageLabel.setText("Cancelled");
                deleteButton.setDisable(false);
                downloadAgainButton.setDisable(false);
                DownloadDAO.updateStatus(databaseId, "Cancelled");
            } else {
                synchronized (this) {
                    paused = false;
                    notifyAll();
                }
            }
        }
    }
    public void restoreAsPaused(Path downloadFolder, String url) {
        this.paused = true;
        this.isRestored = true;
        this.filePath = downloadFolder.resolve(fileNameLabel.getText());

        pauseButton.setText("Resume");
        speedLabel.setText("Speed: --");
        timeLabel.setText("Time remaining: --");

        cancelButton.setDisable(false);
        deleteButton.setDisable(false);
        showFolderButton.setDisable(false);

        CompletableFuture.runAsync(() -> {
            try {
                // Calculate the size of the file, checking chunks if the merged file doesn't exist yet
                long existingSize = 0;
                if (Files.exists(filePath)) {
                    existingSize = Files.size(filePath);
                } else {
                    for (int i = 0; i < 4; i++) {
                        Path partPath = downloadFolder.resolve(fileNameLabel.getText() + ".part" + i);
                        if (Files.exists(partPath)) {
                            existingSize += Files.size(partPath);
                        }
                    }
                }

                // Make variables final/effectively final for the lambda
                final long finalExistingSize = existingSize;

                HttpClient client = HttpClient.newHttpClient();

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .method("HEAD", HttpRequest.BodyPublishers.noBody())
                        .build();

                HttpResponse<Void> response = client.send(
                        request,
                        HttpResponse.BodyHandlers.discarding()
                );

                long totalSize = response.headers().firstValueAsLong("Content-Length").orElse(-1L);

                Platform.runLater(() -> {
                    if (totalSize > 0) {
                        double progress = (double) finalExistingSize / totalSize;
                        progressBar.setProgress(progress);
                        percentageLabel.setText(String.format("Paused (%.0f%%)", progress * 100));
                    } else {
                        percentageLabel.setText("Paused");
                    }
                });

            } catch (Exception e) {
                Platform.runLater(() -> percentageLabel.setText("Paused"));
            }
        });
    }
}