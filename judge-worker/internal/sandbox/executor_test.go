package sandbox

import (
	"context"
	"io"
	"strings"
	"testing"
	"time"

	"ps-platform/judge-worker/internal/backend"
)

const testJavaDockerImage = "eclipse-temurin:17-jdk"

func TestJavaExecutorAcceptsWhenAllTestCasesPass(t *testing.T) {
	executor := NewJavaExecutorWithRunner(&fakeCommandRunner{
		responses: []commandResponse{
			{result: commandResult{exitCode: 0}},
			{result: commandResult{exitCode: 0, stdout: "3\n"}},
			{result: commandResult{exitCode: 0, stdout: "11\n"}},
		},
	}, testJavaDockerImage)

	result, err := executor.Execute(context.Background(), backend.JudgeJobAssignment{
		Language: "java",
		SourceCode: "public class Main {\n" +
			"    public static void main(String[] args) {\n" +
			"        java.util.Scanner sc = new java.util.Scanner(System.in);\n" +
			"        int a = sc.nextInt();\n" +
			"        int b = sc.nextInt();\n" +
			"        System.out.println(a + b);\n" +
			"    }\n" +
			"}",
		TimeLimitMs: 1000,
		TestCases: []backend.JudgeTestCase{
			{Input: "1 2\n", ExpectedOutput: "3\n"},
			{Input: "5 6\n", ExpectedOutput: "11\n"},
		},
	})

	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if result.Verdict != VerdictAccepted {
		t.Fatalf("verdict = %s, want %s", result.Verdict, VerdictAccepted)
	}
	if result.Message != "" {
		t.Fatalf("judge message should be empty, got %q", result.Message)
	}

	if len(executor.runner.(*fakeCommandRunner).calls) != 3 {
		t.Fatalf("expected 3 command invocations, got %d", len(executor.runner.(*fakeCommandRunner).calls))
	}

	calls := executor.runner.(*fakeCommandRunner).calls
	if !containsArg(calls[0].args, "--memory", "128m") {
		t.Fatalf("compile command should apply default memory limit, got args: %v", calls[0].args)
	}
	if !containsArg(calls[1].args, "--memory", "128m") {
		t.Fatalf("run command should apply default memory limit, got args: %v", calls[1].args)
	}
}

func TestJavaExecutorReturnsCompileErrorOnCompileFailure(t *testing.T) {
	executor := NewJavaExecutorWithRunner(&fakeCommandRunner{
		responses: []commandResponse{
			{result: commandResult{exitCode: 1, stderr: "Compilation failed"}},
		},
	}, testJavaDockerImage)

	result, err := executor.Execute(context.Background(), backend.JudgeJobAssignment{
		Language:    "java",
		SourceCode:  "invalid source",
		TimeLimitMs: 1000,
		TestCases:   []backend.JudgeTestCase{},
	})

	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if result.Verdict != VerdictCompileError {
		t.Fatalf("verdict = %s, want %s", result.Verdict, VerdictCompileError)
	}
	if result.Message != "Compilation failed" {
		t.Fatalf("judge message = %q, want %q", result.Message, "Compilation failed")
	}
}

func TestJavaExecutorReturnsMemoryLimitExceededWhenCompileKilledByMemoryCap(t *testing.T) {
	executor := NewJavaExecutorWithRunner(&fakeCommandRunner{
		responses: []commandResponse{
			{result: commandResult{exitCode: 137, stderr: "Killed"}},
		},
	}, testJavaDockerImage)

	result, err := executor.Execute(context.Background(), backend.JudgeJobAssignment{
		Language:      "java",
		SourceCode:    "public class Main {}",
		MemoryLimitMb: 32,
		TimeLimitMs:   1000,
		TestCases:     []backend.JudgeTestCase{},
	})

	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if result.Verdict != VerdictMemoryLimitExceeded {
		t.Fatalf("verdict = %s, want %s, message = %q", result.Verdict, VerdictMemoryLimitExceeded, result.Message)
	}
	if result.Message != "memory limit exceeded on compile" {
		t.Fatalf("judge message = %q, want %q", result.Message, "memory limit exceeded on compile")
	}

	calls := executor.runner.(*fakeCommandRunner).calls
	if !containsArg(calls[0].args, "--memory", "32m") {
		t.Fatalf("compile command should use configured memory limit, got args: %v", calls[0].args)
	}
}

func TestJavaExecutorReturnsWrongAnswerWhenOutputDiffers(t *testing.T) {
	executor := NewJavaExecutorWithRunner(&fakeCommandRunner{
		responses: []commandResponse{
			{result: commandResult{exitCode: 0}},
			{result: commandResult{exitCode: 0, stdout: "4\n"}},
		},
	}, testJavaDockerImage)

	result, err := executor.Execute(context.Background(), backend.JudgeJobAssignment{
		Language:   "java",
		SourceCode: "public class Main {}",
		TestCases: []backend.JudgeTestCase{
			{Input: "2 2\n", ExpectedOutput: "5\n"},
		},
	})

	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if result.Verdict != VerdictWrongAnswer {
		t.Fatalf("verdict = %s, want %s", result.Verdict, VerdictWrongAnswer)
	}
	if result.Message == "" {
		t.Fatalf("judge message should be set for wrong answers")
	}
}

func TestJavaExecutorReturnsMemoryLimitExceededWhenExecutionKilledByMemoryCap(t *testing.T) {
	executor := NewJavaExecutorWithRunner(&fakeCommandRunner{
		responses: []commandResponse{
			{result: commandResult{exitCode: 0}},
			{result: commandResult{exitCode: 137, stderr: "Killed"}},
		},
	}, testJavaDockerImage)

	result, err := executor.Execute(context.Background(), backend.JudgeJobAssignment{
		Language:      "java",
		SourceCode:    "public class Main {}",
		MemoryLimitMb: 64,
		TimeLimitMs:   1000,
		TestCases: []backend.JudgeTestCase{
			{Input: "1 2\n", ExpectedOutput: "3\n"},
		},
	})

	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if result.Verdict != VerdictMemoryLimitExceeded {
		t.Fatalf("verdict = %s, want %s, message = %q", result.Verdict, VerdictMemoryLimitExceeded, result.Message)
	}
	if result.Message != "memory limit exceeded on test case 1" {
		t.Fatalf("judge message = %q, want %q", result.Message, "memory limit exceeded on test case 1")
	}
}

func TestJavaExecutorReturnsMemoryLimitExceededWhenExecutionHitsJavaOutOfMemory(t *testing.T) {
	executor := NewJavaExecutorWithRunner(&fakeCommandRunner{
		responses: []commandResponse{
			{result: commandResult{exitCode: 0}},
			{result: commandResult{exitCode: 1, stderr: "Exception in thread \"main\" java.lang.OutOfMemoryError: Java heap space"}},
		},
	}, testJavaDockerImage)

	result, err := executor.Execute(context.Background(), backend.JudgeJobAssignment{
		Language:   "java",
		SourceCode: "public class Main {}",
		TestCases: []backend.JudgeTestCase{
			{Input: "1 2\n", ExpectedOutput: "3\n"},
		},
	})

	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if result.Verdict != VerdictMemoryLimitExceeded {
		t.Fatalf("verdict = %s, want %s, message = %q", result.Verdict, VerdictMemoryLimitExceeded, result.Message)
	}
	if result.Message != "memory limit exceeded on test case 1" {
		t.Fatalf("judge message = %q, want %q", result.Message, "memory limit exceeded on test case 1")
	}
}

func TestJavaExecutorReturnsRuntimeErrorOnNonZeroExit(t *testing.T) {
	executor := NewJavaExecutorWithRunner(&fakeCommandRunner{
		responses: []commandResponse{
			{result: commandResult{exitCode: 0}},
			{result: commandResult{exitCode: 1, stderr: "RuntimeError"}},
		},
	}, testJavaDockerImage)

	result, err := executor.Execute(context.Background(), backend.JudgeJobAssignment{
		Language:   "java",
		SourceCode: "public class Main {}",
		TestCases: []backend.JudgeTestCase{
			{Input: "2 2\n", ExpectedOutput: "4\n"},
		},
	})

	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if result.Verdict != VerdictRuntimeError {
		t.Fatalf("verdict = %s, want %s", result.Verdict, VerdictRuntimeError)
	}
	if result.Message != "runtime error on test case 1: RuntimeError" {
		t.Fatalf("judge message = %q, want %q", result.Message, "runtime error on test case 1: RuntimeError")
	}
}

func TestJavaExecutorReturnsTimeLimitExceededWhenCompileContextTimesOutWithoutError(t *testing.T) {
	executor := NewJavaExecutorWithRunner(&timeoutAwareRunner{result: commandResult{exitCode: 137}}, testJavaDockerImage)

	ctx, cancel := context.WithTimeout(context.Background(), time.Millisecond)
	defer cancel()

	result, err := executor.Execute(ctx, backend.JudgeJobAssignment{
		Language:   "java",
		SourceCode: "public class Main { public static void main(String[] args) {} }",
		TestCases:  []backend.JudgeTestCase{},
	})

	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if result.Verdict != VerdictTimeLimitExceeded {
		t.Fatalf("verdict = %s, want %s, message = %q", result.Verdict, VerdictTimeLimitExceeded, result.Message)
	}
	if result.Message != "compile timed out" {
		t.Fatalf("judge message = %q, want %q", result.Message, "compile timed out")
	}
}

func TestJavaExecutorReturnsTimeLimitExceededWhenRunContextTimesOutWithoutError(t *testing.T) {
	executor := NewJavaExecutorWithRunner(&timeoutAwareRunner{result: commandResult{exitCode: 143}}, testJavaDockerImage)

	result, err := executor.runTestCase(
		context.Background(),
		"/tmp",
		backend.JudgeTestCase{Input: "1 2\n", ExpectedOutput: "3\n"},
		1,
		time.Millisecond,
		128,
	)

	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if result.Verdict != VerdictTimeLimitExceeded {
		t.Fatalf("verdict = %s, want %s, message = %q", result.Verdict, VerdictTimeLimitExceeded, result.Message)
	}
	if result.Message != "time limit exceeded on test case 1" {
		t.Fatalf("judge message = %q, want %q", result.Message, "time limit exceeded on test case 1")
	}
}

func TestJavaExecutorReturnsRuntimeErrorWithoutDetailWhenOutputMissing(t *testing.T) {
	executor := NewJavaExecutorWithRunner(&fakeCommandRunner{
		responses: []commandResponse{
			{result: commandResult{exitCode: 0}},
			{result: commandResult{exitCode: 1}},
		},
	}, testJavaDockerImage)

	result, err := executor.Execute(context.Background(), backend.JudgeJobAssignment{
		Language:   "java",
		SourceCode: "public class Main {}",
		TestCases: []backend.JudgeTestCase{
			{Input: "1 2\n", ExpectedOutput: "3\n"},
		},
	})

	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if result.Verdict != VerdictRuntimeError {
		t.Fatalf("verdict = %s, want %s, message = %q", result.Verdict, VerdictRuntimeError, result.Message)
	}
	if result.Message != "runtime error on test case 1" {
		t.Fatalf("judge message = %q, want %q", result.Message, "runtime error on test case 1")
	}
}

func TestIsMemoryLimitExceededByJavaOutOfMemoryOutput(t *testing.T) {
	if !isMemoryLimitExceeded(1, "", "Exception in thread \"main\" java.lang.OutOfMemoryError: Java heap space") {
		t.Fatalf("expected Java OutOfMemoryError output to be detected as memory limit exceeded")
	}
	if !isMemoryLimitExceeded(1, "Exception in thread \"main\" java.lang.OutOfMemoryError: Java heap space", "") {
		t.Fatalf("expected Java OutOfMemoryError output in stdout to be detected as memory limit exceeded")
	}
}

func TestRunTestCaseReturnsMemoryLimitExceededOnJavaOutOfMemoryOutput(t *testing.T) {
	executor := NewJavaExecutorWithRunner(&fakeCommandRunner{
		responses: []commandResponse{
			{result: commandResult{exitCode: 1, stderr: "Exception in thread \"main\" java.lang.OutOfMemoryError: Java heap space"}},
		},
	}, testJavaDockerImage)

	result, err := executor.runTestCase(
		context.Background(),
		"/tmp",
		backend.JudgeTestCase{Input: "1 2\n", ExpectedOutput: "3\n"},
		1,
		time.Second,
		128,
	)

	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if result.Verdict != VerdictMemoryLimitExceeded {
		t.Fatalf("verdict = %s, want %s, message = %q", result.Verdict, VerdictMemoryLimitExceeded, result.Message)
	}
}

func TestJavaExecutorReturnsTimeLimitExceededOnCompileTimeout(t *testing.T) {
	timeoutErr := context.DeadlineExceeded
	executor := NewJavaExecutorWithRunner(&fakeCommandRunner{
		errors: []error{timeoutErr},
	}, testJavaDockerImage)

	result, err := executor.Execute(context.Background(), backend.JudgeJobAssignment{
		Language:   "java",
		SourceCode: "public class Main {}",
		TestCases:  []backend.JudgeTestCase{},
	})

	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if result.Verdict != VerdictTimeLimitExceeded {
		t.Fatalf("verdict = %s, want %s, message = %q", result.Verdict, VerdictTimeLimitExceeded, result.Message)
	}
	if result.Message != "compile timed out" {
		t.Fatalf("judge message = %q, want %q", result.Message, "compile timed out")
	}
}

func TestJavaExecutorReturnsTimeLimitExceededOnTimedOutRun(t *testing.T) {
	timeoutErr := context.DeadlineExceeded
	executor := NewJavaExecutorWithRunner(&fakeCommandRunner{
		responses: []commandResponse{
			{result: commandResult{exitCode: 0}},
		},
		errors: []error{
			nil,
			timeoutErr,
		},
	}, testJavaDockerImage)

	result, err := executor.Execute(context.Background(), backend.JudgeJobAssignment{
		Language:   "java",
		SourceCode: "public class Main {}",
		TestCases: []backend.JudgeTestCase{
			{Input: "while(true){}\n", ExpectedOutput: ""},
		},
	})

	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if result.Verdict != VerdictTimeLimitExceeded {
		t.Fatalf("verdict = %s, want %s", result.Verdict, VerdictTimeLimitExceeded)
	}
	if result.Message != "time limit exceeded on test case 1" {
		t.Fatalf("judge message = %q, want %q", result.Message, "time limit exceeded on test case 1")
	}
}

func TestJavaExecutorReturnsErrorOnUnsupportedLanguage(t *testing.T) {
	executor := NewJavaExecutorWithRunner(&fakeCommandRunner{}, testJavaDockerImage)
	_, err := executor.Execute(context.Background(), backend.JudgeJobAssignment{
		Language:   "python",
		SourceCode: "print(1)",
	})
	if err == nil {
		t.Fatalf("expected unsupported language error")
	}
	if !strings.Contains(err.Error(), "unsupported language") {
		t.Fatalf("error = %v", err)
	}
}

type commandResponse struct {
	result commandResult
	err    error
}

type fakeCommandRunner struct {
	responses []commandResponse
	errors    []error
	calls     []fakeCommandCall
}

type fakeCommandCall struct {
	command string
	args    []string
	input   string
}

func containsArg(args []string, key string, expected string) bool {
	for i := 0; i < len(args); i++ {
		if args[i] != key {
			continue
		}
		if i+1 >= len(args) {
			return false
		}
		return args[i+1] == expected
	}
	return false
}

func (r *fakeCommandRunner) Run(_ context.Context, command string, args []string, _ string, stdin io.Reader) (commandResult, error) {
	callIndex := len(r.calls)
	r.calls = append(r.calls, fakeCommandCall{
		command: command,
		args:    append([]string{}, args...),
	})
	if stdin != nil {
		inputBytes, _ := io.ReadAll(stdin)
		r.calls[callIndex].input = string(inputBytes)
	}

	var response commandResponse
	if callIndex < len(r.responses) {
		response = r.responses[callIndex]
	} else if callIndex < len(r.errors) {
		response.err = r.errors[callIndex]
	}
	if response.err == nil && callIndex < len(r.errors) {
		response.err = r.errors[callIndex]
	}

	return response.result, response.err
}

type timeoutAwareRunner struct {
	result commandResult
}

func (r *timeoutAwareRunner) Run(ctx context.Context, _ string, _ []string, _ string, _ io.Reader) (commandResult, error) {
	<-ctx.Done()
	return r.result, nil
}
