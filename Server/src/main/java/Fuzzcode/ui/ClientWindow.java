package Fuzzcode.ui;
import Fuzzcode.model.Item;
import Fuzzcode.model.Order;
import Fuzzcode.model.Person;
import org.apache.kafka.common.security.auth.Login;
import org.h2.engine.User;
import org.h2.table.Column;

import javax.swing.*;
import java.awt.*;

public class ClientWindow extends JFrame {

    private RoundedTextField textField;
    private RoundedButton button;

    public ClientWindow() {
        super("InvenTracker");

        // Create custom rounded components
        textField = new RoundedTextField(20);
        button = new RoundedButton("Submit");

        // Button click -> print text
        button.addActionListener(e -> {
            System.out.println("You typed: " + textField.getText());
        });

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(new Color(40, 40, 40));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.anchor = GridBagConstraints.WEST;

        // ----- Row 1 Label -----
        JLabel userLabel = new JLabel("User:");
        userLabel.setForeground(Color.WHITE);
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(userLabel, gbc);

        // ----- Row 1 Text Field -----
        RoundedTextField userField = new RoundedTextField(15);
        gbc.gridx = 1; gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(userField, gbc);

        // ----- Row 2 Label -----
        JLabel passLabel = new JLabel("Password:");
        passLabel.setForeground(Color.WHITE);
        gbc.gridx = 0; gbc.gridy = 1;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(passLabel, gbc);

        // ----- Row 2 Text Field -----
        RoundedTextField passField = new RoundedTextField(15);
        gbc.gridx = 1; gbc.gridy = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(passField, gbc);

        // ----- Row 3 Button -----
        RoundedButton loginButton = new RoundedButton("Login");
        gbc.gridx = 0; gbc.gridy = 2;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.CENTER;
        panel.add(loginButton, gbc);

        // Button action
        loginButton.addActionListener(e -> {
            System.out.println("User: " + userField.getText());
            System.out.println("Pass: " + passField.getText());
        });

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setContentPane(panel);
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }
    public static void main(String[] args) {
        SwingUtilities.invokeLater(ClientWindow::new);
    }
}
class RoundedTextField extends JTextField {
    private final int radius = 12;
    public RoundedTextField(int columns) {
        super(columns);
        setOpaque(false);
        setForeground(Color.WHITE);
    }
    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2.setColor(new Color(60, 60, 60));
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);

        super.paintComponent(g);
        g2.dispose();
    }

    @Override
    protected void paintBorder(Graphics g) {
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
        setContentAreaFilled(false);   // prevent default background
        setBorderPainted(false);       // prevent square border
        setFocusPainted(false);        // remove dotted focus outline
        setOpaque(false);              // allow our custom paint
        setForeground(Color.WHITE);
    }
    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2.setColor(new Color(70, 70, 70));
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);

        super.paintComponent(g);
        g2.dispose();
    }
}
/*
UI Coloration should be Very Dark Grey and slightly lighter gray
    Dark gray - xxxxx.setColor(new Color(60, 60, 60));
    Slightly lighter gray - xxxxx.setColor(new Color(40, 40, 40));
Text should be white
        xxxxx.setForeground(Color.WHITE);

Login Window
- username
- password
- Login Button
    - Pass through the Websocket for Login confirmation.
    - If Succesfull, open the main Program.

Main Window
Top 2/3rds of the Screen
Two Columns:
        - Orders
- Items

Column 1: Orders
Order ID
Created Date
Start Date
End Date
Customer ID
Logged by ID
Home (count)
        In transit, Home to Out (count)
Delivered to Client (count)
        In transit, Out to Home (count)
If all items in Order, are not on the same stage text should be bold
		- Function: return integer: AllItemsTogether(int orderID)
				- if all items are not together, return 0, otherwise return position
		- Function return integet: ItemsAtPosition(int orderID, int Position)

Column Rules:
Home
        If Home, past their start-date, text should be yellow, otherwise green
        In transit, Home to Out
If Delivered to Driver, past their start-date, text should be yellow, otherwise green
Delivered to Client
If Delivered to Client, past their end-date, text should be yellow, otherwise green
        In transit, Out to Home
If In transit, Out to Home, past their end-date, text should be yellow, otherwise green

If you click an Order - Column 2 gets populated by that order's Items.

Column 2: Items of Order
        tagId
itemId
        Position


Filter Butttons:
Home
        In transit, Home to Out
Delivered to Client
        In transit, Out to Home

Bottom 3rd of the Screen:

CRUD for:
        - Item
- Order
- Person
- User

        Exit*/
