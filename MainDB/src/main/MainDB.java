package main;

import java.sql.*;
import java.util.Scanner;
import user.UserManager;

public class MainDB {
    static final String URL = "jdbc:mysql://localhost:3306/usms_db";
    static final String USER = "root";
    static final String PASS = "";

    static final String RESET = "\u001B[0m";
    static final String GREEN = "\u001B[32m";
    static final String RED = "\u001B[31m";
    static final String ORANGE = "\u001B[38;5;208m";

    static Scanner sc = new Scanner(System.in);

    public static void main(String[] args) {
        try (Connection conn = DriverManager.getConnection(URL, USER, PASS)) {
            System.out.println(GREEN + "Connected to usms_db database!" + RESET);

            boolean running = true;
            while (running) {
                clearScreen();
                System.out.println("╔══════════════════════════════════════════════════════════╗");
                System.out.println("║               UNIFORM SALES & MANAGEMENT SYSTEM          ║");
                System.out.println("╚══════════════════════════════════════════════════════════╝");
                System.out.println("                  Developed by: Team STI Student");
                System.out.println("");
                System.out.println("╭──────────────────────── Options ─────────────────────────╮");
                System.out.println("│ [1] View All Products          [3] Exit Application      │");
                System.out.println("│ [2] Login to Your Account                                │");
                System.out.println("╰──────────────────────────────────────────────────────────╯");
                System.out.print("Enter your choice ➤ ");

                int choice = readInt();
                if (choice == -1) continue;
                switch (choice) {
                    case 1 -> ProductManager.viewAllProducts(conn, sc, 0, false);
                    case 2 -> loginFlow(conn);
                    case 3 -> {
                        System.out.println(ORANGE + "Exiting... Goodbye!" + RESET);
                        pause();
                        running = false;
                    }
                    default -> {
                        System.out.println(RED + "Invalid choice!" + RESET);
                        pause();
                    }
                }
            }

        } catch (SQLException e) {
            System.out.println(RED + "Database connection failed: " + e.getMessage() + RESET);
        }
    }

    private static void loginFlow(Connection conn) {
        Object[] userInfo = UserManager.login(conn);
        if (userInfo == null) {
            System.out.println(ORANGE + "Returning to Main Menu..." + RESET);
            pause();
            return;
        }

        int userId = (int) userInfo[0];
        String loggedUser = (String) userInfo[1];
        String userRole = ((String) userInfo[2]).toUpperCase();
        boolean loggedIn = (boolean) userInfo[3];

        boolean inside = true;
        while (inside) {
        	clearScreen();
        	System.out.println("╔══════════════════════════════════════════════════════════════════╗");

        	String header = "Logged in as: " + loggedUser + " (" + userRole + ")";
        	int totalWidth = 66; 
        	int padding = (totalWidth - header.length()) / 2;
        	if (padding < 0) padding = 0; 
        	String line = "║" + " ".repeat(padding) + header + " ".repeat(totalWidth - header.length() - padding) + "║";

        	System.out.println(line);
        	System.out.println("╚══════════════════════════════════════════════════════════════════╝");

            switch (userRole) {
            case "USER" -> {
                String input = "";
                do {
                    MainDB.clearScreen();
                    System.out.println("╔══════════════════════════════════════════════════════════╗");
                    System.out.println("║                       USER MENU                          ║");
                    System.out.println("╚══════════════════════════════════════════════════════════╝");

                    System.out.println("╭──────────────────────── Options ─────────────────────────╮");
                    System.out.println("│ [1] View Products                [X] Logout              │");
                    System.out.println("│ [2] View Cart                                            │");
                    System.out.println("│ [3] Submit Quotation Request                             │");
                    System.out.println("╰──────────────────────────────────────────────────────────╯");
                    System.out.print("Enter your choice ➤ ");

                    input = sc.nextLine().trim().toUpperCase();

                    switch (input) {
                        case "1" -> ProductManager.viewAllProducts(conn, sc, userId, loggedIn);
                        case "2" -> CartManager.viewCart(conn, sc, userId);
                        case "3" -> CartManager.submitQuotation(conn, sc, userId, loggedIn);
                        case "X" -> inside = false;
                        default -> {
                            System.out.println(RED + "Invalid choice!" + RESET);
                            MainDB.pause();
                        }
                    }
                } while (!input.equals("X"));
            }
                case "PRODUCTMANAGER" -> {
                    ProductManager.manageProducts(conn, sc, loggedUser, userRole);
                    inside = false;
                }
                case "SALESMANAGER" -> {
                    SalesManager.salesMenu(conn, sc, loggedUser, userRole, userId);
                    inside = false;
                }
                case "ADMIN" -> {
                	AdminManager.manageUsers(conn, sc, userId);
                    inside = false;
                }
                default -> {
                    System.out.println(RED + "Unknown role! Logging out..." + RESET);
                    pause();
                    inside = false;
                }
            }
        }
    }

    // Restored old clearScreen function
    public static void clearScreen() {
        for (int i = 0; i < 50; i++) {
            System.out.println();
        }
    }

    public static void pause() {
        System.out.println("Press Enter to continue...");
        sc.nextLine();
    }

    private static int readInt() {
        try {
            return Integer.parseInt(sc.nextLine().trim());
        } catch (NumberFormatException e) {
            System.out.println(RED + "Invalid input! Please enter a number." + RESET);
            pause();
            return -1;
        }
    }
}
