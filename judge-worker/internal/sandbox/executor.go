package sandbox

import (
	"context"
	"strings"

	"ps-platform/judge-worker/internal/backend"
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

// Executor는 채점 작업을 sandbox에서 실행하고 verdict를 반환하는 실행기입니다.
type Executor interface {
	Execute(ctx context.Context, job backend.JudgeJobAssignment) (string, error)
}

// StubExecutor는 실제 sandbox 실행이 구현되기 전까지 사용하는 임시 실행기입니다.
type StubExecutor struct {
}

// NewStubExecutor는 임시 sandbox 실행기를 생성합니다.
func NewStubExecutor() *StubExecutor {
	return &StubExecutor{}
}

// Execute는 사용자 코드를 실행하지 않고 임시 verdict를 반환합니다.
func (e *StubExecutor) Execute(ctx context.Context, job backend.JudgeJobAssignment) (string, error) {
	if strings.TrimSpace(job.SourceCode) == "" {
		return VerdictCompileError, nil
	}

	return VerdictRuntimeError, nil
}
