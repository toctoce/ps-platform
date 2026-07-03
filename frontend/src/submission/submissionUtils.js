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

export function submissionTime(submission) {
  const value = submission.executionTimeMs ?? submission.timeMs ?? submission.runtimeMs ?? submission.elapsedMs;
  return value === undefined || value === null ? '-' : `${value}ms`;
}

export function submissionMemory(submission) {
  const mb = submission.memoryMb ?? submission.memoryUsageMb ?? submission.peakMemoryMb;
  if (mb !== undefined && mb !== null) {
    return `${mb}MB`;
  }
  const kb = submission.memoryKb ?? submission.memoryUsageKb;
  return kb === undefined || kb === null ? '-' : `${kb}KB`;
}

export function formatDateTime(value) {
  if (!value) {
    return '-';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
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
        message: result.message || result.verdict,
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
    return verdict ?? 'JUDGED';
  }
  return status ?? 'UNKNOWN';
}

export function submissionBadgeClass(status) {
  if (status === 'JUDGED') {
    return 'verdict';
  }
  if (status === 'FAILED') {
    return 'failed';
  }
  if (status === 'RUNNING') {
    return 'running';
  }
  return 'pending';
}
