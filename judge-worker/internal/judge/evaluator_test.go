package judge

import "testing"

func TestEvaluateOutputAcceptedWhenOutputsMatch(t *testing.T) {
	verdict := EvaluateOutput("3\n", "3\n")

	if verdict != VerdictAccepted {
		t.Fatalf("verdict = %s, want %s", verdict, VerdictAccepted)
	}
}

func TestEvaluateOutputAcceptedWhenLineEndingsDiffer(t *testing.T) {
	verdict := EvaluateOutput("1\n2\n", "1\r\n2\r\n")

	if verdict != VerdictAccepted {
		t.Fatalf("verdict = %s, want %s", verdict, VerdictAccepted)
	}
}

func TestEvaluateOutputAcceptedWhenTrailingWhitespaceDiffers(t *testing.T) {
	verdict := EvaluateOutput("hello\nworld\n", "hello   \nworld\t\n\n")

	if verdict != VerdictAccepted {
		t.Fatalf("verdict = %s, want %s", verdict, VerdictAccepted)
	}
}

func TestEvaluateOutputWrongAnswerWhenContentDiffers(t *testing.T) {
	verdict := EvaluateOutput("3\n", "4\n")

	if verdict != VerdictWrongAnswer {
		t.Fatalf("verdict = %s, want %s", verdict, VerdictWrongAnswer)
	}
}

func TestEvaluateOutputWrongAnswerWhenInternalWhitespaceDiffers(t *testing.T) {
	verdict := EvaluateOutput("1 2\n", "1    2\n")

	if verdict != VerdictWrongAnswer {
		t.Fatalf("verdict = %s, want %s", verdict, VerdictWrongAnswer)
	}
}
