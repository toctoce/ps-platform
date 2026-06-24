import { StrictMode, useEffect, useMemo, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { Activity, FileText, Plus, RefreshCw, Search } from 'lucide-react';
import './styles.css';

const initialForm = {
  title: 'A+B',
  description: '두 수를 더하는 문제',
  inputDescription: '정수 A와 B가 주어진다.',
  outputDescription: 'A+B를 출력한다.',
  timeLimitMs: 1000,
  memoryLimitMb: 128,
};

function App() {
  const [health, setHealth] = useState('확인 전');
  const [healthBusy, setHealthBusy] = useState(false);
  const [problems, setProblems] = useState([]);
  const [selectedProblem, setSelectedProblem] = useState(null);
  const [selectedId, setSelectedId] = useState('');
  const [form, setForm] = useState(initialForm);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');

  const latestProblem = useMemo(() => problems[0], [problems]);

  async function request(path, options) {
    const response = await fetch(path, options);
    if (!response.ok) {
      const body = await response.text();
      throw new Error(`${response.status} ${response.statusText}${body ? `: ${body}` : ''}`);
    }
    return response.json();
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
      setSelectedProblem(created);
      setSelectedId(String(created.id));
      await loadProblems();
    } catch (error) {
      setMessage(error.message);
    } finally {
      setBusy(false);
    }
  }

  async function loadProblemDetail(id = selectedId) {
    if (!id) {
      setMessage('조회할 문제 ID를 입력하세요.');
      return;
    }
    setBusy(true);
    try {
      const data = await request(`/api/problems/${id}`);
      setSelectedProblem(data);
      setMessage(`문제 #${id} 상세를 조회했습니다.`);
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
    checkHealth();
    loadProblems();
  }, []);

  return (
    <main className="app-shell">
      <section className="topbar">
        <div>
          <p className="eyebrow">PS Platform</p>
          <h1>Backend API 테스트</h1>
        </div>
        <button className="icon-button" type="button" onClick={checkHealth} disabled={healthBusy} title="헬스체크">
          <Activity size={18} />
          <span>{health}</span>
        </button>
      </section>

      {message && <div className="notice">{message}</div>}

      <section className="workspace">
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
            {problems.length === 0 ? (
              <p className="empty">등록된 문제가 없습니다.</p>
            ) : (
              problems.map((problem) => (
                <button
                  className="problem-row"
                  type="button"
                  key={problem.id}
                  onClick={() => {
                    setSelectedId(String(problem.id));
                    loadProblemDetail(problem.id);
                  }}
                >
                  <span>#{problem.id}</span>
                  <strong>{problem.title}</strong>
                  <small>{problem.timeLimitMs}ms / {problem.memoryLimitMb}MB</small>
                </button>
              ))
            )}
          </div>
        </section>

        <section className="panel detail-panel">
          <div className="panel-header">
            <Search size={18} />
            <h2>상세 조회</h2>
          </div>

          <div className="lookup">
            <input
              placeholder={latestProblem ? String(latestProblem.id) : '문제 ID'}
              value={selectedId}
              onChange={(event) => setSelectedId(event.target.value)}
            />
            <button className="ghost-button with-text" type="button" onClick={() => loadProblemDetail()} disabled={busy}>
              <Search size={17} />
              조회
            </button>
          </div>

          {selectedProblem ? (
            <article className="detail">
              <p className="eyebrow">#{selectedProblem.id}</p>
              <h3>{selectedProblem.title}</h3>
              <dl>
                <dt>설명</dt>
                <dd>{selectedProblem.description}</dd>
                <dt>입력</dt>
                <dd>{selectedProblem.inputDescription}</dd>
                <dt>출력</dt>
                <dd>{selectedProblem.outputDescription}</dd>
                <dt>제한</dt>
                <dd>{selectedProblem.timeLimitMs}ms / {selectedProblem.memoryLimitMb}MB</dd>
              </dl>
            </article>
          ) : (
            <p className="empty">목록에서 문제를 선택하거나 ID를 입력하세요.</p>
          )}
        </section>
      </section>
    </main>
  );
}

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
