package Fuzzcode.Client.ui.Components.panels;

import Fuzzcode.Client.ui.models.OrdersTableModel;

import javax.swing.*;
import java.awt.*;
import java.util.function.IntConsumer;

public class OrdersPanel extends JPanel {
    private final JTable table;

    public OrdersPanel(IntConsumer onOrderSelected, OrdersTableModel ordersModel) {
        super(new BorderLayout());
        this.table = new JTable(ordersModel);

        // ---- table styling ----
        table.setFillsViewportHeight(true);
        table.setRowHeight(22);
        table.setGridColor(Color.DARK_GRAY);
        table.setAutoCreateRowSorter(true);
        table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().setBackground(new Color(60, 60, 60));
        table.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 13));
        table.setFont(new Font("SansSerif", Font.PLAIN, 12));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // ---- selection -> callback ----
        table.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            int viewRow = table.getSelectedRow();
            if (viewRow < 0) return;

            int modelRow = table.convertRowIndexToModel(viewRow);
            Integer orderId = ordersModel.getOrderIdAtModelRow(modelRow);
            if (orderId != null && onOrderSelected != null) {
                onOrderSelected.accept(orderId);
            }
        });

        setBorder(BorderFactory.createTitledBorder("Orders"));
        add(new JScrollPane(table), BorderLayout.CENTER);
    }
}
