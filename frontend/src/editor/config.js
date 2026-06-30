export const editorStorageKeyPrefix = 'ps-platform-editor';

export const LANGUAGE_TEMPLATES = {
  java: `import java.io.*;
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
  python: `import sys

def main():
    input_data = sys.stdin.readline().rstrip()
    if not input_data:
        return
    a, b = map(int, input_data.split())
    print(a + b)


if __name__ == "__main__":
    main()
`,
  cpp: `#include <bits/stdc++.h>
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
};

export const initialSubmissionForm = {
  language: 'java',
  sourceCode: LANGUAGE_TEMPLATES.java,
};

export const LANGUAGE_OPTIONS = [
  { value: 'java', label: 'Java' },
  { value: 'python', label: 'Python' },
  { value: 'cpp', label: 'C++' },
];

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
