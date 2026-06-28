package config

import (
	"fmt"
	"os"
	"strconv"
	"strings"
	"time"
)

// Config는 judge-worker 실행 시 필요한 환경설정 값입니다.
type Config struct {
	BackendBaseURL    string
	PollInterval      time.Duration
	JavaDockerImage   string
	StaleAfterSeconds int64
}

const (
	backendBaseURLKey     = "BACKEND_BASE_URL"
	pollIntervalMsKey     = "JUDGE_WORKER_POLL_INTERVAL_MS"
	javaDockerImageKey    = "JAVA_DOCKER_IMAGE"
	staleAfterSecondsKey  = "JUDGE_WORKER_STALE_AFTER_SECONDS"
	defaultStaleAfterSecs = 60
)

// Load는 운영에서 필요한 모든 값을 환경변수에서 로드합니다.
func Load() (Config, error) {
	cfg := Config{
		BackendBaseURL:    strings.TrimSpace(os.Getenv(backendBaseURLKey)),
		JavaDockerImage:   strings.TrimSpace(os.Getenv(javaDockerImageKey)),
		StaleAfterSeconds: defaultStaleAfterSecs,
	}
	if cfg.BackendBaseURL == "" {
		return Config{}, missingEnvError(backendBaseURLKey)
	}
	if cfg.JavaDockerImage == "" {
		return Config{}, missingEnvError(javaDockerImageKey)
	}

	pollIntervalMs, err := parsePositiveInt(pollIntervalMsKey, os.Getenv(pollIntervalMsKey))
	if err != nil {
		return Config{}, err
	}
	cfg.PollInterval = time.Duration(pollIntervalMs) * time.Millisecond

	staleAfterSeconds, err := parsePositiveIntOrDefault(staleAfterSecondsKey, os.Getenv(staleAfterSecondsKey), defaultStaleAfterSecs)
	if err != nil {
		return Config{}, err
	}
	cfg.StaleAfterSeconds = staleAfterSeconds

	return cfg, nil
}

func parsePositiveIntOrDefault(key string, rawValue string, defaultValue int64) (int64, error) {
	value := strings.TrimSpace(rawValue)
	if value == "" {
		return defaultValue, nil
	}
	return parsePositiveInt(key, value)
}

func parsePositiveInt(key string, rawValue string) (int64, error) {
	value := strings.TrimSpace(rawValue)
	if value == "" {
		return 0, missingEnvError(key)
	}

	parsed, err := strconv.ParseInt(value, 10, 64)
	if err != nil || parsed <= 0 {
		return 0, fmt.Errorf(
			"invalid value for %s: %q (must be a positive integer)",
			key,
			value,
		)
	}
	return parsed, nil
}

func missingEnvError(key string) error {
	return fmt.Errorf("missing required environment variable: %s", key)
}
