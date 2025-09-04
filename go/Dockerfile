FROM us-docker.pkg.dev/chronosphere-global-infra/infra/golang-builder:1.24.5-alpine3.22  AS builder

WORKDIR /app

COPY go.mod go.sum ./
COPY main.go ./

RUN env GOOS=linux GOARCH=amd64 go build -o /promApp .

FROM us-docker.pkg.dev/chronosphere-global-infra/infra/service-base:958906df2529

WORKDIR /
COPY --from=builder /promApp .

ENTRYPOINT ["/promApp"]
