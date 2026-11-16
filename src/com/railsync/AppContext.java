package com.railsync;

import com.railsync.database.DatabaseInitializer;
import com.railsync.database.DatabaseSeeder;
import com.railsync.manager.ReservationManager;
import com.railsync.manager.WaitingListManager;
import com.railsync.repository.*;
import com.railsync.service.*;
import com.railsync.thread.SeatAllocationService;

/**
 * Wires repositories and services together. The console app, the JavaFX UI, the AWT demo and the
 * tests all obtain their services from here, so none of them contains SQL or booking logic.
 */
public class AppContext {

    public final StationRepository stations = new StationRepository();
    public final TrainRepository trains = new TrainRepository(stations);
    public final UserRepository users = new UserRepository();
    public final PassengerRepository passengers = new PassengerRepository();
    public final SeatRepository seats = new SeatRepository();
    public final WaitingListRepository waitingListRepository = new WaitingListRepository();
    public final BookingRepository bookings =
            new BookingRepository(users, trains, stations, passengers, waitingListRepository);

    public final FareCalculator fareCalculator = new DynamicFareCalculator();
    public final WaitingListManager waitingListManager = new WaitingListManager(waitingListRepository, bookings, seats);
    public final SeatAllocationService allocationService = new SeatAllocationService(
            trains, stations, users, passengers, seats, bookings, waitingListManager, fareCalculator);
    public final ReservationManager reservations = new ReservationManager(
            trains, stations, bookings, seats, waitingListManager, fareCalculator, allocationService);
    public final AuthenticationService auth = new AuthenticationService(users);
    public final SearchService search = new SearchService(trains, stations, seats, bookings, waitingListRepository, fareCalculator);
    public final AdminService admin = new AdminService(auth, trains, stations, bookings, users, waitingListManager, seats);
    public final TrainService trainService = new TrainService();
    public final CollectionDemoService collectionDemo = new CollectionDemoService();

    /** Creates the schema if needed and seeds an empty database. */
    public static AppContext create(boolean seedSampleData) {
        DatabaseInitializer.initialize();
        AppContext ctx = new AppContext();
        if (seedSampleData) {
            DatabaseSeeder.seedReferenceData(ctx.stations, ctx.trains, ctx.users);
            DatabaseSeeder.seedSampleBookings(ctx.reservations, ctx.bookings.count());
        }
        return ctx;
    }
}
