# OTLP TestApp

A simple Java HTTP server application for testing OpenTelemetry Protocol (OTLP) SDK behavior, specifically for HTTP duration histogram metrics. This application is designed to be auto-instrumented using the OpenTelemetry Java agent with no code changes required.

## Features

- Simple HTTP server that accepts requests to any URL
- Synthetic latency: 0-5 seconds per request (configurable)
- Random HTTP status code distribution:
  - 200 OK: 50% of requests
  - 4xx errors: 25% of requests
  - 5xx errors: 25% of requests
- Automatic OpenTelemetry instrumentation via Java agent
- Docker containerized with multi-architecture support (amd64/arm64)
- Complete observability stack included (Jaeger, Prometheus, OTEL Collector)

## Quick Start

### Prerequisites

- Docker and Docker Compose installed
- No Java toolchain required (everything runs in containers)

### Build and Run

1. **Clone or create the project directory:**
   ```bash
   # Navigate to the testapp directory
   cd testapp
   ```

2. **Build and start the complete stack:**
   ```bash
   # Build and start all services
   docker-compose up --build
   ```

3. **Test the application:**
   ```bash
   # Send test requests
   curl http://localhost:8080/api/test
   curl http://localhost:8080/health
   curl http://localhost:8080/anything
   ```

4. **Access observability tools:**
   - **Application**: http://localhost:8080
   - **Jaeger UI** (traces): http://localhost:16686
   - **Prometheus** (metrics): http://localhost:9090
   - **OTEL Collector health**: http://localhost:13133

### With Load Testing

To run with continuous load generation:

```bash
# Start with load generator
docker-compose --profile load-test up --build
```

## Project Structure

```
testapp/
├── src/
│   └── main/
│       └── java/
│           └── com/
│               └── example/
│                   └── testapp/
│                       └── TestApp.java          # Main application
├── pom.xml                                       # Maven configuration
├── Dockerfile                                    # Multi-stage Docker build
├── docker-compose.yml                           # Complete stack definition
├── otel-collector-config.yaml                   # OpenTelemetry Collector config
├── prometheus.yml                               # Prometheus configuration
└── README.md                                    # This file
```

## Configuration

### Environment Variables

#### Application Settings
- `PORT`: HTTP server port (default: 8080)
- `MAX_LATENCY_MS`: Maximum synthetic latency in milliseconds (default: 5000)

#### OpenTelemetry Settings
- `OTEL_SERVICE_NAME`: Service name for telemetry (default: testapp)
- `OTEL_SERVICE_VERSION`: Service version (default: 1.0.0)
- `OTEL_EXPORTER_OTLP_ENDPOINT`: OTLP collector endpoint (default: http://otel-collector:4317)
- `OTEL_EXPORTER_OTLP_PROTOCOL`: OTLP protocol (default: grpc)

### Customizing Response Distribution

Edit the `generateRandomStatusCode()` method in `TestApp.java` to change the status code distribution:

```java
private int generateRandomStatusCode() {
    int rand = random.nextInt(100);
    
    if (rand < 50) {          // 50% success
        return 200;
    } else if (rand < 75) {   // 25% client errors
        // 4xx errors...
    } else {                  // 25% server errors
        // 5xx errors...
    }
}
```

## Development Commands

### Local Development

```bash
# Build only the application
docker-compose build testapp

# Run only the application (without observability stack)
docker run -p 8080:8080 testapp:latest

# View logs
docker-compose logs -f testapp

# Rebuild after changes
docker-compose up --build testapp
```

### Testing

```bash
# Send various test requests
curl -v http://localhost:8080/api/users
curl -v http://localhost:8080/health
curl -v http://localhost:8080/metrics
curl -X POST http://localhost:8080/data -d '{"test": "data"}'

# Load testing with multiple requests
for i in {1..10}; do curl http://localhost:8080/test-$i; sleep 1; done
```

### Debugging

```bash
# Check application logs
docker-compose logs testapp

# Check OpenTelemetry collector logs
docker-compose logs otel-collector

# Verify OpenTelemetry agent is loaded
docker-compose exec testapp ps aux

# Check JVM arguments
docker-compose exec testapp jps -v
```

## Observability

### Metrics

The application automatically exports HTTP metrics including:
- **http.server.duration**: HTTP request duration histogram
- **http.server.active_requests**: Active HTTP requests
- **jvm.***: JVM runtime metrics
- **process.***: Process metrics

### Traces

Every HTTP request generates a trace with:
- HTTP method, URL, status code
- Request duration
- Any errors or exceptions

### Logs

Application logs include:
- Request details (method, path, remote address)
- Synthetic latency applied
- Response status and timing

## Production Considerations

### Security
- Application runs as non-root user in container
- Minimal Alpine-based runtime image
- No external dependencies beyond JDK

### Performance
- Single-threaded server (suitable for testing, not production)
- Configurable JVM heap settings via `JAVA_OPTS`
- Health checks included

### Kubernetes Deployment

For Kubernetes deployment, consider:
1. Use the built Docker image
2. Configure OTLP endpoint to your collector
3. Set appropriate resource limits
4. Use ConfigMaps for OpenTelemetry configuration

Example Kubernetes snippet:
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: testapp
spec:
  template:
    spec:
      containers:
      - name: testapp
        image: testapp:latest
        env:
        - name: OTEL_EXPORTER_OTLP_ENDPOINT
          value: "http://your-collector:4317"
        - name: OTEL_RESOURCE_ATTRIBUTES
          value: "service.name=testapp,deployment.environment=production"
```

## Troubleshooting

### Common Issues

1. **Port already in use:**
   ```bash
   # Change port in docker-compose.yml or stop conflicting services
   sudo lsof -i :8080
   ```

2. **OpenTelemetry agent not loading:**
   ```bash
   # Check logs for agent download issues
   docker-compose logs testapp | grep -i otel
   ```

3. **No metrics in Prometheus:**
   - Verify collector is receiving data: http://localhost:13133
   - Check Prometheus targets: http://localhost:9090/targets

4. **No traces in Jaeger:**
   - Verify OTLP endpoint configuration
   - Check collector logs for export errors

### Clean Restart

```bash
# Stop all services and remove containers
docker-compose down

# Remove images and rebuild
docker-compose down --rmi all
docker-compose up --build
```

## License

This project is provided as-is for testing OpenTelemetry instrumentation. Modify as needed for your specific testing requirements.