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

        String sql = """
                CREATE TABLE IF NOT EXISTS downloads (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    file_name TEXT NOT NULL,
                    url TEXT NOT NULL,
                    file_path TEXT,
                    status TEXT NOT NULL,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
                )
                """;

        try (
                Connection connection = connect();
                Statement statement =
                        connection.createStatement()
        ) {

            statement.execute(sql);

            System.out.println(
                    "Database initialized successfully."
            );

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}