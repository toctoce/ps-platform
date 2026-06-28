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
	defaultMemoryLimitMb  = 128
	javaCompileTimeout    = 10 * time.Second
	javaSourceFile        = "Main.java"
	containerWorkspaceDir = "/workspace"
	judgeMessageMaxBytes  = 1024
)

// ExecutionResult는 채점 결과 판정 값과 실패 사유를 동시에 담습니다.
type ExecutionResult struct {
	Verdict string
	Message string
}

// Executor는 채점 작업을 sandbox에서 실행하고 verdict를 반환하는 실행기입니다.
type Executor interface {
	Execute(ctx context.Context, job backend.JudgeJobAssignment) (ExecutionResult, error)
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
	image  string
}

// NewJavaExecutor는 운영 환경에서 Java 실행기를 생성합니다.
func NewJavaExecutor(javaImage string) *JavaExecutor {
	image := strings.TrimSpace(javaImage)
	return NewJavaExecutorWithRunner(&dockerCommandRunner{}, image)
}

// NewJavaExecutorWithRunner는 테스트에서 fake runner를 주입하기 위한 생성자입니다.
func NewJavaExecutorWithRunner(runner commandRunner, image string) *JavaExecutor {
	image = strings.TrimSpace(image)

	return &JavaExecutor{
		runner: runner,
		image:  image,
	}
}

// Execute는 소스코드를 Main.java로 저장해 javac, java 실행을 수행한 뒤 verdict를 반환합니다.

func (e *JavaExecutor) Execute(ctx context.Context, job backend.JudgeJobAssignment) (ExecutionResult, error) {
	if strings.TrimSpace(job.SourceCode) == "" {
		return ExecutionResult{
			Verdict: VerdictCompileError,
			Message: "source code is empty",
		}, nil
	}

	if !strings.EqualFold(job.Language, "java") {
		return ExecutionResult{}, fmt.Errorf("unsupported language: %s", job.Language)
	}

	tempDir, err := os.MkdirTemp("", "judge-worker-java-*")
	if err != nil {
		return ExecutionResult{}, err
	}
	defer os.RemoveAll(tempDir) //nolint:all // 실행 종료 후 정리해야 해서 항상 제거.

	if err := os.WriteFile(filepath.Join(tempDir, javaSourceFile), []byte(job.SourceCode), 0o600); err != nil {
		return ExecutionResult{}, err
	}

	memoryLimitMb := determineMemoryLimitMb(job.MemoryLimitMb)

	if result, err := e.compile(ctx, tempDir, memoryLimitMb); err != nil {
		return ExecutionResult{}, err
	} else if result.Verdict != "" {
		return result, nil
	}

	timeLimit := determineTimeLimit(job.TimeLimitMs)
	for index, tc := range job.TestCases {
		result, err := e.runTestCase(ctx, tempDir, tc, index+1, timeLimit, memoryLimitMb)
		if err != nil {
			return ExecutionResult{}, err
		}
		if result.Verdict != "" {
			return result, nil
		}
	}

	return ExecutionResult{Verdict: VerdictAccepted}, nil
}

func determineTimeLimit(timeLimitMs int) time.Duration {
	if timeLimitMs <= 0 {
		return defaultTimeLimitMs * time.Millisecond
	}
	return time.Duration(timeLimitMs) * time.Millisecond
}

func determineMemoryLimitMb(memoryLimitMb int) int {
	if memoryLimitMb <= 0 {
		return defaultMemoryLimitMb
	}

	return memoryLimitMb
}

func (e *JavaExecutor) compile(ctx context.Context, tempDir string, memoryLimitMb int) (ExecutionResult, error) {
	compileCtx, cancel := context.WithTimeout(ctx, javaCompileTimeout)
	defer cancel()

	memoryLimit := memoryLimitArgument(memoryLimitMb)
	args := []string{
		"run", "--rm", "-i",
		"--network=none",
		"--memory", memoryLimit,
		"--memory-swap", memoryLimit,
		"-v", tempDir + ":" + containerWorkspaceDir,
		"-w", containerWorkspaceDir,
		e.image,
		"javac", javaSourceFile,
	}

	result, err := e.runner.Run(compileCtx, "docker", args, "", nil)
	if err != nil {
		if isTimeout(ctx, compileCtx, err) {
			return ExecutionResult{
				Verdict: VerdictTimeLimitExceeded,
				Message: "compile timed out",
			}, nil
		}
		return ExecutionResult{}, err
	}

	if result.exitCode != 0 {
		if isMemoryLimitExceeded(result.exitCode, result.stderr, result.stdout) {
			return ExecutionResult{
				Verdict: VerdictMemoryLimitExceeded,
				Message: "memory limit exceeded on compile",
			}, nil
		}

		return ExecutionResult{
			Verdict: VerdictCompileError,
			Message: firstNonEmpty("compile error", result.stderr, result.stdout),
		}, nil
	}

	return ExecutionResult{}, nil
}

func (e *JavaExecutor) runTestCase(
	ctx context.Context,
	tempDir string,
	testCase backend.JudgeTestCase,
	testCaseIndex int,
	timeLimit time.Duration,
	memoryLimitMb int,
) (ExecutionResult, error) {
	runCtx, cancel := context.WithTimeout(ctx, timeLimit)
	defer cancel()

	memoryLimit := memoryLimitArgument(memoryLimitMb)
	args := []string{
		"run", "--rm", "-i",
		"--network=none",
		"--memory", memoryLimit,
		"--memory-swap", memoryLimit,
		"-v", tempDir + ":" + containerWorkspaceDir,
		"-w", containerWorkspaceDir,
		e.image,
		"java", "Main",
	}

	result, err := e.runner.Run(runCtx, "docker", args, "", bytes.NewBufferString(testCase.Input))
	if err != nil {
		if isTimeout(ctx, runCtx, err) {
			return ExecutionResult{
				Verdict: VerdictTimeLimitExceeded,
				Message: fmt.Sprintf("time limit exceeded on test case %d", testCaseIndex),
			}, nil
		}
		return ExecutionResult{}, err
	}

	if result.exitCode != 0 {
		if isMemoryLimitExceeded(result.exitCode, result.stderr, result.stdout) {
			return ExecutionResult{
				Verdict: VerdictMemoryLimitExceeded,
				Message: fmt.Sprintf("memory limit exceeded on test case %d", testCaseIndex),
			}, nil
		}

		runtimeErrorMessage := firstNonEmpty("", result.stderr, result.stdout)
		if runtimeErrorMessage != "" {
			return ExecutionResult{
				Verdict: VerdictRuntimeError,
				Message: fmt.Sprintf("runtime error on test case %d: %s", testCaseIndex, runtimeErrorMessage),
			}, nil
		}

		return ExecutionResult{
			Verdict: VerdictRuntimeError,
			Message: fmt.Sprintf("runtime error on test case %d", testCaseIndex),
		}, nil
	}

	if judge.EvaluateOutput(testCase.ExpectedOutput, result.stdout) != judge.VerdictAccepted {
		normalizedExpected, normalizedActual := judge.NormalizeOutput(testCase.ExpectedOutput), judge.NormalizeOutput(result.stdout)
		return ExecutionResult{
			Verdict: VerdictWrongAnswer,
			Message: buildWrongAnswerMessage(testCaseIndex, normalizedExpected, normalizedActual),
		}, nil
	}

	return ExecutionResult{}, nil
}

func memoryLimitArgument(memoryLimitMb int) string {
	return fmt.Sprintf("%dm", memoryLimitMb)
}

func firstNonEmpty(fallback string, values ...string) string {
	for _, value := range values {
		trimmed := strings.TrimSpace(value)
		if trimmed != "" {
			return clipMessage(trimmed)
		}
	}
	if fallback == "" {
		return ""
	}
	return fallback
}

func buildWrongAnswerMessage(testCaseIndex int, expected string, actual string) string {
	return fmt.Sprintf(
		"wrong answer on test case %d: expected=%q, actual=%q",
		testCaseIndex,
		clipMessage(expected),
		clipMessage(actual),
	)
}

func clipMessage(value string) string {
	if len(value) <= judgeMessageMaxBytes {
		return value
	}

	return value[:judgeMessageMaxBytes] + "..."
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

func isMemoryLimitExceeded(exitCode int, stdout string, stderr string) bool {
	if exitCode == 137 {
		return true
	}

	combinedOutput := strings.ToLower(stdout) + "\n" + strings.ToLower(stderr)

	return strings.Contains(combinedOutput, "out of memory") ||
		strings.Contains(combinedOutput, "outofmemoryerror") ||
		strings.Contains(combinedOutput, "java.lang.outofmemoryerror") ||
		strings.Contains(combinedOutput, "java heap space") ||
		strings.Contains(combinedOutput, "metaspace") ||
		(strings.Contains(combinedOutput, "exceeded") &&
			strings.Contains(combinedOutput, "memory"))
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
