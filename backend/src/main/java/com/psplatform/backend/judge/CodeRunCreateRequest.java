package com.psplatform.backend.judge;

import java.util.List;

public record CodeRunCreateRequest(
	String language,
	String sourceCode,
	List<JudgeTestCase> testCases
) {
}
