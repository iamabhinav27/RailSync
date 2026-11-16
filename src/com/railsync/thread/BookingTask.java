package com.railsync.thread;

import com.railsync.manager.ReservationManager;
import com.railsync.model.BerthType;
import com.railsync.model.Booking;
import com.railsync.model.Passenger;
import com.railsync.model.SeatClass;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * One booking request that runs on its own thread ({@code Runnable}). Several tasks started together
 * compete for the same seats; {@link SeatAllocationService} makes sure they do so safely.
 */
public class BookingTask implements Runnable {

    private final String taskName;
    private final String passengerName;
    private final String trainNumber;
    private final LocalDate journeyDate;
    private final SeatClass seatClass;
    private final ReservationManager manager;
    private final Consumer<String> eventLogger;
    private StartGate startGate;
    private volatile Booking resultBooking;
    private volatile Exception error;

    public BookingTask(String taskName, String passengerName, String trainNumber, LocalDate journeyDate,
                       SeatClass seatClass, ReservationManager manager, Consumer<String> eventLogger) {
        this.taskName = taskName;
        this.passengerName = passengerName;
        this.trainNumber = trainNumber;
        this.journeyDate = journeyDate;
        this.seatClass = seatClass;
        this.manager = manager;
        this.eventLogger = eventLogger;
    }

    /** Optional: block at this gate until the controller opens it. */
    public void setStartGate(StartGate gate) {
        this.startGate = gate;
    }

    @Override
    public void run() {
        try {
            if (startGate != null) {
                startGate.await();                       // WAITING until notifyAll()
            }
            log(taskName + " requesting a seat for " + passengerName + " on train " + trainNumber);
            List<Passenger> passengers = Collections.singletonList(
                    new Passenger(passengerName, 28, "Male", "9876543210", BerthType.LOWER));
            resultBooking = manager.bookTicket(trainNumber, journeyDate, seatClass, passengers, null);
            log(taskName + " result: PNR=" + resultBooking.getPnr() + " -> "
                    + resultBooking.getTickets().get(0).getStatusDescription()
                    + " (seat " + resultBooking.getTickets().get(0).getSeatDescription() + ")");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            error = e;
        } catch (Exception e) {
            error = e;
            log(taskName + " could not book: " + e.getMessage());
        }
    }

    private void log(String message) {
        if (eventLogger != null) eventLogger.accept(message);
    }

    public String getTaskName() { return taskName; }
    public String getPassengerName() { return passengerName; }
    public Booking getResultBooking() { return resultBooking; }
    public Exception getError() { return error; }
}
