package Fuzzcode.Client.ui.Components.panels;

import Fuzzcode.Server.model.Order;
import Fuzzcode.Client.ui.Order.OrderClient;
import Fuzzcode.Client.ui.Order.OrderStore;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class OrderCrudPanel extends JPanel {

    private final OrderStore orderStore;
    private final OrderClient orderClient;

    private JTable orderTable;
    private DefaultTableModel tableModel;

    private JTextField orderIdField;
    private JComboBox<PersonItem> customerCombo;
    private JComboBox<PersonItem> loggedByCombo;

    private JTextField createdDateField;
    private JTextField startDateField;
    private JTextField endDateField;

    private final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    public OrderCrudPanel() {
        this.orderStore  = OrderStore.getInstance();
        this.orderClient = OrderClient.getInstance();

        setLayout(new BorderLayout(5, 5));
        initUi();

        // whenever the store changes, refresh table
        orderStore.addListener(this::onOrdersChanged);

        // load initial snapshot
        orderClient.requestAll();
    }

    private void onOrdersChanged(java.util.List<Order> orders) {
        SwingUtilities.invokeLater(() -> {
            tableModel.setRowCount(0);

            for (Order o : orders) {
                // store already filters deleted, but keep guard if needed
                if (!o.deleted()) {
                    tableModel.addRow(new Object[]{
                            o.orderId(),
                            o.createdDate() != null ? o.createdDate().format(DATE_FMT) : "",
                            o.startDate()   != null ? o.startDate().format(DATE_FMT)   : "",
                            o.endDate()     != null ? o.endDate().format(DATE_FMT)     : "",
                            o.customerId(),
                            o.loggedById()
                    });
                }
            }
        });
    }

    private void initUi() {
        // ---------- FORM ----------
        JPanel formPanel = new JPanel(new GridLayout(7, 2, 5, 5));
        formPanel.setBorder(BorderFactory.createTitledBorder("Order Details"));

        orderIdField = new JTextField();
        orderIdField.setEnabled(false);

        customerCombo  = new JComboBox<>();
        loggedByCombo  = new JComboBox<>();

        createdDateField = new JTextField();
        startDateField   = new JTextField();
        endDateField     = new JTextField();

        formPanel.add(new JLabel("Order ID:"));
        formPanel.add(orderIdField);
        formPanel.add(new JLabel("Customer:"));
        formPanel.add(customerCombo);
        formPanel.add(new JLabel("Logged By:"));
        formPanel.add(loggedByCombo);
        formPanel.add(new JLabel("Created date (yyyy-MM-dd):"));
        formPanel.add(createdDateField);
        formPanel.add(new JLabel("Start date (yyyy-MM-dd):"));
        formPanel.add(startDateField);
        formPanel.add(new JLabel("End date (yyyy-MM-dd):"));
        formPanel.add(endDateField);

        // ---------- BUTTONS ----------
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton createBtn = new JButton("Create");
        JButton updateBtn = new JButton("Update");
        JButton deleteBtn = new JButton("Delete (logical)");

        buttonPanel.add(createBtn);
        buttonPanel.add(updateBtn);
        buttonPanel.add(deleteBtn);

        // ---------- TABLE ----------
        String[] columns = {
                "Order ID", "Created", "Start", "End", "Customer ID", "Logged By ID"
        };
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        orderTable = new JTable(tableModel);
        JScrollPane tableScroll = new JScrollPane(orderTable);
        tableScroll.setBorder(BorderFactory.createTitledBorder("Active Orders"));

        // click row → load into form
        orderTable.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;

            int row = orderTable.getSelectedRow();
            if (row < 0) return;

            int modelRow = orderTable.convertRowIndexToModel(row);

            Object idVal      = tableModel.getValueAt(modelRow, 0);
            Object createdVal = tableModel.getValueAt(modelRow, 1);
            Object startVal   = tableModel.getValueAt(modelRow, 2);
            Object endVal     = tableModel.getValueAt(modelRow, 3);
            Object custVal    = tableModel.getValueAt(modelRow, 4);
            Object loggedVal  = tableModel.getValueAt(modelRow, 5);

            orderIdField.setText(idVal != null ? idVal.toString() : "");
            createdDateField.setText(createdVal != null ? createdVal.toString() : "");
            startDateField.setText(startVal != null ? startVal.toString() : "");
            endDateField.setText(endVal != null ? endVal.toString() : "");
            // You can resolve customer/loggedBy combobox selection once PersonCrud is wired.
        });

        JPanel centerContainer = new JPanel();
        centerContainer.setLayout(new BoxLayout(centerContainer, BoxLayout.Y_AXIS));
        buttonPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, buttonPanel.getPreferredSize().height));
        centerContainer.add(buttonPanel);
        centerContainer.add(tableScroll);

        add(formPanel, BorderLayout.NORTH);
        add(centerContainer, BorderLayout.CENTER);

        // ---------- BUTTON LOGIC ----------
        createBtn.addActionListener(e -> onCreateClicked());
        updateBtn.addActionListener(e -> onUpdateClicked());
        deleteBtn.addActionListener(e -> onDeleteClicked());
    }

    private void onCreateClicked() {
        try {
            LocalDate createdDate = parseDateOrNow(createdDateField.getText());
            LocalDate startDate   = parseDateField(startDateField, "Start date");
            LocalDate endDate     = parseDateField(endDateField,   "End date");

            PersonItem cust = (PersonItem) customerCombo.getSelectedItem();
            PersonItem log  = (PersonItem) loggedByCombo.getSelectedItem();

            Integer customerId = cust != null ? cust.id() : null;
            Integer loggedById = log  != null ? log.id()  : null;

            Order newOrder = new Order(
                    0, // server assigns ID
                    createdDate,
                    startDate,
                    endDate,
                    customerId,
                    loggedById,
                    false
            );

            orderClient.createOrder(newOrder);
            clearForm();

        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(),
                    "Validation Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onUpdateClicked() {
        try {
            int orderId = parseIntField(orderIdField, "Order ID");

            LocalDate createdDate = parseDateOrNow(createdDateField.getText());
            LocalDate startDate   = parseDateField(startDateField, "Start date");
            LocalDate endDate     = parseDateField(endDateField,   "End date");

            PersonItem cust = (PersonItem) customerCombo.getSelectedItem();
            PersonItem log  = (PersonItem) loggedByCombo.getSelectedItem();

            Integer customerId = cust != null ? cust.id() : null;
            Integer loggedById = log  != null ? log.id()  : null;

            Order updated = new Order(
                    orderId,
                    createdDate,
                    startDate,
                    endDate,
                    customerId,
                    loggedById,
                    false
            );

            orderClient.updateOrder(updated);
            clearForm();

        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(),
                    "Validation Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onDeleteClicked() {
        try {
            int orderId = parseIntField(orderIdField, "Order ID");
            orderClient.deleteOrder(orderId);
            clearForm();
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(),
                    "Validation Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void clearForm() {
        orderIdField.setText("");
        createdDateField.setText("");
        startDateField.setText("");
        endDateField.setText("");
        customerCombo.setSelectedItem(null);
        loggedByCombo.setSelectedItem(null);
        orderTable.clearSelection();
    }

    // ---------- PARSING HELPERS ----------
    private int parseIntField(JTextField field, String fieldName) {
        String text = field.getText().trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(fieldName + " must be an integer.");
        }
    }

    private LocalDate parseDateField(JTextField field, String fieldName) {
        String text = field.getText().trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(text, DATE_FMT);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException(fieldName + " must be in yyyy-MM-dd format.");
        }
    }

    private LocalDate parseDateOrNow(String text) {
        text = text.trim();
        if (text.isEmpty()) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(text, DATE_FMT);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Created date must be in yyyy-MM-dd format.");
        }
    }

    public record PersonItem(int id, String label) {
        @Override
        public String toString() {
            return label;
        }
    }
}
