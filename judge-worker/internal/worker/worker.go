package worker

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"time"

	"ps-platform/judge-worker/internal/backend"
	"ps-platform/judge-worker/internal/sandbox"
)

// Worker는 backend에서 채점 작업을 가져와 executor로 처리하는 polling worker입니다.
type Worker struct {
	client       backendClient
	executor     sandbox.Executor
	pollInterval time.Duration
	staleAfter   int64
}

type backendClient interface {
	NextJob(ctx context.Context) (*backend.JudgeJobAssignment, error)
	CompleteJob(ctx context.Context, jobID int64, verdict string, judgeMessage string) error
	RequeueStaleJobs(ctx context.Context, staleAfterSeconds int64) error
}

// NewWorker는 backend client, sandbox executor, polling 간격을 조합해 Worker를 생성합니다.
func NewWorker(client *backend.Client, executor sandbox.Executor, pollInterval time.Duration, staleAfterSeconds int64) *Worker {
	return &Worker{
		client:       client,
		executor:     executor,
		pollInterval: pollInterval,
		staleAfter:   staleAfterSeconds,
	}
}

// Run은 context가 취소될 때까지 채점 작업 polling loop를 실행합니다.
func (w *Worker) Run(ctx context.Context) error {
	ticker := time.NewTicker(w.pollInterval)
	defer ticker.Stop()

	for {
		if w.staleAfter > 0 {
			if err := w.client.RequeueStaleJobs(ctx, w.staleAfter); err != nil {
				if errors.Is(err, context.Canceled) {
					return err
				}
				slog.Error("failed to requeue stale judge jobs", "error", err)
			}
		}

		processedAny := false

		for {
			processed, err := w.processOnce(ctx)
			if err != nil {
				slog.Error("failed to process judge job", "error", err)
				break
			}

			if !processed {
				break
			}

			processedAny = true
		}

		if processedAny {
			continue
		}

		select {
		case <-ctx.Done():
			return ctx.Err()
		case <-ticker.C:
		}
	}
}

// processOnce는 채점 작업 하나를 가져와 실행하고 결과를 backend에 보고합니다.
func (w *Worker) processOnce(ctx context.Context) (bool, error) {
	job, err := w.client.NextJob(ctx)
	if err != nil {
		return false, err
	}

	if job == nil {
		slog.Debug("no judge job available")
		return false, nil
	}

	slog.Info("judge job picked",
		"jobId", job.JobID,
		"submissionId", job.SubmissionID,
		"problemId", job.ProblemID,
		"language", job.Language,
	)

	execution, err := w.executor.Execute(ctx, *job)
	verdict := execution.Verdict
	judgeMessage := execution.Message
	if err != nil {
		slog.Error("sandbox execution failed", "jobId", job.JobID, "error", err)
		verdict = sandbox.VerdictRuntimeError
		judgeMessage = fmt.Sprintf("execution failed: %v", err)
	}

	if err := w.client.CompleteJob(ctx, job.JobID, verdict, judgeMessage); err != nil {
		return true, err
	}

	slog.Info("judge job completed", "jobId", job.JobID, "verdict", verdict)
	return true, nil
}
