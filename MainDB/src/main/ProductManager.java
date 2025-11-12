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
                System.out.println("╔══════════════════════════════════════════════════════════╗");
                System.out.println("║                 PRODUCT MANAGEMENT MENU                  ║");
                System.out.println("╚══════════════════════════════════════════════════════════╝");
                System.out.println("[1]   Manage Products by Category");
                System.out.println("[2]   View Inventory Logs");
                System.out.println("[3]   Logout");
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
                    case 1 -> manageProductsByCategory(conn, sc);
                    case 2 -> LogManager.viewInventoryLog(conn);
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
    
    private static void manageProductsByCategory(Connection conn, Scanner sc) throws SQLException {
        MainDB.clearScreen(); // <-- Clear screen before showing categories

        int categoryId = selectCategory(conn, sc);
        if (categoryId == -1) return; // user chose Back

        int choice = 0; // declare here for the loop
        do {
            MainDB.clearScreen(); // Clear the screen before displaying products
            displayProductsByCategory(conn, categoryId);
            MainDB.pause(); // Pause so user can read

            System.out.println("\n[1] Add Product");
            System.out.println("[2] Edit Product");
            System.out.println("[3] Delete Product");
            System.out.println("[4] Back");
            System.out.print("Enter choice ➤ ");

            try {
                choice = Integer.parseInt(sc.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println(RED + "Invalid input!" + RESET);
                MainDB.pause();
                continue;
            }

            switch (choice) {
                case 1 -> addProductToCategory(conn, sc, categoryId);
                case 2 -> editProduct(conn, sc, categoryId);
                case 3 -> deleteProduct(conn, sc, categoryId);
                case 4 -> {}
                default -> {
                    System.out.println(RED + "Invalid choice!" + RESET);
                    MainDB.pause();
                }
            }
        } while (choice != 4);
    }

    // ==========================================================
    // PUBLIC VIEW PRODUCTS (Users)
    // ==========================================================
    public static void viewAllProducts(Connection conn, Scanner sc, int userId, boolean loggedIn) {
        int choice = 0;
        do {
            try {
                MainDB.clearScreen();
                int categoryId = selectCategory(conn, sc);
                if (categoryId == -1) return;

                MainDB.clearScreen();
                displayProductsByCategory(conn, categoryId);

                System.out.println("\nPress ENTER to continue...");
                sc.nextLine();

                if (loggedIn) {
                    System.out.println("1. Add to Cart");
                    System.out.println("2. View Cart");
                    System.out.println("3. Submit Quotation");
                    System.out.println("4. Back");
                    System.out.print("Enter choice: ");
                    choice = Integer.parseInt(sc.nextLine().trim());

                    switch (choice) {
                        case 1 -> CartManager.addToCart(conn, sc, userId);
                        case 2 -> CartManager.viewCart(conn, sc, userId);
                        case 3 -> CartManager.submitQuotation(conn, sc, userId, loggedIn);
                        case 4 -> {}
                        default -> {
                            System.out.println(RED + "Invalid choice!" + RESET);
                            MainDB.pause();
                        }
                    }
                } else {
                    System.out.println("1. Back");
                    System.out.print("Enter choice: ");
                    choice = Integer.parseInt(sc.nextLine().trim());
                    if (choice != 1) {
                        System.out.println(RED + "You must log in to perform that action!" + RESET);
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
        } while (loggedIn ? choice != 4 : choice != 1);
    }

    // ==========================================================
    // CATEGORY SELECTION
    // ==========================================================
    private static int selectCategory(Connection conn, Scanner sc) throws SQLException {
        String sql = "SELECT id, name FROM categories ORDER BY id";
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            List<Integer> ids = new ArrayList<>();
            List<String> names = new ArrayList<>();

            System.out.println("╔══════════════════════════════════════════════════════════╗");
            System.out.println("║                   AVAILABLE CATEGORIES                   ║");
            System.out.println("╚══════════════════════════════════════════════════════════╝");

            int index = 1;
            while (rs.next()) {
                ids.add(rs.getInt("id"));
                names.add(rs.getString("name"));
                System.out.printf("[%d] %s%n", index++, rs.getString("name"));
            }
            
            System.out.println("");
            System.out.printf("[%d] Back%n", index);
            System.out.print("Select a category ➤ ");
            String input = sc.nextLine().trim();

            int choice;
            try {
                choice = Integer.parseInt(input);
            } catch (NumberFormatException e) {
                System.out.println(RED + "Invalid input!" + RESET);
                MainDB.pause();
                return selectCategory(conn, sc);
            }

            if (choice == index) return -1;
            if (choice < 1 || choice > ids.size()) {
                System.out.println(RED + "Invalid choice!" + RESET);
                MainDB.pause();
                return selectCategory(conn, sc);
            }

            return ids.get(choice - 1);
        }
    }

    private static String generateProductCode() {
        int randomNum = 10000 + (int)(Math.random() * 90000);
        return "P" + randomNum;
    }

    // ==========================================================
    // DISPLAY PRODUCTS BY CATEGORY
    // ==========================================================
    private static void displayProductsByCategory(Connection conn, int categoryId) throws SQLException {
    	String sql = "SELECT * FROM products WHERE category_id = ? AND active_status = 1 ORDER BY product_code";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, categoryId);
            ResultSet rs = ps.executeQuery();

            System.out.println("╔════════════════════════════════════════════════════════════════════════════════════╗");
            System.out.println("║                                PRODUCT LIST                                        ║");
            System.out.println("╚════════════════════════════════════════════════════════════════════════════════════╝");
            System.out.printf("%-12s│ %-35s│ %-9s│ %-5s │ %-8s%n", "ID Code", "Product Name", "Price", "Stock", "Status");
            System.out.println("────────────┼────────────────────────────────────┼──────────┼───────┼────────");

            boolean hasProducts = false;
            while (rs.next()) {
                hasProducts = true;
                String name = rs.getString("name");
                if (name.length() > 35) name = name.substring(0, 32) + "...";

                String status = rs.getInt("active_status") == 1 ? "Active" : "Deactive";

                System.out.printf("%-12s│ %-35s│ ₱%7.2f │ %5d │ %-8s%n",
                        rs.getString("product_code"),
                        name,
                        rs.getDouble("price"),
                        rs.getInt("stock"),
                        status);
            }

            if (!hasProducts) {
                System.out.println(YELLOW + "No products found in this category." + RESET);
            }

            System.out.println("────────────────────────────────────────────────────────────────────────────────────────");
        }
    }


    // ==========================================================
    // ADD PRODUCT (with log)
    // ==========================================================
    public static void addProductToCategory(Connection conn, Scanner sc, int categoryId) {
        try {
            MainDB.clearScreen();
            System.out.println("╔══════════════════════════════════════════════════════════╗");
            System.out.println("║                      ADD PRODUCT                         ║");
            System.out.println("╚══════════════════════════════════════════════════════════╝");
            System.out.println(YELLOW + "Type 'back' at any time to go back.\n" + RESET);

            // Name
            String name;
            do {
                System.out.print("Product name: ");
                name = sc.nextLine().trim();
                if (name.equalsIgnoreCase("back")) return;
                if (name.isEmpty()) System.out.println(RED + "Product name cannot be empty!" + RESET);
            } while (name.isEmpty());

            // Price
            double price = 0;
            while (true) {
                System.out.print("Price: ");
                String input = sc.nextLine().trim();
                if (input.equalsIgnoreCase("back")) return;
                try {
                    price = Double.parseDouble(input);
                    if (price < 0) throw new NumberFormatException();
                    break;
                } catch (NumberFormatException e) {
                    System.out.println(RED + "Invalid price!" + RESET);
                }
            }

            // Stock
            int stock = 0;
            while (true) {
                System.out.print("Stock: ");
                String input = sc.nextLine().trim();
                if (input.equalsIgnoreCase("back")) return;
                try {
                    stock = Integer.parseInt(input);
                    if (stock < 0) throw new NumberFormatException();
                    break;
                } catch (NumberFormatException e) {
                    System.out.println(RED + "Invalid stock!" + RESET);
                }
            }

            // ✅ Generate product code
            String productCode = generateProductCode();

            String sql = "INSERT INTO products(product_code, name, price, stock, category_id) VALUES(?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, productCode);
                ps.setString(2, name);
                ps.setDouble(3, price);
                ps.setInt(4, stock);
                ps.setInt(5, categoryId);
                ps.executeUpdate();

                ResultSet keys = ps.getGeneratedKeys();
                if (keys.next()) logInventoryChange(conn, keys.getInt(1), "ADD", stock, 0, stock);

                System.out.println(GREEN + "Product added successfully!" + RESET);
                System.out.println("Generated Product Code: " + YELLOW + productCode + RESET);
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
    public static void editProduct(Connection conn, Scanner sc, int categoryId) {
        try {
            MainDB.clearScreen();
            System.out.println("╔══════════════════════════════════════════════════════════╗");
            System.out.println("║                     EDIT PRODUCT MENU                    ║");
            System.out.println("╚══════════════════════════════════════════════════════════╝");
            
            // Display only active products in this category
            displayProductsByCategory(conn, categoryId);

            System.out.println(YELLOW + "\nType 'back' to return.\n" + RESET);
            System.out.print("Enter Product Code to edit: ");
            String code = sc.nextLine().trim();
            if (code.equalsIgnoreCase("back")) return;

            String checkSql = "SELECT * FROM products WHERE product_code = ? AND category_id = ? AND active_status = 1";
            try (PreparedStatement check = conn.prepareStatement(checkSql)) {
                check.setString(1, code);
                check.setInt(2, categoryId);
                ResultSet rs = check.executeQuery();

                if (!rs.next()) {
                    System.out.println(RED + "Product not found in this category!" + RESET);
                    MainDB.pause();
                    return;
                }

                int id = rs.getInt("id");
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

                String updateSql = "UPDATE products SET name = ?, price = ?, stock = ? WHERE product_code = ?";
                try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                    ps.setString(1, name);
                    ps.setDouble(2, price);
                    ps.setInt(3, stock);
                    ps.setString(4, code);
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
    public static void deleteProduct(Connection conn, Scanner sc, int categoryId) {
        try {
            MainDB.clearScreen();
            System.out.println("╔══════════════════════════════════════════════════════════╗");
            System.out.println("║                    DELETE PRODUCT MENU                   ║");
            System.out.println("╚══════════════════════════════════════════════════════════╝");

            // Display only active products in this category
            displayProductsByCategory(conn, categoryId);

            System.out.println(YELLOW + "\nType 'back' to return." + RESET);
            System.out.print("Enter Product Code to delete: ");
            String code = sc.nextLine().trim();
            if (code.equalsIgnoreCase("back")) return;

            String checkSql = "SELECT * FROM products WHERE product_code = ? AND category_id = ? AND active_status = 1";
            try (PreparedStatement check = conn.prepareStatement(checkSql)) {
                check.setString(1, code);
                check.setInt(2, categoryId);
                ResultSet rs = check.executeQuery();

                if (!rs.next()) {
                    System.out.println(RED + "Product not found or already deactivated!" + RESET);
                    MainDB.pause();
                    return;
                }

                int id = rs.getInt("id");
                String name = rs.getString("name");
                int stock = rs.getInt("stock");

                System.out.println(YELLOW + "Type CONFIRM DELETE in ALL CAPS to deactivate '" + name + "'." + RESET);
                System.out.print("Input: ");
                String confirm = sc.nextLine().trim();
                if (!confirm.equals("CONFIRM DELETE")) {
                    System.out.println(YELLOW + "Deactivation canceled." + RESET);
                    MainDB.pause();
                    return;
                }

                // Soft delete: set active_status = 0
                String updateSql = "UPDATE products SET active_status = 0 WHERE product_code = ?";
                try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                    ps.setString(1, code);
                    ps.executeUpdate();

                    logInventoryChange(conn, id, "DELETE", 0, stock, 0);
                    System.out.println(GREEN + "Product deactivated successfully!" + RESET);
                }
            }

            MainDB.pause();
        } catch (SQLException e) {
            System.out.println(RED + "Error deactivating product: " + e.getMessage() + RESET);
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
            System.out.println("╔═══════════════════════════════════════════════════════════╗");
            System.out.println("║                        PRODUCT LIST                       ║");
            System.out.println("╚═══════════════════════════════════════════════════════════╝");

            String sql = "SELECT * FROM products ORDER BY product_code";
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(sql)) {

                System.out.println("ID Code     │ Product Name                 │   Price   │ Stock");
                System.out.println("────────────┼──────────────────────────────┼───────────┼──────");

                int totalProducts = 0;
                while (rs.next()) {
                    String code = rs.getString("product_code");
                    String name = rs.getString("name");
                    double price = rs.getDouble("price");
                    int stock = rs.getInt("stock");

                    System.out.printf("%-10s │ %-28s │ ₱ %7.2f │ %4d%n", code, name, price, stock);
                    totalProducts++;
                }

                System.out.println("────────────────────────────────────────────────────────────");
                System.out.printf("📦 Total Products: %d%n", totalProducts);
            }

            System.out.print("Press ENTER to return...");
            MainDB.pause();

        } catch (SQLException e) {
            System.out.println(RED + "Error loading products: " + e.getMessage() + RESET);
            MainDB.pause();
        }
    }


    private static void viewProductsQuick(Connection conn) {
        try {
            String sql = "SELECT * FROM products ORDER BY product_code";
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(sql)) {

                System.out.println();
                System.out.println("ID Code     │ Product Name              │   Price    │ Stock");
                System.out.println("────────────┼───────────────────────────┼────────────┼──────");

                while (rs.next()) {
                    System.out.printf("%-10s │ %-25s │ ₱ %-8.2f │ %-5d%n",
                            rs.getString("product_code"),
                            rs.getString("name"),
                            rs.getDouble("price"),
                            rs.getInt("stock"));
                }

                System.out.println("────────────────────────────────────────────────────────────");
            }
        } catch (SQLException e) {
            System.out.println(RED + "Error loading product list: " + e.getMessage() + RESET);
        }
    }
}
