package com.railsync.awt;

import java.awt.*;
import java.awt.event.*;

/**
 * Window events.
 * <ul>
 *   <li>The first window implements {@link WindowListener} completely (all seven methods);</li>
 *   <li>the second window uses {@link WindowAdapter} and overrides only {@code windowClosing}
 *       and {@code windowActivated} - an adapter spares you the empty methods.</li>
 * </ul>
 */
public class WindowEventDemo extends Frame implements WindowListener {

    private final TextArea log = new TextArea("", 12, 55, TextArea.SCROLLBARS_VERTICAL_ONLY);

    public WindowEventDemo() {
        super("Window events (WindowListener)");
        setLayout(new BorderLayout(6, 6));
        log.setEditable(false);
        Button openAdapter = new Button("Open WindowAdapter window");
        Button minimise = new Button("Minimise (iconify)");
        Panel buttons = new Panel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(openAdapter);
        buttons.add(minimise);
        add(buttons, BorderLayout.NORTH);
        add(log, BorderLayout.CENTER);

        openAdapter.addActionListener(e -> openAdapterWindow());
        minimise.addActionListener(e -> setState(Frame.ICONIFIED));

        addWindowListener(this);                    // this class is the listener
        pack();
        setLocationRelativeTo(null);
    }

    private void openAdapterWindow() {
        Frame f = new Frame("WindowAdapter window");
        f.setSize(320, 120);
        f.add(new Label("Close me: only windowClosing & windowActivated are overridden."));
        f.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                log.append("[adapter window] windowClosing -> dispose()\n");
                f.dispose();
            }

            @Override
            public void windowActivated(WindowEvent e) {
                log.append("[adapter window] windowActivated\n");
            }
        });
        f.setLocationRelativeTo(this);
        f.setVisible(true);
    }

    // ---- WindowListener: every method has to be written, even the ones we do not need.
    @Override public void windowOpened(WindowEvent e) { log.append("windowOpened\n"); }
    @Override public void windowClosing(WindowEvent e) { dispose(); }
    @Override public void windowClosed(WindowEvent e) { System.out.println("[WindowEventDemo] windowClosed"); }
    @Override public void windowIconified(WindowEvent e) { log.append("windowIconified\n"); }
    @Override public void windowDeiconified(WindowEvent e) { log.append("windowDeiconified\n"); }
    @Override public void windowActivated(WindowEvent e) { log.append("windowActivated\n"); }
    @Override public void windowDeactivated(WindowEvent e) { log.append("windowDeactivated\n"); }
}
