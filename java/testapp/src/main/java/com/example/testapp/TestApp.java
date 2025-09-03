package com.example.testapp;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * Simple HTTP server for testing OpenTelemetry instrumentation.
 * Generates synthetic latency and random HTTP status codes.
 */
public class TestApp {
    private static final Logger logger = Logger.getLogger(TestApp.class.getName());
    private static final Random random = new Random();
    
    // Configuration
    private static final int PORT = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
    private static final int MAX_LATENCY_MS = Integer.parseInt(System.getenv().getOrDefault("MAX_LATENCY_MS", "5000"));
    
    public static void main(String[] args) throws IOException {
        logger.info("Starting TestApp HTTP server on port " + PORT);
        
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/", new RequestHandler());
        server.createContext("/health", new HealthHandler());
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.start();
        
        logger.info("TestApp server started successfully. Ready to accept requests.");
        
        // Add shutdown hook for graceful termination
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down TestApp server...");
            server.stop(5);
            logger.info("TestApp server stopped.");
        }));
    }
    
    static class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String healthResponse = "{\"status\":\"UP\",\"timestamp\":" + System.currentTimeMillis() + "}";
            
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, healthResponse.getBytes().length);
            
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(healthResponse.getBytes());
            }
        }
    }
    
    static class RequestHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            String remoteAddress = exchange.getRemoteAddress().getAddress().getHostAddress();
            
            logger.info(String.format("Received %s request to %s from %s", method, path, remoteAddress));
            
            try {
                // Skip synthetic latency for health endpoint
                int latencyMs = 0;
                if (!path.equals("/health")) {
                    // Add synthetic latency (0-5 seconds by default)
                    latencyMs = random.nextInt(MAX_LATENCY_MS + 1);
                    logger.info("Adding synthetic latency: " + latencyMs + "ms");
                    Thread.sleep(latencyMs);
                }
                
                // Generate random status code
                int statusCode = generateRandomStatusCode();
                String responseBody = generateResponseBody(statusCode, method, path, latencyMs);
                
                // Send response
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(statusCode, responseBody.getBytes().length);
                
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(responseBody.getBytes());
                }
                
                logger.info(String.format("Responded with status %d after %dms latency", statusCode, latencyMs));
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warning("Request handling interrupted");
                
                // Send 503 Service Unavailable
                String errorResponse = "{\"error\":\"Service interrupted\",\"status\":503}";
                exchange.sendResponseHeaders(503, errorResponse.getBytes().length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(errorResponse.getBytes());
                }
            } catch (Exception e) {
                logger.severe("Error handling request: " + e.getMessage());
                
                // Send 500 Internal Server Error
                String errorResponse = "{\"error\":\"Internal server error\",\"status\":500}";
                exchange.sendResponseHeaders(500, errorResponse.getBytes().length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(errorResponse.getBytes());
                }
            }
        }
        
        /**
         * Generate random HTTP status code based on distribution:
         * - 200 OK: 50% of requests
         * - 4xx errors: 25% of requests
         * - 5xx errors: 25% of requests
         */
        private int generateRandomStatusCode() {
            int rand = random.nextInt(100);
            
            if (rand < 50) {
                // 50% chance of 200 OK
                return 200;
            } else if (rand < 75) {
                // 25% chance of 4xx error
                int[] clientErrors = {400, 401, 403, 404, 409, 429};
                return clientErrors[random.nextInt(clientErrors.length)];
            } else {
                // 25% chance of 5xx error
                int[] serverErrors = {500, 502, 503, 504};
                return serverErrors[random.nextInt(serverErrors.length)];
            }
        }
        
        /**
         * Generate JSON response body with request details
         */
        private String generateResponseBody(int statusCode, String method, String path, int latencyMs) {
            String status = getStatusMessage(statusCode);
            long timestamp = System.currentTimeMillis();
            
            return String.format(
                "{\"status\":%d,\"message\": \"%s\",\"method\":\"%s\",\"path\":\"%s\",\"latency_ms\":%d,\"timestamp\":%d}",
                statusCode, status, method, path, latencyMs, timestamp
            );
        }
        
        /**
         * Get human-readable status message for HTTP status code
         */
        private String getStatusMessage(int statusCode) {
            switch (statusCode) {
                case 200: return "OK";
                case 400: return "Bad Request";
                case 401: return "Unauthorized";
                case 403: return "Forbidden";
                case 404: return "Not Found";
                case 409: return "Conflict";
                case 429: return "Too Many Requests";
                case 500: return "Internal Server Error";
                case 502: return "Bad Gateway";
                case 503: return "Service Unavailable";
                case 504: return "Gateway Timeout";
                default: return "Unknown Status";
            }
        }
    }
}