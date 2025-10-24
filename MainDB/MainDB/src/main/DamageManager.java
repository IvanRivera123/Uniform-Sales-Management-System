package main;

import java.sql.*;
import java.util.Scanner;

public class DamageManager {
    static final String RESET = "\u001B[0m";
    static final String GREEN = "\u001B[32m";
    static final String RED = "\u001B[31m";
    static final String ORANGE = "\u001B[38;5;208m";

    // MAIN MENU for Damage Manager
    public static void damageMenu(Connection conn, Scanner sc) {
        boolean running = true;

        while (running) {
            MainDB.clearScreen();
            System.out.println("==================================================");
            System.out.println("               DAMAGE MANAGEMENT MENU");
            System.out.println("==================================================");
            System.out.println("1. View All Damaged Items");
            System.out.println("2. Report Damaged Product");
            System.out.println("3. Delete Damage Record");
            System.out.println("4. Return to Main Menu");
            System.out.print("Enter choice: ");

            int choice = readInt(sc);
            switch (choice) {
                case 1 -> viewDamagedItems(conn);
                case 2 -> reportDamage(conn, sc);
                case 3 -> deleteDamageRecord(conn, sc);
                case 4 -> running = false;
                default -> {
                    System.out.println(RED + "Invalid choice!" + RESET);
                    MainDB.pause();
                }
            }
        }
    }

    // REPORT DAMAGED PRODUCT
    public static void reportDamage(Connection conn, Scanner sc) {
        try {
            System.out.print("Enter Product ID: ");
            int productId = Integer.parseInt(sc.nextLine());
            System.out.print("Enter quantity damaged: ");
            int qty = Integer.parseInt(sc.nextLine());
            System.out.print("Enter reason for damage: ");
            String reason = sc.nextLine();

            String query = "INSERT INTO damaged_products (product_id, quantity, reason, report_date) VALUES (?, ?, ?, NOW())";
            PreparedStatement pstmt = conn.prepareStatement(query);
            pstmt.setInt(1, productId);
            pstmt.setInt(2, qty);
            pstmt.setString(3, reason);
            pstmt.executeUpdate();

            System.out.println(GREEN + "Damage report successfully added!" + RESET);
        } catch (SQLException e) {
            System.out.println(RED + "Error reporting damage: " + e.getMessage() + RESET);
        } catch (NumberFormatException e) {
            System.out.println(RED + "Invalid number entered." + RESET);
        }
        MainDB.pause();
    }

    // VIEW DAMAGED ITEMS
    public static void viewDamagedItems(Connection conn) {
        try {
            String query = """
                SELECT d.id, p.product_name, d.quantity, d.reason, d.report_date
                FROM damaged_products d
                JOIN products p ON d.product_id = p.id
                ORDER BY d.report_date DESC
                """;
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(query);

            System.out.println("=======================================================================");
            System.out.println("                           DAMAGED PRODUCTS LIST");
            System.out.println("=======================================================================");
            System.out.printf("%-5s %-25s %-10s %-25s %-20s%n", "ID", "Product Name", "Qty", "Reason", "Date Reported");
            System.out.println("-----------------------------------------------------------------------");

            boolean hasData = false;
            while (rs.next()) {
                hasData = true;
                System.out.printf("%-5d %-25s %-10d %-25s %-20s%n",
                        rs.getInt("id"),
                        rs.getString("product_name"),
                        rs.getInt("quantity"),
                        rs.getString("reason"),
                        rs.getString("report_date"));
            }

            if (!hasData)
                System.out.println(ORANGE + "No damaged products recorded yet." + RESET);

        } catch (SQLException e) {
            System.out.println(RED + "Error fetching damaged products: " + e.getMessage() + RESET);
        }
        MainDB.pause();
    }

    // DELETE DAMAGE RECORD
    public static void deleteDamageRecord(Connection conn, Scanner sc) {
        try {
            System.out.print("Enter Damage Record ID to delete: ");
            int id = Integer.parseInt(sc.nextLine());

            String query = "DELETE FROM damaged_products WHERE id = ?";
            PreparedStatement pstmt = conn.prepareStatement(query);
            pstmt.setInt(1, id);
            int rows = pstmt.executeUpdate();

            if (rows > 0)
                System.out.println(GREEN + "Record deleted successfully!" + RESET);
            else
                System.out.println(ORANGE + "No record found with that ID." + RESET);

        } catch (SQLException e) {
            System.out.println(RED + "Error deleting damage record: " + e.getMessage() + RESET);
        } catch (NumberFormatException e) {
            System.out.println(RED + "Invalid ID entered." + RESET);
        }
        MainDB.pause();
    }

    private static int readInt(Scanner sc) {
        try {
            return Integer.parseInt(sc.nextLine().trim());
        } catch (NumberFormatException e) {
            System.out.println(RED + "Please enter a valid number." + RESET);
            return -1;
        }
    }
}
