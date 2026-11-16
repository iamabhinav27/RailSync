package com.railsync.javafx;

import com.railsync.AppContext;
import com.railsync.exception.RailSyncException;
import com.railsync.manager.FileManager;
import com.railsync.model.*;
import com.railsync.service.AdminService;
import com.railsync.service.AdminService.CoachPreset;
import com.railsync.service.AdminService.ReportType;
import com.railsync.service.AnalyticsService;
import com.railsync.service.ReportGenerator;
import com.railsync.thread.ConcurrencySimulation;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.io.File;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;

/**
 * Home screen for the ADMIN role. Every action goes through {@link AdminService}, which re-checks the
 * caller's role, so the UI is not the only line of defence.
 */
public class AdminDashboard {

    private final RailSyncApplication app;
    private final User admin;
    private final AppContext ctx;
    private final TabPane tabs = new TabPane();

    private final Label mTrains = new Label("-"), mBookings = new Label("-"), mConfirmed = new Label("-"),
            mRac = new Label("-"), mWaiting = new Label("-"), mCancelled = new Label("-"),
            mRevenue = new Label("-"), mOccupancy = new Label("-");
    private final Label topTrain = UIHelper.muted(""), topRoute = UIHelper.muted("");

    private final TableView<Train> trainTable = UIHelper.table("No trains.");
    private final Label trainDetail = UIHelper.muted("Select a train to see its details.");
    private final TableView<Booking> bookingTable = UIHelper.table("No bookings.");
    private final TextField bookingFilter = new TextField();
    private final TableView<User> passengerTable = UIHelper.table("No registered passengers.");
    private final TableView<WaitingEntry> waitingTable = UIHelper.table("Nobody is waiting.");
    private final TextArea reportArea = UIHelper.monoArea(false);
    private final ListView<File> savedReports = new ListView<>();
    private final TextArea simLog = UIHelper.monoArea(false);
    private List<Booking> allBookings = List.of();

    public AdminDashboard(RailSyncApplication app, User admin) {
        this.app = app;
        this.admin = admin;
        this.ctx = app.context();
    }

    public Parent build() {
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().addAll(new Tab("Overview", overviewTab()), new Tab("Trains", trainsTab()),
                new Tab("Bookings", bookingsTab()), new Tab("Passengers", passengersTab()),
                new Tab("Waiting Lists", waitingTab()), new Tab("Reports", reportsTab()),
                new Tab("Concurrency Lab", simulationTab()));
        BorderPane root = new BorderPane();
        root.setTop(new VBox(menuBar(), toolBar()));
        root.setCenter(tabs);
        root.setStyle("-fx-background-color: " + UIHelper.BG + ";");
        tabs.getSelectionModel().selectedIndexProperty().addListener((o, a, b) -> refreshAll());
        refreshAll();
        return root;
    }

    // ================================================================ chrome

    private MenuBar menuBar() {
        MenuItem addTrain = new MenuItem("Add Train...");
        addTrain.setOnAction(e -> addTrainDialog());
        MenuItem refresh = new MenuItem("Refresh All");
        refresh.setOnAction(e -> refreshAll());
        MenuItem logout = new MenuItem("Log out");
        logout.setOnAction(e -> app.logout());
        MenuItem exit = new MenuItem("Exit");
        exit.setOnAction(e -> app.stage().close());
        Menu adminMenu = new Menu("Administrator", null, addTrain, refresh, new SeparatorMenuItem(), logout, exit);

        Menu reports = new Menu("Reports");
        for (ReportType type : ReportType.values()) {
            MenuItem item = new MenuItem(type.name().replace('_', ' ').toLowerCase(Locale.ROOT));
            item.setOnAction(e -> {
                tabs.getSelectionModel().select(5);
                generateReport(type);
            });
            reports.getItems().add(item);
        }
        MenuItem sim = new MenuItem("Run Concurrency Simulation");
        sim.setOnAction(e -> tabs.getSelectionModel().select(6));

        MenuBar bar = new MenuBar(adminMenu, reports);
        bar.getMenus().addAll(app.commonMenus());
        bar.getMenus().get(2).getItems().add(0, sim);       // into "Tools"
        return bar;
    }

    private ToolBar toolBar() {
        Button refresh = UIHelper.button("Refresh", true);
        refresh.setOnAction(e -> refreshAll());
        Button add = UIHelper.button("Add Train");
        add.setOnAction(e -> addTrainDialog());
        Button report = UIHelper.button("Summary Report");
        report.setOnAction(e -> {
            tabs.getSelectionModel().select(5);
            generateReport(ReportType.SUMMARY);
        });
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button logout = UIHelper.button("Log out");
        logout.setOnAction(e -> app.logout());
        return new ToolBar(refresh, add, report, spacer, new Label(admin.getFullName()), logout);
    }

    // ================================================================ data refresh

    private void refreshAll() {
        try {
            refreshOverview();
            refreshTrains();
            refreshBookings();
            passengerTable.setItems(FXCollections.observableArrayList(ctx.admin.getAllPassengers(admin)));
            waitingTable.setItems(FXCollections.observableArrayList(ctx.admin.getWaitingLists(admin)));
            savedReports.setItems(FXCollections.observableArrayList(FileManager.listReports()));
        } catch (RailSyncException | RuntimeException e) {
            UIHelper.error("Could not refresh", e.getMessage());
        }
    }

    private void refreshOverview() {
        AnalyticsService a = ctx.admin.analytics();
        mTrains.setText(String.valueOf(a.getTotalTrains()));
        mBookings.setText(String.valueOf(a.getTotalBookings()));
        mConfirmed.setText(String.valueOf(a.getConfirmedTicketsCount()));
        mRac.setText(String.valueOf(a.getRacTicketsCount()));
        mWaiting.setText(String.valueOf(a.getWaitingListTicketsCount()));
        mCancelled.setText(String.valueOf(a.getCancelledTicketsCount()));
        mRevenue.setText(String.format("Rs %.0f", a.getTotalActiveRevenue()));
        mOccupancy.setText(String.format("%.1f%%", a.getAverageOccupancyPercentage()));
        topTrain.setText("Most occupied train:  " + a.getMostOccupiedTrain());
        topRoute.setText("Most popular route:  " + a.getMostPopularRoute());
    }

    private void refreshTrains() {
        trainTable.setItems(FXCollections.observableArrayList(ctx.trains.findAll()));
    }

    private void refreshBookings() throws RailSyncException {
        allBookings = ctx.admin.getAllBookings(admin);
        applyBookingFilter();
    }

    // ================================================================ tabs

    private Node overviewTab() {
        FlowPane tiles = new FlowPane(14, 14,
                UIHelper.metric("Trains", mTrains), UIHelper.metric("Bookings", mBookings),
                UIHelper.metric("Confirmed tickets", mConfirmed), UIHelper.metric("RAC tickets", mRac),
                UIHelper.metric("Waiting-list tickets", mWaiting), UIHelper.metric("Cancelled tickets", mCancelled),
                UIHelper.metric("Active revenue", mRevenue), UIHelper.metric("Seat occupancy", mOccupancy));
        VBox box = new VBox(16, UIHelper.logo(), UIHelper.title("Operations overview"), tiles,
                UIHelper.card(topTrain, topRoute));
        box.setPadding(new Insets(18));
        return box;
    }

    private Node trainsTab() {
        trainTable.getColumns().add(UIHelper.<Train>column("Number", Train::getTrainNumber, 80));
        trainTable.getColumns().add(UIHelper.<Train>column("Name", Train::getTrainName, 180));
        trainTable.getColumns().add(UIHelper.<Train>column("Type", Train::getTrainType, 140));
        trainTable.getColumns().add(UIHelper.<Train>column("From", t -> t.getSource().getCode(), 70));
        trainTable.getColumns().add(UIHelper.<Train>column("To", t -> t.getDestination().getCode(), 70));
        trainTable.getColumns().add(UIHelper.<Train>column("Departs", t -> t.getDepartureTime().toString(), 70));
        trainTable.getColumns().add(UIHelper.<Train>column("Arrives", t -> t.getArrivalTime().toString(), 70));
        trainTable.getColumns().add(UIHelper.<Train>column("Km", t -> String.format("%.0f", t.getDistanceKm()), 60));
        trainTable.getColumns().add(UIHelper.<Train>column("Rate/km", t -> String.format("%.2f", t.getBaseFareRatePerKm()), 80));
        trainTable.getColumns().add(UIHelper.<Train>column("Coaches", t -> String.valueOf(t.getCoaches().size()), 70));
        // dynamic dispatch: TrainService only sees the abstract Train type
        trainTable.getSelectionModel().selectedItemProperty().addListener((o, a, t) ->
                trainDetail.setText(t == null ? "Select a train to see its details." : ctx.trainService.describe(t)));

        Button add = UIHelper.button("Add train", true);
        add.setOnAction(e -> addTrainDialog());
        Button update = UIHelper.button("Update selected");
        update.setOnAction(e -> updateTrainDialog(trainTable.getSelectionModel().getSelectedItem()));
        Button delete = UIHelper.button("Delete selected");
        delete.setOnAction(e -> deleteTrain(trainTable.getSelectionModel().getSelectedItem()));
        VBox box = UIHelper.card(UIHelper.title("Train management"), trainTable, trainDetail, new HBox(10, add, update, delete));
        return wrap(box);
    }

    private Node bookingsTab() {
        bookingTable.getColumns().add(UIHelper.<Booking>column("PNR", Booking::getPnr, 90));
        bookingTable.getColumns().add(UIHelper.<Booking>column("User", Booking::getBookedByUserId, 90));
        bookingTable.getColumns().add(UIHelper.<Booking>column("Train", b -> b.getTrainNumber() + " " + b.getTrainName(), 200));
        bookingTable.getColumns().add(UIHelper.<Booking>column("Date", b -> b.getJourneyDate().toString(), 100));
        bookingTable.getColumns().add(UIHelper.<Booking>column("Class", b -> b.getSeatClass().getCode(), 55));
        bookingTable.getColumns().add(UIHelper.<Booking>column("Passengers", b -> {
            StringBuilder sb = new StringBuilder();
            for (Ticket t : b.getTickets()) sb.append(sb.length() > 0 ? ", " : "").append(t.getPassenger().getName());
            return sb.toString();
        }, 220));
        bookingTable.getColumns().add(UIHelper.<Booking>column("Status", b -> b.getStatus().name(), 100));
        bookingTable.getColumns().add(UIHelper.<Booking>column("Fare (Rs)", b -> String.format("%.2f", b.getTotalFare()), 90));
        bookingTable.setRowFactory(tv -> {
            TableRow<Booking> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    UIHelper.showText("Booking " + row.getItem().getPnr(), ReportGenerator.generateTicketText(row.getItem()));
                }
            });
            return row;
        });
        bookingFilter.setPromptText("Filter by PNR, train, user or passenger name");
        bookingFilter.textProperty().addListener((o, a, b) -> applyBookingFilter());
        return wrap(UIHelper.card(UIHelper.title("All bookings"), bookingFilter, bookingTable,
                UIHelper.muted("Double-click a booking to view its ticket.")));
    }

    private void applyBookingFilter() {
        String q = bookingFilter.getText() == null ? "" : bookingFilter.getText().trim().toLowerCase(Locale.ROOT);
        List<Booking> shown = allBookings.stream().filter(b -> q.isEmpty()
                || b.getPnr().toLowerCase().contains(q) || b.getTrainNumber().contains(q)
                || b.getBookedByUserId().toLowerCase().contains(q)
                || b.getTickets().stream().anyMatch(t -> t.getPassenger().getName().toLowerCase().contains(q))).toList();
        bookingTable.setItems(FXCollections.observableArrayList(shown));
    }

    private Node passengersTab() {
        passengerTable.getColumns().add(UIHelper.<User>column("ID", User::getUserId, 50));
        passengerTable.getColumns().add(UIHelper.<User>column("Username", User::getUsername, 120));
        passengerTable.getColumns().add(UIHelper.<User>column("Name", User::getFullName, 200));
        passengerTable.getColumns().add(UIHelper.<User>column("Email", User::getEmail, 240));
        passengerTable.getColumns().add(UIHelper.<User>column("Mobile", User::getPhone, 120));
        passengerTable.getColumns().add(UIHelper.<User>column("Bookings",
                u -> String.valueOf(ctx.bookings.findByUser(u.getUsername()).size()), 80));
        return wrap(UIHelper.card(UIHelper.title("Registered passengers"), passengerTable));
    }

    private Node waitingTab() {
        waitingTable.getColumns().add(UIHelper.<WaitingEntry>column("Queue", WaitingEntry::getLabel, 80));
        waitingTable.getColumns().add(UIHelper.<WaitingEntry>column("Train", WaitingEntry::getTrainNumber, 80));
        waitingTable.getColumns().add(UIHelper.<WaitingEntry>column("Date", e -> e.getJourneyDate().toString(), 100));
        waitingTable.getColumns().add(UIHelper.<WaitingEntry>column("Class", e -> e.getSeatClass().getCode(), 60));
        waitingTable.getColumns().add(UIHelper.<WaitingEntry>column("PNR", WaitingEntry::getPnr, 90));
        waitingTable.getColumns().add(UIHelper.<WaitingEntry>column("Passenger", WaitingEntry::getPassengerName, 200));
        Button refresh = UIHelper.button("Refresh");
        refresh.setOnAction(e -> refreshAll());
        return wrap(UIHelper.card(UIHelper.title("RAC & waiting lists"), waitingTable, refresh));
    }

    private Node reportsTab() {
        ComboBox<ReportType> type = new ComboBox<>(FXCollections.observableArrayList(ReportType.values()));
        type.getSelectionModel().selectFirst();
        Button generate = UIHelper.button("Generate", true);
        generate.setOnAction(e -> generateReport(type.getValue()));
        Button save = UIHelper.button("Save to file");
        save.setOnAction(e -> {
            if (reportArea.getText().isBlank()) {
                UIHelper.warn("Reports", "Generate a report first.");
                return;
            }
            try {
                File f = FileManager.exportReport(type.getValue().name().toLowerCase(), reportArea.getText());
                UIHelper.info("Report saved", f.getAbsolutePath());
                savedReports.setItems(FXCollections.observableArrayList(FileManager.listReports()));
            } catch (java.io.IOException ex) {
                UIHelper.error("Could not save report", ex.getMessage());
            }
        });
        savedReports.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(File f, boolean empty) {
                super.updateItem(f, empty);
                setText(empty || f == null ? null : f.getName());
            }
        });
        savedReports.setPrefWidth(300);
        Button read = UIHelper.button("Open saved report");
        read.setOnAction(e -> {
            File f = savedReports.getSelectionModel().getSelectedItem();
            if (f == null) return;
            try {
                reportArea.setText(FileManager.readReport(f.getName()));
            } catch (java.io.FileNotFoundException ex) {
                UIHelper.warn("Report not found", ex.getMessage());
            } catch (java.io.IOException ex) {
                UIHelper.error("Could not read report", ex.getMessage());
            }
        });
        HBox top = new HBox(10, new Label("Report"), type, generate, save);
        top.setAlignment(Pos.CENTER_LEFT);
        VBox right = new VBox(8, new Label("Saved reports"), savedReports, read);
        VBox.setVgrow(savedReports, Priority.ALWAYS);
        HBox body = new HBox(12, reportArea, right);
        HBox.setHgrow(reportArea, Priority.ALWAYS);
        VBox.setVgrow(body, Priority.ALWAYS);
        return wrap(UIHelper.card(UIHelper.title("Reports"), top, body));
    }

    private void generateReport(ReportType type) {
        try {
            reportArea.setText(ctx.admin.generateReport(admin, type));
        } catch (RailSyncException e) {
            UIHelper.error("Report", e.getMessage());
        }
    }

    private Node simulationTab() {
        Button lastSeat = UIHelper.button("Last seat: 2 passengers", true);
        Button many = UIHelper.button("12 competing threads");
        lastSeat.setOnAction(e -> runSimulation(true, lastSeat, many));
        many.setOnAction(e -> runSimulation(false, lastSeat, many));
        Label explain = UIHelper.muted("Creates a temporary train in the database, starts several booking threads at the same "
                + "moment (released together with notifyAll), then verifies that no seat was sold twice and the seat table is "
                + "consistent. The temporary train is removed afterwards.");
        return wrap(UIHelper.card(UIHelper.title("Concurrency lab"), explain, new HBox(10, lastSeat, many), simLog));
    }

    private void runSimulation(boolean lastSeat, Button... buttons) {
        for (Button b : buttons) b.setDisable(true);
        simLog.clear();
        Thread worker = new Thread(() -> {
            try {
                java.util.function.Consumer<String> log = line -> Platform.runLater(() -> simLog.appendText(line + "\n"));
                if (lastSeat) ConcurrencySimulation.runLastSeatScenario(ctx, log);
                else ConcurrencySimulation.runSimulation(ctx, 12, log);
            } finally {
                Platform.runLater(() -> {
                    for (Button b : buttons) b.setDisable(false);
                    refreshAll();
                });
            }
        }, "SimulationController");
        worker.setDaemon(true);
        worker.start();
    }

    private Node wrap(Node n) {
        StackPane p = new StackPane(n);
        p.setPadding(new Insets(12));
        return p;
    }

    // ================================================================ dialogs

    private void addTrainDialog() {
        TextField number = new TextField("12999");
        TextField name = new TextField("Deccan Superfast");
        ComboBox<String> type = new ComboBox<>(FXCollections.observableArrayList(TrainFactory.knownTypes()));
        type.getSelectionModel().select("Superfast Express");
        var stations = FXCollections.observableArrayList(ctx.search.getSortedStations());
        ComboBox<Station> from = new ComboBox<>(stations);
        ComboBox<Station> to = new ComboBox<>(stations);
        from.getSelectionModel().selectFirst();
        to.getSelectionModel().select(Math.min(2, stations.size() - 1));
        TextField dep = new TextField("08:00");
        TextField arr = new TextField("18:30");
        TextField dist = new TextField("850");
        ComboBox<CoachPreset> coaches = new ComboBox<>(FXCollections.observableArrayList(CoachPreset.values()));
        coaches.getSelectionModel().selectFirst();

        GridPane g = formGrid();
        g.addRow(0, new Label("Train number"), number);
        g.addRow(1, new Label("Name"), name);
        g.addRow(2, new Label("Type"), type);
        g.addRow(3, new Label("From"), from);
        g.addRow(4, new Label("To"), to);
        g.addRow(5, new Label("Departure (HH:mm)"), dep);
        g.addRow(6, new Label("Arrival (HH:mm)"), arr);
        g.addRow(7, new Label("Distance (km)"), dist);
        g.addRow(8, new Label("Coaches"), coaches);

        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle("Add train");
        d.setHeaderText("New train");
        d.getDialogPane().setContent(g);
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        if (d.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        try {
            Train t = ctx.admin.buildTrain(type.getValue(), number.getText().trim(), name.getText().trim(),
                    from.getValue(), to.getValue(), LocalTime.parse(dep.getText().trim()),
                    LocalTime.parse(arr.getText().trim()), Double.parseDouble(dist.getText().trim()), coaches.getValue());
            ctx.admin.addTrain(admin, t);
            refreshAll();
            UIHelper.info("Train added", "Train " + t.getTrainNumber() + " was added.");
        } catch (java.time.format.DateTimeParseException ex) {
            UIHelper.warn("Add train", "Times must look like 08:30.");
        } catch (NumberFormatException ex) {
            UIHelper.warn("Add train", "Distance must be a number.");
        } catch (RailSyncException ex) {
            UIHelper.error("Add train", ex.getMessage());
        }
    }

    private void updateTrainDialog(Train t) {
        if (t == null) {
            UIHelper.warn("Update train", "Select a train first.");
            return;
        }
        TextField name = new TextField(t.getTrainName());
        TextField dep = new TextField(t.getDepartureTime().toString());
        TextField arr = new TextField(t.getArrivalTime().toString());
        TextField dist = new TextField(String.format(Locale.ROOT, "%.0f", t.getDistanceKm()));
        TextField rate = new TextField(String.format(Locale.ROOT, "%.2f", t.getBaseFareRatePerKm()));
        GridPane g = formGrid();
        g.addRow(0, new Label("Name"), name);
        g.addRow(1, new Label("Departure"), dep);
        g.addRow(2, new Label("Arrival"), arr);
        g.addRow(3, new Label("Distance (km)"), dist);
        g.addRow(4, new Label("Fare rate / km"), rate);
        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle("Update train");
        d.setHeaderText("Train " + t.getTrainNumber());
        d.getDialogPane().setContent(g);
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        if (d.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        try {
            ctx.admin.updateTrain(admin, t.getTrainNumber(), name.getText().trim(), LocalTime.parse(dep.getText().trim()),
                    LocalTime.parse(arr.getText().trim()), Double.parseDouble(dist.getText().trim()),
                    Double.parseDouble(rate.getText().trim()));
            refreshAll();
        } catch (java.time.format.DateTimeParseException ex) {
            UIHelper.warn("Update train", "Times must look like 08:30.");
        } catch (NumberFormatException ex) {
            UIHelper.warn("Update train", "Distance and rate must be numbers.");
        } catch (RailSyncException ex) {
            UIHelper.error("Update train", ex.getMessage());
        }
    }

    private void deleteTrain(Train t) {
        if (t == null) {
            UIHelper.warn("Delete train", "Select a train first.");
            return;
        }
        if (!UIHelper.confirm("Delete train " + t.getTrainNumber() + "?", "This cannot be undone.")) return;
        try {
            ctx.admin.deleteTrain(admin, t.getTrainNumber());
            refreshAll();
        } catch (RailSyncException ex) {                  // e.g. dependent bookings
            UIHelper.error("Cannot delete train", ex.getMessage());
        }
    }

    private GridPane formGrid() {
        GridPane g = new GridPane();
        g.setHgap(10);
        g.setVgap(8);
        g.setPadding(new Insets(10));
        return g;
    }
}
