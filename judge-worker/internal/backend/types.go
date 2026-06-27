package backend

// JudgeJobAssignment는 worker가 backend에서 가져오는 채점 작업 정보입니다.
type JudgeJobAssignment struct {
	JobID         int64  `json:"jobId"`
	SubmissionID  int64  `json:"submissionId"`
	ProblemID     int64  `json:"problemId"`
	Language      string `json:"language"`
	SourceCode    string `json:"sourceCode"`
	TimeLimitMs   int    `json:"timeLimitMs"`
	MemoryLimitMb int    `json:"memoryLimitMb"`
}

// CompleteRequest는 worker가 채점 완료 후 backend에 보내는 요청 본문입니다.
type CompleteRequest struct {
	Verdict string `json:"verdict"`
}
