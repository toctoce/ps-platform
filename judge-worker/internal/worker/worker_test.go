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
	executor := &fakeExecutor{verdict: sandbox.VerdictAccepted}
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
		executor:     &fakeExecutor{verdict: sandbox.VerdictAccepted},
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

type fakeBackendClient struct {
	mu            sync.Mutex
	jobs          []backend.JudgeJobAssignment
	nextErr       error
	completeErr   error
	nextJobCalls  chan time.Time
	calledNext    int
	completeCalls int
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

func (c *fakeBackendClient) CompleteJob(context.Context, int64, string) error {
	c.mu.Lock()
	defer c.mu.Unlock()
	c.completeCalls++
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
	verdict     string
	invocations int
	err         error
}

func (e *fakeExecutor) Execute(_ context.Context, job backend.JudgeJobAssignment) (string, error) {
	e.mu.Lock()
	defer e.mu.Unlock()
	e.invocations++

	return e.verdict, e.err
}

func waitNextCall(c chan time.Time, timeout time.Duration) (time.Time, error) {
	select {
	case t := <-c:
		return t, nil
	case <-time.After(timeout):
		return time.Time{}, errors.New("timeout waiting for NextJob call")
	}
}
