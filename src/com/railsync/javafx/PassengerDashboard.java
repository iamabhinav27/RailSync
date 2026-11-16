package com.railsync.javafx;

import com.railsync.exception.RailSyncException;
import com.railsync.model.SearchResult;
import com.railsync.model.User;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/** Home screen for the PASSENGER role: MenuBar + ToolBar on top, switchable content in the centre. */
public class PassengerDashboard {

    private final RailSyncApplication app;
    private final User user;
    private final StackPane content = new StackPane();
    private final TrainSearchView searchView;
    private final BookingHistoryView historyView;

    public PassengerDashboard(RailSyncApplication app, User user) {
        this.app = app;
        this.user = user;
        this.searchView = new TrainSearchView(app, this::openBooking);
        this.historyView = new BookingHistoryView(app, user);
    }

    public Parent build() {
        BorderPane root = new BorderPane();
        root.setTop(new VBox(menuBar(), toolBar()));
        root.setCenter(content);
        root.setBottom(statusBar());
        root.setStyle("-fx-background-color: " + UIHelper.BG + ";");
        content.setPadding(new Insets(14));
        showSearch();
        return root;
    }

    // ---------------------------------------------------------------- navigation

    private void show(Node node) {
        content.getChildren().setAll(node);
        UIHelper.popIn(node);
    }

    private void showSearch() {
        show(searchView.build());
    }

    private void showHistory() {
        historyView.refresh();
        show(historyView.build());
    }

    private void showProfile() {
        Label name = new Label(user.getFullName());
        name.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");
        GridPane g = new GridPane();
        g.setHgap(16);
        g.setVgap(10);
        g.addRow(0, new Label("Username"), new Label(user.getUsername()));
        g.addRow(1, new Label("Email"), new Label(user.getEmail()));
        g.addRow(2, new Label("Mobile"), new Label(user.getPhone().isEmpty() ? "-" : user.getPhone()));
        g.addRow(3, new Label("Role"), new Label(user.getRole().toString()));
        g.addRow(4, new Label("Bookings"),
                new Label(String.valueOf(app.context().reservations.getBookingsForUser(user.getUsername()).size())));
        VBox card = UIHelper.card(UIHelper.title("My Profile"), name, g);
        card.setMaxWidth(480);
        StackPane wrap = new StackPane(card);
        wrap.setAlignment(javafx.geometry.Pos.TOP_LEFT);
        show(wrap);
    }

    private void openBooking(SearchResult result) {
        BookingView view = new BookingView(app, user, result, this::showHistory, this::showSearch);
        show(view.build());
    }

    private void cancelByPnr() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Cancel ticket");
        dialog.setHeaderText("Enter the PNR of the booking you want to cancel");
        dialog.setContentText("PNR:");
        dialog.showAndWait().ifPresent(pnr -> {
            try {
                var booking = app.context().reservations.findBooking(pnr);
                historyView.cancelBooking(booking);
                showHistory();
            } catch (RailSyncException e) {
                UIHelper.error("Cannot cancel", e.getMessage());
            }
        });
    }

    // ---------------------------------------------------------------- chrome

    private MenuBar menuBar() {
        MenuItem search = new MenuItem("Search Trains");
        search.setOnAction(e -> showSearch());
        MenuItem history = new MenuItem("My Bookings");
        history.setOnAction(e -> showHistory());
        MenuItem cancel = new MenuItem("Cancel Ticket by PNR...");
        cancel.setOnAction(e -> cancelByPnr());
        MenuItem profile = new MenuItem("My Profile");
        profile.setOnAction(e -> showProfile());
        MenuItem logout = new MenuItem("Log out");
        logout.setOnAction(e -> app.logout());
        MenuItem exit = new MenuItem("Exit");
        exit.setOnAction(e -> app.stage().close());
        Menu passenger = new Menu("Passenger", null, search, history, cancel, profile,
                new SeparatorMenuItem(), logout, exit);

        MenuBar bar = new MenuBar(passenger);
        bar.getMenus().addAll(app.commonMenus());
        return bar;
    }

    private ToolBar toolBar() {
        Button search = UIHelper.button("Search Trains", true);
        search.setOnAction(e -> showSearch());
        Button history = UIHelper.button("My Bookings");
        history.setOnAction(e -> showHistory());
        Button cancel = UIHelper.button("Cancel by PNR");
        cancel.setOnAction(e -> cancelByPnr());
        Button profile = UIHelper.button("Profile");
        profile.setOnAction(e -> showProfile());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button logout = UIHelper.button("Log out");
        logout.setOnAction(e -> app.logout());
        return new ToolBar(search, history, cancel, profile, spacer, logout);
    }

    private Node statusBar() {
        Label l = new Label("Signed in as " + user.getFullName() + " (" + user.getUsername() + ")");
        l.setPadding(new Insets(5, 12, 5, 12));
        return l;
    }
}
