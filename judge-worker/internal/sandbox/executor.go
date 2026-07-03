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
	"regexp"
	"strconv"
	"strings"
	"sync"
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
	executionSecondsKey   = "__JUDGE_EXECUTION_SECONDS__="
	memoryKbKey           = "__JUDGE_MEMORY_KB__="
)

// ExecutionResult는 채점 결과 판정 값과 실패 사유를 동시에 담습니다.
type ExecutionResult struct {
	Verdict     string
	Message     string
	CaseResults []backend.JudgeCaseResult
}

// Executor는 채점 작업을 sandbox에서 실행하고 verdict를 반환하는 실행기입니다.
type Executor interface {
	Execute(ctx context.Context, job backend.JudgeJobAssignment) (ExecutionResult, error)
}

// commandResult는 sandbox 내부 명령 실행 결과를 담는 값입니다.
type commandResult struct {
	stdout          string
	stderr          string
	exitCode        int
	executionTimeMs int
	memoryMb        int
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
		result.CaseResults = buildGlobalCaseResults(job.TestCases, result.Verdict, result.Message)
		return result, nil
	}

	timeLimit := determineTimeLimit(job.TimeLimitMs)
	caseResults := make([]backend.JudgeCaseResult, 0, len(job.TestCases))
	for index, tc := range job.TestCases {
		result, err := e.runTestCase(ctx, tempDir, tc, index+1, timeLimit, memoryLimitMb)
		if err != nil {
			return ExecutionResult{}, err
		}
		caseResults = append(caseResults, result.CaseResults...)
		if shouldStopAfterCaseFailure(job, result) {
			result.CaseResults = caseResults
			return result, nil
		}
	}

	verdict, message := aggregateCaseResults(caseResults)
	return ExecutionResult{Verdict: verdict, Message: message, CaseResults: caseResults}, nil
}

func shouldStopAfterCaseFailure(job backend.JudgeJobAssignment, result ExecutionResult) bool {
	return result.Verdict != "" && job.JobKind != "CODE_RUN"
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
	containerName := dockerContainerName("compile")
	args := []string{
		"run", "--rm", "-i",
		"--name", containerName,
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
	if isCommandTimedOut(compileCtx) {
		return ExecutionResult{
			Verdict: VerdictTimeLimitExceeded,
			Message: "compile timed out",
		}, nil
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
	containerName := dockerContainerName(fmt.Sprintf("case-%d", testCaseIndex))
	args := []string{
		"run", "--rm", "-i",
		"--name", containerName,
		"--network=none",
		"--memory", memoryLimit,
		"--memory-swap", memoryLimit,
		"-v", tempDir + ":" + containerWorkspaceDir,
		"-w", containerWorkspaceDir,
		e.image,
		"sh", "-c", javaRunCommand(),
	}

	result, err := e.runner.Run(runCtx, "docker", args, "", bytes.NewBufferString(testCase.Input))
	if err != nil {
		if isTimeout(ctx, runCtx, err) {
			return ExecutionResult{
				Verdict: VerdictTimeLimitExceeded,
				Message: fmt.Sprintf("time limit exceeded on test case %d", testCaseIndex),
				CaseResults: []backend.JudgeCaseResult{
					buildCaseResult(testCase, testCaseIndex, VerdictTimeLimitExceeded, "", fmt.Sprintf("time limit exceeded on test case %d", testCaseIndex), result.executionTimeMs, result.memoryMb),
				},
			}, nil
		}
		return ExecutionResult{}, err
	}
	if isCommandTimedOut(runCtx) {
		return ExecutionResult{
			Verdict: VerdictTimeLimitExceeded,
			Message: fmt.Sprintf("time limit exceeded on test case %d", testCaseIndex),
			CaseResults: []backend.JudgeCaseResult{
				buildCaseResult(testCase, testCaseIndex, VerdictTimeLimitExceeded, "", fmt.Sprintf("time limit exceeded on test case %d", testCaseIndex), result.executionTimeMs, result.memoryMb),
			},
		}, nil
	}

	if result.exitCode != 0 {
		if isMemoryLimitExceeded(result.exitCode, result.stderr, result.stdout) {
			message := fmt.Sprintf("memory limit exceeded on test case %d", testCaseIndex)
			return ExecutionResult{
				Verdict: VerdictMemoryLimitExceeded,
				Message: message,
				CaseResults: []backend.JudgeCaseResult{
					buildCaseResult(testCase, testCaseIndex, VerdictMemoryLimitExceeded, result.stdout, message, result.executionTimeMs, result.memoryMb),
				},
			}, nil
		}

		runtimeErrorMessage := firstNonEmpty("", result.stderr, result.stdout)
		if runtimeErrorMessage != "" {
			message := fmt.Sprintf("runtime error on test case %d: %s", testCaseIndex, runtimeErrorMessage)
			return ExecutionResult{
				Verdict: VerdictRuntimeError,
				Message: message,
				CaseResults: []backend.JudgeCaseResult{
					buildCaseResult(testCase, testCaseIndex, VerdictRuntimeError, result.stdout, message, result.executionTimeMs, result.memoryMb),
				},
			}, nil
		}

		message := fmt.Sprintf("runtime error on test case %d", testCaseIndex)
		return ExecutionResult{
			Verdict: VerdictRuntimeError,
			Message: message,
			CaseResults: []backend.JudgeCaseResult{
				buildCaseResult(testCase, testCaseIndex, VerdictRuntimeError, result.stdout, message, result.executionTimeMs, result.memoryMb),
			},
		}, nil
	}

	if judge.EvaluateOutput(testCase.ExpectedOutput, result.stdout) != judge.VerdictAccepted {
		normalizedExpected, normalizedActual := judge.NormalizeOutput(testCase.ExpectedOutput), judge.NormalizeOutput(result.stdout)
		message := buildWrongAnswerMessage(testCaseIndex, normalizedExpected, normalizedActual)
		return ExecutionResult{
			Verdict: VerdictWrongAnswer,
			Message: message,
			CaseResults: []backend.JudgeCaseResult{
				buildCaseResult(testCase, testCaseIndex, VerdictWrongAnswer, result.stdout, message, result.executionTimeMs, result.memoryMb),
			},
		}, nil
	}

	return ExecutionResult{
		CaseResults: []backend.JudgeCaseResult{
			buildCaseResult(testCase, testCaseIndex, VerdictAccepted, result.stdout, "", result.executionTimeMs, result.memoryMb),
		},
	}, nil
}

func buildGlobalCaseResults(testCases []backend.JudgeTestCase, verdict string, message string) []backend.JudgeCaseResult {
	results := make([]backend.JudgeCaseResult, 0, len(testCases))
	for index, testCase := range testCases {
		results = append(results, buildCaseResult(testCase, index+1, verdict, "", message, 0, 0))
	}
	return results
}

func buildCaseResult(
	testCase backend.JudgeTestCase,
	index int,
	verdict string,
	stdout string,
	message string,
	executionTimeMs int,
	memoryMb int,
) backend.JudgeCaseResult {
	return backend.JudgeCaseResult{
		Index:           index,
		Verdict:         verdict,
		Stdout:          stdout,
		ExpectedOutput:  testCase.ExpectedOutput,
		Message:         message,
		ExecutionTimeMs: executionTimeMs,
		MemoryMb:        memoryMb,
	}
}

func aggregateCaseResults(results []backend.JudgeCaseResult) (string, string) {
	for _, result := range results {
		if result.Verdict != VerdictAccepted {
			return result.Verdict, result.Message
		}
	}
	return VerdictAccepted, ""
}

func memoryLimitArgument(memoryLimitMb int) string {
	return fmt.Sprintf("%dm", memoryLimitMb)
}

func dockerContainerName(label string) string {
	sanitized := strings.NewReplacer("/", "-", "_", "-", " ", "-").Replace(label)
	return fmt.Sprintf("judge-worker-%d-%s", time.Now().UnixNano(), sanitized)
}

func javaRunCommand() string {
	return strings.Join([]string{
		"if [ -x /usr/bin/time ]; then",
		"/usr/bin/time -f '" + executionSecondsKey + "%e\\n" + memoryKbKey + "%M' -o /tmp/judge-metrics java Main;",
		"status=$?;",
		"cat /tmp/judge-metrics >&2 2>/dev/null;",
		"exit $status;",
		"fi;",
		"java Main",
	}, " ")
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

func isCommandTimedOut(commandCtx context.Context) bool {
	return errors.Is(commandCtx.Err(), context.DeadlineExceeded)
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

	containerName := dockerRunContainerName(command, args)
	stats := newContainerStats()
	statsCtx, cancelStats := context.WithCancel(ctx)
	defer cancelStats()

	startedAt := time.Now()
	if err := cmd.Start(); err != nil {
		return commandResult{}, err
	}
	if containerName != "" {
		go pollDockerMemory(statsCtx, containerName, stats)
	}

	err := cmd.Wait()
	cancelStats()
	cleanStderr, measuredExecutionTimeMs, measuredMemoryMb := parseAndStripExecutionMetrics(stderr.String())
	executionTimeMs := durationMillis(time.Since(startedAt))
	if measuredExecutionTimeMs > 0 {
		executionTimeMs = measuredExecutionTimeMs
	}
	memoryMb := maxInt(stats.memoryMb(), measuredMemoryMb)
	result := commandResult{
		stdout:          stdout.String(),
		stderr:          cleanStderr,
		executionTimeMs: executionTimeMs,
		memoryMb:        memoryMb,
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

func dockerRunContainerName(command string, args []string) string {
	if command != "docker" || len(args) == 0 || args[0] != "run" {
		return ""
	}
	for index := 1; index < len(args)-1; index++ {
		if args[index] == "--name" {
			return args[index+1]
		}
	}
	return ""
}

type containerStats struct {
	mu           sync.Mutex
	peakMemoryMb int
}

func newContainerStats() *containerStats {
	return &containerStats{}
}

func (s *containerStats) observeMemoryMb(value int) {
	if value <= 0 {
		return
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	if value > s.peakMemoryMb {
		s.peakMemoryMb = value
	}
}

func (s *containerStats) memoryMb() int {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.peakMemoryMb
}

func pollDockerMemory(ctx context.Context, containerName string, stats *containerStats) {
	ticker := time.NewTicker(20 * time.Millisecond)
	defer ticker.Stop()

	for {
		stats.observeMemoryMb(readDockerMemoryMb(ctx, containerName))
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
		}
	}
}

func readDockerMemoryMb(ctx context.Context, containerName string) int {
	statsCtx, cancel := context.WithTimeout(ctx, 100*time.Millisecond)
	defer cancel()

	output, err := exec.CommandContext(
		statsCtx,
		"docker",
		"stats",
		"--no-stream",
		"--format",
		"{{.MemUsage}}",
		containerName,
	).Output()
	if err != nil {
		return 0
	}
	return parseDockerMemoryMb(string(output))
}

var dockerMemoryUsagePattern = regexp.MustCompile(`(?i)([0-9]+(?:\.[0-9]+)?)\s*([kmgt]?i?b)`)

func parseDockerMemoryMb(value string) int {
	matches := dockerMemoryUsagePattern.FindStringSubmatch(value)
	if len(matches) != 3 {
		return 0
	}

	amount, err := strconv.ParseFloat(matches[1], 64)
	if err != nil {
		return 0
	}

	switch strings.ToLower(matches[2]) {
	case "b":
		amount = amount / 1024 / 1024
	case "kb", "kib":
		amount = amount / 1024
	case "mb", "mib":
	case "gb", "gib":
		amount = amount * 1024
	case "tb", "tib":
		amount = amount * 1024 * 1024
	default:
		return 0
	}

	if amount > 0 && amount < 1 {
		return 1
	}
	return int(amount + 0.5)
}

func parseAndStripExecutionMetrics(stderr string) (string, int, int) {
	lines := strings.Split(stderr, "\n")
	keptLines := make([]string, 0, len(lines))
	executionTimeMs := 0
	memoryMb := 0

	for _, line := range lines {
		switch {
		case strings.HasPrefix(line, executionSecondsKey):
			executionTimeMs = parseExecutionSecondsToMillis(strings.TrimPrefix(line, executionSecondsKey))
		case strings.HasPrefix(line, memoryKbKey):
			memoryMb = parseMemoryKbToMb(strings.TrimPrefix(line, memoryKbKey))
		default:
			keptLines = append(keptLines, line)
		}
	}

	return strings.TrimSpace(strings.Join(keptLines, "\n")), executionTimeMs, memoryMb
}

func parseExecutionSecondsToMillis(value string) int {
	seconds, err := strconv.ParseFloat(strings.TrimSpace(value), 64)
	if err != nil || seconds <= 0 {
		return 0
	}
	millis := int(seconds*1000 + 0.5)
	if millis <= 0 {
		return 1
	}
	return millis
}

func parseMemoryKbToMb(value string) int {
	kb, err := strconv.ParseFloat(strings.TrimSpace(value), 64)
	if err != nil || kb <= 0 {
		return 0
	}
	mb := kb / 1024
	if mb > 0 && mb < 1 {
		return 1
	}
	return int(mb + 0.5)
}

func durationMillis(duration time.Duration) int {
	millis := duration.Milliseconds()
	if millis <= 0 {
		return 1
	}
	return int(millis)
}

func maxInt(values ...int) int {
	maxValue := 0
	for _, value := range values {
		if value > maxValue {
			maxValue = value
		}
	}
	return maxValue
}
