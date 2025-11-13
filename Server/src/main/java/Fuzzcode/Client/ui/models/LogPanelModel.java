package Fuzzcode.Client.ui.models;

import javax.swing.*;
import java.awt.*;

public class LogPanelModel extends JPanel {
    JTextArea log = new JTextArea();

    public LogPanelModel() {
        super(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Log"));
        log.setEditable(false);
        add(new JScrollPane(log), BorderLayout.CENTER);
    }

    public void append(String text) {
        SwingUtilities.invokeLater(() -> {
            log.append(text + "\n");
            log.setCaretPosition(log.getDocument().getLength());
        });
    }

    public void clear() {
        SwingUtilities.invokeLater(() -> {
            log.setText("");
        });
    }
}
