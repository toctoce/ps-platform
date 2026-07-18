import { useEffect, useState } from 'react';
import { RefreshCw } from 'lucide-react';
import { request } from '../api/client.js';
import { ProblemRow } from '../components/ProblemRow.jsx';
import { difficultyLabel } from '../problem/problemUtils.js';

export function RandomProblemPage({ navigate, auth }) {
  const [problems, setProblems] = useState([]);
  const [unavailableDifficulties, setUnavailableDifficulties] = useState([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  async function loadProblems() {
    setBusy(true);
    setError('');
    try {
      const data = await request('/api/problems/mock-test', {
        headers: auth?.accessToken ? { Authorization: `Bearer ${auth.accessToken}` } : {},
      });
      setProblems(Array.isArray(data?.problems) ? data.problems : []);
      setUnavailableDifficulties(
        Array.isArray(data?.unavailableDifficulties) ? data.unavailableDifficulties : [],
      );
    } catch (requestError) {
      setError(requestError.message);
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
        <h1>모의 코딩테스트</h1>
        <button className="ghost-button with-text" type="button" disabled>
          <RefreshCw size={16} />
          새로 뽑기
        </button>
      </header>

      <div className="problem-list airy-problem-list random-problem-list">
        {busy ? <p className="empty">불러오는 중...</p> : null}
        {!busy && error ? <p className="empty error-state">{error}</p> : null}
        {!busy && !error && problems.length === 0 ? (
          <p className="empty">선정할 수 있는 문제가 없습니다.</p>
        ) : null}
        {!busy && !error ? problems.map((problem) => (
          <ProblemRow
            key={problem.id}
            problem={problem}
            navigate={navigate}
            showCategories={false}
            showNumber
          />
        )) : null}
      </div>
      {!busy && !error && unavailableDifficulties.length > 0 ? (
        <p className="mock-test-shortage">
          {unavailableDifficulties.map(difficultyLabel).join(', ')} 난이도에는 아직 풀지 않은 문제가 없습니다.
        </p>
      ) : null}
    </section>
  );
}
