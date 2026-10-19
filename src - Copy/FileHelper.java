import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.Properties;

// MySQL-backed storage. The public methods are kept compatible with the GUI.
public class FileHelper {
    private static final String FOLDER = "data";
    private static final String CONFIG_FILE = FOLDER + "/mysql.properties";

    private static Properties loadConfig() throws IOException {
        Properties p = new Properties();
        File f = new File(CONFIG_FILE);
        if (f.isFile()) {
            try (FileInputStream in = new FileInputStream(f)) { p.load(in); }
        }
        return p;
    }

    private static Connection connect() throws SQLException {
        try {
            Properties p = loadConfig();
            String url = p.getProperty("url", "jdbc:mysql://localhost:3306/phc?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
            String user = p.getProperty("user", "root");
            String password = p.getProperty("password", "");
            return DriverManager.getConnection(url, user, password);
        } catch (IOException e) {
            throw new SQLException("Could not read " + CONFIG_FILE + ". Copy mysql.properties.example to mysql.properties and enter your MySQL settings.", e);
        }
    }

    // "patients.csv" -> "patients". Only safe SQL identifier characters are allowed.
    private static String tableName(String fileName) {
        String table = fileName.replace(".csv", "").replaceAll("[^A-Za-z0-9_]", "");
        if (table.isEmpty()) throw new IllegalArgumentException("Invalid table name: " + fileName);
        return table;
    }

    public static ArrayList<String[]> readFile(String fileName) {
        ArrayList<String[]> rows = new ArrayList<>();
        String table = tableName(fileName);
        try (Connection conn = connect()) {
            if (!tableExists(conn, table)) return rows;
            int count = columnCount(conn, table);
            if (count == 0) return rows;
            String sql = "SELECT " + columnList(count) + " FROM `" + table + "` ORDER BY `_row_order`";
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) {
                    String[] row = new String[count];
                    for (int i = 0; i < count; i++) {
                        String v = rs.getString(i + 1);
                        row[i] = v == null ? "" : v;
                    }
                    rows.add(row);
                }
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return rows;
    }

    // Replaces the contents of a table, matching the behavior of the original helper.
    public static void writeFile(String fileName, ArrayList<String[]> rows) {
        String table = tableName(fileName);
        try (Connection conn = connect()) {
            conn.setAutoCommit(false);
            try {
                if (!rows.isEmpty() && tableExists(conn, table) && columnCount(conn, table) != rows.get(0).length) {
                    try (Statement st = conn.createStatement()) { st.executeUpdate("DROP TABLE `" + table + "`"); }
                }
                if (!tableExists(conn, table)) {
                    if (rows.isEmpty()) { conn.commit(); return; }
                    createTable(conn, table, rows.get(0).length);
                }
                try (Statement st = conn.createStatement()) { st.executeUpdate("DELETE FROM `" + table + "`"); }
                if (!rows.isEmpty()) {
                    int n = rows.get(0).length;
                    String sql = "INSERT INTO `" + table + "` (" + columnList(n) + ") VALUES (" + placeholders(n) + ")";
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        for (String[] row : rows) {
                            if (row.length != n) throw new SQLException("Rows in " + fileName + " have different column counts.");
                            for (int i = 0; i < n; i++) ps.setString(i + 1, row[i]);
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                }
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                if (e instanceof SQLException) throw (SQLException) e;
                throw new SQLException("Could not save " + fileName, e);
            }
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public static int countRecords(String fileName) {
        String table = tableName(fileName);
        try (Connection conn = connect()) {
            if (!tableExists(conn, table)) return 0;
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM `" + table + "`")) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) { e.printStackTrace(); return 0; }
    }

    private static boolean tableExists(Connection conn, String table) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    // Excludes the internal ordering column from the app's visible data columns.
    private static int columnCount(Connection conn, String table) throws SQLException {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT * FROM `" + table + "` LIMIT 0")) {
            return Math.max(0, rs.getMetaData().getColumnCount() - 1);
        }
    }

    private static void createTable(Connection conn, String table, int columns) throws SQLException {
        if (columns < 1) throw new SQLException("Cannot create a table with no columns.");
        StringBuilder sb = new StringBuilder("CREATE TABLE `" + table + "` (`_row_order` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY");
        for (int i = 0; i < columns; i++) sb.append(", `col").append(i).append("` TEXT NULL");
        sb.append(") CHARACTER SET utf8mb4");
        try (Statement st = conn.createStatement()) { st.executeUpdate(sb.toString()); }
    }

    private static String columnList(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(i == 0 ? "" : ", ").append("`col").append(i).append("`");
        return sb.toString();
    }

    private static String placeholders(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(i == 0 ? "?" : ",?");
        return sb.toString();
    }
}
