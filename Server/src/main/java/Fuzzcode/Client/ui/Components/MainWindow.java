package Fuzzcode.Client.ui.Components;

import Fuzzcode.Client.ui.Components.panels.*;
import Fuzzcode.Client.ui.Order.OrderClient;
import Fuzzcode.Client.ui.OrderItem.OrderItemClient;
import Fuzzcode.Client.ui.models.OrdersTableModel;
import Fuzzcode.Client.ui.models.ReadsTableModel;
import Fuzzcode.Client.ui.styles.DarkFrame;

import javax.swing.*;
import java.awt.*;

public class MainWindow extends DarkFrame {
    private final LogPanel logPanel;

    public MainWindow(MainUI ui, String username) {
        super("Main — " + username);
        setUndecorated(true);
        JPanel grid = new JPanel(new GridLayout(2, 2, 5, 5));

        OrdersTableModel ordersTableModel = new OrdersTableModel();
        ReadsTableModel  readsTableModel  = new ReadsTableModel();

        OrderItemClient.getInstance().bindReadsModel(readsTableModel);
        // ORDERS Top Left Panel
        OrdersPanel ordersPanel = new OrdersPanel(orderId -> {
            OrderItemClient.getInstance().requestItemsForOrder(orderId);
        }, ordersTableModel);
        grid.add(ordersPanel);

        // READS Top Right Panel
        ReadsPanel readsPanel = new ReadsPanel(readsTableModel);
        grid.add(readsPanel);

        // "CRUDS" Bottom Left Panel
        CrudsPanel crudsPanel = new CrudsPanel();
        grid.add(crudsPanel);

        // "Terminal Window" Bottom Right Panel
        this.logPanel = new LogPanel(ui);
        grid.add(logPanel);

        setContentPane(grid);
        setSize(1400, 800);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Initial orders snapshot
        OrderClient.getInstance().requestAll();
    }

    public void appendToLog(String s) {
        if (logPanel != null) {
            logPanel.append(s);
        } else {
            System.out.println(s);
        }
    }
}
