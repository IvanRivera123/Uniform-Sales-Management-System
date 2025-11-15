package main;
import java.sql.*;
import java.util.Scanner;
import java.util.List;
import java.util.ArrayList;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;

public class SalesManager {
    static final String RESET = "\u001B[0m";
    static final String GREEN = "\u001B[32m";
    static final String RED = "\u001B[31m";
    static final String YELLOW = "\u001B[33m";

    // Main sales menu
    public static void salesMenu(Connection conn, Scanner sc, String username, String role, int userId) {
        int choice = 0;
        do {
            try {
                MainDB.clearScreen();
                System.out.println("╔══════════════════════════════════════════════════════════╗");
                System.out.println("║                 SALES MANAGEMENT MENU                    ║");
                System.out.println("╚══════════════════════════════════════════════════════════╝");

                // Logged-in prompt
                System.out.println("            Logged in as: " + username + " (" + role.toUpperCase() + ")");
                System.out.println("────────────────────────────────────────────────────────────");

                System.out.println("[1] Process Pending Quotation");
                System.out.println("[2] View Transaction History");
                System.out.println("[3] Back");
                System.out.println("────────────────────────────────────────────────────────────");
                System.out.print("Enter your choice ➤ ");

                try {
                    choice = Integer.parseInt(sc.nextLine().trim());
                } catch (NumberFormatException e) {
                    System.out.println(RED + "Invalid input! Please enter a number." + RESET);
                    MainDB.pause();
                    continue;
                }

                switch (choice) {
                    case 1 -> processPendingQuotation(conn, sc, userId);
                    case 2 -> TransactionHistory.viewTransactionHistory(conn);
                    case 3 -> {}
                    default -> {
                        System.out.println(RED + "Invalid choice!" + RESET);
                        MainDB.pause();
                    }
                }
            } catch (Exception e) {
                System.out.println(RED + "Error: " + e.getMessage() + RESET);
                MainDB.pause();
            }
        } while (choice != 3);
    }


    // Process pending quotations and record sales
    public static void processPendingQuotation(Connection conn, Scanner sc, int userId) {
        MainDB.clearScreen();
        System.out.println("╔═══════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                        PENDING QUOTATIONS                                 ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════════════════╝");

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
            boolean hasQuotations = false;

            // Table header
            System.out.printf("%-8s│ %-20s│ %-11s│ %-10s│ %-20s%n",
                    "ID", "User", "Total", "Status", "Created At");
            System.out.println("────────┼─────────────────────┼────────────┼───────────┼────────────────────");

            while (rs.next()) {
                hasQuotations = true;
                int id = rs.getInt("id");
                String user = rs.getString("username");
                if (user.length() > 20) user = user.substring(0, 17) + "..."; 
                double total = rs.getDouble("total_amount");
                String status = rs.getString("status");
                Timestamp createdAt = rs.getTimestamp("created_at");

                System.out.printf("%-8d│ %-20s│ ₱%10.2f│ %-10s│ %-20s%n",
                        id, user, total, status, createdAt);

                quotationIds.add(id);
                users.add(user);
                totals.add(total);
                createdAtList.add(createdAt);
            }

            if (!hasQuotations) {
                System.out.println(YELLOW + "No pending quotations found." + RESET);
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

            // Prepare receipt details
            List<String> receiptProducts = new ArrayList<>();
            List<Integer> receiptQtys = new ArrayList<>();
            List<Double> receiptTotals = new ArrayList<>();
            double grandTotal = 0;

            String sqlItems = "SELECT product_id, quantity FROM quotation_items WHERE quotation_id=?";
            try (PreparedStatement psItems = conn.prepareStatement(sqlItems)) {
                psItems.setInt(1, selectedId);
                try (ResultSet rsItems = psItems.executeQuery()) {

                    while (rsItems.next()) {
                        int productId = rsItems.getInt("product_id");
                        int qty = rsItems.getInt("quantity");

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

                                    // Record sale
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

            // ===== Generate Receipt as Image =====
            String userHome = System.getProperty("user.home");
            String downloadsPath = userHome + File.separator + "Downloads";
            new File(downloadsPath).mkdirs(); // ensure folder exists

            // === CONFIG ===
            int width = 500;
            int padding = 20;
            int lineHeight = 25;

            // Height calculation (auto expand based on wrapped product lines)
            int estimatedProductLines = 0;

            for (String product : receiptProducts) {
                int chunks = (int) Math.ceil(product.length() / 32.0); // wrap at ~32 chars
                estimatedProductLines += chunks;
            }

            int totalLines = 15 + estimatedProductLines;
            int height = padding * 2 + lineHeight * totalLines;

            BufferedImage receiptImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = receiptImage.createGraphics();

            // Background
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);

            // Smooth text
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            // ====== TITLE ======
            g.setColor(new Color(30, 144, 255));
            g.setFont(new Font("Arial", Font.BOLD, 22));
            g.drawString("USMS SYSTEM ", padding, padding + lineHeight);

            g.setColor(new Color(248, 238, 53));
            g.setFont(new Font("Arial", Font.BOLD, 22));
            g.drawString("STI College ProWare", padding, padding + lineHeight * 2);

            int y = padding + lineHeight * 3;

            // ====== INFO ======
            g.setColor(Color.BLACK);
            g.setFont(new Font("Arial", Font.PLAIN, 14));

            g.drawString("Receipt No : " + selectedId, padding, y); y += lineHeight;
            g.drawString("Customer   : " + users.get(index), padding, y); y += lineHeight;
            g.drawString("Cashier ID : " + userId, padding, y); y += lineHeight;
            g.drawString("Date       : " + createdAtList.get(index), padding, y); 
            y += lineHeight + 5;

            // line separator
            g.drawLine(padding, y, width - padding, y);
            y += lineHeight;

            // ====== TABLE HEADER ======
            g.setFont(new Font("Arial", Font.BOLD, 14));
            g.drawString("Product", padding, y);
            g.drawString("Qty", width - 160, y);
            g.drawString("Amount", width - 80, y);
            y += lineHeight;

            g.drawLine(padding, y, width - padding, y);
            y += lineHeight;

            // ====== PRODUCTS (with auto-wrap) ======
            g.setFont(new Font("Arial", Font.PLAIN, 14));

            for (int i = 0; i < receiptProducts.size(); i++) {

                String name = receiptProducts.get(i);
                int qty = receiptQtys.get(i);
                double total = receiptTotals.get(i);

                // Wrap long names every 32 chars
                while (name.length() > 32) {
                    String chunk = name.substring(0, 32);
                    g.drawString(chunk, padding, y);
                    y += lineHeight;
                    name = name.substring(32);
                }

                // Final line of product includes qty + total
                g.drawString(name, padding, y);
                g.drawString(String.valueOf(qty), width - 150, y);
                g.drawString("₱" + String.format("%.2f", total), width - 80, y);
                y += lineHeight;
            }

            g.drawLine(padding, y, width - padding, y);
            y += lineHeight;

            // ====== PAYMENT + TOTAL ======
            g.drawString("Payment Method: " + paymentMethod, padding, y);
            y += lineHeight;

            g.drawLine(padding, y, width - padding, y);
            y += lineHeight;

            g.setFont(new Font("Arial", Font.BOLD, 16));
            g.drawString("TOTAL DUE:", padding, y);
            g.drawString("₱" + String.format("%.2f", grandTotal), width - 120, y);
            y += lineHeight * 2;

            // ====== FOOTER ======
            g.setFont(new Font("Arial", Font.PLAIN, 14));
            g.setColor(new Color(34, 139, 34));
            g.drawString("We appreciate your purchase", padding, y);
            y += lineHeight;

         

            // Finish
            g.dispose();

            File receiptFile = new File(downloadsPath, "receipt_" + selectedId + ".png");
            ImageIO.write(receiptImage, "png", receiptFile);

            System.out.println(GREEN + "Quotation #" + selectedId + " processed successfully!" + RESET);
            System.out.println(YELLOW + "Receipt saved as image: " + receiptFile.getAbsolutePath() + RESET);

            MainDB.pause();

        } catch (SQLException | java.io.IOException e) {
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
