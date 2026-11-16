package com.railsync.javafx;

import com.railsync.exception.RailSyncException;
import com.railsync.manager.FileManager;
import com.railsync.model.Booking;
import com.railsync.model.RefundReceipt;
import com.railsync.model.User;
import com.railsync.service.ReportGenerator;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.List;

/** A passenger's bookings: look up by PNR, view the ticket, cancel, open the saved receipt. */
public class BookingHistoryView {

    private final RailSyncApplication app;
    private final User user;
    private final TableView<Booking> table = UIHelper.table("You have no bookings yet.");
    private final TextArea ticketArea = UIHelper.monoArea(false);
    private final TextField pnrField = new TextField();
    private boolean built = false;

    public BookingHistoryView(RailSyncApplication app, User user) {
        this.app = app;
        this.user = user;
    }

    public Node build() {
        if (!built) {
            table.getColumns().add(UIHelper.<Booking>column("PNR", Booking::getPnr, 90));
            table.getColumns().add(UIHelper.<Booking>column("Train", b -> b.getTrainNumber() + " " + b.getTrainName(), 200));
            table.getColumns().add(UIHelper.<Booking>column("Route", b -> b.getSource().getCode() + " -> " + b.getDestination().getCode(), 110));
            table.getColumns().add(UIHelper.<Booking>column("Date", b -> b.getJourneyDate().toString(), 100));
            table.getColumns().add(UIHelper.<Booking>column("Class", b -> b.getSeatClass().getCode(), 55));
            table.getColumns().add(UIHelper.<Booking>column("Passengers", b -> String.valueOf(b.getPassengerCount()), 85));
            table.getColumns().add(UIHelper.<Booking>column("Status", b -> b.getStatus().name(), 100));
            table.getColumns().add(UIHelper.<Booking>column("Fare (Rs)", b -> String.format("%.2f", b.getTotalFare()), 90));
            table.getSelectionModel().selectedItemProperty().addListener((obs, old, b) ->
                    ticketArea.setText(b == null ? "" : ReportGenerator.generateTicketText(b)));
            built = true;
        }
        pnrField.setPromptText("PNR e.g. RS123456");
        Button find = UIHelper.button("Find PNR");
        find.setOnAction(e -> findPnr());
        Button refresh = UIHelper.button("Refresh");
        refresh.setOnAction(e -> refresh());
        HBox lookup = new HBox(8, new Label("PNR"), pnrField, find, refresh);
        lookup.setAlignment(Pos.CENTER_LEFT);

        Button cancel = UIHelper.button("Cancel selected booking");
        cancel.setOnAction(e -> {
            Booking b = table.getSelectionModel().getSelectedItem();
            if (b == null) UIHelper.warn("Cancel", "Select a booking first.");
            else cancelBooking(b);
        });
        Button receipt = UIHelper.button("Open saved receipt");
        receipt.setOnAction(e -> openReceipt());
        HBox actions = new HBox(10, cancel, receipt);

        SplitPane split = new SplitPane(table, ticketArea);
        split.setOrientation(javafx.geometry.Orientation.VERTICAL);
        split.setDividerPositions(0.5);
        VBox.setVgrow(split, Priority.ALWAYS);

        refresh();
        return UIHelper.card(UIHelper.title("My Bookings"), lookup, split, actions);
    }

    public void refresh() {
        List<Booking> bookings = app.context().reservations.getBookingsForUser(user.getUsername());
        table.setItems(FXCollections.observableArrayList(bookings));
        if (!bookings.isEmpty()) table.getSelectionModel().selectFirst();
        else ticketArea.clear();
    }

    private void findPnr() {
        try {
            Booking b = app.context().reservations.findBooking(pnrField.getText());
            if (!user.isAdmin() && !user.getUsername().equalsIgnoreCase(b.getBookedByUserId())) {
                UIHelper.warn("PNR", "That PNR does not belong to your account.");
                return;
            }
            table.getItems().stream().filter(x -> x.getPnr().equals(b.getPnr())).findFirst()
                    .ifPresent(x -> table.getSelectionModel().select(x));
        } catch (RailSyncException ex) {                       // InvalidPNRException
            UIHelper.warn("PNR lookup", ex.getMessage());
        }
    }

    /** Cancels after confirmation and shows the refund; used by this view and the "Cancel by PNR" action. */
    public void cancelBooking(Booking b) {
        if (!UIHelper.confirm("Cancel booking " + b.getPnr() + "?",
                "All active tickets on this PNR will be cancelled and refunds calculated.")) {
            return;
        }
        try {
            List<RefundReceipt> receipts = app.context().reservations.cancelBooking(user, b.getPnr());
            StringBuilder sb = new StringBuilder();
            for (RefundReceipt r : receipts) sb.append(r.getFormattedReceipt()).append('\n');
            UIHelper.showText("Booking " + b.getPnr() + " cancelled", sb.toString());
            refresh();
        } catch (RailSyncException ex) {                       // InvalidPNR / CancellationNotAllowed / TrainNotFound
            UIHelper.error("Cancellation not possible", ex.getMessage());
        }
    }

    private void openReceipt() {
        Booking b = table.getSelectionModel().getSelectedItem();
        if (b == null) return;
        try {
            UIHelper.showText("Receipt file for " + b.getPnr(), FileManager.readReceipt(b.getPnr()));
        } catch (java.io.FileNotFoundException ex) {
            if (UIHelper.confirm("No saved receipt", "No receipt file exists for " + b.getPnr() + ". Create it now?")) {
                try {
                    FileManager.saveReceipt(b);
                    openReceipt();
                } catch (java.io.IOException io) {
                    UIHelper.error("Could not write receipt", io.getMessage());
                }
            }
        } catch (java.io.IOException ex) {
            UIHelper.error("Could not read receipt", ex.getMessage());
        }
    }
}
