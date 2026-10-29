import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;

class LoadDb {
    static String quoted(String value) {
        if (value == null) return "null";
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\"";
    }
    public static void main(String[] args) throws Exception {
        Map<String, Connection> connections = new HashMap<>();
        BufferedReader input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        PrintWriter output = new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8), true);
        String line;
        try {
            while ((line = input.readLine()) != null) {
                try {
                    String[] parts = line.split("\t", 2);
                    String database = parts[0];
                    String sql = parts[1];
                    if (!Set.of("user", "voucher", "order").contains(database) || !sql.stripLeading().toUpperCase().matches("(?s)(SELECT |SHOW ).*")) throw new IllegalArgumentException("Read-only query required");
                    Connection connection = connections.get(database);
                    if (connection == null || connection.isClosed()) {
                        String prefix = "LOAD_DB_" + database.toUpperCase() + "_";
                        connection = DriverManager.getConnection(System.getenv(prefix + "URL"), System.getenv(prefix + "USER"), System.getenv(prefix + "PASSWORD"));
                        connection.setReadOnly(true);
                        connections.put(database, connection);
                    }
                    StringBuilder json = new StringBuilder("{\"ok\":true,\"rows\":[");
                    try (Statement statement = connection.createStatement()) {
                        statement.setQueryTimeout(10);
                        try (ResultSet rows = statement.executeQuery(sql)) {
                            ResultSetMetaData metadata = rows.getMetaData();
                            boolean first = true;
                            while (rows.next()) {
                                if (!first) json.append(',');
                                first = false;
                                json.append('{');
                                for (int i = 1; i <= metadata.getColumnCount(); i++) {
                                    if (i > 1) json.append(',');
                                    json.append(quoted(metadata.getColumnLabel(i))).append(':').append(quoted(rows.getString(i)));
                                }
                                json.append('}');
                            }
                        }
                    }
                    output.println(json.append("]}"));
                } catch (Exception error) {
                    String detail = error instanceof SQLException e ? "SQLState=" + e.getSQLState() + ", code=" + e.getErrorCode() + ", " + e.getMessage() : error.getClass().getSimpleName();
                    output.println("{\"ok\":false,\"error\":" + quoted(detail) + "}");
                }
            }
        } finally {
            for (Connection connection : connections.values()) try { connection.close(); } catch (SQLException ignored) {}
        }
    }
}
