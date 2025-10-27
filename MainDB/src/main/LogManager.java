package main;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class LogManager {
    static final String RESET = "\u001B[0m";
    static final String GREEN = "\u001B[32m";
    static final String RED = "\u001B[31m";
    static final String YELLOW = "\u001B[33m";
    static final String MAGENTA = "\u001B[35m";

    public static void viewInventoryLog(Connection conn) {
        try {
            MainDB.clearScreen();

            String sql = """
                SELECT l.id, 
                       COALESCE(p.name, 'Deleted Product') AS product_name, 
                       l.change_type,
                       l.quantity, 
                       l.previous_stock, 
                       l.new_stock, 
                       l.created_at
                FROM inventory_log l
                LEFT JOIN products p ON l.product_id = p.id
                ORDER BY l.created_at DESC
            """;

            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(sql)) {

                List<String[]> records = new ArrayList<>();
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMMM dd, yyyy  hh:mm a");

                while (rs.next()) {
                    int id = rs.getInt("id");
                    String product = rs.getString("product_name");
                    String type = rs.getString("change_type");
                    int qty = rs.getInt("quantity");
                    int prev = rs.getInt("previous_stock");
                    int now = rs.getInt("new_stock");
                    Timestamp createdAt = rs.getTimestamp("created_at");
                    String formattedDate = (createdAt != null) ? sdf.format(createdAt) : "N/A";

                    // Determine color
                    String color;
                    switch (type.toUpperCase()) {
                        case "SALE" -> color = RED;
                        case "RESTOCK", "ADD" -> color = GREEN;
                        case "EDIT" -> color = YELLOW;
                        case "DELETE" -> color = MAGENTA;
                        default -> color = RESET;
                    }

                    // Color-coded type & quantity
                    String coloredType = color + String.format("%-6s", type) + RESET;

                    String qtyDisplay = (type.equalsIgnoreCase("ADD") || type.equalsIgnoreCase("RESTOCK") || qty > 0)
                            ? "+" + qty : String.valueOf(qty);
                    String coloredQty = color + String.format("%4s", qtyDisplay) + RESET;

                    records.add(new String[]{
                            String.valueOf(id),
                            product,
                            coloredType,
                            coloredQty,
                            String.valueOf(prev),
                            String.valueOf(now),
                            formattedDate
                    });
                }

                if (records.isEmpty()) {
                    System.out.println(RED + "No inventory logs found." + RESET);
                    MainDB.pause();
                    return;
                }

                final String format = "%-3s │ %-30s │ %-6s │ %-4s │ %-10s │ %-10s │ %-25s%n";
                Scanner scanner = new Scanner(System.in);
                int pageSize = 10;
                int currentPage = 0;
                int totalPages = (int) Math.ceil((double) records.size() / pageSize);

                boolean running = true;
                while (running) {
                    MainDB.clearScreen();

                    // Header Box
                    System.out.println("╔══════════════════════════════════════════════════════════════════════════════════════════════════════════════════════╗");
                    System.out.println("║                                     🧾  INVENTORY LOG HISTORY                                                          ║");
                    System.out.println("╚══════════════════════════════════════════════════════════════════════════════════════════════════════════════════════╝");
                    System.out.printf(format, "ID", "Product Name", "Type", "Qty", "Prev Stock", "New Stock", "Date & Time");
                    System.out.println("────┼────────────────────────────────┼────────┼──────┼────────────┼────────────┼──────────────────────────────");

                    int start = currentPage * pageSize;
                    int end = Math.min(start + pageSize, records.size());

                    for (int i = start; i < end; i++) {
                        String[] r = records.get(i);
                        System.out.printf(format, r[0], r[1], r[2], r[3], r[4], r[5], r[6]);
                    }

                    System.out.println("────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────");
                    System.out.printf("📄 Page %d of %d%n", currentPage + 1, totalPages);

                    // Options
                    System.out.println();
                    String options = "[X] ➜ Exit";
                    if (currentPage > 0) options = "[B] ➜ Previous Page   " + options;
                    if (currentPage < totalPages - 1) options = "[F] ➜ Next Page   " + options;
                    System.out.println(options);

                    System.out.print("Choose option: ");
                    String choice = scanner.nextLine().trim().toUpperCase();

                    switch (choice) {
                        case "F" -> { if (currentPage < totalPages - 1) currentPage++; }
                        case "B" -> { if (currentPage > 0) currentPage--; }
                        case "X" -> running = false;
                        default -> {}
                    }
                }
            }

        } catch (SQLException e) {
            System.out.println(RED + "Error loading inventory log: " + e.getMessage() + RESET);
            MainDB.pause();
        }
    }

}
