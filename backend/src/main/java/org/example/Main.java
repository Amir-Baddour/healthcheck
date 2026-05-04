package org.example;

import static spark.Spark.*;
import com.google.gson.Gson;
import java.sql.*;
import java.util.*;
import java.util.logging.Logger;

public class Main {
    private static final Logger logger = Logger.getLogger(Main.class.getName());
    private static final Gson gson = new Gson();

    private static Connection getConnection() throws SQLException {
        String url  = System.getenv().getOrDefault("DB_URL", "jdbc:postgresql://postgres:5432/healthcheck");
        String user = System.getenv().getOrDefault("DB_USER", "amir");
        String pass = System.getenv().getOrDefault("DB_PASS", "amir123");

        int retries = 10;

        while (retries > 0) {
            try {
                System.out.println("Trying to connect to DB...");
                return DriverManager.getConnection(url, user, pass);
            } catch (SQLException e) {
                retries--;
                System.out.println("DB not ready yet... retrying in 3 seconds");
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ignored) {}
            }
        }

        throw new SQLException("Database not reachable after retries");
    }

    private static void initDb() {
        String sql = """
            CREATE TABLE IF NOT EXISTS users (
                id         SERIAL PRIMARY KEY,
                first_name VARCHAR(100) NOT NULL,
                last_name  VARCHAR(100) NOT NULL,
                age        INT          NOT NULL
            );
        """;
        try (Connection c = getConnection(); Statement st = c.createStatement()) {
            st.execute(sql);
            logger.info("DB initialised – users table ready");
        } catch (SQLException e) {
            logger.severe("DB init failed: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {
        port(8080);
        initDb();

        // Health check
        get("/healthcheck", (req, res) -> "AMIR'S SERVER IS LIVE - version 3 (CRUD enabled)");

        // GET ALL
        get("/users", (req, res) -> {
            res.type("application/json");
            List<Map<String, Object>> users = new ArrayList<>();
            try (Connection c = getConnection();
                 Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT * FROM users ORDER BY id")) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id",         rs.getInt("id"));
                    row.put("first_name", rs.getString("first_name"));
                    row.put("last_name",  rs.getString("last_name"));
                    row.put("age",        rs.getInt("age"));
                    users.add(row);
                }
            }
            return gson.toJson(users);
        });

        // GET ONE
        get("/users/:id", (req, res) -> {
            res.type("application/json");
            int id = Integer.parseInt(req.params("id"));
            try (Connection c = getConnection();
                 PreparedStatement ps = c.prepareStatement("SELECT * FROM users WHERE id = ?")) {
                ps.setInt(1, id);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id",         rs.getInt("id"));
                    row.put("first_name", rs.getString("first_name"));
                    row.put("last_name",  rs.getString("last_name"));
                    row.put("age",        rs.getInt("age"));
                    return gson.toJson(row);
                }
                res.status(404);
                return gson.toJson(Map.of("error", "User not found"));
            }
        });

        // CREATE
        post("/users", (req, res) -> {
            res.type("application/json");
            @SuppressWarnings("unchecked")
            Map<String, Object> body = gson.fromJson(req.body(), Map.class);
            String firstName = (String) body.get("first_name");
            String lastName  = (String) body.get("last_name");
            int    age       = ((Number) body.get("age")).intValue();

            String sql = "INSERT INTO users (first_name, last_name, age) VALUES (?, ?, ?) RETURNING id";
            try (Connection c = getConnection();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, firstName);
                ps.setString(2, lastName);
                ps.setInt(3, age);
                ResultSet rs = ps.executeQuery();
                rs.next();
                int newId = rs.getInt("id");
                res.status(201);
                return gson.toJson(Map.of("id", newId, "first_name", firstName,
                        "last_name", lastName, "age", age));
            }
        });

        // UPDATE
        put("/users/:id", (req, res) -> {
            res.type("application/json");
            int id = Integer.parseInt(req.params("id"));
            @SuppressWarnings("unchecked")
            Map<String, Object> body = gson.fromJson(req.body(), Map.class);

            String sql = "UPDATE users SET first_name=?, last_name=?, age=? WHERE id=?";
            try (Connection c = getConnection();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, (String) body.get("first_name"));
                ps.setString(2, (String) body.get("last_name"));
                ps.setInt(3, ((Number) body.get("age")).intValue());
                ps.setInt(4, id);
                int rows = ps.executeUpdate();
                if (rows == 0) {
                    res.status(404);
                    return gson.toJson(Map.of("error", "User not found"));
                }
                return gson.toJson(Map.of("message", "User updated"));
            }
        });

        // DELETE
        delete("/users/:id", (req, res) -> {
            res.type("application/json");
            int id = Integer.parseInt(req.params("id"));
            try (Connection c = getConnection();
                 PreparedStatement ps = c.prepareStatement("DELETE FROM users WHERE id = ?")) {
                ps.setInt(1, id);
                int rows = ps.executeUpdate();
                if (rows == 0) {
                    res.status(404);
                    return gson.toJson(Map.of("error", "User not found"));
                }
                return gson.toJson(Map.of("message", "User deleted"));
            }
        });
    }
}