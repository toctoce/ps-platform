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
    <section className="open-page random-page">
      <header className="open-page-header">
        <h1>랜덤 5문제</h1>
        <button className="ghost-button with-text" type="button" onClick={loadProblems}>
          <RefreshCw size={16} />
          새로 뽑기
        </button>
      </header>

      <div className="problem-list airy-problem-list random-problem-list">
        {busy ? <p className="empty">불러오는 중...</p> : null}
        {!busy && randomProblems.length === 0 ? (
          <p className="empty">문제가 없습니다.</p>
        ) : null}
        {!busy ? randomProblems.map((problem) => (
          <ProblemRow key={problem.id} problem={problem} navigate={navigate} />
        )) : null}
      </div>
    </section>
  );
}
