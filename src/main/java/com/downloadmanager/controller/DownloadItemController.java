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
import java.util.Optional;

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

    private volatile boolean paused = false;
    private volatile boolean cancelled = false;
    private long lastTime;
    private long lastBytes;
    private boolean isRestored = false;
    private Runnable resumeAction;

    public void setResumeAction(Runnable action) {
        this.resumeAction = action;
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
    public void setDownloadInfo(
            String fileName,
            String url) {

        fileNameLabel.setText(fileName);
        urlLabel.setText(url);
    }

    private void deleteFromFiles() {

        if (filePath == null ||
                !Files.exists(filePath)) {

            return;
        }

        try {

            Files.deleteIfExists(filePath);

            Platform.runLater(() -> {

                percentageLabel.setText(
                        "Deleted"
                );

                // File no longer exists
                showFolderButton.setDisable(true);

                // Keep Delete button enabled
                // so "Remove from list" is still available
                deleteButton.setDisable(false);

                // Allow downloading again
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

        try {
            Files.createDirectories(downloadFolder);
            filePath = downloadFolder.resolve(fileName);

            // 1. Check if the file already exists and get its size
            long existingSize = 0;
            if (Files.exists(filePath)) {
                existingSize = Files.size(filePath);
            }

            HttpClient client = HttpClient.newHttpClient();

            // 2. Build the request and append the Range header if we have partial data
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET();

            if (existingSize > 0) {
                requestBuilder.header("Range", "bytes=" + existingSize + "-");
            }

            HttpRequest request = requestBuilder.build();

            HttpResponse<InputStream> response = client.send(
                    request,
                    HttpResponse.BodyHandlers.ofInputStream()
            );

            // 3. Handle 416 Range Not Satisfiable (file is already fully downloaded)
            if (response.statusCode() == 416) {
                Platform.runLater(() -> {
                    progressBar.setProgress(1.0);
                    percentageLabel.setText("Completed");
                    pauseButton.setDisable(true);
                    cancelButton.setDisable(true);
                    showFolderButton.setDisable(false);
                    deleteButton.setDisable(false);
                    DownloadDAO.updateStatus(databaseId, "Completed");
                });
                return;
            }

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                DownloadDAO.updateStatus(databaseId, "Failed");
                Platform.runLater(() -> {
                    percentageLabel.setText("Failed");
                    pauseButton.setDisable(true);
                    cancelButton.setDisable(true);
                    downloadAgainButton.setDisable(false);
                });
                return;
            }

            // 4. Check if the server accepted our Range request
            boolean append = (response.statusCode() == 206); // 206 = Partial Content

            // If the server rejected the Range header (returned 200 OK instead of 206), we must start over
            if (!append && existingSize > 0) {
                existingSize = 0;
            }

            long serverContentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);

            // Calculate the true total size and starting bytes
            long totalSize = (serverContentLength > 0 && append) ? (existingSize + serverContentLength) : serverContentLength;
            long downloaded = append ? existingSize : 0;

            long downloadStartTime = System.currentTimeMillis();
            long lastUpdateTime = downloadStartTime;
            long lastUpdateBytes = downloaded;

            // 5. Open stream in APPEND mode if we are resuming, else CREATE mode
            try (
                    InputStream input = response.body();
                    OutputStream output = Files.newOutputStream(
                            filePath,
                            append ? java.nio.file.StandardOpenOption.APPEND : java.nio.file.StandardOpenOption.CREATE
                    )
            ) {
                byte[] buffer = new byte[8192];
                int bytesRead;

                while ((bytesRead = input.read(buffer)) != -1) {
                    synchronized (this) {
                        while (paused && !cancelled) {
                            wait();
                        }
                    }

                    if (cancelled) {
                        break;
                    }

                    output.write(buffer, 0, bytesRead);
                    downloaded += bytesRead;

                    if (totalSize > 0) {
                        double progress = (double) downloaded / totalSize;
                        long currentTime = System.currentTimeMillis();
                        long elapsed = currentTime - lastUpdateTime;

                        // Update the speed display every 500 ms
                        if (elapsed >= 500) {
                            long bytesSinceUpdate = downloaded - lastUpdateBytes;
                            double speed = bytesSinceUpdate / (elapsed / 1000.0);
                            long remainingBytes = totalSize - downloaded;
                            double remainingSeconds = speed > 0 ? remainingBytes / speed : 0;

                            lastUpdateTime = currentTime;
                            lastUpdateBytes = downloaded;

                            Platform.runLater(() -> {
                                progressBar.setProgress(progress);
                                percentageLabel.setText(String.format("%.0f%%", progress * 100));
                                speedLabel.setText("Speed: " + formatSpeed(speed));
                                timeLabel.setText("Time remaining: " + formatTime(remainingSeconds));
                            });
                        }
                    }
                }
            }

            if (!cancelled && totalSize > 0) {
                long totalTime = System.currentTimeMillis() - downloadStartTime;
                double averageSpeed = totalTime > 0 ? (downloaded - (append ? existingSize : 0)) / (totalTime / 1000.0) : 0;

                Platform.runLater(() -> {
                    progressBar.setProgress(1.0);
                    percentageLabel.setText("Completed");
                    speedLabel.setText("Speed: " + formatSpeed(averageSpeed));
                    timeLabel.setText("Time remaining: 0s");
                    DownloadDAO.updateStatus(databaseId, "Completed");
                    pauseButton.setDisable(true);
                    cancelButton.setDisable(true);
                    showFolderButton.setDisable(false);
                    deleteButton.setDisable(false);
                });
            }
            // Download cancelled
            if (cancelled) {

                // The output stream is now closed, so it is safe to delete the file without locking errors
                try {
                    if (filePath != null) {
                        Files.deleteIfExists(filePath);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                Platform.runLater(() -> {
                    percentageLabel.setText("Cancelled");
                    pauseButton.setDisable(true);
                    cancelButton.setDisable(true);
                    showFolderButton.setDisable(true); // Keep disabled since file is gone

                    // Allow user to remove the item from the list or start fresh
                    deleteButton.setDisable(false);
                    downloadAgainButton.setDisable(false);

                    DownloadDAO.updateStatus(databaseId, "Cancelled");
                });
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Platform.runLater(() -> percentageLabel.setText("Interrupted"));

        } catch (Exception e) {
            e.printStackTrace();
            DownloadDAO.updateStatus(databaseId, "Failed");
            Platform.runLater(() -> {
                percentageLabel.setText("Failed");
                pauseButton.setDisable(true);
                cancelButton.setDisable(true);
                downloadAgainButton.setDisable(false);
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

        // Don't do anything if file doesn't exist
        if (filePath == null ||
                !Files.exists(filePath)) {

            return;
        }


        try {

            // Windows File Explorer
            new ProcessBuilder(
                    "explorer.exe",
                    "/select,",
                    filePath.toString()
            ).start();

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
                    notify();
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
            showFolderButton.setDisable(true); // Disable because file will be deleted

            if (isRestored) {
                // If it was restored but never resumed, there is no background thread running.
                // We can safely delete the file directly right now.
                try {
                    if (filePath != null) {
                        Files.deleteIfExists(filePath);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                percentageLabel.setText("Cancelled");
                deleteButton.setDisable(false);
                downloadAgainButton.setDisable(false);
                DownloadDAO.updateStatus(databaseId, "Cancelled");
            } else {
                // If a background thread is running, wake it up so it can break its loop.
                // It will close the stream to release the file lock, and then delete the file.
                synchronized (this) {
                    paused = false;
                    notify();
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

        // Fetch file size asynchronously so we don't freeze the UI
        CompletableFuture.runAsync(() -> {
            try {
                long existingSize = Files.exists(filePath) ? Files.size(filePath) : 0;

                HttpClient client = HttpClient.newHttpClient();

                // Use a HEAD request to get file size without downloading the body
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
                        double progress = (double) existingSize / totalSize;
                        progressBar.setProgress(progress);
                        percentageLabel.setText(String.format("Paused (%.0f%%)", progress * 100));
                    } else {
                        percentageLabel.setText("Paused");
                    }
                });

            } catch (Exception e) {
                // If the network check fails (e.g., offline), fallback to standard text
                Platform.runLater(() -> percentageLabel.setText("Paused"));
            }
        });
    }
}