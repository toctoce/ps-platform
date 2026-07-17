FROM golang:1.26.4-alpine AS builder

WORKDIR /app

COPY go.mod ./
RUN go mod download

COPY . .

RUN CGO_ENABLED=0 GOOS=linux \
    go build \
    -trimpath \
    -ldflags="-s -w" \
    -o /out/judge-worker \
    ./cmd/judge-worker

FROM alpine:3.23 AS runtime

RUN apk add --no-cache \
    ca-certificates \
    docker-cli

WORKDIR /app

COPY --from=builder /out/judge-worker ./judge-worker

ENTRYPOINT ["./judge-worker"]
