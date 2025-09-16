package com.example.testapp;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Random;
import java.util.logging.Logger;
import java.time.Duration;
import java.net.URI;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.registry.otlp.OtlpConfig;
import io.micrometer.registry.otlp.OtlpMeterRegistry;

/**
 * Simple HTTP server using Jetty for testing OpenTelemetry instrumentation.
 * Generates synthetic latency and random HTTP status codes.
 * Jetty is fully supported by OpenTelemetry Java agent for auto-instrumentation.
 */
public class TestApp {
    private static final Logger logger = Logger.getLogger(TestApp.class.getName());
    private static final Random random = new Random();

    // Configuration
    private static final int PORT = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
    private static final int MAX_LATENCY_MS = Integer.parseInt(System.getenv().getOrDefault("MAX_LATENCY_MS", "5000"));

    // Micrometer metrics
    private static MeterRegistry meterRegistry;
    private static Counter requestCounter;
    private static Timer requestTimer;
    private static Counter errorCounter;
    
    public static void main(String[] args) throws Exception {
        logger.info("Starting TestApp Jetty server on port " + PORT);

        // Initialize Micrometer OTLP registry
        initializeMicrometer();

        Server server = new Server(PORT);
        server.setHandler(new TestAppHandler());
        
        // Add shutdown hook for graceful termination
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down TestApp server...");
            try {
                server.stop();
                logger.info("TestApp server stopped.");
            } catch (Exception e) {
                logger.severe("Error stopping server: " + e.getMessage());
            }
        }));
        
        server.start();
        logger.info("TestApp server started successfully. Ready to accept requests.");
        server.join();
    }

    private static void initializeMicrometer() {
        try {
            // Get OTLP endpoint from environment variable (same as OpenTelemetry agent)
            String otlpEndpoint = System.getenv().getOrDefault("OTEL_EXPORTER_OTLP_ENDPOINT", "http://localhost:4317");

            // Create OTLP configuration
            OtlpConfig otlpConfig = new OtlpConfig() {
                @Override
                public String get(String key) {
                    return null; // Accept defaults
                }

                @Override
                public String url() {
                    return otlpEndpoint + "/v1/metrics";
                }

                @Override
                public Duration step() {
                    return Duration.ofSeconds(10); // Export metrics every 10 seconds
                }
            };

            // Create and register OTLP meter registry
            meterRegistry = new OtlpMeterRegistry(otlpConfig, io.micrometer.core.instrument.Clock.SYSTEM);
            Metrics.addRegistry(meterRegistry);

            // Initialize custom metrics
            requestCounter = Counter.builder("micrometer_http_requests_total")
                    .description("Total number of HTTP requests")
                    .register(meterRegistry);

            requestTimer = Timer.builder("micrometer_http_request_duration_seconds")
                    .description("HTTP request duration in seconds")
                    .register(meterRegistry);

            errorCounter = Counter.builder("micrometer_http_errors_total")
                    .description("Total number of HTTP errors")
                    .register(meterRegistry);

            // Add JVM gauge
            Gauge.builder("micrometer_jvm_memory_used_bytes", Runtime.getRuntime(), runtime -> runtime.totalMemory() - runtime.freeMemory())
                    .description("JVM memory usage in bytes")
                    .register(meterRegistry);

            logger.info("Micrometer OTLP registry initialized with endpoint: " + otlpEndpoint);

        } catch (Exception e) {
            logger.severe("Failed to initialize Micrometer: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    static class TestAppHandler extends AbstractHandler {
        @Override
        public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
                throws IOException, ServletException {

            String method = request.getMethod();
            String path = request.getRequestURI();
            String remoteAddress = request.getRemoteAddr();

            logger.info(String.format("Received %s request to %s from %s", method, path, remoteAddress));

            // Mark request as handled
            baseRequest.setHandled(true);

            // Start timer for request duration
            Timer.Sample timerSample = Timer.start(meterRegistry);

            try {
                // Increment request counter
                requestCounter.increment();

                // Handle health endpoint without latency
                if ("/health".equals(path)) {
                    handleHealth(response);
                    timerSample.stop(requestTimer);
                    return;
                }

                // Add synthetic latency (0-5 seconds by default)
                int latencyMs = random.nextInt(MAX_LATENCY_MS + 1);
                logger.info("Adding synthetic latency: " + latencyMs + "ms");
                Thread.sleep(latencyMs);

                // Generate random status code
                int statusCode = generateRandomStatusCode();
                String responseBody = generateResponseBody(statusCode, method, path, latencyMs);

                // Send response
                response.setContentType("application/json");
                response.setStatus(statusCode);

                try (PrintWriter out = response.getWriter()) {
                    out.println(responseBody);
                }

                // Record metrics
                timerSample.stop(requestTimer);

                if (statusCode >= 400) {
                    errorCounter.increment();
                }

                logger.info(String.format("Responded with status %d after %dms latency", statusCode, latencyMs));

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warning("Request handling interrupted");
                timerSample.stop(requestTimer);
                errorCounter.increment();
                sendErrorResponse(response, 503, "Service interrupted");
            } catch (Exception e) {
                logger.severe("Error handling request: " + e.getMessage());
                timerSample.stop(requestTimer);
                errorCounter.increment();
                sendErrorResponse(response, 500, "Internal server error");
            }
        }
        
        private void handleHealth(HttpServletResponse response) throws IOException {
            String healthResponse = "{\"status\":\"UP\",\"timestamp\":" + System.currentTimeMillis() + "}";
            
            response.setContentType("application/json");
            response.setStatus(200);
            
            try (PrintWriter out = response.getWriter()) {
                out.println(healthResponse);
            }
            
            logger.info("Health check responded with status 200");
        }
        
        private void sendErrorResponse(HttpServletResponse response, int status, String message) throws IOException {
            String errorResponse = String.format("{\"error\":\"%s\",\"status\":%d}", message, status);
            
            response.setContentType("application/json");
            response.setStatus(status);
            
            try (PrintWriter out = response.getWriter()) {
                out.println(errorResponse);
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
                "{\"status\":%d,\"message\":\"%s\",\"method\":\"%s\",\"path\":\"%s\",\"latency_ms\":%d,\"timestamp\":%d}",
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