export const initialProblemForm = {
  title: 'A+B',
  description: '두 수를 더하는 문제',
  inputDescription: '정수 A와 B가 주어진다.',
  outputDescription: 'A+B를 출력한다.',
  difficulty: 'NORMAL',
  categories: '',
  timeLimitMs: 1000,
  memoryLimitMb: 128,
  testCases: [
    {
      input: '1 2\n',
      expectedOutput: '3\n',
      description: '1과 2를 더하면 3입니다.',
    },
  ],
};

export const DIFFICULTY_OPTIONS = [
  { value: 'EASY', label: '쉬움' },
  { value: 'NORMAL', label: '보통' },
  { value: 'HARD', label: '어려움' },
  { value: 'VERY_HARD', label: '매우 어려움' },
];

export function difficultyLabel(value) {
  return DIFFICULTY_OPTIONS.find((item) => item.value === value)?.label ?? '보통';
}

export function problemCategoryNames(problem) {
  if (Array.isArray(problem?.categories)) {
    const names = problem.categories
      .map((category) => {
        if (typeof category === 'string') {
          return category;
        }
        return category?.name;
      })
      .filter((category) => Boolean(category));

    if (names.length > 0) {
      return names;
    }
  }

  return ['미분류'];
}

export function problemInputRange(problem) {
  return problem?.inputRange
    ?? problem?.inputRangeDescription
    ?? problem?.inputConstraint
    ?? problem?.inputConstraints
    ?? problem?.constraintDescription
    ?? '등록된 입력 범위가 없습니다.';
}

export function buildCategoryGroups(problems) {
  const map = new Map();
  for (const problem of problems) {
    const categoryNames = problemCategoryNames(problem).filter((category) => category !== '미분류');
    for (const category of categoryNames) {
      if (!map.has(category)) {
        map.set(category, []);
      }
      map.get(category).push(problem);
    }
  }
  return map;
}
