package Fuzzcode.Client.ui.styles;

import javax.swing.*;
import java.awt.*;

public class DarkFrame extends JFrame {

    public DarkFrame(String title) {
        setTitle(title);
        setUndecorated(true);  // remove OS bar

        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setBackground(Styles.BG_DARKER);

        JLabel titleLabel = new JLabel(title);
        titleLabel.setForeground(Styles.FG_TEXT);
        titleLabel.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));

        JButton closeBtn = new JButton("✕");
        closeBtn.setForeground(Styles.FG_TEXT);
        closeBtn.setBackground(Styles.BG_DARKER);
        closeBtn.setBorder(null);
        closeBtn.addActionListener(e -> dispose());

        titleBar.add(titleLabel, BorderLayout.WEST);
        titleBar.add(closeBtn, BorderLayout.EAST);

        // add the title bar to the top of the frame
        add(titleBar, BorderLayout.NORTH);

        // enable dragging the window
        enableWindowDrag(titleBar);
    }

    private void enableWindowDrag(JComponent comp) {
        final Point[] mouseDown = {null};

        comp.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                mouseDown[0] = e.getPoint();
            }
        });

        comp.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseDragged(java.awt.event.MouseEvent e) {
                if (mouseDown[0] != null) {
                    Point p = e.getLocationOnScreen();
                    setLocation(p.x - mouseDown[0].x, p.y - mouseDown[0].y);
                }
            }
        });
    }
}

