package com.downloadmanager.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class Database {

    private static final String URL =
            "jdbc:sqlite:download_manager.db";

    public static Connection connect()
            throws SQLException {

        Connection connection =
                DriverManager.getConnection(URL);

        try (Statement statement =
                     connection.createStatement()) {

            statement.execute(
                    "PRAGMA busy_timeout = 10000"
            );
        }

        return connection;
    }

    public static void initializeDatabase() {
        String url = "jdbc:sqlite:downloads.db"; // Make sure this matches your DB name

        // 1. Create Categories Table
        String createCategoriesTable = "CREATE TABLE IF NOT EXISTS categories ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "name TEXT UNIQUE NOT NULL"
                + ");";

        // 2. Create Downloads Table with a FOREIGN KEY relationship
        String createDownloadsTable = "CREATE TABLE IF NOT EXISTS downloads ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "file_name TEXT,"
                + "url TEXT,"
                + "file_path TEXT,"
                + "status TEXT,"
                + "category_id INTEGER,"
                + "FOREIGN KEY(category_id) REFERENCES categories(id)"
                + ");";

        try (java.sql.Connection conn = java.sql.DriverManager.getConnection(url);
             java.sql.Statement stmt = conn.createStatement()) {

            // Enable Foreign Key support in SQLite
            stmt.execute("PRAGMA foreign_keys = ON;");

            // Execute table creation
            stmt.execute(createCategoriesTable);
            stmt.execute(createDownloadsTable);

            // 3. Insert default categories if they don't exist yet
            String insertCategories = "INSERT OR IGNORE INTO categories (name) VALUES "
                    + "('Video'), ('Audio'), ('Compressed'), ('Documents'), ('General');";
            stmt.execute(insertCategories);

        } catch (java.sql.SQLException e) {
            System.out.println("Database initialization error: " + e.getMessage());
        }
    }
}