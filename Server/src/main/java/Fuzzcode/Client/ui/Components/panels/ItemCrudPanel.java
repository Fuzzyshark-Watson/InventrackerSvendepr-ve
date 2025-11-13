package Fuzzcode.Client.ui.Components.panels;

import Fuzzcode.Server.model.Item;
import Fuzzcode.Server.model.Position;
import Fuzzcode.Client.ui.Item.ItemClient;
import Fuzzcode.Client.ui.Item.ItemStore;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

public class ItemCrudPanel extends JPanel {

    private final ItemStore  itemStore;
    private final ItemClient itemClient;

    private JTable            table;
    private DefaultTableModel model;

    private JTextField itemIdField;
    private JTextField tagIdField;
    private JComboBox<Position> positionCombo;
    private JCheckBox overdueCheck;
    private JCheckBox deletedCheck;

    public ItemCrudPanel() {
        this.itemStore  = ItemStore.getInstance();
        this.itemClient = ItemClient.getInstance();

        setLayout(new BorderLayout(5, 5));
        initUi();

        // subscribe to updates
        itemStore.addListener(this::onItemsChanged);

        // initial load
        itemClient.requestAll();
    }

    private void initUi() {
        // ---------- FORM ----------
        JPanel form = new JPanel(new GridLayout(5, 2, 5, 5));
        form.setBorder(BorderFactory.createTitledBorder("Item details"));

        itemIdField = new JTextField();
        itemIdField.setEnabled(false);

        tagIdField = new JTextField();

        positionCombo = new JComboBox<>(Position.values());

        overdueCheck = new JCheckBox("Overdue");
        deletedCheck = new JCheckBox("Deleted");
        deletedCheck.setEnabled(false);

        form.add(new JLabel("Item ID:"));
        form.add(itemIdField);
        form.add(new JLabel("Tag ID:"));
        form.add(tagIdField);
        form.add(new JLabel("Position:"));
        form.add(positionCombo);
        form.add(new JLabel(""));
        form.add(overdueCheck);
        form.add(new JLabel(""));
        form.add(deletedCheck);

        // ---------- BUTTONS ----------
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton createBtn = new JButton("Create");
        JButton updateBtn = new JButton("Update");
        JButton deleteBtn = new JButton("Delete");

        buttons.add(createBtn);
        buttons.add(updateBtn);
        buttons.add(deleteBtn);

        // ---------- TABLE ----------
        String[] cols = { "Item ID", "Tag ID", "Position", "Overdue" };
        model = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new JTable(model);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createTitledBorder("Items"));

        table.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            int viewRow = table.getSelectedRow();
            if (viewRow < 0) return;
            int row = table.convertRowIndexToModel(viewRow);

            Object idVal   = model.getValueAt(row, 0);
            Object tagVal  = model.getValueAt(row, 1);
            Object posVal  = model.getValueAt(row, 2);
            Object overVal = model.getValueAt(row, 3);

            itemIdField.setText(idVal != null ? idVal.toString() : "");
            tagIdField.setText(tagVal != null ? tagVal.toString() : "");
            if (posVal != null) {
                positionCombo.setSelectedItem(Position.fromString(posVal.toString()));
            }
            overdueCheck.setSelected(overVal instanceof Boolean && (Boolean) overVal);
        });

        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, buttons.getPreferredSize().height));
        center.add(buttons);
        center.add(scroll);

        add(form,   BorderLayout.NORTH);
        add(center, BorderLayout.CENTER);

        // ---------- BUTTON LOGIC ----------
        createBtn.addActionListener(e -> onCreate());
        updateBtn.addActionListener(e -> onUpdate());
        deleteBtn.addActionListener(e -> onDelete());
    }

    private void onItemsChanged(java.util.List<Item> items) {
        SwingUtilities.invokeLater(() -> {
            model.setRowCount(0);
            for (Item it : items) {
                if (!it.deleted()) {
                    model.addRow(new Object[] {
                            it.itemId(),
                            it.tagId(),
                            it.position().name(),
                            it.isOverdue() != null && it.isOverdue()
                    });
                }
            }
        });
    }

    private void onCreate() {
        String tagId = tagIdField.getText().trim();
        Position pos = (Position) positionCombo.getSelectedItem();
        boolean overdue = overdueCheck.isSelected();

        if (tagId.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Tag ID is required", "Validation", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (pos == null) {
            JOptionPane.showMessageDialog(this, "Position is required", "Validation", JOptionPane.ERROR_MESSAGE);
            return;
        }
        itemClient.createItem(tagId, pos, overdue);
        clearForm();
    }

    private void onUpdate() {
        String idText = itemIdField.getText().trim();
        if (idText.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Select an item to update", "Validation", JOptionPane.ERROR_MESSAGE);
            return;
        }
        int itemId = Integer.parseInt(idText);

        String tagId = tagIdField.getText().trim();
        Position pos = (Position) positionCombo.getSelectedItem();
        boolean overdue = overdueCheck.isSelected();

        if (tagId.isEmpty() || pos == null) {
            JOptionPane.showMessageDialog(this, "Tag ID and Position are required", "Validation", JOptionPane.ERROR_MESSAGE);
            return;
        }
        itemClient.updateItem(new Item(itemId, tagId, pos, overdue, false));
        clearForm();
    }

    private void onDelete() {
        String idText = itemIdField.getText().trim();
        if (idText.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Select an item to delete", "Validation", JOptionPane.ERROR_MESSAGE);
            return;
        }
        int itemId = Integer.parseInt(idText);
        itemClient.deleteItem(itemId); // soft delete
        clearForm();
    }

    private void clearForm() {
        itemIdField.setText("");
        tagIdField.setText("");
        positionCombo.setSelectedItem(null);
        overdueCheck.setSelected(false);
        deletedCheck.setSelected(false);
        table.clearSelection();
    }
}
