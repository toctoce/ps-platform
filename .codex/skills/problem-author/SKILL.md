---
name: problem-author
description: Generate and optionally save new PS Platform algorithm problem JSON drafts. Use when asked to create, restore, draft, or author new coding-test/problem-solving problem data for this repository, especially JSON matching harness/problem-template.json with Korean statements, examples, test cases, solution markdown, difficulty, and categories. Do not use for reviewing existing draft or approved problem JSON; use problem-reviewer for that.
---

# Problem Author

Generate one new PS Platform problem as JSON. If the user asks to save a draft, save it under `problem-data/drafts` and validate it. Do not review existing problem files with this skill; use `problem-reviewer` for that. Do not move files to `problem-data/approved`, call APIs, run imports, or insert DB rows.

## Required References

Before generating, read these project files from the repository root:

- `harness/problem-template.json`
- `docs/problem-categories.md`
- `docs/problem-authoring-guidelines.md`

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

- Base the problem on the actual public problem requested by the user.
- If a problem number is provided, verify the real problem conditions from external sources before drafting.
- Do not query BOJ or solved.ac directly during verification. Treat those services as unavailable unless the user explicitly says they are usable again.
- For BOJ-based problems, verify conditions with reliable alternative sources such as Web Archive snapshots, solution blogs, archived summaries, or public writeups that confirm input format, output format, constraints, examples, and key rules.
- Do not rely only on memory, problem title, a famous pattern, or inferred algorithm type.
- If the real problem conditions cannot be verified, stop and report that the source could not be confirmed instead of generating a draft.
- Do not copy proprietary problem statements, examples, or explanations verbatim from external sites.
- Rewrite the statement, examples, and solution in original Korean text.
- Keep the problem deterministic and objectively judgeable.
- Do not create ambiguous tasks or tasks with multiple acceptable output formats unless the exact judging rule is clearly stated.
- In the final response, state whether the real source was verified and mention any remaining uncertainty.

## Field Rules

- `title`: concise Korean title.
- `description`: explain the task clearly without mentioning implementation strategy.
- `inputDescription`: describe the input format.
- `inputConstraints`: write input ranges and conditions as formulas or bullet-like mathematical text. Include bounds, size limits, uniqueness conditions, graph constraints, indexing rules, and other input validity assumptions here.
- Use thousands separators in numeric bounds in `inputConstraints`; use `100,000`, not `100000`.
- Prefer clean decimal platform limits such as `1,000,000,000` over implementation-shaped limits such as `2,147,483,647`, unless the exact original boundary is essential to the problem.
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
- When an example explanation is useful, explain the actual reasoning or intermediate values that produce the output.
- `expectedOutput` must never be `null`.

## Test Case Rules

- Provide at least 10 test cases.
- Include small boundary values.
- Include large boundary values near the input limit.
- Include edge cases such as empty-like minimum sizes, duplicates, sorted/reversed data, disconnected graphs, single-node trees, all-equal values, negative values, or overflow-risk values when relevant.
- Ensure every `expectedOutput` is manually computed and non-null.
- Make test cases diverse enough to catch common wrong approaches.
- When adding requested test cases, append new cases instead of replacing existing valid cases unless the user explicitly asks to replace them.
- Include large numeric cases when the required data type matters.
- Recompute every `expectedOutput` with an independent calculation or reference script when feasible.
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
