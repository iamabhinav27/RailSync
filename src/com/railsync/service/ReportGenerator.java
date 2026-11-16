package com.railsync.service;

import com.railsync.manager.FileManager;
import com.railsync.model.*;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Map;

/**
 * Generates formatted text tickets and summary reports.
 */
public class ReportGenerator {

    /**
     * Generates formatted ticket text for a booking.
     */
    public static String generateTicketText(Booking booking) {
        if (booking == null) return "No booking details available.";

        StringBuilder sb = new StringBuilder(1024);
        sb.append("========================================================================================\n");
        sb.append("                       INDIAN RAILWAYS - ELECTRONIC RESERVATION SLIP                     \n");
        sb.append("                               POWERED BY RAILSYNC SYSTEM                               \n");
        sb.append("========================================================================================\n");
        sb.append(String.format(" PNR NUMBER       : %-20s  BOOKING ID    : %s\n", booking.getPnr(), booking.getBookingId()));
        sb.append(String.format(" TRAIN NO & NAME  : %-5s %-20s  TRAVEL CLASS  : %s\n",
                booking.getTrainNumber(), booking.getTrainName(), booking.getSeatClass().getDisplayName()));
        sb.append(String.format(" FROM STATION     : %-20s  TO STATION    : %s\n",
                booking.getSource().toString(), booking.getDestination().toString()));
        sb.append(String.format(" JOURNEY DATE     : %-20s  BOOKED ON     : %s\n",
                booking.getFormattedJourneyDate(), booking.getFormattedBookingTime()));
        sb.append(String.format(" BOOKING STATUS   : %s\n", booking.getStatus().name()));
        sb.append("----------------------------------------------------------------------------------------\n");
        sb.append(String.format(" %-3s | %-20s | %-4s | %-26s | %-10s | %10s\n",
                "SNO", "PASSENGER", "AGE", "SEAT / BERTH", "STATUS", "FARE (Rs)"));
        sb.append("----------------------------------------------------------------------------------------\n");

        int index = 1;
        for (Ticket t : booking.getTickets()) {
            sb.append(String.format(" %-3d | %-20s | %-4d | %-26s | %-10s | %10.2f\n",
                    index++,
                    truncate(t.getPassenger().getName(), 20),
                    t.getPassenger().getAge(),
                    truncate(t.getSeatDescription(), 26),
                    t.getStatusDescription(),
                    t.getIndividualFare()));
        }

        sb.append("----------------------------------------------------------------------------------------\n");
        sb.append(String.format(" TOTAL PASSENGERS : %-20d  TOTAL FARE PAID: ₹ %10.2f\n",
                booking.getPassengerCount(), booking.getTotalFare()));
        sb.append("========================================================================================\n");
        sb.append(" [BARCODE: ||| |||| | |||||| | ||||||| | ||||| | |||| ||| " + booking.getPnr() + "]\n");
        sb.append(" Terms & Conditions:\n");
        sb.append(" 1. One original valid Photo ID card must be presented during journey.\n");
        sb.append(" 2. RAC passengers are entitled to seating accommodation.\n");
        sb.append(" 3. Waiting list passengers cannot board reserved coaches if not confirmed.\n");
        sb.append(" 4. RailSync 24x7 Customer Support Helpline: 139\n");
        sb.append("========================================================================================\n");

        return sb.toString();
    }

    /** Saves the ticket as a receipt file (byte streams, see {@link FileManager}). */
    public static File saveTicketToFile(Booking booking, File targetDir) throws IOException {
        return FileManager.saveReceipt(booking, targetDir);
    }

    /** All bookings as a fixed-width table (administrator report). */
    public static String generateBookingsReport(Collection<? extends Booking> bookings) {
        StringBuilder sb = header("ALL BOOKINGS REPORT");
        sb.append(String.format(" %-10s | %-6s | %-12s | %-5s | %-10s | %-9s | %-4s | %10s%n",
                "PNR", "TRAIN", "DATE", "CLASS", "USER", "STATUS", "PAX", "FARE (Rs)"));
        sb.append("-".repeat(88)).append('\n');
        double total = 0;
        for (Booking b : bookings) {
            sb.append(String.format(" %-10s | %-6s | %-12s | %-5s | %-10s | %-9s | %-4d | %10.2f%n",
                    b.getPnr(), b.getTrainNumber(), b.getJourneyDate(), b.getSeatClass().getCode(),
                    truncate(b.getBookedByUserId(), 10), b.getStatus().name(), b.getPassengerCount(), b.getTotalFare()));
            total += b.getTotalFare();
        }
        sb.append("-".repeat(88)).append('\n');
        sb.append(String.format(" Bookings: %d    Gross fare: Rs %.2f%n", bookings.size(), total));
        return sb.toString();
    }

    public static String generateWaitingListReport(Collection<? extends WaitingEntry> entries) {
        StringBuilder sb = header("RAC & WAITING LIST REPORT");
        sb.append(String.format(" %-7s | %-6s | %-12s | %-5s | %-8s | %-10s | %s%n",
                "QUEUE", "TRAIN", "DATE", "CLASS", "PNR", "TICKET", "PASSENGER"));
        sb.append("-".repeat(88)).append('\n');
        for (WaitingEntry e : entries) {
            sb.append(String.format(" %-7s | %-6s | %-12s | %-5s | %-8s | %-10s | %s%n",
                    e.getLabel(), e.getTrainNumber(), e.getJourneyDate(), e.getSeatClass().getCode(),
                    e.getPnr(), truncate(e.getTicketId(), 10), e.getPassengerName()));
        }
        sb.append("-".repeat(88)).append('\n');
        sb.append(" Passengers waiting: ").append(entries.size()).append('\n');
        return sb.toString();
    }

    public static String generatePassengerReport(Collection<? extends User> users) {
        StringBuilder sb = header("REGISTERED PASSENGERS REPORT");
        sb.append(String.format(" %-4s | %-14s | %-22s | %-28s | %s%n", "ID", "USERNAME", "NAME", "EMAIL", "PHONE"));
        sb.append("-".repeat(88)).append('\n');
        for (User u : users) {
            sb.append(String.format(" %-4s | %-14s | %-22s | %-28s | %s%n", u.getUserId(),
                    truncate(u.getUsername(), 14), truncate(u.getFullName(), 22), truncate(u.getEmail(), 28), u.getPhone()));
        }
        sb.append("-".repeat(88)).append('\n');
        sb.append(" Total: ").append(users.size()).append('\n');
        return sb.toString();
    }

    private static StringBuilder header(String title) {
        StringBuilder sb = new StringBuilder(1024);
        sb.append("=".repeat(88)).append('\n');
        sb.append(" RAILSYNC - ").append(title).append('\n');
        sb.append(" Generated: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss"))).append('\n');
        sb.append("=".repeat(88)).append('\n');
        return sb;
    }

    /**
     * Generates an administrative summary report.
     */
    public static String generateAdminReport(AnalyticsService analytics) {
        StringBuilder sb = new StringBuilder(1024);
        sb.append("========================================================================================\n");
        sb.append("                       RAILSYNC SYSTEM - EXECUTIVE AUDIT & ANALYTICS                    \n");
        sb.append("                         Generated: " + LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss")) + "                         \n");
        sb.append("========================================================================================\n\n");

        sb.append("1. FLEET & RESERVATION SUMMARY\n");
        sb.append("----------------------------------------------------------------------------------------\n");
        sb.append(String.format(" Total Scheduled Trains       : %d\n", analytics.getTotalTrains()));
        sb.append(String.format(" Total Booking Transactions   : %d\n", analytics.getTotalBookings()));
        sb.append(String.format(" Total Individual Tickets     : %d\n", analytics.getTotalTicketsBooked()));
        sb.append(String.format(" Active Confirmed Passengers  : %d\n", analytics.getConfirmedTicketsCount()));
        sb.append(String.format(" Passengers in RAC Queue      : %d\n", analytics.getRacTicketsCount()));
        sb.append(String.format(" Passengers in Waiting List   : %d\n", analytics.getWaitingListTicketsCount()));
        sb.append(String.format(" Cancelled Tickets Count      : %d\n", analytics.getCancelledTicketsCount()));
        sb.append(String.format(" Fleet Average Occupancy Rate : %.2f%%\n\n", analytics.getAverageOccupancyPercentage()));

        sb.append("2. FINANCIAL METRICS\n");
        sb.append("----------------------------------------------------------------------------------------\n");
        sb.append(String.format(" Gross Revenue Generated      : ₹ %12.2f\n", analytics.getTotalGrossRevenue()));
        sb.append(String.format(" Net Active Retained Revenue  : ₹ %12.2f\n\n", analytics.getTotalActiveRevenue()));

        sb.append("3. TRAIN PERFORMANCE & POPULARITY\n");
        sb.append("----------------------------------------------------------------------------------------\n");
        sb.append(String.format(" Most Occupied Train          : %s\n", analytics.getMostOccupiedTrain()));
        sb.append(String.format(" Highest Traffic Route        : %s\n\n", analytics.getMostPopularRoute()));

        sb.append("4. CLASS-WISE PASSENGER DISTRIBUTION\n");
        sb.append("----------------------------------------------------------------------------------------\n");
        Map<SeatClass, Integer> classMap = analytics.getClassWiseOccupancy();
        for (Map.Entry<SeatClass, Integer> entry : classMap.entrySet()) {
            sb.append(String.format(" %-28s : %d passengers\n", entry.getKey().getDisplayName(), entry.getValue()));
        }
        sb.append("\n========================================================================================\n");
        sb.append("                           END OF ADMINISTRATIVE AUDIT REPORT                           \n");
        sb.append("========================================================================================\n");
        return sb.toString();
    }

    private static String truncate(String val, int maxLen) {
        if (val == null) return "";
        return val.length() <= maxLen ? val : val.substring(0, maxLen - 2) + "..";
    }
}
