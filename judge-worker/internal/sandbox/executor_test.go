package sandbox

import (
	"context"
	"io"
	"strings"
	"testing"

	"ps-platform/judge-worker/internal/backend"
)

func TestJavaExecutorAcceptsWhenAllTestCasesPass(t *testing.T) {
	executor := NewJavaExecutorWithRunner(&fakeCommandRunner{
		responses: []commandResponse{
			{result: commandResult{exitCode: 0}},
			{result: commandResult{exitCode: 0, stdout: "3\n"}},
			{result: commandResult{exitCode: 0, stdout: "11\n"}},
		},
	}, "")

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
}

func TestJavaExecutorReturnsCompileErrorOnCompileFailure(t *testing.T) {
	executor := NewJavaExecutorWithRunner(&fakeCommandRunner{
		responses: []commandResponse{
			{result: commandResult{exitCode: 1, stderr: "Compilation failed"}},
		},
	}, "")

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

func TestJavaExecutorReturnsWrongAnswerWhenOutputDiffers(t *testing.T) {
	executor := NewJavaExecutorWithRunner(&fakeCommandRunner{
		responses: []commandResponse{
			{result: commandResult{exitCode: 0}},
			{result: commandResult{exitCode: 0, stdout: "4\n"}},
		},
	}, "")

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

func TestJavaExecutorReturnsRuntimeErrorOnNonZeroExit(t *testing.T) {
	executor := NewJavaExecutorWithRunner(&fakeCommandRunner{
		responses: []commandResponse{
			{result: commandResult{exitCode: 0}},
			{result: commandResult{exitCode: 1, stderr: "RuntimeError"}},
		},
	}, "")

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
	}, "")

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
	executor := NewJavaExecutorWithRunner(&fakeCommandRunner{}, "")
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
