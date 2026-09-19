package com.downloadmanager.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;

public class HistoryItemController {

    @FXML
    private Label fileNameLabel;

    @FXML
    private Label statusLabel;

    @FXML
    private Label urlLabel;

    @FXML
    private Label pathLabel;


    public void setData(
            String fileName,
            String url,
            String filePath,
            String status) {

        fileNameLabel.setText(fileName);
        urlLabel.setText(url);
        pathLabel.setText(filePath);
        statusLabel.setText(status);
    }
}