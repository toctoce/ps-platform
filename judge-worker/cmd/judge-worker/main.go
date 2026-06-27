package main

import (
	"context"
	"log/slog"
	"os"
	"os/signal"
	"time"

	"ps-platform/judge-worker/internal/backend"
	"ps-platform/judge-worker/internal/sandbox"
	"ps-platform/judge-worker/internal/worker"
)

// main은 judge-worker 실행에 필요한 객체를 조립하고 worker loop를 시작합니다.
func main() {
	// BACKEND_BASE_URL이 없으면 로컬 backend 주소를 기본값으로 사용합니다.
	backendBaseURL := getenv("BACKEND_BASE_URL", "http://localhost:8080")
	pollInterval := 1 * time.Second

	// Ctrl+C 같은 종료 신호를 context 취소 신호로 변환합니다.
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt)
	defer stop()

	// backend client, sandbox executor, worker loop를 조립합니다.
	client := backend.NewClient(backendBaseURL)
	executor := sandbox.NewStubExecutor()
	judgeWorker := worker.NewWorker(client, executor, pollInterval)

	slog.Info("judge worker started", "backendBaseURL", backendBaseURL)

	if err := judgeWorker.Run(ctx); err != nil && err != context.Canceled {
		slog.Error("judge worker stopped with error", "error", err)
		os.Exit(1)
	}

	slog.Info("judge worker stopped")
}

// getenv는 환경변수 값이 비어 있으면 fallback 값을 반환합니다.
func getenv(key string, fallback string) string {
	value := os.Getenv(key)
	if value == "" {
		return fallback
	}
	return value
}
