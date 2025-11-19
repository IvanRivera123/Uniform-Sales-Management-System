package main;

import java.sql.*;
import java.util.Scanner;
import java.util.List;
import java.util.ArrayList;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;


public class CartManager {

    // ==========================================================
    // ADD TO CART (DB VERSION)
    // ==========================================================
	public static void addToCart(Connection conn, Scanner sc, int userId) {
	    try {
	        System.out.print("Enter Product Code (e.g. P85957): ");
	        String productCode = sc.nextLine().trim().toUpperCase();

	        
	        if (!productCode.startsWith("P")) {
	            System.out.println(Colors.YELLOW + "Invalid code format. It must start with 'P' (e.g., P85957)." + Colors.RESET);
	            MainDB.pause();
	            return;
	        }

	        System.out.print("Enter quantity: ");
	        int qty = Integer.parseInt(sc.nextLine().trim());

	       
	        String checkSql = "SELECT id, stock FROM products WHERE product_code = ?";
	        int pid = -1;
	        int stock = 0;

	        try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
	            ps.setString(1, productCode);
	            ResultSet rs = ps.executeQuery();
	            if (!rs.next()) {
	                System.out.println(Colors.RED + "Product not found!" + Colors.RESET);
	                MainDB.pause();
	                return;
	            }
	            pid = rs.getInt("id");
	            stock = rs.getInt("stock");
	        }

	     
	        if (qty > stock) {
	            System.out.println(Colors.YELLOW + "Not enough stock. Available: " + stock + Colors.RESET);
	            MainDB.pause();
	            return;
	        }

	   
	        String existingSql = "SELECT quantity FROM cart WHERE user_id = ? AND product_id = ?";
	        try (PreparedStatement ps = conn.prepareStatement(existingSql)) {
	            ps.setInt(1, userId);
	            ps.setInt(2, pid);
	            ResultSet rs = ps.executeQuery();

	            if (rs.next()) {
	                int newQty = rs.getInt("quantity") + qty;
	                String updateSql = "UPDATE cart SET quantity = ? WHERE user_id = ? AND product_id = ?";
	                try (PreparedStatement updatePs = conn.prepareStatement(updateSql)) {
	                    updatePs.setInt(1, newQty);
	                    updatePs.setInt(2, userId);
	                    updatePs.setInt(3, pid);
	                    updatePs.executeUpdate();
	                }
	            } else {
	                String insertSql = "INSERT INTO cart(user_id, product_id, quantity) VALUES (?, ?, ?)";
	                try (PreparedStatement insertPs = conn.prepareStatement(insertSql)) {
	                    insertPs.setInt(1, userId);
	                    insertPs.setInt(2, pid);
	                    insertPs.setInt(3, qty);
	                    insertPs.executeUpdate();
	                }
	            }
	        }

	        System.out.println(Colors.GREEN + "Added to cart!" + Colors.RESET);

	    } catch (SQLException e) {
	        System.out.println(Colors.RED + "Error adding to cart: " + e.getMessage() + Colors.RESET);
	    } catch (NumberFormatException e) {
	        System.out.println(Colors.YELLOW + "Invalid input. Please enter numbers only for quantity." + Colors.RESET);
	    }

	    MainDB.pause();
	}

    // ==========================================================
    // VIEW CART (DB VERSION)
    // ==========================================================
	public static void viewCart(Connection conn, Scanner sc, int userId) {
	    try {
	        String sql = """
	                SELECT c.id, p.id AS product_id, p.name, p.price, c.quantity, (p.price * c.quantity) AS total
	                FROM cart c
	                JOIN products p ON c.product_id = p.id
	                WHERE c.user_id = ?
	                """;

	        try (PreparedStatement ps = conn.prepareStatement(sql)) {
	            ps.setInt(1, userId);
	            ResultSet rs = ps.executeQuery();

	            MainDB.clearScreen();
	            System.out.println("╔══════════════════════════════════════════════════════════════════════════╗");
	            System.out.println("║                                YOUR CART                                 ║");
	            System.out.println("╚══════════════════════════════════════════════════════════════════════════╝");
	            System.out.printf("%-5s│ %-25s│ %-10s│ %-8s│ %-10s%n", "ID", "Product", "Price", "Qty", "Total");
	            System.out.println("─────┼──────────────────────────┼───────────┼─────────┼─────────────────────");

	            boolean hasItems = false;
	            double grandTotal = 0;

	            while (rs.next()) {
	                hasItems = true;
	                int id = rs.getInt("id");
	                String product = rs.getString("name");
	                double price = rs.getDouble("price");
	                int qty = rs.getInt("quantity");
	                double total = rs.getDouble("total");
	                grandTotal += total;

	                // Truncate product names if too long
	                if (product.length() > 25) product = product.substring(0, 22) + "...";

	                System.out.printf("%-5d│ %-25s│ ₱%-9.2f│ %-8d│ ₱%-9.2f%n",
	                        id, product, price, qty, total);
	            }

	            if (!hasItems) {
	                System.out.println("────────────────────────────────────────────────────────────────────────────");
	                System.out.println(Colors.YELLOW + "Your cart is empty!" + Colors.RESET);
	            } else {
	                System.out.println("────────────────────────────────────────────────────────────────────────────");
	                System.out.printf(Colors.GREEN + "\033[1mGrand Total: ₱%.2f\033[0m%n" + Colors.RESET, grandTotal);
	            }
	        }

	    } catch (SQLException e) {
	        System.out.println(Colors.RED + "Error viewing cart: " + e.getMessage() + Colors.RESET);
	    }

	    System.out.print("\nPress Enter to return...");
	    sc.nextLine();
	}

    // ==========================================================
    // SUBMIT QUOTATION (DB VERSION)
    // ==========================================================
	public static void submitQuotation(Connection conn, Scanner sc, int userId, boolean loggedIn) {
	    if (!loggedIn) {
	        System.out.println(Colors.YELLOW + "You must register/login to submit quotation." + Colors.RESET);
	        MainDB.pause();
	        return;
	    }

	    try {
	        // Fetch cart items
	        String fetchCart = """
	                SELECT c.id, c.product_id, p.name, p.price, c.quantity, (p.price * c.quantity) AS total
	                FROM cart c
	                JOIN products p ON c.product_id = p.id
	                WHERE c.user_id = ?
	                """;

	        List<Integer> cartRowIds = new ArrayList<>();
	        List<Integer> cartIds = new ArrayList<>();
	        List<String> cartNames = new ArrayList<>();
	        List<Double> cartPrices = new ArrayList<>();
	        List<Integer> cartQty = new ArrayList<>();
	        List<Double> cartTotal = new ArrayList<>();

	        try (PreparedStatement ps = conn.prepareStatement(fetchCart)) {
	            ps.setInt(1, userId);
	            ResultSet rs = ps.executeQuery();

	            while (rs.next()) {
	                cartRowIds.add(rs.getInt("id"));
	                cartIds.add(rs.getInt("product_id"));
	                cartNames.add(rs.getString("name"));
	                cartPrices.add(rs.getDouble("price"));
	                cartQty.add(rs.getInt("quantity"));
	                cartTotal.add(rs.getDouble("total"));
	            }
	        }

	        if (cartIds.isEmpty()) {
	            System.out.println(Colors.YELLOW + "Your cart is empty." + Colors.RESET);
	            MainDB.pause();
	            return;
	        }

	        // Show cart
	        MainDB.clearScreen();
	        System.out.println("╔══════════════════════════════════════════════════════════╗");
	        System.out.println("║                       YOUR CART                          ║");
	        System.out.println("╚══════════════════════════════════════════════════════════╝");
	        System.out.printf("%-4s │ %-25s │ %-6s  │ %-4s │ %-8s%n", "No.", "Product", "Price", "Qty", "Total");
	        System.out.println("─────┼───────────────────────────┼─────────┼──────┼──────────");

	        for (int i = 0; i < cartIds.size(); i++) {
	            String name = cartNames.get(i);
	            int y = 0;
	            if (name.length() <= 25) {
	                System.out.printf("%-4d │ %-25s │ ₱%-5.2f │ %-4d │ ₱%-7.2f%n",
	                        i + 1, name, cartPrices.get(i), cartQty.get(i), cartTotal.get(i));
	            } else {
	                // Wrap long names
	                int maxChars = 25;
	                int start = 0;
	                while (start < name.length()) {
	                    int end = Math.min(start + maxChars, name.length());
	                    String part = name.substring(start, end);
	                    if (start == 0) {
	                        System.out.printf("%-4d │ %-25s │ ₱%-5.2f │ %-4d │ ₱%-7.2f%n",
	                                i + 1, part, cartPrices.get(i), cartQty.get(i), cartTotal.get(i));
	                    } else {
	                        System.out.printf("     │ %-25s │ %-6s │ %-4s │ %-8s%n", part, "", "", "");
	                    }
	                    start += maxChars;
	                }
	            }
	        }

	        System.out.println("─────────────────────────────────────────────────────────────");

	        // Options
	        System.out.println("\nOptions:");
	        System.out.println("[A] Submit All Products");
	        System.out.println("[1-" + cartIds.size() + "] Submit a specific product");
	        System.out.println("[X] Cancel");

	        System.out.print("Choose an option: ");
	        String choice = sc.nextLine().trim().toUpperCase();

	        List<Integer> selectedIndices = new ArrayList<>();
	        if (choice.equals("A")) {
	            for (int i = 0; i < cartIds.size(); i++) selectedIndices.add(i);
	        } else if (choice.equals("X")) {
	            System.out.println(Colors.ORANGE + "Quotation canceled." + Colors.RESET);
	            MainDB.pause();
	            return;
	        } else {
	            try {
	                int index = Integer.parseInt(choice) - 1;
	                if (index < 0 || index >= cartIds.size()) {
	                    System.out.println(Colors.RED + "Invalid selection." + Colors.RESET);
	                    MainDB.pause();
	                    return;
	                }
	                selectedIndices.add(index);
	            } catch (NumberFormatException e) {
	                System.out.println(Colors.RED + "Invalid input." + Colors.RESET);
	                MainDB.pause();
	                return;
	            }
	        }

	        // Compute total for selected items
	        double totalAmount = 0;
	        for (int i : selectedIndices) totalAmount += cartTotal.get(i);

	        System.out.printf(Colors.YELLOW + "Your quotation total: ₱%.2f%n" + Colors.RESET, totalAmount);
	        System.out.print("Confirm submission? (Yes/No): ");
	        String confirm = sc.nextLine().trim().toUpperCase();

	        if (!confirm.equals("YES")) {
	            System.out.println(Colors.ORANGE + "Quotation canceled. Please enter a valid choice." + Colors.RESET);
	            MainDB.pause();
	            return;
	        }

	        // Insert quotation
	        int quotationId = 0;
	        String qSql = "INSERT INTO quotations(user_id, total_amount, status, created_at) VALUES(?, ?, 'PENDING', NOW())";
	        try (PreparedStatement psQ = conn.prepareStatement(qSql, Statement.RETURN_GENERATED_KEYS)) {
	            psQ.setInt(1, userId);
	            psQ.setDouble(2, totalAmount);
	            psQ.executeUpdate();
	            ResultSet keys = psQ.getGeneratedKeys();
	            if (keys.next()) quotationId = keys.getInt(1);
	        }

	        // Move selected items to quotation_items
	        String itemSql = "INSERT INTO quotation_items(quotation_id, product_id, quantity, subtotal) VALUES(?, ?, ?, ?)";
	        try (PreparedStatement psItem = conn.prepareStatement(itemSql)) {
	            for (int i : selectedIndices) {
	                psItem.setInt(1, quotationId);
	                psItem.setInt(2, cartIds.get(i));
	                psItem.setInt(3, cartQty.get(i));
	                psItem.setDouble(4, cartTotal.get(i));
	                psItem.addBatch();
	            }
	            psItem.executeBatch();
	        }

	        // Remove selected items from cart
	        String deleteSql = "DELETE FROM cart WHERE id = ?";
	        try (PreparedStatement psDel = conn.prepareStatement(deleteSql)) {
	            for (int i : selectedIndices) {
	                psDel.setInt(1, cartRowIds.get(i));
	                psDel.addBatch();
	            }
	            psDel.executeBatch();
	        }

	        System.out.println(Colors.GREEN + "Quotation submitted successfully!" + Colors.RESET);

	        // ============================
	        // Generate invoice image
	        // ============================
	        try {
	            int padding = 20;
	            int lineHeight = 25;
	            int imageWidth = 700;
	            int estimatedLines = selectedIndices.size() * 3 + 8; // extra for wrapped names
	            int imageHeight = padding * 2 + lineHeight * estimatedLines;

	            BufferedImage invoiceImage = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_RGB);
	            Graphics2D g = invoiceImage.createGraphics();

	            g.setColor(Color.WHITE);
	            g.fillRect(0, 0, imageWidth, imageHeight);

	            g.setColor(Color.BLACK);
	            g.setFont(new Font("Monospaced", Font.PLAIN, 14));

	            int y = padding;
	            g.drawString("INVOICE", padding, y); y += lineHeight;
	            g.drawString("Invoice No : " + quotationId, padding, y); y += lineHeight;
	            g.drawString("User ID    : " + userId, padding, y); y += lineHeight;
	            g.drawString("Date       : " + new Timestamp(System.currentTimeMillis()), padding, y); y += lineHeight;
	            g.drawLine(padding, y, imageWidth - padding, y); y += lineHeight;

	            g.drawString("PRODUCT", padding, y);
	            g.drawString("QTY", imageWidth - 160, y);
	            g.drawString("PRICE", imageWidth - 100, y);
	            g.drawString("TOTAL", imageWidth - 40, y);
	            y += lineHeight;
	            g.drawLine(padding, y, imageWidth - padding, y); y += lineHeight;

	            for (int i : selectedIndices) {
	                String name = cartNames.get(i);
	                int qty = cartQty.get(i);
	                double price = cartPrices.get(i);
	                double total = cartTotal.get(i);

	                // Wrap long names
	                List<String> wrappedLines = new ArrayList<>();
	                int maxCharsPerLine = 40;
	                while (name.length() > maxCharsPerLine) {
	                    wrappedLines.add(name.substring(0, maxCharsPerLine));
	                    name = name.substring(maxCharsPerLine);
	                }
	                wrappedLines.add(name);

	                for (int j = 0; j < wrappedLines.size(); j++) {
	                    String line = wrappedLines.get(j);
	                    g.drawString(line, padding, y);
	                    if (j == 0) {
	                        g.drawString(String.valueOf(qty), imageWidth - 160, y);
	                        g.drawString(String.format("%.2f", price), imageWidth - 100, y);
	                        g.drawString(String.format("%.2f", total), imageWidth - 40, y);
	                    }
	                    y += lineHeight;
	                }
	            }

	            g.drawLine(padding, y, imageWidth - padding, y); y += lineHeight;
	            g.drawString("GRAND TOTAL: " + String.format("%.2f", totalAmount), padding, y);

	            g.dispose();

	            String userHome = System.getProperty("user.home");
	            String downloadsPath = userHome + File.separator + "Downloads";
	            new File(downloadsPath).mkdirs();

	            File invoiceFile = new File(downloadsPath, "invoice_" + quotationId + ".png");
	            ImageIO.write(invoiceImage, "png", invoiceFile);

	            System.out.println(Colors.CYAN + "Invoice generated: " + invoiceFile.getAbsolutePath() + Colors.RESET);

	        } catch (IOException e) {
	            System.out.println(Colors.RED + "Failed to generate invoice: " + e.getMessage() + Colors.RESET);
	        }

	    } catch (SQLException e) {
	        System.out.println(Colors.RED + "Error submitting quotation: " + e.getMessage() + Colors.RESET);
	    }

	    MainDB.pause();
	}



    // ==========================================================
    // COLORS CLASS (INLINE)
    // ==========================================================
    static class Colors {
        public static final String RESET = "\u001B[0m";
        public static final String BOLD = "\u001B[1m";

        public static final String RED = "\u001B[31m";
        public static final String GREEN = "\u001B[32m";
        public static final String YELLOW = "\u001B[33m";
        public static final String BLUE = "\u001B[34m";
        public static final String MAGENTA = "\u001B[35m";
        public static final String CYAN = "\u001B[36m";
        public static final String WHITE = "\u001B[37m";
        public static final String ORANGE = "\u001B[38;5;208m";
    }
}
