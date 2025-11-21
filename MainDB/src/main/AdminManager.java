package main;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public class AdminManager {

    static final String RESET = "\u001B[0m";
    static final String GREEN = "\u001B[32m";
    static final String RED = "\u001B[31m";
    static final String YELLOW = "\u001B[33m";

    // ================================
    // ADMIN MENU
    // ================================
    public static void manageUsers(Connection conn, Scanner sc, int adminId, String adminUsername) {
        while (true) {
            MainDB.clearScreen();
            System.out.println("╔══════════════════════════════════════════════════════════╗");
            System.out.println("║                           ADMIN MENU                     ║");
            System.out.println("╚══════════════════════════════════════════════════════════╝");

            System.out.println("╭──────────────────────── Options ─────────────────────────╮");
            System.out.println("│ [1] Manage User Accounts                                 │");
            System.out.println("│ [2] View as Product Manager                              │");
            System.out.println("│ [3] View as Sales Manager                                │");
            System.out.println("│ [4] View Pending Recovery Requests                       │");
            System.out.println("│ [X] Back                                                 │");
            System.out.println("╰──────────────────────────────────────────────────────────╯");
            System.out.print("Enter choice ➤ ");

            String input = sc.nextLine().trim().toUpperCase();

            switch (input) {
                case "1" -> {
                    boolean inSubMenu = true;
                    while (inSubMenu) {
                        MainDB.clearScreen();
                        System.out.println("╔══════════════════════════════════════════════════════════╗");
                        System.out.println("║                USER ACCOUNT MANAGER                      ║");
                        System.out.println("╚══════════════════════════════════════════════════════════╝");

                        System.out.println("╭───────────────────── Options ────────────────────────────╮");
                        System.out.println("│ [1] View Users                [4] Deactivate User        │");
                        System.out.println("│ [2] Add User                  [5] Recover User           │");
                        System.out.println("│ [3] Edit User                 [X] Back                   │");
                        System.out.println("╰──────────────────────────────────────────────────────────╯");
                        System.out.print("Enter choice ➤ ");

                        String sub = sc.nextLine().trim().toUpperCase();

                        switch (sub) {
                            case "1" -> viewUsers(conn);
                            case "2" -> addUser(conn, sc);
                            case "3" -> editUser(conn, sc);
                            case "4" -> deactivateUser(conn, sc, adminId);
                            case "5" -> recoverUser(conn, sc);
                            case "X" -> inSubMenu = false;
                            default -> {
                                System.out.println(RED + "Invalid choice!" + RESET);
                                MainDB.pause();
                            }
                        }
                    }
                }

                case "2" -> ProductManager.manageProducts(conn, sc, adminUsername, "ADMIN");

                case "3" -> SalesManager.salesMenu(conn, sc, adminUsername, "ADMIN", adminId);

                case "4" -> viewPendingRecoveries(conn, sc);

                case "X" -> { return; }

                default -> {
                    System.out.println(RED + "Invalid choice!" + RESET);
                    MainDB.pause();
                }
            }
        }
    }

    // =======================================
    // VIEW & APPROVE PENDING PRODUCT/CATEGORY RECOVERIES
    // =======================================
    private static void viewPendingRecoveries(Connection conn, Scanner sc) {
        while (true) {
            MainDB.clearScreen();
            System.out.println("╔══════════════════════════════════════════════════════════╗");
            System.out.println("║               PENDING RECOVERY REQUESTS                  ║");
            System.out.println("╚══════════════════════════════════════════════════════════╝");

            System.out.println("╭───────────────────── Options ────────────────────────────╮");
            System.out.println("│ [1] View & Approve Product Recovery                      │");
            System.out.println("│ [2] View & Approve Category Recovery                     │");
            System.out.println("│ [X] Back                                                 │");
            System.out.println("╰──────────────────────────────────────────────────────────╯");
            System.out.print("Enter choice ➤ ");

            String choice = sc.nextLine().trim().toUpperCase();

            switch (choice) {
                case "1" -> requestProductRecoveryAdmin(conn, sc);
                case "2" -> requestCategoryRecoveryAdmin(conn, sc);
                case "X" -> { return; }
                default -> {
                    System.out.println(RED + "Invalid choice!" + RESET);
                    MainDB.pause();
                }
            }
        }
    }

    // ADMIN APPROVE PRODUCT RECOVERY
    private static void requestProductRecoveryAdmin(Connection conn, Scanner sc) {
        try {
            MainDB.clearScreen();
            String sql = """
                SELECT p.id AS product_id, p.name AS product_name, c.name AS category_name
                FROM products p
                JOIN categories c ON p.category_id = c.id
                WHERE p.active_status = 2
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

                    System.out.printf("%-4d │ %-40s │ %-40s%n", productId, productName, categoryName);
                }
                System.out.println("─────┼──────────────────────────────────────────┼──────────────────────────────────────────────");
            }

            if (productMap.isEmpty()) {
                System.out.println(YELLOW + "No pending product recoveries." + RESET);
                MainDB.pause();
                return;
            }

            System.out.println(YELLOW + "Enter Product ID to approve recovery." + RESET);
            System.out.println(YELLOW + "Type 'back' to cancel." + RESET);
            System.out.print("Enter ID: ");
            String input = sc.nextLine().trim();
            if (input.equalsIgnoreCase("back")) return;

            int selectedId = Integer.parseInt(input);
            if (!productMap.containsKey(selectedId)) {
                System.out.println(RED + "Product ID not found!" + RESET);
                MainDB.pause();
                return;
            }

            String updateSql = "UPDATE products SET active_status = 1 WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                ps.setInt(1, selectedId);
                ps.executeUpdate();
            }

            System.out.println(GREEN + "Product recovery approved!" + RESET);
            MainDB.pause();
        } catch (Exception e) {
            System.out.println(RED + "Error approving product recovery: " + e.getMessage() + RESET);
            MainDB.pause();
        }
    }

    // ADMIN APPROVE CATEGORY RECOVERY
    private static void requestCategoryRecoveryAdmin(Connection conn, Scanner sc) {
        try {
            MainDB.clearScreen();
            String sql = "SELECT id, name FROM categories WHERE active_status = 2 ORDER BY id";
            List<Integer> ids = new ArrayList<>();
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
                System.out.println("ID   │ Category Name");
                System.out.println("─────┼──────────────────────────────────────────────────────────");
                while (rs.next()) {
                    ids.add(rs.getInt("id"));
                    System.out.printf("%-4d │ %s%n", rs.getInt("id"), rs.getString("name"));
                }
                System.out.println("─────┼──────────────────────────────────────────────────────────");
            }

            if (ids.isEmpty()) {
                System.out.println(YELLOW + "No pending category recoveries." + RESET);
                MainDB.pause();
                return;
            }

            System.out.println(YELLOW + "Enter Category ID to approve recovery." + RESET);
            System.out.println(YELLOW + "Type 'back' to cancel." + RESET);
            System.out.print("Enter ID: ");
            String input = sc.nextLine().trim();
            if (input.equalsIgnoreCase("back")) return;

            int selectedId = Integer.parseInt(input);
            if (!ids.contains(selectedId)) {
                System.out.println(RED + "Category ID not found!" + RESET);
                MainDB.pause();
                return;
            }

            String updateSql = "UPDATE categories SET active_status = 1 WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                ps.setInt(1, selectedId);
                ps.executeUpdate();
            }

            System.out.println(GREEN + "Category recovery approved!" + RESET);
            MainDB.pause();
        } catch (Exception e) {
            System.out.println(RED + "Error approving category recovery: " + e.getMessage() + RESET);
            MainDB.pause();
        }
    }


    // ================================
    // OVERLOADED 3-PARAMETER METHOD
    // ================================
    public static void manageUsers(Connection conn, Scanner sc, int adminId) {
        String adminUsername = "";
        try {
            String sql = "SELECT username FROM users WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, adminId);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    adminUsername = rs.getString("username");
                } else {
                    System.out.println("\u001B[31mAdmin not found!\u001B[0m");
                    MainDB.pause();
                    return;
                }
            }
        } catch (Exception e) {
            System.out.println("\u001B[31mError fetching admin username: " + e.getMessage() + "\u001B[0m");
            MainDB.pause();
            return;
        }

        // Call the main 4-parameter method
        manageUsers(conn, sc, adminId, adminUsername);
    }

    // ================================
    // DISPLAY USERS (NO PAUSE)
    // ================================
    public static void displayUsers(Connection conn) {
        try {
            String sql = """
                SELECT id, username, role, active_status
                FROM users
                ORDER BY id
            """;

            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(sql)) {

                System.out.printf("%-5s │ %-20s │ %-15s │ %-10s%n",
                        "ID", "Username", "Role", "Status");
                System.out.println("──────┼──────────────────────┼─────────────────┼──────────────");

                while (rs.next()) {
                    String status = switch (rs.getInt("active_status")) {
                        case 1 -> "ACTIVE";
                        case 0 -> "DELETED";
                        case 2 -> "PENDING";
                        default -> "UNKNOWN";
                    };

                    System.out.printf("%-5d │ %-20s │ %-15s │ %-10s%n",
                            rs.getInt("id"),
                            rs.getString("username"),
                            rs.getString("role").toUpperCase(),
                            status
                    );
                }

                System.out.println("──────┼──────────────────────┼─────────────────┼──────────────");
            }
        } catch (Exception e) {
            System.out.println(RED + "Error loading users: " + e.getMessage() + RESET);
        }
    }

    // ================================
    // VIEW USERS
    // ================================
    public static void viewUsers(Connection conn) {
        MainDB.clearScreen();
        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.println("║                         USER LIST                        ║");
        System.out.println("╚══════════════════════════════════════════════════════════╝");

        displayUsers(conn);
        MainDB.pause();
    }

    // ================================
    // ADD USER
    // ================================
    public static void addUser(Connection conn, Scanner sc) {
        try {
            MainDB.clearScreen();
            System.out.println("╔══════════════════════════════════════════════════════════╗");
            System.out.println("║                         ADD USER                         ║");
            System.out.println("╚══════════════════════════════════════════════════════════╝");
            System.out.println(YELLOW + "Type 'back' at any time to go back.\n" + RESET);

            String username;
            while (true) {
                System.out.print("Username: ");
                username = sc.nextLine().trim();
                if (username.equalsIgnoreCase("back")) return;
                if (username.isEmpty()) {
                    System.out.println(RED + "Username cannot be empty!" + RESET);
                    continue;
                }
                break;
            }

            String password;
            while (true) {
                System.out.print("Password: ");
                password = sc.nextLine().trim();
                if (password.equalsIgnoreCase("back")) return;
                if (password.isEmpty()) {
                    System.out.println(RED + "Password cannot be empty!" + RESET);
                    continue;
                }
                break;
            }

            String role;
            while (true) {
                System.out.print("Role (USER / PRODUCTMANAGER / SALESMANAGER / ADMIN): ");
                role = sc.nextLine().trim().toUpperCase();
                if (role.equalsIgnoreCase("back")) return;
                if (!role.matches("USER|PRODUCTMANAGER|SALESMANAGER|ADMIN")) {
                    System.out.println(RED + "Invalid role!" + RESET);
                    continue;
                }
                break;
            }

            String sql = "INSERT INTO users (username, password, role, active_status) VALUES (?, ?, ?, 1)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, username);
                ps.setString(2, password);
                ps.setString(3, role);
                ps.executeUpdate();
            }

            System.out.println(GREEN + "User added successfully!" + RESET);
            MainDB.pause();

        } catch (Exception e) {
            System.out.println(RED + "Error adding user: " + e.getMessage() + RESET);
            MainDB.pause();
        }
    }

    // ================================
    // EDIT USER
    // ================================
    public static void editUser(Connection conn, Scanner sc) {
        try {
            MainDB.clearScreen();
            System.out.println("╔══════════════════════════════════════════════════════════╗");
            System.out.println("║                         EDIT USER                        ║");
            System.out.println("╚══════════════════════════════════════════════════════════╝");

            displayUsers(conn);

            System.out.println(YELLOW + "Type 'back' to cancel." + RESET);
            System.out.print("\nEnter user ID to edit: ");
            String idInput = sc.nextLine().trim();
            if (idInput.equalsIgnoreCase("back")) return;

            int id = Integer.parseInt(idInput);

            System.out.print("New username (leave blank to keep current): ");
            String newUsername = sc.nextLine().trim();

            System.out.print("New password (leave blank to keep current): ");
            String newPassword = sc.nextLine().trim();

            System.out.print("New role (USER / PRODUCTMANAGER / SALESMANAGER / ADMIN) or leave blank: ");
            String newRole = sc.nextLine().trim().toUpperCase();

            String sql = """
                UPDATE users
                SET username = IF(? = '', username, ?),
                    password = IF(? = '', password, ?),
                    role = IF(? = '', role, ?)
                WHERE id = ?
            """;

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, newUsername);
                ps.setString(2, newUsername);

                ps.setString(3, newPassword);
                ps.setString(4, newPassword);

                ps.setString(5, newRole);
                ps.setString(6, newRole);

                ps.setInt(7, id);

                ps.executeUpdate();
            }

            System.out.println(GREEN + "User updated!" + RESET);
            MainDB.pause();

        } catch (Exception e) {
            System.out.println(RED + "Error editing user: " + e.getMessage() + RESET);
            MainDB.pause();
        }
    }

    // ================================
    // DELETE USER (SET inactive)
    // ================================
    public static void deactivateUser(Connection conn, Scanner sc, int adminId) {
        try {
            MainDB.clearScreen();
            System.out.println("╔══════════════════════════════════════════════════════════╗");
            System.out.println("║                      DEACTIVATE USER                     ║");
            System.out.println("╚══════════════════════════════════════════════════════════╝");

            displayUsers(conn);

            System.out.println(YELLOW + "Type 'back' to cancel." + RESET);
            System.out.print("\nEnter user ID to deactivate: ");
            String idInput = sc.nextLine().trim();
            if (idInput.equalsIgnoreCase("back")) return;

            int id = Integer.parseInt(idInput);

            // Prevent self-deactivation
            if (id == adminId) {
                System.out.println(RED + "You cannot deactivate your own account!" + RESET);
                MainDB.pause();
                return;
            }

            // Confirmation
            System.out.print(YELLOW + "Are you sure you want to deactivate this user? (yes/no): " + RESET);
            String confirm = sc.nextLine().trim().toLowerCase();
            if (!confirm.equals("yes")) {
                System.out.println(YELLOW + "Deactivation cancelled." + RESET);
                MainDB.pause();
                return;
            }

            // Password verification of the logged-in admin
            System.out.print("Enter your admin password to continue: ");
            String adminPass = sc.nextLine().trim();

            String checkAdmin = "SELECT password FROM users WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(checkAdmin)) {
                ps.setInt(1, adminId);
                ResultSet rs = ps.executeQuery();

                if (!rs.next()) {
                    System.out.println(RED + "Admin verification failed!" + RESET);
                    MainDB.pause();
                    return;
                }

                String correctPass = rs.getString("password");

                if (!correctPass.equals(adminPass)) {
                    System.out.println(RED + "Incorrect password. Action denied!" + RESET);
                    MainDB.pause();
                    return;
                }
            }

            // FINAL ACTION: Deactivate user
            String sql = "UPDATE users SET active_status = 0 WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, id);
                ps.executeUpdate();
            }

            System.out.println(GREEN + "User successfully deactivated!" + RESET);
            MainDB.pause();

        } catch (Exception e) {
            System.out.println(RED + "Error deactivating user: " + e.getMessage() + RESET);
            MainDB.pause();
        }
    }

    // ================================
    // RECOVER USER
    // ================================
    public static void recoverUser(Connection conn, Scanner sc) {
        try {
            MainDB.clearScreen();
            System.out.println("╔══════════════════════════════════════════════════════════╗");
            System.out.println("║                       RECOVER USER                       ║");
            System.out.println("╚══════════════════════════════════════════════════════════╝");

            String sql = """
                SELECT id, username, role
                FROM users
                WHERE active_status = 0
            """;

            List<Integer> ids = new ArrayList<>();

            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(sql)) {

                System.out.printf("%-5s │ %-20s │ %-15s%n", "ID", "Username", "Role");
                System.out.println("──────┼──────────────────────┼──────────────────");

                while (rs.next()) {
                    System.out.printf("%-5d │ %-20s │ %-15s%n",
                            rs.getInt("id"),
                            rs.getString("username"),
                            rs.getString("role"));
                    ids.add(rs.getInt("id"));
                }
            }

            if (ids.isEmpty()) {
                System.out.println(YELLOW + "No users to recover." + RESET);
                MainDB.pause();
                return;
            }

            System.out.println(YELLOW + "Type 'back' to cancel." + RESET);
            System.out.print("\nEnter user ID to recover: ");
            String input = sc.nextLine().trim();
            if (input.equalsIgnoreCase("back")) return;

            int id = Integer.parseInt(input);

            if (!ids.contains(id)) {
                System.out.println(RED + "User ID not found!" + RESET);
                MainDB.pause();
                return;
            }

            String updateSql = "UPDATE users SET active_status = 1 WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                ps.setInt(1, id);
                ps.executeUpdate();
            }

            System.out.println(GREEN + "User recovered!" + RESET);
            MainDB.pause();

        } catch (Exception e) {
            System.out.println(RED + "Error recovering user: " + e.getMessage() + RESET);
            MainDB.pause();
        }
    }
}
