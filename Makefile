# Docker image URLs
GO_IMAGE_URL ?= us-docker.pkg.dev/chronosphere-global-infra/dev/erikwugoapp:test
JAVA_IMAGE_URL ?= us-docker.pkg.dev/chronosphere-global-infra/dev/erikwujavaapp:test
PYTHON_IMAGE_URL ?= us-docker.pkg.dev/chronosphere-global-infra/dev/erikwupythonapp:test

# Default target
.PHONY: all
all: build-go build-java build-python

# Build Go Docker image
.PHONY: build-go
build-go:
	docker build -t $(GO_IMAGE_URL) ./go

# Build Java Docker image
.PHONY: build-java
build-java:
	docker build -t $(JAVA_IMAGE_URL) ./java/testapp

# Build Python Docker image
.PHONY: build-python
build-python:
	docker build -t $(PYTHON_IMAGE_URL) ./python

# Push Go Docker image
.PHONY: push-go
push-go:
	docker push $(GO_IMAGE_URL)

# Push Java Docker image
.PHONY: push-java
push-java:
	docker push $(JAVA_IMAGE_URL)

# Push Python Docker image
.PHONY: push-python
push-python:
	docker push $(PYTHON_IMAGE_URL)

# Build and push all images
.PHONY: build-and-push
build-and-push: build-go build-java build-python push-go push-java push-python

# Clean up dangling images
.PHONY: clean
clean:
	docker image prune -f

# Help target
.PHONY: help
help:
	@echo "Available targets:"
	@echo "  all            - Build all Docker images (Go, Java, Python) (default)"
	@echo "  build-go       - Build Go Docker image"
	@echo "  build-java     - Build Java Docker image"
	@echo "  build-python   - Build Python Docker image"
	@echo "  push-go        - Push Go Docker image to registry"
	@echo "  push-java      - Push Java Docker image to registry"
	@echo "  push-python    - Push Python Docker image to registry"
	@echo "  build-and-push - Build and push all images"
	@echo "  clean          - Remove dangling Docker images"
	@echo "  help           - Show this help message"
	@echo ""
	@echo "You can override image URLs with:"
	@echo "  make build-go GO_IMAGE_URL=<your-url>"
	@echo "  make build-java JAVA_IMAGE_URL=<your-url>"
	@echo "  make build-python PYTHON_IMAGE_URL=<your-url>"