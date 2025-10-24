package user;

import java.sql.*;
import java.util.Scanner;

public class UserManager {
    static Scanner sc = new Scanner(System.in);
    static final String RESET = "\u001B[0m";
    static final String GREEN = "\u001B[32m";
    static final String RED = "\u001B[31m";
    static final String YELLOW = "\u001B[33m";

    public static Object[] login(Connection conn) {
        boolean loggedIn = false;
        int userId = 0;
        String loggedUser = "";
        String userRole = "";

        while (!loggedIn) {
            clearScreen();
            System.out.println("USER LOGIN");
            System.out.print("Username: ");
            String username = sc.nextLine().trim();
            System.out.print("Password: ");
            String password = sc.nextLine().trim();

            String sql = "SELECT * FROM users WHERE username=? AND password=?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, username);
                ps.setString(2, password);
                ResultSet rs = ps.executeQuery();

                if (rs.next()) {
                    loggedIn = true;
                    userId = rs.getInt("id");
                    loggedUser = rs.getString("username");
                    userRole = rs.getString("role");
                    System.out.println("Login successful! Welcome, " + loggedUser);
                } else {
                    System.out.println("Invalid username or password!");
                }
                pause();
            } catch (SQLException e) {
                System.out.println("Database error: " + e.getMessage());
                pause();
            }
        }

        return new Object[] { userId, loggedUser, userRole, loggedIn };
    }

    // ADMIN / MANAGER USER MENU
    public static void userMenu(Connection conn) {
        int choice = 0;
        do {
            clearScreen();
            System.out.println("=================================================================");
            System.out.println("                     USER ACCOUNT MANAGEMENT");
            System.out.println("=================================================================");
            System.out.println("1. Add User");
            System.out.println("2. View Users");
            System.out.println("3. Edit User");
            System.out.println("4. Delete User");
            System.out.println("5. Back");
            System.out.print("\nEnter choice: ");

            try {
                choice = Integer.parseInt(sc.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println(RED + "Invalid input! Enter a number." + RESET);
                pause();
                continue;
            }

            switch (choice) {
                case 1 -> addUser(conn);
                case 2 -> viewUsers(conn);
                case 3 -> editUser(conn);
                case 4 -> deleteUser(conn);
                case 5 -> {}
                default -> {
                    System.out.println(RED + "Invalid choice!" + RESET);
                    pause();
                }
            }
        } while (choice != 5);
    }

    // REGISTER A PUBLIC USER OR MANAGER
    public static void register(Connection conn, String role) {
        try {
            clearScreen();
            System.out.println("=================================================================");
            System.out.println("                           REGISTER " + role.toUpperCase());
            System.out.println("=================================================================");

            System.out.print("Username: ");
            String username = sc.nextLine().trim();
            System.out.print("Password: ");
            String password = sc.nextLine().trim();

            if (username.isEmpty() || password.isEmpty()) {
                System.out.println(RED + "All fields are required!" + RESET);
                pause();
                return;
            }

            String sql = "INSERT INTO users(username, password, role) VALUES(?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, username);
                ps.setString(2, password);
                ps.setString(3, role.toUpperCase());
                ps.executeUpdate();
                System.out.println(GREEN + role + " registered successfully!" + RESET);
            }
        } catch (SQLIntegrityConstraintViolationException e) {
            System.out.println(RED + "Username already exists!" + RESET);
        } catch (SQLException e) {
            System.out.println(RED + "Error registering user: " + e.getMessage() + RESET);
        }
        pause();
    }


    // ADD USER
    static void addUser(Connection conn) {
        try {
            clearScreen();
            System.out.println("=================================================================");
            System.out.println("                            ADD USER");
            System.out.println("=================================================================");
            System.out.println(YELLOW + "Type 'back' at any time to go back.\n" + RESET);

            System.out.print("Username: ");
            String username = sc.nextLine().trim();
            if (username.equalsIgnoreCase("back")) return;

            System.out.print("Password: ");
            String password = sc.nextLine().trim();
            if (password.equalsIgnoreCase("back")) return;

            String role = "";
            while (true) {
                System.out.println("\nSelect Role:");
                System.out.println("1. ADMIN");
                System.out.println("2. PRODUCTMANAGER");
                System.out.println("3. SALESMANAGER");
                System.out.print("Enter choice (1-3): ");
                String input = sc.nextLine().trim();
                if (input.equalsIgnoreCase("back")) return;

                switch (input) {
                    case "1" -> { role = "ADMIN"; break; }
                    case "2" -> { role = "PRODUCTMANAGER"; break; }
                    case "3" -> { role = "SALESMANAGER"; break; }
                    default -> {
                        System.out.println(RED + "Invalid choice! Enter 1, 2, or 3." + RESET);
                        continue;
                    }
                }
                break;
            }

            String sql = "INSERT INTO users (username, password, role) VALUES (?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, username);
                ps.setString(2, password);
                ps.setString(3, role);
                ps.executeUpdate();
                System.out.println(GREEN + "User created successfully!" + RESET);
            }
        } catch (SQLIntegrityConstraintViolationException e) {
            System.out.println(RED + "Username already exists!" + RESET);
        } catch (SQLException e) {
            System.out.println(RED + "Error adding user: " + e.getMessage() + RESET);
        }
        pause();
    }

    // VIEW USERS
    static void viewUsers(Connection conn) {
        try {
            clearScreen();
            System.out.println("=================================================================");
            System.out.println("                           USER LIST");
            System.out.println("=================================================================");
            String sql = "SELECT id, username, role, created_at FROM users";
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
                System.out.printf("%-5s %-15s %-10s %-25s%n", "ID", "Username", "Role", "Created At");
                System.out.println("---------------------------------------------------------------");

                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMMM dd, yyyy hh:mm a");
                while (rs.next()) {
                    int id = rs.getInt("id");
                    String username = rs.getString("username");
                    String role = rs.getString("role");
                    Timestamp createdAt = rs.getTimestamp("created_at");
                    String formatted = (createdAt != null) ? sdf.format(createdAt) : "N/A";

                    System.out.printf("%-5d %-15s %-10s %-25s%n", id, username, role, formatted);
                }
            }
        } catch (SQLException e) {
            System.out.println(RED + "Error loading users: " + e.getMessage() + RESET);
        }
        pause();
    }

    // EDIT USER
    static void editUser(Connection conn) {
        try {
            clearScreen();
            System.out.println("=================================================================");
            System.out.println("                           EDIT USER");
            System.out.println("=================================================================");
            System.out.println(YELLOW + "Type 'back' at any time to go back.\n" + RESET);

            viewUsers(conn);

            System.out.print("\nEnter user ID to edit: ");
            String input = sc.nextLine().trim();
            if (input.equalsIgnoreCase("back")) return;

            int id;
            try {
                id = Integer.parseInt(input);
            } catch (NumberFormatException e) {
                System.out.println(RED + "Invalid ID!" + RESET);
                pause();
                return;
            }

            String checkSql = "SELECT username, password, role FROM users WHERE id=?";
            try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
                ps.setInt(1, id);
                ResultSet rs = ps.executeQuery();

                if (rs.next()) {
                    String currentUsername = rs.getString("username");
                    String currentPassword = rs.getString("password");
                    String currentRole = rs.getString("role");

                    System.out.print("Enter current password for verification: ");
                    String inputPass = sc.nextLine().trim();

                    if (!inputPass.equals(currentPassword)) {
                        System.out.println(RED + "Incorrect password! Access denied." + RESET);
                        pause();
                        return;
                    }

                    System.out.print("New username (leave blank to keep '" + currentUsername + "'): ");
                    String newUsername = sc.nextLine().trim();
                    if (newUsername.isEmpty()) newUsername = currentUsername;

                    System.out.print("New password (leave blank to keep current): ");
                    String newPassword = sc.nextLine().trim();
                    if (newPassword.isEmpty()) newPassword = currentPassword;

                    System.out.print("New role (admin/sales, leave blank to keep '" + currentRole + "'): ");
                    String newRole = sc.nextLine().trim().toLowerCase();
                    if (newRole.isEmpty()) newRole = currentRole;

                    String updateSql = "UPDATE users SET username=?, password=?, role=? WHERE id=?";
                    try (PreparedStatement updatePs = conn.prepareStatement(updateSql)) {
                        updatePs.setString(1, newUsername);
                        updatePs.setString(2, newPassword);
                        updatePs.setString(3, newRole);
                        updatePs.setInt(4, id);
                        updatePs.executeUpdate();
                        System.out.println(GREEN + "User updated successfully!" + RESET);
                    }
                } else {
                    System.out.println(RED + "User not found!" + RESET);
                }
            }
        } catch (SQLException e) {
            System.out.println(RED + "Error editing user: " + e.getMessage() + RESET);
        }
        pause();
    }

    // DELETE USER
    static void deleteUser(Connection conn) {
        try {
            clearScreen();
            System.out.println("=================================================================");
            System.out.println("                           DELETE USER");
            System.out.println("=================================================================");
            System.out.println(YELLOW + "Type 'back' at any time to go back.\n" + RESET);

            viewUsers(conn);

            System.out.print("\nEnter user ID to delete: ");
            String input = sc.nextLine().trim();
            if (input.equalsIgnoreCase("back")) return;

            int id;
            try {
                id = Integer.parseInt(input);
            } catch (NumberFormatException e) {
                System.out.println(RED + "Invalid ID!" + RESET);
                pause();
                return;
            }

            String checkSql = "SELECT username, password FROM users WHERE id=?";
            try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
                ps.setInt(1, id);
                ResultSet rs = ps.executeQuery();

                if (rs.next()) {
                    String username = rs.getString("username");
                    String password = rs.getString("password");

                    System.out.print("Enter password for '" + username + "' to confirm: ");
                    String inputPass = sc.nextLine().trim();

                    if (!inputPass.equals(password)) {
                        System.out.println(RED + "Incorrect password! Deletion cancelled." + RESET);
                        pause();
                        return;
                    }

                    System.out.print(YELLOW + "Type CONFIRM DELETE to permanently delete this user: " + RESET);
                    String confirm = sc.nextLine().trim();

                    if (!confirm.equals("CONFIRM DELETE")) {
                        System.out.println(YELLOW + "Deletion cancelled." + RESET);
                        pause();
                        return;
                    }

                    String deleteSql = "DELETE FROM users WHERE id=?";
                    try (PreparedStatement deletePs = conn.prepareStatement(deleteSql)) {
                        deletePs.setInt(1, id);
                        deletePs.executeUpdate();
                        System.out.println(GREEN + "User deleted successfully!" + RESET);
                    }
                } else {
                    System.out.println(RED + "User not found!" + RESET);
                }
            }
        } catch (SQLException e) {
            System.out.println(RED + "Error deleting user: " + e.getMessage() + RESET);
        }
        pause();
    }

    // UTILITIES
    static void clearScreen() {
        for (int i = 0; i < 50; i++) System.out.println();
    }

    static void pause() {
        System.out.print("Press Enter to continue...");
        sc.nextLine();
    }
}
