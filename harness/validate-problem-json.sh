#!/bin/bash
set -euo pipefail

ROOT_DIR="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"

if [ "$#" -eq 0 ]; then
  echo "사용법: $0 <problem-json-file> [problem-json-file ...]" >&2
  exit 2
fi

node - "$ROOT_DIR" "$@" <<'NODE'
const fs = require('fs');
const path = require('path');

const [, , rootDir, ...files] = process.argv;
const templatePath = path.join(rootDir, 'harness/problem-template.json');
const categoryPath = path.join(rootDir, 'docs/problem-categories.md');
const allowedDifficulties = new Set(['EASY', 'NORMAL', 'HARD', 'VERY_HARD']);

function readJson(filePath) {
  try {
    return JSON.parse(fs.readFileSync(filePath, 'utf8'));
  } catch (error) {
    throw new Error(`JSON 파싱 실패: ${error.message}`);
  }
}

function readCategories(filePath) {
  return fs.readFileSync(filePath, 'utf8')
    .split(/\r?\n/)
    .map((line) => line.match(/^\s*-\s+(.+?)\s*$/)?.[1])
    .filter(Boolean);
}

function isPlainObject(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function isNonBlankString(value) {
  return typeof value === 'string' && value.trim().length > 0;
}

function pushUnknownOrMissing(errors, object, expectedFields, label) {
  const expected = new Set(expectedFields);
  for (const field of expectedFields) {
    if (!(field in object)) {
      errors.push(`${label}: '${field}' 필드가 없습니다.`);
    }
  }
  for (const field of Object.keys(object)) {
    if (!expected.has(field)) {
      errors.push(`${label}: 허용되지 않은 '${field}' 필드가 있습니다.`);
    }
  }
}

function validateTextField(errors, problem, field) {
  if (!isNonBlankString(problem[field])) {
    errors.push(`'${field}'는 비어 있지 않은 문자열이어야 합니다.`);
  }
}

function validatePositiveInteger(errors, problem, field) {
  if (!Number.isInteger(problem[field]) || problem[field] <= 0) {
    errors.push(`'${field}'는 양의 정수여야 합니다.`);
  }
}

function validateExamples(errors, examples) {
  if (!Array.isArray(examples)) {
    errors.push("'examples'는 배열이어야 합니다.");
    return;
  }
  if (examples.length < 2 || examples.length > 4) {
    errors.push("'examples'는 2개 이상 4개 이하여야 합니다.");
  }

  examples.forEach((example, index) => {
    const label = `examples[${index}]`;
    if (!isPlainObject(example)) {
      errors.push(`${label}: 객체여야 합니다.`);
      return;
    }
    pushUnknownOrMissing(errors, example, ['input', 'expectedOutput', 'description'], label);
    if (typeof example.input !== 'string') {
      errors.push(`${label}.input: 문자열이어야 합니다.`);
    }
    if (!isNonBlankString(example.expectedOutput)) {
      errors.push(`${label}.expectedOutput: 비어 있지 않은 문자열이어야 합니다.`);
    }
    if (example.description !== null && typeof example.description !== 'string') {
      errors.push(`${label}.description: 문자열 또는 null이어야 합니다.`);
    }
    if (typeof example.description === 'string' && example.description.trim().length === 0) {
      errors.push(`${label}.description: 빈 문자열 대신 null을 사용해야 합니다.`);
    }
  });
}

function validateTestCases(errors, testCases) {
  if (!Array.isArray(testCases)) {
    errors.push("'testCases'는 배열이어야 합니다.");
    return;
  }
  if (testCases.length < 10) {
    errors.push("'testCases'는 최소 10개 이상이어야 합니다.");
  }

  const seen = new Set();
  testCases.forEach((testCase, index) => {
    const label = `testCases[${index}]`;
    if (!isPlainObject(testCase)) {
      errors.push(`${label}: 객체여야 합니다.`);
      return;
    }
    pushUnknownOrMissing(errors, testCase, ['input', 'expectedOutput'], label);
    if (typeof testCase.input !== 'string') {
      errors.push(`${label}.input: 문자열이어야 합니다.`);
    }
    if (!isNonBlankString(testCase.expectedOutput)) {
      errors.push(`${label}.expectedOutput: 비어 있지 않은 문자열이어야 합니다.`);
    }

    const key = `${testCase.input}\u0000${testCase.expectedOutput}`;
    if (seen.has(key)) {
      errors.push(`${label}: 같은 input/expectedOutput 테스트케이스가 중복되었습니다.`);
    }
    seen.add(key);
  });
}

function validateProblem(problem, templateFields, allowedCategories) {
  const errors = [];
  if (!isPlainObject(problem)) {
    return ['최상위 값은 JSON 객체여야 합니다.'];
  }

  pushUnknownOrMissing(errors, problem, templateFields, 'problem');

  [
    'title',
    'description',
    'inputDescription',
    'inputConstraints',
    'outputDescription',
    'solution',
  ].forEach((field) => validateTextField(errors, problem, field));

  if (typeof problem.solution === 'string' && !/(^|\n)#{1,6}\s+\S/.test(problem.solution)) {
    errors.push("'solution'은 Markdown 제목을 포함해야 합니다. 예: '## 풀이'");
  }

  validatePositiveInteger(errors, problem, 'timeLimitMs');
  validatePositiveInteger(errors, problem, 'memoryLimitMb');

  if (!allowedDifficulties.has(problem.difficulty)) {
    errors.push("'difficulty'는 EASY, NORMAL, HARD, VERY_HARD 중 하나여야 합니다.");
  }

  if (!Array.isArray(problem.categories) || problem.categories.length === 0) {
    errors.push("'categories'는 하나 이상의 카테고리를 가진 배열이어야 합니다.");
  } else {
    const seenCategories = new Set();
    problem.categories.forEach((category, index) => {
      if (!isNonBlankString(category)) {
        errors.push(`categories[${index}]: 비어 있지 않은 문자열이어야 합니다.`);
        return;
      }
      if (!allowedCategories.has(category)) {
        errors.push(`categories[${index}]: 허용되지 않은 카테고리 '${category}'입니다.`);
      }
      if (seenCategories.has(category)) {
        errors.push(`categories[${index}]: 중복 카테고리 '${category}'입니다.`);
      }
      seenCategories.add(category);
    });
  }

  validateExamples(errors, problem.examples);
  validateTestCases(errors, problem.testCases);
  return errors;
}

let template;
let categories;
try {
  template = readJson(templatePath);
  categories = readCategories(categoryPath);
} catch (error) {
  console.error(`[ERROR] 검증 기준 파일을 읽을 수 없습니다: ${error.message}`);
  process.exit(2);
}

const templateFields = Object.keys(template);
const allowedCategories = new Set(categories);
let failed = false;

for (const file of files) {
  const filePath = path.resolve(process.cwd(), file);
  let problem;
  try {
    problem = readJson(filePath);
  } catch (error) {
    failed = true;
    console.error(`[FAIL] ${file}`);
    console.error(`  - ${error.message}`);
    continue;
  }

  const errors = validateProblem(problem, templateFields, allowedCategories);
  if (errors.length > 0) {
    failed = true;
    console.error(`[FAIL] ${file}`);
    for (const error of errors) {
      console.error(`  - ${error}`);
    }
  } else {
    console.log(`[OK] ${file}`);
  }
}

if (failed) {
  process.exit(1);
}
NODE
