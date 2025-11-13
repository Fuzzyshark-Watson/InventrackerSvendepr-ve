package Fuzzcode.Client.ui.Components.panels;

import javax.swing.*;
import java.awt.*;

public class CrudsPanel extends JPanel {

    private final JTabbedPane tabbedPane;

    public CrudsPanel() {
        super(new BorderLayout());

        setBorder(BorderFactory.createTitledBorder("CRUDs"));

        UIManager.put("TabbedPane.selected", Color.gray);

        tabbedPane = new JTabbedPane();

        tabbedPane.addTab("People", new PersonCrudPanel());
        tabbedPane.addTab("Orders", new OrderCrudPanel());
        tabbedPane.addTab("Items",  new ItemCrudPanel());
        tabbedPane.addTab("Reads",  new ReadsCrudPanel());

        add(tabbedPane, BorderLayout.CENTER);
    }
}
