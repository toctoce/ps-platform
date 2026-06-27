package worker

import (
	"context"
	"log/slog"
	"time"

	"ps-platform/judge-worker/internal/backend"
	"ps-platform/judge-worker/internal/sandbox"
)

// Worker는 backend에서 채점 작업을 가져와 executor로 처리하는 polling worker입니다.
type Worker struct {
	client       *backend.Client
	executor     sandbox.Executor
	pollInterval time.Duration
}

// NewWorker는 backend client, sandbox executor, polling 간격을 조합해 Worker를 생성합니다.
func NewWorker(client *backend.Client, executor sandbox.Executor, pollInterval time.Duration) *Worker {
	return &Worker{
		client:       client,
		executor:     executor,
		pollInterval: pollInterval,
	}
}

// Run은 context가 취소될 때까지 채점 작업 polling loop를 실행합니다.
func (w *Worker) Run(ctx context.Context) error {
	ticker := time.NewTicker(w.pollInterval)
	defer ticker.Stop()

	for {
		if err := w.processOnce(ctx); err != nil {
			slog.Error("failed to process judge job", "error", err)
		}

		select {
		case <-ctx.Done():
			return ctx.Err()
		case <-ticker.C:
		}
	}
}

// processOnce는 채점 작업 하나를 가져와 실행하고 결과를 backend에 보고합니다.
func (w *Worker) processOnce(ctx context.Context) error {
	job, err := w.client.NextJob(ctx)
	if err != nil {
		return err
	}

	if job == nil {
		slog.Debug("no judge job available")
		return nil
	}

	slog.Info("judge job picked",
		"jobId", job.JobID,
		"submissionId", job.SubmissionID,
		"problemId", job.ProblemID,
		"language", job.Language,
	)

	verdict, err := w.executor.Execute(ctx, *job)
	if err != nil {
		slog.Error("sandbox execution failed", "jobId", job.JobID, "error", err)
		verdict = sandbox.VerdictRuntimeError
	}

	if err := w.client.CompleteJob(ctx, job.JobID, verdict); err != nil {
		return err
	}

	slog.Info("judge job completed", "jobId", job.JobID, "verdict", verdict)
	return nil
}
