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
	
	public static void returnExpiredStock(Connection conn) {
	    try {
	        // Update product_sizes in one go using a JOIN and aggregation
	        String updateStockSql = """
	            UPDATE product_sizes ps
	            JOIN (
	                SELECT qi.product_id, qi.size, SUM(qi.quantity) AS qty
	                FROM quotations q
	                JOIN quotation_items qi ON q.id = qi.quotation_id
	                WHERE q.status = 'PENDING' AND DATE(q.created_at) < CURDATE()
	                GROUP BY qi.product_id, qi.size
	            ) expired ON ps.product_id = expired.product_id AND ps.size = expired.size
	            SET ps.stock = ps.stock + expired.qty
	        """;

	        try (PreparedStatement ps = conn.prepareStatement(updateStockSql)) {
	            int rows = ps.executeUpdate();
	            System.out.println("\u001B[32mReturned stock for " + rows + " product-size entries.\u001B[0m");
	        }

	        // Mark quotations as expired in one query
	        String updateQuotes = "UPDATE quotations SET status = 'EXPIRED' WHERE status = 'PENDING' AND DATE(created_at) < CURDATE()";
	        try (PreparedStatement psUpdate = conn.prepareStatement(updateQuotes)) {
	            int updated = psUpdate.executeUpdate();
	            System.out.println("\u001B[32mMarked " + updated + " quotations as expired.\u001B[0m");
	        }

	    } catch (SQLException e) {
	        System.out.println("\u001B[31mError returning expired stock: " + e.getMessage() + "\u001B[0m");
	    }
	}


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

	        String findProduct = "SELECT id, name FROM products WHERE product_code = ? AND active_status = 1";
	        int productId = -1;
	        String productName = "";

	        try (PreparedStatement ps = conn.prepareStatement(findProduct)) {
	            ps.setString(1, productCode);
	            ResultSet rs = ps.executeQuery();
	            if (!rs.next()) {
	                System.out.println(Colors.RED + "Product not found!" + Colors.RESET);
	                MainDB.pause();
	                return;
	            }
	            productId = rs.getInt("id");
	            productName = rs.getString("name");
	        }

	        String sizeSql = """
	                SELECT size, stock 
	                FROM product_sizes 
	                WHERE product_id = ? AND stock > 0
	                ORDER BY FIELD(size,'XS','S','M','L','XL','XXL','XXXL')
	            """;

	        List<String> sizeList = new ArrayList<>();
	        System.out.println("\n" + Colors.CYAN + "Available Sizes for: " + productName + Colors.RESET);
	        System.out.println(Colors.YELLOW + "Type 'back' at any time to go back.\n" + Colors.RESET);
	        System.out.println("╔═══════╦════════╗");
	        System.out.println("║ Size  ║ Stock  ║");
	        System.out.println("╠═══════╬════════╣");

	        try (PreparedStatement ps = conn.prepareStatement(sizeSql)) {
	            ps.setInt(1, productId);
	            ResultSet rs = ps.executeQuery();
	            while (rs.next()) {
	                String size = rs.getString("size");
	                int stock = rs.getInt("stock");
	                sizeList.add(size);
	                System.out.printf("║ %-5s ║ %-6d ║%n", size, stock);
	            }
	        }

	        System.out.println("╚═══════╩════════╝");

	        if (sizeList.isEmpty()) {
	            System.out.println(Colors.YELLOW + "No available sizes for this product." + Colors.RESET);
	            MainDB.pause();
	            return;
	        }

	        String chosenSize;
	        while (true) {
	            System.out.print("Choose size: ");
	            chosenSize = sc.nextLine().trim().toUpperCase();
	            if (chosenSize.equalsIgnoreCase("BACK")) return;
	            if (sizeList.contains(chosenSize)) break;
	            System.out.println("Invalid size. Try again.");
	        }

	        int qty = 0;
	        while (true) {
	            System.out.print("Enter quantity: ");
	            String input = sc.nextLine().trim();
	            if (input.equalsIgnoreCase("BACK")) return;
	            try {
	                qty = Integer.parseInt(input);
	                if (qty <= 0) {
	                    System.out.println(Colors.RED + "Quantity must be a positive number." + Colors.RESET);
	                    continue;
	                }
	                break;
	            } catch (NumberFormatException e) {
	                System.out.println(Colors.RED + "Invalid input. Enter a valid number." + Colors.RESET);
	            }
	        }

	        String stockSql = "SELECT stock FROM product_sizes WHERE product_id = ? AND size = ?";
	        int available = 0;
	        try (PreparedStatement ps = conn.prepareStatement(stockSql)) {
	            ps.setInt(1, productId);
	            ps.setString(2, chosenSize);
	            ResultSet rs = ps.executeQuery();
	            if (rs.next()) available = rs.getInt("stock");
	        }

	        if (qty > available) {
	            System.out.println(Colors.YELLOW + "Not enough stock for size " + chosenSize + ". Available: " + available + Colors.RESET);
	            MainDB.pause();
	            return;
	        }

	        String findCart = "SELECT quantity FROM cart WHERE user_id = ? AND product_id = ? AND size = ?";
	        try (PreparedStatement ps = conn.prepareStatement(findCart)) {
	            ps.setInt(1, userId);
	            ps.setInt(2, productId);
	            ps.setString(3, chosenSize);
	            ResultSet rs = ps.executeQuery();
	            if (rs.next()) {
	                int newQty = rs.getInt("quantity") + qty;
	                String updateSql = "UPDATE cart SET quantity = ? WHERE user_id = ? AND product_id = ? AND size = ?";
	                try (PreparedStatement ps2 = conn.prepareStatement(updateSql)) {
	                    ps2.setInt(1, newQty);
	                    ps2.setInt(2, userId);
	                    ps2.setInt(3, productId);
	                    ps2.setString(4, chosenSize);
	                    ps2.executeUpdate();
	                }
	            } else {
	                String insertSql = "INSERT INTO cart(user_id, product_id, size, quantity) VALUES (?, ?, ?, ?)";
	                try (PreparedStatement ps2 = conn.prepareStatement(insertSql)) {
	                    ps2.setInt(1, userId);
	                    ps2.setInt(2, productId);
	                    ps2.setString(3, chosenSize);
	                    ps2.setInt(4, qty);
	                    ps2.executeUpdate();
	                }
	            }
	        }

	        System.out.println(Colors.GREEN + "Added to cart!" + Colors.RESET);

	    } catch (SQLException e) {
	        System.out.println(Colors.RED + "Error adding to cart: " + e.getMessage() + Colors.RESET);
	    }

	    MainDB.pause();
	}
    // ==========================================================
    // VIEW CART 
    // ==========================================================
	public static void viewCart(Connection conn, Scanner sc, int userId) {
	    try {
	        String sql = """
	                SELECT c.id, p.id AS product_id, p.name, p.price, c.quantity, c.size, (p.price * c.quantity) AS total
	                FROM cart c
	                JOIN products p ON c.product_id = p.id
	                WHERE c.user_id = ?
	                """;

	        try (PreparedStatement ps = conn.prepareStatement(sql)) {
	            ps.setInt(1, userId);
	            ResultSet rs = ps.executeQuery();

	            MainDB.clearScreen();
	            System.out.println("╔════════════════════════════════════════════════════════════════════════════════╗");
	            System.out.println("║                                   YOUR CART                                    ║");
	            System.out.println("╚════════════════════════════════════════════════════════════════════════════════╝");
	            System.out.printf("%-5s│ %-25s│ %-8s│ %-8s│ %-8s│ %-10s%n", "ID", "Product", "Size", "Price", "Qty", "Total");
	            System.out.println("─────┼──────────────────────────┼─────────┼─────────┼─────────┼─────────────────────");

	            boolean hasItems = false;
	            double grandTotal = 0;

	            while (rs.next()) {
	                hasItems = true;
	                int id = rs.getInt("id");
	                String product = rs.getString("name");
	                String size = rs.getString("size");
	                double price = rs.getDouble("price");
	                int qty = rs.getInt("quantity");
	                double total = rs.getDouble("total");
	                grandTotal += total;

	                // Truncate product names if too long
	                if (product.length() > 25) product = product.substring(0, 22) + "...";

	                System.out.printf("%-5d│ %-25s│ %-8s│ ₱%-7.2f│ %-8d│ ₱%-9.2f%n",
	                        id, product, size, price, qty, total);
	            }

	            if (!hasItems) {
	                System.out.println("────────────────────────────────────────────────────────────────────────────────────");
	                System.out.println(Colors.YELLOW + "Your cart is empty!" + Colors.RESET);
	            } else {
	                System.out.println("────────────────────────────────────────────────────────────────────────────────────");
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
	private static String generateInvoiceNumber() {
	    int randomNum = 10000 + (int)(Math.random() * 90000); 
	    return "INV" + randomNum;
	}

	public static void submitQuotation(Connection conn, Scanner sc, int userId, boolean loggedIn) {
	    if (!loggedIn) {
	        System.out.println(Colors.YELLOW + "You must register/login to submit quotation." + Colors.RESET);
	        MainDB.pause();
	        return;
	    }

	    try {
	        // Fetch cart items including size
	        String fetchCart = """
	                SELECT c.id, c.product_id, p.name, p.price, c.size, c.quantity, (p.price * c.quantity) AS total
	                FROM cart c
	                JOIN products p ON c.product_id = p.id
	                WHERE c.user_id = ?
	                """;

	        List<Integer> cartRowIds = new ArrayList<>();
	        List<Integer> cartIds = new ArrayList<>();
	        List<String> cartNames = new ArrayList<>();
	        List<String> cartSizes = new ArrayList<>();
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
	                cartSizes.add(rs.getString("size"));
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
	        System.out.printf("%-4s │ %-25s │ %-6s  │ %-4s │ %-8s │ %-6s%n", "No.", "Product", "Price", "Qty", "Total", "Size");
	        System.out.println("─────┼───────────────────────────┼─────────┼──────┼──────────┼──────");

	        for (int i = 0; i < cartIds.size(); i++) {
	            String name = cartNames.get(i);
	            String size = cartSizes.get(i);
	            if (name.length() <= 25) {
	                System.out.printf("%-4d │ %-25s │ ₱%-5.2f │ %-4d │ ₱%-7.2f │ %-6s%n",
	                        i + 1, name, cartPrices.get(i), cartQty.get(i), cartTotal.get(i), size);
	            } else {
	                int maxChars = 25;
	                int start = 0;
	                while (start < name.length()) {
	                    int end = Math.min(start + maxChars, name.length());
	                    String part = name.substring(start, end);
	                    if (start == 0) {
	                        System.out.printf("%-4d │ %-25s │ ₱%-5.2f │ %-4d │ ₱%-7.2f │ %-6s%n",
	                                i + 1, part, cartPrices.get(i), cartQty.get(i), cartTotal.get(i), size);
	                    } else {
	                        System.out.printf("     │ %-25s │ %-6s │ %-4s │ %-8s │ %-6s%n", part, "", "", "", "");
	                    }
	                    start += maxChars;
	                }
	            }
	        }

	        System.out.println("────────────────────────────────────────────────────────────────────");

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

	        // Generate a unique invoice number
	        String invoiceNumber = generateInvoiceNumber();

	        // Insert quotation with invoice_number
	        int quotationId = 0;
	        String qSql = "INSERT INTO quotations(user_id, total_amount, status, invoice_number, created_at) VALUES(?, ?, 'PENDING', ?, NOW())";
	        try (PreparedStatement psQ = conn.prepareStatement(qSql, Statement.RETURN_GENERATED_KEYS)) {
	            psQ.setInt(1, userId);
	            psQ.setDouble(2, totalAmount);
	            psQ.setString(3, invoiceNumber);
	            psQ.executeUpdate();
	            ResultSet keys = psQ.getGeneratedKeys();
	            if (keys.next()) quotationId = keys.getInt(1);
	        }

	        // Move selected items to quotation_items including size
	        String itemSql = "INSERT INTO quotation_items(quotation_id, product_id, size, quantity, subtotal) VALUES(?, ?, ?, ?, ?)";
	        try (PreparedStatement psItem = conn.prepareStatement(itemSql)) {
	            for (int i : selectedIndices) {
	                psItem.setInt(1, quotationId);
	                psItem.setInt(2, cartIds.get(i));
	                psItem.setString(3, cartSizes.get(i));
	                psItem.setInt(4, cartQty.get(i));
	                psItem.setDouble(5, cartTotal.get(i));
	                psItem.addBatch();
	            }
	            psItem.executeBatch();
	        }

	        // Deduct stock from product_sizes
	        String updateStockSql = "UPDATE product_sizes SET stock = stock - ? WHERE product_id = ? AND size = ?";
	        try (PreparedStatement psStock = conn.prepareStatement(updateStockSql)) {
	            for (int i : selectedIndices) {
	                psStock.setInt(1, cartQty.get(i));
	                psStock.setInt(2, cartIds.get(i));
	                psStock.setString(3, cartSizes.get(i));
	                psStock.addBatch();
	            }
	            psStock.executeBatch();
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
	        // Fetch username for invoice
	        // ============================
	        String username = "";
	        String userSql = "SELECT username FROM users WHERE id=?";
	        try (PreparedStatement psUser = conn.prepareStatement(userSql)) {
	            psUser.setInt(1, userId);
	            try (ResultSet rsUser = psUser.executeQuery()) {
	                if (rsUser.next()) {
	                    username = rsUser.getString("username");
	                } else {
	                    username = "Unknown User";
	                }
	            }
	        }

	        // ============================
	        // Generate invoice image
	        // ============================
	        try {
	            int padding = 20;
	            int lineHeight = 25;
	            int imageWidth = 700;
	            int estimatedLines = selectedIndices.size() * 2 + 8;
	            int imageHeight = padding * 2 + lineHeight * estimatedLines;

	            BufferedImage invoiceImage = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_RGB);
	            Graphics2D g = invoiceImage.createGraphics();

	            g.setColor(Color.WHITE);
	            g.fillRect(0, 0, imageWidth, imageHeight);

	            g.setColor(Color.BLACK);
	            g.setFont(new Font("Monospaced", Font.PLAIN, 14));

	            int y = padding;
	            g.drawString("INVOICE", padding, y); y += lineHeight;
	            g.drawString("Invoice No : " + invoiceNumber, padding, y); y += lineHeight;
	            g.drawString("User       : " + username, padding, y); y += lineHeight; 
	            g.drawString("Date       : " + new Timestamp(System.currentTimeMillis()), padding, y); y += lineHeight;
	            g.drawLine(padding, y, imageWidth - padding, y); y += lineHeight;

	            // Table header
	            g.drawString("PRODUCT / (SIZE)", padding, y);
	            g.drawString("QTY", imageWidth - 200, y);
	            g.drawString("PRICE", imageWidth - 140, y);
	            g.drawString("TOTAL", imageWidth - 80, y);
	            y += lineHeight;
	            g.drawLine(padding, y, imageWidth - padding, y); y += lineHeight;

	            for (int i : selectedIndices) {
	                String productWithSize = cartNames.get(i) + " (" + cartSizes.get(i) + ")";
	                int qty = cartQty.get(i);
	                double price = cartPrices.get(i);
	                double total = cartTotal.get(i);

	                // Wrap long product names
	                List<String> wrappedLines = new ArrayList<>();
	                int maxCharsPerLine = 50;
	                while (productWithSize.length() > maxCharsPerLine) {
	                    wrappedLines.add(productWithSize.substring(0, maxCharsPerLine));
	                    productWithSize = productWithSize.substring(maxCharsPerLine);
	                }
	                wrappedLines.add(productWithSize);

	                for (int j = 0; j < wrappedLines.size(); j++) {
	                    String line = wrappedLines.get(j);
	                    g.drawString(line, padding, y);
	                    if (j == 0) {
	                        g.drawString(String.valueOf(qty), imageWidth - 200, y);
	                        g.drawString(String.format("%.2f", price), imageWidth - 140, y);
	                        g.drawString(String.format("%.2f", total), imageWidth - 80, y);
	                    }
	                    y += lineHeight;
	                }
	            }

	            g.drawLine(padding, y, imageWidth - padding, y); 
	            y += lineHeight;

	            g.drawString("GRAND TOTAL: " + String.format("%.2f", totalAmount), padding, y);
	            y += lineHeight; // move down for caption

	            g.setFont(new Font("Monospaced", Font.ITALIC, 12));
	            g.drawString("This invoice is valid only on the date of issue.", padding, y);

	            g.dispose();

	            String userHome = System.getProperty("user.home");
	            String downloadsPath = userHome + File.separator + "Downloads";
	            new File(downloadsPath).mkdirs();

	            File invoiceFile = new File(downloadsPath, "invoice_" + invoiceNumber + ".png");
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
