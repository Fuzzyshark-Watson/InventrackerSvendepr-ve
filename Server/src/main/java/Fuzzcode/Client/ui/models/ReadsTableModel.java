package Fuzzcode.Client.ui.models;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

public class ReadsTableModel extends AbstractTableModel {

    public static final int COL_ITEM_ID   = 0;
    public static final int COL_TAG_ID    = 1;
    public static final int COL_POSITION  = 2;
    public static final int COL_OVERDUE   = 3;

    public static final class Row {
        public final int itemId;
        public final String tagId;
        public final String position;
        public final boolean overdue;

        public Row(int itemId, String tagId, String position, boolean overdue) {
            this.itemId = itemId;
            this.tagId = tagId;
            this.position = position;
            this.overdue = overdue;
        }
    }

    private int currentOrderId = 0;
    private final List<Row> rows = new ArrayList<>();

    @Override public int getRowCount() { return rows.size(); }
    @Override public int getColumnCount() { return 4; }

    @Override
    public String getColumnName(int column) {
        return switch (column) {
            case COL_ITEM_ID  -> "Item ID";
            case COL_TAG_ID   -> "Tag";
            case COL_POSITION -> "Position";
            case COL_OVERDUE  -> "Overdue";
            default -> "";
        };
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        Row r = rows.get(rowIndex);
        return switch (columnIndex) {
            case COL_ITEM_ID  -> r.itemId;
            case COL_TAG_ID   -> r.tagId;
            case COL_POSITION -> r.position;
            case COL_OVERDUE  -> r.overdue;
            default -> null;
        };
    }

    public void setSnapshotForOrder(int orderId, List<Row> newRows) {
        this.currentOrderId = orderId;
        rows.clear();
        rows.addAll(newRows);
        fireTableDataChanged();
    }

    public int getCurrentOrderId() { return currentOrderId; }
}

