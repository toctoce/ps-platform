import { useEffect, useMemo, useState } from 'react';
import { List, Play } from 'lucide-react';
import { request } from '../api/client.js';

export function HomePage({ navigate, setMessage }) {
  const [problems, setProblems] = useState([]);
  const [categories, setCategories] = useState([]);
  const [todaySubmissionCount, setTodaySubmissionCount] = useState(null);
  const [todaySolverCount, setTodaySolverCount] = useState(null);
  const [busy, setBusy] = useState(false);

  const categoryRows = useMemo(() => (
    categories
      .map((category) => ({
        categoryName: category.name,
        count: category.problemCount ?? 0,
      }))
      .sort((a, b) => b.count - a.count || a.categoryName.localeCompare(b.categoryName))
  ), [categories]);
  const statItems = [
    {
      label: '총 문제 수',
      value: busy ? '-' : problems.length.toLocaleString(),
    },
    {
      label: '오늘 제출 수',
      value: busy || todaySubmissionCount === null ? '-' : todaySubmissionCount.toLocaleString(),
    },
    {
      label: '오늘 푼 사람',
      value: busy || todaySolverCount === null ? '-' : todaySolverCount.toLocaleString(),
    },
  ];

  async function loadProblems() {
    setBusy(true);
    try {
      const [problemData, submissionStats, categoryData] = await Promise.all([
        request('/api/problems'),
        request('/api/submissions/stats/today'),
        request('/api/categories'),
      ]);
      setProblems(Array.isArray(problemData) ? problemData : []);
      setCategories(Array.isArray(categoryData) ? categoryData : []);
      setTodaySubmissionCount(submissionStats?.todaySubmissionCount ?? 0);
      setTodaySolverCount(submissionStats?.todaySolverCount ?? 0);
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
        <div className="landing-stats-track">
          {[0, 1].map((setIndex) => (
            <div className="landing-stats-set" aria-hidden={setIndex === 1} key={setIndex}>
              {statItems.map((item) => (
                <div className="landing-stat-item" key={`${setIndex}-${item.label}`}>
                  <strong>{item.value}</strong>
                  <span>{item.label}</span>
                </div>
              ))}
            </div>
          ))}
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
