package sandbox

import (
	"bytes"
	"context"
	"errors"
	"fmt"
	"io"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"time"

	"ps-platform/judge-worker/internal/backend"
	"ps-platform/judge-worker/internal/judge"
)

// verdict 상수는 backend의 SubmissionVerdict enum 값과 반드시 일치해야 합니다.
const (
	VerdictAccepted            = "ACCEPTED"
	VerdictWrongAnswer         = "WRONG_ANSWER"
	VerdictTimeLimitExceeded   = "TIME_LIMIT_EXCEEDED"
	VerdictMemoryLimitExceeded = "MEMORY_LIMIT_EXCEEDED"
	VerdictRuntimeError        = "RUNTIME_ERROR"
	VerdictCompileError        = "COMPILE_ERROR"
)

const (
	defaultTimeLimitMs    = 1000
	javaCompileTimeout    = 10 * time.Second
	javaSourceFile        = "Main.java"
	javaImage             = "openjdk:17-jdk"
	containerWorkspaceDir = "/workspace"
)

// Executor는 채점 작업을 sandbox에서 실행하고 verdict를 반환하는 실행기입니다.
type Executor interface {
	Execute(ctx context.Context, job backend.JudgeJobAssignment) (string, error)
}

// commandResult는 sandbox 내부 명령 실행 결과를 담는 값입니다.
type commandResult struct {
	stdout   string
	stderr   string
	exitCode int
}

// commandRunner는 외부 실행기를 추상화해 sandbox 실행을 테스트에서 분리합니다.
type commandRunner interface {
	Run(ctx context.Context, command string, args []string, workDir string, stdin io.Reader) (commandResult, error)
}

// JavaExecutor는 Java 제출 코드를 sandbox에서 실행해 판정 결과를 계산합니다.
type JavaExecutor struct {
	runner commandRunner
}

// NewJavaExecutor는 운영 환경에서 Java 실행기를 생성합니다.
func NewJavaExecutor() *JavaExecutor {
	return NewJavaExecutorWithRunner(&dockerCommandRunner{})
}

// NewJavaExecutorWithRunner는 테스트에서 fake runner를 주입하기 위한 생성자입니다.
func NewJavaExecutorWithRunner(runner commandRunner) *JavaExecutor {
	return &JavaExecutor{
		runner: runner,
	}
}

// Execute는 소스코드를 Main.java로 저장해 javac, java 실행을 수행한 뒤 verdict를 반환합니다.
func (e *JavaExecutor) Execute(ctx context.Context, job backend.JudgeJobAssignment) (string, error) {
	if strings.TrimSpace(job.SourceCode) == "" {
		return VerdictCompileError, nil
	}

	if !strings.EqualFold(job.Language, "java") {
		return "", fmt.Errorf("unsupported language: %s", job.Language)
	}

	tempDir, err := os.MkdirTemp("", "judge-worker-java-*")
	if err != nil {
		return "", err
	}
	defer os.RemoveAll(tempDir) //nolint:all // 실행 종료 후 정리해야 해서 항상 제거.

	if err := os.WriteFile(filepath.Join(tempDir, javaSourceFile), []byte(job.SourceCode), 0o600); err != nil {
		return "", err
	}

	if verdict, err := e.compile(ctx, tempDir); err != nil {
		return "", err
	} else if verdict != "" {
		return verdict, nil
	}

	timeLimit := determineTimeLimit(job.TimeLimitMs)
	for _, tc := range job.TestCases {
		verdict, err := e.runTestCase(ctx, tempDir, tc, timeLimit)
		if err != nil {
			return "", err
		}
		if verdict != "" {
			return verdict, nil
		}
	}

	return VerdictAccepted, nil
}

func determineTimeLimit(timeLimitMs int) time.Duration {
	if timeLimitMs <= 0 {
		return defaultTimeLimitMs * time.Millisecond
	}
	return time.Duration(timeLimitMs) * time.Millisecond
}

func (e *JavaExecutor) compile(ctx context.Context, tempDir string) (string, error) {
	compileCtx, cancel := context.WithTimeout(ctx, javaCompileTimeout)
	defer cancel()

	args := []string{
		"run", "--rm", "-i",
		"--network=none",
		"-v", tempDir + ":" + containerWorkspaceDir,
		"-w", containerWorkspaceDir,
		javaImage,
		"javac", javaSourceFile,
	}

	result, err := e.runner.Run(compileCtx, "docker", args, "", nil)
	if err != nil {
		if isTimeout(ctx, compileCtx, err) {
			return VerdictTimeLimitExceeded, nil
		}
		return "", err
	}

	if result.exitCode != 0 {
		return VerdictCompileError, nil
	}

	return "", nil
}

func (e *JavaExecutor) runTestCase(
	ctx context.Context,
	tempDir string,
	testCase backend.JudgeTestCase,
	timeLimit time.Duration,
) (string, error) {
	runCtx, cancel := context.WithTimeout(ctx, timeLimit)
	defer cancel()

	args := []string{
		"run", "--rm", "-i",
		"--network=none",
		"-v", tempDir + ":" + containerWorkspaceDir,
		"-w", containerWorkspaceDir,
		javaImage,
		"java", "Main",
	}

	result, err := e.runner.Run(runCtx, "docker", args, "", bytes.NewBufferString(testCase.Input))
	if err != nil {
		if isTimeout(ctx, runCtx, err) {
			return VerdictTimeLimitExceeded, nil
		}
		return "", err
	}

	if result.exitCode != 0 {
		return VerdictRuntimeError, nil
	}

	if judge.EvaluateOutput(testCase.ExpectedOutput, result.stdout) != judge.VerdictAccepted {
		return VerdictWrongAnswer, nil
	}

	return "", nil
}

func isTimeout(parentCtx context.Context, commandCtx context.Context, err error) bool {
	if errors.Is(parentCtx.Err(), context.DeadlineExceeded) {
		return true
	}
	if errors.Is(commandCtx.Err(), context.DeadlineExceeded) {
		return true
	}
	if errors.Is(err, context.DeadlineExceeded) {
		return true
	}
	return false
}

// dockerCommandRunner는 docker CLI를 호출해 격리된 컨테이너에서 명령을 실행합니다.
type dockerCommandRunner struct{}

func (r *dockerCommandRunner) Run(ctx context.Context, command string, args []string, workDir string, stdin io.Reader) (commandResult, error) {
	cmd := exec.CommandContext(ctx, command, args...)
	if workDir != "" {
		cmd.Dir = workDir
	}
	cmd.Stdin = stdin

	var stdout, stderr strings.Builder
	cmd.Stdout = &stdout
	cmd.Stderr = &stderr

	err := cmd.Run()
	result := commandResult{
		stdout: stdout.String(),
		stderr: stderr.String(),
	}

	var exitErr *exec.ExitError
	if errors.As(err, &exitErr) {
		result.exitCode = exitErr.ExitCode()
		return result, nil
	}
	if err != nil {
		return commandResult{}, err
	}

	return result, nil
}
