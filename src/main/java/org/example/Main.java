package org.example;

import static spark.Spark.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

public class Main {
    private static final Logger logger = Logger.getLogger(Main.class.getName());
    private static final AtomicInteger requestCount = new AtomicInteger(0);

    public static void main(String[] args) {
        port(8080);
        get("/healthcheck", (req, res) -> {
            int count = requestCount.incrementAndGet();
            logger.info("Healthcheck hit #" + count + " from " + req.ip());
            return "AMIR'S SERVER IS LIVE AND ON PROD - version 2";
        });
    }
}