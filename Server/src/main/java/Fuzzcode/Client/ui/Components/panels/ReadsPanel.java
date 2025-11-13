package Fuzzcode.Client.ui.Components.panels;

import Fuzzcode.Client.ui.models.ReadsTableModel;

import javax.swing.*;
import java.awt.*;

public class ReadsPanel extends JPanel {

    private final ReadsTableModel model;
    private final JTable table;

    public ReadsPanel(ReadsTableModel readsModel) {
        super(new BorderLayout());

        this.model = readsModel;
        this.table = new JTable(model);

        table.setFillsViewportHeight(true);
        table.setRowHeight(22);
        table.setAutoCreateRowSorter(true);

        setBorder(BorderFactory.createTitledBorder("Reads"));
        add(new JScrollPane(table), BorderLayout.CENTER);
    }

    public ReadsTableModel getModel() { return model; }
    public JTable getTable() { return table; }
}
