export const PROBLEM_REPORT_TYPES = [
  { value: 'DESCRIPTION_ERROR', label: '문제 설명 오류' },
  { value: 'IO_DESCRIPTION_ERROR', label: '입력/출력 설명 오류' },
  { value: 'EXAMPLE_ERROR', label: '예제 오류' },
  { value: 'TEST_CASE_ERROR', label: '테스트케이스 오류' },
  { value: 'JUDGE_ERROR', label: '정답 판정 오류' },
  { value: 'SOLUTION_ERROR', label: '해설 오류' },
  { value: 'OTHER', label: '기타' },
];

export const PROBLEM_REPORT_STATUSES = [
  { value: 'PENDING', label: '대기' },
  { value: 'REVIEWING', label: '검토 중' },
  { value: 'RESOLVED', label: '해결' },
  { value: 'REJECTED', label: '반려' },
];

export function problemReportTypeLabel(type) {
  return PROBLEM_REPORT_TYPES.find((item) => item.value === type)?.label ?? type;
}

export function problemReportStatusLabel(status) {
  return PROBLEM_REPORT_STATUSES.find((item) => item.value === status)?.label ?? status;
}
