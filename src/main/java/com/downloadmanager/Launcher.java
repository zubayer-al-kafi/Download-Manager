package com.downloadmanager;

public class Launcher {
    public static void main(String[] args) {
        // This bypasses the JavaFX module path restrictions in fat JARs
        Main.main(args);
    }
}