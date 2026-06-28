package worker

import (
	"context"
	"errors"
	"sync"
	"testing"
	"time"

	"ps-platform/judge-worker/internal/backend"
	"ps-platform/judge-worker/internal/sandbox"
)

func TestRunProcessesAvailableJobsBackToBack(t *testing.T) {
	client := &fakeBackendClient{
		jobs: []backend.JudgeJobAssignment{
			{JobID: 101},
			{JobID: 102},
		},
		nextJobCalls: make(chan time.Time, 4),
	}
	executor := &fakeExecutor{
		execution: sandbox.ExecutionResult{
			Verdict: sandbox.VerdictAccepted,
		},
	}
	w := &Worker{
		client:       client,
		executor:     executor,
		pollInterval: 1 * time.Second,
	}

	ctx, cancel := context.WithCancel(context.Background())
	done := make(chan struct{})

	go func() {
		_ = w.Run(ctx)
		close(done)
	}()

	firstCall, err := waitNextCall(client.nextJobCalls, 1*time.Second)
	if err != nil {
		cancel()
		<-done
		t.Fatalf("first NextJob call: %v", err)
	}

	secondCall, err := waitNextCall(client.nextJobCalls, 100*time.Millisecond)
	if err != nil {
		cancel()
		<-done
		t.Fatalf("second NextJob call: %v", err)
	}

	if elapsed := secondCall.Sub(firstCall); elapsed >= 100*time.Millisecond {
		cancel()
		<-done
		t.Fatalf("second job was not processed back-to-back (elapsed=%s)", elapsed)
	}

	cancel()
	<-done

	if executor.invocations != 2 {
		t.Fatalf("expected 2 executions, got %d", executor.invocations)
	}
	if client.completeCalls != 2 {
		t.Fatalf("expected 2 complete calls, got %d", client.completeCalls)
	}
}

func TestProcessOnceReturnsFalseWhenNoJob(t *testing.T) {
	client := &fakeBackendClient{
		jobs:         []backend.JudgeJobAssignment{},
		nextJobCalls: make(chan time.Time, 1),
	}
	w := &Worker{
		client:       client,
		executor:     &fakeExecutor{execution: sandbox.ExecutionResult{Verdict: sandbox.VerdictAccepted}},
		pollInterval: time.Second,
	}

	processed, err := w.processOnce(context.Background())
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if processed {
		t.Fatalf("expected no job to be processed")
	}
}

func TestProcessOnceReturnsFalseWhenNextJobErrors(t *testing.T) {
	client := &fakeBackendClient{
		nextErr: errors.New("backend unavailable"),
	}
	w := &Worker{
		client:   client,
		executor: &fakeExecutor{execution: sandbox.ExecutionResult{Verdict: sandbox.VerdictAccepted}},
	}

	processed, err := w.processOnce(context.Background())
	if err == nil {
		t.Fatalf("expected error")
	}
	if processed {
		t.Fatalf("expected no job to be processed")
	}
}

func TestProcessOnceCompletesRuntimeErrorOnExecutorError(t *testing.T) {
	client := &fakeBackendClient{
		jobs: []backend.JudgeJobAssignment{
			{JobID: 201},
		},
	}
	execErr := errors.New("executor panic")
	executor := &fakeExecutor{
		err: execErr,
		execution: sandbox.ExecutionResult{
			Verdict: sandbox.VerdictAccepted,
		},
	}
	w := &Worker{
		client:       client,
		executor:     executor,
		pollInterval: time.Second,
	}

	processed, err := w.processOnce(context.Background())
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if !processed {
		t.Fatalf("expected job to be processed")
	}
	if got := client.lastVerdict; got != sandbox.VerdictRuntimeError {
		t.Fatalf("verdict = %s, want %s", got, sandbox.VerdictRuntimeError)
	}
	if client.lastJudgeMessage == "" {
		t.Fatalf("expected judge message on executor error")
	}
	if client.lastJobID != 201 {
		t.Fatalf("job id = %d, want %d", client.lastJobID, 201)
	}
}

func TestRunExitsAfterContextCancelWhenNoJobs(t *testing.T) {
	client := &fakeBackendClient{
		jobs:         []backend.JudgeJobAssignment{},
		nextJobCalls: make(chan time.Time, 1),
	}
	w := &Worker{
		client: client,
		executor: &fakeExecutor{
			execution: sandbox.ExecutionResult{
				Verdict: sandbox.VerdictAccepted,
			},
		},
		pollInterval: 10 * time.Minute,
	}

	ctx, cancel := context.WithCancel(context.Background())
	done := make(chan struct{})

	go func() {
		_ = w.Run(ctx)
		close(done)
	}()

	_, err := waitNextCall(client.nextJobCalls, 1*time.Second)
	if err != nil {
		t.Fatalf("first NextJob call: %v", err)
	}
	cancel()
	<-done

	client.mu.Lock()
	calls := client.calledNext
	client.mu.Unlock()
	if calls != 1 {
		t.Fatalf("next job called %d times, want 1", calls)
	}
}

func TestProcessOnceReturnsErrorWhenCompleteJobFails(t *testing.T) {
	client := &fakeBackendClient{
		jobs: []backend.JudgeJobAssignment{
			{JobID: 301},
		},
		completeErr: errors.New("complete failed"),
	}
	executor := &fakeExecutor{
		execution: sandbox.ExecutionResult{Verdict: sandbox.VerdictAccepted},
	}
	w := &Worker{
		client:       client,
		executor:     executor,
		pollInterval: time.Second,
	}

	processed, err := w.processOnce(context.Background())
	if !processed {
		t.Fatalf("expected processed")
	}
	if err == nil {
		t.Fatalf("expected complete error")
	}
}

type fakeBackendClient struct {
	mu               sync.Mutex
	jobs             []backend.JudgeJobAssignment
	nextErr          error
	completeErr      error
	lastJobID        int64
	lastVerdict      string
	lastJudgeMessage string
	nextJobCalls     chan time.Time
	calledNext       int
	completeCalls    int
}

func (c *fakeBackendClient) NextJob(context.Context) (*backend.JudgeJobAssignment, error) {
	c.mu.Lock()
	c.calledNext++
	callTime := time.Now()
	c.mu.Unlock()

	c.recordCall(callTime)

	if c.nextErr != nil {
		return nil, c.nextErr
	}
	c.mu.Lock()
	defer c.mu.Unlock()
	if len(c.jobs) == 0 {
		return nil, nil
	}

	job := c.jobs[0]
	c.jobs = c.jobs[1:]
	return &job, nil
}

func (c *fakeBackendClient) CompleteJob(_ context.Context, jobID int64, verdict string, judgeMessage string) error {
	c.mu.Lock()
	defer c.mu.Unlock()
	c.completeCalls++
	c.lastJobID = jobID
	c.lastVerdict = verdict
	c.lastJudgeMessage = judgeMessage
	if c.completeErr != nil {
		return c.completeErr
	}
	return nil
}

func (c *fakeBackendClient) recordCall(t time.Time) {
	if c.nextJobCalls != nil {
		c.nextJobCalls <- t
	}
}

type fakeExecutor struct {
	mu          sync.Mutex
	execution   sandbox.ExecutionResult
	invocations int
	err         error
}

func (e *fakeExecutor) Execute(_ context.Context, job backend.JudgeJobAssignment) (sandbox.ExecutionResult, error) {
	e.mu.Lock()
	defer e.mu.Unlock()
	e.invocations++

	return e.execution, e.err
}

func waitNextCall(c chan time.Time, timeout time.Duration) (time.Time, error) {
	select {
	case t := <-c:
		return t, nil
	case <-time.After(timeout):
		return time.Time{}, errors.New("timeout waiting for NextJob call")
	}
}
