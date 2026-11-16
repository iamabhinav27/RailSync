package com.railsync;

import com.railsync.database.DatabaseConnection;
import com.railsync.database.DatabaseSeeder;
import com.railsync.exception.*;
import com.railsync.manager.FileManager;
import com.railsync.model.*;
import com.railsync.service.*;
import com.railsync.thread.ConcurrencySimulation;
import com.railsync.util.GenericUtils;
import com.railsync.util.PasswordHasher;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/**
 * Automated test suite. Every test runs against its own empty temporary SQLite file, so the real
 * {@code data/railsync.db} is never touched. Run with {@code test.bat} / {@code ./test.sh}.
 */
public class TestRunner {

    private static int passed = 0;
    private static int failed = 0;
    private static Path tempDir;
    private static int dbCounter = 0;

    public static void main(String[] args) throws Exception {
        tempDir = Files.createTempDirectory("railsync-test");
        System.out.println("==========================================");
        System.out.println("      RailSync Automated Test Suite       ");
        System.out.println("==========================================\n");

        run("Auth: register, salted hash, login", TestRunner::testAuth);
        run("Auth: invalid login & role enforcement", TestRunner::testInvalidLoginAndRoles);
        run("Booking: confirmed seat, PNR, receipt file", TestRunner::testBooking);
        run("Booking: berth preference & per-date seats", TestRunner::testPerDateSeats);
        run("Booking: RAC then waiting list then regret", TestRunner::testQueues);
        run("Booking: failed booking rolls back", TestRunner::testRollback);
        run("Cancel: refund + RAC/WL promotion cascade", TestRunner::testCancellationAndPromotion);
        run("Cancel: waiting list takes seat when RAC=0", TestRunner::testWaitingPromotedWithoutRac);
        run("Cancel: restrictions & invalid PNR", TestRunner::testCancellationRules);
        run("Fare: overloads & concessions", TestRunner::testFare);
        run("Search: availability, sorting, validation", TestRunner::testSearch);
        run("Concurrency: last seat, two threads", TestRunner::testLastSeat);
        run("Concurrency: 12 threads, DB consistent", TestRunner::testManyThreads);
        run("Persistence: close & reopen keeps data", TestRunner::testPersistence);
        run("Admin: train CRUD, delete blocked by bookings", TestRunner::testAdminTrains);
        run("Files: byte streams, receipts, reports", TestRunner::testFiles);
        run("Collections & generics", TestRunner::testCollectionsAndGenerics);

        System.out.println("\n==========================================");
        System.out.println("Result: " + passed + " passed, " + failed + " failed (Total: " + (passed + failed) + ")");
        System.out.println("==========================================");
        if (failed > 0) System.exit(1);
    }

    // ---------------------------------------------------------------- harness

    interface TestCase { void run() throws Exception; }

    private static void run(String name, TestCase test) {
        System.out.printf("%-52s : ", name);
        try {
            freshDatabase();
            test.run();
            System.out.println("[PASS]");
            passed++;
        } catch (Throwable t) {
            System.out.println("[FAIL] -> " + t);
            failed++;
        }
    }

    private static void freshDatabase() {
        Path dir = tempDir.resolve("t" + (++dbCounter));
        DatabaseConnection.setDatabasePath(dir.resolve("railsync.db").toString());
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void equal(Object expected, Object actual, String message) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(message + " (expected: " + expected + ", actual: " + actual + ")");
        }
    }

    private static <T extends Throwable> void expect(Class<T> type, String what, TestCase action) throws Exception {
        try {
            action.run();
        } catch (Throwable t) {
            if (type.isInstance(t)) return;
            throw new AssertionError(what + ": expected " + type.getSimpleName() + " but got " + t);
        }
        throw new AssertionError(what + ": expected " + type.getSimpleName() + " but nothing was thrown");
    }

    /** Context with two stations and one small train (seats / RAC / waiting list). */
    private static AppContext smallTrain(String number, int seats, int rac, int wl) {
        AppContext ctx = AppContext.create(false);
        Station a = new Station("AAA", "Alpha", "Alpha", "S", "NR", 0);
        Station b = new Station("BBB", "Beta", "Beta", "S", "NR", 500);
        ctx.stations.save(a);
        ctx.stations.save(b);
        SuperfastExpress t = new SuperfastExpress(number, "Mini Express", a, b, LocalTime.of(10, 0), LocalTime.of(20, 0), 500);
        t.addCoach(new Coach("C1", SeatClass.CHAIR_CAR, seats, rac, wl));
        ctx.trains.save(t);
        return ctx;
    }

    private static Booking book(AppContext ctx, String train, LocalDate date, String name, String user) throws Exception {
        return ctx.reservations.bookTicket(train, date, SeatClass.CHAIR_CAR,
                Collections.singletonList(new Passenger(name, 30, "Male", "9876543210", BerthType.WINDOW)), user);
    }

    private static LocalDate day(int n) { return LocalDate.now().plusDays(n); }

    // ---------------------------------------------------------------- tests

    private static void testAuth() throws Exception {
        AppContext ctx = AppContext.create(false);
        User u = ctx.auth.register("Asha_01", "Asha Rao", "asha@example.com", "9876543210", "secret99");
        equal("asha_01", u.getUsername(), "username is normalised");
        equal(User.Role.PASSENGER, u.getRole(), "new accounts are passengers");
        check(u instanceof PassengerAccount, "PASSENGER role uses PassengerAccount subclass");
        check(!u.getPasswordHash().contains("secret99"), "plaintext password must not be stored");
        check(u.getPasswordHash().startsWith("pbkdf2_sha256$"), "hash format");
        // stored value in the database is the hash too
        try (Connection c = DatabaseConnection.getConnection(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT password_hash FROM users WHERE username='asha_01'")) {
            rs.next();
            check(!rs.getString(1).equals("secret99"), "database holds a hash");
        }
        String h1 = PasswordHasher.hash("same-password1");
        String h2 = PasswordHasher.hash("same-password1");
        check(!h1.equals(h2), "same password gets different salts");
        User in = ctx.auth.login("ASHA_01", "secret99");
        equal(u.getUsername(), in.getUsername(), "login works (case-insensitive username)");
        expect(InvalidLoginException.class, "duplicate username", () ->
                ctx.auth.register("asha_01", "Other Person", "o@example.com", "", "secret99"));
    }

    private static void testInvalidLoginAndRoles() throws Exception {
        AppContext ctx = AppContext.create(true);
        expect(InvalidLoginException.class, "blank username", () -> ctx.auth.login("", "x"));
        expect(InvalidLoginException.class, "blank password", () -> ctx.auth.login("admin", ""));
        expect(InvalidLoginException.class, "wrong password", () -> ctx.auth.login("admin", "nope"));
        expect(InvalidLoginException.class, "unknown user", () -> ctx.auth.login("ghost", "pass123"));
        expect(InvalidLoginException.class, "weak password", () ->
                ctx.auth.register("newuser", "New User", "n@example.com", "", "abc"));
        expect(InvalidLoginException.class, "bad email", () ->
                ctx.auth.register("newuser", "New User", "not-an-email", "", "abc12345"));
        // SQL-injection style input is just a wrong login
        expect(InvalidLoginException.class, "injection attempt", () -> ctx.auth.login("admin' OR '1'='1", "x"));

        User admin = ctx.auth.login("admin", "admin123");
        User passenger = ctx.auth.login("passenger", "pass123");
        check(admin instanceof Admin && admin.isAdmin(), "admin account is an Admin");
        expect(AccessDeniedException.class, "passenger adds train", () ->
                ctx.admin.addTrain(passenger, new ExpressTrain("99001", "X", ctx.stations.findById("NDLS"),
                        ctx.stations.findById("BCT"), LocalTime.NOON, LocalTime.MIDNIGHT, 100)));
        expect(AccessDeniedException.class, "passenger lists bookings", () -> ctx.admin.getAllBookings(passenger));
        expect(AccessDeniedException.class, "passenger reports", () ->
                ctx.admin.generateReport(passenger, AdminService.ReportType.SUMMARY));
        expect(AccessDeniedException.class, "null actor", () -> ctx.admin.getAllPassengers(null));
        check(!ctx.admin.getAllBookings(admin).isEmpty(), "admin sees seeded bookings");
    }

    private static void testBooking() throws Exception {
        AppContext ctx = smallTrain("T100", 3, 1, 1);
        Booking b = book(ctx, "T100", day(3), "Aditya Sharma", null);
        check(b.getPnr().startsWith("RS"), "PNR prefix");
        Ticket t = b.getTickets().get(0);
        equal(BookingStatus.CONFIRMED, t.getStatus(), "first booking confirmed");
        check(t.getSeat() != null && t.getSeat().getSeatId() > 0, "seat allocated");
        Booking loaded = ctx.reservations.findBooking(b.getPnr());
        equal(b.getPnr(), loaded.getPnr(), "found by PNR");
        equal(BookingStatus.CONFIRMED, loaded.getStatus(), "stored status");
        equal(t.getSeatDescription(), loaded.getTickets().get(0).getSeatDescription(), "seat persisted");
        File receipt = new File(FileManager.receiptsDir(), b.getPnr() + ".txt");
        check(receipt.isFile(), "receipt file written: " + receipt);
        String text = FileManager.readReceipt(b.getPnr());
        check(text.contains(b.getPnr()) && text.contains("Aditya Sharma") && text.contains("CONFIRMED")
                && text.contains("T100") && text.contains("Mini Express"), "receipt content");
    }

    private static void testPerDateSeats() throws Exception {
        AppContext ctx = smallTrain("T101", 1, 0, 0);
        Booking d1 = book(ctx, "T101", day(2), "Date One", null);
        Booking d2 = book(ctx, "T101", day(3), "Date Two", null);   // same seat number, different date
        equal(BookingStatus.CONFIRMED, d1.getStatus(), "day 2 confirmed");
        equal(BookingStatus.CONFIRMED, d2.getStatus(), "day 3 confirmed (separate inventory)");
        expect(SeatUnavailableException.class, "day 2 is now full", () -> book(ctx, "T101", day(2), "Date Three", null));

        // berth preference honoured when available
        freshDatabase();
        AppContext ctx2 = AppContext.create(true);
        Passenger p = new Passenger("Upper Seeker", 30, "Male", "9876543210", BerthType.UPPER);
        Booking b = ctx2.reservations.bookTicket("12626", day(10), SeatClass.SLEEPER, List.of(p), "passenger");
        equal(BerthType.UPPER, b.getTickets().get(0).getSeat().getBerthType(), "preferred berth type");
    }

    private static void testQueues() throws Exception {
        AppContext ctx = smallTrain("T102", 1, 2, 1);
        LocalDate d = day(4);
        equal(BookingStatus.CONFIRMED, book(ctx, "T102", d, "P One", null).getStatus(), "CNF");
        Booking r1 = book(ctx, "T102", d, "P Two", null);
        Booking r2 = book(ctx, "T102", d, "P Three", null);
        equal(BookingStatus.RAC, r1.getStatus(), "RAC 1");
        equal(1, r1.getTickets().get(0).getRacPosition(), "RAC position 1");
        equal(2, r2.getTickets().get(0).getRacPosition(), "RAC position 2");
        Booking w = book(ctx, "T102", d, "P Four", null);
        equal(BookingStatus.WAITING, w.getStatus(), "waiting list");
        equal(1, w.getTickets().get(0).getWlPosition(), "WL 1");
        expect(SeatUnavailableException.class, "all exhausted", () -> book(ctx, "T102", d, "P Five", null));
        equal(4L, ctx.bookings.count(), "rejected request left no booking behind");
    }

    private static void testRollback() throws Exception {
        AppContext ctx = smallTrain("T103", 1, 0, 0);
        LocalDate d = day(2);
        List<Passenger> two = List.of(new Passenger("Pax One", 30, "Male", "9876543210", BerthType.LOWER),
                new Passenger("Pax Two", 31, "Male", "9876543210", BerthType.LOWER));
        expect(SeatUnavailableException.class, "second passenger cannot be placed", () ->
                ctx.reservations.bookTicket("T103", d, SeatClass.CHAIR_CAR, two, null));
        equal(0L, ctx.bookings.count(), "no partial booking stored");
        equal(1, ctx.reservations.getAvailableSeats(ctx.trains.findById("T103"), d, SeatClass.CHAIR_CAR),
                "seat was released by the rollback");
        equal(BookingStatus.CONFIRMED, book(ctx, "T103", d, "Real Pax", null).getStatus(), "seat still bookable");
    }

    private static void testCancellationAndPromotion() throws Exception {
        AppContext ctx = smallTrain("T104", 1, 1, 1);
        LocalDate d = day(5);
        Booking b1 = book(ctx, "T104", d, "Confirmed Pass", null);
        Booking b2 = book(ctx, "T104", d, "RAC Pass", null);
        Booking b3 = book(ctx, "T104", d, "WL Pass", null);
        RefundReceipt receipt = ctx.reservations.cancelTicket(b1.getPnr(), b1.getTickets().get(0).getTicketId());
        check(receipt.getNetRefundAmount() > 0, "refund > 0");
        check(receipt.getReason().contains("PROMOTED"), "promotion reported");

        Booking a1 = ctx.reservations.findBooking(b1.getPnr());
        Booking a2 = ctx.reservations.findBooking(b2.getPnr());
        Booking a3 = ctx.reservations.findBooking(b3.getPnr());
        equal(BookingStatus.CANCELLED, a1.getStatus(), "booking status CANCELLED");
        equal(BookingStatus.CONFIRMED, a2.getStatus(), "RAC promoted to CONFIRMED");
        check(a2.getTickets().get(0).getSeat() != null, "promoted passenger holds the seat");
        equal(BookingStatus.RAC, a3.getStatus(), "WL promoted to RAC");
        equal(1, a3.getTickets().get(0).getRacPosition(), "now RAC 1");
        check(ctx.seats.findDoubleAllocatedSeats().isEmpty(), "no seat double-allocated");
        check(FileManager.readReceipt(b1.getPnr()).contains("CANCELLED"), "receipt file updated");
        equal(0, ctx.reservations.getAvailableSeats(ctx.trains.findById("T104"), d, SeatClass.CHAIR_CAR),
                "seat is not free: it was handed to the promoted passenger");

        // cancel the now-confirmed passenger: seat goes to RAC 1, then nobody left -> released later
        ctx.reservations.cancelTicket(b2.getPnr(), a2.getTickets().get(0).getTicketId());
        equal(BookingStatus.CONFIRMED, ctx.reservations.findBooking(b3.getPnr()).getStatus(), "RAC -> CONFIRMED");
        ctx.reservations.cancelTicket(b3.getPnr(), a3.getTickets().get(0).getTicketId());
        equal(1, ctx.reservations.getAvailableSeats(ctx.trains.findById("T104"), d, SeatClass.CHAIR_CAR),
                "seat returns to inventory");
    }

    private static void testWaitingPromotedWithoutRac() throws Exception {
        AppContext ctx = smallTrain("T105", 1, 0, 2);
        LocalDate d = day(5);
        Booking a = book(ctx, "T105", d, "Passenger A", null);
        Booking b = book(ctx, "T105", d, "Passenger B", null);
        Booking c = book(ctx, "T105", d, "Passenger C", null);
        equal(BookingStatus.WAITING, b.getStatus(), "B waiting");
        equal(2, c.getTickets().get(0).getWlPosition(), "C is WL 2");
        ctx.reservations.cancelTicket(a.getPnr(), a.getTickets().get(0).getTicketId());
        Booking nowB = ctx.reservations.findBooking(b.getPnr());
        Booking nowC = ctx.reservations.findBooking(c.getPnr());
        equal(BookingStatus.CONFIRMED, nowB.getStatus(), "earliest waiting passenger promoted to CONFIRMED");
        check(nowB.getTickets().get(0).getSeat() != null, "B holds the seat");
        equal(BookingStatus.WAITING, nowC.getStatus(), "C still waiting");
        equal(1, nowC.getTickets().get(0).getWlPosition(), "C moved up to WL 1");
        check(ctx.seats.findDoubleAllocatedSeats().isEmpty(), "no double allocation");
    }

    private static void testCancellationRules() throws Exception {
        AppContext ctx = smallTrain("T106", 2, 0, 1);
        LocalDate d = day(5);
        User owner = ctx.auth.register("owner1", "Owner One", "o@example.com", "", "abc12345");
        User other = ctx.auth.register("other1", "Other One", "x@example.com", "", "abc12345");
        Booking mine = ctx.reservations.bookTicket("T106", d, SeatClass.CHAIR_CAR,
                List.of(new Passenger("Owner Pax", 30, "Male", "9876543210", BerthType.WINDOW)), "owner1");

        expect(InvalidPNRException.class, "blank PNR", () -> ctx.reservations.findBooking("  "));
        expect(InvalidPNRException.class, "malformed PNR", () -> ctx.reservations.findBooking("XYZ"));
        expect(InvalidPNRException.class, "unknown PNR", () -> ctx.reservations.findBooking("RS999999"));
        expect(CancellationNotAllowedException.class, "someone else's booking", () ->
                ctx.reservations.cancelBooking(other, mine.getPnr()));
        expect(CancellationNotAllowedException.class, "unknown ticket id", () ->
                ctx.reservations.cancelTicket(mine.getPnr(), "TK-NOPE"));
        expect(CancellationNotAllowedException.class, "after departure", () ->
                ctx.allocationService.cancel(mine.getPnr(), null, LocalDateTime.now().plusDays(30)));
        equal(BookingStatus.CONFIRMED, ctx.reservations.findBooking(mine.getPnr()).getStatus(),
                "failed cancellations changed nothing");

        List<RefundReceipt> receipts = ctx.reservations.cancelBooking(owner, mine.getPnr());
        equal(1, receipts.size(), "one receipt");
        equal(BookingStatus.CANCELLED, ctx.reservations.findBooking(mine.getPnr()).getStatus(), "cancelled");
        expect(CancellationNotAllowedException.class, "already cancelled", () ->
                ctx.reservations.cancelBooking(owner, mine.getPnr()));
    }

    private static void testFare() throws Exception {
        AppContext ctx = AppContext.create(true);
        Train rajdhani = ctx.trains.findById("12952");
        FareCalculator fc = ctx.fareCalculator;
        Passenger adult = new Passenger("Adult", 30, "Male", "9876543210", BerthType.LOWER);
        Passenger senior = new Passenger("Senior", 65, "Male", "9876543210", BerthType.LOWER);
        Passenger infant = new Passenger("Infant", 2, "Female", "9876543210", BerthType.LOWER);
        double a = fc.calculateFare(rajdhani, SeatClass.SECOND_AC, adult);
        check(a > fc.calculateFare(rajdhani, SeatClass.SECOND_AC, senior), "senior discount");
        equal(0.0, fc.calculateFare(rajdhani, SeatClass.SECOND_AC, infant), "infant free");
        Station ndls = ctx.stations.findById("NDLS"), bpl = ctx.stations.findById("BPL");
        double partial = fc.calculateFare(rajdhani, SeatClass.SECOND_AC, adult, ndls, bpl);
        check(partial < a, "shorter segment costs less");
        equal(fc.calculateFare(rajdhani, SeatClass.SECOND_AC, adult, rajdhani.getDistanceKm()), a, "distance overload");
        equal(a + a, fc.calculateFare(rajdhani, SeatClass.SECOND_AC, List.of(adult, adult)), "group overload");
        TrainService ts = new TrainService();
        check(ts.describe(rajdhani).contains("Rajdhani"), "dynamic dispatch description");
    }

    private static void testSearch() throws Exception {
        AppContext ctx = AppContext.create(true);
        Station ndls = ctx.stations.findById("NDLS"), bct = ctx.stations.findById("BCT");
        ArrayList<SearchResult> r = ctx.search.searchAvailability(ndls, bct, day(9), null);
        check(!r.isEmpty(), "Delhi -> Mumbai has results");
        for (SearchResult x : r) check(x.getTrain().getTrainNumber().equals("12952"), "only the Mumbai train");
        ArrayList<SearchResult> only3A = ctx.search.searchAvailability("NDLS", "BCT", day(9), SeatClass.THIRD_AC);
        equal(1, only3A.size(), "class filter");
        equal(96, only3A.get(0).getAvailableSeats(), "3A availability = 2 coaches x 48");
        ArrayList<SearchResult> byFare = ctx.search.sortResults(r, SearchService.byFare());
        for (int i = 1; i < byFare.size(); i++) check(byFare.get(i - 1).getFare() <= byFare.get(i).getFare(), "sorted by fare");
        ArrayList<SearchResult> byDep = ctx.search.sortResults(r, SearchService.byDepartureTime());
        check(byDep.size() == r.size(), "sorted by departure");
        expect(InvalidStationException.class, "same station", () -> ctx.search.searchAvailability(ndls, ndls, day(9), null));
        expect(InvalidStationException.class, "unknown code", () -> ctx.search.searchAvailability("NDLS", "ZZZZ", day(9), null));
        expect(InvalidStationException.class, "null station", () -> ctx.search.searchAvailability(null, bct, day(9), null));
        expect(IllegalArgumentException.class, "past date", () -> ctx.search.searchAvailability(ndls, bct, day(-2), null));
        // availability drops after a booking
        ctx.reservations.bookTicket("12952", day(9), SeatClass.THIRD_AC,
                List.of(new Passenger("Search Pax", 30, "Male", "9876543210", BerthType.LOWER)), "passenger");
        equal(95, ctx.search.searchAvailability(ndls, bct, day(9), SeatClass.THIRD_AC).get(0).getAvailableSeats(),
                "availability decremented");
        // intermediate station search
        check(!ctx.search.searchAvailability("NDLS", "BPL", day(9), null).isEmpty(), "intermediate stop search");
        check(ctx.search.searchAvailability("BCT", "NDLS", day(9), null).stream()
                .noneMatch(x -> x.getTrain().getTrainNumber().equals("12952")), "direction matters");
    }

    private static void testLastSeat() throws Exception {
        AppContext ctx = AppContext.create(true);
        for (int round = 0; round < 5; round++) {                    // repeat: races are not deterministic
            ConcurrencySimulation.SimulationResult r = ConcurrencySimulation.runLastSeatScenario(ctx, null);
            equal(1, r.confirmedAllocations, "exactly one passenger gets the seat");
            equal(1, r.waitingListAllocations, "the other is waiting-listed");
            equal(0, r.failedAttempts, "no request failed");
            equal(0, r.seatsStillAvailable, "no seat left in the database");
            check(r.databaseConsistent && r.zeroDuplicateSeats, "database consistent");
        }
        equal(3L, ctx.bookings.count(), "simulation cleaned up its bookings (only the 3 seeded remain)");
    }

    private static void testManyThreads() throws Exception {
        AppContext ctx = AppContext.create(true);
        ConcurrencySimulation.SimulationResult r = ConcurrencySimulation.runSimulation(ctx, 12, null);
        equal(4, r.confirmedAllocations, "4 confirmed");
        equal(3, r.racAllocations, "3 RAC");
        equal(3, r.waitingListAllocations, "3 waiting");
        equal(2, r.failedAttempts, "2 regretted");
        check(r.zeroDuplicateSeats && r.databaseConsistent, "no duplicates, consistent database");
    }

    private static void testPersistence() throws Exception {
        AppContext first = AppContext.create(true);
        first.auth.register("keeper", "Keeper Kumar", "k@example.com", "9876543210", "keep1234");
        Booking b = first.reservations.bookTicket("22436", day(6), SeatClass.CHAIR_CAR,
                List.of(new Passenger("Kept Traveller", 40, "Male", "9876543210", BerthType.WINDOW)), "keeper");
        String pnr = b.getPnr();
        first = null;                                                 // "close the application"

        AppContext second = AppContext.create(true);                  // "reopen": new objects, same file
        User again = second.auth.login("keeper", "keep1234");
        equal("Keeper Kumar", again.getFullName(), "passenger survived restart");
        Booking restored = second.reservations.findBooking(pnr);
        equal("Kept Traveller", restored.getTickets().get(0).getPassenger().getName(), "booking survived restart");
        equal(1, second.reservations.getBookingsForUser("keeper").size(), "history survived");
        equal(4L, second.bookings.count(), "seed data not duplicated on restart (3 seeded + 1)");
    }

    private static void testAdminTrains() throws Exception {
        AppContext ctx = AppContext.create(true);
        User admin = ctx.auth.login("admin", "admin123");
        Station a = ctx.stations.findById("NDLS"), b = ctx.stations.findById("AGC");
        Train t = ctx.admin.buildTrain("Superfast Express", "12999", "Deccan Superfast", a, b,
                LocalTime.of(8, 0), LocalTime.of(12, 0), 200, AdminService.CoachPreset.CHAIR_CAR);
        ctx.admin.addTrain(admin, t);
        check(ctx.trains.findById("12999") != null, "train inserted");
        expect(InvalidTrainException.class, "duplicate number", () -> ctx.admin.addTrain(admin, t));
        expect(InvalidTrainException.class, "same stations", () ->
                ctx.admin.buildTrain("Mail/Express", "12998", "Bad", a, a, LocalTime.NOON, LocalTime.MIDNIGHT, 10, null));
        Train upd = ctx.admin.updateTrain(admin, "12999", "Deccan Queen", LocalTime.of(9, 0), LocalTime.of(13, 0), 210, 0.9);
        equal("Deccan Queen", ctx.trains.findById("12999").getTrainName(), "name updated");
        equal(LocalTime.of(9, 0), ctx.trains.findById("12999").getDepartureTime(), "departure updated");
        equal(0.9, ctx.trains.findById("12999").getBaseFareRatePerKm(), "rate updated");
        check(upd.getCoaches().size() == 2, "coaches kept on update");
        ctx.admin.deleteTrain(admin, "12999");
        check(ctx.trains.findById("12999") == null, "train without bookings deleted");
        expect(InvalidTrainException.class, "train with bookings", () -> ctx.admin.deleteTrain(admin, "12952"));
        check(ctx.trains.findById("12952") != null, "train with bookings still exists");
        expect(TrainNotFoundException.class, "missing train", () -> ctx.admin.deleteTrain(admin, "00000"));
        Station dup = new Station("NDLS", "Dup", "Dup", "S", "NR", 1);
        expect(InvalidStationException.class, "duplicate station code", () -> ctx.admin.addStation(admin, dup));
        check(ctx.admin.getWaitingLists(admin) != null, "admin can view waiting lists");
        check(ctx.admin.getAllPassengers(admin).size() >= 2, "admin can view passengers");
    }

    private static void testFiles() throws Exception {
        AppContext ctx = AppContext.create(true);
        User admin = ctx.auth.login("admin", "admin123");
        for (AdminService.ReportType type : AdminService.ReportType.values()) {
            String report = ctx.admin.generateReport(admin, type);
            File f = FileManager.exportReport("test_" + type, report);
            equal(report, FileManager.readTextFile(f), "report round-trips through byte streams: " + type);
        }
        check(FileManager.listReports().size() == 4, "four reports listed");
        File copy = new File(FileManager.reportsDir(), "copy.bin");
        byte[] data = new byte[10000];
        new Random(1).nextBytes(data);
        FileManager.writeBytes(copy, data);
        check(Arrays.equals(data, FileManager.readBytes(copy)), "binary bytes preserved");
        File dup = new File(FileManager.reportsDir(), "copy2.bin");
        FileManager.copyFile(copy, dup);
        check(Arrays.equals(data, FileManager.readBytes(dup)), "copyFile");
        expect(FileNotFoundException.class, "missing file", () -> FileManager.readBytes(new File("no/such/file.txt")));
        expect(FileNotFoundException.class, "missing receipt", () -> FileManager.readReceipt("RS000000"));
        expect(FileNotFoundException.class, "path traversal blocked", () -> FileManager.readReport("../railsync.db"));
    }

    private static void testCollectionsAndGenerics() throws Exception {
        AppContext ctx = AppContext.create(true);
        Collection<Train> trains = ctx.trains.findAll();
        Collection<Booking> bookings = ctx.bookings.findAll();
        CollectionDemoService c = ctx.collectionDemo;
        TreeSet<String> numbers = c.sortedTrainNumbers(trains);
        equal("12002", numbers.first(), "TreeSet sorted");
        equal(bookings.size(), c.indexByPnr(bookings).size(), "HashMap by PNR");
        List<Station> withDup = new ArrayList<>(ctx.stations.findAll());
        withDup.add(ctx.stations.findById("NDLS"));
        equal(Set.of("NDLS"), c.findDuplicateStationCodes(withDup), "HashSet finds duplicates");
        Booking any = bookings.iterator().next();
        ctx.reservations.cancelBooking(ctx.users.findById(any.getBookedByUserId()), any.getPnr());
        equal(bookings.size() - 1, c.activeBookings(ctx.bookings.findAll()).size(), "Iterator removes cancelled");
        check(ctx.search.getSortedStations().first().getName().compareToIgnoreCase("Agra Cantt") == 0, "sorted stations");
        equal(5.0, GenericUtils.sum(List.of(2, 3)), "? extends Number");
        List<Object> sink = new ArrayList<>();
        equal(2, GenericUtils.copyInto(List.of("a", "b"), sink), "? super T");
        equal("b", GenericUtils.maxOf(List.of("a", "b")).get(), "bounded generic method");
        equal(2, GenericUtils.groupBy(List.of("aa", "bb", "c"), String::length).size(), "groupBy");
        DatabaseSeeder.seedReferenceData(ctx.stations, ctx.trains, ctx.users);   // idempotent
        equal(trains.size(), ctx.trains.findAll().size(), "seeding twice adds nothing");
    }
}
