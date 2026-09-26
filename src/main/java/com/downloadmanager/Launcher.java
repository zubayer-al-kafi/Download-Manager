package com.downloadmanager;

import com.downloadmanager.database.Database;
import javafx.application.Application;

import javax.swing.JOptionPane;
import java.io.IOException;
import java.net.ServerSocket;

public class Launcher {

    private static ServerSocket instanceLock;

    public static void main(String[] args) {

        try {
            instanceLock = new ServerSocket(9999);

        } catch (IOException e) {

            JOptionPane.showMessageDialog(
                    null,
                    "Download Manager is already running!\n\n"
                            + "Please check your System Tray.",
                    "Download Manager",
                    JOptionPane.WARNING_MESSAGE
            );

            return;
        }

        Database.initializeDatabase();

        Runtime.getRuntime().addShutdownHook(
                new Thread(() -> {
                    closeInstanceLock();
                })
        );

        Application.launch(Main.class, args);
    }

    public static void closeInstanceLock() {

        if (instanceLock != null && !instanceLock.isClosed()) {
            try {
                instanceLock.close();
                instanceLock = null;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}