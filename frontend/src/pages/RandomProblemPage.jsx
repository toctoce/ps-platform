import { useEffect, useMemo, useState } from 'react';
import { RefreshCw } from 'lucide-react';
import { request } from '../api/client.js';
import { ProblemRow } from '../components/ProblemRow.jsx';

export function RandomProblemPage({ navigate, setMessage }) {
  const [problems, setProblems] = useState([]);
  const [busy, setBusy] = useState(false);
  const randomProblems = useMemo(() => problems, [problems]);

  async function loadProblems() {
    setBusy(true);
    try {
      const data = await request('/api/problems/random?count=5');
      setProblems(data);
      setMessage(`랜덤으로 ${data.length}문제를 조회했습니다.`);
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
          <button className="ghost-button with-text" type="button" onClick={loadProblems}>
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
