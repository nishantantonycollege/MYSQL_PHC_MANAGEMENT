import java.io.FileInputStream;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/** One-time migration utility for copying tables from data/phc.db into MySQL database phc. */
public class SQLiteToMySQLMigration {
    public static void main(String[] args) throws Exception {
        Properties p = new Properties();
        try (FileInputStream in = new FileInputStream("data/mysql.properties")) { p.load(in); }
        String mysqlUrl = p.getProperty("url", "jdbc:mysql://localhost:3306/phc?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
        String user = p.getProperty("user", "root");
        String password = p.getProperty("password", "");
        String sqliteUrl = "jdbc:sqlite:data/phc.db";

        try (Connection sqlite = DriverManager.getConnection(sqliteUrl);
             Connection mysql = DriverManager.getConnection(mysqlUrl, user, password)) {
            List<String> tables = new ArrayList<>();
            try (Statement st = sqlite.createStatement();
                 ResultSet rs = st.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name")) {
                while (rs.next()) tables.add(rs.getString(1));
            }
            mysql.setAutoCommit(false);
            try {
                for (String table : tables) {
                    if (!table.matches("[A-Za-z0-9_]+")) {
                        System.out.println("Skipping table with unsupported name: " + table);
                        continue;
                    }
                    try (Statement st = mysql.createStatement()) { st.executeUpdate("DROP TABLE IF EXISTS `" + table + "`"); }
                    int columns;
                    try (Statement st = sqlite.createStatement(); ResultSet rs = st.executeQuery("SELECT * FROM `" + table + "` LIMIT 0")) {
                        columns = rs.getMetaData().getColumnCount();
                    }
                    if (columns == 0) continue;
                    StringBuilder create = new StringBuilder("CREATE TABLE `" + table + "` (`_row_order` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY");
                    for (int i = 0; i < columns; i++) create.append(", `col").append(i).append("` LONGTEXT NULL");
                    create.append(") CHARACTER SET utf8mb4");
                    try (Statement st = mysql.createStatement()) { st.executeUpdate(create.toString()); }

                    StringBuilder insert = new StringBuilder("INSERT INTO `" + table + "` (");
                    for (int i = 0; i < columns; i++) insert.append(i == 0 ? "" : ", ").append("`col").append(i).append("`");
                    insert.append(") VALUES (");
                    for (int i = 0; i < columns; i++) insert.append(i == 0 ? "?" : ",?");
                    insert.append(")");
                    int copied = 0;
                    try (Statement read = sqlite.createStatement(); ResultSet rs = read.executeQuery("SELECT * FROM `" + table + "` ORDER BY rowid");
                         PreparedStatement write = mysql.prepareStatement(insert.toString())) {
                        while (rs.next()) {
                            for (int i = 1; i <= columns; i++) write.setString(i, rs.getString(i));
                            write.addBatch();
                            copied++;
                            if (copied % 500 == 0) write.executeBatch();
                        }
                        write.executeBatch();
                    }
                    System.out.println("Migrated " + table + ": " + copied + " rows");
                }
                mysql.commit();
                System.out.println("Migration complete. Tables copied: " + tables.size());
            } catch (Exception e) {
                mysql.rollback();
                throw e;
            }
        }
    }
}
