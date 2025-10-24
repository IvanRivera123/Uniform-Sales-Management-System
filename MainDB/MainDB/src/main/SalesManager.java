package main;
import java.sql.*;
import java.util.Scanner;
import java.util.List;
import java.util.ArrayList;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;

public class SalesManager {
    static final String RESET = "\u001B[0m";
    static final String GREEN = "\u001B[32m";
    static final String RED = "\u001B[31m";
    static final String YELLOW = "\u001B[33m";

    // Main sales menu
    public static void salesMenu(Connection conn, Scanner sc, int userId) {
        int choice = 0;
        do {
            MainDB.clearScreen();
            System.out.println("===============================================================");
            System.out.println("                       SALES MANAGEMENT MENU");
            System.out.println("===============================================================");
            System.out.println("1. Process Pending Quotation");
            System.out.println("2. View Transaction History");
            System.out.println("3. Back");
            System.out.print("Enter choice: ");

            try {
                choice = Integer.parseInt(sc.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println(RED + "Invalid input! Enter a number." + RESET);
                MainDB.pause();
                continue;
            }

            switch (choice) {
                case 1 -> processPendingQuotation(conn, sc, userId);
                case 2 -> TransactionHistory.viewTransactionHistory(conn);
                case 3 -> { return; }
                default -> {
                    System.out.println(RED + "Invalid choice!" + RESET);
                    MainDB.pause();
                }
            }
        } while (choice != 3);
    }

    // Process pending quotations and record sales
    public static void processPendingQuotation(Connection conn, Scanner sc, int userId) {
        MainDB.clearScreen();
        System.out.println("=================================================================");
        System.out.println("                       PROCESS PENDING QUOTATION");
        System.out.println("=================================================================");

        String sqlPending = """
            SELECT q.id, u.username, q.total_amount, q.status, q.created_at
            FROM quotations q
            JOIN users u ON q.user_id = u.id
            WHERE q.status='pending'
            ORDER BY q.created_at
        """;

        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sqlPending)) {

            List<Integer> quotationIds = new ArrayList<>();
            List<String> users = new ArrayList<>();
            List<Double> totals = new ArrayList<>();
            List<Timestamp> createdAtList = new ArrayList<>();
            int count = 0;

            System.out.printf("%-5s %-15s %-12s %-10s %-20s%n", "ID", "User", "Total", "Status", "Created At");
            System.out.println("---------------------------------------------------------------");

            while (rs.next()) {
                int id = rs.getInt("id");
                String user = rs.getString("username");
                double total = rs.getDouble("total_amount");
                String status = rs.getString("status");
                Timestamp createdAt = rs.getTimestamp("created_at");

                System.out.printf("%-5d %-15s %-12.2f %-10s %-20s%n", id, user, total, status, createdAt);
                quotationIds.add(id);
                users.add(user);
                totals.add(total);
                createdAtList.add(createdAt);
                count++;
            }

            if (count == 0) {
                System.out.println("No pending quotations.");
                MainDB.pause();
                return;
            }

            System.out.print("\nEnter Quotation ID to process or BACK to exit: ");
            String input = sc.nextLine().trim();
            if (input.equalsIgnoreCase("back")) return;

            int selectedId;
            int index = -1;
            try {
                selectedId = Integer.parseInt(input);
                index = quotationIds.indexOf(selectedId);
                if (index == -1) {
                    System.out.println(RED + "Invalid quotation ID!" + RESET);
                    MainDB.pause();
                    return;
                }
            } catch (NumberFormatException e) {
                System.out.println(RED + "Invalid input!" + RESET);
                MainDB.pause();
                return;
            }

            // Confirm payment method
            String paymentMethod;
            while (true) {
                System.out.print("Enter payment method (Cash/GCash): ");
                paymentMethod = sc.nextLine().trim();
                if (paymentMethod.equalsIgnoreCase("back")) return;

                if (paymentMethod.equalsIgnoreCase("Cash") || paymentMethod.equalsIgnoreCase("GCash")) break;
                else System.out.println(RED + "Invalid input! Enter Cash or GCash." + RESET);
            }

            // Prepare to collect item details for receipt
            List<String> receiptProducts = new ArrayList<>();
            List<Integer> receiptQtys = new ArrayList<>();
            List<Double> receiptTotals = new ArrayList<>();
            double grandTotal = 0;

            // Process each item in quotation
            String sqlItems = "SELECT product_id, quantity FROM quotation_items WHERE quotation_id=?";
            try (PreparedStatement psItems = conn.prepareStatement(sqlItems)) {
                psItems.setInt(1, selectedId);
                try (ResultSet rsItems = psItems.executeQuery()) {

                    while (rsItems.next()) {
                        int productId = rsItems.getInt("product_id");
                        int qty = rsItems.getInt("quantity");

                        // Get current product stock and info
                        String sqlStock = "SELECT stock, name, price FROM products WHERE id=?";
                        try (PreparedStatement psStock = conn.prepareStatement(sqlStock)) {
                            psStock.setInt(1, productId);
                            try (ResultSet rsStock = psStock.executeQuery()) {
                                if (rsStock.next()) {
                                    int stock = rsStock.getInt("stock");
                                    String name = rsStock.getString("name");
                                    double price = rsStock.getDouble("price");

                                    if (qty > stock) {
                                        System.out.println(RED + "Not enough stock for product " + name + "! Skipping." + RESET);
                                        continue;
                                    }

                                    int newStock = stock - qty;

                                    // Update stock
                                    try (PreparedStatement psUpdate = conn.prepareStatement(
                                            "UPDATE products SET stock=? WHERE id=?")) {
                                        psUpdate.setInt(1, newStock);
                                        psUpdate.setInt(2, productId);
                                        psUpdate.executeUpdate();
                                    }

                                    // Log inventory
                                    try (PreparedStatement psLog = conn.prepareStatement(
                                            "INSERT INTO inventory_log(product_id, change_type, quantity, previous_stock, new_stock) VALUES(?, 'SALE', ?, ?, ?)")) {
                                        psLog.setInt(1, productId);
                                        psLog.setInt(2, qty);
                                        psLog.setInt(3, stock);
                                        psLog.setInt(4, newStock);
                                        psLog.executeUpdate();
                                    }

                                    // Record sale with user_id
                                    try (PreparedStatement psSale = conn.prepareStatement(
                                            "INSERT INTO sales(product_id, quantity, total_price, payment_method, user_id) VALUES(?, ?, ?, ?, ?)")) {
                                        psSale.setInt(1, productId);
                                        psSale.setInt(2, qty);
                                        psSale.setDouble(3, price * qty);
                                        psSale.setString(4, paymentMethod);
                                        psSale.setInt(5, userId);
                                        psSale.executeUpdate();
                                    }

                                    // Save for receipt
                                    receiptProducts.add(name);
                                    receiptQtys.add(qty);
                                    double totalItem = price * qty;
                                    receiptTotals.add(totalItem);
                                    grandTotal += totalItem;
                                }
                            }
                        }
                    }
                }
            }

            // Mark quotation as completed
            try (PreparedStatement psUpdate = conn.prepareStatement(
                    "UPDATE quotations SET status='completed' WHERE id=?")) {
                psUpdate.setInt(1, selectedId);
                psUpdate.executeUpdate();
            }

            // Prepare receipt
            String userHome = System.getProperty("user.home");
            String downloadsPath = userHome + File.separator + "Downloads";
            File receiptFile = new File(downloadsPath, "receipt_" + selectedId + ".txt");
            receiptFile.getParentFile().mkdirs(); // ensure folder exists

            try (PrintWriter writer = new PrintWriter(receiptFile)) {
                writer.println("========================================");
                writer.println("             USMS STORE RECEIPT         ");
                writer.println("========================================");
                writer.printf("Receipt #: %d%n", selectedId);
                writer.printf("Customer: %s%n", users.get(index));
                writer.printf("Processed By User ID: %d%n", userId);
                writer.printf("Date: %s%n", createdAtList.get(index));
                writer.println("----------------------------------------");
                writer.printf("%-20s %-5s %-10s%n", "Product", "Qty", "Total");
                writer.println("----------------------------------------");

                for (int i = 0; i < receiptProducts.size(); i++) {
                    writer.printf("%-20s %-5d %-10.2f%n", receiptProducts.get(i), receiptQtys.get(i), receiptTotals.get(i));
                }

                writer.println("----------------------------------------");
                writer.printf("Payment Method: %s%n", paymentMethod);
                writer.printf("Grand Total: %.2f%n", grandTotal);
                writer.println("========================================");
                writer.println("Thank you for your purchase!");
            }

            System.out.println(GREEN + "Quotation #" + selectedId + " processed successfully!" + RESET);
            System.out.println(YELLOW + "Receipt saved to: " + receiptFile.getAbsolutePath() + RESET);
            MainDB.pause();

        } catch (SQLException | FileNotFoundException e) {
            System.out.println(RED + "Error processing quotation: " + e.getMessage() + RESET);
            MainDB.pause();
        }
    }

    // Submit a quotation from the user's cart
    public static void submitQuotation(Connection conn, Scanner sc, String username) {
        try {
            int userId = 0;
            String sqlUser = "SELECT id FROM users WHERE username=?";
            try (PreparedStatement ps = conn.prepareStatement(sqlUser)) {
                ps.setString(1, username);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) userId = rs.getInt("id");
                else {
                    System.out.println("User not found!");
                    return;
                }
            }

            String sqlCart = "INSERT INTO quotations(user_id, product_id, quantity) " +
                             "SELECT c.user_id, c.product_id, c.quantity FROM cart c WHERE c.user_id=?";
            try (PreparedStatement ps = conn.prepareStatement(sqlCart)) {
                ps.setInt(1, userId);
                int rows = ps.executeUpdate();
                if (rows > 0) {
                    String sqlClear = "DELETE FROM cart WHERE user_id=?";
                    try (PreparedStatement psClear = conn.prepareStatement(sqlClear)) {
                        psClear.setInt(1, userId);
                        psClear.executeUpdate();
                    }
                    System.out.println("Quotation submitted successfully!");
                } else {
                    System.out.println("Your cart is empty. Nothing to submit.");
                }
            }

        } catch (SQLException e) {
            System.out.println("Error submitting quotation: " + e.getMessage());
        }

        System.out.print("Press Enter to continue...");
        sc.nextLine();
    }
}
