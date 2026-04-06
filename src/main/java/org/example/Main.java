package org.example;

import static spark.Spark.*;

public class Main {
    public static void main(String[] args) {
        port(8080);

        get("/healthcheck", (req, res) -> "AMIR'S SERVER IS LIVE AND ON PROD");
    }
}
