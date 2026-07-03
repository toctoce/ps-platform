import { useEffect, useRef, useState } from 'react';
import Editor from '@monaco-editor/react';
import { BookOpen, ChevronDown, ChevronUp, Code2, Moon, Play, Plus, RefreshCw, Send, Sun } from 'lucide-react';
import { request } from '../api/client.js';
import { MarkdownContent } from '../components/MarkdownContent.jsx';
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
  isFinishedStatus,
  isPendingStatus,
  mapCodeRunResults,
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
  const [testCaseForm, setTestCaseForm] = useState({ input: '', expectedOutput: '' });
  const [activeIdeTestCaseIndex, setActiveIdeTestCaseIndex] = useState(0);
  const [testRunBusy, setTestRunBusy] = useState(false);
  const [testRunJobId, setTestRunJobId] = useState(null);
  const [testRunResults, setTestRunResults] = useState([]);
  const [problemPanelRatio, setProblemPanelRatio] = useState(44);
  const [isNarrowLayout, setIsNarrowLayout] = useState(false);
  const [editorOnlyMode, setEditorOnlyMode] = useState(false);
  const [testCaseInputHeight, setTestCaseInputHeight] = useState(58);
  const [activeTooltip, setActiveTooltip] = useState(null);
  const pollingRef = useRef({});
  const lastSubmissionStatus = useRef({});
  const editorRef = useRef(null);
  const formRef = useRef(null);
  const runCodeRef = useRef(null);
  const submissionFormRef = useRef(submissionForm);
  const testCasesRef = useRef([]);
  const testRunJobIdRef = useRef(null);
  const languageMenuRef = useRef(null);
  const solveLayoutRef = useRef(null);
  const testCaseInputRef = useRef(null);

  const problemKey = `${editorStorageKeyPrefix}:${problemId}`;
  const currentUsername = auth?.user?.username;

  function showTooltip(event, text) {
    const rect = event.currentTarget.getBoundingClientRect();
    const x = Math.min(Math.max(rect.left + rect.width / 2, 16), window.innerWidth - 16);
    const y = rect.top - 8;
    setActiveTooltip({ text, x, y });
  }

  function hideTooltip() {
    setActiveTooltip(null);
  }

  useEffect(() => {
    submissionFormRef.current = submissionForm;
  }, [submissionForm]);

  useEffect(() => {
    testCasesRef.current = testCases;
  }, [testCases]);

  useEffect(() => {
    testRunJobIdRef.current = testRunJobId;
  }, [testRunJobId]);

  useEffect(() => {
    runCodeRef.current = runCode;
  });

  useEffect(() => {
    function handleGlobalShortcut(event) {
      if (!(event.metaKey || event.ctrlKey) || event.key.toLowerCase() !== 'j') {
        return;
      }

      event.preventDefault();
      setTestCasePanelOpen((current) => !current);
    }

    document.addEventListener('keydown', handleGlobalShortcut);
    return () => document.removeEventListener('keydown', handleGlobalShortcut);
  }, []);

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

  useEffect(() => {
    const mediaQuery = window.matchMedia('(max-width: 1040px)');
    const applyResponsiveMode = () => {
      const isSmall = mediaQuery.matches;
      setIsNarrowLayout(isSmall);
      if (!isSmall) {
        setEditorOnlyMode(false);
      }
    };

    applyResponsiveMode();

    if (typeof mediaQuery.addEventListener === 'function') {
      mediaQuery.addEventListener('change', applyResponsiveMode);
      return () => mediaQuery.removeEventListener('change', applyResponsiveMode);
    }
    mediaQuery.addListener(applyResponsiveMode);
    return () => mediaQuery.removeListener(applyResponsiveMode);
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
    editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyJ, () => {
      setTestCasePanelOpen((current) => !current);
    });
    editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyS, () => {
      const current = submissionFormRef.current;
      const draftKey = `${problemKey}:${current.language}`;
      localStorage.setItem(draftKey, editor.getValue());
      setMessage('임시 저장 완료');
    });
  }

  function startTestCaseInputResize(event) {
    event.preventDefault();

    const input = testCaseInputRef.current;
    if (!input) {
      return;
    }

    const startY = event.clientY;
    const startHeight = input.getBoundingClientRect().height;

    function resizeInput(pointerEvent) {
      const nextHeight = startHeight + (startY - pointerEvent.clientY);
      setTestCaseInputHeight(Math.max(58, Math.min(240, nextHeight)));
    }

    function stopResize() {
      document.removeEventListener('pointermove', resizeInput);
      document.removeEventListener('pointerup', stopResize);
    }

    document.addEventListener('pointermove', resizeInput);
    document.addEventListener('pointerup', stopResize);
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
    if (testCaseForm.expectedOutput.trim() === '') {
      setMessage('예상 출력을 입력해 주세요.');
      return;
    }

    const created = {
      id: `local-${Date.now()}`,
      input: testCaseForm.input,
      expectedOutput: testCaseForm.expectedOutput,
      description: '',
      localOnly: true,
    };
    setTestCases((current) => {
      setActiveIdeTestCaseIndex(current.length);
      return [...current, created];
    });
    setTestCaseForm({ input: created.input, expectedOutput: created.expectedOutput });
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
      const created = await request(`/api/problems/${problemId}/runs`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...authHeaders(),
        },
        body: JSON.stringify({
          ...submissionForm,
          testCases: testCases.map((testCase) => ({
            input: testCase.input,
            expectedOutput: testCase.expectedOutput,
          })),
        }),
      });
      testRunJobIdRef.current = created.jobId;
      setTestRunJobId(created.jobId);
      startRunPolling(created.jobId);
    } catch (error) {
      setTestRunBusy(false);
      setTestRunResults([]);
      setMessage(error.message);
    }
  }

  function selectIdeTestCase(index) {
    const selected = testCases[index];
    setActiveIdeTestCaseIndex(index);
    setTestCaseForm({ input: selected?.input ?? '', expectedOutput: selected?.expectedOutput ?? '' });
  }

  function prepareNewIdeTestCase() {
    setActiveIdeTestCaseIndex(testCases.length);
    setTestCaseForm({ input: '', expectedOutput: '' });
  }

  async function refreshCodeRun(jobId) {
    try {
      const codeRun = await request(`/api/runs/${jobId}`);
      if (testRunJobIdRef.current === jobId) {
        setTestRunResults(mapCodeRunResults(codeRun, testCasesRef.current));
        if (!isPendingStatus(codeRun.status)) {
          setTestRunBusy(false);
          stopRunPolling(jobId);
        }
      }
    } catch (error) {
      stopRunPolling(jobId);
      setTestRunBusy(false);
      setMessage(`실행 결과 조회 실패 (${jobId}): ${error.message}`);
    }
  }

  function stopRunPolling(jobId) {
    const key = `run-${jobId}`;
    const timer = pollingRef.current[key];
    if (timer) {
      clearInterval(timer);
      delete pollingRef.current[key];
    }
  }

  function startRunPolling(jobId) {
    const key = `run-${jobId}`;
    if (pollingRef.current[key]) {
      return;
    }
    refreshCodeRun(jobId);
    pollingRef.current[key] = setInterval(() => {
      refreshCodeRun(jobId);
    }, 1000);
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
      const [testCaseData, exampleData] = await Promise.all([
        request(`/api/problems/${problemId}/test-cases`),
        request(`/api/problems/${problemId}/examples`),
      ]);
      const fetchedSubmissions = Array.isArray(submissionData) ? submissionData : [];

      setProblem(problemData);
      const fetchedTestCases = Array.isArray(testCaseData) ? testCaseData : [];
      const fetchedExamples = Array.isArray(exampleData) ? exampleData : [];
      setExampleCases(fetchedExamples);
      setTestCases(fetchedTestCases);
      setActiveIdeTestCaseIndex(0);
      setTestCaseForm({
        input: fetchedTestCases[0]?.input ?? '',
        expectedOutput: fetchedTestCases[0]?.expectedOutput ?? '',
      });
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
        headers: {
          'Content-Type': 'application/json',
          ...authHeaders(),
        },
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
    setEditorOnlyMode(false);
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
        className={`solve-layout ${isNarrowLayout ? (editorOnlyMode ? 'is-editor-only' : 'is-problem-only') : ''}`}
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
            {isNarrowLayout && !editorOnlyMode ? (
              <button
                className="problem-editor-open-button"
                type="button"
                onClick={() => setEditorOnlyMode(true)}
              >
                {'<<'} 에디터 열기
              </button>
            ) : null}
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
                          {testCase.description ? (
                            <details className="example-description">
                              <summary>설명</summary>
                              <p>{testCase.description}</p>
                            </details>
                          ) : null}
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
              <section className="solution-tab">
                <MarkdownContent source={problem?.solution} emptyMessage="등록된 풀이가 없습니다." />
              </section>
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
            {isNarrowLayout ? (
              <button
                className="problem-editor-open-button is-back"
                type="button"
                onClick={() => setEditorOnlyMode(false)}
              >
                에디터 닫기 {'>>'}
              </button>
            ) : null}
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
                  <button
                    className="ide-testcase-resize-handle"
                    type="button"
                    onPointerDown={startTestCaseInputResize}
                    aria-label="테스트케이스 입력 높이 조절"
                  />
                  <div className="ide-testcase-top-row">
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
                        data-tooltip="⌘ / Ctrl + '"
                        onMouseEnter={(event) => showTooltip(event, "⌘ / Ctrl + '")}
                        onFocus={(event) => showTooltip(event, "⌘ / Ctrl + '")}
                        onMouseLeave={hideTooltip}
                        onBlur={hideTooltip}
                      >
                        <Play size={14} />
                        실행
                      </button>
                    </div>
                  </div>

                  <div className="ide-testcase-editor">
                    <div className="ide-testcase-fields">
                      <label>
                        입력
                        <textarea
                          ref={testCaseInputRef}
                          style={{ height: `${testCaseInputHeight}px` }}
                          value={testCaseForm.input}
                          onChange={(event) => setTestCaseForm((current) => ({ ...current, input: event.target.value }))}
                          readOnly={activeIdeTestCaseIndex < testCases.length}
                          placeholder="예: 1 2"
                        />
                      </label>
                      <label>
                        예상 출력
                        <textarea
                          value={testCaseForm.expectedOutput}
                          onChange={(event) => setTestCaseForm((current) => ({ ...current, expectedOutput: event.target.value }))}
                          readOnly={activeIdeTestCaseIndex < testCases.length}
                          placeholder="예: 3"
                        />
                      </label>
                    </div>
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
                  className="ide-testcase-toggle has-tooltip"
                  type="button"
                  onClick={() => setTestCasePanelOpen((current) => !current)}
                  data-tooltip="⌘ / Ctrl + J"
                  onMouseEnter={(event) => showTooltip(event, '⌘ / Ctrl + J')}
                  onFocus={(event) => showTooltip(event, '⌘ / Ctrl + J')}
                  onMouseLeave={hideTooltip}
                  onBlur={hideTooltip}
                >
                  {testCasePanelOpen ? <ChevronDown size={14} /> : <ChevronUp size={14} />}
                  테스트케이스
                </button>
                <button
                  className="primary-button ide-small-button has-tooltip"
                  type="submit"
                  disabled={submissionBusy || !problem}
                  data-tooltip="⌘ / Ctrl + Enter"
                  onMouseEnter={(event) => showTooltip(event, '⌘ / Ctrl + Enter')}
                  onFocus={(event) => showTooltip(event, '⌘ / Ctrl + Enter')}
                  onMouseLeave={hideTooltip}
                  onBlur={hideTooltip}
                >
                  <Send size={14} />
                  제출
                </button>
              </div>
            </div>
          </form>
          </section>
      </div>
      {activeTooltip ? (
        <div
          className="global-tooltip"
          style={{ left: `${activeTooltip.x}px`, top: `${activeTooltip.y}px` }}
          role="tooltip"
        >
          {activeTooltip.text}
        </div>
      ) : null}
    </section>
  );
}
