export const editorStorageKeyPrefix = 'ps-platform-editor';
export const editorDraftTtlMs = 7 * 24 * 60 * 60 * 1000;
export const editorLanguageStorageKey = `${editorStorageKeyPrefix}:default-language`;

const EDITOR_DRAFT_KIND = 'ps-platform-editor-draft';
const DEFAULT_EDITOR_LANGUAGE = 'java';

export const LANGUAGE_TEMPLATES = {
  java: `import java.io.*;
import java.util.*;

public class Main {
    public static void main(String[] args) throws Exception {
\t\t
    }
}`,
  python: `import sys
`,
  cpp: `#include <bits/stdc++.h>
using namespace std;

int main() {
    return 0;
}`,
};

const LEGACY_LANGUAGE_TEMPLATES = {
  java: [
    `public class Main {
    public static void main(String[] args) throws Exception {
\t\t
    }
}`,
    `public class Main {
    public static void main(String[] args) throws Exception {

    }
}`,
    `import java.io.*;
import java.util.*;

public class Main {
    public static void main(String[] args) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        StringTokenizer st = new StringTokenizer(br.readLine());
        int a = Integer.parseInt(st.nextToken());
        int b = Integer.parseInt(st.nextToken());
        System.out.println(a + b);
    }
}`,
  ],
  python: [
    '',
    `import sys

def main():
    input_data = sys.stdin.readline().rstrip()
    if not input_data:
        return
    a, b = map(int, input_data.split())
    print(a + b)


if __name__ == "__main__":
    main()
`,
    `def main():
    pass


if __name__ == "__main__":
    main()
`,
    `def main():
    pass
`,
  ],
  cpp: [
    `int main() {
    return 0;
}`,
    `#include <bits/stdc++.h>
using namespace std;

int main() {
    ios::sync_with_stdio(false);
    cin.tie(nullptr);

    long long a, b;
    if (!(cin >> a >> b)) {
        return 0;
    }
    cout << (a + b) << "\\n";
    return 0;
}`,
    `#include <bits/stdc++.h>
using namespace std;

int main() {
    ios::sync_with_stdio(false);
    cin.tie(nullptr);

    return 0;
}`,
    `int main() {

    return 0;
}`,
  ],
};

export const LANGUAGE_OPTIONS = [
  { value: 'java', label: 'Java' },
  { value: 'python', label: 'Python' },
  { value: 'cpp', label: 'C++' },
];

function isSupportedLanguage(language) {
  return LANGUAGE_OPTIONS.some((option) => option.value === language);
}

export function readPreferredEditorLanguage(storage) {
  try {
    const language = storage.getItem(editorLanguageStorageKey);
    return isSupportedLanguage(language) ? language : DEFAULT_EDITOR_LANGUAGE;
  } catch {
    return DEFAULT_EDITOR_LANGUAGE;
  }
}

export function writePreferredEditorLanguage(storage, language) {
  if (!isSupportedLanguage(language)) {
    return;
  }

  try {
    storage.setItem(editorLanguageStorageKey, language);
  } catch {
    // 저장소를 사용할 수 없는 브라우저에서도 현재 언어 변경은 그대로 허용합니다.
  }
}

export function createInitialSubmissionForm(storage) {
  const language = readPreferredEditorLanguage(storage);
  return {
    language,
    sourceCode: LANGUAGE_TEMPLATES[language],
  };
}

export function editorLanguageLabel(language) {
  return LANGUAGE_OPTIONS.find((item) => item.value === language)?.label ?? 'Java';
}

export function editorFileName(language) {
  if (language === 'python') {
    return 'main.py';
  }
  if (language === 'cpp') {
    return 'main.cpp';
  }
  return 'Main.java';
}

export function editorLanguage(language) {
  if (language === 'cpp') {
    return 'cpp';
  }
  if (language === 'python') {
    return 'python';
  }
  return 'java';
}

export function isTemplateSource(language, sourceCode) {
  const normalized = (sourceCode ?? '').trim();
  const candidates = [
    LANGUAGE_TEMPLATES[language],
    ...(LEGACY_LANGUAGE_TEMPLATES[language] ?? []),
  ];
  return candidates.some((template) => (template ?? '').trim() === normalized);
}

export function normalizeTemplateSource(language, sourceCode) {
  return isTemplateSource(language, sourceCode)
    ? LANGUAGE_TEMPLATES[language] ?? sourceCode
    : sourceCode;
}

function parseEditorDraft(rawValue) {
  if (rawValue === null) {
    return null;
  }
  try {
    const parsed = JSON.parse(rawValue);
    if (
      parsed?.kind === EDITOR_DRAFT_KIND
      && typeof parsed.sourceCode === 'string'
      && Number.isFinite(parsed.savedAt)
      && Number.isFinite(parsed.expiresAt)
    ) {
      return parsed;
    }
  } catch {
    // 기존 문자열 형식의 임시저장 코드는 아래에서 새 형식으로 변환합니다.
  }
  return { legacySourceCode: rawValue };
}

export function readEditorDraft(storage, key, now = Date.now()) {
  const draft = parseEditorDraft(storage.getItem(key));
  if (draft === null) {
    return null;
  }
  if (draft.legacySourceCode !== undefined) {
    writeEditorDraft(storage, key, draft.legacySourceCode, now, true);
    return draft.legacySourceCode;
  }
  if (draft.expiresAt <= now) {
    storage.removeItem(key);
    return null;
  }
  return draft.sourceCode;
}

export function writeEditorDraft(storage, key, sourceCode, now = Date.now(), renew = false) {
  const existing = parseEditorDraft(storage.getItem(key));
  const unchanged = existing?.sourceCode === sourceCode && existing.expiresAt > now;
  if (unchanged && !renew) {
    return;
  }

  storage.setItem(key, JSON.stringify({
    kind: EDITOR_DRAFT_KIND,
    sourceCode,
    savedAt: now,
    expiresAt: now + editorDraftTtlMs,
  }));
}
