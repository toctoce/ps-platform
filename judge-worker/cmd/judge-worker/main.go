package main

import (
	"context"
	"log/slog"
	"os"
	"os/signal"

	"ps-platform/judge-worker/internal/backend"
	"ps-platform/judge-worker/internal/config"
	"ps-platform/judge-worker/internal/sandbox"
	"ps-platform/judge-worker/internal/worker"
)

// main은 judge-worker 실행에 필요한 객체를 조립하고 worker loop를 시작합니다.
func main() {
	cfg, err := config.Load()
	if err != nil {
		slog.Error("invalid judge-worker configuration", "error", err)
		os.Exit(1)
	}

	// Ctrl+C 같은 종료 신호를 context 취소 신호로 변환합니다.
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt)
	defer stop()

	// backend client, sandbox executor, worker loop를 조립합니다.
	client := backend.NewClient(cfg.BackendBaseURL)
	executor := sandbox.NewJavaExecutor(cfg.JavaDockerImage)
	judgeWorker := worker.NewWorker(client, executor, cfg.PollInterval)

	slog.Info(
		"judge worker started",
		"backendBaseURL", cfg.BackendBaseURL,
		"pollInterval", cfg.PollInterval,
		"javaDockerImage", cfg.JavaDockerImage,
	)

	if err := judgeWorker.Run(ctx); err != nil && err != context.Canceled {
		slog.Error("judge worker stopped with error", "error", err)
		os.Exit(1)
	}

	slog.Info("judge worker stopped")
}
