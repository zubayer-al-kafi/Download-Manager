package com.downloadmanager;

import com.downloadmanager.database.Database;

import com.downloadmanager.database.DownloadDAO;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.application.Platform;
import java.awt.AWTException;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import java.awt.Color;

public class Main extends Application {

    @Override
    public void start(Stage stage) throws Exception {

        // 1. Prevent JavaFX from closing when the window is hidden
        Platform.setImplicitExit(false);

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com.downloadmanager/main.fxml"));
        Scene scene = new Scene(loader.load());

        stage.setTitle("Download Manager");
        stage.setScene(scene);

        // 2. Override the close button (X) to hide the window instead
        stage.setOnCloseRequest(event -> {
            stage.hide();
            event.consume(); // Consume the event so the window doesn't actually close
        });

        // 3. Initialize the System Tray
        setupSystemTray(stage);

        stage.show();
    }
    @Override
    public void stop() throws Exception {
        // Automatically mark any "Downloading" items as "Paused" in the database
        DownloadDAO.pauseActiveDownloadsOnShutdown();
        super.stop();
    }
    private void setupSystemTray(Stage stage) {

        // Check if the OS supports the system tray
        if (!SystemTray.isSupported()) {
            System.out.println("System tray is not supported on this OS.");
            Platform.setImplicitExit(true);
            return;
        }

        SystemTray tray = SystemTray.getSystemTray();

        // Create a temporary blue square icon for the tray
        // (Replace this later with your own Image via Toolkit.getDefaultToolkit().getImage(...))
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setColor(Color.BLUE);
        g2d.fillRect(0, 0, 16, 16);
        g2d.dispose();

        // Create Tray Menu Items
        PopupMenu trayMenu = new PopupMenu();

        MenuItem showItem = new MenuItem("Show Download Manager");
        MenuItem exitItem = new MenuItem("Exit");

        // Action: Re-open the window
        showItem.addActionListener(e -> Platform.runLater(() -> {
            stage.show();
            stage.toFront();
        }));

        // Action: Safely exit the app and pause downloads
        exitItem.addActionListener(e -> {

            // 1. Pause active downloads in the database
            // DownloadDAO.pauseActiveDownloadsOnShutdown();

            // 2. Shut down JavaFX gracefully
            Platform.exit();

            // 3. Terminate JVM (forces background ExecutorService threads to stop)
            System.exit(0);
        });

        trayMenu.add(showItem);
        trayMenu.addSeparator();
        trayMenu.add(exitItem);

        TrayIcon trayIcon = new TrayIcon(image, "Download Manager", trayMenu);
        trayIcon.setImageAutoSize(true);

        // Double-clicking the tray icon also shows the app
        trayIcon.addActionListener(e -> Platform.runLater(() -> {
            stage.show();
            stage.toFront();
        }));

        try {
            tray.add(trayIcon);
        } catch (AWTException e) {
            e.printStackTrace();
        }
    }


    public static void main(String[] args) {

        Database.initializeDatabase();

        launch();
    }
}