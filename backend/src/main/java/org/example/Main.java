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
        String url = System.getenv().getOrDefault("DB_URL", "jdbc:postgresql://postgres:5432/healthcheck");
        String user = System.getenv().getOrDefault("DB_USER", "amir");
        String pass = System.getenv().getOrDefault("DB_PASS", "amir123");

        int retries = 10;

        while (retries > 0) {
            try {
                logger.info("Trying to connect to database...");
                Connection connection = DriverManager.getConnection(url, user, pass);
                logger.info("Database connected successfully");
                return connection;
            } catch (SQLException e) {
                retries--;
                logger.warning("Database not ready. Retries left: " + retries);

                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ex) {
                    logger.warning("Database retry sleep interrupted");
                }
            }
        }

        logger.severe("Database not reachable after all retries");
        throw new SQLException("Database not reachable after retries");
    }

    private static void initDb() {
        logger.info("Initializing database...");

        String sql = """
            CREATE TABLE IF NOT EXISTS users (
                id SERIAL PRIMARY KEY,
                first_name VARCHAR(100) NOT NULL,
                last_name VARCHAR(100) NOT NULL,
                age INT NOT NULL
            );
        """;

        try (Connection c = getConnection(); Statement st = c.createStatement()) {
            st.execute(sql);
            logger.info("Users table is ready");
        } catch (SQLException e) {
            logger.severe("Database initialization failed: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {
        logger.info("Starting server...");

        port(8080);

        initDb();

        logger.info("Server running on port 8080");

        get("/healthcheck", (req, res) -> {
            logger.info("GET /healthcheck called");

            return "AMIR'S SERVER IS LIVE - version 3 (CRUD enabled)";
        });

        get("/users", (req, res) -> {
            logger.info("GET /users called");

            res.type("application/json");

            List<Map<String, Object>> users = new ArrayList<>();

            try (Connection c = getConnection();
                 Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT * FROM users ORDER BY id")) {

                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();

                    row.put("id", rs.getInt("id"));
                    row.put("first_name", rs.getString("first_name"));
                    row.put("last_name", rs.getString("last_name"));
                    row.put("age", rs.getInt("age"));

                    users.add(row);
                }

                logger.info("Fetched users count: " + users.size());

            } catch (SQLException e) {
                logger.severe("Error fetching users: " + e.getMessage());

                res.status(500);
                return gson.toJson(Map.of("error", "Database error"));
            }

            return gson.toJson(users);
        });

        get("/users/:id", (req, res) -> {
            res.type("application/json");

            int id = Integer.parseInt(req.params("id"));

            logger.info("GET /users/" + id + " called");

            try (Connection c = getConnection();
                 PreparedStatement ps = c.prepareStatement("SELECT * FROM users WHERE id = ?")) {

                ps.setInt(1, id);

                ResultSet rs = ps.executeQuery();

                if (rs.next()) {
                    logger.info("User found with id: " + id);

                    Map<String, Object> row = new LinkedHashMap<>();

                    row.put("id", rs.getInt("id"));
                    row.put("first_name", rs.getString("first_name"));
                    row.put("last_name", rs.getString("last_name"));
                    row.put("age", rs.getInt("age"));

                    return gson.toJson(row);
                }

                logger.warning("User not found with id: " + id);

                res.status(404);
                return gson.toJson(Map.of("error", "User not found"));

            } catch (SQLException e) {
                logger.severe("Error fetching user with id " + id + ": " + e.getMessage());

                res.status(500);
                return gson.toJson(Map.of("error", "Database error"));
            }
        });

        post("/users", (req, res) -> {
            logger.info("POST /users called");

            res.type("application/json");

            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> body = gson.fromJson(req.body(), Map.class);

                String firstName = (String) body.get("first_name");
                String lastName = (String) body.get("last_name");
                int age = ((Number) body.get("age")).intValue();

                String sql = "INSERT INTO users (first_name, last_name, age) VALUES (?, ?, ?) RETURNING id";

                try (Connection c = getConnection();
                     PreparedStatement ps = c.prepareStatement(sql)) {

                    ps.setString(1, firstName);
                    ps.setString(2, lastName);
                    ps.setInt(3, age);

                    ResultSet rs = ps.executeQuery();
                    rs.next();

                    int newId = rs.getInt("id");

                    logger.info("User created with id: " + newId);

                    res.status(201);

                    return gson.toJson(Map.of(
                            "id", newId,
                            "first_name", firstName,
                            "last_name", lastName,
                            "age", age
                    ));
                }

            } catch (Exception e) {
                logger.severe("Error creating user: " + e.getMessage());

                res.status(500);
                return gson.toJson(Map.of("error", "Could not create user"));
            }
        });

        put("/users/:id", (req, res) -> {
            res.type("application/json");

            int id = Integer.parseInt(req.params("id"));

            logger.info("PUT /users/" + id + " called");

            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> body = gson.fromJson(req.body(), Map.class);

                String sql = "UPDATE users SET first_name = ?, last_name = ?, age = ? WHERE id = ?";

                try (Connection c = getConnection();
                     PreparedStatement ps = c.prepareStatement(sql)) {

                    ps.setString(1, (String) body.get("first_name"));
                    ps.setString(2, (String) body.get("last_name"));
                    ps.setInt(3, ((Number) body.get("age")).intValue());
                    ps.setInt(4, id);

                    int rows = ps.executeUpdate();

                    if (rows == 0) {
                        logger.warning("Update failed. User not found with id: " + id);

                        res.status(404);
                        return gson.toJson(Map.of("error", "User not found"));
                    }

                    logger.info("User updated with id: " + id);

                    return gson.toJson(Map.of("message", "User updated"));
                }

            } catch (Exception e) {
                logger.severe("Error updating user with id " + id + ": " + e.getMessage());

                res.status(500);
                return gson.toJson(Map.of("error", "Could not update user"));
            }
        });

        delete("/users/:id", (req, res) -> {
            res.type("application/json");

            int id = Integer.parseInt(req.params("id"));

            logger.info("DELETE /users/" + id + " called");

            try (Connection c = getConnection();
                 PreparedStatement ps = c.prepareStatement("DELETE FROM users WHERE id = ?")) {

                ps.setInt(1, id);

                int rows = ps.executeUpdate();

                if (rows == 0) {
                    logger.warning("Delete failed. User not found with id: " + id);

                    res.status(404);
                    return gson.toJson(Map.of("error", "User not found"));
                }

                logger.info("User deleted with id: " + id);

                return gson.toJson(Map.of("message", "User deleted"));

            } catch (SQLException e) {
                logger.severe("Error deleting user with id " + id + ": " + e.getMessage());

                res.status(500);
                return gson.toJson(Map.of("error", "Could not delete user"));
            }
        });
    }
}