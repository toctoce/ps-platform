export function isPendingStatus(status) {
  return status === 'PENDING' || status === 'QUEUED' || status === 'RUNNING';
}

export function isFinishedStatus(status) {
  return !isPendingStatus(status);
}

export function submissionNickname(submission) {
  return submission.username
    ?? submission.userName
    ?? submission.authorUsername
    ?? submission.submitterUsername
    ?? '-';
}

function shouldShowJudgeMetrics(submission) {
  return submission.status === 'JUDGED' && submission.verdict === 'ACCEPTED';
}

export function submissionTime(submission) {
  if (!shouldShowJudgeMetrics(submission)) {
    return '-';
  }
  const value = submission.executionTimeMs ?? submission.timeMs ?? submission.runtimeMs ?? submission.elapsedMs;
  return value === undefined || value === null ? '-' : `${value}ms`;
}

export function submissionMemory(submission) {
  if (!shouldShowJudgeMetrics(submission)) {
    return '-';
  }
  const mb = submission.memoryMb ?? submission.memoryUsageMb ?? submission.peakMemoryMb;
  if (mb !== undefined && mb !== null) {
    return `${mb}MB`;
  }
  const kb = submission.memoryKb ?? submission.memoryUsageKb;
  return kb === undefined || kb === null ? '-' : `${kb}KB`;
}

export function formatSubmissionDate(value) {
  if (!value) {
    return '-';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  const diffMs = Date.now() - date.getTime();
  if (diffMs >= 0) {
    const diffMinutes = Math.floor(diffMs / 60000);
    if (diffMinutes < 60) {
      return `${diffMinutes}분 전`;
    }
    if (diffMinutes < 1440) {
      const hours = Math.floor(diffMinutes / 60);
      const minutes = diffMinutes % 60;
      return `${hours}시간 ${minutes}분 전`;
    }
  }
  return new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
  }).format(date);
}

export function buildInitialTestRunResults(testCases, status = '대기') {
  return testCases.map((testCase, index) => ({
    id: testCase.id ?? index,
    index: index + 1,
    status,
    message: '',
  }));
}

const STATUS_LABELS = {
  PENDING: '대기 중',
  QUEUED: '대기 중',
  RUNNING: '채점 중',
  JUDGED: '채점 완료',
  FAILED: '실패',
  DONE: '완료',
};

const VERDICT_LABELS = {
  ACCEPTED: '정답',
  WRONG_ANSWER: '오답',
  COMPILE_ERROR: '컴파일 에러',
  RUNTIME_ERROR: '런타임 에러',
  TIME_LIMIT_EXCEEDED: '시간 초과',
  MEMORY_LIMIT_EXCEEDED: '메모리 초과',
};

export function verdictToLabel(verdict) {
  return VERDICT_LABELS[verdict] ?? verdict ?? '알 수 없음';
}

export function inferTestRunResults(submission, testCases) {
  if (testCases.length === 0) {
    return [];
  }

  if (isPendingStatus(submission.status)) {
    return buildInitialTestRunResults(testCases, '실행 중');
  }

  const resultLabel = statusToLabel(submission.status, submission.verdict);
  const message = submission.judgeMessage || resultLabel;

  if (submission.status === 'JUDGED' && submission.verdict === 'ACCEPTED') {
    return testCases.map((testCase, index) => ({
      id: testCase.id ?? index,
      index: index + 1,
      status: '성공',
      message: '',
    }));
  }

  const match = String(message).match(/test case\s+(\d+)/i);
  const failedIndex = match ? Number(match[1]) : null;

  return testCases.map((testCase, index) => {
    const currentIndex = index + 1;
    if (failedIndex === null) {
      return {
        id: testCase.id ?? index,
        index: currentIndex,
        status: '실패',
        message,
      };
    }
    if (currentIndex < failedIndex) {
      return {
        id: testCase.id ?? index,
        index: currentIndex,
        status: '성공',
        message: '',
      };
    }
    if (currentIndex === failedIndex) {
      return {
        id: testCase.id ?? index,
        index: currentIndex,
        status: '실패',
        message,
      };
    }
    return {
      id: testCase.id ?? index,
      index: currentIndex,
      status: '미실행',
      message: '',
    };
  });
}

export function mapCodeRunResults(codeRun, testCases) {
  if (testCases.length === 0) {
    return [];
  }

  if (isPendingStatus(codeRun.status)) {
    return buildInitialTestRunResults(testCases, '실행 중');
  }

  if (Array.isArray(codeRun.caseResults) && codeRun.caseResults.length > 0) {
    return testCases.map((testCase, index) => {
      const result = codeRun.caseResults.find((item) => item.index === index + 1);
      if (!result) {
        return {
          id: testCase.id ?? index,
          index: index + 1,
          status: '미실행',
          message: '',
        };
      }
      return {
        id: testCase.id ?? index,
        index: index + 1,
        status: result.verdict === 'ACCEPTED' ? '성공' : '실패',
        message: result.message || verdictToLabel(result.verdict),
      };
    });
  }

  return inferTestRunResults(
    {
      status: codeRun.status === 'DONE' ? 'JUDGED' : codeRun.status,
      verdict: codeRun.verdict,
      judgeMessage: codeRun.judgeMessage,
    },
    testCases
  );
}

export function statusToLabel(status, verdict) {
  if (status === 'JUDGED') {
    return verdictToLabel(verdict);
  }
  return STATUS_LABELS[status] ?? status ?? '알 수 없음';
}

export function submissionBadgeClass(status, verdict) {
  return `is-${submissionResultClass(status, verdict)}`;
}

export function submissionResultClass(status, verdict) {
  if (status === 'JUDGED') {
    if (verdict === 'ACCEPTED') {
      return 'accepted';
    }
    if (verdict === 'WRONG_ANSWER') {
      return 'wrong-answer';
    }
    if (verdict === 'TIME_LIMIT_EXCEEDED' || verdict === 'MEMORY_LIMIT_EXCEEDED') {
      return 'limit-exceeded';
    }
    if (verdict === 'COMPILE_ERROR' || verdict === 'RUNTIME_ERROR') {
      return 'error';
    }
    return 'error';
  }
  if (status === 'FAILED') {
    return 'error';
  }
  if (status === 'RUNNING') {
    return 'running';
  }
  return 'pending';
}

export function submissionLanguageLabel(language) {
  const normalized = String(language ?? '').toLowerCase();
  if (normalized === 'java') {
    return 'Java';
  }
  if (normalized === 'python') {
    return 'Python';
  }
  if (normalized === 'cpp' || normalized === 'c++') {
    return 'C++';
  }
  return language ?? '-';
}
