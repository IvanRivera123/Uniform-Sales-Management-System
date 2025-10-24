package main;

import java.sql.*;
import java.util.*;

public class ProductManager {
    static final String RESET = "\u001B[0m";
    static final String GREEN = "\u001B[32m";
    static final String RED = "\u001B[31m";
    static final String YELLOW = "\u001B[33m";

    // ==========================================================
    // MANAGE PRODUCTS MENU (Admin/Manager)
    // ==========================================================
    public static void manageProducts(Connection conn, Scanner sc) {
        int choice = 0;
        do {
            try {
                MainDB.clearScreen();
                System.out.println("==================== MANAGE PRODUCTS ====================");
                System.out.println("1. Add Product");
                System.out.println("2. View Products");
                System.out.println("3. Edit Product");
                System.out.println("4. Delete Product");
                System.out.println("5. View Inventory Log");
                System.out.println("6. Back");
                System.out.print("Enter choice: ");

                try {
                    choice = Integer.parseInt(sc.nextLine().trim());
                } catch (NumberFormatException e) {
                    System.out.println(RED + "Invalid input! Please enter a number." + RESET);
                    MainDB.pause();
                    continue;
                }

                switch (choice) {
                    case 1 -> addProduct(conn, sc);
                    case 2 -> viewProducts(conn);
                    case 3 -> editProduct(conn, sc);
                    case 4 -> deleteProduct(conn, sc);
                    case 5 -> LogManager.viewInventoryLog(conn);
                    case 6 -> {} // back
                    default -> {
                        System.out.println(RED + "Invalid choice!" + RESET);
                        MainDB.pause();
                    }
                }
            } catch (Exception e) {
                System.out.println(RED + "Error: " + e.getMessage() + RESET);
                MainDB.pause();
            }
        } while (choice != 6);
    }

    // ==========================================================
    // PUBLIC VIEW PRODUCTS (Users)
    // ==========================================================
    public static void viewAllProducts(Connection conn, Scanner sc, int userId, boolean loggedIn) {
        Map<Integer, Integer> cart = new HashMap<>(); // product_id -> quantity
        int choice;

        do {
            try {
                MainDB.clearScreen();
                System.out.println("==================== PRODUCT LIST ====================");
                displayProducts(conn);

                System.out.println("1. Add to Cart");
                System.out.println("2. View Cart");
                System.out.println("3. Submit Quotation");
                System.out.println("4. Back");
                System.out.print("Enter choice: ");

                choice = Integer.parseInt(sc.nextLine().trim());

                switch (choice) {
                    case 1 -> addToCart(conn, sc, cart);
                    case 2 -> viewCart(conn, sc, cart);
                    case 3 -> submitQuotation(conn, cart, userId, loggedIn);
                    case 4 -> {} // back
                    default -> {
                        System.out.println(RED + "Invalid choice!" + RESET);
                        MainDB.pause();
                    }
                }

            } catch (NumberFormatException e) {
                System.out.println(RED + "Invalid input!" + RESET);
                MainDB.pause();
                choice = 0;
            } catch (SQLException e) {
                System.out.println(RED + "Database error: " + e.getMessage() + RESET);
                MainDB.pause();
                choice = 0;
            }
        } while (choice != 4);
    }

    // ==========================================================
    // HELPER METHODS FOR PUBLIC VIEW
    // ==========================================================
    private static void displayProducts(Connection conn) throws SQLException {
        String sql = "SELECT * FROM products ORDER BY id";
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            System.out.printf("%-5s %-30s %-10s %-10s%n", "ID", "Name", "Price", "Stock");
            System.out.println("===============================================================");
            while (rs.next()) {
                System.out.printf("%-5d %-30s %-10.2f %-10d%n",
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getDouble("price"),
                        rs.getInt("stock"));
            }
            System.out.println("---------------------------------------------------------------");
        }
    }

    private static void addToCart(Connection conn, Scanner sc, Map<Integer, Integer> cart) throws SQLException {
        System.out.print("Enter Product ID to add: ");
        int pid = Integer.parseInt(sc.nextLine().trim());
        System.out.print("Enter quantity: ");
        int qty = Integer.parseInt(sc.nextLine().trim());

        // check if product exists
        String sql = "SELECT stock FROM products WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, pid);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                System.out.println(RED + "Product not found!" + RESET);
            } else {
                int stock = rs.getInt("stock");
                if (qty > stock) {
                    System.out.println(YELLOW + "Not enough stock. Available: " + stock + RESET);
                } else {
                    cart.put(pid, cart.getOrDefault(pid, 0) + qty);
                    System.out.println(GREEN + "Added to cart!" + RESET);
                }
            }
        }
        MainDB.pause();
    }

    private static void viewCart(Connection conn, Scanner sc, Map<Integer, Integer> cart) throws SQLException {
        MainDB.clearScreen();
        System.out.println("==================== CART ====================");
        System.out.println("==================== CART ====================");
        if (cart.isEmpty()) {
            System.out.println("Cart is empty.");
        } else {
            double total = 0;
            System.out.println("ID\tQty\tPrice\tSubtotal");
            System.out.println("------------------------------------------");

            String sql = "SELECT name, price FROM products WHERE id = ?";
            for (Map.Entry<Integer, Integer> entry : cart.entrySet()) {
                int pid = entry.getKey();
                int qty = entry.getValue();
                String name = "";
                double price = 0;

                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, pid);
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) {
                        name = rs.getString("name");
                        price = rs.getDouble("price");
                    }
                }

                double subtotal = qty * price;
                total += subtotal;
                System.out.printf("%d\t%d\t%.2f\t%.2f%n", pid, qty, price, subtotal);
            }
            System.out.println("------------------------------------------");
            System.out.printf("Total: %.2f%n", total);
        }
        MainDB.pause();
    }

    private static void submitQuotation(Connection conn, Map<Integer, Integer> cart, int userId, boolean loggedIn) throws SQLException {
        if (!loggedIn) {
            System.out.println(YELLOW + "You must register/login to submit quotation." + RESET);
        } else if (cart.isEmpty()) {
            System.out.println(YELLOW + "Cart is empty." + RESET);
        } else {
            // Calculate total
            double totalAmount = 0;
            String priceSql = "SELECT price FROM products WHERE id=?";
            for (Map.Entry<Integer, Integer> entry : cart.entrySet()) {
                int pid = entry.getKey();
                int qty = entry.getValue();
                try (PreparedStatement ps = conn.prepareStatement(priceSql)) {
                    ps.setInt(1, pid);
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) {
                        totalAmount += rs.getDouble("price") * qty;
                    }
                }
            }

            // Create quotation with total_amount
            String qSql = "INSERT INTO quotations(user_id, total_amount, status, created_at) VALUES(?, ?, 'PENDING', NOW())";
            int quotationId = 0;
            try (PreparedStatement ps = conn.prepareStatement(qSql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, userId);
                ps.setDouble(2, totalAmount);
                ps.executeUpdate();
                ResultSet keys = ps.getGeneratedKeys();
                if (keys.next()) quotationId = keys.getInt(1);
            }

            // Add items to quotation_items
            String itemSql = "INSERT INTO quotation_items(quotation_id, product_id, quantity, subtotal) VALUES(?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(itemSql)) {
                for (Map.Entry<Integer, Integer> entry : cart.entrySet()) {
                    int pid = entry.getKey();
                    int qty = entry.getValue();
                    double subtotal = 0;
                    try (PreparedStatement psPrice = conn.prepareStatement("SELECT price FROM products WHERE id=?")) {
                        psPrice.setInt(1, pid);
                        ResultSet rs = psPrice.executeQuery();
                        if (rs.next()) subtotal = rs.getDouble("price") * qty;
                    }

                    ps.setInt(1, quotationId);
                    ps.setInt(2, pid);
                    ps.setInt(3, qty);
                    ps.setDouble(4, subtotal);
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            // DO NOT clear cart yet
            System.out.println(GREEN + "Quotation submitted! Sales Manager will process it." + RESET);
            MainDB.pause();
        }
    }

    // ==========================================================
    // ADD PRODUCT (with log)
    // ==========================================================
    public static void addProduct(Connection conn, Scanner sc) {
        try {
            MainDB.clearScreen();
            System.out.println("=================================================================");
            System.out.println( "                            ADD PRODUCT"                                                     );
            System.out.println("=================================================================");
            System.out.println(YELLOW + "Type 'back' at any time to go back.\n" + RESET);

            String name;
            do {
                System.out.print("Product name: ");
                name = sc.nextLine().trim();
                if (name.equalsIgnoreCase("back")) return;
                if (name.isEmpty()) {
                    System.out.println(RED + "Product name cannot be empty!" + RESET);
                }
            } while (name.isEmpty());

            double price = 0;
            while (true) {
                System.out.print("Price: ");
                String priceInput = sc.nextLine().trim();
                if (priceInput.equalsIgnoreCase("back")) return;
                try {
                    price = Double.parseDouble(priceInput);
                    if (price < 0) throw new NumberFormatException();
                    break;
                } catch (NumberFormatException e) {
                    System.out.println(RED + "Invalid price! Enter a valid number." + RESET);
                }
            }

            int stock = 0;
            while (true) {
                System.out.print("Stock: ");
                String stockInput = sc.nextLine().trim();
                if (stockInput.equalsIgnoreCase("back")) return;
                try {
                    stock = Integer.parseInt(stockInput);
                    if (stock < 0) throw new NumberFormatException();
                    break;
                } catch (NumberFormatException e) {
                    System.out.println(RED + "Invalid stock! Enter a valid non-negative integer." + RESET);
                }
            }

            String sql = "INSERT INTO products(name, price, stock) VALUES(?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, name);
                ps.setDouble(2, price);
                ps.setInt(3, stock);
                ps.executeUpdate();

                ResultSet keys = ps.getGeneratedKeys();
                if (keys.next()) {
                    int newId = keys.getInt(1);
                    logInventoryChange(conn, newId, "ADD", stock, 0, stock);
                }

                System.out.println(GREEN + "Product added successfully!" + RESET);
            }

            MainDB.pause();
        } catch (SQLException e) {
            System.out.println(RED + "Database error while adding product: " + e.getMessage() + RESET);
            MainDB.pause();
        }
    }

    // ==========================================================
    // EDIT PRODUCT (with log)
    // ==========================================================
    public static void editProduct(Connection conn, Scanner sc) {
        try {
            MainDB.clearScreen();
            System.out.println(     "==================== EDIT PRODUCT ======================"                         );
            viewProductsQuick(conn);

            System.out.println(YELLOW + "\nType 'back' to return.\n" + RESET);
            System.out.print("Enter Product ID to edit: ");
            String input = sc.nextLine().trim();
            if (input.equalsIgnoreCase("back")) return;

            int id;
            try {
                id = Integer.parseInt(input);
            } catch (NumberFormatException e) {
                System.out.println(RED + "Invalid ID!" + RESET);
                MainDB.pause();
                return;
            }

            String checkSql = "SELECT * FROM products WHERE id = ?";
            try (PreparedStatement check = conn.prepareStatement(checkSql)) {
                check.setInt(1, id);
                ResultSet rs = check.executeQuery();
                if (!rs.next()) {
                    System.out.println(RED + "Product not found!" + RESET);
                    MainDB.pause();
                    return;
                }

                String currentName = rs.getString("name");
                double currentPrice = rs.getDouble("price");
                int currentStock = rs.getInt("stock");

                System.out.println("\nEditing Product: " + currentName);
                System.out.print("New name (leave blank to keep '" + currentName + "'): ");
                String name = sc.nextLine().trim();
                if (name.isEmpty()) name = currentName;

                System.out.print("New price (leave blank to keep '" + currentPrice + "'): ");
                String priceInput = sc.nextLine().trim();
                double price = currentPrice;
                if (!priceInput.isEmpty()) {
                    try {
                        price = Double.parseDouble(priceInput);
                    } catch (NumberFormatException e) {
                        System.out.println(RED + "Invalid price format!" + RESET);
                        MainDB.pause();
                        return;
                    }
                }

                System.out.print("New stock (leave blank to keep '" + currentStock + "'): ");
                String stockInput = sc.nextLine().trim();
                int stock = currentStock;
                if (!stockInput.isEmpty()) {
                    try {
                        stock = Integer.parseInt(stockInput);
                    } catch (NumberFormatException e) {
                        System.out.println(RED + "Invalid stock format!" + RESET);
                        MainDB.pause();
                        return;
                    }
                }

                String updateSql = "UPDATE products SET name = ?, price = ?, stock = ? WHERE id = ?";
                try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                    ps.setString(1, name);
                    ps.setDouble(2, price);
                    ps.setInt(3, stock);
                    ps.setInt(4, id);
                    ps.executeUpdate();

                    int changeQty = stock - currentStock; 
                    logInventoryChange(conn, id, "EDIT", changeQty, currentStock, stock);
                    System.out.println(GREEN + "Product updated successfully!" + RESET);
                }
            }

            MainDB.pause();
        } catch (SQLException e) {
            System.out.println(RED + "Error editing product: " + e.getMessage() + RESET);
            MainDB.pause();
        }
    }

    // ==========================================================
    // DELETE PRODUCT (with log)
    // ==========================================================
    public static void deleteProduct(Connection conn, Scanner sc) {
        try {
            MainDB.clearScreen();
            System.out.println(     "==================== DELETE PRODUCT ===================="                         );
            viewProductsQuick(conn);

            System.out.println(YELLOW + "\nType 'back' to return." + RESET);
            System.out.print("Enter Product ID to delete: ");
            String input = sc.nextLine().trim();
            if (input.equalsIgnoreCase("back")) return;

            int id;
            try {
                id = Integer.parseInt(input);
            } catch (NumberFormatException e) {
                System.out.println(RED + "Invalid ID!" + RESET);
                MainDB.pause();
                return;
            }

            String checkSql = "SELECT * FROM products WHERE id = ?";
            try (PreparedStatement check = conn.prepareStatement(checkSql)) {
                check.setInt(1, id);
                ResultSet rs = check.executeQuery();
                if (!rs.next()) {
                    System.out.println(RED + "Product not found!" + RESET);
                    MainDB.pause();
                    return;
                }

                String name = rs.getString("name");
                int stock = rs.getInt("stock");

                System.out.println(YELLOW + "Type CONFIRM DELETE in ALL CAPS to permanently delete '" + name + "'." + RESET);
                System.out.print("Input: ");
                String confirm = sc.nextLine().trim();
                if (!confirm.equals("CONFIRM DELETE")) {
                    System.out.println(YELLOW + "Deletion canceled." + RESET);
                    MainDB.pause();
                    return;
                }

                String deleteSql = "DELETE FROM products WHERE id = ?";
                try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                    ps.setInt(1, id);
                    ps.executeUpdate();
                    logInventoryChange(conn, id, "DELETE", 0, stock, 0);
                    System.out.println(GREEN + "Product deleted successfully!" + RESET);
                }
            }

            MainDB.pause();
        } catch (SQLException e) {
            System.out.println(RED + "Error deleting product: " + e.getMessage() + RESET);
            MainDB.pause();
        }
    }

    // ==========================================================
    // LOG HELPER
    // ==========================================================
    private static void logInventoryChange(Connection conn, int productId, String type, int qty, int prev, int now) {
        try {
            String sql = """
                INSERT INTO inventory_log (product_id, change_type, quantity, previous_stock, new_stock, created_at)
                VALUES (?, ?, ?, ?, ?, NOW())
            """;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, productId);
                ps.setString(2, type);
                ps.setInt(3, qty);
                ps.setInt(4, prev);
                ps.setInt(5, now);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            System.out.println(RED + "Failed to log inventory change: " + e.getMessage() + RESET);
        }
    }

    // ==========================================================
    // VIEW PRODUCTS
    // ==========================================================
    public static void viewProducts(Connection conn) {
        try {
            MainDB.clearScreen();
            System.out.println(     "==================== PRODUCT LIST ===================="                         );
            String sql = "SELECT * FROM products ORDER BY id";
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(sql)) {

                System.out.println("ID\tName\t\tPrice\tStock");
                System.out.println(     "======================================================"                         );
                while (rs.next()) {
                    System.out.printf("%d\t%-15s\t%.2f\t%d%n",
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getDouble("price"),
                            rs.getInt("stock"));
                }
                System.out.println("------------------------------------------------------");
            }
            MainDB.pause();
        } catch (SQLException e) {
            System.out.println(RED + "Error loading products: " + e.getMessage() + RESET);
            MainDB.pause();
        }
    }

    private static void viewProductsQuick(Connection conn) {
        try {
            String sql = "SELECT * FROM products ORDER BY id";
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(sql)) {

                System.out.println("\nID\tName\t\tPrice\tStock");
                System.out.println("--------------------------------------------------------");
                while (rs.next()) {
                    System.out.printf("%d\t%-15s\t%.2f\t%d%n",
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getDouble("price"),
                            rs.getInt("stock"));
                }
                System.out.println("--------------------------------------------------------");
            }
        } catch (SQLException e) {
            System.out.println(RED + "Error loading product list: " + e.getMessage() + RESET);
        }
    }
    
    public static void viewCart(Connection conn, Scanner sc, String username) {
        try {
            String sql = "SELECT c.id, p.name, p.price, c.quantity, (p.price*c.quantity) as total " +
                         "FROM cart c JOIN users u ON c.user_id = u.id " +
                         "JOIN products p ON c.product_id = p.id " +
                         "WHERE u.username=?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, username);
                ResultSet rs = ps.executeQuery();
                
                System.out.println("==================================================");
                System.out.println("YOUR CART");
                System.out.println("==================================================");
                System.out.printf("%-5s %-20s %-10s %-10s %-10s%n", "ID", "Product", "Price", "Qty", "Total");
                System.out.println("--------------------------------------------------");

                boolean hasItems = false;
                while (rs.next()) {
                    hasItems = true;
                    int id = rs.getInt("id");
                    String product = rs.getString("name");
                    double price = rs.getDouble("price");
                    int qty = rs.getInt("quantity");
                    double total = rs.getDouble("total");
                    System.out.printf("%-5d %-20s %-10.2f %-10d %-10.2f%n", id, product, price, qty, total);
                }

                if (!hasItems) System.out.println("Your cart is empty!");
            }
        } catch (SQLException e) {
            System.out.println("Error viewing cart: " + e.getMessage());
        }

        System.out.print("Press Enter to continue...");
        sc.nextLine();
    }

}
