package com.railsync.database;

import org.sqlite.SQLiteConfig;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Central place that decides <em>where</em> the SQLite database lives and opens connections.
 *
 * <p>The path is never hard-coded to a machine. It is resolved in this order:</p>
 * <ol>
 *   <li>{@link #setDatabasePath(String)} (used by tests),</li>
 *   <li>the JVM property {@code -Drailsync.db=...},</li>
 *   <li>the environment variable {@code RAILSYNC_DB},</li>
 *   <li>the default {@code data/railsync.db} relative to the folder the app is started from.</li>
 * </ol>
 */
public final class DatabaseConnection {

    public static final String DEFAULT_PATH = "data" + File.separator + "railsync.db";

    private static volatile String databasePath;

    private DatabaseConnection() {}

    public static synchronized void setDatabasePath(String path) {
        databasePath = path;
    }

    public static String getDatabasePath() {
        String path = databasePath;
        if (path == null) {
            path = System.getProperty("railsync.db");
        }
        if (path == null || path.isBlank()) {
            path = System.getenv("RAILSYNC_DB");
        }
        if (path == null || path.isBlank()) {
            path = DEFAULT_PATH;
        }
        return path;
    }

    /** Folder that holds the database; receipts and reports are stored in sub-folders of it. */
    public static File getDataDirectory() {
        File parent = new File(getDatabasePath()).getAbsoluteFile().getParentFile();
        return parent != null ? parent : new File("data").getAbsoluteFile();
    }

    /**
     * Opens a new JDBC connection to the SQLite file. Each connection has foreign keys enforced,
     * a busy timeout so that concurrent writers wait instead of failing, WAL journaling, and
     * {@code BEGIN IMMEDIATE} transactions so a booking takes the write lock before it reads
     * seat counts (this is what makes check-then-allocate safe).
     */
    public static Connection getConnection() throws SQLException {
        File dbFile = new File(getDatabasePath()).getAbsoluteFile();
        File dir = dbFile.getParentFile();
        if (dir != null && !dir.exists() && !dir.mkdirs()) {
            throw new SQLException("Cannot create database directory: " + dir);
        }
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new SQLException("SQLite JDBC driver not found. Put lib/sqlite-jdbc-*.jar on the classpath.", e);
        }
        SQLiteConfig config = new SQLiteConfig();
        config.enforceForeignKeys(true);
        config.setBusyTimeout(30_000);
        config.setJournalMode(SQLiteConfig.JournalMode.WAL);
        config.setSynchronous(SQLiteConfig.SynchronousMode.NORMAL);
        config.setTransactionMode(SQLiteConfig.TransactionMode.IMMEDIATE);
        return config.createConnection("jdbc:sqlite:" + dbFile.getPath());
    }
}
