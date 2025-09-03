#!/bin/bash

# Simple test script for the OTLP TestApp
# This script tests the application functionality

echo "🧪 Testing OTLP TestApp"
echo "======================="

# Test if Docker is available
if ! command -v docker &> /dev/null; then
    echo "❌ Docker is not installed or not in PATH"
    exit 1
fi

if ! command -v docker-compose &> /dev/null; then
    echo "❌ Docker Compose is not installed or not in PATH"
    exit 1
fi

echo "✅ Docker and Docker Compose are available"

# Build the application
echo ""
echo "🏗️  Building the application..."
docker-compose build testapp

if [ $? -ne 0 ]; then
    echo "❌ Build failed"
    exit 1
fi

echo "✅ Build successful"

# Start the application (without full stack for quick test)
echo ""
echo "🚀 Starting the application..."
docker-compose up -d testapp

# Wait for the application to start
echo "⏳ Waiting for application to start..."
sleep 10

# Test health endpoint
echo ""
echo "🩺 Testing health endpoint..."
HEALTH_RESPONSE=$(curl -s -w "%{http_code}" http://localhost:8080/health)
HEALTH_CODE="${HEALTH_RESPONSE: -3}"

if [ "$HEALTH_CODE" = "200" ]; then
    echo "✅ Health check passed"
    echo "   Response: ${HEALTH_RESPONSE%???}"
else
    echo "❌ Health check failed (HTTP $HEALTH_CODE)"
    docker-compose logs testapp
    docker-compose down
    exit 1
fi

# Test a few random endpoints
echo ""
echo "🎯 Testing random endpoints..."

for i in {1..5}; do
    echo "   Test $i/5..."
    RESPONSE=$(curl -s -w "%{http_code}" "http://localhost:8080/api/test$i")
    HTTP_CODE="${RESPONSE: -3}"
    RESPONSE_BODY="${RESPONSE%???}"
    
    echo "   -> HTTP $HTTP_CODE: $RESPONSE_BODY"
    
    # Add small delay between requests
    sleep 1
done

# Test different HTTP methods
echo ""
echo "🔧 Testing different HTTP methods..."

echo "   Testing POST..."
POST_RESPONSE=$(curl -s -w "%{http_code}" -X POST -d '{"test":"data"}' -H "Content-Type: application/json" http://localhost:8080/api/data)
POST_CODE="${POST_RESPONSE: -3}"
echo "   -> POST HTTP $POST_CODE"

echo "   Testing PUT..."
PUT_RESPONSE=$(curl -s -w "%{http_code}" -X PUT -d '{"update":"data"}' -H "Content-Type: application/json" http://localhost:8080/api/update)
PUT_CODE="${PUT_RESPONSE: -3}"
echo "   -> PUT HTTP $PUT_CODE"

echo "   Testing DELETE..."
DELETE_RESPONSE=$(curl -s -w "%{http_code}" -X DELETE http://localhost:8080/api/delete)
DELETE_CODE="${DELETE_RESPONSE: -3}"
echo "   -> DELETE HTTP $DELETE_CODE"

# Check application logs
echo ""
echo "📋 Recent application logs:"
echo "=========================="
docker-compose logs --tail=10 testapp

# Cleanup
echo ""
echo "🧹 Cleaning up..."
docker-compose down

if [ $? -eq 0 ]; then
    echo "✅ All tests completed successfully!"
    echo ""
    echo "🎉 The OTLP TestApp is working correctly!"
    echo ""
    echo "Next steps:"
    echo "1. Run the full stack: docker-compose up --build"
    echo "2. Access Jaeger UI: http://localhost:16686"
    echo "3. Access Prometheus: http://localhost:9090"
    echo "4. Send test requests: curl http://localhost:8080/api/test"
else
    echo "❌ Cleanup failed"
    exit 1
fi