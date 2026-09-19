package com.downloadmanager.controller;

import com.downloadmanager.database.DownloadDAO;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.VBox;

import java.awt.Desktop;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

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


    private Path filePath;
    public void setDownloadAgainAction(
            Runnable action) {

        this.downloadAgainAction = action;
    }
    @FXML
    private void downloadAgain() {

        cancelled = false;
        paused = false;

        progressBar.setProgress(0);
        percentageLabel.setText("Downloading");

        speedLabel.setText("Speed: 0 KB/s");
        timeLabel.setText("Time remaining: --");

        pauseButton.setDisable(false);
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
    public void download(
            String fileName,
            String url,
            Path downloadFolder) {

        try {

            HttpClient client =
                    HttpClient.newHttpClient();

            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .GET()
                            .build();

            HttpResponse<InputStream> response =
                    client.send(
                            request,
                            HttpResponse.BodyHandlers
                                    .ofInputStream()
                    );


            if (response.statusCode() < 200 ||
                    response.statusCode() >= 300) {

                DownloadDAO.updateStatus(
                        databaseId,
                        "Failed"
                );

                Platform.runLater(() -> {

                    percentageLabel.setText(
                            "Failed"
                    );

                    pauseButton.setDisable(true);
                    cancelButton.setDisable(true);

                    downloadAgainButton.setDisable(false);
                });

                return;
            }


            long totalSize =
                    response.headers()
                            .firstValueAsLong(
                                    "Content-Length"
                            )
                            .orElse(-1);


            Files.createDirectories(
                    downloadFolder
            );


            filePath =
                    downloadFolder.resolve(
                            fileName
                    );

            long downloaded = 0;

            long downloadStartTime =
                    System.currentTimeMillis();

            long lastUpdateTime =
                    downloadStartTime;

            long lastUpdateBytes = 0;

            try (
                    InputStream input =
                            response.body();

                    OutputStream output =
                            Files.newOutputStream(
                                    filePath
                            )
            ) {

                byte[] buffer =
                        new byte[8192];

                int bytesRead;


                while (
                        (bytesRead =
                                input.read(buffer)) != -1
                ) {

                    synchronized (this) {

                        while (
                                paused &&
                                        !cancelled
                        ) {

                            wait();
                        }
                    }


                    if (cancelled) {
                        break;
                    }


                    output.write(
                            buffer,
                            0,
                            bytesRead
                    );


                    downloaded += bytesRead;

                    if (totalSize > 0) {

                        double progress =
                                (double) downloaded / totalSize;

                        long currentTime =
                                System.currentTimeMillis();

                        long elapsed =
                                currentTime - lastUpdateTime;

                        // Update the speed display every 500 ms
                        if (elapsed >= 500) {

                            long bytesSinceUpdate =
                                    downloaded - lastUpdateBytes;

                            double speed =
                                    bytesSinceUpdate /
                                            (elapsed / 1000.0);

                            long remainingBytes =
                                    totalSize - downloaded;

                            double remainingSeconds =
                                    speed > 0
                                            ? remainingBytes / speed
                                            : 0;

                            lastUpdateTime = currentTime;
                            lastUpdateBytes = downloaded;

                            Platform.runLater(() -> {

                                progressBar.setProgress(
                                        progress
                                );

                                percentageLabel.setText(
                                        String.format(
                                                "%.0f%%",
                                                progress * 100
                                        )
                                );

                                speedLabel.setText(
                                        "Speed: " +
                                                formatSpeed(speed)
                                );

                                timeLabel.setText(
                                        "Time remaining: " +
                                                formatTime(remainingSeconds)
                                );
                            });
                        }
                    }
                }
            }

            if (!cancelled && totalSize > 0) {

                long totalTime =
                        System.currentTimeMillis()
                                - downloadStartTime;

                double averageSpeed =
                        totalTime > 0
                                ? downloaded /
                                (totalTime / 1000.0)
                                : 0;

                Platform.runLater(() -> {

                    progressBar.setProgress(1.0);

                    percentageLabel.setText(
                            "100%"
                    );

                    speedLabel.setText(
                            "Speed: " +
                                    formatSpeed(averageSpeed)
                    );

                    timeLabel.setText(
                            "Time remaining: 0s"
                    );
                });
            }

            // Download cancelled
            if (cancelled) {

                Platform.runLater(() -> {

                    percentageLabel.setText(
                            "Cancelled"
                    );

                    pauseButton.setDisable(true);
                    cancelButton.setDisable(true);

                    // Allow user to delete partial file
                    deleteButton.setDisable(false);

                    // Allow user to download again
                    downloadAgainButton.setDisable(false);

                    // Allow user to open the containing folder
                    showFolderButton.setDisable(false);

                    DownloadDAO.updateStatus(
                            databaseId,
                            "Cancelled"
                    );

                });

                return;
            }


            // Download completed
            Platform.runLater(() -> {

                progressBar.setProgress(1.0);

                percentageLabel.setText(
                        "Completed"
                );
                DownloadDAO.updateStatus(
                        databaseId,
                        "Completed"
                );

                pauseButton.setDisable(true);
                cancelButton.setDisable(true);

                // Enable folder button
                showFolderButton.setDisable(false);
                deleteButton.setDisable(false);

            });


        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            Platform.runLater(() ->
                    percentageLabel.setText(
                            "Interrupted"
                    )
            );


        } catch (Exception e) {

            e.printStackTrace();

            DownloadDAO.updateStatus(
                    databaseId,
                    "Failed"
            );

            Platform.runLater(() -> {

                percentageLabel.setText(
                        "Failed"
                );

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

                pauseButton.setText(
                        "Resume"
                );

                percentageLabel.setText(
                        "Paused"
                );


            } else {

                pauseButton.setText(
                        "Pause"
                );

                notify();
            }
        }
    }


    // ==========================================
    // CANCEL
    // ==========================================

    @FXML
    private void cancelDownload() {

        cancelled = true;

        synchronized (this) {

            paused = false;
            notify();
        }

        pauseButton.setDisable(true);
        cancelButton.setDisable(true);

        // Enable delete and download again
        deleteButton.setDisable(false);
        downloadAgainButton.setDisable(false);

        percentageLabel.setText("Cancelled");
    }
}