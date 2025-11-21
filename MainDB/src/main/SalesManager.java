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
import java.text.SimpleDateFormat;


public class SalesManager {
    static final String RESET = "\u001B[0m";
    static final String GREEN = "\u001B[32m";
    static final String RED = "\u001B[31m";
    static final String YELLOW = "\u001B[33m";

    // Main sales menu
    public static void salesMenu(Connection conn, Scanner sc, String username, String role, int userId) {
        String input = "";
        do {
            try {
                MainDB.clearScreen();
                System.out.println("            Logged in as: " + username + " (" + role.toUpperCase() + ")");
                System.out.println("╔══════════════════════════════════════════════════════════╗");
                System.out.println("║                 SALES MANAGEMENT MENU                    ║");
                System.out.println("╚══════════════════════════════════════════════════════════╝");

                System.out.println("╭──────────────────────── Options ─────────────────────────╮");
                System.out.println("│ [1] Process Pending Quotation      [X] Logout            │");
                System.out.println("│ [2] View Transaction History                             │");
                System.out.println("╰──────────────────────────────────────────────────────────╯");
                System.out.print("Enter your choice ➤ ");

                input = sc.nextLine().trim().toUpperCase();

                switch (input) {
                    case "1" -> processPendingQuotation(conn, sc, userId);
                    case "2" -> TransactionHistory.viewTransactionHistory(conn);
                    case "X" -> {}
                    default -> {
                        System.out.println(RED + "Invalid choice!" + RESET);
                        MainDB.pause();
                    }
                }
            } catch (Exception e) {
                System.out.println(RED + "Error: " + e.getMessage() + RESET);
                MainDB.pause();
            }
        } while (!input.equals("X"));
    }


    // Process pending quotations and record sales
    public static void processPendingQuotation(Connection conn, Scanner sc, int userId) {
        MainDB.clearScreen();
        System.out.println("╔═══════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                        PENDING QUOTATIONS                                 ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════════════════╝");

        String sqlPending = """
            SELECT q.invoice_number, u.username, q.total_amount, q.created_at
            FROM quotations q
            JOIN users u ON q.user_id = u.id
            WHERE q.status='pending'
            ORDER BY q.created_at
        """;

        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sqlPending)) {

            List<String> invoiceNumbers = new ArrayList<>();
            boolean hasQuotations = false;

            // Table header
            System.out.printf("%-12s│ %-15s│ %-25s│ %-8s │ %-20s%n",
                    "Invoice No", "User", "Product (Size)", "Total", "Created At");
            System.out.println("────────────┼────────────────┼──────────────────────────┼──────────┼──────────────────────────");

            while (rs.next()) {
                hasQuotations = true;
                String invoice = rs.getString("invoice_number");
                String user = rs.getString("username");
                if (user.length() > 15) user = user.substring(0, 12) + "...";

                double total = rs.getDouble("total_amount");
                Timestamp createdAt = rs.getTimestamp("created_at");

                // Fetch products and sizes
                StringBuilder productsBuilder = new StringBuilder();
                try (PreparedStatement psItems = conn.prepareStatement(
                        "SELECT p.name, qi.size FROM quotation_items qi " +
                        "JOIN products p ON qi.product_id=p.id " +
                        "WHERE qi.quotation_id=(SELECT id FROM quotations WHERE invoice_number=?)")) {
                    psItems.setString(1, invoice);
                    try (ResultSet rsItems = psItems.executeQuery()) {
                        List<String> items = new ArrayList<>();
                        while (rsItems.next()) {
                            String name = rsItems.getString("name");
                            String size = rsItems.getString("size");
                            items.add(name + " (" + size + ")");
                        }
                        productsBuilder.append(String.join(", ", items));
                    }
                }

                String productsDisplay = productsBuilder.toString();
                if (productsDisplay.length() > 25) productsDisplay = productsDisplay.substring(0, 22) + "...";

                // Format date
                SimpleDateFormat sdf = new SimpleDateFormat("MMMM d, yyyy  hh:mm a");
                String formattedDate = sdf.format(createdAt);

                System.out.printf("%-12s│ %-15s│ %-25s│ ₱%-8.2f│ %-20s%n",
                        invoice, user, productsDisplay, total, formattedDate);

                invoiceNumbers.add(invoice);
            }

            if (!hasQuotations) {
                System.out.println(YELLOW + "No pending quotations found." + RESET);
                MainDB.pause();
                return;
            }

            System.out.print("\nEnter Invoice Number (E.g INV38182) to process or BACK to exit: ");
            String input = sc.nextLine().trim().toUpperCase();
            if (input.equalsIgnoreCase("BACK")) return;

            if (!invoiceNumbers.contains(input)) {
                System.out.println(RED + "Invalid invoice number!" + RESET);
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

            // Fetch quotation ID and customer info using invoice_number
            int quotationId = 0;
            String customer = "";
            Timestamp createdAt = null;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, user_id, created_at FROM quotations WHERE invoice_number=?")) {
                ps.setString(1, input);
                try (ResultSet rsQ = ps.executeQuery()) {
                    if (rsQ.next()) {
                        quotationId = rsQ.getInt("id");
                        int customerId = rsQ.getInt("user_id");
                        createdAt = rsQ.getTimestamp("created_at");

                        try (PreparedStatement psUser = conn.prepareStatement(
                                "SELECT username FROM users WHERE id=?")) {
                            psUser.setInt(1, customerId);
                            try (ResultSet rsUser = psUser.executeQuery()) {
                                if (rsUser.next()) customer = rsUser.getString("username");
                            }
                        }
                    }
                }
            }

            // Fetch cashier username
            String cashierName = "";
            try (PreparedStatement psCashier = conn.prepareStatement(
                    "SELECT username FROM users WHERE id=?")) {
                psCashier.setInt(1, userId);
                try (ResultSet rsCashier = psCashier.executeQuery()) {
                    if (rsCashier.next()) cashierName = rsCashier.getString("username");
                }
            }

            // Prepare receipt data
            List<String> receiptProducts = new ArrayList<>();
            List<String> receiptSizes = new ArrayList<>();
            List<Integer> receiptQtys = new ArrayList<>();
            List<Double> receiptTotals = new ArrayList<>();
            double grandTotal = 0;

            String sqlItems = "SELECT product_id, quantity, size FROM quotation_items WHERE quotation_id=?";
            try (PreparedStatement psItems = conn.prepareStatement(sqlItems)) {
                psItems.setInt(1, quotationId);
                try (ResultSet rsItems = psItems.executeQuery()) {
                    while (rsItems.next()) {
                        int productId = rsItems.getInt("product_id");
                        int qty = rsItems.getInt("quantity");
                        String size = rsItems.getString("size");

                        try (PreparedStatement psProd = conn.prepareStatement(
                                "SELECT name, price, stock FROM products WHERE id=?")) {
                            psProd.setInt(1, productId);
                            try (ResultSet rsProd = psProd.executeQuery()) {
                                if (rsProd.next()) {
                                    String name = rsProd.getString("name");
                                    double price = rsProd.getDouble("price");
                                    int stock = rsProd.getInt("stock");

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

                                    // Add to receipt
                                    receiptProducts.add(name);
                                    receiptSizes.add(size);
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
                    "UPDATE quotations SET status='completed' WHERE invoice_number=?")) {
                psUpdate.setString(1, input);
                psUpdate.executeUpdate();
            }

            // ===== Generate Receipt Image =====
            String userHome = System.getProperty("user.home");
            String downloadsPath = userHome + File.separator + "Downloads";
            new File(downloadsPath).mkdirs();

            int width = 500;
            int padding = 20;
            int lineHeight = 25;

            int estimatedProductLines = 0;
            for (String product : receiptProducts) {
                int chunks = (int) Math.ceil(product.length() / 32.0);
                estimatedProductLines += chunks;
            }
            int totalLines = 15 + estimatedProductLines;
            int height = padding * 2 + lineHeight * totalLines;

            BufferedImage receiptImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = receiptImage.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            // Title
            g.setColor(new Color(30, 144, 255));
            g.setFont(new Font("Arial", Font.BOLD, 22));
            g.drawString("USMS SYSTEM ", padding, padding + lineHeight);
            g.setColor(new Color(248, 238, 53));
            g.drawString("STI College ProWare", padding, padding + lineHeight * 2);

            int y = padding + lineHeight * 3;
            g.setColor(Color.BLACK);
            g.setFont(new Font("Arial", Font.PLAIN, 14));

            // Info
            g.drawString("Receipt No : " + input, padding, y); y += lineHeight;
            g.drawString("Customer   : " + customer, padding, y); y += lineHeight;
            g.drawString("Cashier    : " + cashierName, padding, y); y += lineHeight; // updated here
            SimpleDateFormat sdf = new SimpleDateFormat("MMMM d, yyyy  hh:mm a");
            g.drawString("Date       : " + sdf.format(createdAt), padding, y); y += lineHeight + 5;

            // Table header
            g.drawLine(padding, y, width - padding, y); y += lineHeight;
            g.setFont(new Font("Arial", Font.BOLD, 14));
            g.drawString("Product Name / (Size)", padding, y);
            g.drawString("Qty", width - 160, y);
            g.drawString("Amount", width - 80, y); y += lineHeight;
            g.drawLine(padding, y, width - padding, y); y += lineHeight;

            // Products
            g.setFont(new Font("Arial", Font.PLAIN, 14));
            for (int i = 0; i < receiptProducts.size(); i++) {
                String name = receiptProducts.get(i) + " (" + receiptSizes.get(i) + ")";
                int qty = receiptQtys.get(i);
                double total = receiptTotals.get(i);

                while (name.length() > 32) {
                    String chunk = name.substring(0, 32);
                    g.drawString(chunk, padding, y); y += lineHeight;
                    name = name.substring(32);
                }

                g.drawString(name, padding, y);
                g.drawString(String.valueOf(qty), width - 150, y);
                g.drawString("₱" + String.format("%.2f", total), width - 80, y);
                y += lineHeight;
            }

            g.drawLine(padding, y, width - padding, y); y += lineHeight;

            // Payment + total
            g.drawString("Payment Method: " + paymentMethod, padding, y); y += lineHeight;
            g.drawLine(padding, y, width - padding, y); y += lineHeight;
            g.setFont(new Font("Arial", Font.BOLD, 16));
            g.drawString("TOTAL DUE:", padding, y);
            g.drawString("₱" + String.format("%.2f", grandTotal), width - 120, y); y += lineHeight * 2;

            // Footer
            g.setFont(new Font("Arial", Font.PLAIN, 14));
            g.setColor(new Color(34, 139, 34));
            g.drawString("We appreciate your purchase", padding, y); y += lineHeight;

            g.dispose();
            File receiptFile = new File(downloadsPath, "receipt_" + input + ".png");
            ImageIO.write(receiptImage, "png", receiptFile);

            System.out.println(GREEN + "Quotation #" + input + " processed successfully!" + RESET);
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
