package com.railsync.javafx;

import com.railsync.exception.InvalidLoginException;
import com.railsync.model.User;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

/** Login and passenger registration. */
public class LoginView {

    private final RailSyncApplication app;

    public LoginView(RailSyncApplication app) {
        this.app = app;
    }

    public Parent build() {
        TabPane tabs = new TabPane(new Tab("Login", loginForm()), new Tab("Register", registerForm()));
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        VBox card = UIHelper.card(tabs);
        card.setMaxWidth(430);

        Label hint = UIHelper.muted("Demo accounts: passenger / pass123   |   priya / pass123   |   admin / admin123");
        hint.setMaxWidth(430);

        VBox root = new VBox(18, UIHelper.logo(), UIHelper.muted("Train reservation & seat management"), card, hint);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(30));
        root.setStyle("-fx-background-color: " + UIHelper.BG + ";");
        return root;
    }

    private GridPane loginForm() {
        TextField username = new TextField();
        username.setPromptText("username");
        PasswordField password = new PasswordField();
        password.setPromptText("password");
        Button login = UIHelper.button("Sign in", true);
        login.setDefaultButton(true);
        login.setMaxWidth(Double.MAX_VALUE);

        login.setOnAction(e -> {
            try {
                User user = app.context().auth.login(username.getText(), password.getText());
                app.showDashboard(user);
            } catch (InvalidLoginException ex) {
                UIHelper.warn("Login failed", ex.getMessage());
                password.clear();
            }
        });

        GridPane g = grid();
        g.addRow(0, new Label("Username"), username);
        g.addRow(1, new Label("Password"), password);
        g.add(login, 1, 2);
        return g;
    }

    private GridPane registerForm() {
        TextField username = new TextField();
        TextField name = new TextField();
        TextField email = new TextField();
        TextField phone = new TextField();
        PasswordField password = new PasswordField();
        PasswordField confirm = new PasswordField();
        username.setPromptText("3-30 chars: a-z 0-9 _ .");
        phone.setPromptText("10 digits (optional)");
        password.setPromptText("min 6, letters + digits");
        Button register = UIHelper.button("Create account", true);
        register.setMaxWidth(Double.MAX_VALUE);

        register.setOnAction(e -> {
            if (!password.getText().equals(confirm.getText())) {
                UIHelper.warn("Registration", "The two passwords do not match.");
                return;
            }
            try {
                User user = app.context().auth.register(username.getText(), name.getText(), email.getText(),
                        phone.getText(), password.getText());
                UIHelper.info("Account created", "Welcome, " + user.getFullName() + "! You are now signed in.");
                app.showDashboard(user);
            } catch (InvalidLoginException ex) {
                UIHelper.warn("Registration failed", ex.getMessage());
            }
        });

        GridPane g = grid();
        g.addRow(0, new Label("Username"), username);
        g.addRow(1, new Label("Full name"), name);
        g.addRow(2, new Label("Email"), email);
        g.addRow(3, new Label("Mobile"), phone);
        g.addRow(4, new Label("Password"), password);
        g.addRow(5, new Label("Confirm"), confirm);
        g.add(register, 1, 6);
        return g;
    }

    private GridPane grid() {
        GridPane g = new GridPane();
        g.setHgap(10);
        g.setVgap(12);
        g.setPadding(new Insets(18));
        return g;
    }
}
