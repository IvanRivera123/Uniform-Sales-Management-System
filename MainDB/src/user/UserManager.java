package user;

import java.sql.*;
import java.util.Scanner;

public class UserManager {
    static Scanner sc = new Scanner(System.in);
    static final String RESET = "\u001B[0m";
    static final String GREEN = "\u001B[32m";
    static final String RED = "\u001B[31m";
    static final String YELLOW = "\u001B[33m";
    static final String CYAN = "\u001B[36m";

    public static Object[] login(Connection conn) {
        boolean loggedIn = false;
        int userId = 0;
        String loggedUser = "";
        String userRole = "";

        while (!loggedIn) {
            clearScreen();
            System.out.println("╔════════════════════════════════════════════════════╗");
            System.out.println("║                   ACCOUNT LOGIN                    ║");
            System.out.println("╚════════════════════════════════════════════════════╝");
            System.out.println(YELLOW + "Tip: Type 'back' to return to the Main Menu" + RESET);
            System.out.println("──────────────────────────────────────────────────────");

            System.out.print("Enter username ➤ ");
            String username = sc.nextLine().trim();
            if (username.equalsIgnoreCase("back")) return null;

            System.out.print("Enter password ➤ ");
            String password = sc.nextLine().trim();
            if (password.equalsIgnoreCase("back")) return null;

            System.out.println(CYAN + "\nVerifying credentials..." + RESET);

            String sql = "SELECT * FROM users WHERE username=? AND password=? AND active_status=1";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, username);
                ps.setString(2, password);
                ResultSet rs = ps.executeQuery();

                if (rs.next()) {
                    loggedIn = true;
                    userId = rs.getInt("id");
                    loggedUser = rs.getString("username");
                    userRole = rs.getString("role");
                    System.out.println(GREEN + "\nLogin successful! Welcome, " + loggedUser + RESET);
                } else {
                    System.out.println(RED + "\nInvalid username or password!" + RESET);
                }
                pause();
            } catch (SQLException e) {
                System.out.println(RED + "Database error: " + e.getMessage() + RESET);
                pause();
            }
        }

        return new Object[] { userId, loggedUser, userRole, true };
    }

    public static void userMenu(Connection conn, int loggedUserId, String loggedUserRole) {
        String input = "";
        do {
            clearScreen();
            System.out.println("╔═════════════════════════════════════════════════════════════╗");
            System.out.println("║                   USER ACCOUNT MANAGEMENT                   ║");
            System.out.println("╚═════════════════════════════════════════════════════════════╝");
            System.out.println("[1] View Users");
            System.out.println("[2] Add User");
            System.out.println("[3] Edit User");
            System.out.println("[4] Delete User");
            System.out.println("");
            System.out.println("[X] Back");
            System.out.print("\nEnter choice ➤ ");

            input = sc.nextLine().trim().toUpperCase();

            switch (input) {
                case "2" -> addUser(conn);
                case "1" -> viewUsers(conn);
                case "3" -> {
                    if (loggedUserRole.equals("ADMIN")) {
                        editUser(conn, loggedUserId); 
                    } else {
                        System.out.println(RED + "Access denied! Only ADMIN can edit users." + RESET);
                        pause();
                    }
                }
                case "4" -> {
                    if (loggedUserRole.equals("ADMIN")) {
                        deleteUser(conn, loggedUserId); 
                    } else {
                        System.out.println(RED + "Access denied! Only ADMIN can delete users." + RESET);
                        pause();
                    }
                }
                case "X" -> {} // exit menu
                default -> {
                    System.out.println(RED + "Invalid choice!" + RESET);
                    pause();
                }
            }
        } while (!input.equalsIgnoreCase("X"));
    }

    // ADD USER
    static void addUser(Connection conn) {
        try {
            clearScreen();
            System.out.println("╔════════════════════════════════════════════════════╗");
            System.out.println("║                     ADD USER                       ║");
            System.out.println("╚════════════════════════════════════════════════════╝");
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
                System.out.println("4. USER");
                System.out.print("Enter choice (1-4): ");
                String input = sc.nextLine().trim();
                if (input.equalsIgnoreCase("back")) return;

                switch (input) {
                    case "1" -> { role = "ADMIN"; break; }
                    case "2" -> { role = "PRODUCTMANAGER"; break; }
                    case "3" -> { role = "SALESMANAGER"; break; }
                    case "4" -> { role = "USER"; break; }
                    default -> {
                        System.out.println(RED + "Invalid choice! Enter 1, 2, 3, or 4." + RESET);
                        continue;
                    }
                }
                break;
            }

            String sql = "INSERT INTO users (username, password, role, active_status) VALUES (?, ?, ?, 1)";
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
            System.out.println("╔═══════════════════════════════════════════════════════════════════════╗");
            System.out.println("║                               USER LIST                               ║");
            System.out.println("╚═══════════════════════════════════════════════════════════════════════╝");

            String sql = "SELECT id, username, role, created_at FROM users WHERE active_status=1";
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
                
                
                System.out.printf("%-5s│ %-20s│ %-15s│ %-25s%n", "ID", "Username", "Role", "Created At");
                System.out.println("─────┼─────────────────────┼────────────────┼────────────────────────────");

                boolean hasUsers = false;
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMMM dd, yyyy hh:mm a");
                while (rs.next()) {
                    hasUsers = true;
                    int id = rs.getInt("id");
                    String username = rs.getString("username");
                    if (username.length() > 20) username = username.substring(0, 17) + "..."; 
                    String role = rs.getString("role");
                    Timestamp createdAt = rs.getTimestamp("created_at");
                    String formatted = (createdAt != null) ? sdf.format(createdAt) : "N/A";

                    System.out.printf("%-5d│ %-20s│ %-15s│ %-25s%n", id, username, role, formatted);
                }

                if (!hasUsers) {
                    System.out.println(YELLOW + "No users found." + RESET);
                }

                System.out.println("─────────────────────────────────────────────────────────────────────────");
            }

        } catch (SQLException e) {
            System.out.println(RED + "Error loading users: " + e.getMessage() + RESET);
        }
        pause();
    }


 // EDIT USER
    static void editUser(Connection conn, int adminId) {
        try {
            clearScreen();
            System.out.println("╔════════════════════════════════════════════════════╗");
            System.out.println("║                     EDIT USER                      ║");
            System.out.println("╚════════════════════════════════════════════════════╝");
            System.out.println(YELLOW + "Type 'back' at any time to go back.\n" + RESET);

            // Only show active users
            String sqlList = "SELECT id, username, role FROM users WHERE active_status=1";
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sqlList)) {
              	System.out.println("───────────────────────────────────────────────────────");
            	System.out.printf("%-5s %-15s %-15s%n", "ID", "Username", "Role");
            	System.out.println("───────────────────────────────────────────────────────");
                while (rs.next()) {
                    System.out.printf("%-5d %-15s %-15s%n", rs.getInt("id"), rs.getString("username"), rs.getString("role"));
                }
            }

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

            String checkSql = "SELECT username, password, role FROM users WHERE id=? AND active_status=1";
            try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
                ps.setInt(1, id);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    String currentUsername = rs.getString("username");
                    String currentPassword = rs.getString("password");
                    String currentRole = rs.getString("role");

                    // ADMIN verification
                    System.out.print("Enter YOUR ADMIN password to continue: ");
                    String adminPass = sc.nextLine().trim();
                    String adminVerifySql = "SELECT id FROM users WHERE id=? AND password=? AND role='ADMIN' AND active_status=1";
                    try (PreparedStatement adminPs = conn.prepareStatement(adminVerifySql)) {
                        adminPs.setInt(1, adminId);
                        adminPs.setString(2, adminPass);
                        ResultSet adminRs = adminPs.executeQuery();
                        if (!adminRs.next()) {
                            System.out.println(RED + "Incorrect admin password! Access denied." + RESET);
                            pause();
                            return;
                        }
                    }

                    // EDIT FIELDS
                    System.out.print("New username (leave blank to keep '" + currentUsername + "'): ");
                    String newUsername = sc.nextLine().trim();
                    if (newUsername.isEmpty()) newUsername = currentUsername;

                    System.out.print("New password (leave blank to keep current): ");
                    String newPassword = sc.nextLine().trim();
                    if (newPassword.isEmpty()) newPassword = currentPassword;

                    System.out.print("New role (leave blank to keep '" + currentRole + "'): ");
                    String newRole = sc.nextLine().trim().toUpperCase();
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

    // DELETE USER (soft delete)
    static void deleteUser(Connection conn, int adminId) {
        try {
            clearScreen();
            System.out.println("╔═════════════════════════════════════════════════════╗");
            System.out.println("║                     DELETE USER                     ║");
            System.out.println("╚═════════════════════════════════════════════════════╝");
            System.out.println(YELLOW + "Type 'back' at any time to go back.\n" + RESET);

            // Only show active users
            String sqlList = "SELECT id, username FROM users WHERE active_status=1";
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sqlList)) {
            	System.out.println("───────────────────────────────────────────────────────");
            	System.out.printf("%-5s %-15s%n", "ID", "Username");
            	System.out.println("───────────────────────────────────────────────────────");
                while (rs.next()) {
                    System.out.printf("%-5d %-15s%n", rs.getInt("id"), rs.getString("username"));
                }
            }

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

            String checkSql = "SELECT username FROM users WHERE id=? AND active_status=1";
            try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
                ps.setInt(1, id);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    String username = rs.getString("username");

                    // ADMIN verification
                    System.out.print("Enter YOUR ADMIN password to confirm deletion: ");
                    String adminPass = sc.nextLine().trim();
                    String adminVerifySql = "SELECT id FROM users WHERE id=? AND password=? AND role='ADMIN' AND active_status=1";
                    try (PreparedStatement adminPs = conn.prepareStatement(adminVerifySql)) {
                        adminPs.setInt(1, adminId);
                        adminPs.setString(2, adminPass);
                        ResultSet adminRs = adminPs.executeQuery();
                        if (!adminRs.next()) {
                            System.out.println(RED + "Incorrect admin password! Deletion cancelled." + RESET);
                            pause();
                            return;
                        }
                    }

                    // CONFIRM DELETE
                    System.out.print(YELLOW + "Are you sure you want to delete user '" + username + "'? (yes/no): " + RESET);
                    String confirm = sc.nextLine().trim();
                    if (!confirm.equalsIgnoreCase("yes")) {
                        System.out.println(YELLOW + "Deletion cancelled." + RESET);
                        pause();
                        return;
                    }

                    String softDeleteSql = "UPDATE users SET active_status=0 WHERE id=?";
                    try (PreparedStatement deletePs = conn.prepareStatement(softDeleteSql)) {
                        deletePs.setInt(1, id);
                        deletePs.executeUpdate();
                        System.out.println(GREEN + "User deleted successfully (soft delete)!" + RESET);
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


    static void clearScreen() {
        for (int i = 0; i < 50; i++) System.out.println();
    }

    static void pause() {
        System.out.print("Press Enter to continue...");
        sc.nextLine();
    }
}
