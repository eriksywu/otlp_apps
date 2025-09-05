package main

import (
	"context"
	"encoding/json"
	"io"
	"log"
	"net/http"
	"os"
	"strconv"
	"sync"
	"time"

	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promhttp"
	"go.opentelemetry.io/contrib/instrumentation/net/http/otelhttp"
	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/exporters/otlp/otlpmetric/otlpmetricgrpc"
	"go.opentelemetry.io/otel/metric"
	sdkmetric "go.opentelemetry.io/otel/sdk/metric"
	"go.opentelemetry.io/otel/sdk/resource"
	semconv "go.opentelemetry.io/otel/semconv/v1.4.0"
)

var (
	// OTLP metric
	otlpPathIncrementSum metric.Int64Counter
	// Prometheus metric
	promPathIncrementSum *prometheus.CounterVec
)

const (
	otlpSumCounterName = "erik_otlp_path_increment_sum"
	promCounterName    = "erik_prom_path_increment_sum"
)

func init() {
	promPathIncrementSum = prometheus.NewCounterVec(
		prometheus.CounterOpts{
			Name: promCounterName,
			Help: "Running sum of incrementBy values by path",
		},
		[]string{"path"},
	)
	prometheus.MustRegister(promPathIncrementSum)
}

const scopeName = "erik-wu-test-scope"

func initOTLPMetrics(ctx context.Context) error {
	otlpEndpoint := os.Getenv("OTLP_ENDPOINT")
	if otlpEndpoint == "" {
		otlpEndpoint = "localhost:4317"
	}

	exporter, err := otlpmetricgrpc.New(ctx,
		otlpmetricgrpc.WithEndpoint(otlpEndpoint),
		otlpmetricgrpc.WithInsecure(),
	)
	if err != nil {
		return err
	}

	instanceId := "erik-test-instance"

	if v, k := os.LookupEnv("POD_NAME"); k && v != "" {
		instanceId = v
	}
	res, err := resource.New(ctx,
		resource.WithAttributes(
			semconv.ServiceInstanceIDKey.String(instanceId),
			semconv.ServiceNameKey.String("erik-test-service"),
			semconv.ServiceVersionKey.String("1.0.0"),
		),
	)
	if err != nil {
		return err
	}

	meterProvider := sdkmetric.NewMeterProvider(
		sdkmetric.WithResource(res),
		sdkmetric.WithReader(sdkmetric.NewPeriodicReader(exporter, sdkmetric.WithInterval(10*time.Second))),
	)

	otel.SetMeterProvider(meterProvider)

	meter := meterProvider.Meter(
		scopeName,
		metric.WithInstrumentationVersion("v1.0.0"),
	)

	otlpPathIncrementSum, err = meter.Int64Counter(
		otlpSumCounterName,
		metric.WithDescription("Running sum of incrementBy values by path"),
	)

	if err != nil {
		return err
	}

	log.Printf("OTLP metrics initialized, sending to endpoint: %s", otlpEndpoint)
	return nil
}

type IncrementRequest struct {
	IncrementBy              int `json:"incrementBy"`
	IncrementByPeriodic      int `json:"incrementByPeriodic,omitempty"`
	IncrementIntervalSeconds int `json:"incrementIntervalSeconds,omitempty"`
}

type dummyHandler struct{}

func (h *dummyHandler) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	handleIncrement(w, r)
}

type intervalWorker struct {
	path            string
	incBy           int
	incIntervalSecs int
	done            chan struct{}
}

func (w *intervalWorker) start() {
	ticker := time.NewTicker(time.Duration(w.incIntervalSecs) * time.Second)
	defer ticker.Stop()

	for {
		log.Printf("Incrementing by %d for path %s", w.incBy, w.path)
		if otlpPathIncrementSum != nil {
			otlpPathIncrementSum.Add(context.Background(), int64(w.incBy),
				metric.WithAttributes(
					attribute.String("path", w.path),
				))
		}
		// Update Prometheus counter with path label
		if promPathIncrementSum != nil {
			promPathIncrementSum.WithLabelValues(w.path).Add(float64(w.incBy))
		}
		select {
		case <-ticker.C:
			continue
		case <-w.done:
			log.Printf("Stopping interval worker for path %s", w.path)
			return
		}
	}
}

const defaultIntervalSecs = 10
const defaultIncrementBy = 100

var intervalsForPath = make(map[string]*intervalWorker)

var l sync.Mutex

func handleIncrement(w http.ResponseWriter, r *http.Request) {
	l.Lock()
	defer l.Unlock()
	worker, exists := intervalsForPath[r.URL.Path]

	log.Printf("Received POST request to %s", r.URL.String())

	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	body, err := io.ReadAll(r.Body)
	if err != nil {
		http.Error(w, "Failed to read request body", http.StatusBadRequest)
		return
	}
	_ = r.Body.Close()

	var req IncrementRequest
	if err := json.Unmarshal(body, &req); err != nil {
		http.Error(w, "Invalid JSON payload", http.StatusBadRequest)
		return
	}

	log.Printf("Incrementing by %d for path %s", req.IncrementBy, r.URL.Path)

	// Update OTLP counter with path attribute
	if otlpPathIncrementSum != nil {
		otlpPathIncrementSum.Add(context.Background(), int64(req.IncrementBy),
			metric.WithAttributes(
				attribute.String("path", r.URL.Path),
			))
	}
	// Update Prometheus counter with path label
	if promPathIncrementSum != nil {
		promPathIncrementSum.WithLabelValues(r.URL.Path).Add(float64(req.IncrementBy))
	}
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusOK)
	if !exists && req.IncrementByPeriodic == 0 && req.IncrementIntervalSeconds == 0 {

		_, _ = w.Write(body)
		return
	}
	newWorker := &intervalWorker{
		path:            r.URL.Path,
		incBy:           max(req.IncrementByPeriodic, defaultIncrementBy),
		incIntervalSecs: max(req.IncrementIntervalSeconds, defaultIntervalSecs),
		done:            make(chan struct{}),
	}
	if exists {
		close(worker.done)
	}
	intervalsForPath[r.URL.Path] = newWorker
	go newWorker.start()
	_, _ = w.Write(body)
	return
}

func main() {
	ctx := context.Background()

	// Initialize OTLP metrics
	err := initOTLPMetrics(ctx)
	if err != nil {
		panic(err)
	}

	// Set up HTTP server with metrics endpoint
	// Configure OpenMetrics options based on environment variables
	enableOpenMetrics := false
	enableOpenMetricsTextCreatedSamples := false

	if value, exists := os.LookupEnv("ENABLE_OPEN_METRICS"); exists {
		if parsed, err := strconv.ParseBool(value); err == nil {
			enableOpenMetrics = parsed
		}
	}

	if value, exists := os.LookupEnv("ENABLE_OPEN_METRICS_TEXT_CREATED_SAMPLES"); exists {
		if parsed, err := strconv.ParseBool(value); err == nil {
			enableOpenMetricsTextCreatedSamples = parsed
		}
	}
	log.Printf("EnableOpenMetrics: %t", enableOpenMetrics)
	log.Printf("EnableOpenMetricsTextCreatedSamples: %t", enableOpenMetricsTextCreatedSamples)
	http.Handle("/metrics", promhttp.InstrumentMetricHandler(
		prometheus.DefaultRegisterer, promhttp.HandlerFor(prometheus.DefaultGatherer, promhttp.HandlerOpts{
			EnableOpenMetrics:                   enableOpenMetrics,
			EnableOpenMetricsTextCreatedSamples: enableOpenMetricsTextCreatedSamples,
		}),
	))
	// Add HTTP POST handler for any path on port 80
	handler := otelhttp.NewHandler(&dummyHandler{}, "test")
	http.Handle("/", handler)

	// Start HTTP server on port 80 for POST handlers
	go func() {
		log.Println("Starting POST handler server on :80")
		if err := http.ListenAndServe(":80", nil); err != nil {
			log.Printf("Error starting server on port 80: %v", err)
		}
	}()

	log.Println("Starting metrics server on :8080")
	log.Println("Metrics available at http://localhost:8080/metrics")
	log.Fatal(http.ListenAndServe(":8080", nil))
}
