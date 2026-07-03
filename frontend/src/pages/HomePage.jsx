import { useEffect, useMemo, useState } from 'react';
import { List, Play } from 'lucide-react';
import { request } from '../api/client.js';
import {
  buildCategoryGroups,
} from '../problem/problemUtils.js';

export function HomePage({ navigate, setMessage }) {
  const [problems, setProblems] = useState([]);
  const [busy, setBusy] = useState(false);

  const groups = useMemo(() => buildCategoryGroups(problems), [problems]);
  const categoryRows = useMemo(() => (
    Array.from(groups.entries())
      .map(([categoryName, list]) => ({ categoryName, count: list.length }))
      .sort((a, b) => b.count - a.count || a.categoryName.localeCompare(b.categoryName))
      .slice(0, 6)
  ), [groups]);

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
    <section className="landing-page">
      <section className="landing-hero">
        <div className="hero-copy">
          <p className="hero-line">오늘도 한 문제만 풀고 가세요.</p>
          <div className="hero-actions">
            <button className="primary-button" type="button" onClick={() => navigate('/random')}>
              <Play size={17} />
              바로 문제 풀기
            </button>
            <button className="ghost-button" type="button" onClick={() => navigate('/problems')}>
              <List size={17} />
              문제 목록
            </button>
          </div>
        </div>
      </section>

      <section className="landing-stats" aria-label="서비스 통계">
        <div>
          <strong>{problems.length.toLocaleString()}</strong>
          <span>총 문제 수</span>
        </div>
        <div>
          <strong>{categoryRows.length.toLocaleString()}</strong>
          <span>카테고리</span>
        </div>
        <div>
          <strong>{busy ? '-' : '실시간'}</strong>
          <span>문제 목록</span>
        </div>
        <div>
          <strong>Java</strong>
          <span>현재 지원 언어</span>
        </div>
      </section>

      <section className="landing-section">
        <div className="section-heading">
          <h2>카테고리별로 풀기</h2>
          <button className="text-button" type="button" onClick={() => navigate('/categories')}>
            전체 보기
          </button>
        </div>
        <div className="landing-category-grid">
          {categoryRows.length === 0 ? (
            <p className="empty">카테고리 데이터가 없습니다.</p>
          ) : (
            categoryRows.map((item) => (
              <button
                className="landing-category-card"
                type="button"
                key={item.categoryName}
                onClick={() => navigate(`/categories/${encodeURIComponent(item.categoryName)}`)}
              >
                <span>{item.categoryName}</span>
                <strong>{item.count} 문제</strong>
              </button>
            ))
          )}
        </div>
      </section>

    </section>
  );
}
