import { StrictMode, useEffect, useMemo, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { Activity, ArrowLeft, Code2, FileText, Plus, RefreshCw, Send, X } from 'lucide-react';
import './styles.css';

const initialForm = {
  title: 'A+B',
  description: '두 수를 더하는 문제',
  inputDescription: '정수 A와 B가 주어진다.',
  outputDescription: 'A+B를 출력한다.',
  timeLimitMs: 1000,
  memoryLimitMb: 128,
  testCases: [
    {
      input: '1 2\n',
      expectedOutput: '3\n',
    },
  ],
};

const initialTestCaseForm = {
  input: '1 2\n',
  expectedOutput: '3\n',
};

const initialSubmissionForm = {
  language: 'java',
  sourceCode: `import java.io.*;
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
};

const authStorageKey = 'ps-platform-auth';

function getStoredAuth() {
  const raw = localStorage.getItem(authStorageKey);
  if (!raw) {
    return null;
  }

  try {
    const parsed = JSON.parse(raw);
    return {
      accessToken: parsed.accessToken || '',
      refreshToken: parsed.refreshToken || '',
      user: parsed.user || null,
    };
  } catch (_error) {
    return null;
  }
}

function saveStoredAuth(auth) {
  localStorage.setItem(authStorageKey, JSON.stringify(auth));
}

function clearStoredAuth() {
  localStorage.removeItem(authStorageKey);
}

function parseRoute() {
  const path = window.location.pathname.replace(/\/+$/, '');
  if (path === '/login') {
    return { name: 'login' };
  }

  const detailMatch = path.match(/^\/problems\/(\d+)$/);

  if (detailMatch) {
    return { name: 'problem-detail', problemId: detailMatch[1] };
  }

  return { name: 'problem-list' };
}

async function request(path, options) {
  const response = await fetch(path, options);
  if (!response.ok) {
    const body = await response.text();
    throw new Error(`${response.status} ${response.statusText}${body ? `: ${body}` : ''}`);
  }
  return response.json();
}

function App() {
  const [route, setRoute] = useState(parseRoute);
  const [auth, setAuth] = useState(() => getStoredAuth());
  const [health, setHealth] = useState('확인 전');
  const [healthBusy, setHealthBusy] = useState(false);
  const [message, setMessage] = useState('');
  const [authBusy, setAuthBusy] = useState(false);
  const isAuthenticated = Boolean(auth?.accessToken);
  const pageTitle = route.name === 'problem-detail'
    ? '문제 상세'
    : route.name === 'login'
      ? '로그인'
      : '문제 목록';

  function applyAuth(nextAuth) {
    setAuth(nextAuth);
    saveStoredAuth(nextAuth);
  }

  function clearAuth() {
    setAuth(null);
    clearStoredAuth();
  }

  function navigate(path) {
    window.history.pushState(null, '', path);
    setRoute(parseRoute());
  }

  function handleLoginSuccess(authResponse) {
    applyAuth({
      accessToken: authResponse.accessToken,
      refreshToken: authResponse.refreshToken,
      user: authResponse.user,
    });
    setMessage('로그인에 성공했습니다.');
    navigate('/problems');
  }

  async function logout() {
    setAuthBusy(true);

    try {
      if (auth?.refreshToken) {
        await request('/api/auth/logout', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ refreshToken: auth.refreshToken }),
        });
      }
      clearAuth();
      setMessage('로그아웃했습니다.');
      navigate('/login');
    } catch (error) {
      clearAuth();
      setMessage(`로그아웃 중 오류가 발생해 로그인 정보를 초기화합니다. (${error.message})`);
      navigate('/login');
    } finally {
      setAuthBusy(false);
    }
  }

  async function checkHealth() {
    setHealthBusy(true);
    try {
      const data = await request('/api/health');
      setHealth(data.status);
    } catch (error) {
      setHealth('실패');
      setMessage(error.message);
    } finally {
      setHealthBusy(false);
    }
  }

  async function checkCurrentUser() {
    if (!auth?.accessToken) {
      return;
    }

    try {
      setAuthBusy(true);
      const user = await request('/api/auth/me', {
        headers: { Authorization: `Bearer ${auth.accessToken}` },
      });
      applyAuth({ ...auth, user });
    } catch (error) {
      clearAuth();
      setMessage(`자동 로그인 세션이 만료되어 로그인이 해제되었습니다. (${error.message})`);
    } finally {
      setAuthBusy(false);
    }
  }

  useEffect(() => {
    checkHealth();
    checkCurrentUser();

    function handlePopState() {
      setRoute(parseRoute());
    }

    window.addEventListener('popstate', handlePopState);
    return () => window.removeEventListener('popstate', handlePopState);
  }, []);

  return (
    <main className="app-shell">
      <section className="topbar">
        <button className="brand-button" type="button" onClick={() => navigate('/problems')}>
          <p className="eyebrow">PS Platform</p>
          <h1>{pageTitle}</h1>
        </button>
        <div className="topbar-actions">
          {isAuthenticated ? (
            <>
              <span className="user-chip">{auth.user?.email ?? '로그인됨'}</span>
              <button
                className="ghost-button with-text"
                type="button"
                onClick={logout}
                disabled={authBusy}
              >
                로그아웃
              </button>
            </>
          ) : (
            <button
              className="ghost-button with-text"
              type="button"
              onClick={() => navigate('/login')}
            >
              로그인
            </button>
          )}
          <button className="icon-button" type="button" onClick={checkHealth} disabled={healthBusy} title="헬스체크">
            <Activity size={18} />
            <span>{health}</span>
          </button>
        </div>
      </section>

      {message && <div className="notice">{message}</div>}

      {route.name === 'login' ? (
        <LoginPage
          onLoginSuccess={handleLoginSuccess}
          setMessage={setMessage}
          isAuthenticated={isAuthenticated}
          navigate={navigate}
        />
      ) : route.name === 'problem-detail' ? (
        <ProblemDetailPage
          problemId={route.problemId}
          navigate={navigate}
          setMessage={setMessage}
        />
      ) : (
        <ProblemListPage
          navigate={navigate}
          setMessage={setMessage}
        />
      )}
    </main>
  );
}

function LoginPage({ onLoginSuccess, setMessage, isAuthenticated, navigate }) {
  const [busy, setBusy] = useState(false);
  const [form, setForm] = useState({ email: '', password: '' });

  async function login(event) {
    event.preventDefault();
    if (!form.email.trim() || !form.password.trim()) {
      setMessage('이메일/비밀번호를 입력해 주세요.');
      return;
    }

    setBusy(true);
    try {
      const auth = await request('/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          provider: 'local',
          credentials: {
            email: form.email,
            password: form.password,
          },
        }),
      });
      onLoginSuccess(auth);
    } catch (error) {
      setMessage(error.message);
    } finally {
      setBusy(false);
    }
  }

  function update(field, value) {
    setForm((current) => ({ ...current, [field]: value }));
  }

  return (
    <section className="auth-page">
      <form className="panel form-panel auth-form" onSubmit={login}>
        <div className="panel-header">
          <Code2 size={18} />
          <h2>로그인</h2>
        </div>

        {isAuthenticated ? (
          <p className="empty">이미 로그인되어 있습니다.</p>
        ) : (
          <>
            <label>
              이메일
              <input
                type="email"
                value={form.email}
                onChange={(event) => update('email', event.target.value)}
                autoComplete="username"
              />
            </label>
            <label>
              비밀번호
              <input
                type="password"
                value={form.password}
                onChange={(event) => update('password', event.target.value)}
                autoComplete="current-password"
              />
            </label>
            <button className="primary-button" type="submit" disabled={busy}>
              로그인
            </button>
          </>
        )}

        <button
          className="ghost-button with-text"
          type="button"
          onClick={() => navigate('/problems')}
        >
          문제 목록 보기
        </button>
      </form>
    </section>
  );
}

function ProblemListPage({ navigate, setMessage }) {
  const [problems, setProblems] = useState([]);
  const [form, setForm] = useState(initialForm);
  const [busy, setBusy] = useState(false);

  const hasProblems = useMemo(() => problems.length > 0, [problems]);

  async function loadProblems() {
    setBusy(true);
    try {
      const data = await request('/api/problems');
      setProblems(data);
      setMessage(`문제 ${data.length}개를 조회했습니다.`);
    } catch (error) {
      setMessage(error.message);
    } finally {
      setBusy(false);
    }
  }

  async function createProblem(event) {
    event.preventDefault();
    setBusy(true);
    try {
      const payload = {
        ...form,
        timeLimitMs: Number(form.timeLimitMs),
        memoryLimitMb: Number(form.memoryLimitMb),
        testCases: form.testCases.filter((testCase) => (
          testCase.input.trim() !== '' || testCase.expectedOutput.trim() !== ''
        )),
      };
      const created = await request('/api/problems', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });
      setMessage(`문제 #${created.id}를 등록했습니다.`);
      navigate(`/problems/${created.id}`);
    } catch (error) {
      setMessage(error.message);
    } finally {
      setBusy(false);
    }
  }

  function updateForm(field, value) {
    setForm((current) => ({ ...current, [field]: value }));
  }

  function updateCreateTestCase(index, field, value) {
    setForm((current) => ({
      ...current,
      testCases: current.testCases.map((testCase, currentIndex) => (
        currentIndex === index ? { ...testCase, [field]: value } : testCase
      )),
    }));
  }

  function addCreateTestCase() {
    setForm((current) => ({
      ...current,
      testCases: [...current.testCases, { input: '', expectedOutput: '' }],
    }));
  }

  function removeCreateTestCase(index) {
    setForm((current) => ({
      ...current,
      testCases: current.testCases.filter((_, currentIndex) => currentIndex !== index),
    }));
  }

  useEffect(() => {
    loadProblems();
  }, []);

  return (
    <section className="workspace list-page">
      <form className="panel form-panel" onSubmit={createProblem}>
        <div className="panel-header">
          <Plus size={18} />
          <h2>문제 등록</h2>
        </div>

        <label>
          제목
          <input value={form.title} onChange={(event) => updateForm('title', event.target.value)} />
        </label>

        <label>
          설명
          <textarea value={form.description} onChange={(event) => updateForm('description', event.target.value)} />
        </label>

        <label>
          입력 설명
          <textarea value={form.inputDescription} onChange={(event) => updateForm('inputDescription', event.target.value)} />
        </label>

        <label>
          출력 설명
          <textarea value={form.outputDescription} onChange={(event) => updateForm('outputDescription', event.target.value)} />
        </label>

        <div className="field-row">
          <label>
            시간 제한(ms)
            <input type="number" min="1" value={form.timeLimitMs} onChange={(event) => updateForm('timeLimitMs', event.target.value)} />
          </label>
          <label>
            메모리 제한(MB)
            <input type="number" min="1" value={form.memoryLimitMb} onChange={(event) => updateForm('memoryLimitMb', event.target.value)} />
          </label>
        </div>

        <div className="testcase-editor">
          <div className="panel-header compact-header split">
            <span className="heading-inline">
              <FileText size={17} />
              <h2>테스트케이스</h2>
            </span>
            <button className="ghost-button" type="button" onClick={addCreateTestCase} title="테스트케이스 추가">
              <Plus size={17} />
            </button>
          </div>

          {form.testCases.map((testCase, index) => (
            <div className="testcase-fields" key={`create-test-case-${index}`}>
              <div className="testcase-title">
                <strong>#{index + 1}</strong>
                <button
                  className="ghost-button icon-only"
                  type="button"
                  onClick={() => removeCreateTestCase(index)}
                  disabled={form.testCases.length === 1}
                  title="테스트케이스 제거"
                >
                  <X size={16} />
                </button>
              </div>
              <label>
                입력
                <textarea
                  className="case-textarea"
                  value={testCase.input}
                  onChange={(event) => updateCreateTestCase(index, 'input', event.target.value)}
                />
              </label>
              <label>
                기대 출력
                <textarea
                  className="case-textarea"
                  value={testCase.expectedOutput}
                  onChange={(event) => updateCreateTestCase(index, 'expectedOutput', event.target.value)}
                />
              </label>
            </div>
          ))}
        </div>

        <button className="primary-button" type="submit" disabled={busy}>
          <Plus size={18} />
          등록
        </button>
      </form>

      <section className="panel">
        <div className="panel-header split">
          <span className="heading-inline">
            <FileText size={18} />
            <h2>문제 목록</h2>
          </span>
          <button className="ghost-button" type="button" onClick={loadProblems} disabled={busy} title="새로고침">
            <RefreshCw size={17} />
          </button>
        </div>

        <div className="problem-list">
          {!hasProblems ? (
            <p className="empty">등록된 문제가 없습니다.</p>
          ) : (
            problems.map((problem) => (
              <button
                className="problem-row"
                type="button"
                key={problem.id}
                onClick={() => navigate(`/problems/${problem.id}`)}
              >
                <span>#{problem.id}</span>
                <strong>{problem.title}</strong>
                <small>{problem.timeLimitMs}ms / {problem.memoryLimitMb}MB</small>
              </button>
            ))
          )}
        </div>
      </section>
    </section>
  );
}

function ProblemDetailPage({ problemId, navigate, setMessage }) {
  const [problem, setProblem] = useState(null);
  const [testCases, setTestCases] = useState([]);
  const [testCaseForm, setTestCaseForm] = useState(initialTestCaseForm);
  const [submissions, setSubmissions] = useState([]);
  const [submissionForm, setSubmissionForm] = useState(initialSubmissionForm);
  const [busy, setBusy] = useState(false);
  const [submitBusy, setSubmitBusy] = useState(false);
  const [testCaseBusy, setTestCaseBusy] = useState(false);

  async function loadProblem() {
    setBusy(true);
    try {
      const [problemData, testCaseData, submissionData] = await Promise.all([
        request(`/api/problems/${problemId}`),
        request(`/api/problems/${problemId}/test-cases`),
        request(`/api/problems/${problemId}/submissions`),
      ]);
      setProblem(problemData);
      setTestCases(testCaseData);
      setSubmissions(submissionData);
      setMessage(`문제 #${problemId} 상세를 조회했습니다.`);
    } catch (error) {
      setProblem(null);
      setTestCases([]);
      setSubmissions([]);
      setMessage(error.message);
    } finally {
      setBusy(false);
    }
  }

  async function submitCode(event) {
    event.preventDefault();
    setSubmitBusy(true);
    try {
      const created = await request(`/api/problems/${problemId}/submissions`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(submissionForm),
      });
      setSubmissions((current) => [created, ...current]);
      setMessage(`제출 #${created.id}이 접수되었습니다. 채점 대기 상태입니다.`);
    } catch (error) {
      setMessage(error.message);
    } finally {
      setSubmitBusy(false);
    }
  }

  async function addTestCase(event) {
    event.preventDefault();
    setTestCaseBusy(true);
    try {
      const created = await request(`/api/problems/${problemId}/test-cases`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(testCaseForm),
      });
      setTestCases((current) => [...current, created]);
      setTestCaseForm(initialTestCaseForm);
      setMessage(`테스트케이스 #${created.id}를 추가했습니다.`);
    } catch (error) {
      setMessage(error.message);
    } finally {
      setTestCaseBusy(false);
    }
  }

  function updateSubmissionForm(field, value) {
    setSubmissionForm((current) => ({ ...current, [field]: value }));
  }

  function updateTestCaseForm(field, value) {
    setTestCaseForm((current) => ({ ...current, [field]: value }));
  }

  useEffect(() => {
    loadProblem();
  }, [problemId]);

  return (
    <section className="detail-page">
      <div className="page-actions">
        <button className="ghost-button with-text" type="button" onClick={() => navigate('/problems')}>
          <ArrowLeft size={17} />
          목록
        </button>
        <button className="ghost-button with-text" type="button" onClick={loadProblem} disabled={busy}>
          <RefreshCw size={17} />
          새로고침
        </button>
      </div>

      <div className="solve-layout">
        <section className="panel detail-panel">
          {problem ? (
            <article className="detail">
              <p className="eyebrow">#{problem.id}</p>
              <h2>{problem.title}</h2>
              <dl>
                <dt>설명</dt>
                <dd>{problem.description}</dd>
                <dt>입력</dt>
                <dd>{problem.inputDescription}</dd>
                <dt>출력</dt>
                <dd>{problem.outputDescription}</dd>
                <dt>제한</dt>
                <dd>{problem.timeLimitMs}ms / {problem.memoryLimitMb}MB</dd>
                <dt>테스트케이스</dt>
                <dd>{testCases.length}개</dd>
              </dl>
            </article>
          ) : (
            <p className="empty">문제를 불러오지 못했습니다.</p>
          )}

          <div className="testcase-list">
            <div className="panel-header compact-header">
              <FileText size={17} />
              <h2>테스트케이스</h2>
            </div>

            {testCases.length === 0 ? (
              <p className="empty">등록된 테스트케이스가 없습니다.</p>
            ) : (
              testCases.map((testCase) => (
                <div className="testcase-row" key={testCase.id}>
                  <strong>#{testCase.sortOrder + 1}</strong>
                  <label>
                    입력
                    <pre>{testCase.input}</pre>
                  </label>
                  <label>
                    기대 출력
                    <pre>{testCase.expectedOutput}</pre>
                  </label>
                </div>
              ))
            )}
          </div>

          <form className="testcase-add-form" onSubmit={addTestCase}>
            <div className="panel-header compact-header">
              <Plus size={17} />
              <h2>테스트케이스 추가</h2>
            </div>
            <label>
              입력
              <textarea
                className="case-textarea"
                value={testCaseForm.input}
                onChange={(event) => updateTestCaseForm('input', event.target.value)}
              />
            </label>
            <label>
              기대 출력
              <textarea
                className="case-textarea"
                value={testCaseForm.expectedOutput}
                onChange={(event) => updateTestCaseForm('expectedOutput', event.target.value)}
              />
            </label>
            <button className="primary-button" type="submit" disabled={testCaseBusy || !problem}>
              <Plus size={17} />
              추가
            </button>
          </form>
        </section>

        <section className="panel solve-panel">
          <div className="panel-header">
            <Code2 size={18} />
            <h2>코드 제출</h2>
          </div>

          <form className="submission-form" onSubmit={submitCode}>
            <label>
              언어
              <select
                value={submissionForm.language}
                onChange={(event) => updateSubmissionForm('language', event.target.value)}
              >
                <option value="java">Java</option>
                <option value="python">Python</option>
                <option value="cpp">C++</option>
              </select>
            </label>

            <label>
              소스 코드
              <textarea
                className="code-editor"
                value={submissionForm.sourceCode}
                onChange={(event) => updateSubmissionForm('sourceCode', event.target.value)}
                spellCheck="false"
              />
            </label>

            <button className="primary-button" type="submit" disabled={submitBusy || !problem}>
              <Send size={17} />
              제출
            </button>
          </form>

          <div className="submission-list">
            <div className="panel-header compact-header">
              <FileText size={17} />
              <h2>제출 기록</h2>
            </div>
          {submissions.length === 0 ? (
              <p className="empty">아직 제출이 없습니다.</p>
            ) : (
              submissions.map((submission) => (
                <div className="submission-row" key={submission.id}>
                  <span>#{submission.id}</span>
                  <strong>{submission.status}</strong>
                  <small>{submission.language}</small>
                  <small>{submission.verdict ?? '채점 대기'}</small>
                  {submission.judgeMessage ? (
                    <pre className="submission-message">{submission.judgeMessage}</pre>
                  ) : null}
                </div>
              ))
            )}
          </div>
        </section>
      </div>
    </section>
  );
}

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
