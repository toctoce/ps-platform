import { StrictMode, useEffect, useMemo, useRef, useState } from 'react';
import { createRoot } from 'react-dom/client';
import {
  Activity,
  ArrowLeft,
  Code2,
  FileText,
  Lock,
  FolderKanban,
  Plus,
  RefreshCw,
  Send,
  Shuffle,
  X,
} from 'lucide-react';
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
const editorStorageKeyPrefix = 'ps-platform-editor';
const IDE_TAB_SIZE = 2;
const IDE_OPEN_CLOSE = {
  '(': ')',
  '{': '}',
  '[': ']',
  '"': '"',
  "'": "'",
};

const categoryRules = [
  {
    name: '배열',
    keywords: ['배열', 'array', '정렬', 'sort'],
  },
  {
    name: '문자열',
    keywords: ['문자열', 'string', 'substring', '문자'],
  },
  {
    name: '구현',
    keywords: ['구현', 'implement', '시뮬레이션'],
  },
  {
    name: '구간',
    keywords: ['구간', '투포인터', 'prefix', '누적'],
  },
  {
    name: '그래프',
    keywords: ['그래프', 'bfs', 'dfs', '최단거리', '다익스트라'],
  },
  {
    name: '자료구조',
    keywords: ['스택', '큐', '스택', '스택', 'hash', '맵', '큐', '트리'],
  },
];

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
  const path = (window.location.pathname || '/').replace(/\/+$/, '') || '/';
  if (path === '/login') {
    return { name: 'login' };
  }
  if (path === '/signup') {
    return { name: 'signup' };
  }
  if (path === '/categories') {
    return { name: 'category-list' };
  }
  const categoryMatch = path.match(/^\/categories\/(.+)$/);
  if (categoryMatch) {
    return {
      name: 'category-detail',
      category: decodeURIComponent(categoryMatch[1]),
    };
  }
  if (path === '/random') {
    return { name: 'random' };
  }
  if (path === '/mypage' || path === '/my') {
    return { name: 'my-page' };
  }
  if (path === '/admin') {
    return { name: 'admin' };
  }
  const detailMatch = path.match(/^\/problems\/(\d+)$/);
  if (detailMatch) {
    return { name: 'problem-detail', problemId: detailMatch[1] };
  }
  if (path === '/problems' || path === '/home' || path === '/') {
    return { name: 'home' };
  }
  return { name: 'home' };
}

function getProblemCategory(problem) {
  if (problem.category) {
    return problem.category;
  }

  const source = `${problem.title ?? ''} ${problem.description ?? ''} ${problem.inputDescription ?? ''}`.toLowerCase();

  for (const rule of categoryRules) {
    if (rule.keywords.some((keyword) => source.includes(keyword.toLowerCase()))) {
      return rule.name;
    }
  }

  return '기타';
}

function buildCategoryGroups(problems) {
  const map = new Map();
  for (const problem of problems) {
    const category = getProblemCategory(problem);
    if (!map.has(category)) {
      map.set(category, []);
    }
    map.get(category).push(problem);
  }
  return map;
}

function pickRandomProblems(problems, count) {
  const copied = [...problems];
  for (let i = copied.length - 1; i > 0; i--) {
    const swapIndex = Math.floor(Math.random() * (i + 1));
    [copied[i], copied[swapIndex]] = [copied[swapIndex], copied[i]];
  }
  return copied.slice(0, count);
}

function safeJsonRequest(response) {
  const contentLength = Number(response.headers.get('content-length') || 0);
  if (contentLength === 0) {
    return null;
  }
  return response.text().then((text) => {
    if (!text) {
      return null;
    }
    try {
      return JSON.parse(text);
    } catch (_error) {
      return text;
    }
  });
}

async function request(path, options = {}) {
  const response = await fetch(path, options);
  if (!response.ok) {
    const body = await safeJsonRequest(response);
    throw new Error(`${response.status} ${response.statusText}${body ? `: ${body}` : ''}`);
  }
  return safeJsonRequest(response);
}

function getRouteTitle(route) {
  if (route.name === 'home') {
    return '홈';
  }
  if (route.name === 'login') {
    return '로그인';
  }
  if (route.name === 'signup') {
    return '회원가입';
  }
  if (route.name === 'category-list') {
    return '카테고리별 풀기';
  }
  if (route.name === 'category-detail') {
    return `${route.category} 카테고리`;
  }
  if (route.name === 'random') {
    return '랜덤 풀기';
  }
  if (route.name === 'my-page') {
    return '마이페이지';
  }
  if (route.name === 'problem-detail') {
    return '문제 해결';
  }
  if (route.name === 'admin') {
    return '관리자 페이지';
  }
  return '홈';
}

function isPendingStatus(status) {
  return status === 'PENDING' || status === 'RUNNING';
}

function isFinishedStatus(status) {
  return !isPendingStatus(status);
}

function submissionResultLabel(submission) {
  if (submission.status === 'JUDGED') {
    return submission.verdict ?? 'JUDGED';
  }
  if (submission.status === 'FAILED') {
    return submission.verdict ?? 'FAILED';
  }
  return submission.status ?? 'UNKNOWN';
}

function statusToLabel(status, verdict) {
  if (status === 'JUDGED') {
    return verdict ?? 'JUDGED';
  }
  return status ?? 'UNKNOWN';
}

function submissionBadgeClass(status) {
  if (status === 'JUDGED') {
    return 'verdict';
  }
  if (status === 'FAILED') {
    return 'failed';
  }
  if (status === 'RUNNING') {
    return 'running';
  }
  return 'pending';
}

function ProblemRow({ problem, navigate }) {
  const category = getProblemCategory(problem);
  return (
    <button className="problem-row" type="button" onClick={() => navigate(`/problems/${problem.id}`)}>
      <div className="problem-row-header">
        <span>#{problem.id}</span>
        <span className="category-chip">{category}</span>
      </div>
      <strong>{problem.title}</strong>
      <small>{problem.timeLimitMs}ms / {problem.memoryLimitMb}MB</small>
    </button>
  );
}

function App() {
  const [route, setRoute] = useState(parseRoute);
  const [auth, setAuth] = useState(() => getStoredAuth());
  const [health, setHealth] = useState('확인 전');
  const [healthBusy, setHealthBusy] = useState(false);
  const [message, setMessage] = useState('');
  const [authBusy, setAuthBusy] = useState(false);

  const isAuthenticated = Boolean(auth?.accessToken);
  const isAdmin = auth?.user?.role === 'ADMIN';
  const pageTitle = getRouteTitle(route);

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

  function isActiveNav(target) {
    if (target === 'home') {
      return route.name === 'home' || route.name === 'problem-detail';
    }
    if (target === 'random') {
      return route.name === 'random';
    }
    if (target === 'categories') {
      return route.name === 'category-list' || route.name === 'category-detail';
    }
    if (target === 'mypage') {
      return route.name === 'my-page';
    }
    return false;
  }

  function handleLoginSuccess(authResponse) {
    applyAuth({
      accessToken: authResponse.accessToken,
      refreshToken: authResponse.refreshToken,
      user: authResponse.user,
    });
    setMessage('로그인에 성공했습니다.');
    navigate('/');
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
      setMessage(`자동 로그인 세션이 만료되어 로그아웃됩니다. (${error.message})`);
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
        <button className="brand-button" type="button" onClick={() => navigate('/')}>
          <p className="eyebrow">PS Platform</p>
          <h1>{pageTitle}</h1>
        </button>
        <div className="topbar-nav">
          <button
            className={`ghost-button nav-link ${isActiveNav('home') ? 'is-active' : ''}`}
            type="button"
            onClick={() => navigate('/')}
          >
            홈
          </button>
          <button
            className={`ghost-button nav-link ${isActiveNav('categories') ? 'is-active' : ''}`}
            type="button"
            onClick={() => navigate('/categories')}
          >
            카테고리별 풀기
          </button>
          <button
            className={`ghost-button nav-link ${isActiveNav('random') ? 'is-active' : ''}`}
            type="button"
            onClick={() => navigate('/random')}
          >
            랜덤 풀기
          </button>
          <button
            className={`ghost-button nav-link ${isActiveNav('mypage') ? 'is-active' : ''}`}
            type="button"
            onClick={() => navigate('/mypage')}
          >
            마이페이지
          </button>
        </div>
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
            <>
              <button
                className="ghost-button with-text"
                type="button"
                onClick={() => navigate('/signup')}
              >
                회원가입
              </button>
              <button
                className="ghost-button with-text"
                type="button"
                onClick={() => navigate('/login')}
              >
                로그인
              </button>
            </>
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
      ) : route.name === 'signup' ? (
        <SignupPage
          setMessage={setMessage}
          navigate={navigate}
        />
      ) : route.name === 'category-list' ? (
        <CategoryPage
          navigate={navigate}
          setMessage={setMessage}
          category={null}
        />
      ) : route.name === 'category-detail' ? (
        <CategoryPage
          category={route.category}
          navigate={navigate}
          setMessage={setMessage}
        />
      ) : route.name === 'random' ? (
        <RandomProblemPage
          navigate={navigate}
          setMessage={setMessage}
        />
      ) : route.name === 'my-page' ? (
        <MyPage
          auth={auth}
          isAuthenticated={isAuthenticated}
          setMessage={setMessage}
          navigate={navigate}
        />
      ) : route.name === 'admin' ? (
        <AdminPage
          auth={auth}
          isAuthenticated={isAuthenticated}
          isAdmin={isAdmin}
          setMessage={setMessage}
          navigate={navigate}
        />
      ) : route.name === 'problem-detail' ? (
        <ProblemDetailPage
          problemId={route.problemId}
          navigate={navigate}
          setMessage={setMessage}
          isAuthenticated={isAuthenticated}
        />
      ) : (
        <HomePage
          navigate={navigate}
          setMessage={setMessage}
          isAdmin={isAdmin}
          isAuthenticated={isAuthenticated}
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
          onClick={() => navigate('/signup')}
        >
          회원가입
        </button>
        <button
          className="ghost-button with-text"
          type="button"
          onClick={() => navigate('/')}
        >
          홈으로 이동
        </button>
      </form>
    </section>
  );
}

function SignupPage({ setMessage, navigate }) {
  const [busy, setBusy] = useState(false);
  const [form, setForm] = useState({ email: '', username: '', password: '', confirmPassword: '' });

  async function signup(event) {
    event.preventDefault();
    if (!form.email.trim() || !form.username.trim() || !form.password.trim() || !form.confirmPassword.trim()) {
      setMessage('모든 항목을 입력해 주세요.');
      return;
    }
    if (form.password.length < 8) {
      setMessage('비밀번호는 8자 이상이어야 합니다.');
      return;
    }
    if (form.password !== form.confirmPassword) {
      setMessage('비밀번호 확인이 일치하지 않습니다.');
      return;
    }

    setBusy(true);
    try {
      await request('/api/users', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          email: form.email.trim(),
          username: form.username.trim(),
          password: form.password,
        }),
      });
      setMessage('회원가입이 완료되었습니다. 로그인해 주세요.');
      navigate('/login');
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
      <form className="panel form-panel auth-form" onSubmit={signup}>
        <div className="panel-header">
          <Code2 size={18} />
          <h2>회원가입</h2>
        </div>

        <label>
          이메일
          <input
            type="email"
            value={form.email}
            onChange={(event) => update('email', event.target.value)}
            autoComplete="email"
          />
        </label>
        <label>
          닉네임
          <input
            type="text"
            value={form.username}
            onChange={(event) => update('username', event.target.value)}
            autoComplete="username"
          />
        </label>
        <label>
          비밀번호
          <input
            type="password"
            value={form.password}
            onChange={(event) => update('password', event.target.value)}
            autoComplete="new-password"
          />
        </label>
        <label>
          비밀번호 확인
          <input
            type="password"
            value={form.confirmPassword}
            onChange={(event) => update('confirmPassword', event.target.value)}
            autoComplete="new-password"
          />
        </label>

        <button className="primary-button" type="submit" disabled={busy}>
          회원가입
        </button>
        <button
          className="ghost-button with-text"
          type="button"
          onClick={() => navigate('/login')}
        >
          로그인으로 이동
        </button>
      </form>
    </section>
  );
}

function HomePage({ navigate, setMessage, isAdmin, isAuthenticated }) {
  const [problems, setProblems] = useState([]);
  const [busy, setBusy] = useState(false);
  const [randomSeed, setRandomSeed] = useState(0);

  const categoryGroups = useMemo(() => buildCategoryGroups(problems), [problems]);
  const categories = useMemo(
    () => Array.from(categoryGroups.entries())
      .map(([name, list]) => ({ name, count: list.length }))
      .sort((a, b) => a.name.localeCompare(b.name)),
    [categoryGroups]
  );
  const recentProblems = useMemo(
    () => [...problems].sort((a, b) => b.id - a.id).slice(0, 10),
    [problems]
  );
  const randomProblems = useMemo(
    () => pickRandomProblems(problems, 5),
    [problems, randomSeed]
  );

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

  useEffect(() => {
    loadProblems();
  }, []);

  return (
    <section className="home-page">
      <section className="home-grid">
        <section className="panel">
          <div className="panel-header split">
            <span className="heading-inline">
              <FolderKanban size={18} />
              <h2>카테고리별로 풀기</h2>
            </span>
            <button
              className="ghost-button with-text"
              type="button"
              onClick={() => navigate('/categories')}
            >
              전체 보기
            </button>
          </div>
          <div className="category-summary">
            {categories.length === 0 ? (
              <p className="empty">분류 가능한 문제가 없습니다.</p>
            ) : (
              categories.slice(0, 8).map((category) => (
                <button
                  className="category-chip category-chip--button"
                  key={category.name}
                  type="button"
                  onClick={() => navigate(`/categories/${encodeURIComponent(category.name)}`)}
                >
                  <strong>{category.name}</strong>
                  <span>{category.count}문제</span>
                </button>
              ))
            )}
          </div>
        </section>

        <section className="panel">
          <div className="panel-header split">
            <span className="heading-inline">
              <Shuffle size={18} />
              <h2>랜덤 5문제</h2>
            </span>
            <button
              className="ghost-button with-text"
              type="button"
              onClick={() => setRandomSeed((current) => current + 1)}
              disabled={busy}
            >
              다시 뽑기
            </button>
          </div>
          <div className="problem-list">
            {randomProblems.length === 0 ? (
              <p className="empty">문제가 없습니다.</p>
            ) : (
              randomProblems.map((problem) => (
                <ProblemRow
                  key={`random-${problem.id}`}
                  problem={problem}
                  navigate={navigate}
                />
              ))
            )}
          </div>
        </section>
      </section>

      <section className="panel">
        <div className="panel-header split">
          <span className="heading-inline">
            <FileText size={18} />
            <h2>최근 문제</h2>
          </span>
          <button className="ghost-button" type="button" onClick={loadProblems} disabled={busy} title="새로고침">
            <RefreshCw size={17} />
          </button>
        </div>
        <div className="problem-list">
          {recentProblems.length === 0 ? (
            <p className="empty">등록된 문제가 없습니다.</p>
          ) : (
            recentProblems.map((problem) => (
              <ProblemRow
                key={problem.id}
                problem={problem}
                navigate={navigate}
              />
            ))
          )}
        </div>
      </section>
      <section className="panel">
        <div className="panel-header">
          <Lock size={18} />
          <h2>{isAdmin ? '관리자 기능' : '관리자 페이지'}</h2>
        </div>
        <p className="helper-text">
          {isAdmin
            ? '문제 등록과 테스트케이스 등록은 관리자 페이지에서 수행합니다.'
            : '문제 등록·테스트케이스 등록은 관리자 권한 전용입니다.'}
        </p>
        <button
          className="primary-button"
          type="button"
          onClick={() => navigate('/admin')}
          disabled={!isAuthenticated}
        >
          관리자 페이지로 이동
        </button>
      </section>
    </section>
  );
}

function AdminPage({ auth, isAuthenticated, isAdmin, setMessage, navigate }) {
  const [problems, setProblems] = useState([]);
  const [loadingProblems, setLoadingProblems] = useState(false);
  const [problemForm, setProblemForm] = useState(initialForm);
  const [createBusy, setCreateBusy] = useState(false);
  const [problemId, setProblemId] = useState('');
  const [testCaseForms, setTestCaseForms] = useState([{ input: '', expectedOutput: '' }]);
  const [testCaseBusy, setTestCaseBusy] = useState(false);

  function authHeaders() {
    return auth?.accessToken
      ? { Authorization: `Bearer ${auth.accessToken}` }
      : {};
  }

  async function loadProblems() {
    setLoadingProblems(true);
    try {
      const data = await request('/api/problems');
      setProblems(data);
      setMessage(`문제 ${data.length}개를 조회했습니다.`);
      if (problemId && !data.some((problem) => String(problem.id) === String(problemId))) {
        setProblemId('');
      }
    } catch (error) {
      setMessage(error.message);
    } finally {
      setLoadingProblems(false);
    }
  }

  function updateProblemForm(field, value) {
    setProblemForm((current) => ({ ...current, [field]: value }));
  }

  function updateTestCaseForm(index, field, value) {
    setTestCaseForms((current) => (
      current.map((item, currentIndex) => (
        currentIndex === index ? { ...item, [field]: value } : item
      ))
    ));
  }

  function addTestCaseForm() {
    setTestCaseForms((current) => [...current, { input: '', expectedOutput: '' }]);
  }

  function removeTestCaseForm(index) {
    setTestCaseForms((current) => {
      if (current.length <= 1) {
        return current;
      }
      return current.filter((_, currentIndex) => currentIndex !== index);
    });
  }

  async function createProblem(event) {
    event.preventDefault();
    setCreateBusy(true);
    try {
      const payload = {
        ...problemForm,
        timeLimitMs: Number(problemForm.timeLimitMs),
        memoryLimitMb: Number(problemForm.memoryLimitMb),
        testCases: [],
      };
      const created = await request('/api/problems', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...authHeaders() },
        body: JSON.stringify(payload),
      });
      setMessage(`문제 #${created.id}를 등록했습니다.`);
      setProblemId(String(created.id));
      setProblemForm(initialForm);
      await loadProblems();
      navigate(`/problems/${created.id}`);
    } catch (error) {
      setMessage(error.message);
    } finally {
      setCreateBusy(false);
    }
  }

  async function addTestCases(event) {
    event.preventDefault();
    const targetProblemId = Number(problemId);
    if (!Number.isInteger(targetProblemId) || targetProblemId <= 0) {
      setMessage('문제를 선택해 주세요.');
      return;
    }

    const cases = testCaseForms
      .map((item) => ({ input: item.input, expectedOutput: item.expectedOutput }))
      .filter((item) => item.input.trim() !== '' || item.expectedOutput.trim() !== '');

    if (cases.length === 0) {
      setMessage('저장할 테스트케이스가 없습니다.');
      return;
    }

    setTestCaseBusy(true);
    try {
      await Promise.all(
        cases.map((item) => request(`/api/problems/${targetProblemId}/test-cases`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', ...authHeaders() },
          body: JSON.stringify(item),
        }))
      );
      setMessage(`문제 #${targetProblemId} 테스트케이스 ${cases.length}개를 등록했습니다.`);
      setTestCaseForms([{ input: '', expectedOutput: '' }]);
      await loadProblems();
    } catch (error) {
      setMessage(error.message);
    } finally {
      setTestCaseBusy(false);
    }
  }

  useEffect(() => {
    if (isAuthenticated && isAdmin) {
      loadProblems();
    }
  }, [isAuthenticated, isAdmin]);

  if (!isAuthenticated || !isAdmin) {
    return (
      <section className="home-page">
        <div className="page-actions">
          <button className="ghost-button with-text" type="button" onClick={() => navigate('/')}>
            <ArrowLeft size={17} />
            홈으로
          </button>
        </div>
        <section className="panel">
          <div className="panel-header">
            <Lock size={18} />
            <h2>관리자 전용</h2>
          </div>
          <p className="empty">관리자 권한으로 로그인해야 접근할 수 있습니다.</p>
        </section>
      </section>
    );
  }

  return (
    <section className="home-page">
      <div className="page-actions">
        <button className="ghost-button with-text" type="button" onClick={() => navigate('/')}>
          <ArrowLeft size={17} />
          홈으로
        </button>
        <button className="ghost-button with-text" type="button" onClick={loadProblems} disabled={loadingProblems}>
          <RefreshCw size={17} />
          문제 목록 새로고침
        </button>
      </div>

      <section className="panel">
        <div className="panel-header">
          <Plus size={18} />
          <h2>문제 등록</h2>
        </div>
        <form className="form-panel" onSubmit={createProblem}>
          <div className="problem-form-grid">
            <label>
              제목
              <input
                value={problemForm.title}
                onChange={(event) => updateProblemForm('title', event.target.value)}
              />
            </label>
            <label>
              시간 제한(ms)
              <input
                type="number"
                min="1"
                value={problemForm.timeLimitMs}
                onChange={(event) => updateProblemForm('timeLimitMs', event.target.value)}
              />
            </label>
            <label>
              메모리 제한(MB)
              <input
                type="number"
                min="1"
                value={problemForm.memoryLimitMb}
                onChange={(event) => updateProblemForm('memoryLimitMb', event.target.value)}
              />
            </label>
          </div>
          <label>
            설명
            <textarea
              value={problemForm.description}
              onChange={(event) => updateProblemForm('description', event.target.value)}
            />
          </label>
          <label>
            입력 설명
            <textarea
              value={problemForm.inputDescription}
              onChange={(event) => updateProblemForm('inputDescription', event.target.value)}
            />
          </label>
          <label>
            출력 설명
            <textarea
              value={problemForm.outputDescription}
              onChange={(event) => updateProblemForm('outputDescription', event.target.value)}
            />
          </label>
          <button className="primary-button" type="submit" disabled={createBusy}>
            <Plus size={17} />
            문제 등록
          </button>
        </form>
      </section>

      <section className="panel">
        <div className="panel-header">
          <Code2 size={18} />
          <h2>테스트케이스 등록</h2>
        </div>
        <p className="helper-text">이미 등록된 문제에 대해 테스트케이스를 추가합니다.</p>
        <label>
          문제 선택
          <select value={problemId} onChange={(event) => setProblemId(event.target.value)}>
            <option value="">문제 선택</option>
            {problems.map((problem) => (
              <option key={problem.id} value={String(problem.id)}>
                #{problem.id} {problem.title}
              </option>
            ))}
          </select>
        </label>

        <form className="form-panel" onSubmit={addTestCases}>
          <div className="testcase-editor">
            <div className="panel-header compact-header split">
              <span className="heading-inline">
                <FileText size={17} />
                <h2>테스트케이스 목록</h2>
              </span>
              <button className="ghost-button" type="button" onClick={addTestCaseForm} title="테스트케이스 추가">
                <Plus size={16} />
              </button>
            </div>
            {testCaseForms.map((testCaseForm, index) => (
              <div className="testcase-fields" key={`test-case-${index}`}>
                <div className="testcase-title">
                  <strong>#{index + 1}</strong>
                  <button
                    className="ghost-button icon-only"
                    type="button"
                    onClick={() => removeTestCaseForm(index)}
                    disabled={testCaseForms.length === 1}
                    title="테스트케이스 제거"
                  >
                    <X size={16} />
                  </button>
                </div>
                <label>
                  입력
                  <textarea
                    className="case-textarea"
                    value={testCaseForm.input}
                    onChange={(event) => updateTestCaseForm(index, 'input', event.target.value)}
                  />
                </label>
                <label>
                  기대 출력
                  <textarea
                    className="case-textarea"
                    value={testCaseForm.expectedOutput}
                    onChange={(event) => updateTestCaseForm(index, 'expectedOutput', event.target.value)}
                  />
                </label>
              </div>
            ))}
          </div>
          <button className="primary-button" type="submit" disabled={testCaseBusy}>
            <Plus size={17} />
            테스트케이스 등록
          </button>
        </form>
      </section>
    </section>
  );
}

function CategoryPage({ category, navigate, setMessage }) {
  const [problems, setProblems] = useState([]);
  const [busy, setBusy] = useState(false);

  const groups = useMemo(() => buildCategoryGroups(problems), [problems]);
  const categoryRows = useMemo(() => {
    if (!category) {
      return Array.from(groups.entries())
        .map(([categoryName, list]) => ({ categoryName, count: list.length }))
        .sort((a, b) => a.categoryName.localeCompare(b.categoryName));
    }

    return [{ categoryName: category, count: (groups.get(category) ?? []).length }];
  }, [category, groups]);

  const selectedProblems = useMemo(() => {
    if (!category) {
      return [];
    }
    return [...(groups.get(category) ?? [])];
  }, [category, groups]);

  async function loadProblems() {
    setBusy(true);
    try {
      const data = await request('/api/problems');
      setProblems(data);
      if (!category) {
        setMessage(`카테고리 목록을 불러왔습니다.`);
      } else {
        const target = data.filter((problem) => getProblemCategory(problem) === category);
        setMessage(`${category} 카테고리 문제 ${target.length}개`);
      }
    } catch (error) {
      setMessage(error.message);
    } finally {
      setBusy(false);
    }
  }

  useEffect(() => {
    loadProblems();
  }, [category]);

  return (
    <section className="home-page">
      <section className="panel">
        <div className="panel-header split">
          <span className="heading-inline">
            <FileText size={18} />
            <h2>{category ? `${category} 카테고리` : '카테고리 목록'}</h2>
          </span>
          <button className="ghost-button with-text" type="button" onClick={() => navigate('/categories')}>
            목록
          </button>
        </div>

        {!category ? (
          <div className="category-list">
            {categoryRows.length === 0 ? (
              <p className="empty">카테고리 데이터가 없습니다.</p>
            ) : (
              categoryRows.map((item) => (
                <button
                  className="category-panel"
                  type="button"
                  key={item.categoryName}
                  onClick={() => navigate(`/categories/${encodeURIComponent(item.categoryName)}`)}
                >
                  <h3>{item.categoryName}</h3>
                  <small>{item.count}문제</small>
                </button>
              ))
            )}
          </div>
        ) : (
          <div className="problem-list">
            {busy ? <p className="empty">불러오는 중...</p> : null}
            {!busy && selectedProblems.length === 0 ? (
              <p className="empty">해당 카테고리에 문제가 없습니다.</p>
            ) : null}
            {!busy ? selectedProblems.map((problem) => (
              <ProblemRow
                key={problem.id}
                problem={problem}
                navigate={navigate}
              />
            )) : null}
          </div>
        )}
      </section>

      <section className="panel">
        <div className="panel-header">
          <Code2 size={18} />
          <h2>{category ? `${category} 바로 풀기` : '빠른 이동'}</h2>
        </div>
        <div className="quick-links">
          <button className="ghost-button" type="button" onClick={() => navigate('/random')}>
            랜덤 풀기
          </button>
          <button className="ghost-button" type="button" onClick={() => navigate('/')}>
            홈으로 돌아가기
          </button>
        </div>
      </section>
    </section>
  );
}

function RandomProblemPage({ navigate, setMessage }) {
  const [problems, setProblems] = useState([]);
  const [busy, setBusy] = useState(false);
  const [seed, setSeed] = useState(0);

  const randomProblems = useMemo(() => pickRandomProblems(problems, 5), [problems, seed]);

  async function loadProblems() {
    setBusy(true);
    try {
      const data = await request('/api/problems');
      setProblems(data);
      setMessage(`문제 ${data.length}개 중 5개를 무작위로 추출했습니다.`);
    } catch (error) {
      setMessage(error.message);
    } finally {
      setBusy(false);
    }
  }

  useEffect(() => {
    loadProblems();
  }, []);

  return (
    <section className="home-page">
      <section className="panel">
        <div className="panel-header split">
          <span className="heading-inline">
            <RefreshCw size={18} />
            <h2>랜덤 5문제</h2>
          </span>
          <button className="ghost-button with-text" type="button" onClick={() => setSeed((current) => current + 1)}>
            새로 뽑기
          </button>
        </div>

        <div className="problem-list">
          {busy ? <p className="empty">불러오는 중...</p> : null}
          {!busy && randomProblems.length === 0 ? (
            <p className="empty">문제가 없습니다.</p>
          ) : null}
          {!busy ? randomProblems.map((problem) => (
            <ProblemRow key={problem.id} problem={problem} navigate={navigate} />
          )) : null}
        </div>
      </section>
    </section>
  );
}

function MyPage({ auth, isAuthenticated, setMessage, navigate }) {
  if (!isAuthenticated) {
    return (
      <section className="home-page">
        <section className="panel">
          <div className="panel-header">
            <Code2 size={18} />
            <h2>마이페이지</h2>
          </div>
          <p className="empty">로그인 후 이용할 수 있습니다.</p>
          <button className="ghost-button with-text" type="button" onClick={() => navigate('/login')}>
            로그인
          </button>
        </section>
      </section>
    );
  }

  return (
    <section className="home-page">
      <section className="panel">
        <div className="panel-header">
          <Code2 size={18} />
          <h2>계정 정보</h2>
        </div>
        <dl className="profile-list">
          <dt>이메일</dt>
          <dd>{auth.user?.email}</dd>
          <dt>닉네임</dt>
          <dd>{auth.user?.username}</dd>
          <dt>권한</dt>
          <dd>{auth.user?.role ?? 'USER'}</dd>
          <dt>가입일</dt>
          <dd>{auth.user?.createdAt ?? '확인 불가'}</dd>
        </dl>
        <button
          className="ghost-button with-text"
          type="button"
          onClick={() => setMessage('현재 API에 유저별 제출 기록 API가 없어 추가 개발 예정입니다.')}
        >
          제출 기록 확인
        </button>
      </section>
    </section>
  );
}

function ProblemDetailPage({ problemId, navigate, setMessage, isAuthenticated }) {
  const [problem, setProblem] = useState(null);
  const [submissions, setSubmissions] = useState([]);
  const [busy, setBusy] = useState(false);
  const [submissionBusy, setSubmissionBusy] = useState(false);
  const [submissionForm, setSubmissionForm] = useState(initialSubmissionForm);
  const [testCaseCount, setTestCaseCount] = useState(0);
  const [editorHint, setEditorHint] = useState('Ctrl+Enter: 제출 / Tab: 들여쓰기 / 괄호 자동 닫힘');
  const pollingRef = useRef({});
  const lastSubmissionStatus = useRef({});
  const editorRef = useRef(null);
  const formRef = useRef(null);

  const problemKey = `${editorStorageKeyPrefix}:${problemId}`;

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

  function applyCode(nextCode, cursorPosition) {
    setSubmissionForm((current) => ({ ...current, sourceCode: nextCode }));
    requestAnimationFrame(() => {
      if (!editorRef.current) {
        return;
      }
      editorRef.current.focus();
      editorRef.current.selectionStart = cursorPosition;
      editorRef.current.selectionEnd = cursorPosition;
    });
  }

  function handleEditorKeyDown(event) {
    const currentCode = submissionForm.sourceCode;
    const key = event.key;
    const start = editorRef.current?.selectionStart ?? 0;
    const end = editorRef.current?.selectionEnd ?? 0;

    if ((event.ctrlKey || event.metaKey) && key === 's') {
      event.preventDefault();
      if (problemId) {
        const draftKey = `${problemKey}:${submissionForm.language}`;
        localStorage.setItem(draftKey, submissionForm.sourceCode);
        setMessage('임시 저장 완료');
      }
      return;
    }

    if ((event.ctrlKey || event.metaKey) && key === 'Enter') {
      event.preventDefault();
      if (formRef.current && isAuthenticated) {
        formRef.current.requestSubmit();
        setEditorHint('제출 실행');
      } else {
        setMessage('로그인이 필요합니다.');
      }
      return;
    }

    if (key === 'Tab') {
      event.preventDefault();
      if (event.shiftKey) {
        const lineStart = currentCode.lastIndexOf('\n', start - 1) + 1;
        const lineHeadEnd = (() => {
          let idx = lineStart;
          while (idx < currentCode.length && currentCode[idx] === ' ') {
            idx += 1;
          }
          return idx;
        })();
        const removeCount = Math.min(IDE_TAB_SIZE, lineHeadEnd - lineStart);
        if (removeCount <= 0) {
          applyCode(currentCode.slice(0, start) + currentCode.slice(start), start);
          return;
        }
        const nextCode = currentCode.slice(0, lineStart) + currentCode.slice(lineStart + removeCount);
        const nextCursor = Math.max(lineStart, start - removeCount);
        applyCode(nextCode, nextCursor);
        setEditorHint('들여쓰기 제거');
        return;
      }

      const nextCode = currentCode.slice(0, start) + '  ' + currentCode.slice(end);
      const nextCursor = start + 2;
      applyCode(nextCode, nextCursor);
      setEditorHint('탭 2칸 삽입');
      return;
    }

    if (Object.keys(IDE_OPEN_CLOSE).includes(key)) {
      if (start !== end) {
        event.preventDefault();
        const selected = currentCode.slice(start, end);
        const close = IDE_OPEN_CLOSE[key];
        const nextCode = `${currentCode.slice(0, start)}${key}${selected}${close}${currentCode.slice(end)}`;
        const nextCursor = start + 1 + selected.length;
        applyCode(nextCode, nextCursor);
        setEditorHint('자동 괄호 닫기');
        return;
      }
      event.preventDefault();
      const close = IDE_OPEN_CLOSE[key];
      const nextCode = `${currentCode.slice(0, start)}${key}${close}${currentCode.slice(end)}`;
      const nextCursor = start + 1;
      applyCode(nextCode, nextCursor);
      setEditorHint('자동 괄호 닫기');
      return;
    }

    if (key === 'Enter') {
      event.preventDefault();
      const lineStart = currentCode.lastIndexOf('\n', start - 1) + 1;
      const lineText = currentCode.slice(lineStart, start);
      const leadingWhitespaceMatch = lineText.match(/^[ \t]*/);
      const leadingWhitespace = leadingWhitespaceMatch ? leadingWhitespaceMatch[0] : '';
      const needsIndent = lineText.trim().endsWith('{');
      const insertedIndent = needsIndent ? `${leadingWhitespace}${' '.repeat(IDE_TAB_SIZE)}` : leadingWhitespace;
      const nextCode = `${currentCode.slice(0, start)}\n${insertedIndent}${currentCode.slice(end)}`;
      const nextCursor = start + 1 + insertedIndent.length;
      applyCode(nextCode, nextCursor);
      setEditorHint('자동 들여쓰기');
    }
  }

  function updateSubmissionForm(field, value) {
    setSubmissionForm((current) => ({ ...current, [field]: value }));
  }

  async function refreshSubmission(submissionId) {
    try {
      const submission = await request(`/api/submissions/${submissionId}`);
      const previousStatus = lastSubmissionStatus.current[submission.id];
      lastSubmissionStatus.current[submission.id] = submission.status;

      setSubmissions((current) => {
        const index = current.findIndex((item) => item.id === submission.id);
        if (index === -1) {
          return [submission, ...current];
        }
        const next = [...current];
        next[index] = submission;
        return next;
      });

      if (previousStatus !== undefined && isFinishedStatus(previousStatus) && isFinishedStatus(submission.status)) {
        return;
      }

      if (isFinishedStatus(submission.status)) {
        setMessage(`채점 결과: 제출 #${submission.id} - ${submissionResultLabel(submission)}`);
        setEditorHint(`${submission.id}번 채점 완료`);
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
      setTestCaseCount(Array.isArray(testCaseData) ? testCaseData.length : 0);
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
      setTestCaseCount(0);
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
      lastSubmissionStatus.current[created.id] = created.status;
      setSubmissions((current) => [created, ...current]);
      setMessage(`제출 #${created.id}이 접수되었습니다.`);
      startPolling(created.id);
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
      <div className="page-actions">
        <button className="ghost-button with-text" type="button" onClick={() => navigate('/')}>
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
                <dt>카테고리</dt>
                <dd>{getProblemCategory(problem)}</dd>
                <dt>테스트케이스</dt>
                <dd>{testCaseCount}개</dd>
              </dl>
            </article>
          ) : (
            <p className="empty">문제를 불러오지 못했습니다.</p>
          )}
        </section>

        <section className="panel solve-panel">
          <div className="panel-header">
            <Code2 size={18} />
            <h2>온라인 IDE</h2>
          </div>
          <p className="helper-text">{editorHint}</p>
          <form className="submission-form" onSubmit={submitCode} ref={formRef}>
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
                ref={editorRef}
                value={submissionForm.sourceCode}
                onChange={(event) => updateSubmissionForm('sourceCode', event.target.value)}
                onKeyDown={handleEditorKeyDown}
                spellCheck="false"
              />
            </label>

            <button className="primary-button" type="submit" disabled={submissionBusy || !problem}>
              <Send size={17} />
              제출
            </button>
          </form>

          <div className="submission-list">
            <div className="panel-header compact-header">
              <FileText size={17} />
              <h2>채점 상태</h2>
            </div>
            {submissions.length === 0 ? (
              <p className="empty">아직 제출이 없습니다.</p>
            ) : (
              submissions.map((submission) => (
                <div className="submission-row" key={submission.id}>
                  <span>#{submission.id}</span>
                  <strong className={`submission-badge ${submissionBadgeClass(submission.status)}`}>
                    {statusToLabel(submission.status, submission.verdict)}
                  </strong>
                  <small>{submission.language}</small>
                  <small>{submission.verdict ? `${submission.verdict}` : '채점 중'}</small>
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
