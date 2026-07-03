package backend

// JudgeJobAssignment는 worker가 backend에서 가져오는 채점 작업 정보입니다.
type JudgeJobAssignment struct {
	JobID         int64           `json:"jobId"`
	JobKind       string          `json:"jobKind"`
	SubmissionID  int64           `json:"submissionId"`
	ProblemID     int64           `json:"problemId"`
	Language      string          `json:"language"`
	SourceCode    string          `json:"sourceCode"`
	TimeLimitMs   int             `json:"timeLimitMs"`
	MemoryLimitMb int             `json:"memoryLimitMb"`
	TestCases     []JudgeTestCase `json:"testCases"`
}

// JudgeTestCase는 worker가 실행할 입력과 비교할 기대 출력입니다.
type JudgeTestCase struct {
	Input          string `json:"input"`
	ExpectedOutput string `json:"expectedOutput"`
}

// JudgeCaseResult는 테스트케이스 하나의 실행 결과입니다.
type JudgeCaseResult struct {
	Index           int    `json:"index"`
	Verdict         string `json:"verdict"`
	Stdout          string `json:"stdout"`
	ExpectedOutput  string `json:"expectedOutput"`
	Message         string `json:"message"`
	ExecutionTimeMs int    `json:"executionTimeMs,omitempty"`
	MemoryMb        int    `json:"memoryMb,omitempty"`
}

// CompleteRequest는 worker가 채점 완료 후 backend에 보내는 요청 본문입니다.
type CompleteRequest struct {
	Verdict      string            `json:"verdict"`
	JudgeMessage string            `json:"judgeMessage"`
	CaseResults  []JudgeCaseResult `json:"caseResults"`
}
