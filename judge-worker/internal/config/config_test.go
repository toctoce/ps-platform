package config

import (
	"testing"
	"time"
)

func TestLoadSuccess(t *testing.T) {
	setRequiredEnvironment(t)

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
	if cfg.JavaDockerImage != "ps-platform/judge-java:17" {
		t.Fatalf("javaDockerImage = %q, want %q", cfg.JavaDockerImage, "ps-platform/judge-java:17")
	}
	if cfg.PythonDockerImage != "ps-platform/judge-python:3.12" {
		t.Fatalf("pythonDockerImage = %q, want %q", cfg.PythonDockerImage, "ps-platform/judge-python:3.12")
	}
	if cfg.CppDockerImage != "ps-platform/judge-cpp:17" {
		t.Fatalf("cppDockerImage = %q, want %q", cfg.CppDockerImage, "ps-platform/judge-cpp:17")
	}
	if cfg.CppCompileMemoryMb != 512 {
		t.Fatalf("cppCompileMemoryMb = %d, want %d", cfg.CppCompileMemoryMb, 512)
	}
	if cfg.CppCompileTimeout != 30*time.Second {
		t.Fatalf("cppCompileTimeout = %s, want %s", cfg.CppCompileTimeout, 30*time.Second)
	}
	if cfg.StaleAfterSeconds != 45 {
		t.Fatalf("staleAfterSeconds = %d, want %d", cfg.StaleAfterSeconds, 45)
	}
}

func TestLoadMissingRequiredValues(t *testing.T) {
	t.Setenv("BACKEND_BASE_URL", "")
	t.Setenv("JUDGE_WORKER_POLL_INTERVAL_MS", "")
	t.Setenv("JAVA_DOCKER_IMAGE", "")
	t.Setenv("PYTHON_DOCKER_IMAGE", "")
	t.Setenv("CPP_DOCKER_IMAGE", "")
	t.Setenv("CPP_COMPILE_MEMORY_MB", "")
	t.Setenv("CPP_COMPILE_TIMEOUT_MS", "")
	t.Setenv("JUDGE_WORKER_STALE_AFTER_SECONDS", "")

	if _, err := Load(); err == nil {
		t.Fatal("expected error for missing BACKEND_BASE_URL, got nil")
	}

	t.Setenv("BACKEND_BASE_URL", "http://localhost:8080")
	t.Setenv("JAVA_DOCKER_IMAGE", "ps-platform/judge-java:17")
	t.Setenv("PYTHON_DOCKER_IMAGE", "ps-platform/judge-python:3.12")
	t.Setenv("CPP_DOCKER_IMAGE", "ps-platform/judge-cpp:17")
	if _, err := Load(); err == nil {
		t.Fatal("expected error for missing JUDGE_WORKER_POLL_INTERVAL_MS, got nil")
	}
}

func TestLoadReturnsErrorWhenCppCompileMemoryMissing(t *testing.T) {
	setRequiredEnvironment(t)
	t.Setenv("CPP_COMPILE_MEMORY_MB", "")

	if _, err := Load(); err == nil {
		t.Fatal("expected error for missing CPP_COMPILE_MEMORY_MB, got nil")
	}
}

func TestLoadReturnsErrorWhenCppCompileTimeoutInvalid(t *testing.T) {
	setRequiredEnvironment(t)
	t.Setenv("CPP_COMPILE_TIMEOUT_MS", "0")

	if _, err := Load(); err == nil {
		t.Fatal("expected error for invalid CPP_COMPILE_TIMEOUT_MS, got nil")
	}
}

func TestLoadInvalidPollInterval(t *testing.T) {
	setRequiredEnvironment(t)
	t.Setenv("JUDGE_WORKER_POLL_INTERVAL_MS", "abc")

	if _, err := Load(); err == nil {
		t.Fatal("expected error for invalid JUDGE_WORKER_POLL_INTERVAL_MS, got nil")
	}
}

func TestLoadReturnsErrorWhenPythonDockerImageMissing(t *testing.T) {
	setRequiredEnvironment(t)
	t.Setenv("PYTHON_DOCKER_IMAGE", "")

	if _, err := Load(); err == nil {
		t.Fatal("expected error for missing PYTHON_DOCKER_IMAGE, got nil")
	}
}

func TestLoadReturnsErrorWhenCppDockerImageMissing(t *testing.T) {
	setRequiredEnvironment(t)
	t.Setenv("CPP_DOCKER_IMAGE", "")

	if _, err := Load(); err == nil {
		t.Fatal("expected error for missing CPP_DOCKER_IMAGE, got nil")
	}
}

func TestLoadReturnsErrorWhenStaleAfterSecondsMissing(t *testing.T) {
	setRequiredEnvironment(t)
	t.Setenv("JUDGE_WORKER_STALE_AFTER_SECONDS", "")

	if _, err := Load(); err == nil {
		t.Fatal("expected error for missing JUDGE_WORKER_STALE_AFTER_SECONDS, got nil")
	}
}

func TestLoadInvalidStaleAfterSeconds(t *testing.T) {
	setRequiredEnvironment(t)
	t.Setenv("JUDGE_WORKER_STALE_AFTER_SECONDS", "0")

	if _, err := Load(); err == nil {
		t.Fatal("expected error for invalid JUDGE_WORKER_STALE_AFTER_SECONDS, got nil")
	}
}

func setRequiredEnvironment(t *testing.T) {
	t.Helper()
	t.Setenv("BACKEND_BASE_URL", "http://localhost:8080")
	t.Setenv("JUDGE_WORKER_POLL_INTERVAL_MS", "1200")
	t.Setenv("JAVA_DOCKER_IMAGE", "ps-platform/judge-java:17")
	t.Setenv("PYTHON_DOCKER_IMAGE", "ps-platform/judge-python:3.12")
	t.Setenv("CPP_DOCKER_IMAGE", "ps-platform/judge-cpp:17")
	t.Setenv("CPP_COMPILE_MEMORY_MB", "512")
	t.Setenv("CPP_COMPILE_TIMEOUT_MS", "30000")
	t.Setenv("JUDGE_WORKER_STALE_AFTER_SECONDS", "45")
}
