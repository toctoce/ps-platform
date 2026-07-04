import { useEffect, useMemo, useState } from 'react';
import { FileText } from 'lucide-react';
import { request } from '../api/client.js';
import { ProblemRow } from '../components/ProblemRow.jsx';

export function CategoryPage({ category, navigate, setMessage }) {
  const [problems, setProblems] = useState([]);
  const [categories, setCategories] = useState([]);
  const [busy, setBusy] = useState(false);

  const categoryRows = useMemo(() => {
    if (!category) {
      return categories
        .map((item) => ({ categoryName: item.name, count: item.problemCount ?? 0 }))
        .sort((a, b) => a.categoryName.localeCompare(b.categoryName));
    }

    return [{ categoryName: category, count: problems.length }];
  }, [categories, category, problems.length]);

  const selectedProblems = useMemo(() => {
    return category ? [...problems] : [];
  }, [category, problems]);

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
