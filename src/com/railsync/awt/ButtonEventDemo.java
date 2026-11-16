package com.railsync.awt;

import java.awt.*;
import java.awt.event.*;

/**
 * Button, mouse and keyboard events.
 * <ul>
 *   <li>{@link ActionListener} on a Button;</li>
 *   <li>{@link MouseListener} implemented in full (all five methods) on a Panel;</li>
 *   <li>{@link MouseAdapter} on a Label that only needs {@code mouseClicked};</li>
 *   <li>{@link KeyListener} on a TextField (typed / pressed / released).</li>
 * </ul>
 */
public class ButtonEventDemo extends Frame {

    private final TextArea log = new TextArea("", 10, 60, TextArea.SCROLLBARS_VERTICAL_ONLY);
    private final AWTEventDemo parent;
    private int clicks = 0;

    public ButtonEventDemo(AWTEventDemo parent) {
        super("Button / Mouse / Key events");
        this.parent = parent;
        setLayout(new BorderLayout(6, 6));
        log.setEditable(false);

        // Button -> ActionListener (anonymous inner class)
        Button button = new Button("Click me (ActionEvent)");
        button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                clicks++;
                say("ActionEvent from button, command='" + e.getActionCommand() + "', total clicks=" + clicks);
            }
        });

        // TextField -> KeyListener (all three methods)
        TextField field = new TextField(20);
        field.addKeyListener(new KeyListener() {
            @Override public void keyTyped(KeyEvent e) { say("keyTyped: '" + e.getKeyChar() + "'"); }
            @Override public void keyPressed(KeyEvent e) { say("keyPressed: " + KeyEvent.getKeyText(e.getKeyCode())); }
            @Override public void keyReleased(KeyEvent e) { say("keyReleased: " + KeyEvent.getKeyText(e.getKeyCode())); }
        });

        // Panel -> MouseListener (all five methods)
        Panel pad = new Panel();
        pad.setBackground(new Color(220, 235, 250));
        pad.add(new Label("Mouse pad: click, enter, leave, press, release"));
        pad.addMouseListener(new MouseListener() {
            @Override public void mouseClicked(MouseEvent e) { say("mouseClicked at (" + e.getX() + "," + e.getY() + ")"); }
            @Override public void mousePressed(MouseEvent e) { say("mousePressed"); }
            @Override public void mouseReleased(MouseEvent e) { say("mouseReleased"); }
            @Override public void mouseEntered(MouseEvent e) { pad.setBackground(new Color(200, 225, 255)); say("mouseEntered"); }
            @Override public void mouseExited(MouseEvent e) { pad.setBackground(new Color(220, 235, 250)); say("mouseExited"); }
        });

        // Label -> MouseAdapter (only the method we care about)
        Label adapterLabel = new Label("Adapter label: double-click me (MouseAdapter.mouseClicked)");
        adapterLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) say("MouseAdapter: double click detected");
            }
        });

        Panel north = new Panel(new FlowLayout(FlowLayout.LEFT));
        north.add(button);
        north.add(new Label("Type here:"));
        north.add(field);
        Panel center = new Panel(new GridLayout(2, 1, 4, 4));
        center.add(pad);
        center.add(adapterLabel);
        add(north, BorderLayout.NORTH);
        add(center, BorderLayout.CENTER);
        add(log, BorderLayout.SOUTH);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                dispose();
            }
        });
        pack();
        setLocationRelativeTo(parent);
    }

    private void say(String text) {
        log.append(text + "\n");
        if (parent != null) parent.log("[ButtonEventDemo] " + text);
    }
}
