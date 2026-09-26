package com.downloadmanager.controller;
import java.sql.SQLException;
import java.util.Optional;
import java.util.prefs.Preferences;

import com.downloadmanager.DownloadManager;
import com.downloadmanager.database.DownloadDAO;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.input.Clipboard;
import javafx.util.Duration;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import javafx.scene.control.Alert;
import javafx.application.Platform;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.downloadmanager.server.DownloadServer;
import com.downloadmanager.server.DownloadServer;
public class MainController {
    private String lastClipboardContent = "";
    @FXML
    private TextField urlField;
    @FXML
    private ListView<Node> downloadList;
    @FXML
    private Label locationLabel;
    private final DownloadManager downloadManager =
            new DownloadManager();
    private void setupClipboardMonitoring() {
        Timeline clipboardTimeline = new Timeline(
                new KeyFrame(Duration.seconds(1), event -> {

                    Clipboard clipboard = Clipboard.getSystemClipboard();

                    if (clipboard.hasString()) {
                        String content = clipboard.getString();

                        // If the content is new and exists
                        if (content != null && !content.equals(lastClipboardContent)) {
                            lastClipboardContent = content;

                            // Check if it's a URL
                            if (content.startsWith("http://") || content.startsWith("https://")) {

                                // Optional: Only auto-paste if it looks like a file
                                if (isDownloadableFile(content)) {
                                    urlField.setText(content);
                                    urlField.requestFocus();
                                    urlField.positionCaret(content.length());
                                }
                            }
                        }
                    }
                })
        );

        clipboardTimeline.setCycleCount(Timeline.INDEFINITE);
        clipboardTimeline.play();
    }
    private boolean isDownloadableFile(String url) {
        // A list of common file extensions to monitor for, PLUS youtube links
        if (url.contains("youtube.com/watch") || url.contains("youtu.be/")) {
            return true;
        }
        String fileRegex = "(?i).*\\.(zip|rar|7z|exe|msi|mp4|mkv|avi|pdf|iso|jpg|jpeg|png|mp3|dat)$";
        return url.matches(fileRegex);
    }
    // Default download location
    private Path downloadLocation;
    public void showMainScreen() {

        try {

            FXMLLoader loader =
                    new FXMLLoader(
                            getClass().getResource(
                                    "/com.downloadmanager/main.fxml"
                            )
                    );

            Parent mainRoot =
                    loader.load();

            Scene scene =
                    downloadList
                            .getScene();

            scene.setRoot(mainRoot);

        } catch (IOException e) {

            e.printStackTrace();
        }
    }
    // --------------------------------------------------
    // INITIALIZE
    // --------------------------------------------------

    public Path getDownloadLocation() {
        return downloadLocation;
    }

    @FXML
    private void initialize() {

        // Get saved download location
        String savedLocation =
                preferences.get("downloadLocation", null);

        if (savedLocation != null) {

            downloadLocation =
                    Paths.get(savedLocation);

        } else {

            // Default location
            downloadLocation =
                    Paths.get(
                            System.getProperty("user.home"),
                            "Downloads"
                    );
        }

        // Show location
        locationLabel.setText(
                downloadLocation.toString()
        );

        // Custom ListView cells
        downloadList.setCellFactory(
                list -> new ListCell<>() {

                    @Override
                    protected void updateItem(
                            Node item,
                            boolean empty) {

                        super.updateItem(item, empty);

                        if (empty || item == null) {
                            setGraphic(null);
                        } else {
                            setGraphic(item);
                        }
                    }
                }
        );
        loadSavedActiveDownloads();
        setupClipboardMonitoring();
        DownloadServer.start(this);
        // ==========================================
        // LAYOUT RESPONSIVENESS (INSTRUCTOR REQUIREMENT)
        // ==========================================
        // We must wait for the UI to be attached to the Scene/Window before binding
        downloadList.sceneProperty().addListener((observableScene, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.windowProperty().addListener((observableWindow, oldWindow, newWindow) -> {
                    if (newWindow != null) {

                        // 1. Bind the URL text field to always be exactly 60% of the window's width
                        urlField.prefWidthProperty().bind(newWindow.widthProperty().multiply(0.6));

                        // 2. Bind the ListView height to dynamically scale with the window height
                        // (Subtracting 250px to leave room for the top cards and bottom bar)
                        downloadList.prefHeightProperty().bind(newWindow.heightProperty().subtract(250));

                    }
                });
            }
        });
    }

    @FXML
    private void openHistory() throws IOException {

        FXMLLoader loader =
                new FXMLLoader(
                        getClass().getResource(
                                "/com.downloadmanager/History.fxml"
                        )
                );

        Scene scene =
                new Scene(
                        loader.load(),
                        900,
                        600
                );

        Stage historyStage =
                new Stage();

        historyStage.setTitle(
                "Download History"
        );

        historyStage.setScene(scene);

        historyStage.setMinWidth(900);
        historyStage.setMinHeight(600);

        historyStage.show();
    }

    @FXML
    private void chooseLocation() {

        DirectoryChooser directoryChooser =
                new DirectoryChooser();

        directoryChooser.setTitle(
                "Choose Download Location"
        );


        // Start from current location
        File currentFolder =
                downloadLocation.toFile();

        if (currentFolder.exists()) {

            directoryChooser.setInitialDirectory(
                    currentFolder
            );
        }


        // Get current window
        Window window =
                locationLabel.getScene().getWindow();


        // Show folder selection dialog
        File selectedFolder =
                directoryChooser.showDialog(window);


        // User cancelled
        if (selectedFolder == null) {
            return;
        }


        // Save selected location
        downloadLocation =
                selectedFolder.toPath();
        preferences.put(
                "downloadLocation",
                downloadLocation.toString()
        );

        // Update label
        locationLabel.setText(
                downloadLocation.toString()
        );
    }

    private final Preferences preferences =
            Preferences.userNodeForPackage(MainController.class);

    @FXML
    private void addDownload() {

        String url = urlField.getText().trim();

        // Don't add empty URL
        if (url.isEmpty()) {
            return;
        }

        // ==========================================
// YOUTUBE QUALITY SELECTOR (MODERN DARK THEME)
// ==========================================

        String selectedQuality = "Best Quality (Default)";

        boolean isYouTube = url.contains("youtube.com") || url.contains("youtu.be");

        if (isYouTube) {

            javafx.scene.control.Dialog<String> dialog = new javafx.scene.control.Dialog<>();
            dialog.setTitle("Download Options");

            // Remove the ugly default dialog header space
            dialog.getDialogPane().setHeaderText(null);
            dialog.getDialogPane().setGraphic(null);

            // Style the main Dialog Window
            dialog.getDialogPane().setStyle(
                    "-fx-background-color: #18181b;" + // Dark sleek background
                            "-fx-font-family: 'Segoe UI', Arial, sans-serif;"
            );

            // ------------------------------------------
            // Header & Subtitle
            // ------------------------------------------
            javafx.scene.control.Label titleLabel = new javafx.scene.control.Label("YouTube Media");
            titleLabel.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 22px; -fx-font-weight: bold;");

            javafx.scene.control.Label subtitleLabel = new javafx.scene.control.Label("Select your preferred format and resolution:");
            subtitleLabel.setStyle("-fx-text-fill: #a1a1aa; -fx-font-size: 13px; -fx-padding: 0 0 10 0;");

            // ------------------------------------------
            // ComboBox (Fully Styled in Java)
            // ------------------------------------------
            javafx.scene.control.ComboBox<String> qualityBox = new javafx.scene.control.ComboBox<>();
            qualityBox.getItems().addAll(
                    "Best Quality (Default)",
                    "Audio Only (MP3)",
                    "1080p",
                    "720p",
                    "480p"
            );
            qualityBox.setValue("Best Quality (Default)");
            qualityBox.setPrefWidth(350);

            // Style the ComboBox main button
            qualityBox.setStyle(
                    "-fx-background-color: #27272a;" +
                            "-fx-border-color: #3f3f46;" +
                            "-fx-border-radius: 6px;" +
                            "-fx-background-radius: 6px;" +
                            "-fx-padding: 4px;"
            );

            // Pro-Trick: Style the actual dropdown text to fix the "faded" bug
            qualityBox.setButtonCell(new javafx.scene.control.ListCell<String>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                    } else {
                        setText(item);
                        setStyle("-fx-text-fill: #ffffff; -fx-font-size: 14px; -fx-background-color: transparent;");
                    }
                }
            });

            // Pro-Trick: Style the popup list items and add hover effects
            qualityBox.setCellFactory(lv -> new javafx.scene.control.ListCell<String>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setStyle("-fx-background-color: #27272a;");
                    } else {
                        setText(item);
                        setStyle("-fx-background-color: #27272a; -fx-text-fill: #ffffff; -fx-font-size: 14px; -fx-padding: 10px;");

                        // Hover effects for dropdown items
                        setOnMouseEntered(e -> setStyle("-fx-background-color: #3f3f46; -fx-text-fill: #ffffff; -fx-font-size: 14px; -fx-padding: 10px; -fx-cursor: hand;"));
                        setOnMouseExited(e -> setStyle("-fx-background-color: #27272a; -fx-text-fill: #ffffff; -fx-font-size: 14px; -fx-padding: 10px;"));
                    }
                }
            });

            // ------------------------------------------
            // Layout Container
            // ------------------------------------------
            javafx.scene.layout.VBox content = new javafx.scene.layout.VBox(8, titleLabel, subtitleLabel, qualityBox);
            content.setPadding(new javafx.geometry.Insets(25, 30, 20, 30));

            // Add layout to Dialog
            dialog.getDialogPane().setContent(content);

            // ------------------------------------------
            // Dialog Buttons
            // ------------------------------------------
            javafx.scene.control.ButtonType downloadButtonType = new javafx.scene.control.ButtonType("Download", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
            javafx.scene.control.ButtonType cancelButtonType = new javafx.scene.control.ButtonType("Cancel", javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE);

            dialog.getDialogPane().getButtonTypes().addAll(downloadButtonType, cancelButtonType);

            // Style the Primary "Download" Button
            javafx.scene.Node downloadButton = dialog.getDialogPane().lookupButton(downloadButtonType);
            downloadButton.setStyle(
                    "-fx-background-color: #3b82f6;" + // Modern Blue
                            "-fx-text-fill: white;" +
                            "-fx-font-weight: bold;" +
                            "-fx-font-size: 14px;" +
                            "-fx-padding: 8px 20px;" +
                            "-fx-background-radius: 6px;" +
                            "-fx-cursor: hand;"
            );

            // Style the "Cancel" Button
            javafx.scene.Node cancelButton = dialog.getDialogPane().lookupButton(cancelButtonType);
            cancelButton.setStyle(
                    "-fx-background-color: transparent;" +
                            "-fx-text-fill: #a1a1aa;" +
                            "-fx-font-size: 14px;" +
                            "-fx-padding: 8px 15px;" +
                            "-fx-cursor: hand;"
            );

            // ------------------------------------------
            // Handle Result
            // ------------------------------------------
            dialog.setResultConverter(button -> {
                if (button == downloadButtonType) {
                    return qualityBox.getValue();
                }
                return null;
            });

            java.util.Optional<String> result = dialog.showAndWait();

            // User cancelled
            if (result.isEmpty()) {
                return;
            }

            selectedQuality = result.get();
        }

        // ==========================================
        // CREATE DOWNLOAD ITEM
        // ==========================================

        try {

            FXMLLoader loader =
                    new FXMLLoader(
                            getClass().getResource(
                                    "/com.downloadmanager/DownloadItem.fxml"
                            )
                    );

            Node downloadItem =
                    loader.load();

            // Get controller
            DownloadItemController controller = loader.getController();

            // Pass selected YouTube quality
            controller.setSelectedQuality(
                    selectedQuality
            );

            // ==========================================
            // GET FILE NAME
            // ==========================================

            var ref = new Object() {
                String fileName =
                        getFileName(url);
            };

            if (isYouTube) {

                ref.fileName =
                        "YouTube Media Processing...";
            }

            // ==========================================
            // FILE PATH
            // ==========================================

            String filePath =
                    downloadLocation
                            .resolve(ref.fileName)
                            .toString();

            // ==========================================
            // DISPLAY DOWNLOAD INFORMATION
            // ==========================================

            controller.setDownloadInfo(
                    ref.fileName,
                    url
            );

            // ==========================================
            // SAVE TO DATABASE
            // ==========================================

            int databaseId =
                    DownloadDAO.addDownload(
                            ref.fileName,
                            url,
                            filePath,
                            "Downloading"
                    );

            controller.setDatabaseId(
                    databaseId
            );

            // ==========================================
            // ADD TO LIST
            // ==========================================

            downloadList.getItems().add(0,downloadItem);

            // ==========================================
            // REMOVE FROM LIST CALLBACK
            // ==========================================

            controller.setRemoveAction(
                    () -> downloadList
                            .getItems()
                            .remove(downloadItem)
            );

            // ==========================================
            // DOWNLOAD AGAIN CALLBACK
            // ==========================================

            controller.setDownloadAgainAction(
                    () -> {

                        downloadManager.startDownload(
                                controller,
                                ref.fileName,
                                url,
                                downloadLocation
                        );
                    }
            );

            // ==========================================
            // START DOWNLOAD
            // ==========================================

            downloadManager.startDownload(
                    controller,
                    ref.fileName,
                    url,
                    downloadLocation
            );

            // ==========================================
            // CLEAR URL FIELD
            // ==========================================

            urlField.clear();

        } catch (IOException e) {

            e.printStackTrace();
        }
    }


    private String getFileName(String url) {

        try {

            String cleanUrl =
                    url.split("\\?")[0];

            int lastSlash =
                    cleanUrl.lastIndexOf("/");


            if (lastSlash >= 0 &&
                    lastSlash < cleanUrl.length() - 1) {

                return cleanUrl.substring(
                        lastSlash + 1
                );
            }

        } catch (Exception e) {

            e.printStackTrace();
        }


        return "Unknown File";
    }

    private void loadSavedActiveDownloads() {
        try {
            DownloadDAO.loadActiveDownloads((id, fileName, url, filePath, status) -> {
                try {
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("/com.downloadmanager/DownloadItem.fxml"));
                    Node downloadItem = loader.load();
                    DownloadItemController controller = loader.getController();

                    controller.setDownloadInfo(fileName, url);
                    controller.setDatabaseId(id);
                    controller.setRemoveAction(() -> downloadList.getItems().remove(downloadItem));
                    // Set up actions
                    controller.setRemoveFromListAction(() -> downloadList.getItems().remove(downloadItem));
                    controller.setDownloadAgainAction(() -> {
                        downloadManager.startDownload(controller, fileName, url, downloadLocation);
                    });
                    controller.setResumeAction(() -> {
                        downloadManager.startDownload(controller, fileName, url, downloadLocation);
                    });

                    // Pass the url variable as the second argument
                    controller.restoreAsPaused(downloadLocation, url);

                    downloadList.getItems().add(0,downloadItem);

                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    public void receiveUrlFromExtension(String url) {
        javafx.application.Platform.runLater(() -> {
            javafx.stage.Stage stage = (javafx.stage.Stage) urlField.getScene().getWindow();
            if (stage != null) {
                if (!stage.isShowing()) {
                    stage.show();
                }
                if (stage.isIconified()) {
                    stage.setIconified(false);
                }

                stage.toFront();
                stage.requestFocus();

                // Chrome-er opor force-fully focus anar jonno eita korben:
                stage.setAlwaysOnTop(true);
                stage.setAlwaysOnTop(false);
            }

            urlField.setText(url);
            addDownload();
        });
    }

    @FXML
    private void checkForUpdates() {
        // Run on a background thread so the UI doesn't freeze while waiting for the internet!
        new Thread(() -> {
            try {
                // 1. Create the HTTP Client and Request
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://api.github.com/repos/zubayer-al-kafi/Download-Manager/releases/latest"))
                        .header("Accept", "application/vnd.github.v3+json")
                        .header("User-Agent", "Download-Manager-App") // <-- CRITICAL FIX: Prevents GitHub 403 Forbidden Error
                        .build();


                // 2. Send request and get response
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                System.out.println("HTTP Status: " + response.statusCode());
                System.out.println("GitHub Response: " + response.body());

                if (response.statusCode() == 403) {
                    javafx.application.Platform.runLater(() -> {
                        Alert alert = new Alert(Alert.AlertType.WARNING);
                        alert.setTitle("Rate Limit Exceeded");
                        alert.setHeaderText("GitHub API rate limit exceeded.");
                        alert.setContentText("Please try checking for updates after an hour.");
                        alert.showAndWait();
                    });
                    return;
                }

                if (response.statusCode() == 200) {

                    // 3. PARSE THE JSON USING JACKSON
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode jsonNode = mapper.readTree(response.body());

                    // Use .asText() to safely extract the string values from the JSON tree
                    String latestVersion = jsonNode.get("tag_name").asText("Unknown Version");
                    String releaseNotes = jsonNode.get("name").asText("No release notes");

                    // 4. Update UI on the JavaFX Thread
                    Platform.runLater(() -> {
                        Alert alert = new Alert(Alert.AlertType.INFORMATION);
                        alert.setTitle("Update Checker");
                        alert.setHeaderText("Latest Version on GitHub: " + latestVersion);
                        alert.setContentText("Release Name: " + releaseNotes + "\n\nYou are running the latest version!");
                        alert.showAndWait();
                    });

                } else {
                    Platform.runLater(() -> {
                        Alert alert = new Alert(Alert.AlertType.WARNING);
                        alert.setTitle("Update Checker");
                        alert.setHeaderText("No Releases Found");
                        alert.setContentText("Create a Release tag on your GitHub repository to see this work!");
                        alert.showAndWait();
                    });
                }
            } catch (Exception e) {
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Network Error");
                    alert.setHeaderText("Could not connect to GitHub API");
                    alert.setContentText(e.getMessage());
                    alert.showAndWait();
                });
            }
        }).start();
    }

    @FXML
    private void showExtensionGuide() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Extension Installation Guide");
        alert.setHeaderText("How to add the browser extension:");

        // Ektu nicely formatted text jate user sohoje bujhte pare
        String guideText = "1. Open Chrome or Edge and go to the extensions page:\n"
                + "   - Chrome: chrome://extensions\n"
                + "   - Edge: edge://extensions\n\n"
                + "2. Turn ON 'Developer Mode' (usually at the top right).\n\n"
                + "3. Click 'Load unpacked' (or 'Load extension').\n\n"
                + "4. Select the 'extension' folder located inside this Download Manager's installation folder.\n\n"
                + "5. Pin the extension to your browser toolbar and you are ready to go!";

        alert.setContentText(guideText);

        // Dialog ti shundor vabe show korbe
        alert.showAndWait();
    }
}