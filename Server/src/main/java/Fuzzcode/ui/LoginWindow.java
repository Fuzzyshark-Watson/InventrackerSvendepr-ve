package Fuzzcode.ui;

import Fuzzcode.model.Order;
import Fuzzcode.security.JwtAuthenticator;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.CompletionStage;

public class LoginWindow extends JFrame {

    // 👇 removed "throws Exception"
    public LoginWindow() {
        super("Login");

        JTextField userField = new JTextField(16);
        JPasswordField passField = new JPasswordField(16);
        JButton loginButton = new JButton("Login");

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0; panel.add(new JLabel("User:"), gbc);
        gbc.gridx = 1; gbc.gridy = 0; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        panel.add(userField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        panel.add(new JLabel("Password:"), gbc);
        gbc.gridx = 1; gbc.gridy = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        panel.add(passField, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2; gbc.anchor = GridBagConstraints.CENTER;
        panel.add(loginButton, gbc);

        loginButton.addActionListener(e -> {
            String user = userField.getText().trim();
            char[] pass = passField.getPassword();
            if (user.isEmpty() || pass.length == 0) {
                JOptionPane.showMessageDialog(this, "Please enter username and password.",
                        "Missing info", JOptionPane.WARNING_MESSAGE);
                Arrays.fill(pass, '\0');
                return;
            }

            try {
                if ("admin".equals(user) && Arrays.equals(pass, "root".toCharArray())) {
                    // Build JWT with your helper inside JwtAuthenticator
                    String token = JwtAuthenticator.issueHmacTestToken(
                            "system-client",
                            "ws-service",
                            "e3f7a9c4b8d1f0a2c6e9d4b3f7a8c1e2d3f4b5a6c7d8e9f0a1b2c3d4e5f6a7b8"
                                    .getBytes(StandardCharsets.UTF_8),
                            user,      // subject
                            "admin",   // scope (space-delimited if multiple)
                            3600       // ttl seconds
                    );

                    new MainWindow(user, token).setVisible(true);
                    dispose();
                } else {
                    JOptionPane.showMessageDialog(this, "Invalid username or password.",
                            "Login failed", JOptionPane.ERROR_MESSAGE);
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage(),
                        "Login error", JOptionPane.ERROR_MESSAGE);
            } finally {
                Arrays.fill(pass, '\0');
            }
        });

        setContentPane(panel);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    static class MainWindow extends JFrame {
        private final JTextArea log = new JTextArea();
        private WebSocket socket;
        private final DefaultListModel<String> readsModel = new DefaultListModel<>();
        private final javax.swing.table.DefaultTableModel ordersModel =
                new javax.swing.table.DefaultTableModel(
                        new Object[]{"Order ID","Created Date","Start Date","End Date","Customer ID","Logged by ID","Home (count)"}, 0);

        // Fast lookups to keep counts up to date
        private final java.util.Map<Integer, Integer> orderRow = new java.util.HashMap<>();
        private final java.util.Map<Integer, java.util.Set<Integer>> orderItems = new java.util.HashMap<>();
        private final java.util.Map<Integer, String> itemPosition = new java.util.HashMap<>();

        private final DefaultTableModel readsTableModel =
                new DefaultTableModel(new Object[]{"Item ID", "Position"}, 0);
        private final JTable readsTable = new JTable(readsTableModel);

        private Integer readsForOrderId = null;

        MainWindow(String username, String jwt) {
            super("Main — " + username);

            // Top Left Panel
            JTable ordersTable = new JTable(ordersModel);
            ordersTable.setFillsViewportHeight(true);
            ordersTable.setRowHeight(22);
            ordersTable.setGridColor(Color.DARK_GRAY);
            ordersTable.setAutoCreateRowSorter(true);
            ordersTable.getTableHeader().setReorderingAllowed(false);
            ordersTable.getTableHeader().setBackground(new Color(230, 230, 230));
            ordersTable.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 13));
            ordersTable.setFont(new Font("SansSerif", Font.PLAIN, 12));
            ordersTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            ordersTable.getSelectionModel().addListSelectionListener(e -> {
                if (e.getValueIsAdjusting()) return;
                int viewRow = ordersTable.getSelectedRow();
                if (viewRow < 0) return;

                // convert view index → model index (because sorting may be enabled)
                int modelRow = ordersTable.convertRowIndexToModel(viewRow);
                int orderId = (Integer) ordersModel.getValueAt(modelRow, 0);
                populateReadsForOrder(orderId);
            });

            JPanel ordersPanel = new JPanel(new BorderLayout());
            ordersPanel.setBorder(BorderFactory.createTitledBorder("Orders"));
            ordersPanel.add(new JScrollPane(ordersTable), BorderLayout.CENTER);

            // Top Right Panel
            JPanel readsPanel = new JPanel(new BorderLayout());
            readsPanel.setBorder(BorderFactory.createTitledBorder("Reads"));
            readsTable.setFillsViewportHeight(true);
            readsTable.setRowHeight(22);
            readsTable.setAutoCreateRowSorter(true);
            readsPanel.add(new JScrollPane(readsTable), BorderLayout.CENTER);

            // Bottom Left Panel
            JPanel crudsPanel = new JPanel(new BorderLayout());
            crudsPanel.setBorder(BorderFactory.createTitledBorder("Cruds"));
            crudsPanel.add(new JLabel("CRUD controls will go here."), BorderLayout.CENTER);

            // Bottom Right Panel
            JPanel logPanel = new JPanel(new BorderLayout());
            logPanel.setBorder(BorderFactory.createTitledBorder("Log"));
            log.setEditable(false);
            logPanel.add(new JScrollPane(log), BorderLayout.CENTER);
            JButton sendPingBtn = new JButton("Send Ping");
            sendPingBtn.addActionListener(e -> {
                if (socket != null) socket.sendText("ping_from_client", true);
            });
            crudsPanel.add(sendPingBtn, BorderLayout.NORTH);

            // --- Combine into a 2x2 grid ---
            JPanel grid = new JPanel(new GridLayout(2, 2, 5, 5)); // 5px gap between cells
            grid.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
            grid.add(ordersPanel);
            grid.add(readsPanel);
            grid.add(crudsPanel);
            grid.add(logPanel);

            // Frame setup
            setContentPane(grid);
            setSize(1400, 800);
            setLocationRelativeTo(null);
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            // Handle resize to enforce 2/3–1/3 split
            addComponentListener(new java.awt.event.ComponentAdapter() {
                @Override
                public void componentResized(java.awt.event.ComponentEvent e) {
                    int total = getContentPane().getHeight();
                    int bottomHeight = total / 3;
                    int topHeight = total - bottomHeight;
                    ordersPanel.setPreferredSize(new Dimension(getWidth(), topHeight));
                    readsPanel.setPreferredSize(new Dimension(getWidth(), topHeight));
                    crudsPanel.setPreferredSize(new Dimension(getWidth(), bottomHeight));
                    logPanel.setPreferredSize(new Dimension(getWidth(), bottomHeight));
                    getContentPane().revalidate();
                }
            });

            connect(jwt);
        }
        private void connect(String jwt) {
            appendToLog("Connecting to ws://localhost:8080/ws ...");
            HttpClient client = HttpClient.newHttpClient();
            try {
                socket = client.newWebSocketBuilder()
                        .header("Authorization", "Bearer " + jwt)
                        .buildAsync(URI.create("ws://localhost:8080/ws"), new WsListener(this))
                        .join();
                appendToLog("Connected.");
                socket.sendText("fetch : orders_full", true);
            } catch (Exception e) {
                appendToLog("WS connect failed: " + e.getMessage());
                JOptionPane.showMessageDialog(this,
                        "WebSocket connection failed:\n" + e.getMessage(),
                        "WS Error", JOptionPane.ERROR_MESSAGE);
            }
        }
        void onMessage(String msg) {
            SwingUtilities.invokeLater(() -> {
                appendToLog("WS <- " + msg); // your existing logger

                if (msg.startsWith("order:")) {
                    // order:<orderId>:<created>:<start>:<end>:<customerId>:<loggedById>
                    String[] p = msg.split(":", 7);
                    if (p.length >= 7) {
                        int orderId    = Integer.parseInt(p[1]);
                        String created = nz(p[2]);
                        String start   = nz(p[3]);
                        String end     = nz(p[4]);
                        int customerId = Integer.parseInt(p[5]);
                        int loggedById = Integer.parseInt(p[6]);
                        upsertOrderRow(orderId, created, start, end, customerId, loggedById);
                    }
                } else if (msg.startsWith("link:")) {
                    // link:<orderId>:<itemId>:<position>
                    String[] p = msg.split(":", 4);
                    if (p.length == 4) {
                        linkItem(Integer.parseInt(p[1]), Integer.parseInt(p[2]), p[3]);
                    }
                } else if (msg.startsWith("pos:")) {
                    // pos:<itemId>:<position>
                    String[] p = msg.split(":", 3);
                    if (p.length == 3) {
                        setItemPosition(Integer.parseInt(p[1]), p[2]);
                    }
                } else if (msg.startsWith("unlink:")) {
                    // unlink:<orderId>:<itemId>
                    String[] p = msg.split(":", 3);
                    if (p.length == 3) {
                        unlinkItem(Integer.parseInt(p[1]), Integer.parseInt(p[2]));
                    }
                }
            });
        }
        private static String nz(String s) { return (s == null || s.isBlank()) ? "" : s; }
        private void upsertOrderRow(int orderId, String created, String start, String end, int customerId, int loggedById) {
            Integer row = orderRow.get(orderId);
            int homeCount = computeHomeCount(orderId);

            if (row == null) {
                int newRow = ordersModel.getRowCount();
                ordersModel.addRow(new Object[]{orderId, created, start, end, customerId, loggedById, homeCount});
                orderRow.put(orderId, newRow);
            } else {
                ordersModel.setValueAt(created,    row, 1);
                ordersModel.setValueAt(start,      row, 2);
                ordersModel.setValueAt(end,        row, 3);
                ordersModel.setValueAt(customerId, row, 4);
                ordersModel.setValueAt(loggedById, row, 5);
                ordersModel.setValueAt(homeCount,  row, 6);
            }
        }
        private int computeHomeCount(int orderId) {
            java.util.Set<Integer> items = orderItems.get(orderId);
            if (items == null) return 0;
            int count = 0;
            for (int itemId : items) {
                if ("HOME".equals(itemPosition.get(itemId))) count++;
            }
            return count;
        }
        private void linkItem(int orderId, int itemId, String pos) {
            orderItems.computeIfAbsent(orderId, k -> new java.util.HashSet<>()).add(itemId);
            itemPosition.put(itemId, pos);
            refreshHomeCount(orderId);
            if (readsForOrderId != null && readsForOrderId == orderId) {
                // add or update the row in reads table
                upsertReadsRow(itemId, pos);
            }
        }

        private void unlinkItem(int orderId, int itemId) {
            var items = orderItems.get(orderId);
            if (items != null) items.remove(itemId);
            refreshHomeCount(orderId);
            if (readsForOrderId != null && readsForOrderId == orderId) {
                removeReadsRow(itemId);
            }
        }

        private void setItemPosition(int itemId, String pos) {
            itemPosition.put(itemId, pos);
            // Update whichever order has this item
            Integer owningOrder = null;
            for (var e : orderItems.entrySet()) {
                if (e.getValue().contains(itemId)) { owningOrder = e.getKey(); break; }
            }
            if (owningOrder != null) {
                refreshHomeCount(owningOrder);
                if (readsForOrderId != null && readsForOrderId.equals(owningOrder)) {
                    upsertReadsRow(itemId, pos);
                }
            }
        }
        private void upsertReadsRow(int itemId, String pos) {
            // itemId is unique per row in Reads
            for (int r = 0; r < readsTableModel.getRowCount(); r++) {
                if (((Integer) readsTableModel.getValueAt(r, 0)) == itemId) {
                    readsTableModel.setValueAt(pos, r, 1);
                    return;
                }
            }
            readsTableModel.addRow(new Object[]{itemId, pos});
        }

        private void removeReadsRow(int itemId) {
            for (int r = 0; r < readsTableModel.getRowCount(); r++) {
                if (((Integer) readsTableModel.getValueAt(r, 0)) == itemId) {
                    readsTableModel.removeRow(r);
                    return;
                }
            }
        }

        private void refreshHomeCount(int orderId) {
            Integer row = orderRow.get(orderId);
            if (row != null) {
                ordersModel.setValueAt(computeHomeCount(orderId), row, 6);
            }
        }
        private void populateReadsForOrder(int orderId) {
            readsForOrderId = orderId;
            readsTableModel.setRowCount(0); // clear

            var items = orderItems.get(orderId);
            if (items == null || items.isEmpty()) {
                // Optional: ask server to send links for this specific order
                if (socket != null) socket.sendText("fetch:order_items:" + orderId, true);
                return;
            }

            for (int itemId : items) {
                String pos = itemPosition.getOrDefault(itemId, "");
                readsTableModel.addRow(new Object[]{itemId, pos});
            }
        }
        void appendToLog(String s) {
            SwingUtilities.invokeLater(() -> {
                log.append(s + "\n");
                log.setCaretPosition(log.getDocument().getLength());
            });
        }
    }

    static class WsListener implements WebSocket.Listener {
        private final MainWindow ui;
        WsListener(MainWindow ui) { this.ui = ui; }

        @Override public void onOpen(WebSocket ws) {
            ui.appendToLog("WS onOpen");
            ws.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            // forward to your parser (this updates the table)
            ui.onMessage(data.toString());
            ws.request(1);
            return null;
        }

        @Override public void onError(WebSocket ws, Throwable error) {
            ui.appendToLog("WS error: " + error);
        }

        @Override public CompletionStage<?> onClose(WebSocket ws, int code, String reason) {
            ui.appendToLog("WS closed [" + code + "]: " + reason);
            return null;
        }
    }


    public static void main(String[] args) {
        SwingUtilities.invokeLater(LoginWindow::new);
    }
}
