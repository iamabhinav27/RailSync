package com.railsync.cli;

import com.railsync.AppContext;
import com.railsync.exception.InvalidLoginException;
import com.railsync.exception.InvalidPNRException;
import com.railsync.exception.RailSyncException;
import com.railsync.manager.FileManager;
import com.railsync.manager.ReservationManager;
import com.railsync.model.*;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Interactive command-line interface. It logs in through the same {@code AuthenticationService} as the
 * JavaFX UI and uses the same services; all data is stored in the SQLite database.
 */
public class ConsoleApp {

    private final AppContext ctx;
    private final ReservationManager manager;
    private final Scanner scanner;
    private User currentUser;

    public ConsoleApp(AppContext ctx, Scanner scanner) {
        this.ctx = ctx;
        this.manager = ctx.reservations;
        this.scanner = scanner;
    }

    public static void main(String[] args) {
        start(AppContext.create(true));
    }

    public static void start(AppContext ctx) {
        new ConsoleApp(ctx, new Scanner(System.in)).run();
    }

    /** Login / register loop. Returns false when the user chooses to quit. */
    private boolean authenticate() {
        while (currentUser == null) {
            System.out.println("1. Login   2. Register new passenger   3. Quit");
            String choice = readLine("Choice: ").trim();
            try {
                if ("1".equals(choice)) {
                    String u = readLine("Username: ");
                    String pw = readLine("Password: ");
                    currentUser = ctx.auth.login(u, pw);
                } else if ("2".equals(choice)) {
                    currentUser = ctx.auth.register(readLine("Username: "), readLine("Full name: "),
                            readLine("Email: "), readLine("Mobile (optional): "), readLine("Password: "));
                    System.out.println("Account created.");
                } else if ("3".equals(choice)) {
                    return false;
                }
            } catch (InvalidLoginException e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
        System.out.println("Welcome, " + currentUser.getFullName() + " (" + currentUser.getRole() + ")\n");
        return true;
    }

    public void run() {
        printHeader();
        boolean running = authenticate();
        while (running) {
            printMainMenu();
            String choice = readLine("Enter your choice (1-9): ").trim();
            System.out.println();
            switch (choice) {
                case "1":
                    handleSearchTrains();
                    break;
                case "2":
                    handleViewAllTrains();
                    break;
                case "3":
                    handleBookTicket();
                    break;
                case "4":
                    handlePnrEnquiry();
                    break;
                case "5":
                    handleCancelTicket();
                    break;
                case "6":
                    handleCheckQueues();
                    break;
                case "7":
                    handleCalculateFare();
                    break;
                case "8":
                    handleMyBookings();
                    break;
                case "9":
                    running = false;
                    System.out.println("Thank you for using RailSync. Goodbye!");
                    break;
                default:
                    System.out.println("Invalid option. Please choose a number between 1 and 9.");
            }
            if (running) {
                System.out.println();
            }
        }
    }

    private void printHeader() {
        System.out.println("==================================================================");
        System.out.println("        RailSync – Interactive Command-Line Console Application   ");
        System.out.println("               Core Java Train Reservation System                 ");
        System.out.println("==================================================================");
    }

    private void printMainMenu() {
        System.out.println("------------------------- MAIN MENU ------------------------------");
        System.out.println("  1. Search Trains (by Source & Destination)");
        System.out.println("  2. View All Trains Catalog");
        System.out.println("  3. Book Train Ticket");
        System.out.println("  4. PNR Status Enquiry");
        System.out.println("  5. Cancel Ticket (with Promotion Cascade & Refund)");
        System.out.println("  6. View RAC & Waiting List Queue Status");
        System.out.println("  7. Dynamic Fare & Concession Calculator");
        System.out.println("  8. My Bookings & Saved Receipts");
        System.out.println("  9. Exit");
        System.out.println("------------------------------------------------------------------");
    }

    // 1. Search Trains (live availability)
    private void handleSearchTrains() {
        System.out.println("--- SEARCH TRAINS ---");
        String srcCode = readLine("Enter Source Station Code (e.g. NDLS, BCT, HWH): ").toUpperCase().trim();
        String destCode = readLine("Enter Destination Station Code (e.g. SBC, BSB, NDLS): ").toUpperCase().trim();
        LocalDate date = promptJourneyDate();
        if (date == null) return;
        try {
            List<SearchResult> results = ctx.search.searchAvailability(srcCode, destCode, date, null);
            if (results.isEmpty()) {
                System.out.println("No trains found running between " + srcCode + " and " + destCode + ".");
                return;
            }
            String sort = readLine("Sort by (D)eparture, (F)are or Enter for none: ").trim().toUpperCase();
            if (sort.startsWith("D")) results = ctx.search.sortResults(results, com.railsync.service.SearchService.byDepartureTime());
            else if (sort.startsWith("F")) results = ctx.search.sortResults(results, com.railsync.service.SearchService.byFare());
            System.out.printf("%n%-8s | %-22s | %-5s | %-6s | %-6s | %-9s | %-6s%n",
                    "Train", "Name", "Class", "Dep", "Arr", "Fare(Rs)", "Avail");
            System.out.println("-------------------------------------------------------------------------------");
            for (SearchResult r : results) {
                System.out.printf("%-8s | %-22s | %-5s | %-6s | %-6s | %9.2f | %-6s%n",
                        r.getTrain().getTrainNumber(), r.getTrain().getTrainName(), r.getSeatClass().getCode(),
                        r.getDepartureTime(), r.getArrivalTime(), r.getFare(), r.getAvailabilityText());
            }
        } catch (RailSyncException e) {
            System.out.println("Search error: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            System.out.println("Invalid date: " + e.getMessage());
        }
    }

    // 2. View All Trains
    private void handleViewAllTrains() {
        System.out.println("--- TRAIN CATALOG (" + manager.getAllTrains().size() + " Trains Registered) ---");
        System.out.printf("%-8s | %-22s | %-18s | %-16s | %-16s | %-8s%n",
                "Train No", "Name", "Type", "Source", "Destination", "Distance");
        System.out.println("------------------------------------------------------------------------------------------------");
        for (Train t : manager.getAllTrains()) {
            System.out.printf("%-8s | %-22s | %-18s | %-16s | %-16s | %-8.1f km%n",
                    t.getTrainNumber(), t.getTrainName(), t.getTrainType(),
                    t.getSource().getCode() + " (" + t.getSource().getName() + ")",
                    t.getDestination().getCode() + " (" + t.getDestination().getName() + ")",
                    t.getDistanceKm());
        }
    }

    // 3. Book Ticket
    private void handleBookTicket() {
        System.out.println("--- BOOK TICKET ---");
        String trainNo = readLine("Enter Train Number (e.g. 12952, 22436, 12628): ").trim();
        Train train = manager.getTrain(trainNo);
        if (train == null) {
            System.out.println("Error: Train '" + trainNo + "' not found.");
            return;
        }

        System.out.println("Selected Train: " + train.getTrainNumber() + " - " + train.getTrainName() +
                " (" + train.getSource().getCode() + " -> " + train.getDestination().getCode() + ")");

        // Date selection
        LocalDate journeyDate = promptJourneyDate();
        if (journeyDate == null) return;

        // Class selection
        System.out.println("\nAvailable Classes on this train:");
        List<SeatClass> availableClasses = new ArrayList<>(train.getAvailableClasses());
        for (int i = 0; i < availableClasses.size(); i++) {
            SeatClass sc = availableClasses.get(i);
            System.out.println("  " + (i + 1) + ". " + sc.getCode() + " - " + sc.getDisplayName() +
                    " (Confirmed capacity: " + train.getConfirmedCapacity(sc) + ", RAC cap: " + train.getRacCapacity(sc) + ")");
        }
        int classIdx = readInt("Select Class (1-" + availableClasses.size() + "): ", 1, availableClasses.size());
        if (classIdx < 1) return;
        SeatClass selectedClass = availableClasses.get(classIdx - 1);

        // Passengers
        int passengerCount = readInt("Enter number of passengers (1-6): ", 1, 6);
        if (passengerCount < 1) return;

        List<Passenger> passengers = new ArrayList<>();
        for (int i = 1; i <= passengerCount; i++) {
            System.out.println("\nPassenger #" + i + " Details:");
            String name = readLine("  Full Name: ").trim();
            int age = readInt("  Age (0-125): ", 0, 125);
            String gender = readLine("  Gender (M/F/O): ").trim().toUpperCase();
            String phone = readLine("  Mobile Number (10 digits): ").trim();

            BerthType preference = promptBerthPreference();
            try {
                Passenger p = new Passenger(name, age, gender, phone, preference);
                passengers.add(p);
            } catch (Exception e) {
                System.out.println("Validation Error: " + e.getMessage());
                return;
            }
        }

        // Execute Booking
        try {
            System.out.println("\nProcessing reservation transaction...");
            Booking booking = manager.bookTicket(train.getTrainNumber(), journeyDate, selectedClass, passengers, currentUser.getUsername());
            System.out.println("\n================ BOOKING CONFIRMATION ================");
            System.out.println("PNR Number        : " + booking.getPnr());
            System.out.println("Booking ID        : " + booking.getBookingId());
            System.out.println("Train             : " + booking.getTrainNumber() + " - " + booking.getTrainName());
            System.out.println("Route             : " + booking.getSource().getCode() + " -> " + booking.getDestination().getCode());
            System.out.println("Journey Date      : " + booking.getJourneyDate());
            System.out.println("Travel Class      : " + booking.getSeatClass().getDisplayName());
            System.out.println("Total Amount Paid : ₹" + String.format("%.2f", booking.getTotalFare()));
            System.out.println("\nAllocated Passenger Tickets:");
            System.out.printf("%-14s | %-16s | %-4s | %-14s | %-12s | %-10s%n",
                    "Ticket ID", "Passenger", "Age", "Status", "Seat/Berth", "Fare");
            System.out.println("--------------------------------------------------------------------------------");
            for (Ticket t : booking.getTickets()) {
                String seatInfo = t.getSeat() != null ?
                        (t.getSeat().getCoachId() + "-" + t.getSeat().getSeatNumber() + " (" + t.getSeat().getBerthType().getLabel() + ")") :
                        (t.getStatus() == BookingStatus.RAC ? "RAC" : "WL");
                System.out.printf("%-14s | %-16s | %-4d | %-14s | %-12s | ₹%-9.2f%n",
                        t.getTicketId(), t.getPassenger().getName(), t.getPassenger().getAge(),
                        t.getStatus().getDisplayStatus(), seatInfo, t.getIndividualFare());
            }
            System.out.println("======================================================");
        } catch (RailSyncException e) {
            System.out.println("\n[BOOKING REJECTED] " + e.getMessage());
        } catch (Exception e) {
            System.out.println("\n[ERROR] An unexpected error occurred: " + e.getMessage());
        }
    }

    // 4. PNR Status Enquiry
    private void handlePnrEnquiry() {
        System.out.println("--- PNR STATUS ENQUIRY ---");
        String pnr = readLine("Enter PNR Number (e.g. RS861402): ").toUpperCase().trim();
        Booking booking;
        try {
            booking = manager.findBooking(pnr);
        } catch (InvalidPNRException e) {
            System.out.println("Error: " + e.getMessage());
            return;
        }

        System.out.println("\n================ PNR RECORD ================");
        System.out.println("PNR Number   : " + booking.getPnr());
        System.out.println("Train        : " + booking.getTrainNumber() + " - " + booking.getTrainName());
        System.out.println("Route        : " + booking.getSource().getCode() + " -> " + booking.getDestination().getCode());
        System.out.println("Date         : " + booking.getJourneyDate());
        System.out.println("Class        : " + booking.getSeatClass().getDisplayName());
        System.out.println("Booked By    : " + booking.getBookedByUserId());
        System.out.println("Booking Status: " + booking.getStatus().name());
        System.out.println("\nPassenger List:");
        for (Ticket t : booking.getTickets()) {
            String seatStr = t.getSeat() != null ?
                    ("Coach " + t.getSeat().getCoachId() + ", Berth " + t.getSeat().getSeatNumber() + " (" + t.getSeat().getBerthType() + ")") :
                    "Queue: " + t.getStatus();
            String cancelledStr = t.isCancelled() ? " [CANCELLED]" : "";
            System.out.println("  • " + t.getTicketId() + " | " + t.getPassenger().getName() + " (" +
                    t.getPassenger().getAge() + " yrs) | Status: " + t.getStatus() + cancelledStr + " | " + seatStr);
        }
        System.out.println("============================================");
    }

    // 5. Cancel Ticket
    private void handleCancelTicket() {
        System.out.println("--- CANCEL TICKET ---");
        String pnr = readLine("Enter PNR Number to cancel: ").toUpperCase().trim();
        Booking booking;
        try {
            booking = manager.findBooking(pnr);
        } catch (InvalidPNRException e) {
            System.out.println("Error: " + e.getMessage());
            return;
        }

        System.out.println("\nTickets under PNR " + pnr + ":");
        List<Ticket> tickets = booking.getTickets();
        for (int i = 0; i < tickets.size(); i++) {
            Ticket t = tickets.get(i);
            System.out.println("  " + (i + 1) + ". " + t.getTicketId() + " - " + t.getPassenger().getName() +
                    " [Status: " + t.getStatus() + (t.isCancelled() ? ", ALREADY CANCELLED" : "") + "] Fare: ₹" + t.getIndividualFare());
        }

        int choice = readInt("Select ticket number to cancel (1-" + tickets.size() + ", or 0 to abort): ", 0, tickets.size());
        if (choice <= 0) {
            System.out.println("Cancellation aborted.");
            return;
        }

        Ticket targetTicket = tickets.get(choice - 1);
        if (targetTicket.isCancelled()) {
            System.out.println("This ticket is already cancelled.");
            return;
        }

        String confirm = readLine("Are you sure you want to cancel ticket " + targetTicket.getTicketId() + " for " +
                targetTicket.getPassenger().getName() + "? (y/n): ").trim();
        if (!"y".equalsIgnoreCase(confirm) && !"yes".equalsIgnoreCase(confirm)) {
            System.out.println("Cancellation aborted.");
            return;
        }

        try {
            RefundReceipt receipt = manager.cancelTicket(currentUser, pnr, targetTicket.getTicketId());
            System.out.println("\n" + receipt.getFormattedReceipt());
        } catch (RailSyncException e) {
            System.out.println("[CANCELLATION FAILED] " + e.getMessage());
        } catch (Exception e) {
            System.out.println("[ERROR] " + e.getMessage());
        }
    }

    // 6. Check Queues
    private void handleCheckQueues() {
        System.out.println("--- RAC & WAITING LIST QUEUE STATUS ---");
        String trainNo = readLine("Enter Train Number: ").trim();
        Train train = manager.getTrain(trainNo);
        if (train == null) {
            System.out.println("Train not found.");
            return;
        }

        LocalDate journeyDate = promptJourneyDate();
        if (journeyDate == null) return;

        System.out.println("\nQueue status for Train " + train.getTrainNumber() + " on " + journeyDate + ":");
        for (SeatClass sc : train.getAvailableClasses()) {
            int racCount = 0, wlCount = 0;
            for (WaitingEntry w : manager.getWaitingListManager().getQueue(trainNo, journeyDate, sc)) {
                if (w.getQueueType() == WaitingEntry.QueueType.RAC) racCount++; else wlCount++;
            }
            int racCap = train.getRacCapacity(sc);
            int wlCap = train.getWaitingListCapacity(sc);
            int confirmedAvail = manager.getAvailableSeats(train, journeyDate, sc);

            System.out.printf("  • %-18s: Available Confirmed: %2d | RAC Queue: %2d/%2d | WL Queue: %2d/%2d%n",
                    sc.getDisplayName(), confirmedAvail, racCount, racCap, wlCount, wlCap);
        }
    }

    // 7. Calculate Fare
    private void handleCalculateFare() {
        System.out.println("--- DYNAMIC FARE & CONCESSION CALCULATOR ---");
        String trainNo = readLine("Enter Train Number (or press Enter for default 12952): ").trim();
        if (trainNo.isEmpty()) trainNo = "12952";
        Train train = manager.getTrain(trainNo);
        if (train == null) {
            System.out.println("Train not found.");
            return;
        }

        System.out.println("Select Class: 1) 1A  2) 2A  3) 3A  4) CC  5) SL");
        String classChoice = readLine("Choice (1-5): ").trim();
        SeatClass sc = SeatClass.SLEEPER;
        switch (classChoice) {
            case "1": sc = SeatClass.FIRST_AC; break;
            case "2": sc = SeatClass.SECOND_AC; break;
            case "3": sc = SeatClass.THIRD_AC; break;
            case "4": sc = SeatClass.CHAIR_CAR; break;
            case "5": sc = SeatClass.SLEEPER; break;
        }

        int age = readInt("Enter passenger age: ", 0, 125);
        String gender = readLine("Enter gender (M/F/O): ").trim().toUpperCase();

        Passenger dummy = new Passenger("EnquiryPassenger", age, gender, "9876543210", BerthType.LOWER);
        double totalFare = manager.getFareCalculator().calculateFare(train, sc, dummy);

        System.out.println("\nFare Calculation Breakdown:");
        System.out.println("  Train             : " + train.getTrainNumber() + " - " + train.getTrainName() + " (" + train.getTrainType() + ")");
        System.out.println("  Distance          : " + train.getDistanceKm() + " km");
        System.out.println("  Base Fare Rate    : ₹" + train.getBaseFareRatePerKm() + " per km");
        System.out.println("  Class Selected    : " + sc.getDisplayName() + " (Multiplier: " + sc.getFareMultiplier() + "x)");
        System.out.println("  Reservation Fee   : ₹" + sc.getReservationFee());
        System.out.println("  Applied Concession: " + dummy.getConcession().getDescription());
        System.out.println("  Total Fare        : ₹" + String.format("%.2f", totalFare));
    }

    // 8. My bookings and receipt files
    private void handleMyBookings() {
        System.out.println("--- MY BOOKINGS ---");
        List<Booking> mine = manager.getBookingsForUser(currentUser.getUsername());
        if (mine.isEmpty()) {
            System.out.println("You have no bookings yet.");
            return;
        }
        for (Booking b : mine) {
            System.out.printf("  %-9s %-6s %-12s %-3s %-10s Rs %.2f%n", b.getPnr(), b.getTrainNumber(),
                    b.getJourneyDate(), b.getSeatClass().getCode(), b.getStatus().name(), b.getTotalFare());
        }
        String pnr = readLine("Enter a PNR to show its saved receipt (or press Enter): ").trim();
        if (pnr.isEmpty()) return;
        try {
            System.out.println(FileManager.readReceipt(pnr));
        } catch (java.io.FileNotFoundException e) {
            System.out.println("No receipt file found for " + pnr + ".");
        } catch (java.io.IOException e) {
            System.out.println("Could not read receipt: " + e.getMessage());
        }
    }

    // Helper input methods
    private String readLine(String prompt) {
        System.out.print(prompt);
        if (scanner.hasNextLine()) {
            return scanner.nextLine();
        }
        return "";
    }

    private int readInt(String prompt, int min, int max) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.hasNextLine() ? scanner.nextLine().trim() : "";
            try {
                int val = Integer.parseInt(input);
                if (val >= min && val <= max) {
                    return val;
                }
                System.out.println("Please enter a number between " + min + " and " + max + ".");
            } catch (NumberFormatException e) {
                System.out.println("Invalid integer format. Please try again.");
            }
        }
    }

    private LocalDate promptJourneyDate() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        String dateStr = readLine("Enter Journey Date (YYYY-MM-DD, press Enter for " + tomorrow + "): ").trim();
        if (dateStr.isEmpty()) {
            return tomorrow;
        }
        try {
            LocalDate date = LocalDate.parse(dateStr);
            if (date.isBefore(LocalDate.now())) {
                System.out.println("Error: Journey date cannot be in the past.");
                return null;
            }
            return date;
        } catch (DateTimeParseException e) {
            System.out.println("Error: Invalid date format. Please use YYYY-MM-DD (e.g. 2026-09-15).");
            return null;
        }
    }

    private BerthType promptBerthPreference() {
        System.out.println("  Berth Preference: 1) Lower  2) Middle  3) Upper  4) Side Lower  5) Side Upper  6) Window  7) Aisle");
        String pref = readLine("  Choice (1-7, default 1): ").trim();
        switch (pref) {
            case "1": return BerthType.LOWER;
            case "2": return BerthType.MIDDLE;
            case "3": return BerthType.UPPER;
            case "4": return BerthType.SIDE_LOWER;
            case "5": return BerthType.SIDE_UPPER;
            case "6": return BerthType.WINDOW;
            case "7": return BerthType.AISLE;
            default: return BerthType.LOWER;
        }
    }
}
