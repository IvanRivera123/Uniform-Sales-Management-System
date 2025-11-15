package main;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class TransactionHistory {
    static final String RESET = "\u001B[0m";
    static final String RED = "\u001B[31m";

    public static void viewTransactionHistory(Connection conn) {
        try {
            MainDB.clearScreen();
            System.out.println("╔════════════════════════════════════════════════════════════════════════════════════════════════╗");
            System.out.println("║                                 TRANSACTION HISTORY                                             ║");
            System.out.println("╚════════════════════════════════════════════════════════════════════════════════════════════════╝");

            String sql = """
                SELECT s.id, 
                       COALESCE(p.name, 'Deleted Product') AS product, 
                       s.quantity, 
                       s.total_price, 
                       s.payment_method, 
                       u.username AS sold_by, 
                       s.sale_date
                FROM sales s
                LEFT JOIN products p ON s.product_id = p.id
                LEFT JOIN users u ON s.user_id = u.id
                ORDER BY s.sale_date DESC
            """;

            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(sql)) {

                List<String[]> records = new ArrayList<>();
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMMM dd, yyyy hh:mm a");

                while (rs.next()) {
                    int id = rs.getInt("id");
                    String product = rs.getString("product");
                    int qty = rs.getInt("quantity");
                    double total = rs.getDouble("total_price");
                    String payment = rs.getString("payment_method");
                    String soldBy = rs.getString("sold_by");
                    Timestamp date = rs.getTimestamp("sale_date");
                    String formattedDate = (date != null) ? sdf.format(date) : "N/A";

                    records.add(new String[]{
                            String.valueOf(id),
                            product,
                            String.valueOf(qty),
                            String.format("%.2f", total),
                            payment,
                            soldBy,
                            formattedDate
                    });
                }

                if (records.isEmpty()) {
                    System.out.println(RED + "No transactions found." + RESET);
                    System.out.println("─────────────────────────────────────────────────────────────────────────────────────────────────");
                    MainDB.pause();
                    return;
                }

                final String format = "%-5s│ %-25s│ %-6s│ %-10s│ %-15s│ %-15s│ %-25s%n";
                Scanner scanner = new Scanner(System.in);
                int pageSize = 10;
                int currentPage = 0;
                int totalPages = (int) Math.ceil((double) records.size() / pageSize);
                boolean running = true;

                while (running) {
                    MainDB.clearScreen();
                    System.out.println("╔════════════════════════════════════════════════════════════════════════════════════════════════╗");
                    System.out.println("║                                 TRANSACTION HISTORY                                            ║");
                    System.out.println("╚════════════════════════════════════════════════════════════════════════════════════════════════╝");

                    System.out.printf(format, "ID", "Product", "Qty", "Total", "Payment", "Sold By", "Date");
                    System.out.println("─────┼──────────────────────────┼───────┼───────────┼────────────────┼────────────────┼───────────────────────────");

                    int start = currentPage * pageSize;
                    int end = Math.min(start + pageSize, records.size());

                    for (int i = start; i < end; i++) {
                        String[] r = records.get(i);
                        String productName = r[1];
                        if (productName.length() > 25) productName = productName.substring(0, 22) + "...";
                        System.out.printf(format, r[0], productName, r[2], r[3], r[4], r[5], r[6]);
                    }

                    System.out.println("──────────────────────────────────────────────────────────────────────────────────────────────────────────────────");
                    System.out.printf("Page %d of %d%n%n", currentPage + 1, totalPages);

                    String options = "[X] Exit";
                    if (currentPage > 0) options = "[B] Back   " + options;
                    if (currentPage < totalPages - 1) options = "[F] Forward   " + options;
                    System.out.println(options);

                    System.out.print("Choose option ➤ ");
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
            System.out.println(RED + "Error loading transaction history: " + e.getMessage() + RESET);
            MainDB.pause();
        }
    }
}
