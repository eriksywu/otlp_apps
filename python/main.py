from prometheus_client import Counter, start_http_server
import time
import random

REQUEST_COUNT = Counter('http_requests', 'Total HTTP requests served', ['method', 'status_code'])

# Simulate HTTP request handling
def handle_request():
    REQUEST_COUNT.labels(method='get', status_code='200').inc()
    # Add some random delay
    time.sleep(random.uniform(0.1, 0.5))

# Simulate HTTP request with error
def handle_error():
    # Add processing delay so that this time series has a later creation time
    time.sleep(30)
    REQUEST_COUNT.labels(method='get', status_code='500').inc()

if __name__ == '__main__':
    # Start up the Prometheus metrics HTTP server on port 8001
    start_http_server(8001)
    print("Prometheus metrics available at http://localhost:8001/metrics")

    # Simulate incoming requests in a loop
    while True:
        handle_request()
        handle_error()
