import { useEffect, useRef, useState } from 'react';
import Editor from '@monaco-editor/react';
import solarizedLightTheme from '../editor/themes/Solarized-light.json';
import krTheme from '../editor/themes/krTheme.json';
import { BookOpen, ChevronDown, ChevronUp, Code2, Moon, Play, Plus, Send, Siren, Sun, X } from 'lucide-react';
import { request } from '../api/client.js';
import { ConstraintMath } from '../components/ConstraintMath.jsx';
import { MarkdownContent } from '../components/MarkdownContent.jsx';
import {
  editorFileName,
  editorLanguage,
  editorLanguageLabel,
  editorStorageKeyPrefix,
  initialSubmissionForm,
  isTemplateSource,
  LANGUAGE_OPTIONS,
  LANGUAGE_TEMPLATES,
  normalizeTemplateSource,
} from '../editor/config.js';
import { difficultyClassName, difficultyLabel, problemCategoryNames, problemInputRangeItems } from '../problem/problemUtils.js';
import { PROBLEM_REPORT_TYPES, problemReportTypeLabel } from '../problem/reportUtils.js';
import {
  buildInitialTestRunResults,
  formatSubmissionDate,
  isFinishedStatus,
  isPendingStatus,
  mapCodeRunResults,
  statusToLabel,
  submissionBadgeClass,
  submissionLanguageLabel,
  submissionMemory,
  submissionNickname,
  submissionTime,
} from '../submission/submissionUtils.js';

function defineEditorThemes(monaco) {
  monaco.editor.defineTheme('ps-dark', krTheme);
  monaco.editor.defineTheme('ps-light', solarizedLightTheme);
}

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
  const [reportPanelOpen, setReportPanelOpen] = useState(false);
  const [reportBusy, setReportBusy] = useState(false);
  const [reportForm, setReportForm] = useState({ type: 'DESCRIPTION_ERROR', message: '' });
  const [reportTypeMenuOpen, setReportTypeMenuOpen] = useState(false);
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
  const reportTypeMenuRef = useRef(null);
  const solveLayoutRef = useRef(null);
  const testCaseInputRef = useRef(null);

  const problemKey = `${editorStorageKeyPrefix}:${problemId}`;
  const currentUsername = auth?.user?.username;
  const selectedReportTypeLabel = problemReportTypeLabel(reportForm.type);

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
    function closeFloatingMenus(event) {
      if (!languageMenuRef.current?.contains(event.target)) {
        setLanguageMenuOpen(false);
      }
      if (!reportTypeMenuRef.current?.contains(event.target)) {
        setReportTypeMenuOpen(false);
      }
    }

    document.addEventListener('mousedown', closeFloatingMenus);
    return () => document.removeEventListener('mousedown', closeFloatingMenus);
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

  function hasAcceptedMySubmission() {
    return submissions.some((submission) => (
      isMySubmission(submission)
      && submission.status === 'JUDGED'
      && submission.verdict === 'ACCEPTED'
    ));
  }

  function openSolutionTab() {
    if (activeProblemTab === 'solution') {
      return;
    }
    if (!hasAcceptedMySubmission()) {
      const confirmed = window.confirm('아직 이 문제의 정답을 맞추지 못했습니다. 그래도 풀이를 보시겠습니까?');
      if (!confirmed) {
        return;
      }
    }
    setActiveProblemTab('solution');
  }

  function handleEditorBeforeMount(monaco) {
    defineEditorThemes(monaco);
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

  function updateReportForm(field, value) {
    setReportForm((current) => ({ ...current, [field]: value }));
  }

  function closeProblemReportModal() {
    setReportPanelOpen(false);
    setReportTypeMenuOpen(false);
  }

  function changeSubmissionLanguage(nextLanguage) {
    setSubmissionForm((current) => {
      const isFromTemplate = isTemplateSource(current.language, current.sourceCode);
      return {
        ...current,
        language: nextLanguage,
        sourceCode: isFromTemplate ? LANGUAGE_TEMPLATES[nextLanguage] ?? current.sourceCode : current.sourceCode,
      };
    });
    setLanguageMenuOpen(false);
  }

  function toggleIdeTheme() {
    setIdeTheme((current) => (current === 'light' ? 'dark' : 'light'));
  }

  function authHeaders() {
    return auth?.accessToken
      ? { Authorization: `Bearer ${auth.accessToken}` }
      : {};
  }

  function loadSavedSourceCode(language) {
    const draftKey = `${problemKey}:${language}`;
    const saved = localStorage.getItem(draftKey);
    if (saved === null) {
      return null;
    }

    const normalized = normalizeTemplateSource(language, saved);
    if (normalized !== saved) {
      localStorage.setItem(draftKey, normalized);
    }
    return normalized;
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

  async function submitProblemReport(event) {
    event.preventDefault();
    if (!isAuthenticated) {
      const returnTo = `${window.location.pathname}${window.location.search}`;
      setMessage('로그인 후 신고할 수 있습니다.');
      navigate(`/login?returnTo=${encodeURIComponent(returnTo)}`);
      return;
    }
    if (!reportForm.message.trim()) {
      setMessage('신고 내용을 입력해 주세요.');
      return;
    }

    setReportBusy(true);
    try {
      await request(`/api/problems/${problemId}/reports`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...authHeaders() },
        body: JSON.stringify({
          type: reportForm.type,
          message: reportForm.message.trim(),
        }),
      });
      setMessage('문제 신고가 접수되었습니다.');
      setReportForm({ type: 'DESCRIPTION_ERROR', message: '' });
      closeProblemReportModal();
    } catch (error) {
      setMessage(error.message);
    } finally {
      setReportBusy(false);
    }
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
      setMessage(error.message);
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
      setMessage(error.message);
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
      const exampleData = await request(`/api/problems/${problemId}/examples`);
      const fetchedSubmissions = Array.isArray(submissionData) ? submissionData : [];

      setProblem(problemData);
      const fetchedExamples = Array.isArray(exampleData) ? exampleData : [];
      setExampleCases(fetchedExamples);
      setTestCases(fetchedExamples);
      setActiveIdeTestCaseIndex(0);
      setTestCaseForm({
        input: fetchedExamples[0]?.input ?? '',
        expectedOutput: fetchedExamples[0]?.expectedOutput ?? '',
      });
      setSubmissions(fetchedSubmissions);
      lastSubmissionStatus.current = fetchedSubmissions.reduce((acc, submission) => {
        acc[submission.id] = submission.status;
        return acc;
      }, {});
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
      setMessage('제출이 접수되었습니다.');
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
    const saved = loadSavedSourceCode(submissionForm.language);
    if (saved !== null) {
      setSubmissionForm((current) => ({ ...current, sourceCode: saved }));
    }
    return cleanupPolling;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [problemId]);

  useEffect(() => {
    const saved = loadSavedSourceCode(submissionForm.language);
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
                onClick={openSolutionTab}
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
              <button
                className="problem-report-tab-button"
                type="button"
                aria-label="문제 신고"
                title="문제 신고"
                onClick={() => setReportPanelOpen(true)}
                disabled={!problem}
              >
                <Siren size={15} />
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
                  <div className="problem-title-line">
                    <h1>{problem.id}. {problem.title}</h1>
                    <span className={difficultyClassName(problem.difficulty)}>
                      {difficultyLabel(problem.difficulty)}
                    </span>
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
                    <ul className="limit-list">
                      {problemInputRangeItems(problem).map((item, index) => (
                        <li key={`${item}-${index}`}>
                          <ConstraintMath value={item} />
                        </li>
                      ))}
                    </ul>
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

                <footer className="problem-footer-meta">
                  <div className="problem-resource-limits">
                    <span>시간 제한 {problem.timeLimitMs}ms</span>
                    <span>메모리 제한 {problem.memoryLimitMb}MB</span>
                  </div>
                  <details className="problem-category-details">
                    <summary>카테고리</summary>
                    <div className="category-chip-list">
                      {problemCategoryNames(problem).map((category) => (
                        <span className="category-chip" key={category}>{category}</span>
                      ))}
                    </div>
                  </details>
                </footer>
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
                        <strong className={`submission-badge ${submissionBadgeClass(submission.status, submission.verdict)}`}>
                          {statusToLabel(submission.status, submission.verdict)}
                        </strong>
                        <span>{submissionTime(submission)}</span>
                        <span>{submissionMemory(submission)}</span>
                        <span>{submissionLanguageLabel(submission.language)}</span>
                        <span>{formatSubmissionDate(submission.createdAt)}</span>
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
            <button
              className="ide-theme-toggle"
              type="button"
              onClick={toggleIdeTheme}
              title="테마 변경"
              aria-label="테마 변경"
            >
              <span className={ideTheme === 'light' ? 'is-active' : ''}>
                <Sun size={15} />
              </span>
              <span className={ideTheme === 'dark' ? 'is-active' : ''}>
                <Moon size={15} />
              </span>
            </button>
            <span className="editor-file-name">{editorFileName(submissionForm.language)}</span>
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
                        />
                      </label>
                      <label>
                        예상 출력
                        <textarea
                          value={testCaseForm.expectedOutput}
                          onChange={(event) => setTestCaseForm((current) => ({ ...current, expectedOutput: event.target.value }))}
                          readOnly={activeIdeTestCaseIndex < testCases.length}
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
                <div className="ide-submit-actions">
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
            </div>
          </form>
          </section>
      </div>
      {reportPanelOpen ? (
        <div
          className="problem-report-modal-backdrop"
          role="presentation"
          onMouseDown={closeProblemReportModal}
        >
          <section
            className="problem-report-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="problem-report-title"
            onMouseDown={(event) => event.stopPropagation()}
          >
            <header className="problem-report-modal-header">
              <h2 id="problem-report-title">문제 신고</h2>
              <button
                className="ghost-button ide-small-button"
                type="button"
                aria-label="신고 모달 닫기"
                onClick={closeProblemReportModal}
              >
                <X size={14} />
              </button>
            </header>
            <form className="problem-report-form" onSubmit={submitProblemReport}>
              <div className="problem-report-field">
                <span>신고 종류</span>
                <div className="problem-report-type-control" ref={reportTypeMenuRef}>
                  <button
                    className="problem-report-type-button"
                    type="button"
                    onClick={() => setReportTypeMenuOpen((current) => !current)}
                    aria-expanded={reportTypeMenuOpen}
                    aria-haspopup="menu"
                  >
                    {selectedReportTypeLabel}
                    <ChevronDown size={14} />
                  </button>
                  {reportTypeMenuOpen ? (
                    <div className="problem-report-type-menu" role="menu">
                      {PROBLEM_REPORT_TYPES.map((item) => (
                        <button
                          className={reportForm.type === item.value ? 'is-active' : ''}
                          type="button"
                          role="menuitem"
                          key={item.value}
                          onClick={() => {
                            updateReportForm('type', item.value);
                            setReportTypeMenuOpen(false);
                          }}
                        >
                          {item.label}
                        </button>
                      ))}
                    </div>
                  ) : null}
                </div>
              </div>
              <label>
                신고 내용
                <textarea
                  value={reportForm.message}
                  onChange={(event) => updateReportForm('message', event.target.value)}
                  placeholder="문제를 확인할 수 있도록 오류 내용을 구체적으로 적어주세요."
                />
              </label>
              <div className="problem-report-actions">
                <button className="ghost-button" type="button" onClick={closeProblemReportModal}>
                  취소
                </button>
                <button className="primary-button" type="submit" disabled={reportBusy}>
                  신고 접수
                </button>
              </div>
            </form>
          </section>
        </div>
      ) : null}
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
