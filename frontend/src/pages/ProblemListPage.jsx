import { useEffect, useMemo, useState } from 'react';
import { Search } from 'lucide-react';
import { request } from '../api/client.js';
import {
  difficultyLabel,
  DIFFICULTY_OPTIONS,
  problemCategoryNames,
} from '../problem/problemUtils.js';

export function ProblemListPage({ navigate, setMessage }) {
  const [problems, setProblems] = useState([]);
  const [busy, setBusy] = useState(false);
  const [problemSearch, setProblemSearch] = useState('');
  const [difficultyFilter, setDifficultyFilter] = useState('ALL');

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
      const data = await request('/api/problems');
      setProblems(Array.isArray(data) ? data : []);
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
    <section className="landing-page problem-list-page">
      <section className="landing-section">
        <div className="problem-list-heading">
          <h2>문제 목록</h2>
          <div className="problem-list-tools">
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
        <div className="landing-problem-table">
          <div className="problem-table-head">
            <span>제목</span>
            <span>카테고리</span>
            <span>난이도</span>
          </div>
          {visibleProblems.length === 0 ? (
            <p className="problem-table-empty">{busy ? '불러오는 중...' : '등록된 문제가 없습니다.'}</p>
          ) : (
            visibleProblems.map((problem) => (
              <button
                className="problem-table-row"
                type="button"
                key={problem.id}
                onClick={() => navigate(`/problems/${problem.id}`)}
              >
                <strong>
                  {problem.id}. {problem.title}
                </strong>
                <span>{problemCategoryNames(problem).filter((item) => item !== '미분류').join(', ') || '-'}</span>
                <span className="difficulty-chip">{difficultyLabel(problem.difficulty)}</span>
              </button>
            ))
          )}
        </div>
      </section>
    </section>
  );
}
