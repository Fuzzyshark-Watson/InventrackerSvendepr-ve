package Fuzzcode.Client.ui.models;

import Fuzzcode.Server.model.Order;
import Fuzzcode.Client.ui.Order.OrderStore;

import javax.swing.table.AbstractTableModel;
import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;

public class OrdersTableModel extends AbstractTableModel implements OrderStore.Listener {

    public static final int COL_ORDER_ID    = 0;
    public static final int COL_CREATED     = 1;
    public static final int COL_START       = 2;
    public static final int COL_END         = 3;
    public static final int COL_CUSTOMER_ID = 4;
    public static final int COL_LOGGED_BY   = 5;
    public static final int COL_DELETED     = 6; // optional, or HOME_COUNT etc

    private final List<Order> rows = new ArrayList<>();

    public OrdersTableModel() {
        OrderStore.getInstance().addListener(this);
    }

    // Called by OrderStore whenever its orders change
    @Override
    public void onOrdersChanged(List<Order> orders) {
        SwingUtilities.invokeLater(() -> {
            rows.clear();
            rows.addAll(orders);  // store already filtered out deleted()
            fireTableDataChanged();
        });
    }

    public Integer getOrderIdAtModelRow(int modelRow) {
        if (modelRow < 0 || modelRow >= rows.size()) return null;
        return rows.get(modelRow).orderId();
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return 6; // or however many you want to show
    }

    @Override
    public String getColumnName(int column) {
        return switch (column) {
            case COL_ORDER_ID    -> "Order ID";
            case COL_CREATED     -> "Created";
            case COL_START       -> "Start";
            case COL_END         -> "End";
            case COL_CUSTOMER_ID -> "Customer";
            case COL_LOGGED_BY   -> "Logged By";
            default -> "";
        };
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        Order o = rows.get(rowIndex);
        return switch (columnIndex) {
            case COL_ORDER_ID    -> o.orderId();
            case COL_CREATED     -> o.createdDate();
            case COL_START       -> o.startDate();
            case COL_END         -> o.endDate();
            case COL_CUSTOMER_ID -> o.customerId();
            case COL_LOGGED_BY   -> o.loggedById();
            default -> null;
        };
    }
}
