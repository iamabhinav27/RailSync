package com.railsync.javafx;

import javafx.animation.Interpolator;
import javafx.animation.RotateTransition;
import javafx.animation.ScaleTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.Glow;
import javafx.scene.effect.Reflection;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Scale;
import javafx.util.Duration;

import java.util.Optional;
import java.util.function.Function;

/** Shared look and small UI utilities: styled controls, effects, transforms, alerts and table columns. */
public final class UIHelper {

    public static final String PRIMARY = "#1e3a8a";
    public static final String ACCENT = "#2563eb";
    public static final String BG = "#f1f5f9";
    public static final String MONO = "-fx-font-family: 'Consolas','Menlo','DejaVu Sans Mono',monospace; -fx-font-size: 12px;";

    private UIHelper() {}

    // ---------------------------------------------------------------- controls

    /** A button that grows slightly and glows when the mouse is over it (Scale transform + Glow effect). */
    public static Button button(String text, boolean primary) {
        Button b = new Button(text);
        String base = primary
                ? "-fx-background-color: " + ACCENT + "; -fx-text-fill: white; -fx-font-weight: bold;"
                : "-fx-background-color: #e2e8f0; -fx-text-fill: #0f172a;";
        b.setStyle(base + " -fx-background-radius: 6; -fx-padding: 7 16 7 16; -fx-cursor: hand;");
        Scale scale = new Scale(1, 1);
        b.getTransforms().add(scale);
        b.setOnMouseEntered(e -> {
            scale.setPivotX(b.getWidth() / 2);
            scale.setPivotY(b.getHeight() / 2);
            scale.setX(1.04);
            scale.setY(1.04);
            b.setEffect(new Glow(0.25));
        });
        b.setOnMouseExited(e -> {
            scale.setX(1);
            scale.setY(1);
            b.setEffect(null);
        });
        return b;
    }

    public static Button button(String text) {
        return button(text, false);
    }

    public static Label title(String text) {
        Label l = new Label(text);
        l.setFont(Font.font("System", FontWeight.BOLD, 22));
        l.setTextFill(Color.web(PRIMARY));
        return l;
    }

    public static Label muted(String text) {
        Label l = new Label(text);
        l.setTextFill(Color.web("#64748b"));
        l.setWrapText(true);
        return l;
    }

    /** White rounded panel with a soft drop shadow. */
    public static VBox card(Node... children) {
        VBox box = new VBox(10, children);
        box.setPadding(new Insets(16));
        box.setStyle("-fx-background-color: white; -fx-background-radius: 10;");
        box.setEffect(new DropShadow(10, 0, 2, Color.rgb(15, 23, 42, 0.18)));
        return box;
    }

    /** A statistic tile used on the admin overview. */
    public static VBox metric(String caption, Label value) {
        value.setFont(Font.font("System", FontWeight.BOLD, 26));
        value.setTextFill(Color.web(PRIMARY));
        Label c = muted(caption);
        VBox box = card(value, c);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPrefWidth(190);
        return box;
    }

    /** The app logo: a text glyph with a reflection effect that spins (Rotate transform) when clicked. */
    public static Node logo() {
        Text t = new Text("\u2756 RailSync");
        t.setFont(Font.font("System", FontWeight.BOLD, 30));
        t.setFill(Color.web(PRIMARY));
        t.setEffect(new Reflection(0.0, 0.35, 0.3, 0.0));
        t.setOnMouseClicked(e -> {
            RotateTransition spin = new RotateTransition(Duration.millis(700), t);
            spin.setAxis(Rotate.Y_AXIS);
            spin.setByAngle(360);
            spin.setInterpolator(Interpolator.EASE_BOTH);
            spin.play();
        });
        t.setStyle("-fx-cursor: hand;");
        return t;
    }

    /** Pops a node in with a short scale animation. */
    public static void popIn(Node node) {
        node.setScaleX(0.96);
        node.setScaleY(0.96);
        ScaleTransition st = new ScaleTransition(Duration.millis(180), node);
        st.setToX(1);
        st.setToY(1);
        st.play();
    }

    // ---------------------------------------------------------------- tables

    public static <T> TableColumn<T, String> column(String title, Function<T, String> value, double width) {
        TableColumn<T, String> col = new TableColumn<>(title);
        col.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(value.apply(cd.getValue())));
        col.setPrefWidth(width);
        return col;
    }

    public static <T> TableView<T> table(String emptyText) {
        TableView<T> table = new TableView<>();
        table.setPlaceholder(new Label(emptyText));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        VBox.setVgrow(table, Priority.ALWAYS);
        return table;
    }

    // ---------------------------------------------------------------- alerts

    public static void info(String header, String message) {
        show(Alert.AlertType.INFORMATION, "RailSync", header, message);
    }

    public static void error(String header, String message) {
        show(Alert.AlertType.ERROR, "RailSync - Error", header, message);
    }

    public static void warn(String header, String message) {
        show(Alert.AlertType.WARNING, "RailSync - Warning", header, message);
    }

    private static void show(Alert.AlertType type, String title, String header, String message) {
        Alert a = new Alert(type);
        a.setTitle(title);
        a.setHeaderText(header);
        a.setContentText(message);
        a.getDialogPane().setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        a.showAndWait();
    }

    public static boolean confirm(String header, String message) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO);
        a.setTitle("RailSync - Confirm");
        a.setHeaderText(header);
        Optional<ButtonType> r = a.showAndWait();
        return r.isPresent() && r.get() == ButtonType.YES;
    }

    /** Shows long monospaced text (a ticket or a report) in a dialog. */
    public static void showText(String header, String text) {
        TextArea area = new TextArea(text);
        area.setEditable(false);
        area.setStyle(MONO);
        area.setPrefSize(760, 420);
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("RailSync");
        a.setHeaderText(header);
        a.getDialogPane().setContent(area);
        a.setResizable(true);
        a.showAndWait();
    }

    public static TextArea monoArea(boolean editable) {
        TextArea area = new TextArea();
        area.setEditable(editable);
        area.setStyle(MONO);
        VBox.setVgrow(area, Priority.ALWAYS);
        return area;
    }
}
