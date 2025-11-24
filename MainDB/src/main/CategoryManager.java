package main;

import java.sql.*;
import java.util.*;

public class CategoryManager {
    static final String RESET = "\u001B[0m";
    static final String GREEN = "\u001B[32m";
    static final String RED = "\u001B[31m";
    static final String YELLOW = "\u001B[33m";

    // ================================
    // CATEGORY MENU
    // ================================
    public static void manageCategories(Connection conn, Scanner sc) {
        while (true) {
            MainDB.clearScreen();
            System.out.println("╔══════════════════════════════════════════════════════════╗");
            System.out.println("║                    CATEGORY MANAGEMENT                   ║");
            System.out.println("╚══════════════════════════════════════════════════════════╝");

            System.out.println("╭───────────────────── Options ────────────────────────────╮");
            System.out.println("│ [1] View Categories           [4] Delete Category        │");
            System.out.println("│ [2] Add Category              [X] Back                   │");
            System.out.println("│ [3] Edit Category                                        │");
            System.out.println("╰──────────────────────────────────────────────────────────╯");
            System.out.print("Enter choice ➤ ");

            String input = sc.nextLine().trim().toUpperCase();

            switch (input) {
                case "1" -> viewCategories(conn);
                case "2" -> addCategory(conn, sc);
                case "3" -> editCategory(conn, sc);
                case "4" -> deleteCategory(conn, sc);
                case "X" -> {
                    return; 
                }
                default -> {
                    System.out.println(RED + "Invalid choice!" + RESET);
                    MainDB.pause();
                }
            }
        }
    }

    private static String shorten(String text, int max) {
        if (text.length() <= max) return text;
        return text.substring(0, max - 3) + "...";
    }
    
    // ================================
    // DISPLAY CATEGORIES (NO PAUSE)
    // ================================
    public static void displayCategories(Connection conn) {
        try {
            String sql = "SELECT id, name FROM categories WHERE active_status = 1 ORDER BY id";

            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(sql)) {

                System.out.println("ID   │ Category Name");
                System.out.println("─────┼──────────────────────────────────────────────────────────");

                while (rs.next()) {
                	String name = shorten(rs.getString("name"), 50);
                	System.out.printf("%-4d │ %s%n", rs.getInt("id"), name);
                }

                System.out.println("─────┼──────────────────────────────────────────────────────────");
            }
        } catch (Exception e) {
            System.out.println(RED + "Error loading categories: " + e.getMessage() + RESET);
        }
    }

    // ================================
    // VIEW CATEGORIES (WITH PAUSE)
    // ================================
    public static void viewCategories(Connection conn) {
        try {
            MainDB.clearScreen();
            System.out.println("╔══════════════════════════════════════════════════════════╗");
            System.out.println("║                      CATEGORY LIST                       ║");
            System.out.println("╚══════════════════════════════════════════════════════════╝");

            displayCategories(conn);

            MainDB.pause();
        } catch (Exception e) {
            System.out.println(RED + "Error loading categories: " + e.getMessage() + RESET);
            MainDB.pause();
        }
    }

    // ================================
    // ADD CATEGORY
    // ================================
    public static void addCategory(Connection conn, Scanner sc) {
        try {
            MainDB.clearScreen();
            System.out.println("╔══════════════════════════════════════════════════════════╗");
            System.out.println("║                      ADD CATEGORY                        ║");
            System.out.println("╚══════════════════════════════════════════════════════════╝");
            System.out.println(YELLOW + "Type 'back' at any time to go back.\n" + RESET);

            String name;

            while (true) {
                System.out.print("Category name: ");
                name = sc.nextLine().trim();

                if (name.equalsIgnoreCase("back")) return;

                if (name.isEmpty()) {
                    System.out.println(RED + "Category name cannot be empty!" + RESET);
                    continue;
                }

                break;
            }

            String sql = "INSERT INTO categories(name, active_status) VALUES(?, 1)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, name);
                ps.executeUpdate();
            }

            System.out.println(GREEN + "Category added successfully!" + RESET);
            MainDB.pause();

        } catch (Exception e) {
            System.out.println(RED + "Error adding category: " + e.getMessage() + RESET);
            MainDB.pause();
        }
    }

    // ================================
    // EDIT CATEGORY
    // ================================
    public static void editCategory(Connection conn, Scanner sc) {
        try {
            MainDB.clearScreen();
            System.out.println("╔══════════════════════════════════════════════════════════╗");
            System.out.println("║                      EDIT CATEGORY                       ║");
            System.out.println("╚══════════════════════════════════════════════════════════╝");
           

            displayCategories(conn);
            System.out.print("");
            System.out.println(YELLOW + "Type 'back' at any time to go back.\n" + RESET);

            Integer id = MainDB.readInt(sc, "\nEnter category ID to edit: ");
            if (id == null) return;


            // New name
            String newName;
            while (true) {
                System.out.print("Enter new name: ");
                newName = sc.nextLine().trim();

                if (newName.equalsIgnoreCase("back")) return;

                if (newName.isEmpty()) {
                    System.out.println(RED + "Name cannot be empty!" + RESET);
                    continue;
                }
                break;
            }

            String sql = "UPDATE categories SET name = ? WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, newName);
                ps.setInt(2, id);
                ps.executeUpdate();
            }

            System.out.println(GREEN + "Category updated!" + RESET);
            MainDB.pause();

        } catch (Exception e) {
            System.out.println(RED + "Error editing category: " + e.getMessage() + RESET);
            MainDB.pause();
        }
    }

    // ================================
    // DELETE CATEGORY
    // ================================
    public static void deleteCategory(Connection conn, Scanner sc) {
        try {
            MainDB.clearScreen();
            System.out.println("╔══════════════════════════════════════════════════════════╗");
            System.out.println("║                     DELETE CATEGORY                      ║");
            System.out.println("╚══════════════════════════════════════════════════════════╝");
            

            displayCategories(conn);

            System.out.print("");
            System.out.println(YELLOW + "Type 'back' at any time to go back.\n" + RESET);
            Integer id = MainDB.readInt(sc, "\nEnter category ID to delete: ");
            if (id == null) return;


            System.out.println(YELLOW + "This will delete the category. Products inside will NOT be deleted." + RESET);
            System.out.print("Type CONFIRM DELETE: ");
            String confirm = sc.nextLine().trim();

            if (confirm.equalsIgnoreCase("back")) return;

            if (!confirm.equals("CONFIRM DELETE")) {
                System.out.println(YELLOW + "Canceled." + RESET);
                MainDB.pause();
                return;
            }

            String sql = "UPDATE categories SET active_status = 0 WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, id);
                ps.executeUpdate();
            }

            System.out.println(GREEN + "Category deleted!" + RESET);
            MainDB.pause();

        } catch (Exception e) {
            System.out.println(RED + "Error deleting category: " + e.getMessage() + RESET);
            MainDB.pause();
        }
    }
    
    public static void requestRecovery(Connection conn, Scanner sc) {
        while (true) {
            MainDB.clearScreen();
            System.out.println("╔══════════════════════════════════════════════════════════╗");
            System.out.println("║                  REQUEST RECOVERY MENU                   ║");
            System.out.println("╚══════════════════════════════════════════════════════════╝");
            System.out.println("╭───────────────────── Options ────────────────────────────╮");
            System.out.println("│ [1] Category Recovery        [2] Product Recovery        │");
            System.out.println("│ [3] Check Status                                         │");
            System.out.println("│ [X] Back                                                 │");
            System.out.println("╰──────────────────────────────────────────────────────────╯");
            System.out.print("Enter choice ➤ ");

            String input = sc.nextLine().trim().toUpperCase();

            switch (input) {
                case "1" -> requestCategoryRecovery(conn, sc);
                case "2" -> requestProductRecovery(conn, sc);
                case "3" -> checkPendingStatus(conn);
                case "X" -> { return; }
                default -> {
                    System.out.println(RED + "Invalid choice!" + RESET);
                    MainDB.pause();
                }
            }
        }
    }

    // ============================
    // Check Pending Status
    // ============================
    private static void checkPendingStatus(Connection conn) {
        try {
            MainDB.clearScreen();
            System.out.println("╔══════════════════════════════════════════════════════════╗");
            System.out.println("║                    PENDING RECOVERY STATUS               ║");
            System.out.println("╚══════════════════════════════════════════════════════════╝");

            // Categories
            String sqlCat = "SELECT id, name FROM categories WHERE active_status = 2 ORDER BY id";
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sqlCat)) {
                System.out.println("\nPending Categories:");
                System.out.println("ID   │ Category Name");
                System.out.println("─────┼──────────────────────────────────────────────────────────");
                while (rs.next()) {
                    int id = rs.getInt("id");
                    String name = rs.getString("name");
                    System.out.printf("%-4d │ %s%n", id, name);
                }
                System.out.println("─────┼──────────────────────────────────────────────────────────");
            }

            // Products
            String sqlProd = """
                SELECT p.id AS product_id, p.name AS product_name, c.name AS category_name
                FROM products p
                JOIN categories c ON p.category_id = c.id
                WHERE p.active_status = 2
                ORDER BY p.name, c.name
            """;

            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sqlProd)) {
                System.out.println("\nPending Products:");
                System.out.printf("%-4s │ %-40s │ %-40s%n", "ID", "Product Name", "Category");
                System.out.println("─────┼──────────────────────────────────────────┼──────────────────────────────────────────────");
                while (rs.next()) {
                    int id = rs.getInt("product_id");
                    String productName = rs.getString("product_name");
                    String categoryName = rs.getString("category_name");

                    String pName = shorten(productName, 40);
                    String cName = shorten(categoryName, 40);

                    System.out.printf("%-4d │ %-40s │ %-40s%n", id, pName, cName);
                }
                System.out.println("─────┼──────────────────────────────────────────┼──────────────────────────────────────────────");
            }

            MainDB.pause();

        } catch (Exception e) {
            System.out.println(RED + "Error checking pending status: " + e.getMessage() + RESET);
            MainDB.pause();
        }
    }


    // ============================
    // Category Recovery
    // ============================
    private static void requestCategoryRecovery(Connection conn, Scanner sc) {
        try {
            MainDB.clearScreen();
            System.out.println("╔══════════════════════════════════════════════════════════╗");
            System.out.println("║                  REQUEST CATEGORY RECOVERY               ║");
            System.out.println("╚══════════════════════════════════════════════════════════╝");

            String sql = "SELECT id, name FROM categories WHERE active_status = 0 ORDER BY id";
            List<Integer> ids = new ArrayList<>();
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
                System.out.println("ID   │ Category Name");
                System.out.println("─────┼──────────────────────────────────────────────────────────");
                while (rs.next()) {
                    ids.add(rs.getInt("id"));
                    String name = shorten(rs.getString("name"), 50);
                    System.out.printf("%-4d │ %s%n", rs.getInt("id"), name);

                }
                System.out.println("─────┼──────────────────────────────────────────────────────────");
            }

            if (ids.isEmpty()) {
                System.out.println(YELLOW + "No inactive categories available." + RESET);
                MainDB.pause();
                return;
            }

            System.out.println(YELLOW + "Enter a category ID to request recovery. Type 'back' to cancel." + RESET);

            Integer selectedId = MainDB.readInt(sc, "Enter category ID: ");
            if (selectedId == null) return;



            if (!ids.contains(selectedId)) {
                System.out.println(RED + "Category ID not found!" + RESET);
                MainDB.pause();
                return;
            }

            String updateSql = "UPDATE categories SET active_status = 2 WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                ps.setInt(1, selectedId);
                ps.executeUpdate();
            }

            System.out.println(GREEN + "Category recovery request submitted! Status set to PENDING." + RESET);
            MainDB.pause();

        } catch (Exception e) {
            System.out.println(RED + "Error requesting category recovery: " + e.getMessage() + RESET);
            MainDB.pause();
        }
    }

    // ============================
    // Product Recovery
    // ============================
    private static void requestProductRecovery(Connection conn, Scanner sc) {
        try {
            MainDB.clearScreen();
            System.out.println("╔══════════════════════════════════════════════════════════╗");
            System.out.println("║                  REQUEST PRODUCT RECOVERY                ║");
            System.out.println("╚══════════════════════════════════════════════════════════╝");

            String sql = """
                SELECT p.id AS product_id, p.name AS product_name, c.name AS category_name
                FROM products p
                JOIN categories c ON p.category_id = c.id
                WHERE p.active_status = 0
                ORDER BY p.name, c.name
            """;

            Map<Integer, String> productMap = new LinkedHashMap<>();
            Map<Integer, String> productCategory = new LinkedHashMap<>();

            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {

                System.out.printf("%-4s │ %-40s │ %-40s%n", "ID", "Product Name", "Category");
                System.out.println("─────┼──────────────────────────────────────────┼──────────────────────────────────────────────");

                while (rs.next()) {
                    int productId = rs.getInt("product_id");
                    String productName = rs.getString("product_name");
                    String categoryName = rs.getString("category_name");

                    productMap.put(productId, productName);
                    productCategory.put(productId, categoryName);

                    String pName = shorten(productName, 40);
                    String cName = shorten(categoryName, 40);

                    System.out.printf("%-4d │ %-40s │ %-40s%n", productId, pName, cName);
                }

                System.out.println("─────┼──────────────────────────────────────────┼──────────────────────────────────────────────");
            }

            if (productMap.isEmpty()) {
                System.out.println(YELLOW + "No inactive products available." + RESET);
                MainDB.pause();
                return;
            }

            System.out.println(YELLOW + "Enter a product ID to request recovery." + RESET);
            System.out.println(YELLOW + "Type 'back' to cancel." + RESET);
            Integer selectedId = MainDB.readInt(sc, "Enter product ID: ");
            if (selectedId == null) return;

            if (!productMap.containsKey(selectedId)) {
                System.out.println(RED + "Product ID not found!" + RESET);
                MainDB.pause();
                return;
            }

            String updateSql = "UPDATE products SET active_status = 2 WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                ps.setInt(1, selectedId);
                ps.executeUpdate();
            }

            System.out.println(GREEN + "Product recovery request submitted! Status set to PENDING." + RESET);
            MainDB.pause();

        } catch (Exception e) {
            System.out.println(RED + "Error requesting product recovery: " + e.getMessage() + RESET);
            MainDB.pause();
        }
    }

}
