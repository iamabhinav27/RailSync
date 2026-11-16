package com.railsync.javafx;

import com.railsync.AppContext;
import com.railsync.model.User;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;

/**
 * JavaFX entry point: owns the primary {@link Stage}, switches {@link Scene}s between the login screen
 * and the role dashboards, and shares the {@link AppContext} (services) with every view. Views never
 * contain SQL or booking logic; they call the services.
 */
public class RailSyncApplication extends Application {

    private static AppContext sharedContext;
    private Stage stage;
    private AppContext ctx;

    public static void launchApp(String[] args) {
        Application.launch(RailSyncApplication.class, args);
    }

    /** Lets tests or other launchers provide an already-built context. */
    public static void setContext(AppContext context) {
        sharedContext = context;
    }

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;
        try {
            ctx = sharedContext != null ? sharedContext : AppContext.create(true);
        } catch (RuntimeException e) {
            UIHelper.error("Could not open the database", e.getMessage());
            return;
        }
        stage.setTitle("RailSync - Train Reservation System");
        stage.setMinWidth(1000);
        stage.setMinHeight(680);
        showLogin();
        stage.show();
    }

    public AppContext context() {
        return ctx;
    }

    public Stage stage() {
        return stage;
    }

    public void showLogin() {
        setScene(new LoginView(this).build(), 520, 640);
        stage.setTitle("RailSync - Sign in");
    }

    /** Opens the dashboard that matches the user's role. */
    public void showDashboard(User user) {
        if (user.isAdmin()) {
            setScene(new AdminDashboard(this, user).build(), 1250, 780);
        } else {
            setScene(new PassengerDashboard(this, user).build(), 1250, 780);
        }
        stage.setTitle("RailSync - " + user.getDashboardTitle() + " - " + user.getFullName());
        stage.centerOnScreen();
    }

    private void setScene(javafx.scene.Parent root, double w, double h) {
        stage.setScene(new Scene(root, w, h));
        UIHelper.popIn(root);
    }

    public void logout() {
        showLogin();
    }

    // ---------------------------------------------------------------- shared menu items

    /** Menus common to both roles: Tools (AWT demo) and Help. */
    public java.util.List<Menu> commonMenus() {
        MenuItem awt = new MenuItem("AWT Event Demo...");
        awt.setOnAction(e -> openAwtDemo());
        Menu tools = new Menu("Tools", null, awt);

        MenuItem about = new MenuItem("About RailSync");
        about.setOnAction(e -> UIHelper.info("RailSync",
                "Train reservation system\nJava 17+ | JavaFX | JDBC + SQLite\nDatabase: "
                        + com.railsync.database.DatabaseConnection.getDatabasePath()));
        Menu help = new Menu("Help", null, about);
        return java.util.List.of(tools, help);
    }

    private void openAwtDemo() {
        try {
            com.railsync.awt.AWTEventDemo.launch(ctx, false);
        } catch (Throwable t) {
            UIHelper.error("AWT demo could not start", String.valueOf(t.getMessage()));
        }
    }
}
