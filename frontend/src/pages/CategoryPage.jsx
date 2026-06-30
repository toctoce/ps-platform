import { useEffect, useMemo, useState } from 'react';
import { FileText } from 'lucide-react';
import { request } from '../api/client.js';
import { ProblemRow } from '../components/ProblemRow.jsx';
import { buildCategoryGroups } from '../problem/problemUtils.js';

export function CategoryPage({ category, navigate, setMessage }) {
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
    return category ? [...problems] : [];
  }, [category, problems]);

  async function loadProblems() {
    setBusy(true);
    try {
      const endpoint = category
        ? `/api/problems?category=${encodeURIComponent(category)}`
        : '/api/problems';
      const data = await request(endpoint);
      setProblems(data);
      if (!category) {
        setMessage(`카테고리 목록을 불러왔습니다.`);
      } else {
        setMessage(`${category} 카테고리 문제 ${data.length}개`);
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
                  <span>{item.categoryName}</span>
                  <strong>{item.count}문제</strong>
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

    </section>
  );
}
