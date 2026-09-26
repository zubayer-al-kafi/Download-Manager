package com.downloadmanager;
import java.net.ServerSocket;
import javax.swing.JOptionPane;
import javafx.application.Application; // Make sure your existing imports stay

public class Launcher { // (Use your actual class name here)

    // We keep a reference to the ServerSocket so it stays open as long as the app is running
    private static ServerSocket instanceLock;

    public static void main(String[] args) {

        // ==========================================
        // SINGLE INSTANCE LOCK
        // ==========================================
        try {
            // Try to claim port 9999.
            instanceLock = new ServerSocket(9999);
        } catch (Exception e) {
            // If it fails, it means another instance is already holding port 9999!
            // Show a quick warning message and kill this duplicate instance immediately.
            JOptionPane.showMessageDialog(null,
                    "Download Manager is already running!\nPlease check your System Tray (bottom right corner).",
                    "Already Running",
                    JOptionPane.WARNING_MESSAGE);
            System.exit(0);
        }

        // If we successfully claimed the port, this is the first instance.
        // Proceed to launch the JavaFX application normally!

        Application.launch(Main.class, args); // Use your actual Application class here
    }
}