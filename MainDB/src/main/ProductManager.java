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
    public static void manageProducts(Connection conn, Scanner sc, String username, String role) {
        String input = "";
        do {
            try {
                MainDB.clearScreen();
                System.out.println("            Logged in as: " + username + " (" + role.toUpperCase() + ")");
                System.out.println("╔══════════════════════════════════════════════════════════╗");
                System.out.println("║              PRODUCT MANAGEMENT MENU                     ║");
                System.out.println("╚══════════════════════════════════════════════════════════╝");

                System.out.println("╭──────────────────────── Options ─────────────────────────╮");
                System.out.println("│ [1] Manage Products by Category      [4] Manage Restock  │");
                System.out.println("│ [2] Manage Categories                [5] Request Recovery│");
                System.out.println("│ [3] View Inventory Logs              [X] Logout          │");
                System.out.println("╰──────────────────────────────────────────────────────────╯");
                System.out.print("Enter your choice ➤ ");


                input = sc.nextLine().trim().toUpperCase();

                switch (input) {
                    case "1" -> manageProductsByCategory(conn, sc);
                    case "2" -> CategoryManager.manageCategories(conn, sc);
                    case "3" -> LogManager.viewInventoryLog(conn);
                    case "4" -> manageRestock(conn, sc, username);
                    case "5" -> CategoryManager.requestRecovery(conn, sc);

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


    
    private static void manageProductsByCategory(Connection conn, Scanner sc) throws SQLException {
        while (true) { 
            MainDB.clearScreen(); 
            int categoryId = selectCategory(conn, sc);
            if (categoryId == -1) return; 

            while (true) { 
                MainDB.clearScreen();
                displayProductsByCategory(conn, categoryId);
                MainDB.pause();

                System.out.println("\n[1] Add Product");
                System.out.println("[2] Edit Product");
                System.out.println("[3] Delete Product");
                System.out.println("");
                System.out.println("[X] Back to Categories");
                System.out.print("Enter choice ➤ ");

                String choice = sc.nextLine().trim().toUpperCase();
                switch (choice) {
                    case "1" -> addProductToCategory(conn, sc, categoryId);
                    case "2" -> editProduct(conn, sc, categoryId);
                    case "3" -> deleteProduct(conn, sc, categoryId);
                    case "X" -> {
                        break; 
                    }
                    default -> {
                        System.out.println(RED + "Invalid choice!" + RESET);
                        MainDB.pause();
                    }
                }
                if (choice.equals("X")) break; 
            }
        }
    }

    // ==========================================================
    // PUBLIC VIEW PRODUCTS (Users)
    // ==========================================================
    public static void viewAllProducts(Connection conn, Scanner sc, int userId, boolean loggedIn) {
        do {
            try {
                MainDB.clearScreen();

                // CATEGORY SELECTION
                int categoryId = selectCategory(conn, sc);
                if (categoryId == -1) return; 

                boolean inCategory = true;
                while (inCategory) {
                    MainDB.clearScreen();
                    displayProductsByCategory(conn, categoryId);

                    System.out.println("\nPress ENTER to continue...");
                    sc.nextLine();

                    if (loggedIn) {
                        System.out.println("[1] Add to Cart");
                        System.out.println("[2] View Cart");
                        System.out.println("[3] Submit Quotation");
                        System.out.println("");
                        System.out.println("[X] Back");
                        System.out.print("Enter choice: ");
                        String choice = sc.nextLine().trim().toUpperCase();

                        switch (choice) {
                            case "1" -> CartManager.addToCart(conn, sc, userId);
                            case "2" -> CartManager.viewCart(conn, sc, userId);
                            case "3" -> CartManager.submitQuotation(conn, sc, userId, loggedIn);
                            case "X" -> inCategory = false; 
                            default -> {
                                System.out.println(RED + "Invalid choice!" + RESET);
                                MainDB.pause();
                            }
                        }
                    } else {
                        System.out.println("[X] Back");
                        System.out.print("Enter choice: ");
                        String choice = sc.nextLine().trim().toUpperCase();
                        if ("X".equals(choice)) inCategory = false;
                        else {
                            System.out.println(RED + "You must log in to perform that action!" + RESET);
                            MainDB.pause();
                        }
                    }
                }
            } catch (SQLException e) {
                System.out.println(RED + "Database error: " + e.getMessage() + RESET);
                MainDB.pause();
            }
        } while (loggedIn); 
    }
    
    



    // ==========================================================
    // CATEGORY SELECTION
    // ==========================================================
    private static int selectCategory(Connection conn, Scanner sc) throws SQLException {
        String sql = "SELECT id, name FROM categories WHERE active_status = 1 ORDER BY id"; // only active categories
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

            if (ids.isEmpty()) {
                System.out.println(YELLOW + "No categories available." + RESET);
                MainDB.pause();
                return -1;
            }

            System.out.println("");
            System.out.println("[X] Back");
            System.out.print("Select a category ➤ ");
            String input = sc.nextLine().trim();

            if (input.equalsIgnoreCase("X")) {
                return -1; 
            }

            int choice;
            try {
                choice = Integer.parseInt(input);
            } catch (NumberFormatException e) {
                System.out.println(RED + "Invalid input!" + RESET);
                MainDB.pause();
                return selectCategory(conn, sc);
            }

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
            System.out.println("────────────┼────────────────────────────────────┼──────────┼───────┼───────────────────");

            boolean hasProducts = false;

            while (rs.next()) {
                hasProducts = true;

                String code = rs.getString("product_code");
                String name = rs.getString("name");
                double price = rs.getDouble("price");
                int totalStock = rs.getInt("stock");
                String status = rs.getInt("active_status") == 1 ? "Active" : "Deactive";

                // Trim name if too long
                if (name.length() > 35) {
                    name = name.substring(0, 32) + "...";
                }

                // Print product row only — no sizes
                System.out.printf("%-12s│ %-35s│ ₱%7.2f │ %5d │ %-8s%n",
                        code, name, price, totalStock, status);
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
            System.out.print("Enter Product Code to edit (e.g P22988): ");
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
            System.out.print("Enter Product Code to delete (e.g P22988): ");
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

    public static void manageRestock(Connection conn, Scanner sc, String username) {
        String input = "";
        do {
            try {
                MainDB.clearScreen();
                MainDB.clearScreen();
                System.out.println("╭──────────────────────────── Options ──────────────────────────╮");
                System.out.println("│ [1] Restock Dashboard             [2] Restock Products        │");
                System.out.println("│ [X] Back to Product Management                                │");
                System.out.println("╰───────────────────────────────────────────────────────────────╯");
                System.out.print("Enter choice ➤ ");

                input = sc.nextLine().trim().toUpperCase();

                switch (input) {
                    case "1" -> showRestockDashboard(conn);
                    case "2" -> restockProducts(conn, sc);
                    case "X" -> {} // exit loop
                    default -> {
                        System.out.println(RED + "Invalid choice!" + RESET);
                        MainDB.pause();
                    }
                }
            } catch (SQLException e) {
                System.out.println(RED + "Database error: " + e.getMessage() + RESET);
                MainDB.pause();
            }
        } while (!input.equals("X"));
    }

    // ==========================================================
    // RESTOCK DASHBOARD
    // ==========================================================
    private static void showRestockDashboard(Connection conn) throws SQLException {
        MainDB.clearScreen();
        System.out.println("╔════════════════════════════════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                                         RESTOCK DASHBOARD                                              ║");
        System.out.println("╚════════════════════════════════════════════════════════════════════════════════════════════════════════╝");

        String sql = """
            SELECT c.name AS category, p.name AS product, ps.size, ps.stock, ps.damaged, ps.critical_stock
            FROM products p
            LEFT JOIN product_sizes ps ON p.id = ps.product_id
            LEFT JOIN categories c ON p.category_id = c.id
            WHERE p.active_status = 1
            ORDER BY c.name, p.name, ps.size
        """;

        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            int categoryWidth = 50;   // wider category
            int productWidth = 60;    // wider product name
            int sizeWidth = 8;
            int stockWidth = 7;
            int damagedWidth = 9;
            int statusWidth = 15;

            // Header
            System.out.printf("%-" + categoryWidth + "s │ %-" + productWidth + "s │ %-" + sizeWidth + "s │ %-" + stockWidth + "s │ %-" + damagedWidth + "s │ %-" + statusWidth + "s%n",
                "Category", "Product Name", "Size", "Stock", "Damaged", "Status");

            // Separator line
            System.out.println("───────────────────────────────────────────────────┼──────────────────────────────────────────────────────────────┼──────────┼─────────┼───────────┼───────────────");

            boolean hasData = false;
            while (rs.next()) {
                hasData = true;
                String category = rs.getString("category");
                String name = rs.getString("product");
                String size = rs.getString("size") == null ? "-" : rs.getString("size");
                int stock = rs.getInt("stock");
                int damaged = rs.getInt("damaged");
                int critical = rs.getInt("critical_stock");

                // Truncate if too long
                if (category.length() > categoryWidth) category = category.substring(0, categoryWidth - 3) + "...";
                if (name.length() > productWidth) name = name.substring(0, productWidth - 3) + "...";

                String status;
                if (stock == 0) status = RED + "Out of Stock" + RESET;
                else if (stock <= critical) status = YELLOW + "Low Stock" + RESET;
                else status = GREEN + "Safe Stock" + RESET;

                System.out.printf("%-" + categoryWidth + "s │ %-" + productWidth + "s │ %-" + sizeWidth + "s │ %-" + stockWidth + "d │ %-" + damagedWidth + "d │ %-" + statusWidth + "s%n",
                    category, name, size, stock, damaged, status);
            }

            if (!hasData) System.out.println(YELLOW + "No products/sizes found." + RESET);
        }

        System.out.println("\nPress ENTER to return...");
        MainDB.pause();
    }




    // ==========================================================
    // RESTOCK PRODUCTS
    // ==========================================================
    private static void restockProducts(Connection conn, Scanner sc) throws SQLException {
        while (true) {
            MainDB.clearScreen();
            System.out.println("╔════════════════════════════════════════╗");
            System.out.println("║            RESTOCK PRODUCTS            ║");
            System.out.println("╚════════════════════════════════════════╝");

            String sqlCategories = "SELECT id, name FROM categories WHERE active_status = 1 ORDER BY id ASC";
            List<Integer> categoryIds = new ArrayList<>();
            List<String> categoryNames = new ArrayList<>();
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sqlCategories)) {
                int idx = 1;
                System.out.println("\nChoose a category:");
                while (rs.next()) {
                    categoryIds.add(rs.getInt("id"));
                    categoryNames.add(rs.getString("name"));
                    System.out.printf("%d. %s%n", idx++, rs.getString("name"));
                }
            }

            if (categoryIds.isEmpty()) {
                System.out.println(YELLOW + "No categories available." + RESET);
                MainDB.pause();
                return;
            }

            System.out.print("Enter category number or BACK to exit: ");
            String categoryChoice = sc.nextLine().trim().toUpperCase();
            if (categoryChoice.equals("BACK")) return;

            int selectedCategory;
            try {
                selectedCategory = Integer.parseInt(categoryChoice) - 1;
                if (selectedCategory < 0 || selectedCategory >= categoryIds.size()) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                System.out.println(RED + "Invalid selection!" + RESET);
                MainDB.pause();
                continue; // go back to category selection
            }
            int categoryId = categoryIds.get(selectedCategory);

            while (true) { // product selection loop
                MainDB.clearScreen();
                String sqlProducts = "SELECT id, name FROM products WHERE category_id = ? AND active_status = 1 ORDER BY name";
                List<Integer> productIds = new ArrayList<>();
                List<String> productNames = new ArrayList<>();
                try (PreparedStatement ps = conn.prepareStatement(sqlProducts)) {
                    ps.setInt(1, categoryId);
                    ResultSet rs = ps.executeQuery();
                    int idx = 1;
                    System.out.println("╔══════════════════════════════════════╗");
                    System.out.println("║         PRODUCTS TO RESTOCK          ║");
                    System.out.println("╚══════════════════════════════════════╝");
                    System.out.printf("%-4s │ %-30s%n", "No.", "Product Name");
                    System.out.println("─────┼─────────────────────────────────");
                    while (rs.next()) {
                        productIds.add(rs.getInt("id"));
                        productNames.add(rs.getString("name"));
                        System.out.printf("%-4d │ %-30s%n", idx++, rs.getString("name"));
                    }
                }

                if (productIds.isEmpty()) {
                    System.out.println(YELLOW + "No products in this category." + RESET);
                    MainDB.pause();
                    break; // back to category selection
                }

                System.out.print("\nEnter product number or BACK to return to categories: ");
                String productChoice = sc.nextLine().trim().toUpperCase();
                if (productChoice.equals("BACK")) break; // go back to category selection

                int selectedProduct;
                try {
                    selectedProduct = Integer.parseInt(productChoice) - 1;
                    if (selectedProduct < 0 || selectedProduct >= productIds.size()) throw new NumberFormatException();
                } catch (NumberFormatException e) {
                    System.out.println(RED + "Invalid selection!" + RESET);
                    MainDB.pause();
                    continue; // back to product selection
                }
                int productId = productIds.get(selectedProduct);

                while (true) { // size selection loop
                    MainDB.clearScreen();
                    String sqlSizes = "SELECT id, size, stock, damaged FROM product_sizes WHERE product_id = ? ORDER BY size";
                    List<Integer> sizeIds = new ArrayList<>();
                    List<Integer> stocks = new ArrayList<>();
                    List<Integer> damagedList = new ArrayList<>();
                    List<String> sizes = new ArrayList<>();

                    try (PreparedStatement ps = conn.prepareStatement(sqlSizes)) {
                        ps.setInt(1, productId);
                        ResultSet rs = ps.executeQuery();
                        int idx = 1;
                        System.out.println("\nAvailable sizes:");
                        System.out.printf("%-4s │ %-8s │ %-6s │ %-7s%n", "No.", "Size", "Stock", "Damaged");
                        System.out.println("─────┼─────────┼───────┼────────");
                        while (rs.next()) {
                            sizeIds.add(rs.getInt("id"));
                            sizes.add(rs.getString("size"));
                            stocks.add(rs.getInt("stock"));
                            damagedList.add(rs.getInt("damaged"));
                            System.out.printf("%-4d │ %-8s │ %-6d │ %-7d%n", idx++, rs.getString("size"), rs.getInt("stock"), rs.getInt("damaged"));
                        }
                    }

                    if (sizeIds.isEmpty()) {
                        System.out.println(YELLOW + "No sizes available for this product." + RESET);
                        MainDB.pause();
                        break; // back to product selection
                    }

                    System.out.print("\nEnter size number to restock or BACK to return to products: ");
                    String sizeChoice = sc.nextLine().trim().toUpperCase();
                    if (sizeChoice.equals("BACK")) break; // back to product selection

                    int selectedSize;
                    try {
                        selectedSize = Integer.parseInt(sizeChoice) - 1;
                        if (selectedSize < 0 || selectedSize >= sizeIds.size()) throw new NumberFormatException();
                    } catch (NumberFormatException e) {
                        System.out.println(RED + "Invalid selection!" + RESET);
                        MainDB.pause();
                        continue; // back to size selection
                    }

                    int receivedQty = 0, damagedQty = 0;

                    while (true) {
                        System.out.print("Enter quantity received (must be > 0): ");
                        try {
                            receivedQty = Integer.parseInt(sc.nextLine().trim());
                            if (receivedQty <= 0) throw new NumberFormatException();
                            break;
                        } catch (NumberFormatException e) {
                            System.out.println(RED + "Quantity must be a positive number!" + RESET);
                        }
                    }

                    while (true) {
                        System.out.print("Enter damaged quantity (0 - " + receivedQty + "): ");
                        try {
                            damagedQty = Integer.parseInt(sc.nextLine().trim());
                            if (damagedQty < 0 || damagedQty > receivedQty) throw new NumberFormatException();
                            break;
                        } catch (NumberFormatException e) {
                            System.out.println(RED + "Damaged quantity must be between 0 and received quantity!" + RESET);
                        }
                    }

                    int goodQty = receivedQty - damagedQty;
                    int selectedSizeId = sizeIds.get(selectedSize);
                    int prevStock = stocks.get(selectedSize);
                    int newStock = prevStock + goodQty;

                    String sqlUpdate = "UPDATE product_sizes SET stock = stock + ?, `damaged` = `damaged` + ? WHERE id = ?";
                    try (PreparedStatement ps = conn.prepareStatement(sqlUpdate)) {
                        ps.setInt(1, goodQty);
                        ps.setInt(2, damagedQty);
                        ps.setInt(3, selectedSizeId);
                        ps.executeUpdate();
                    }

                    logInventoryChange(conn, selectedSizeId, "RESTOCK", goodQty, prevStock, newStock);

                    System.out.println(GREEN + "Restock complete! Good: " + goodQty + ", Damaged: " + damagedQty + RESET);
                    MainDB.pause();
                }
            }
        }
    }


    
}
