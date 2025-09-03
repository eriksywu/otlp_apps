#!/bin/bash

# Simple script to run the complete OTLP TestApp stack

echo "🚀 Starting OTLP TestApp with full observability stack"
echo "====================================================="

# Check dependencies
if ! command -v docker &> /dev/null; then
    echo "❌ Docker is not installed"
    exit 1
fi

if ! command -v docker-compose &> /dev/null; then
    echo "❌ Docker Compose is not installed"
    exit 1
fi

# Parse command line arguments
LOAD_TEST=false
DETACHED=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --load-test|-l)
            LOAD_TEST=true
            shift
            ;;
        --detached|-d)
            DETACHED=true
            shift
            ;;
        --help|-h)
            echo "Usage: $0 [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  -l, --load-test    Start with continuous load testing"
            echo "  -d, --detached     Run in detached mode (background)"
            echo "  -h, --help         Show this help message"
            echo ""
            echo "Examples:"
            echo "  $0                 # Start the stack in foreground"
            echo "  $0 -d              # Start the stack in background"
            echo "  $0 -l              # Start with load testing"
            echo "  $0 -d -l           # Start with load testing in background"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

# Build the stack
echo "🏗️  Building the application..."
docker-compose build

if [ $? -ne 0 ]; then
    echo "❌ Build failed"
    exit 1
fi

# Start the stack
echo ""
if [ "$LOAD_TEST" = true ]; then
    echo "🔥 Starting with load testing enabled..."
    COMPOSE_CMD="docker-compose --profile load-test up"
else
    echo "🏃 Starting the observability stack..."
    COMPOSE_CMD="docker-compose up"
fi

if [ "$DETACHED" = true ]; then
    COMPOSE_CMD="$COMPOSE_CMD -d"
fi

# Execute the command
eval $COMPOSE_CMD

if [ "$DETACHED" = true ]; then
    echo ""
    echo "✅ Stack started in background!"
    echo ""
    echo "🌐 Access URLs:"
    echo "   Application:     http://localhost:8080"
    echo "   Health Check:    http://localhost:8080/health"
    echo "   Jaeger UI:       http://localhost:16686"
    echo "   Prometheus:      http://localhost:9090"
    echo "   OTEL Collector:  http://localhost:13133"
    echo ""
    echo "📋 Useful commands:"
    echo "   docker-compose logs -f testapp     # View application logs"
    echo "   docker-compose logs -f otel-collector  # View collector logs"
    echo "   docker-compose down                # Stop all services"
    echo "   docker-compose ps                  # Show running services"
    echo ""
    echo "🧪 Test the application:"
    echo "   curl http://localhost:8080/health"
    echo "   curl http://localhost:8080/api/test"
else
    echo ""
    echo "👋 Stack stopped. Goodbye!"
fi