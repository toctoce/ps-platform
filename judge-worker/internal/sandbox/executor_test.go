package sandbox

import (
	"context"
	"testing"

	"ps-platform/judge-worker/internal/backend"
)

func TestStubExecutorReturnsCompileErrorOnBlankSource(t *testing.T) {
	executor := NewStubExecutor()
	verdict, err := executor.Execute(context.Background(), backend.JudgeJobAssignment{
		SourceCode: "   \n\t",
	})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if verdict != VerdictCompileError {
		t.Fatalf("verdict = %s, want %s", verdict, VerdictCompileError)
	}
}

func TestStubExecutorReturnsRuntimeErrorOnNonEmptySource(t *testing.T) {
	executor := NewStubExecutor()
	verdict, err := executor.Execute(context.Background(), backend.JudgeJobAssignment{
		SourceCode: "public class Main {}",
	})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if verdict != VerdictRuntimeError {
		t.Fatalf("verdict = %s, want %s", verdict, VerdictRuntimeError)
	}
}
