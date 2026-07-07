---
name: problem-reviewer
description: Review, verify, and improve existing PS Platform problem JSON files in drafts or approved. Use when asked to 검수, review, compare with an actual BOJ/public problem, fix descriptions, examples, constraints, categories, test cases, expectedOutput, or move a reviewed problem toward approval. Do not use to create brand-new problem drafts from scratch; use problem-author for that.
---

# Problem Reviewer

## Overview

Review existing PS Platform problem JSON against source material and repository standards. Keep changes scoped to the requested problem files, validate JSON, and report source verification status and remaining risks.

## Required References

Before reviewing a problem, read these project files from the repository root:

- `harness/problem-template.json`
- `docs/problem-categories.md`
- `docs/problem-authoring-guidelines.md`

Read the target problem JSON before deciding what to edit. Choose categories only from `docs/problem-categories.md`.

## Source Verification

- Verify the requested problem against real public source material before claiming it matches the original.
- Do not query BOJ or solved.ac directly. Treat them as unavailable unless the user explicitly says they are usable again.
- For BOJ-based problems, prefer Web Archive snapshots of the BOJ page. If unavailable, use reliable solution blogs, archived summaries, public writeups, or public code repositories that state input, output, constraints, examples, and core rules.
- Do not rely only on memory, title, problem number, or known algorithm pattern.
- If source verification fails, say so clearly and separate verified fixes from assumptions.
- Do not copy original statements verbatim; rewrite platform text in Korean.

## Review Workflow

1. Identify the next problem file from the user's request and the current draft/approved ordering.
2. Read the target JSON and relevant references.
3. Verify source conditions: title, input format, output format, constraints, examples, impossible cases, ordering rules, multiple-answer rules, and time/memory limits.
4. Compare the JSON against both the source and `docs/problem-authoring-guidelines.md`.
5. Edit only what is needed: statement clarity, input/output descriptions, constraints, examples, example descriptions, solution, difficulty, categories, and tests.
6. Recompute `expectedOutput` with an independent calculation or reference script whenever feasible.
7. Run `./harness/validate-problem-json.sh <file>` for every edited problem JSON.
8. Run `./harness/verify.sh` when the change is broad, touches docs/code, or the user asks for final approval.

Do not call import scripts, backend APIs, or database writes. Do not commit generated or edited problem data unless the user explicitly asks. If the user explicitly asks to move a problem to `approved`, move only the requested file and keep unrelated worktree changes intact.

## Review Priorities

- Make the problem understandable to beginners without adding implementation hints to the statement.
- Keep platform-specific input/output changes consistent across description, examples, tests, and solution.
- Use three-digit thousands separators in `inputConstraints`, such as `100,000`.
- Prefer clean decimal platform limits such as `1,000,000,000` over implementation-shaped limits such as `2,147,483,647`, unless the exact boundary is essential.
- Add existing categories when clearly appropriate, and tell the user when a new category would be needed.
- Keep trivial example descriptions as `null`; add explanations only when they clarify non-obvious reasoning.
- Append requested new tests instead of replacing valid existing tests unless the user explicitly asks to replace them.

## Reporting

In the final response, include:

- Files changed.
- Whether real source conditions were verified and which source type was used.
- Validation commands and results.
- How `expectedOutput` was recomputed.
- Categories added or changed.
- Any unverified details or residual risks.
