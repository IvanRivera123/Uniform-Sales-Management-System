package main;

import java.sql.*;
import java.util.Scanner;

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
                    SELECT c.id, p.name, p.price, c.quantity, (p.price * c.quantity) AS total
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
            // Check if cart has items
            String checkCart = "SELECT COUNT(*) FROM cart WHERE user_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(checkCart)) {
                ps.setInt(1, userId);
                ResultSet rs = ps.executeQuery();
                if (rs.next() && rs.getInt(1) == 0) {
                    System.out.println(Colors.YELLOW + "Your cart is empty." + Colors.RESET);
                    MainDB.pause();
                    return;
                }
            }

            // Compute total
            String totalSql = """
                    SELECT SUM(p.price * c.quantity) AS total
                    FROM cart c
                    JOIN products p ON c.product_id = p.id
                    WHERE c.user_id = ?
                    """;
            double totalAmount = 0;
            try (PreparedStatement ps = conn.prepareStatement(totalSql)) {
                ps.setInt(1, userId);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) totalAmount = rs.getDouble("total");
            }

            // Confirmation before submitting
            System.out.printf(Colors.YELLOW + "Your total quotation amount is ₱%.2f%n" + Colors.RESET, totalAmount);
            System.out.print("Do you want to submit this quotation? (Y/N): ");
            String confirm = sc.nextLine().trim().toUpperCase();

            if (!confirm.equals("Y")) {
                System.out.println(Colors.ORANGE + "Quotation submission canceled." + Colors.RESET);
                MainDB.pause();
                return;
            }

            // Create quotation record
            String qSql = "INSERT INTO quotations(user_id, total_amount, status, created_at) VALUES(?, ?, 'PENDING', NOW())";
            int quotationId = 0;
            try (PreparedStatement ps = conn.prepareStatement(qSql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, userId);
                ps.setDouble(2, totalAmount);
                ps.executeUpdate();
                ResultSet keys = ps.getGeneratedKeys();
                if (keys.next()) quotationId = keys.getInt(1);
            }

            // Move items to quotation_items
            String itemSql = """
                    INSERT INTO quotation_items(quotation_id, product_id, quantity, subtotal)
                    SELECT ?, c.product_id, c.quantity, (p.price * c.quantity)
                    FROM cart c
                    JOIN products p ON c.product_id = p.id
                    WHERE c.user_id = ?
                    """;
            try (PreparedStatement ps = conn.prepareStatement(itemSql)) {
                ps.setInt(1, quotationId);
                ps.setInt(2, userId);
                ps.executeUpdate();
            }

            // Clear user's cart
            String clearSql = "DELETE FROM cart WHERE user_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(clearSql)) {
                ps.setInt(1, userId);
                ps.executeUpdate();
            }

            System.out.println(Colors.GREEN + "Quotation submitted! Sales Manager will process it." + Colors.RESET);

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
