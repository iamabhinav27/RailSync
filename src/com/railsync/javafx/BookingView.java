package com.railsync.javafx;

import com.railsync.exception.RailSyncException;
import com.railsync.manager.FileManager;
import com.railsync.model.*;
import com.railsync.service.ReportGenerator;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

/** Passenger details, berth preference, live fare, and booking confirmation for one chosen train. */
public class BookingView {

    private final RailSyncApplication app;
    private final User user;
    private final SearchResult result;
    private final Runnable onBooked;
    private final Runnable onBack;

    private final ObservableList<Passenger> passengers = FXCollections.observableArrayList();
    private final TableView<Passenger> table = UIHelper.table("Add at least one passenger.");
    private final TextField name = new TextField();
    private final TextField age = new TextField();
    private final ComboBox<String> gender = new ComboBox<>(FXCollections.observableArrayList("Male", "Female", "Other"));
    private final TextField phone = new TextField();
    private final ComboBox<BerthType> berth = new ComboBox<>(FXCollections.observableArrayList(BerthType.values()));
    private final Label totalFare = new Label("Total fare: Rs 0.00");

    public BookingView(RailSyncApplication app, User user, SearchResult result, Runnable onBooked, Runnable onBack) {
        this.app = app;
        this.user = user;
        this.result = result;
        this.onBooked = onBooked;
        this.onBack = onBack;
    }

    public Node build() {
        Train t = result.getTrain();
        Label summary = new Label(String.format("%s %s  |  %s -> %s  |  %s  |  %s  |  availability: %s",
                t.getTrainNumber(), t.getTrainName(), result.getSource().getName(), result.getDestination().getName(),
                result.getJourneyDate(), result.getSeatClass().getDisplayName(), result.getAvailabilityText()));
        summary.setStyle("-fx-font-weight: bold;");
        summary.setWrapText(true);

        gender.getSelectionModel().selectFirst();
        berth.getSelectionModel().selectFirst();
        name.setPromptText("Full name");
        age.setPromptText("Age");
        age.setPrefWidth(70);
        phone.setPromptText("Mobile (10 digits)");
        if (!user.getPhone().isEmpty()) phone.setText(user.getPhone());

        Button add = UIHelper.button("Add passenger");
        add.setOnAction(e -> addPassenger());
        HBox form = new HBox(8, name, age, gender, phone, berth, add);
        form.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(name, Priority.ALWAYS);

        table.setItems(passengers);
        table.getColumns().add(UIHelper.<Passenger>column("Name", Passenger::getName, 180));
        table.getColumns().add(UIHelper.<Passenger>column("Age", p -> String.valueOf(p.getAge()), 50));
        table.getColumns().add(UIHelper.<Passenger>column("Gender", Passenger::getGender, 70));
        table.getColumns().add(UIHelper.<Passenger>column("Concession", p -> p.getConcession().name(), 130));
        table.getColumns().add(UIHelper.<Passenger>column("Berth preference", p -> String.valueOf(p.getBerthPreference()), 150));
        table.getColumns().add(UIHelper.<Passenger>column("Fare (Rs)", p -> String.format("%.2f", fareFor(p)), 90));

        Button remove = UIHelper.button("Remove selected");
        remove.setOnAction(e -> {
            Passenger p = table.getSelectionModel().getSelectedItem();
            if (p != null) {
                passengers.remove(p);
                updateFare();
            }
        });
        Button confirm = UIHelper.button("Confirm booking", true);
        confirm.setOnAction(e -> confirm());
        confirm.disableProperty().bind(javafx.beans.binding.Bindings.isEmpty(passengers));
        Button back = UIHelper.button("Back to search");
        back.setOnAction(e -> onBack.run());
        totalFare.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = new HBox(10, back, remove, spacer, totalFare, confirm);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox box = UIHelper.card(UIHelper.title("Book Tickets"), summary,
                UIHelper.muted("Add up to 6 passengers. Concessions (child, senior citizen) are applied automatically from age and gender."),
                form, table, actions);
        box.setPadding(new Insets(16));
        return box;
    }

    private double fareFor(Passenger p) {
        return app.context().fareCalculator.calculateFare(result.getTrain(), result.getSeatClass(), p,
                result.getSource(), result.getDestination());
    }

    private void updateFare() {
        double total = 0;
        for (Passenger p : passengers) total += fareFor(p);
        totalFare.setText(String.format("Total fare: Rs %.2f", total));
        table.refresh();
    }

    private void addPassenger() {
        if (passengers.size() >= 6) {
            UIHelper.warn("Passengers", "A booking can hold at most 6 passengers.");
            return;
        }
        try {
            int years = Integer.parseInt(age.getText().trim());
            Passenger p = new Passenger(name.getText(), years, gender.getValue(), phone.getText(), berth.getValue());
            com.railsync.util.ValidationUtils.validatePassenger(p.getName(), p.getAge(), p.getPhone());
            passengers.add(p);
            name.clear();
            age.clear();
            updateFare();
        } catch (NumberFormatException ex) {
            UIHelper.warn("Passenger", "Age must be a whole number.");
        } catch (IllegalArgumentException | RailSyncException ex) {
            UIHelper.warn("Passenger", ex.getMessage());
        }
    }

    private void confirm() {
        try {
            Booking b = app.context().reservations.bookTicket(result.getTrain().getTrainNumber(),
                    result.getSource(), result.getDestination(), result.getJourneyDate(), result.getSeatClass(),
                    new ArrayList<>(passengers), user.getUsername());
            File receipt = new File(FileManager.receiptsDir(), b.getPnr() + ".txt");
            UIHelper.showText("Booking " + b.getStatus().name() + " - PNR " + b.getPnr()
                    + (receipt.isFile() ? "   (receipt saved: " + receipt.getName() + ")" : ""),
                    ReportGenerator.generateTicketText(b));
            onBooked.run();
        } catch (RailSyncException ex) {
            UIHelper.error("Booking failed", ex.getMessage());
        } catch (RuntimeException ex) {
            UIHelper.error("Unexpected error", ex.getMessage());
        }
    }
}
