package com.downloadmanager;

import com.downloadmanager.database.Database;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage stage)
            throws Exception {

        FXMLLoader loader =
                new FXMLLoader(
                        getClass().getResource(
                                "/com.downloadmanager/main.fxml"
                        )
                );

        Scene scene =
                new Scene(loader.load());

        stage.setTitle(
                "Download Manager"
        );

        stage.setScene(scene);

        stage.show();
    }


    public static void main(String[] args) {

        Database.initializeDatabase();

        launch();
    }
}