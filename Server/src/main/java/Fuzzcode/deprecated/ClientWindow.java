package Fuzzcode.deprecated;

import javax.swing.*;
import java.awt.*;

// Simple auth seam — swap this for your real logic
interface AuthService {
    boolean authenticate(String user, char[] pass) throws Exception;
}



/* ================= Rounded controls ================= */

class RoundedTextField extends JTextField {
    private final int radius = 12;
    public RoundedTextField(int columns) {
        super(columns);
        setOpaque(false);
        setForeground(Color.WHITE);
        setCaretColor(Color.WHITE);
    }
    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(new Color(60, 60, 60));
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
        super.paintComponent(g);
        g2.dispose();
    }
    @Override protected void paintBorder(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(new Color(95, 95, 95));
        g2.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, radius, radius);
        g2.dispose();
    }
}

class RoundedPasswordField extends JPasswordField {
    private final int radius = 12;
    public RoundedPasswordField(int columns) {
        super(columns);
        setOpaque(false);
        setForeground(Color.WHITE);
        setCaretColor(Color.WHITE);
    }
    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(new Color(60, 60, 60));
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
        super.paintComponent(g);
        g2.dispose();
    }
    @Override protected void paintBorder(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(new Color(95, 95, 95));
        g2.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, radius, radius);
        g2.dispose();
    }
}

class RoundedButton extends JButton {
    private final int radius = 12;
    public RoundedButton(String text) {
        super(text);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        setForeground(Color.WHITE);
    }
    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(new Color(70, 70, 70));
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
        super.paintComponent(g);
        g2.dispose();
    }
}

/* ================= Your "next window" ================= */

class MainWindow extends JFrame {
    public MainWindow() {
        super("InvenTracker — Main");
        JPanel p = new JPanel();
        p.setBackground(new Color(36,36,36));
        p.add(new JLabel("<html><div style='color:white'>Welcome!</div></html>"));
        setContentPane(p);
        setSize(600, 400);          // pick a size for your app
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    }
}

/*
*/
