package com.railsync;

import com.railsync.database.DatabaseConnection;

/**
 * RailSync entry point.
 *
 * <pre>
 *   (no argument)  JavaFX desktop application (falls back to the console if JavaFX/a display is missing)
 *   --cli          interactive console application
 *   --test         automated test suite (uses temporary databases)
 *   --simulate     multithreaded booking simulation (last-seat scenario + 12 threads)
 *   --demo         collections / generics demonstration on real data
 *   --awt          standalone AWT event-handling demonstration window
 * </pre>
 *
 * The database file is {@code data/railsync.db} unless overridden with {@code -Drailsync.db=PATH}
 * or the {@code RAILSYNC_DB} environment variable.
 */
public class Main {

    public static void main(String[] args) throws Exception {
        String mode = args.length > 0 ? args[0].toLowerCase() : "";

        if ("--test".equals(mode)) {
            TestRunner.main(args);
            return;
        }
        System.out.println("RailSync - Train Reservation System (SQLite: " + DatabaseConnection.getDatabasePath() + ")");

        switch (mode) {
            case "--cli":
            case "--console":
                com.railsync.cli.ConsoleApp.start(AppContext.create(true));
                return;
            case "--simulate":
                runSimulation();
                return;
            case "--demo":
                runCollectionDemo();
                return;
            case "--awt":
                com.railsync.awt.AWTEventDemo.launch(AppContext.create(true));
                return;
            default:
                launchJavaFx(args);
        }
    }

    private static void launchJavaFx(String[] args) {
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            System.out.println("[INFO] No display detected - starting the console interface.\n");
            com.railsync.cli.ConsoleApp.start(AppContext.create(true));
            return;
        }
        try {
            Class.forName("javafx.application.Application");
        } catch (ClassNotFoundException e) {
            System.err.println("[ERROR] JavaFX was not found on the module path.");
            System.err.println("        Install a JavaFX SDK and run with run.bat / run.sh (see README), or use --cli.");
            return;
        }
        com.railsync.javafx.RailSyncApplication.launchApp(args);
    }

    private static void runSimulation() {
        AppContext ctx = AppContext.create(true);
        System.out.println("\n--- Scenario 1: only one seat left, two passengers book at the same time ---");
        com.railsync.thread.ConcurrencySimulation.runLastSeatScenario(ctx, System.out::println);
        System.out.println("\n--- Scenario 2: 12 threads, 4 seats + 3 RAC + 3 waiting-list places ---");
        com.railsync.thread.ConcurrencySimulation.runSimulation(ctx, 12, System.out::println);
    }

    private static void runCollectionDemo() {
        AppContext ctx = AppContext.create(true);
        ctx.collectionDemo.printDemo(ctx.trains.findAll(), ctx.stations.findAll(), ctx.bookings.findAll());
        for (com.railsync.model.Train t : ctx.trainService.byPriority(ctx.trains.findAll())) {
            System.out.println("Polymorphism: " + ctx.trainService.describe(t));
        }
    }
}
