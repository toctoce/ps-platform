const CLANG_STYLES = {
  java: JSON.stringify({
    BasedOnStyle: 'Google',
    AllowShortFunctionsOnASingleLine: 'None',
    ColumnLimit: 100,
    ContinuationIndentWidth: 4,
    IndentWidth: 4,
    UseTab: 'Never',
  }),
  cpp: JSON.stringify({
    BasedOnStyle: 'LLVM',
    AllowShortFunctionsOnASingleLine: 'None',
    ColumnLimit: 100,
    ContinuationIndentWidth: 4,
    IndentWidth: 4,
    UseTab: 'Never',
  }),
};

let clangFormatterPromise;
let pythonFormatterPromise;

function loadClangFormatter() {
  if (!clangFormatterPromise) {
    clangFormatterPromise = import('@wasm-fmt/clang-format/vite').then(async (module) => {
      await module.default();
      return module.format;
    });
  }
  return clangFormatterPromise;
}

function loadPythonFormatter() {
  if (!pythonFormatterPromise) {
    pythonFormatterPromise = import('@wasm-fmt/ruff_fmt/vite').then(async (module) => {
      await module.default();
      return module.format;
    });
  }
  return pythonFormatterPromise;
}

async function formatSource(language, sourceCode) {
  if (language === 'python') {
    const format = await loadPythonFormatter();
    return format(sourceCode, 'main.py', {
      indent_style: 'space',
      indent_width: 4,
      line_width: 88,
      magic_trailing_comma: 'respect',
      quote_style: 'double',
    });
  }

  if (language === 'java' || language === 'cpp') {
    const format = await loadClangFormatter();
    const fileName = language === 'java' ? 'Main.java' : 'main.cpp';
    return format(sourceCode, fileName, CLANG_STYLES[language]);
  }

  throw new Error(`지원하지 않는 언어입니다: ${language}`);
}

self.addEventListener('message', async (event) => {
  const { id, language, sourceCode } = event.data;
  try {
    const formattedSource = await formatSource(language, sourceCode);
    self.postMessage({ id, formattedSource });
  } catch (error) {
    self.postMessage({
      id,
      error: error instanceof Error ? error.message : '코드 포맷팅에 실패했습니다.',
    });
  }
});
