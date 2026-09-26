package com.downloadmanager.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class DownloadDAO {

    // Make database writes happen one at a time
    private static final Object DB_LOCK = new Object();

    // Example of how the insert should look now:
    public static int addDownload(String fileName, String url, String filePath, String status) {
        // Notice we added category_id to the query
        String sql = "INSERT INTO downloads (file_name, url, file_path, status, category_id) VALUES (?, ?, ?, ?, ?)";

        try (java.sql.Connection conn = java.sql.DriverManager.getConnection("jdbc:sqlite:downloads.db");
             java.sql.PreparedStatement pstmt = conn.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setString(1, fileName);
            pstmt.setString(2, url);
            pstmt.setString(3, filePath);
            pstmt.setString(4, status);
            pstmt.setInt(5, 5); // Default to 'General' category (ID 5)

            pstmt.executeUpdate();

            try (java.sql.ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (java.sql.SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    public static void updateStatus(
            int id,
            String status) {

        synchronized (DB_LOCK) {

            String sql = """
                    UPDATE downloads
                    SET status = ?
                    WHERE id = ?
                    """;

            try (
                    Connection connection =
                            Database.connect();

                    PreparedStatement statement =
                            connection.prepareStatement(sql)
            ) {

                statement.setString(1, status);
                statement.setInt(2, id);

                statement.executeUpdate();

            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }
    public static void clearHistory() {

        synchronized (DB_LOCK) {

            String sql =
                    "DELETE FROM downloads";

            try (
                    Connection connection =
                            Database.connect();

                    PreparedStatement statement =
                            connection.prepareStatement(sql)
            ) {

                statement.executeUpdate();

            } catch (SQLException e) {

                e.printStackTrace();
            }
        }
    }
    public static int getTotalDownloads() {

        synchronized (DB_LOCK) {

            String sql =
                    "SELECT COUNT(*) FROM downloads";

            try (
                    Connection connection =
                            Database.connect();

                    PreparedStatement statement =
                            connection.prepareStatement(sql);

                    ResultSet resultSet =
                            statement.executeQuery()
            ) {

                if (resultSet.next()) {
                    return resultSet.getInt(1);
                }

            } catch (SQLException e) {
                e.printStackTrace();
            }

            return 0;
        }
    }
    public static int getCompletedDownloads() {

        synchronized (DB_LOCK) {

            String sql =
                    "SELECT COUNT(*) FROM downloads WHERE status = ?";

            try (
                    Connection connection =
                            Database.connect();

                    PreparedStatement statement =
                            connection.prepareStatement(sql)
            ) {

                statement.setString(1, "Completed");

                try (ResultSet resultSet =
                             statement.executeQuery()) {

                    if (resultSet.next()) {
                        return resultSet.getInt(1);
                    }
                }

            } catch (SQLException e) {
                e.printStackTrace();
            }

            return 0;
        }
    }
    public static int getCancelledDownloads() {

        synchronized (DB_LOCK) {

            String sql =
                    "SELECT COUNT(*) FROM downloads WHERE status = ?";

            try (
                    Connection connection =
                            Database.connect();

                    PreparedStatement statement =
                            connection.prepareStatement(sql)
            ) {

                statement.setString(1, "Cancelled");

                try (ResultSet resultSet =
                             statement.executeQuery()) {

                    if (resultSet.next()) {
                        return resultSet.getInt(1);
                    }
                }

            } catch (SQLException e) {
                e.printStackTrace();
            }

            return 0;
        }
    }
    public static int getFailedDownloads() {

        synchronized (DB_LOCK) {

            String sql =
                    "SELECT COUNT(*) FROM downloads WHERE status = ?";

            try (
                    Connection connection =
                            Database.connect();

                    PreparedStatement statement =
                            connection.prepareStatement(sql)
            ) {

                statement.setString(1, "Failed");

                try (ResultSet resultSet =
                             statement.executeQuery()) {

                    if (resultSet.next()) {
                        return resultSet.getInt(1);
                    }
                }

            } catch (SQLException e) {
                e.printStackTrace();
            }

            return 0;
        }
    }

    public static void loadAllDownloads(
            DownloadConsumer consumer)
            throws SQLException {

        synchronized (DB_LOCK) {

            String sql = """
                    SELECT *
                    FROM downloads
                    ORDER BY id DESC
                    """;

            try (
                    Connection connection =
                            Database.connect();

                    PreparedStatement statement =
                            connection.prepareStatement(sql);

                    ResultSet resultSet =
                            statement.executeQuery()
            ) {

                while (resultSet.next()) {

                    consumer.accept(
                            resultSet.getString("file_name"),
                            resultSet.getString("url"),
                            resultSet.getString("file_path"),
                            resultSet.getString("status")
                    );
                }
            }
        }
    }

    public interface DownloadConsumer {

        void accept(
                String fileName,
                String url,
                String filePath,
                String status
        );
    }
    public interface ActiveDownloadConsumer {
        void accept(int id, String fileName, String url, String filePath, String status);
    }
    public static void pauseActiveDownloadsOnShutdown() {
        synchronized (DB_LOCK) {
            String sql = "UPDATE downloads SET status = 'Paused' WHERE status = 'Downloading'";
            try (Connection connection = Database.connect();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public static void loadActiveDownloads(ActiveDownloadConsumer consumer) throws SQLException {
        synchronized (DB_LOCK) {
            String sql = "SELECT * FROM downloads WHERE status IN ('Downloading', 'Paused') ORDER BY id ASC";
            try (Connection connection = Database.connect();
                 PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet resultSet = statement.executeQuery()) {

                while (resultSet.next()) {
                    consumer.accept(
                            resultSet.getInt("id"),
                            resultSet.getString("file_name"),
                            resultSet.getString("url"),
                            resultSet.getString("file_path"),
                            resultSet.getString("status")
                    );
                }
            }
        }
    }
}