package judge

import (
	"strings"
	"unicode"
)

const (
	VerdictAccepted    = "ACCEPTED"
	VerdictWrongAnswer = "WRONG_ANSWER"
)

// EvaluateOutput은 기대 출력과 실제 출력을 비교해 정답 여부를 판정합니다.
func EvaluateOutput(expected string, actual string) string {
	if NormalizeOutput(expected) == NormalizeOutput(actual) {
		return VerdictAccepted
	}
	return VerdictWrongAnswer
}

// NormalizeOutput은 운영체제별 줄바꿈 차이와 줄 끝 공백 차이를 정리합니다.
func NormalizeOutput(output string) string {
	output = strings.ReplaceAll(output, "\r\n", "\n")
	output = strings.ReplaceAll(output, "\r", "\n")

	lines := strings.Split(output, "\n")
	for i, line := range lines {
		lines[i] = strings.TrimRightFunc(line, unicode.IsSpace)
	}

	for len(lines) > 0 && lines[len(lines)-1] == "" {
		lines = lines[:len(lines)-1]
	}

	return strings.Join(lines, "\n")
}
