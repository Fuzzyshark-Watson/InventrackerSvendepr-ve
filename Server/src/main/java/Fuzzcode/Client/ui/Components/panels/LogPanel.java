package Fuzzcode.Client.ui.Components.panels;

import Fuzzcode.Client.ui.Components.MainUI;
import Fuzzcode.Client.ui.models.LogPanelModel;

import javax.swing.*;
import java.awt.*;

public class LogPanel extends JPanel {

    private final MainUI ui;
    private final LogPanelModel logModel;

    public LogPanel(MainUI ui) {
        super(new BorderLayout());
        this.ui = ui;
        this.logModel = new LogPanelModel();

        // --- settings / toolbar at top ---
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);

        // Clear button
        JButton clearBtn = new JButton("Clear");
        clearBtn.addActionListener(e -> logModel.clear());
        toolbar.add(clearBtn);

        // (Optional) place for settings toggles later:
        // JCheckBox wsToggle   = new JCheckBox("WS", true);
        // JCheckBox httpToggle = new JCheckBox("HTTP", true);
        // toolbar.add(wsToggle);
        // toolbar.add(httpToggle);

        // You can later wire these to ui / WsClientEndpoint to filter logs

        // --- layout similar to OrdersPanel ---
        setBorder(BorderFactory.createTitledBorder("Log"));
        add(toolbar, BorderLayout.NORTH);
        add(logModel, BorderLayout.CENTER);
    }

    public void append(String text) {
        logModel.append(text);
    }
}

