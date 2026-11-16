package com.railsync.awt;

import com.railsync.AppContext;
import com.railsync.model.SearchResult;

import java.awt.*;
import java.awt.event.*;
import java.time.LocalDate;
import java.util.List;

/**
 * Standalone AWT window that shows the <b>event delegation model</b>: a <i>source</i> (Button, TextField,
 * Label, Frame) generates an event object and hands it to every registered <i>listener</i>.
 *
 * <ul>
 *   <li>{@link ActionListener} - the Search button (runs a real train search);</li>
 *   <li>{@link KeyListener} - the station code fields (upper-casing as you type, Enter = search);</li>
 *   <li>{@link MouseListener} - the status label (see {@link ButtonEventDemo});</li>
 *   <li>{@link WindowListener} / {@link WindowAdapter} - see {@link WindowEventDemo};</li>
 *   <li>{@link MouseAdapter} - the tip label below.</li>
 * </ul>
 * This is a teaching/demo window; the main application interface is JavaFX.
 */
public class AWTEventDemo extends Frame implements ActionListener {

    private final AppContext ctx;
    private final TextField txtFrom = new TextField("NDLS", 8);
    private final TextField txtTo = new TextField("BCT", 8);
    private final Button btnSearch = new Button("Search");
    private final Button btnButtonDemo = new Button("Button / Mouse / Key demo");
    private final Button btnWindowDemo = new Button("Window events demo");
    private final TextArea output = new TextArea("", 12, 70, TextArea.SCROLLBARS_VERTICAL_ONLY);
    private final Label tip = new Label("Tip: click here to clear the output (MouseAdapter).");

    public AWTEventDemo(AppContext ctx, boolean exitOnClose) {
        super("RailSync - AWT Event Handling Demo");
        this.ctx = ctx;
        setLayout(new BorderLayout(8, 8));

        Panel top = new Panel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        top.add(new Label("From (code):"));
        top.add(txtFrom);
        top.add(new Label("To (code):"));
        top.add(txtTo);
        top.add(btnSearch);
        add(top, BorderLayout.NORTH);

        output.setEditable(false);
        output.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        add(output, BorderLayout.CENTER);

        Panel bottom = new Panel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        bottom.add(btnButtonDemo);
        bottom.add(btnWindowDemo);
        bottom.add(tip);
        add(bottom, BorderLayout.SOUTH);

        // ---- ActionListener: this class implements it and is registered on the Search button.
        btnSearch.addActionListener(this);

        // ---- ActionListener as a lambda: the other buttons open the other demos.
        btnButtonDemo.addActionListener(e -> new ButtonEventDemo(this).setVisible(true));
        btnWindowDemo.addActionListener(e -> new WindowEventDemo().setVisible(true));

        // ---- KeyListener on the text fields (KeyAdapter: only keyTyped / keyPressed are needed).
        KeyAdapter upper = new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                TextField source = (TextField) e.getSource();          // the event knows its source
                String text = source.getText().toUpperCase();
                if (!text.equals(source.getText())) {
                    source.setText(text);
                    source.setCaretPosition(text.length());
                }
            }

            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    btnSearch.dispatchEvent(new ActionEvent(btnSearch, ActionEvent.ACTION_PERFORMED, "Search"));
                }
            }
        };
        txtFrom.addKeyListener(upper);
        txtTo.addKeyListener(upper);

        // ---- MouseAdapter: override only mouseClicked.
        tip.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                output.setText("");
            }
        });

        // ---- WindowAdapter: override only windowClosing.
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                dispose();
                if (exitOnClose) System.exit(0);
            }
        });

        pack();
        setLocationRelativeTo(null);
    }

    /** Called by AWT when the Search button is pressed (ActionListener). */
    @Override
    public void actionPerformed(ActionEvent e) {
        String from = txtFrom.getText().trim().toUpperCase();
        String to = txtTo.getText().trim().toUpperCase();
        output.append("> Search " + from + " -> " + to + "   (command: " + e.getActionCommand() + ")\n");
        try {
            List<SearchResult> results = ctx.search.searchAvailability(from, to, LocalDate.now().plusDays(1), null);
            if (results.isEmpty()) {
                output.append("  no trains found\n");
            }
            for (SearchResult r : results) {
                output.append(String.format("  %-6s %-22s %-3s dep %s  fare %8.2f  avail %s%n",
                        r.getTrain().getTrainNumber(), r.getTrain().getTrainName(), r.getSeatClass().getCode(),
                        r.getDepartureTime(), r.getFare(), r.getAvailabilityText()));
            }
        } catch (Exception ex) {                                        // validation (same station, unknown code...)
            output.append("  error: " + ex.getMessage() + "\n");
        }
    }

    public void log(String line) {
        output.append(line + "\n");
    }

    /** Opens the window. Must not be called in a headless environment. */
    public static void launch(AppContext ctx) {
        launch(ctx, true);
    }

    public static void launch(AppContext ctx, boolean exitOnClose) {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("AWT needs a display; none is available.");
            return;
        }
        EventQueue.invokeLater(() -> new AWTEventDemo(ctx, exitOnClose).setVisible(true));
    }
}
