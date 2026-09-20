package com.downloadmanager;

import com.downloadmanager.database.Database;
import com.downloadmanager.database.DownloadDAO;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;

public class Main extends Application {

    // Keep a static reference so other controllers can trigger notifications
    private static TrayIcon activeTrayIcon;

    public static void showNotification(String title, String message, TrayIcon.MessageType type) {
        if (activeTrayIcon != null) {
            // Hand the notification task over to the AWT Event Dispatch Thread
            java.awt.EventQueue.invokeLater(() -> {
                activeTrayIcon.displayMessage(title, message, type);
            });
        }
    }
    @Override
    public void start(Stage stage) throws Exception {

        // 1. Prevent JavaFX from closing when the window is hidden
        Platform.setImplicitExit(false);

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com.downloadmanager/main.fxml"));
        Scene scene = new Scene(loader.load());

        stage.setTitle("Download Manager");
        stage.setScene(scene);

        // ==========================================
        // SET MAIN WINDOW ICON (JavaFX Image)
        // ==========================================
        try {
            stage.getIcons().add(
                    new javafx.scene.image.Image(getClass().getResourceAsStream("/com.downloadmanager/icon.jpg"))
            );
        } catch (Exception e) {
            System.out.println("Window icon not found, using default.");
        }

        // 2. Override the close button (X) to hide the window instead
        stage.setOnCloseRequest(event -> {
            stage.hide();
            event.consume();
        });

        // 3. Initialize the System Tray
        setupSystemTray(stage);

        stage.show();
    }

    private void setupSystemTray(Stage stage) {

        if (!SystemTray.isSupported()) {
            System.out.println("System tray is not supported on this OS.");
            Platform.setImplicitExit(true);
            return;
        }

        SystemTray tray = SystemTray.getSystemTray();
        PopupMenu trayMenu = new PopupMenu();

        MenuItem showItem = new MenuItem("Show Download Manager");
        MenuItem exitItem = new MenuItem("Exit");

        showItem.addActionListener(e -> Platform.runLater(() -> {
            stage.show();
            stage.toFront();
        }));

        exitItem.addActionListener(e -> {
            Platform.exit();
            System.exit(0);
        });

        trayMenu.add(showItem);
        trayMenu.addSeparator();
        trayMenu.add(exitItem);

        // ==========================================
        // SET SYSTEM TRAY ICON (AWT Image)
        // ==========================================
        java.awt.Image trayImage = null;
        try {
            URL iconURL = getClass().getResource("/com.downloadmanager/icon.jpg");
            if (iconURL != null) {
                trayImage = ImageIO.read(iconURL);
            } else {
                // Fallback to the blue square if icon.png is missing
                BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2d = image.createGraphics();
                g2d.setColor(Color.BLUE);
                g2d.fillRect(0, 0, 16, 16);
                g2d.dispose();
                trayImage = image;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        activeTrayIcon = new TrayIcon(trayImage, "Download Manager", trayMenu);
        activeTrayIcon.setImageAutoSize(true);

        activeTrayIcon.addActionListener(e -> Platform.runLater(() -> {
            stage.show();
            stage.toFront();
        }));

        try {
            tray.add(activeTrayIcon);
        } catch (AWTException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void stop() throws Exception {
        // Automatically mark any "Downloading" items as "Paused" in the database
        DownloadDAO.pauseActiveDownloadsOnShutdown();
        super.stop();
    }

    public static void main(String[] args) {
        Database.initializeDatabase();
        launch();
    }
}