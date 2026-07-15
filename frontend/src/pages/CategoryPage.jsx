import { useEffect, useMemo, useState } from 'react';
import { request } from '../api/client.js';
import { ProblemTable } from '../components/ProblemTable.jsx';
import { DIFFICULTY_OPTIONS } from '../problem/problemUtils.js';

export function CategoryPage({ category, navigate, setMessage }) {
  const [problems, setProblems] = useState([]);
  const [categories, setCategories] = useState([]);
  const [busy, setBusy] = useState(false);
  const [difficultyFilter, setDifficultyFilter] = useState('ALL');

  const categoryRows = useMemo(() => {
    if (!category) {
      return categories
        .map((item) => ({ categoryName: item.name, count: item.problemCount ?? 0 }))
        .sort((a, b) => a.categoryName.localeCompare(b.categoryName));
    }

    return [{ categoryName: category, count: problems.length }];
  }, [categories, category, problems.length]);

  const selectedProblems = useMemo(() => {
    if (!category) {
      return [];
    }
    return [...problems]
      .sort((a, b) => a.id - b.id)
      .filter((problem) => difficultyFilter === 'ALL' || problem.difficulty === difficultyFilter);
  }, [category, difficultyFilter, problems]);

  async function loadProblems() {
    setBusy(true);
    try {
      if (category) {
        const data = await request(`/api/problems?category=${encodeURIComponent(category)}`);
        setProblems(Array.isArray(data) ? data : []);
        setCategories([]);
      } else {
        const data = await request('/api/categories');
        setCategories(Array.isArray(data) ? data : []);
        setProblems([]);
      }
    } catch (error) {
      setMessage(error.message);
    } finally {
      setBusy(false);
    }
  }

  useEffect(() => {
    loadProblems();
    setDifficultyFilter('ALL');
  }, [category]);

  return (
    <section className={`open-page category-page${category ? ' category-detail-page' : ''}`}>
      <header className="open-page-header">
        <h1>{category ? category : '카테고리 목록'}</h1>
        {category ? (
          <div className="category-page-actions">
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
        ) : null}
      </header>

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
                <span>{item.categoryName}</span>
                <strong>{item.count}문제</strong>
              </button>
            ))
          )}
        </div>
      ) : (
        <ProblemTable
          problems={selectedProblems}
          busy={busy}
          emptyMessage="해당 카테고리에 문제가 없습니다."
          navigate={navigate}
          showCategories={false}
        />
      )}
    </section>
  );
}
