import { StrictMode, useEffect, useMemo, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { Activity, ArrowLeft, FileText, Plus, RefreshCw } from 'lucide-react';
import './styles.css';

const initialForm = {
  title: 'A+B',
  description: '두 수를 더하는 문제',
  inputDescription: '정수 A와 B가 주어진다.',
  outputDescription: 'A+B를 출력한다.',
  timeLimitMs: 1000,
  memoryLimitMb: 128,
};

function parseRoute() {
  const path = window.location.pathname;
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
  const [health, setHealth] = useState('확인 전');
  const [healthBusy, setHealthBusy] = useState(false);
  const [message, setMessage] = useState('');

  function navigate(path) {
    window.history.pushState(null, '', path);
    setRoute(parseRoute());
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

  useEffect(() => {
    checkHealth();

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
          <h1>{route.name === 'problem-detail' ? '문제 상세' : '문제 목록'}</h1>
        </button>
        <button className="icon-button" type="button" onClick={checkHealth} disabled={healthBusy} title="헬스체크">
          <Activity size={18} />
          <span>{health}</span>
        </button>
      </section>

      {message && <div className="notice">{message}</div>}

      {route.name === 'problem-detail' ? (
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
  const [busy, setBusy] = useState(false);

  async function loadProblem() {
    setBusy(true);
    try {
      const data = await request(`/api/problems/${problemId}`);
      setProblem(data);
      setMessage(`문제 #${problemId} 상세를 조회했습니다.`);
    } catch (error) {
      setProblem(null);
      setMessage(error.message);
    } finally {
      setBusy(false);
    }
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
            </dl>
          </article>
        ) : (
          <p className="empty">문제를 불러오지 못했습니다.</p>
        )}
      </section>
    </section>
  );
}

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
