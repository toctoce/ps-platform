import { useEffect, useMemo, useState } from 'react';
import { Search } from 'lucide-react';
import { request } from '../api/client.js';
import { ProblemTable } from '../components/ProblemTable.jsx';
import {
  DIFFICULTY_OPTIONS,
  problemCategoryNames,
} from '../problem/problemUtils.js';

export function ProblemListPage({ navigate, setMessage, auth, isAuthenticated }) {
  const [problems, setProblems] = useState([]);
  const [solvedProblemIds, setSolvedProblemIds] = useState([]);
  const [busy, setBusy] = useState(false);
  const [problemSearch, setProblemSearch] = useState('');
  const [difficultyFilter, setDifficultyFilter] = useState('ALL');
  const [showCategories, setShowCategories] = useState(false);

  const visibleProblems = useMemo(() => {
    const keyword = problemSearch.trim().toLowerCase();
    return [...problems]
      .sort((a, b) => a.id - b.id)
      .filter((problem) => {
        if (difficultyFilter !== 'ALL' && problem.difficulty !== difficultyFilter) {
          return false;
        }
        if (!keyword) {
          return true;
        }
        const categories = problemCategoryNames(problem).join(' ').toLowerCase();
        return problem.title.toLowerCase().includes(keyword) || categories.includes(keyword);
      });
  }, [difficultyFilter, problemSearch, problems]);

  async function loadProblems() {
    setBusy(true);
    try {
      const [problemData, solvedData] = await Promise.all([
        request('/api/problems'),
        isAuthenticated && auth?.accessToken
          ? request('/api/me/solved-problems', {
            headers: { Authorization: `Bearer ${auth.accessToken}` },
          })
          : Promise.resolve([]),
      ]);
      setProblems(Array.isArray(problemData) ? problemData : []);
      setSolvedProblemIds(Array.isArray(solvedData) ? solvedData : []);
    } catch (error) {
      setMessage(error.message);
    } finally {
      setBusy(false);
    }
  }

  useEffect(() => {
    loadProblems();
  }, [auth?.accessToken, isAuthenticated]);

  return (
    <section className="landing-page problem-list-page">
      <section className="landing-section">
        <div className="problem-list-heading">
          <h2>문제 목록</h2>
          <div className="problem-list-tools">
            <button
              className="category-toggle-button"
              type="button"
              aria-pressed={showCategories}
              onClick={() => setShowCategories((current) => !current)}
            >
              {showCategories ? '카테고리 숨기기' : '카테고리 보기'}
            </button>
            <label className="problem-search">
              <Search size={16} />
              <input
                value={problemSearch}
                onChange={(event) => setProblemSearch(event.target.value)}
                placeholder="문제 검색..."
              />
            </label>
            <div className="difficulty-filter" aria-label="난이도 필터">
              {[{ value: 'ALL', label: '전체' }, ...DIFFICULTY_OPTIONS].map((item) => (
                <button
                  className={difficultyFilter === item.value ? 'is-active' : ''}
                  type="button"
                  key={item.value}
                  onClick={() => setDifficultyFilter(item.value)}
                >
                  {item.label}
                </button>
              ))}
            </div>
          </div>
        </div>
        <ProblemTable
          problems={visibleProblems}
          busy={busy}
          navigate={navigate}
          showCategories={showCategories}
          solvedProblemIds={solvedProblemIds}
        />
      </section>
    </section>
  );
}
