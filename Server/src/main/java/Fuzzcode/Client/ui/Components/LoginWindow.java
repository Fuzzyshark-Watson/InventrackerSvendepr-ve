package Fuzzcode.Client.ui.Components;

import Fuzzcode.Client.ui.styles.DarkFrame;
import Fuzzcode.Server.utilities.LoggerHandler;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;

public class LoginWindow extends DarkFrame {

    public LoginWindow(MainUI mainUI) {
        super("Login");
        setUndecorated(true);
        JTextField userField = new JTextField(16);
        JPasswordField passField = new JPasswordField(16);
        JButton loginButton = new JButton("Login");

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0; panel.add(new JLabel("User:"), gbc);
        gbc.gridx = 1; gbc.gridy = 0; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        panel.add(userField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        panel.add(new JLabel("Password:"), gbc);
        gbc.gridx = 1; gbc.gridy = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        panel.add(passField, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2; gbc.anchor = GridBagConstraints.CENTER;
        panel.add(loginButton, gbc);

        loginButton.addActionListener(e -> {
            String user = userField.getText().trim();
            char[] pass = passField.getPassword();
            LoggerHandler.log("Login Button Pressed, User: " + user
                    + " | Password: " + Arrays.toString(pass));

            boolean ok = mainUI.handleLogin(user, pass);

            if (ok) {
                dispose();
            }
        });

        setContentPane(panel);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }
}