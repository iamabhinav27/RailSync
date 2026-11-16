package com.railsync.manager;

import com.railsync.database.DatabaseConnection;
import com.railsync.model.Booking;
import com.railsync.model.RefundReceipt;
import com.railsync.service.ReportGenerator;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * File handling for receipts and reports. The database keeps the data; files are the human-readable
 * output: booking receipts in {@code data/receipts/<PNR>.txt} and exported reports in
 * {@code data/reports/}.
 *
 * <p>All reading and writing uses <b>byte streams</b>:
 * {@link FileInputStream} / {@link FileOutputStream} wrapped in {@link BufferedInputStream} /
 * {@link BufferedOutputStream}. Missing files raise {@link FileNotFoundException}; other failures
 * raise {@link IOException}.</p>
 */
public final class FileManager {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private FileManager() {}

    // ------------------------------------------------------------ locations (relative to the data folder)

    public static File receiptsDir() {
        return new File(DatabaseConnection.getDataDirectory(), "receipts");
    }

    public static File reportsDir() {
        return new File(DatabaseConnection.getDataDirectory(), "reports");
    }

    // ------------------------------------------------------------ byte-stream primitives

    /** Writes bytes with a {@link BufferedOutputStream}; the stream is always closed (try-with-resources / finally). */
    public static void writeBytes(File destination, byte[] data) throws IOException {
        File parent = destination.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Cannot create directory: " + parent);
        }
        try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(destination))) {
            out.write(data);
            out.flush();
        }
    }

    /** Reads a whole file with a {@link BufferedInputStream}. @throws FileNotFoundException when it does not exist. */
    public static byte[] readBytes(File source) throws IOException {
        if (!source.isFile()) {
            throw new FileNotFoundException("File not found: " + source.getPath());
        }
        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(source))) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int read;
            while ((read = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            return buffer.toByteArray();
        }
    }

    /** Copies a file using buffered byte streams in both directions. */
    public static void copyFile(File source, File destination) throws IOException {
        if (!source.isFile()) {
            throw new FileNotFoundException("File not found: " + source.getPath());
        }
        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(source));
             BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(destination))) {
            byte[] chunk = new byte[4096];
            int read;
            while ((read = in.read(chunk)) != -1) {
                out.write(chunk, 0, read);
            }
        }
    }

    public static void writeTextFile(String content, File destination) throws IOException {
        writeBytes(destination, content.getBytes(StandardCharsets.UTF_8));
    }

    public static String readTextFile(File source) throws IOException {
        return new String(readBytes(source), StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------ receipts

    /** Saves (or refreshes) the booking receipt as {@code data/receipts/<PNR>.txt}. */
    public static File saveReceipt(Booking booking) throws IOException {
        return saveReceipt(booking, receiptsDir());
    }

    public static File saveReceipt(Booking booking, File directory) throws IOException {
        File file = new File(directory, booking.getPnr() + ".txt");
        writeTextFile(ReportGenerator.generateTicketText(booking), file);
        return file;
    }

    /** Reads a previously saved receipt by PNR. @throws FileNotFoundException when none was saved. */
    public static String readReceipt(String pnr) throws IOException {
        return readTextFile(new File(receiptsDir(), pnr.trim().toUpperCase() + ".txt"));
    }

    /** Saves a refund receipt next to the booking receipt. */
    public static File saveRefundReceipt(RefundReceipt receipt) throws IOException {
        File file = new File(receiptsDir(), "REFUND_" + receipt.getPnr() + "_" + receipt.getReceiptId() + ".txt");
        writeTextFile(receipt.getFormattedReceipt(), file);
        return file;
    }

    // ------------------------------------------------------------ reports

    /** Exports report text to {@code data/reports/<name>_<timestamp>.txt}. */
    public static File exportReport(String reportName, String content) throws IOException {
        String safe = reportName.replaceAll("[^A-Za-z0-9_-]", "_");
        File file = new File(reportsDir(), safe + "_" + LocalDateTime.now().format(STAMP) + ".txt");
        writeTextFile(content, file);
        return file;
    }

    /** Saved report files, newest first. */
    public static List<File> listReports() {
        File[] files = reportsDir().listFiles((dir, name) -> name.endsWith(".txt"));
        if (files == null) return new ArrayList<>();
        List<File> list = new ArrayList<>(Arrays.asList(files));
        list.sort(Comparator.comparingLong(File::lastModified).reversed());
        return list;
    }

    /** Reads a saved report by file name (only files inside the reports folder are allowed). */
    public static String readReport(String fileName) throws IOException {
        File file = new File(reportsDir(), fileName).getCanonicalFile();
        if (!file.getParentFile().equals(reportsDir().getCanonicalFile())) {
            throw new FileNotFoundException("Not a saved report: " + fileName);
        }
        return readTextFile(file);
    }

    /** True when the file exists and has content (used by the UI). */
    public static boolean exists(File f) {
        try {
            return Files.isRegularFile(f.toPath()) && Files.size(f.toPath()) > 0;
        } catch (IOException e) {
            return false;
        }
    }
}
