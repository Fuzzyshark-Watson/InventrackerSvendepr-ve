package Fuzzcode.Client.ui.Components.panels;

import Fuzzcode.Server.model.ItemRead;
import Fuzzcode.Client.ui.ItemRead.ItemReadClient;
import Fuzzcode.Client.ui.ItemRead.ItemReadStore;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

public class ReadsCrudPanel extends JPanel {

    private final ItemReadStore  readStore;
    private final ItemReadClient readClient;

    private JTable            table;
    private DefaultTableModel model;

    private JTextField readIdField;
    private JTextField tagIdField;
    private JTextField readTimeField; // ISO string

    private JCheckBox deletedCheck;

    public ReadsCrudPanel() {
        this.readStore  = ItemReadStore.getInstance();
        this.readClient = ItemReadClient.getInstance();

        setLayout(new BorderLayout(5, 5));
        initUi();

        readStore.addListener(this::onReadsChanged);
        readClient.requestAll(); // ItemRead.List
    }

    private void initUi() {
        // ---------- FORM ----------
        JPanel form = new JPanel(new GridLayout(4, 2, 5, 5));
        form.setBorder(BorderFactory.createTitledBorder("ItemRead details"));

        readIdField   = new JTextField();
        readIdField.setEnabled(false);
        tagIdField    = new JTextField();
        readTimeField = new JTextField("2025-02-02T09:32:10Z");

        deletedCheck = new JCheckBox("Deleted");
        deletedCheck.setEnabled(false);

        form.add(new JLabel("Read ID:"));
        form.add(readIdField);
        form.add(new JLabel("Tag ID:"));
        form.add(tagIdField);
        form.add(new JLabel("Read time (ISO-8601):"));
        form.add(readTimeField);
        form.add(new JLabel(""));
        form.add(deletedCheck);

        // ---------- BUTTONS ----------
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton createBtn = new JButton("Create");
        JButton deleteBtn = new JButton("Delete");

        buttons.add(createBtn);
        buttons.add(deleteBtn);

        // ---------- TABLE ----------
        String[] cols = { "Read ID", "Tag ID", "Read Time" };
        model = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new JTable(model);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createTitledBorder("Item Reads"));

        table.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            int viewRow = table.getSelectedRow();
            if (viewRow < 0) return;
            int row = table.convertRowIndexToModel(viewRow);

            Object idVal   = model.getValueAt(row, 0);
            Object tagVal  = model.getValueAt(row, 1);
            Object timeVal = model.getValueAt(row, 2);

            readIdField.setText(idVal != null ? idVal.toString() : "");
            tagIdField.setText(tagVal != null ? tagVal.toString() : "");
            readTimeField.setText(timeVal != null ? timeVal.toString() : "");
        });

        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, buttons.getPreferredSize().height));
        center.add(buttons);
        center.add(scroll);

        add(form,   BorderLayout.NORTH);
        add(center, BorderLayout.CENTER);

        createBtn.addActionListener(e -> onCreate());
        deleteBtn.addActionListener(e -> onDelete());
    }

    private void onReadsChanged(java.util.List<ItemRead> reads) {
        SwingUtilities.invokeLater(() -> {
            model.setRowCount(0);
            for (ItemRead r : reads) {
                if (!r.deleted()) {
                    model.addRow(new Object[]{
                            r.readId(),
                            r.tagId(),
                            r.readTime().toString()
                    });
                }
            }
        });
    }

    private void onCreate() {
        String tagId   = tagIdField.getText().trim();
        String readIso = readTimeField.getText().trim();

        if (tagId.isEmpty() || readIso.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Tag ID and Read Time are required",
                    "Validation", JOptionPane.ERROR_MESSAGE);
            return;
        }
        readClient.createRead(tagId, readIso);
        clearForm();
    }

    private void onDelete() {
        String idText = readIdField.getText().trim();
        if (idText.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Select a read to delete",
                    "Validation", JOptionPane.ERROR_MESSAGE);
            return;
        }
        int readId = Integer.parseInt(idText);
        readClient.deleteRead(readId);
        clearForm();
    }

    private void clearForm() {
        readIdField.setText("");
        tagIdField.setText("");
        // keep last readTime as a convenience
        deletedCheck.setSelected(false);
        table.clearSelection();
    }
}
