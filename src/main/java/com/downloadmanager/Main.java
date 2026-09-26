package com.downloadmanager;

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

    private static TrayIcon activeTrayIcon;

    public static void showNotification(String title, String message, TrayIcon.MessageType type) {
        if (activeTrayIcon != null) {
            EventQueue.invokeLater(() ->
                    activeTrayIcon.displayMessage(
                            title,
                            message,
                            type
                    )
            );
        }
    }

    @Override
    public void start(Stage stage) throws Exception {

        Platform.setImplicitExit(false);

        FXMLLoader loader = new FXMLLoader(
                getClass().getResource(
                        "/com.downloadmanager/main.fxml"
                )
        );

        Scene scene = new Scene(loader.load());

        stage.setTitle("Download Manager");
        stage.setScene(scene);

        try {

            URL iconUrl = getClass().getResource(
                    "/com.downloadmanager/icon.jpeg"
            );

            System.out.println("ICON URL = " + iconUrl);

            if (iconUrl != null) {
                javafx.scene.image.Image icon =
                        new javafx.scene.image.Image(
                                iconUrl.toExternalForm()
                        );

                System.out.println("ICON ERROR = " + icon.isError());
                System.out.println("ICON WIDTH = " + icon.getWidth());
                System.out.println("ICON HEIGHT = " + icon.getHeight());

                stage.getIcons().add(icon);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        stage.setOnCloseRequest(event -> {

            event.consume();

            stage.hide();
        });

        stage.show();
        try {
            setupSystemTray(stage);
        } catch (Exception e) {
            System.err.println(
                    "System tray initialization failed."
            );

            e.printStackTrace();
        }
    }

    private void setupSystemTray(Stage stage) {

        if (activeTrayIcon != null) {
            return;
        }

        if (!SystemTray.isSupported()) {
            return;
        }

        SystemTray tray = SystemTray.getSystemTray();

        PopupMenu trayMenu = new PopupMenu();

        MenuItem showItem =
                new MenuItem("Show Download Manager");

        MenuItem exitItem =
                new MenuItem("Exit");

        // ==========================================
        // SHOW APPLICATION
        // ==========================================

        showItem.addActionListener(e ->
                Platform.runLater(() -> {

                    if (!stage.isShowing()) {
                        stage.show();
                    }

                    stage.setIconified(false);
                    stage.toFront();
                    stage.requestFocus();
                })
        );

        // ==========================================
        // EXIT APPLICATION
        // ==========================================

        exitItem.addActionListener(e -> {

            removeSystemTray();

            Platform.runLater(() -> {

                Platform.exit();
            });
        });

        trayMenu.add(showItem);
        trayMenu.addSeparator();
        trayMenu.add(exitItem);

        // ==========================================
        // LOAD TRAY ICON
        // ==========================================

        java.awt.Image trayImage = loadTrayIcon();

        // ==========================================
        // CREATE TRAY ICON
        // ==========================================

        activeTrayIcon = new TrayIcon(
                trayImage,
                "Download Manager",
                trayMenu
        );

        activeTrayIcon.setImageAutoSize(true);

        // ==========================================
        // CLICK TRAY ICON
        // ==========================================

        activeTrayIcon.addActionListener(e ->
                Platform.runLater(() -> {

                    if (!stage.isShowing()) {
                        stage.show();
                    }

                    stage.setIconified(false);
                    stage.toFront();
                    stage.requestFocus();
                })
        );

        // ==========================================
        // ADD ICON TO SYSTEM TRAY
        // ==========================================

        try {

            tray.add(activeTrayIcon);

        } catch (AWTException e) {

            e.printStackTrace();

            activeTrayIcon = null;
        }
    }

    private java.awt.Image loadTrayIcon() {

        try {

            URL iconUrl = getClass().getResource(
                    "/com.downloadmanager/icon.jpeg"
            );

            if (iconUrl != null) {

                java.awt.Image image =
                        ImageIO.read(iconUrl);

                if (image != null) {
                    return image;
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        // Fallback icon
        return createFallbackTrayIcon();
    }

    private java.awt.Image createFallbackTrayIcon() {

        BufferedImage image = new BufferedImage(
                16,
                16,
                BufferedImage.TYPE_INT_ARGB
        );

        Graphics2D g2d = image.createGraphics();

        g2d.setColor(Color.BLUE);
        g2d.fillRect(0, 0, 16, 16);

        g2d.dispose();

        return image;
    }

    private void removeSystemTray() {

        if (activeTrayIcon == null) {
            return;
        }

        try {

            SystemTray tray =
                    SystemTray.getSystemTray();

            tray.remove(activeTrayIcon);

        } catch (Exception e) {
            e.printStackTrace();
        }

        activeTrayIcon = null;
    }

    @Override
    public void stop() throws Exception {

        DownloadDAO.pauseActiveDownloadsOnShutdown();

        removeSystemTray();

        // Release single-instance lock
        Launcher.closeInstanceLock();

        super.stop();
    }
}