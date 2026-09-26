package com.downloadmanager.controller;

import javafx.scene.control.Label;
import com.downloadmanager.database.DownloadDAO;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ListView;
import javafx.stage.Stage;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Optional;

public class HistoryController {

    @FXML
    private ListView<Node> historyList;

    @FXML
    private Label totalLabel;

    @FXML
    private Label completedLabel;

    @FXML
    private Label cancelledLabel;

    @FXML
    private Label failedLabel;

    @FXML
    private void initialize() {
        loadHistory();
        loadStatistics();
    }

    @FXML
    private void clearHistory() {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Clear History");
        confirmation.setHeaderText("Clear download history?");
        confirmation.setContentText(
                "All history records will be permanently removed.\n"
                        + "Downloaded files will not be deleted."
        );

        Optional<ButtonType> result = confirmation.showAndWait();

        if (result.isPresent() && result.get() == ButtonType.OK) {
            DownloadDAO.clearHistory();
            historyList.getItems().clear();
            loadStatistics();
        }
    }

    private void loadStatistics() {
        int total = DownloadDAO.getTotalDownloads();
        int completed = DownloadDAO.getCompletedDownloads();
        int cancelled = DownloadDAO.getCancelledDownloads();
        int failed = DownloadDAO.getFailedDownloads();

        totalLabel.setText("Total: " + total);
        completedLabel.setText("Completed: " + completed);
        cancelledLabel.setText("Cancelled: " + cancelled);
        failedLabel.setText("Failed: " + failed);
    }

    private void loadHistory() {
        // Clear existing items to prevent duplicates on reload
        historyList.getItems().clear();

        try {
            DownloadDAO.loadAllDownloads(
                    (fileName, url, filePath, status) -> {
                        try {
                            FXMLLoader loader =
                                    new FXMLLoader(
                                            getClass().getResource(
                                                    "/com.downloadmanager/HistoryItem.fxml"
                                            )
                                    );

                            Node historyItem =
                                    loader.load();

                            HistoryItemController controller =
                                    loader.getController();

                            controller.setData(
                                    fileName,
                                    url,
                                    filePath,
                                    status
                            );

                            historyList.getItems().add(
                                    0, historyItem
                            );

                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
            );

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void backToDownloads(javafx.event.ActionEvent event) {
        Stage historyStage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        historyStage.close();
    }
}