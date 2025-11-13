package Fuzzcode.Client.ui.Components.panels;

import Fuzzcode.Server.model.Person;
import Fuzzcode.Server.model.PersonRole;
import Fuzzcode.Client.ui.Person.PersonClient;
import Fuzzcode.Client.ui.Person.PersonStore;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

public class PersonCrudPanel extends JPanel {

    private final PersonStore  personStore;
    private final PersonClient personClient;

    private JTable table;
    private DefaultTableModel model;

    private JTextField idField;
    private JTextField nameField;
    private JComboBox<PersonRole> roleCombo;

    public PersonCrudPanel() {
        this.personStore  = PersonStore.getInstance();
        this.personClient = PersonClient.getInstance();

        setLayout(new BorderLayout(5, 5));
        initUi();

        // react to store changes
        personStore.addListener(this::onPeopleChanged);

        // initial snapshot
        personClient.requestAll();
    }

    private void initUi() {
        // --------- FORM ----------
        JPanel form = new JPanel(new GridLayout(4, 2, 5, 5));
        form.setBorder(BorderFactory.createTitledBorder("Person details"));

        idField = new JTextField();
        idField.setEnabled(false); // server assigns

        nameField = new JTextField();

        roleCombo = new JComboBox<>(PersonRole.values());

        form.add(new JLabel("Person ID:"));
        form.add(idField);
        form.add(new JLabel("Name:"));
        form.add(nameField);
        form.add(new JLabel("Role:"));
        form.add(roleCombo);

        // --------- BUTTONS ----------
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton createBtn = new JButton("Create");
        JButton updateBtn = new JButton("Update");
        JButton deleteBtn = new JButton("Delete");

        buttons.add(createBtn);
        buttons.add(updateBtn);
        buttons.add(deleteBtn);

        // --------- TABLE ----------
        String[] cols = { "Person ID", "Name", "Role" };
        model = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new JTable(model);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createTitledBorder("Active People"));

        // table selection → form
        table.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            int viewRow = table.getSelectedRow();
            if (viewRow < 0) return;
            int row = table.convertRowIndexToModel(viewRow);

            Object idVal   = model.getValueAt(row, 0);
            Object nameVal = model.getValueAt(row, 1);
            Object roleVal = model.getValueAt(row, 2);

            idField.setText(idVal != null ? idVal.toString() : "");
            nameField.setText(nameVal != null ? nameVal.toString() : "");
            if (roleVal != null) {
                roleCombo.setSelectedItem(PersonRole.valueOf(roleVal.toString()));
            }
        });

        // compose
        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, buttons.getPreferredSize().height));
        center.add(buttons);
        center.add(scroll);

        add(form,   BorderLayout.NORTH);
        add(center, BorderLayout.CENTER);

        // --------- BUTTON LOGIC ----------
        createBtn.addActionListener(e -> onCreate());
        updateBtn.addActionListener(e -> onUpdate());
        deleteBtn.addActionListener(e -> onDelete());
    }

    private void onPeopleChanged(java.util.List<Person> people) {
        SwingUtilities.invokeLater(() -> {
            model.setRowCount(0);
            for (Person p : people) {
                if (!p.deleted()) {
                    model.addRow(new Object[] {
                            p.personId(),
                            p.name(),
                            p.role().name()
                    });
                }
            }
        });
    }

    private void onCreate() {
        String name = nameField.getText().trim();
        PersonRole role = (PersonRole) roleCombo.getSelectedItem();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Name is required", "Validation", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (role == null) {
            JOptionPane.showMessageDialog(this, "Role is required", "Validation", JOptionPane.ERROR_MESSAGE);
            return;
        }
        personClient.createPerson(name, role);
        clearForm();
    }

    private void onUpdate() {
        String idText = idField.getText().trim();
        if (idText.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Select a person to update", "Validation", JOptionPane.ERROR_MESSAGE);
            return;
        }
        int id = Integer.parseInt(idText);
        String name = nameField.getText().trim();
        PersonRole role = (PersonRole) roleCombo.getSelectedItem();
        if (name.isEmpty() || role == null) {
            JOptionPane.showMessageDialog(this, "Name and Role are required", "Validation", JOptionPane.ERROR_MESSAGE);
            return;
        }
        personClient.updatePerson(new Person(id, name, role, false));
        clearForm();
    }

    private void onDelete() {
        String idText = idField.getText().trim();
        if (idText.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Select a person to delete", "Validation", JOptionPane.ERROR_MESSAGE);
            return;
        }
        int id = Integer.parseInt(idText);
        personClient.deletePerson(id); // soft delete on server side
        clearForm();
    }

    private void clearForm() {
        idField.setText("");
        nameField.setText("");
        roleCombo.setSelectedItem(null);
        table.clearSelection();
    }
}
