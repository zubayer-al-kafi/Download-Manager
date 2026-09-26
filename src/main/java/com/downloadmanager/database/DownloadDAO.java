package com.downloadmanager.database;

import java.sql.*;

public class DownloadDAO {

    // Make database writes happen one at a time
    private static final Object DB_LOCK = new Object();

    public static int addDownload(String fileName, String url, String filePath, String status) {
        synchronized (DB_LOCK) {
            String sql = "INSERT INTO downloads (file_name, url, file_path, status, category_id) VALUES (?, ?, ?, ?, ?)";

            try (Connection conn = Database.connect();
                 PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

                pstmt.setString(1, fileName);
                pstmt.setString(2, url);
                pstmt.setString(3, filePath);
                pstmt.setString(4, status);
                pstmt.setInt(5, 5); // Default to 'General' category (ID 5)

                pstmt.executeUpdate();

                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    if (rs.next()) return rs.getInt(1);
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return -1;
        }
    }

    public static void updateStatus(int id, String status) {
        synchronized (DB_LOCK) {
            String sql = """
                    UPDATE downloads
                    SET status = ?
                    WHERE id = ?
                    """;

            try (Connection connection = Database.connect();
                 PreparedStatement statement = connection.prepareStatement(sql)) {

                statement.setString(1, status);
                statement.setInt(2, id);
                statement.executeUpdate();

            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public static void deleteDownload(int id) {
        synchronized (DB_LOCK) {
            String sql = "DELETE FROM downloads WHERE id = ?";

            try (Connection connection = Database.connect();
                 PreparedStatement statement = connection.prepareStatement(sql)) {

                statement.setInt(1, id);
                statement.executeUpdate();

            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public static void clearHistory() {
        synchronized (DB_LOCK) {
            String sql = "DELETE FROM downloads";

            try (Connection connection = Database.connect();
                 PreparedStatement statement = connection.prepareStatement(sql)) {

                statement.executeUpdate();

            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    // Helper method to eliminate redundant counting logic
    private static int getCountByStatus(String status) {
        synchronized (DB_LOCK) {
            String sql = (status == null)
                    ? "SELECT COUNT(*) FROM downloads"
                    : "SELECT COUNT(*) FROM downloads WHERE status = ?";

            try (Connection connection = Database.connect();
                 PreparedStatement statement = connection.prepareStatement(sql)) {

                if (status != null) {
                    statement.setString(1, status);
                }

                try (ResultSet resultSet = statement.executeQuery()) {
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

    public static int getTotalDownloads() {
        return getCountByStatus(null);
    }

    public static int getCompletedDownloads() {
        return getCountByStatus("Completed");
    }

    public static int getCancelledDownloads() {
        return getCountByStatus("Cancelled");
    }

    public static int getFailedDownloads() {
        return getCountByStatus("Failed");
    }

    public static void loadAllDownloads(DownloadConsumer consumer) throws SQLException {
        synchronized (DB_LOCK) {
            String sql = """
                    SELECT *
                    FROM downloads
                    ORDER BY id ASC
                    """;

            try (Connection connection = Database.connect();
                 PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet resultSet = statement.executeQuery()) {

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
        void accept(String fileName, String url, String filePath, String status);
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
    public static void updateFileName(int id, String newFileName) {
        synchronized (DB_LOCK) {
            String sql = "UPDATE downloads SET file_name = ? WHERE id = ?";

            try (Connection conn = Database.connect();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setString(1, newFileName);
                pstmt.setInt(2, id);
                pstmt.executeUpdate();

            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }
}