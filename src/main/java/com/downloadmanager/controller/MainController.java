package com.downloadmanager.controller;
import java.util.prefs.Preferences;

import com.downloadmanager.DownloadManager;
import com.downloadmanager.database.DownloadDAO;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MainController {

    @FXML
    private TextField urlField;

    @FXML
    private ListView<Node> downloadList;

    @FXML
    private Label locationLabel;


    private final DownloadManager downloadManager =
            new DownloadManager();


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

    // --------------------------------------------------
    // CHOOSE DOWNLOAD LOCATION
    // --------------------------------------------------

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

    // --------------------------------------------------
    // ADD DOWNLOAD
    // --------------------------------------------------

    @FXML
    private void addDownload() {

        String url =
                urlField.getText().trim();


        // Don't add empty URL
        if (url.isEmpty()) {
            return;
        }


        try {

            // Load download item
            FXMLLoader loader =
                    new FXMLLoader(
                            getClass().getResource(
                                    "/com.downloadmanager/DownloadItem.fxml"
                            )
                    );


            Node downloadItem =
                    loader.load();


            // Get item controller
            DownloadItemController controller =
                    loader.getController();


            // Get file name
            String fileName =
                    getFileName(url);
            String filePath =
                    downloadLocation
                            .resolve(fileName)
                            .toString();


            // Display information
            controller.setDownloadInfo(
                    fileName,
                    url
            );

            int databaseId =
                    DownloadDAO.addDownload(
                            fileName,
                            url,
                            filePath,
                            "Downloading"
                    );
            controller.setDatabaseId(databaseId);
            // Add item to ListView
            downloadList.getItems().add(
                    downloadItem
            );
            controller.setRemoveFromListAction(() ->
                    downloadList.getItems().remove(
                            downloadItem
                    )
            );

            controller.setDownloadAgainAction(() -> {

                // Start the download again
                downloadManager.startDownload(
                        controller,
                        fileName,
                        url,
                        downloadLocation
                );

            });
            // Start download
            downloadManager.startDownload(
                    controller,
                    fileName,
                    url,
                    downloadLocation
            );


            // Clear URL field
            urlField.clear();


        } catch (IOException e) {

            e.printStackTrace();
        }
    }


    // --------------------------------------------------
    // GET FILE NAME
    // --------------------------------------------------

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
}