package com.railsync.javafx;

import com.railsync.model.SearchResult;
import com.railsync.model.SeatClass;
import com.railsync.model.Station;
import com.railsync.service.SearchService;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.function.Consumer;

/** Train search: source, destination, date, class -> table of trains with availability and fare. */
public class TrainSearchView {

    private static final String ALL_CLASSES = "All classes";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final RailSyncApplication app;
    private final Consumer<SearchResult> onBook;

    private final ComboBox<Station> cbSource = new ComboBox<>();
    private final ComboBox<Station> cbDestination = new ComboBox<>();
    private final DatePicker datePicker = new DatePicker(LocalDate.now().plusDays(1));
    private final ComboBox<String> cbClass = new ComboBox<>();
    private final ComboBox<String> cbSort = new ComboBox<>();
    private final TableView<SearchResult> table = UIHelper.table("Choose stations and a date, then press Search.");
    private final Label status = UIHelper.muted("");
    private ArrayList<SearchResult> lastResults = new ArrayList<>();

    public TrainSearchView(RailSyncApplication app, Consumer<SearchResult> onBook) {
        this.app = app;
        this.onBook = onBook;
        buildControls();
        buildTable();
    }

    private void buildControls() {
        var stations = FXCollections.observableArrayList(app.context().search.getSortedStations());
        cbSource.setItems(stations);
        cbDestination.setItems(stations);
        cbSource.setPromptText("From");
        cbDestination.setPromptText("To");
        cbSource.setPrefWidth(230);
        cbDestination.setPrefWidth(230);

        // only dates that can actually be booked (today .. +120 days)
        datePicker.setDayCellFactory(p -> new DateCell() {
            @Override
            public void updateItem(LocalDate d, boolean empty) {
                super.updateItem(d, empty);
                setDisable(empty || d.isBefore(LocalDate.now()) || d.isAfter(LocalDate.now().plusDays(120)));
            }
        });

        cbClass.getItems().add(ALL_CLASSES);
        for (SeatClass sc : SeatClass.values()) cbClass.getItems().add(sc.getCode());
        cbClass.getSelectionModel().selectFirst();

        cbSort.getItems().addAll("Sort: as found", "Sort: departure time", "Sort: fare (low to high)", "Sort: most seats");
        cbSort.getSelectionModel().selectFirst();
        cbSort.setOnAction(e -> showResults());
    }

    private void buildTable() {
        table.getColumns().add(UIHelper.<SearchResult>column("Train", r -> r.getTrain().getTrainNumber() + "  " + r.getTrain().getTrainName(), 220));
        table.getColumns().add(UIHelper.<SearchResult>column("Source", r -> r.getSource().getName(), 150));
        table.getColumns().add(UIHelper.<SearchResult>column("Destination", r -> r.getDestination().getName(), 150));
        table.getColumns().add(UIHelper.<SearchResult>column("Departs", r -> r.getDepartureTime().format(TIME), 70));
        table.getColumns().add(UIHelper.<SearchResult>column("Arrives", r -> r.getArrivalTime().format(TIME), 70));
        table.getColumns().add(UIHelper.<SearchResult>column("Class", r -> r.getSeatClass().getCode(), 60));
        table.getColumns().add(UIHelper.<SearchResult>column("Availability", SearchResult::getAvailabilityText, 100));
        table.getColumns().add(UIHelper.<SearchResult>column("Fare (Rs)", r -> String.format("%.2f", r.getFare()), 100));
        table.setRowFactory(tv -> {
            TableRow<SearchResult> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) book(row.getItem());
            });
            return row;
        });
    }

    public Node build() {
        Button swap = UIHelper.button("\u21C4");
        swap.setOnAction(e -> {
            Station s = cbSource.getValue();
            cbSource.setValue(cbDestination.getValue());
            cbDestination.setValue(s);
        });
        Button search = UIHelper.button("Search", true);
        search.setDefaultButton(true);
        search.setOnAction(e -> search());

        HBox form = new HBox(10, new Label("From"), cbSource, swap, new Label("To"), cbDestination,
                new Label("Date"), datePicker, new Label("Class"), cbClass, search);
        form.setAlignment(Pos.CENTER_LEFT);
        form.setPadding(new Insets(0, 0, 4, 0));

        Button book = UIHelper.button("Book selected train", true);
        book.setOnAction(e -> book(table.getSelectionModel().getSelectedItem()));
        book.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        HBox bottom = new HBox(12, cbSort, book, status);
        bottom.setAlignment(Pos.CENTER_LEFT);

        VBox box = UIHelper.card(UIHelper.title("Search Trains"), form, table, bottom);
        VBox.setVgrow(table, Priority.ALWAYS);
        return box;
    }

    private void search() {
        Station from = cbSource.getValue();
        Station to = cbDestination.getValue();
        if (from == null || to == null) {
            UIHelper.warn("Search", "Please select both a source and a destination station.");
            return;
        }
        SeatClass cls = ALL_CLASSES.equals(cbClass.getValue()) ? null : SeatClass.fromCode(cbClass.getValue());
        try {
            lastResults = app.context().search.searchAvailability(from, to, datePicker.getValue(), cls);
            showResults();
            status.setText(lastResults.isEmpty() ? "No trains found for this route." : lastResults.size() + " result(s). Double-click a row to book.");
        } catch (com.railsync.exception.InvalidStationException | IllegalArgumentException ex) {
            UIHelper.warn("Search", ex.getMessage());
        } catch (RuntimeException ex) {
            UIHelper.error("Search failed", ex.getMessage());
        }
    }

    private void showResults() {
        Comparator<SearchResult> cmp = null;
        switch (cbSort.getSelectionModel().getSelectedIndex()) {
            case 1: cmp = SearchService.byDepartureTime(); break;
            case 2: cmp = SearchService.byFare(); break;
            case 3: cmp = SearchService.byAvailability(); break;
            default: break;
        }
        ArrayList<SearchResult> shown = cmp == null ? lastResults : app.context().search.sortResults(lastResults, cmp);
        table.setItems(FXCollections.observableArrayList(shown));
    }

    private void book(SearchResult r) {
        if (r == null) return;
        if ("REGRET".equals(r.getAvailabilityText())) {
            UIHelper.warn("Sold out", "No confirmed, RAC or waiting-list places are left for this train and class.");
            return;
        }
        onBook.accept(r);
    }
}
