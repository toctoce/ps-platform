---
name: problem-author
description: Generate and optionally draft PS Platform algorithm problem JSON. Use when asked to create, draft, or author coding-test/problem-solving problem data for this repository, especially JSON matching harness/problem-template.json with Korean statements, examples, test cases, solution markdown, difficulty, and categories; when saving drafts, validate them with harness/validate-problem-json.sh.
---

# Problem Author

Generate one PS Platform problem as JSON. If the user asks to save a draft, save it under `problem-data/drafts` and validate it. Do not move files to `problem-data/approved`, call APIs, run imports, or insert DB rows.

## Required References

Before generating, read these project files from the repository root:

- `harness/problem-template.json`
- `docs/problem-categories.md`

Follow the template field names exactly. Choose categories only from `docs/problem-categories.md`.

## Output Contract

- If the user asks only for generated content, output exactly one JSON object and nothing else.
- Do not wrap the JSON in Markdown code fences.
- Do not add explanatory text before or after the JSON.
- Do not add fields that are not in `harness/problem-template.json`.
- Use valid JSON syntax: double-quoted strings, no trailing commas, no comments.
- Write all user-facing content in Korean.

## Draft Workflow

When the user asks to save the generated problem:

- Save the JSON file under `problem-data/drafts`.
- Use a lowercase, hyphen-separated filename that identifies the source or pattern, for example `boj-2751-sort-numbers-2.json`.
- Run `./harness/validate-problem-json.sh <draft-file>` after saving.
- If validation fails, fix the JSON and run the validation command again.
- Repeat until the draft passes validation or the issue requires user input.

Do not move draft files to `problem-data/approved`.
Do not run `harness/import-problem-json.sh`.
Do not call backend APIs.
Do not insert rows into the database.
Do not commit generated or edited problem data files unless the user explicitly asks for that commit.

## Problem Source Policy

- Base the problem on a famous, widely solved algorithm problem pattern.
- Do not invent obscure or artificial tasks when a standard well-known pattern fits.
- Do not copy proprietary problem statements, examples, or explanations verbatim from external sites.
- Rewrite the statement, examples, and solution in original Korean text.
- Keep the problem deterministic and objectively judgeable.
- Do not create ambiguous tasks or tasks with multiple acceptable output formats unless the exact judging rule is clearly stated.

## Field Rules

- `title`: concise Korean title.
- `description`: explain the task clearly without mentioning implementation strategy.
- `inputDescription`: describe the input format.
- `inputConstraints`: write input ranges and conditions as formulas or bullet-like mathematical text. Include bounds, size limits, uniqueness conditions, graph constraints, indexing rules, and other input validity assumptions here.
- `outputDescription`: describe the exact output format.
- `solution`: Markdown string. Explain what algorithm or idea is applied for what purpose, and connect it to the time complexity. Use readable headings such as approach, complexity, and implementation notes. Use `##`, `###`, code blocks, and backticks when they improve readability. Include a concise example code snippet or reference implementation when it helps the reader understand the core algorithm.
- `timeLimitMs`: positive integer, usually `1000` or `2000`.
- `memoryLimitMb`: positive integer, usually `128`, `256`, or `512`.
- `difficulty`: one of `EASY`, `NORMAL`, `HARD`, `VERY_HARD`.
- `categories`: non-empty array using only allowed categories from `docs/problem-categories.md`.
- `examples`: 2 to 4 items.
- `testCases`: at least 10 items.

## Example Rules

- Provide at least 2 examples.
- Use up to 4 examples only when the problem has meaningfully different ordinary cases.
- Do not put edge cases in `examples`; reserve edge cases for `testCases`.
- Each example must include `input`, `expectedOutput`, and `description`.
- `description` explains how the input produces the output only when that explanation adds useful clarity.
- If the example is simple or the explanation would be too trivial, omit the explanation by setting `description` to `null`.
- Treat restating the obvious output transformation as too trivial. For example, do not write explanations like "5, 4, 3, 2, 1을 오름차순으로 정렬하면 1, 2, 3, 4, 5입니다."; use `null` instead.
- `expectedOutput` must never be `null`.

## Test Case Rules

- Provide at least 10 test cases.
- Include small boundary values.
- Include large boundary values near the input limit.
- Include edge cases such as empty-like minimum sizes, duplicates, sorted/reversed data, disconnected graphs, single-node trees, all-equal values, negative values, or overflow-risk values when relevant.
- Ensure every `expectedOutput` is manually computed and non-null.
- Make test cases diverse enough to catch common wrong approaches.
- Keep test inputs valid according to `inputConstraints`.

## Quality Checklist

Before final output, verify mentally:

- The JSON parses.
- All template fields are present.
- Category names are allowed.
- Difficulty is allowed.
- Examples are ordinary, not edge cases.
- Test cases include edge and boundary cases.
- Every expected output is correct.
- The solution matches the generated problem and test cases.
- No approved move, DB import, API call, or direct DB write is attempted.
