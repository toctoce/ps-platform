package backend

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"
)

type roundTripFunc func(*http.Request) (*http.Response, error)

func (f roundTripFunc) RoundTrip(r *http.Request) (*http.Response, error) {
	return f(r)
}

func newTestClient(t *testing.T, handler http.HandlerFunc) *Client {
	t.Helper()

	return &Client{
		baseURL: "http://backend.local",
		httpClient: &http.Client{
			Transport: roundTripFunc(func(r *http.Request) (*http.Response, error) {
				recorder := httptest.NewRecorder()
				handler(recorder, r)
				return recorder.Result(), nil
			}),
		},
	}
}

func TestNextJobRequestsJobEndpointAndParsesResponse(t *testing.T) {
	client := newTestClient(t, func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost {
			t.Fatalf("method = %s, want POST", r.Method)
		}
		if r.URL.Path != "/api/judge-jobs/next" {
			t.Fatalf("path = %s, want /api/judge-jobs/next", r.URL.Path)
		}

		job := JudgeJobAssignment{
			JobID:         17,
			SubmissionID:  42,
			ProblemID:     9,
			Language:      "java",
			SourceCode:    "public class Main {}",
			TimeLimitMs:   1000,
			MemoryLimitMb: 128,
			TestCases: []JudgeTestCase{
				{Input: "1 2\n", ExpectedOutput: "3\n"},
			},
		}
		w.Header().Set("Content-Type", "application/json")
		if err := json.NewEncoder(w).Encode(job); err != nil {
			t.Fatalf("encode response: %v", err)
		}
	})

	job, err := client.NextJob(context.Background())
	if err != nil {
		t.Fatalf("NextJob error: %v", err)
	}
	if job == nil {
		t.Fatalf("job should not be nil")
	}

	if job.JobID != 17 || job.SourceCode != "public class Main {}" {
		t.Fatalf("job = %+v", job)
	}
}

func TestNextJobReturnsNilWhenNoContent(t *testing.T) {
	client := newTestClient(t, func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/api/judge-jobs/next" {
			t.Fatalf("path = %s", r.URL.Path)
		}
		w.WriteHeader(http.StatusNoContent)
	})

	job, err := client.NextJob(context.Background())
	if err != nil {
		t.Fatalf("NextJob error: %v", err)
	}
	if job != nil {
		t.Fatalf("job should be nil when no content")
	}
}

func TestNextJobReturnsErrorOnHTTPError(t *testing.T) {
	client := newTestClient(t, func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusInternalServerError)
	})

	if _, err := client.NextJob(context.Background()); err == nil {
		t.Fatalf("expected error")
	}
}

func TestNextJobReturnsErrorOnInvalidResponse(t *testing.T) {
	client := newTestClient(t, func(w http.ResponseWriter, r *http.Request) {
		_, _ = w.Write([]byte("{invalid-json"))
	})

	if _, err := client.NextJob(context.Background()); err == nil {
		t.Fatalf("expected JSON decode error")
	}
}

func TestCompleteJobPostsVerdictToCorrectEndpoint(t *testing.T) {
	received := struct {
		verdict      string
		judgeMessage string
	}{}

	client := newTestClient(t, func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost {
			t.Fatalf("method = %s, want POST", r.Method)
		}
		if r.URL.Path != "/api/judge-jobs/7/complete" {
			t.Fatalf("path = %s, want /api/judge-jobs/7/complete", r.URL.Path)
		}
		if got := r.Header.Get("Content-Type"); got != "application/json" {
			t.Fatalf("content-type = %s, want application/json", got)
		}

		var req CompleteRequest
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
			t.Fatalf("decode request: %v", err)
		}
		received.verdict = req.Verdict
		received.judgeMessage = req.JudgeMessage
		w.WriteHeader(http.StatusOK)
	})

	if err := client.CompleteJob(context.Background(), 7, "RUNTIME_ERROR", "runtime failed"); err != nil {
		t.Fatalf("CompleteJob error: %v", err)
	}
	if received.verdict != "RUNTIME_ERROR" {
		t.Fatalf("verdict = %s, want %s", received.verdict, "RUNTIME_ERROR")
	}
	if received.judgeMessage != "runtime failed" {
		t.Fatalf("judgeMessage = %s, want %s", received.judgeMessage, "runtime failed")
	}
}

func TestCompleteJobReturnsErrorOnHTTPError(t *testing.T) {
	client := newTestClient(t, func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusBadGateway)
	})

	if err := client.CompleteJob(context.Background(), 7, "ACCEPTED", ""); err == nil {
		t.Fatalf("expected error")
	}
}

func TestClientRespectsContextCancellation(t *testing.T) {
	client := &Client{
		baseURL: "http://127.0.0.1:1",
		httpClient: &http.Client{
			Transport: roundTripFunc(func(r *http.Request) (*http.Response, error) {
				return nil, r.Context().Err()
			}),
			Timeout: 50 * time.Millisecond,
		},
	}

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Millisecond)
	defer cancel()

	if _, err := client.NextJob(ctx); err == nil {
		t.Fatalf("expected context timeout/error")
	}
}
