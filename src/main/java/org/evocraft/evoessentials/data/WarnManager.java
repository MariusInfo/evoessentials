package org.evocraft.evoessentials.data;

import org.evocraft.evocore.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class WarnManager {

    private static volatile ExecutorService dbExecutor = createExecutor();

    private static ExecutorService createExecutor() {
        return Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "EvoEssentials-Warn-DB");
            t.setDaemon(true);
            return t;
        });
    }

    private static synchronized ExecutorService getExecutor() {
        if (dbExecutor == null || dbExecutor.isShutdown()) {
            dbExecutor = createExecutor();
        }
        return dbExecutor;
    }

    public static synchronized void shutdown() {
        if (dbExecutor != null && !dbExecutor.isShutdown()) {
            dbExecutor.shutdown();
            try {
                if (!dbExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    dbExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                dbExecutor.shutdownNow();
            }
        }
    }

    public static void initializeDB() {
        getExecutor().execute(() -> {
            synchronized (DatabaseManager.get()) {
                try {
                    Connection conn = DatabaseManager.get().getConnection();
                    if (conn == null) return;
                    String idColumn = DatabaseManager.get().isMySql()
                            ? "id INT AUTO_INCREMENT PRIMARY KEY"
                            : "id INTEGER PRIMARY KEY AUTOINCREMENT";
                    try (Statement stmt = conn.createStatement()) {
                        // Creăm tabelul pentru warn-uri (compatibil SQLite & MySQL)
                        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS player_warnings (" +
                                idColumn + ", " +
                                "uuid VARCHAR(36), " +
                                "reason TEXT, " +
                                "admin_name VARCHAR(32), " +
                                "date TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
                    }
                } catch (Exception e) { e.printStackTrace(); }
            }
        });
    }

    public static void addWarn(UUID uuid, String reason, String adminName) {
        if (uuid == null) return;
        String safeReason = reason == null ? "" : reason;
        String safeAdmin = adminName == null ? "Console" : adminName;
        if (safeAdmin.length() > 32) safeAdmin = safeAdmin.substring(0, 32);
        final String finalAdmin = safeAdmin;

        getExecutor().execute(() -> {
            synchronized (DatabaseManager.get()) {
                try {
                    Connection conn = DatabaseManager.get().getConnection();
                    if (conn == null) return;
                    try (PreparedStatement stmt = conn.prepareStatement(
                            "INSERT INTO player_warnings (uuid, reason, admin_name) VALUES (?, ?, ?)")) {
                        stmt.setString(1, uuid.toString());
                        stmt.setString(2, safeReason);
                        stmt.setString(3, finalAdmin);
                        stmt.executeUpdate();
                    }
                } catch (Exception e) { e.printStackTrace(); }
            }
        });
    }

    public static void removeWarn(int warnId, Consumer<Boolean> callback) {
        getExecutor().execute(() -> {
            boolean removed = false;
            synchronized (DatabaseManager.get()) {
                try {
                    Connection conn = DatabaseManager.get().getConnection();
                    if (conn != null) {
                        try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM player_warnings WHERE id = ?")) {
                            stmt.setInt(1, warnId);
                            removed = stmt.executeUpdate() > 0;
                        }
                    }
                } catch (Exception e) { e.printStackTrace(); }
            }
            if (callback != null) {
                callback.accept(removed);
            }
        });
    }

    public static List<WarnEntry> getWarns(UUID uuid) {
        List<WarnEntry> warns = new ArrayList<>();
        if (uuid == null) return warns;
        synchronized (DatabaseManager.get()) {
            try {
                Connection conn = DatabaseManager.get().getConnection();
                if (conn == null) return warns;
                try (PreparedStatement stmt = conn.prepareStatement(
                        "SELECT id, reason, admin_name, date FROM player_warnings WHERE uuid = ? ORDER BY date DESC LIMIT 100")) {
                    stmt.setString(1, uuid.toString());
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            String rawDate = rs.getString("date");
                            warns.add(new WarnEntry(
                                    rs.getInt("id"),
                                    rs.getString("reason"),
                                    rs.getString("admin_name"),
                                    rawDate != null ? rawDate : ""
                            ));
                        }
                    }
                }
            } catch (Exception e) { e.printStackTrace(); }
        }
        return warns;
    }

    public static record WarnEntry(int id, String reason, String admin, String date) {}
}
