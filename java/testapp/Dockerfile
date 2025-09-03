# Multi-stage Dockerfile for OTLP TestApp
# Supports both amd64 and arm64 architectures

# Build stage - Use Maven with OpenJDK 17
FROM --platform=$BUILDPLATFORM maven:3.9.4-eclipse-temurin-17 AS builder

# Set working directory
WORKDIR /app

# Copy Maven configuration
COPY pom.xml .

# Download dependencies (this layer will be cached if pom.xml doesn't change)
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application
RUN mvn clean package -DskipTests -B

# Verify the JAR was created
RUN ls -la target/ && test -f target/testapp-1.0.0.jar

# Runtime stage - Use minimal JRE
FROM eclipse-temurin:17-jre-alpine AS runtime

# Install curl for downloading OpenTelemetry agent and health checks
RUN apk add --no-cache curl

# Create non-root user for security
RUN addgroup -g 1001 appgroup && \
    adduser -u 1001 -G appgroup -s /bin/sh -D appuser

# Set working directory
WORKDIR /app

# Create directories with proper permissions
RUN mkdir -p /app/agents && \
    chown -R appuser:appgroup /app

# Switch to non-root user
USER appuser

# Download OpenTelemetry Java agent
# Using a specific version for reproducibility
ARG OTEL_VERSION=1.32.0
RUN curl -L -o /app/agents/opentelemetry-javaagent.jar \
    "https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_VERSION}/opentelemetry-javaagent.jar"

# Verify the agent was downloaded
RUN ls -la /app/agents/ && test -f /app/agents/opentelemetry-javaagent.jar

# Copy the built JAR from builder stage
COPY --from=builder --chown=appuser:appgroup /app/target/testapp-1.0.0.jar /app/testapp.jar

# Verify the application JAR
RUN ls -la /app/testapp.jar

# Expose the application port
EXPOSE 8080

# Environment variables for OpenTelemetry configuration
ENV OTEL_SERVICE_NAME=testapp
ENV OTEL_SERVICE_VERSION=1.0.0
ENV OTEL_RESOURCE_ATTRIBUTES=service.name=testapp,service.version=1.0.0
ENV OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317
ENV OTEL_EXPORTER_OTLP_PROTOCOL=grpc
ENV OTEL_METRICS_EXPORTER=otlp
ENV OTEL_LOGS_EXPORTER=otlp
ENV OTEL_TRACES_EXPORTER=otlp

# JVM options for better container performance
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"

# Application configuration
ENV PORT=8080
ENV MAX_LATENCY_MS=5000

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
    CMD curl -f http://localhost:${PORT}/health || exit 1

# Command to run the application with OpenTelemetry agent
CMD java ${JAVA_OPTS} \
    -javaagent:/app/agents/opentelemetry-javaagent.jar \
    -jar /app/testapp.jar

# Labels for better maintainability
LABEL maintainer="OTLP TestApp" \
      version="1.0.0" \
      description="Simple HTTP server for testing OpenTelemetry instrumentation" \
      org.opencontainers.image.source="https://github.com/example/otlp-testapp" \
      org.opencontainers.image.title="OTLP TestApp" \
      org.opencontainers.image.description="HTTP server with synthetic latency for OpenTelemetry testing"