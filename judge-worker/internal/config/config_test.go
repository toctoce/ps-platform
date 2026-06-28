package config

import (
	"testing"
)

func TestLoadSuccess(t *testing.T) {
	t.Setenv("BACKEND_BASE_URL", "http://localhost:8080")
	t.Setenv("JUDGE_WORKER_POLL_INTERVAL_MS", "1200")
	t.Setenv("JAVA_DOCKER_IMAGE", "eclipse-temurin:17-jdk")
	t.Setenv("JUDGE_WORKER_STALE_AFTER_SECONDS", "45")

	cfg, err := Load()
	if err != nil {
		t.Fatalf("Load() error = %v", err)
	}
	if cfg.BackendBaseURL != "http://localhost:8080" {
		t.Fatalf("backendBaseURL = %q, want %q", cfg.BackendBaseURL, "http://localhost:8080")
	}
	if cfg.PollInterval.Milliseconds() != 1200 {
		t.Fatalf("pollInterval = %d, want %d", cfg.PollInterval.Milliseconds(), 1200)
	}
	if cfg.JavaDockerImage != "eclipse-temurin:17-jdk" {
		t.Fatalf("javaDockerImage = %q, want %q", cfg.JavaDockerImage, "eclipse-temurin:17-jdk")
	}
	if cfg.StaleAfterSeconds != 45 {
		t.Fatalf("staleAfterSeconds = %d, want %d", cfg.StaleAfterSeconds, 45)
	}
}

func TestLoadMissingRequiredValues(t *testing.T) {
	t.Setenv("BACKEND_BASE_URL", "")
	t.Setenv("JUDGE_WORKER_POLL_INTERVAL_MS", "")
	t.Setenv("JAVA_DOCKER_IMAGE", "")

	if _, err := Load(); err == nil {
		t.Fatal("expected error for missing BACKEND_BASE_URL, got nil")
	}

	t.Setenv("BACKEND_BASE_URL", "http://localhost:8080")
	if _, err := Load(); err == nil {
		t.Fatal("expected error for missing JUDGE_WORKER_POLL_INTERVAL_MS, got nil")
	}
}

func TestLoadInvalidPollInterval(t *testing.T) {
	t.Setenv("BACKEND_BASE_URL", "http://localhost:8080")
	t.Setenv("JUDGE_WORKER_POLL_INTERVAL_MS", "abc")
	t.Setenv("JAVA_DOCKER_IMAGE", "eclipse-temurin:17-jdk")

	if _, err := Load(); err == nil {
		t.Fatal("expected error for invalid JUDGE_WORKER_POLL_INTERVAL_MS, got nil")
	}
}

func TestLoadReturnsErrorWhenStaleAfterSecondsMissing(t *testing.T) {
	t.Setenv("BACKEND_BASE_URL", "http://localhost:8080")
	t.Setenv("JUDGE_WORKER_POLL_INTERVAL_MS", "1200")
	t.Setenv("JAVA_DOCKER_IMAGE", "eclipse-temurin:17-jdk")

	if _, err := Load(); err == nil {
		t.Fatal("expected error for missing JUDGE_WORKER_STALE_AFTER_SECONDS, got nil")
	}
}

func TestLoadInvalidStaleAfterSeconds(t *testing.T) {
	t.Setenv("BACKEND_BASE_URL", "http://localhost:8080")
	t.Setenv("JUDGE_WORKER_POLL_INTERVAL_MS", "1200")
	t.Setenv("JAVA_DOCKER_IMAGE", "eclipse-temurin:17-jdk")
	t.Setenv("JUDGE_WORKER_STALE_AFTER_SECONDS", "0")

	if _, err := Load(); err == nil {
		t.Fatal("expected error for invalid JUDGE_WORKER_STALE_AFTER_SECONDS, got nil")
	}
}
