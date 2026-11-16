package com.railsync.thread;

import com.railsync.AppContext;
import com.railsync.exception.*;
import com.railsync.manager.ReservationManager;
import com.railsync.model.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Demonstrates safe concurrent booking against the real database. Each run creates a temporary
 * train (unique number), lets several threads book it at the same moment, checks the database
 * afterwards, then deletes the train and its bookings again so nothing is left behind.
 *
 * <p>Thread life cycle shown in the log: NEW (created) -> WAITING (blocked at the start gate in
 * {@code wait()}) -> RUNNABLE (after {@code notifyAll()}) -> TERMINATED.</p>
 */
public class ConcurrencySimulation {

    public static class SimulationResult {
        public int totalThreads;
        public int confirmedAllocations;
        public int racAllocations;
        public int waitingListAllocations;
        public int failedAttempts;
        public boolean zeroDuplicateSeats;
        /** True when the seat table agrees with the tickets handed out. */
        public boolean databaseConsistent;
        public int seatsStillAvailable;
        public int seatsBookedInDatabase;
        public List<String> seatAssignedList = new ArrayList<>();
        public List<String> logs = new ArrayList<>();
    }

    /** General run: {@code confirmed} seats, 3 RAC and 3 waiting-list places, {@code threadCount} competing threads. */
    public static SimulationResult runSimulation(AppContext ctx, int threadCount, Consumer<String> liveLogger) {
        return run(ctx, threadCount, 4, 3, 3, liveLogger);
    }

    /** The key scenario: one seat left, two passengers book at the same time. */
    public static SimulationResult runLastSeatScenario(AppContext ctx, Consumer<String> liveLogger) {
        return run(ctx, 2, 1, 0, 2, liveLogger);
    }

    private static SimulationResult run(AppContext ctx, int threadCount, int seats, int rac, int wl,
                                        Consumer<String> liveLogger) {
        SimulationResult result = new SimulationResult();
        result.totalThreads = threadCount;
        List<String> logs = new CopyOnWriteArrayList<>();
        Consumer<String> log = msg -> {
            logs.add(msg);
            if (liveLogger != null) liveLogger.accept(msg);
        };

        ReservationManager sim = new ReservationManager(ctx.trains, ctx.stations, ctx.bookings, ctx.seats,
                ctx.waitingListManager, ctx.fareCalculator, ctx.allocationService);
        sim.setReceiptsEnabled(false);

        String trainNumber = "S" + (System.currentTimeMillis() % 100000);
        Station source = null;
        Station dest = null;
        Iterator<Station> it = ctx.stations.findAll().iterator();
        if (it.hasNext()) source = it.next();
        if (it.hasNext()) dest = it.next();
        if (source == null || dest == null) {
            log.accept("Need at least two stations in the database.");
            result.logs.addAll(logs);
            return result;
        }

        SuperfastExpress train = new SuperfastExpress(trainNumber, "Concurrency Express", source, dest,
                LocalTime.of(23, 0), LocalTime.of(23, 30), 100.0);
        train.addCoach(new Coach("C1", SeatClass.CHAIR_CAR, seats, rac, wl));
        LocalDate date = LocalDate.now().plusDays(2);

        log.accept("=== Multithreaded Booking Simulation ===");
        log.accept("Temporary train " + trainNumber + ": " + seats + " seat(s), " + rac + " RAC, " + wl + " waiting-list places.");
        ctx.trains.save(train);
        try {
            StartGate gate = new StartGate();
            List<BookingTask> tasks = new ArrayList<>();
            List<Thread> threads = new ArrayList<>();
            for (int i = 1; i <= threadCount; i++) {
                String name = "Passenger-" + (char) ('A' + (i - 1) % 26) + (i > 26 ? i : "");
                BookingTask task = new BookingTask("Thread-" + i, name, trainNumber, date, SeatClass.CHAIR_CAR, sim, log);
                task.setStartGate(gate);
                tasks.add(task);
                threads.add(new Thread(task, "BookingWorker-" + i));
            }
            log.accept("Thread state before start(): " + threads.get(0).getState());
            for (Thread t : threads) t.start();

            // Wait (briefly) until every worker is parked in wait(); this only makes the log readable,
            // correctness does not depend on it.
            long deadline = System.nanoTime() + 3_000_000_000L;
            while (System.nanoTime() < deadline && !allInState(threads, Thread.State.WAITING)) {
                Thread.yield();
            }
            log.accept("Thread state at the gate: " + threads.get(0).getState() + " - opening gate (notifyAll)");
            gate.open();

            for (Thread t : threads) {
                try {
                    t.join();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            log.accept("Thread state after join(): " + threads.get(0).getState());

            Set<String> assigned = new HashSet<>();
            boolean duplicate = false;
            int confirmedTickets = 0;
            for (BookingTask task : tasks) {
                Booking b = task.getResultBooking();
                if (b == null) {
                    result.failedAttempts++;
                    continue;
                }
                Ticket t = b.getTickets().get(0);
                switch (t.getStatus()) {
                    case CONFIRMED:
                        result.confirmedAllocations++;
                        confirmedTickets++;
                        result.seatAssignedList.add(t.getPassenger().getName() + " -> " + t.getSeatDescription());
                        if (!assigned.add(t.getSeatDescription())) {
                            duplicate = true;
                            log.accept("[ERROR] Duplicate seat assigned: " + t.getSeatDescription());
                        }
                        break;
                    case RAC:
                        result.racAllocations++;
                        result.seatAssignedList.add(t.getPassenger().getName() + " -> RAC " + t.getRacPosition());
                        break;
                    default:
                        result.waitingListAllocations++;
                        result.seatAssignedList.add(t.getPassenger().getName() + " -> WAITING " + t.getWlPosition());
                }
            }
            long trainId = ctx.trains.findDbId(trainNumber);
            result.seatsStillAvailable = ctx.seats.countAvailable(trainId, date, SeatClass.CHAIR_CAR, seats);
            result.seatsBookedInDatabase = seats - result.seatsStillAvailable;
            result.zeroDuplicateSeats = !duplicate && ctx.seats.findDoubleAllocatedSeats().isEmpty();
            result.databaseConsistent = result.zeroDuplicateSeats
                    && result.seatsBookedInDatabase == confirmedTickets
                    && result.seatsStillAvailable >= 0;

            log.accept("=== Simulation Results ===");
            log.accept("Threads: " + threadCount + " | Confirmed: " + result.confirmedAllocations + " (max " + seats + ")"
                    + " | RAC: " + result.racAllocations + " | Waiting: " + result.waitingListAllocations
                    + " | Failed: " + result.failedAttempts);
            log.accept("Database: " + result.seatsBookedInDatabase + " seat(s) booked, " + result.seatsStillAvailable + " available");
            log.accept("Thread safety check: " + (result.zeroDuplicateSeats && result.databaseConsistent
                    ? "PASSED (no duplicates, database consistent)" : "FAILED"));
        } finally {
            cleanup(ctx, trainNumber, log);
        }
        result.logs.addAll(logs);
        return result;
    }

    private static boolean allInState(List<Thread> threads, Thread.State state) {
        for (Thread t : threads) {
            if (t.getState() != state) return false;
        }
        return true;
    }

    private static void cleanup(AppContext ctx, String trainNumber, Consumer<String> log) {
        try {
            for (Booking b : ctx.bookings.findByTrain(trainNumber)) ctx.bookings.delete(b.getPnr());
            ctx.trains.delete(trainNumber);
            log.accept("Temporary train " + trainNumber + " and its bookings removed.");
        } catch (RuntimeException e) {
            log.accept("Cleanup warning: " + e.getMessage());
        }
    }
}
