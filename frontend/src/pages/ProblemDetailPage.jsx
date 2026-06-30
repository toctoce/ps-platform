import { useEffect, useRef, useState } from 'react';
import Editor from '@monaco-editor/react';
import { BookOpen, ChevronDown, ChevronUp, Code2, Moon, Play, Plus, RefreshCw, Send, Sun } from 'lucide-react';
import { request } from '../api/client.js';
import {
  editorFileName,
  editorLanguage,
  editorLanguageLabel,
  editorStorageKeyPrefix,
  initialSubmissionForm,
  LANGUAGE_OPTIONS,
  LANGUAGE_TEMPLATES,
} from '../editor/config.js';
import { difficultyLabel, problemCategoryNames, problemInputRange } from '../problem/problemUtils.js';
import {
  buildInitialTestRunResults,
  formatDateTime,
  inferTestRunResults,
  isFinishedStatus,
  isPendingStatus,
  statusToLabel,
  submissionBadgeClass,
  submissionMemory,
  submissionNickname,
  submissionTime,
} from '../submission/submissionUtils.js';

export function ProblemDetailPage({ problemId, navigate, setMessage, isAuthenticated, auth }) {
  const [problem, setProblem] = useState(null);
  const [submissions, setSubmissions] = useState([]);
  const [busy, setBusy] = useState(false);
  const [submissionBusy, setSubmissionBusy] = useState(false);
  const [submissionForm, setSubmissionForm] = useState(initialSubmissionForm);
  const [exampleCases, setExampleCases] = useState([]);
  const [testCases, setTestCases] = useState([]);
  const [activeProblemTab, setActiveProblemTab] = useState('problem');
  const [languageMenuOpen, setLanguageMenuOpen] = useState(false);
  const [ideTheme, setIdeTheme] = useState('dark');
  const [testCasePanelOpen, setTestCasePanelOpen] = useState(false);
  const [testCaseForm, setTestCaseForm] = useState({ input: '' });
  const [activeIdeTestCaseIndex, setActiveIdeTestCaseIndex] = useState(0);
  const [testRunBusy, setTestRunBusy] = useState(false);
  const [testRunSubmissionId, setTestRunSubmissionId] = useState(null);
  const [testRunResults, setTestRunResults] = useState([]);
  const [problemPanelRatio, setProblemPanelRatio] = useState(44);
  const pollingRef = useRef({});
  const lastSubmissionStatus = useRef({});
  const editorRef = useRef(null);
  const formRef = useRef(null);
  const runCodeRef = useRef(null);
  const submissionFormRef = useRef(submissionForm);
  const testCasesRef = useRef([]);
  const testRunSubmissionIdRef = useRef(null);
  const languageMenuRef = useRef(null);
  const solveLayoutRef = useRef(null);

  const problemKey = `${editorStorageKeyPrefix}:${problemId}`;
  const currentUsername = auth?.user?.username;

  useEffect(() => {
    submissionFormRef.current = submissionForm;
  }, [submissionForm]);

  useEffect(() => {
    testCasesRef.current = testCases;
  }, [testCases]);

  useEffect(() => {
    testRunSubmissionIdRef.current = testRunSubmissionId;
  }, [testRunSubmissionId]);

  useEffect(() => {
    runCodeRef.current = runCode;
  });

  useEffect(() => {
    function closeLanguageMenu(event) {
      if (languageMenuRef.current?.contains(event.target)) {
        return;
      }
      setLanguageMenuOpen(false);
    }

    document.addEventListener('mousedown', closeLanguageMenu);
    return () => document.removeEventListener('mousedown', closeLanguageMenu);
  }, []);

  function cleanupPolling() {
    Object.keys(pollingRef.current).forEach((key) => {
      clearInterval(pollingRef.current[key]);
      delete pollingRef.current[key];
    });
  }

  function stopPolling(submissionId) {
    const key = String(submissionId);
    const timer = pollingRef.current[key];
    if (timer) {
      clearInterval(timer);
      delete pollingRef.current[key];
    }
  }

  function isMySubmission(submission) {
    if (submission.isMine === true || submission.mine === true) {
      return true;
    }
    if (!currentUsername) {
      return false;
    }
    return submissionNickname(submission) === currentUsername;
  }

  function handleEditorBeforeMount(monaco) {
    monaco.editor.defineTheme('ps-dark', {
      base: 'vs-dark',
      inherit: true,
      rules: [
        { token: 'keyword', foreground: 'c792ea' },
        { token: 'number', foreground: 'f78c6c' },
        { token: 'string', foreground: 'c3e88d' },
        { token: 'comment', foreground: '676e95', fontStyle: 'italic' },
        { token: 'type.identifier', foreground: 'ffcb6b' },
      ],
      colors: {
        'editor.background': '#1e1e1e',
        'editor.foreground': '#d4d4d4',
        'editorLineNumber.foreground': '#6f737b',
        'editorLineNumber.activeForeground': '#c7c7c7',
        'editorCursor.foreground': '#ffffff',
        'editor.selectionBackground': '#3a4a5f',
        'editor.lineHighlightBackground': '#252526',
      },
    });
    monaco.editor.defineTheme('ps-light', {
      base: 'vs',
      inherit: true,
      rules: [
        { token: 'keyword', foreground: '6f42c1' },
        { token: 'number', foreground: 'a14d00' },
        { token: 'string', foreground: '2f7d32' },
        { token: 'comment', foreground: '7c7a6f', fontStyle: 'italic' },
        { token: 'type.identifier', foreground: '8a6116' },
      ],
      colors: {
        'editor.background': '#fffdf8',
        'editor.foreground': '#24271f',
        'editorLineNumber.foreground': '#a49d8c',
        'editorLineNumber.activeForeground': '#5b614f',
        'editorCursor.foreground': '#24271f',
        'editor.selectionBackground': '#dfe7d4',
        'editor.lineHighlightBackground': '#f6f1e7',
      },
    });
  }

  function handleEditorMount(editor, monaco) {
    editorRef.current = editor;
    editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.Enter, () => {
      if (formRef.current) {
        formRef.current.requestSubmit();
      }
    });
    editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.Quote, () => {
      runCodeRef.current?.();
    });
    editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyS, () => {
      const current = submissionFormRef.current;
      const draftKey = `${problemKey}:${current.language}`;
      localStorage.setItem(draftKey, editor.getValue());
      setMessage('임시 저장 완료');
    });
  }

  function updateSubmissionForm(field, value) {
    setSubmissionForm((current) => ({ ...current, [field]: value }));
  }

  function changeSubmissionLanguage(nextLanguage) {
    setSubmissionForm((current) => {
      const isFromTemplate = current.sourceCode.trim() === (LANGUAGE_TEMPLATES[current.language] ?? '').trim();
      return {
        ...current,
        language: nextLanguage,
        sourceCode: isFromTemplate ? LANGUAGE_TEMPLATES[nextLanguage] ?? current.sourceCode : current.sourceCode,
      };
    });
    setLanguageMenuOpen(false);
  }

  function resetEditorCode() {
    setSubmissionForm((current) => ({
      ...current,
      sourceCode: LANGUAGE_TEMPLATES[current.language] ?? current.sourceCode,
    }));
    setMessage('템플릿으로 되돌렸습니다.');
  }

  function authHeaders() {
    return auth?.accessToken
      ? { Authorization: `Bearer ${auth.accessToken}` }
      : {};
  }

  function addTestCaseFromIde(event) {
    event.preventDefault();
    if (testCaseForm.input.trim() === '') {
      setMessage('추가할 테스트케이스를 입력해 주세요.');
      return;
    }

    const created = {
      id: `local-${Date.now()}`,
      input: testCaseForm.input,
      expectedOutput: '',
      description: '',
      localOnly: true,
    };
    setTestCases((current) => {
      setActiveIdeTestCaseIndex(current.length);
      return [...current, created];
    });
    setTestCaseForm({ input: created.input });
    setMessage('테스트케이스를 추가했습니다.');
  }

  function startPanelResize(event) {
    if (!solveLayoutRef.current) {
      return;
    }

    event.preventDefault();
    const layout = solveLayoutRef.current;
    const previousCursor = document.body.style.cursor;
    const previousUserSelect = document.body.style.userSelect;

    document.body.style.cursor = 'col-resize';
    document.body.style.userSelect = 'none';

    function updatePanelRatio(pointerEvent) {
      const rect = layout.getBoundingClientRect();
      const nextRatio = ((pointerEvent.clientX - rect.left) / rect.width) * 100;
      setProblemPanelRatio(Math.min(68, Math.max(32, nextRatio)));
    }

    function stopPanelResize() {
      document.body.style.cursor = previousCursor;
      document.body.style.userSelect = previousUserSelect;
      window.removeEventListener('pointermove', updatePanelRatio);
      window.removeEventListener('pointerup', stopPanelResize);
      window.removeEventListener('pointercancel', stopPanelResize);
    }

    updatePanelRatio(event);
    window.addEventListener('pointermove', updatePanelRatio);
    window.addEventListener('pointerup', stopPanelResize);
    window.addEventListener('pointercancel', stopPanelResize);
  }

  function handlePanelResizeKeyDown(event) {
    if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') {
      return;
    }

    event.preventDefault();
    const direction = event.key === 'ArrowLeft' ? -1 : 1;
    setProblemPanelRatio((current) => Math.min(68, Math.max(32, current + direction * 3)));
  }

  async function runCode() {
    if (!isAuthenticated) {
      setMessage('로그인 후 실행할 수 있습니다.');
      return;
    }
    if (!problem) {
      setMessage('문제를 불러온 뒤 실행할 수 있습니다.');
      return;
    }
    if (testCases.length === 0) {
      setTestCasePanelOpen(true);
      setMessage('실행할 테스트케이스가 없습니다.');
      return;
    }

    setTestCasePanelOpen(true);
    setTestRunBusy(true);
    setTestRunResults(buildInitialTestRunResults(testCases, '실행 중'));
    try {
      const created = await request(`/api/problems/${problemId}/submissions`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(submissionForm),
      });
      const visibleSubmission = {
        ...created,
        username: created.username ?? currentUsername,
        isMine: true,
      };
      testRunSubmissionIdRef.current = visibleSubmission.id;
      setTestRunSubmissionId(visibleSubmission.id);
      lastSubmissionStatus.current[visibleSubmission.id] = visibleSubmission.status;
      setSubmissions((current) => [visibleSubmission, ...current]);
      startPolling(visibleSubmission.id);
    } catch (error) {
      setTestRunBusy(false);
      setTestRunResults([]);
      setMessage(error.message);
    }
  }

  function selectIdeTestCase(index) {
    const selected = testCases[index];
    setActiveIdeTestCaseIndex(index);
    setTestCaseForm({ input: selected?.input ?? '' });
  }

  function prepareNewIdeTestCase() {
    setActiveIdeTestCaseIndex(testCases.length);
    setTestCaseForm({ input: '' });
  }

  async function refreshSubmission(submissionId) {
    try {
      const submission = await request(`/api/submissions/${submissionId}`);
      const previousStatus = lastSubmissionStatus.current[submission.id];
      lastSubmissionStatus.current[submission.id] = submission.status;

      let mergedSubmission = submission;
      setSubmissions((current) => {
        const index = current.findIndex((item) => item.id === submission.id);
        if (index === -1) {
          return [submission, ...current];
        }
        const next = [...current];
        mergedSubmission = { ...next[index], ...submission };
        next[index] = mergedSubmission;
        return next;
      });

      if (testRunSubmissionIdRef.current === submission.id) {
        setTestRunResults(inferTestRunResults(mergedSubmission, testCasesRef.current));
        if (!isPendingStatus(submission.status)) {
          setTestRunBusy(false);
        }
      }

      if (previousStatus !== undefined && isFinishedStatus(previousStatus) && isFinishedStatus(submission.status)) {
        return;
      }

      if (!isPendingStatus(submission.status)) {
        stopPolling(submissionId);
      }
    } catch (error) {
      stopPolling(submissionId);
      setMessage(`채점 상태 조회 실패 (${submissionId}): ${error.message}`);
    }
  }

  function startPolling(submissionId) {
    const key = String(submissionId);
    if (pollingRef.current[key]) {
      return;
    }
    refreshSubmission(submissionId);
    pollingRef.current[key] = setInterval(() => {
      refreshSubmission(submissionId);
    }, 1400);
  }

  async function loadProblem() {
    setBusy(true);
    try {
      const [problemData, submissionData] = await Promise.all([
        request(`/api/problems/${problemId}`),
        request(`/api/problems/${problemId}/submissions`),
      ]);
      const testCaseData = await request(`/api/problems/${problemId}/test-cases`);
      const fetchedSubmissions = Array.isArray(submissionData) ? submissionData : [];

      setProblem(problemData);
      const fetchedTestCases = Array.isArray(testCaseData) ? testCaseData : [];
      setExampleCases(fetchedTestCases);
      setTestCases(fetchedTestCases);
      setActiveIdeTestCaseIndex(0);
      setTestCaseForm({ input: fetchedTestCases[0]?.input ?? '' });
      setSubmissions(fetchedSubmissions);
      lastSubmissionStatus.current = fetchedSubmissions.reduce((acc, submission) => {
        acc[submission.id] = submission.status;
        return acc;
      }, {});
      setMessage(`문제 #${problemId} 상세를 조회했습니다.`);
      cleanupPolling();
      fetchedSubmissions
        .filter((submission) => isPendingStatus(submission.status))
        .forEach((submission) => startPolling(submission.id));
    } catch (error) {
      setProblem(null);
      setSubmissions([]);
      setExampleCases([]);
      setTestCases([]);
      setMessage(error.message);
      cleanupPolling();
    } finally {
      setBusy(false);
    }
  }

  async function submitCode(event) {
    event.preventDefault();
    if (!isAuthenticated) {
      setMessage('로그인 후 제출할 수 있습니다.');
      return;
    }

    setSubmissionBusy(true);
    try {
      const created = await request(`/api/problems/${problemId}/submissions`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(submissionForm),
      });
      const visibleSubmission = {
        ...created,
        username: created.username ?? currentUsername,
        isMine: true,
      };
      lastSubmissionStatus.current[visibleSubmission.id] = visibleSubmission.status;
      setSubmissions((current) => [visibleSubmission, ...current]);
      setActiveProblemTab('submissions');
      setMessage(`제출 #${created.id}이 접수되었습니다.`);
      startPolling(visibleSubmission.id);
    } catch (error) {
      setMessage(error.message);
    } finally {
      setSubmissionBusy(false);
    }
  }

  useEffect(() => {
    loadProblem();
    const draftKey = `${problemKey}:${submissionForm.language}`;
    const saved = localStorage.getItem(draftKey);
    if (saved !== null) {
      setSubmissionForm((current) => ({ ...current, sourceCode: saved }));
    }
    return cleanupPolling;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [problemId]);

  useEffect(() => {
    const draftKey = `${problemKey}:${submissionForm.language}`;
    const saved = localStorage.getItem(draftKey);
    if (saved !== null) {
      setSubmissionForm((current) => ({ ...current, sourceCode: saved }));
    }
  }, [submissionForm.language, problemId, problemKey]);

  useEffect(() => {
    if (!problemId) {
      return;
    }
    const draftKey = `${problemKey}:${submissionForm.language}`;
    localStorage.setItem(draftKey, submissionForm.sourceCode);
  }, [submissionForm.sourceCode, submissionForm.language, problemId, problemKey]);

  return (
    <section className="detail-page">
      <div
        className="solve-layout"
        ref={solveLayoutRef}
        style={{
          '--problem-panel-column': `minmax(360px, ${problemPanelRatio}fr)`,
          '--ide-panel-column': `minmax(420px, ${100 - problemPanelRatio}fr)`,
        }}
      >
        <section className="problem-panel">
          <div className="problem-panel-header">
            <div className="problem-tabs">
              <button
                className={activeProblemTab === 'problem' ? 'is-active' : ''}
                type="button"
                onClick={() => setActiveProblemTab('problem')}
              >
                <BookOpen size={16} />
                문제
              </button>
              <button
                className={activeProblemTab === 'solution' ? 'is-active' : ''}
                type="button"
                onClick={() => setActiveProblemTab('solution')}
              >
                풀이
              </button>
              <button
                className={activeProblemTab === 'submissions' ? 'is-active' : ''}
                type="button"
                onClick={() => setActiveProblemTab('submissions')}
              >
                제출
              </button>
            </div>
          </div>
          <div className="problem-panel-body">
            {activeProblemTab === 'problem' && problem ? (
              <article className="problem-document">
                <header className="problem-title-block">
                  <h1>{problem.id}. {problem.title}</h1>
                  <div className="problem-meta">
                    <span>{difficultyLabel(problem.difficulty)}</span>
                    <span>{problemCategoryNames(problem).join(', ')}</span>
                  </div>
                  <div className="problem-resource-limits">
                    <span>시간 제한 {problem.timeLimitMs}ms</span>
                    <span>메모리 제한 {problem.memoryLimitMb}MB</span>
                  </div>
                </header>

                <section className="problem-section problem-body-section">
                  <p>{problem.description}</p>
                </section>

                <section className="problem-section problem-box-section">
                  <div className="problem-box-part">
                    <h2>입력</h2>
                    <p>{problem.inputDescription}</p>
                  </div>
                  <div className="problem-box-part">
                    <h2>제한</h2>
                    <p>{problemInputRange(problem)}</p>
                  </div>
                </section>

                <section className="problem-section problem-box-section">
                  <h2>출력</h2>
                  <p>{problem.outputDescription}</p>
                </section>

                <section className="problem-section">
                  <h2>예제</h2>
                  {exampleCases.length === 0 ? (
                    <p className="empty">등록된 예제가 없습니다.</p>
                  ) : (
                    <div className="example-list">
                      {exampleCases.map((testCase, index) => (
                        <div className="example-block" key={testCase.id ?? index}>
                          <div className="example-title">예제 {index + 1}</div>
                          <div className="example-cells">
                            <div className="example-cell">
                              <strong>입력</strong>
                              <pre>{testCase.input}</pre>
                            </div>
                            <div className="example-cell">
                              <strong>출력</strong>
                              <pre>{testCase.expectedOutput}</pre>
                            </div>
                          </div>
                          <details className="example-description">
                            <summary>설명</summary>
                            <p>{testCase.description ?? '예제 설명이 등록되지 않았습니다.'}</p>
                          </details>
                        </div>
                      ))}
                    </div>
                  )}
                </section>
              </article>
            ) : activeProblemTab === 'submissions' ? (
              <section className="submission-tab">
                {submissions.length === 0 ? (
                  <p className="empty">아직 제출이 없습니다.</p>
                ) : (
                  <div className="submission-table">
                    <div className="submission-table-head">
                      <span>닉네임</span>
                      <span>결과</span>
                      <span>시간</span>
                      <span>메모리</span>
                      <span>언어</span>
                      <span>제출 날짜</span>
                    </div>
                    {submissions.map((submission) => (
                      <div
                        className={`submission-table-row ${isMySubmission(submission) ? 'is-mine' : ''}`}
                        key={submission.id}
                      >
                        <span>{submissionNickname(submission)}</span>
                        <strong className={`submission-badge ${submissionBadgeClass(submission.status)}`}>
                          {statusToLabel(submission.status, submission.verdict)}
                        </strong>
                        <span>{submissionTime(submission)}</span>
                        <span>{submissionMemory(submission)}</span>
                        <span>{submission.language}</span>
                        <span>{formatDateTime(submission.createdAt)}</span>
                      </div>
                    ))}
                  </div>
                )}
              </section>
            ) : activeProblemTab === 'solution' ? (
              <p className="empty">풀이 기능은 추가 개발 예정입니다.</p>
            ) : (
              <p className="empty">문제를 불러오지 못했습니다.</p>
            )}
          </div>
        </section>

        <button
          className="panel-resizer"
          type="button"
          role="separator"
          onPointerDown={startPanelResize}
          onKeyDown={handlePanelResizeKeyDown}
          aria-label="문제 영역과 코드 편집기 너비 조절"
          aria-orientation="vertical"
          aria-valuemin={32}
          aria-valuemax={68}
          aria-valuenow={Math.round(problemPanelRatio)}
        />

        <section className={`ide-panel ${ideTheme === 'light' ? 'is-light' : ''}`}>
          <div className="ide-header">
            <div className="ide-language-control" ref={languageMenuRef}>
              <Code2 size={18} />
              <button
                className="language-menu-button"
                type="button"
                onClick={() => setLanguageMenuOpen((current) => !current)}
                aria-expanded={languageMenuOpen}
                aria-haspopup="menu"
              >
                {editorLanguageLabel(submissionForm.language)}
                <ChevronDown size={14} />
              </button>
              {languageMenuOpen ? (
                <div className="language-menu" role="menu">
                  {LANGUAGE_OPTIONS.map((language) => (
                    <button
                      className={language.value === submissionForm.language ? 'is-active' : ''}
                      type="button"
                      role="menuitem"
                      key={language.value}
                      onClick={() => changeSubmissionLanguage(language.value)}
                    >
                      {language.label}
                    </button>
                  ))}
                </div>
              ) : null}
            </div>
            <div className="ide-file-actions">
              <div className="ide-theme-toggle" aria-label="IDE 테마">
                <button
                  className={ideTheme === 'light' ? 'is-active' : ''}
                  type="button"
                  onClick={() => setIdeTheme('light')}
                  title="라이트 모드"
                  aria-label="라이트 모드"
                >
                  <Sun size={15} />
                </button>
                <button
                  className={ideTheme === 'dark' ? 'is-active' : ''}
                  type="button"
                  onClick={() => setIdeTheme('dark')}
                  title="다크 모드"
                  aria-label="다크 모드"
                >
                  <Moon size={15} />
                </button>
              </div>
              <span className="editor-file-name">{editorFileName(submissionForm.language)}</span>
              <button
                className="editor-reset-button"
                type="button"
                onClick={resetEditorCode}
                title="초기화"
                aria-label="초기화"
              >
                <RefreshCw size={14} />
              </button>
            </div>
          </div>
          <form className="submission-form" onSubmit={submitCode} ref={formRef}>
            <div className="editor-shell">
              <div className="monaco-editor-frame">
                <Editor
                  height="100%"
                  language={editorLanguage(submissionForm.language)}
                  value={submissionForm.sourceCode}
                  theme={ideTheme === 'light' ? 'ps-light' : 'ps-dark'}
                  beforeMount={handleEditorBeforeMount}
                  onMount={handleEditorMount}
                  onChange={(value) => updateSubmissionForm('sourceCode', value ?? '')}
                  options={{
                    automaticLayout: true,
                    autoClosingBrackets: 'always',
                    autoClosingQuotes: 'always',
                    bracketPairColorization: { enabled: true },
                    cursorBlinking: 'smooth',
                    detectIndentation: false,
                    fontFamily: '"SFMono-Regular", Consolas, "Liberation Mono", monospace',
                    fontSize: 14,
                    formatOnPaste: true,
                    hover: { enabled: false },
                    insertSpaces: true,
                    lineHeight: 22,
                    minimap: { enabled: false },
                    parameterHints: { enabled: false },
                    padding: { top: 12, bottom: 12 },
                    quickSuggestions: false,
                    renderLineHighlight: 'line',
                    scrollBeyondLastLine: false,
                    smoothScrolling: true,
                    suggestOnTriggerCharacters: false,
                    tabSize: 4,
                    wordWrap: 'off',
                  }}
                />
              </div>
            </div>

            <div className="ide-bottom-dock">
              {testCasePanelOpen ? (
                <section className="ide-testcase-panel">
                  <div className="ide-testcase-tabs">
                    {testCases.map((testCase, index) => {
                      const result = testRunResults.find((item) => item.index === index + 1);
                      return (
                        <button
                          className={activeIdeTestCaseIndex === index ? 'is-active' : ''}
                          type="button"
                          key={testCase.id ?? index}
                          onClick={() => selectIdeTestCase(index)}
                        >
                          #{index + 1}
                          {result ? (
                            <span className={`testcase-dot ${result.status === '성공' ? 'is-pass' : result.status === '실패' ? 'is-fail' : ''}`} />
                          ) : null}
                        </button>
                      );
                    })}
                    <button
                      className={activeIdeTestCaseIndex === testCases.length ? 'is-active' : ''}
                      type="button"
                      onClick={prepareNewIdeTestCase}
                      title="테스트케이스 추가"
                    >
                      <Plus size={14} />
                    </button>
                  </div>
                  <div className="ide-testcase-action-row">
                    <button
                      className="ghost-button ide-small-button has-tooltip"
                      type="button"
                      onClick={runCode}
                      disabled={testRunBusy || submissionBusy || !problem}
                      data-tooltip="Cmd/Ctrl + ' 로 실행"
                    >
                      <Play size={14} />
                      실행
                    </button>
                  </div>
                  <div className="ide-testcase-editor">
                    <label>
                      입력
                      <textarea
                        value={testCaseForm.input}
                        onChange={(event) => setTestCaseForm({ input: event.target.value })}
                        readOnly={activeIdeTestCaseIndex < testCases.length}
                        placeholder="예: 1 2"
                      />
                    </label>
                    {activeIdeTestCaseIndex === testCases.length ? (
                      <button
                        className="ghost-button ide-small-button"
                        type="button"
                        onClick={addTestCaseFromIde}
                      >
                        <Plus size={14} />
                        추가
                      </button>
                    ) : null}
                  </div>
                  {(() => {
                    const result = testRunResults.find((item) => item.index === activeIdeTestCaseIndex + 1);
                    if (!result) {
                      return null;
                    }
                    return (
                      <div className="ide-testcase-result-line">
                        <span className={`testcase-result ${result.status === '성공' ? 'is-pass' : result.status === '실패' ? 'is-fail' : ''}`}>
                          {result.status}
                        </span>
                        {result.message ? <p>{result.message}</p> : null}
                      </div>
                    );
                  })()}
                </section>
              ) : null}

              <div className="ide-actions">
                <button
                  className="ghost-button ide-small-button has-tooltip"
                  type="button"
                  onClick={() => setTestCasePanelOpen((current) => !current)}
                  data-tooltip="테스트케이스 패널 열기/닫기"
                >
                  {testCasePanelOpen ? <ChevronDown size={14} /> : <ChevronUp size={14} />}
                  테스트케이스
                </button>
                <button
                  className="primary-button ide-small-button has-tooltip"
                  type="submit"
                  disabled={submissionBusy || !problem}
                  data-tooltip="Cmd/Ctrl + Enter 로 제출"
                >
                  <Send size={14} />
                  제출
                </button>
              </div>
            </div>
          </form>

        </section>
      </div>
    </section>
  );
}
