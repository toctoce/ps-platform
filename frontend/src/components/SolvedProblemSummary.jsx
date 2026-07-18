import { useEffect, useState } from 'react';
import { request } from '../api/client.js';
import { difficultyClassName, difficultyLabel } from '../problem/problemUtils.js';

export function SolvedProblemSummary({ accessToken, onCategoriesLoaded }) {
  const [summary, setSummary] = useState(null);
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let active = true;
    setBusy(true);
    setError('');
    request('/api/me/solved-summary', {
      headers: { Authorization: `Bearer ${accessToken}` },
    })
      .then((data) => {
        if (!active) {
          return;
        }
        setSummary(data);
        onCategoriesLoaded(data?.categories?.map((item) => item.category) ?? []);
      })
      .catch((requestError) => {
        if (active) {
          setError(requestError.message);
        }
      })
      .finally(() => {
        if (active) {
          setBusy(false);
        }
      });
    return () => {
      active = false;
    };
  }, [accessToken]);

  return (
    <section className="my-page-section" aria-labelledby="solved-summary-heading">
      <h2 id="solved-summary-heading">풀이 요약</h2>
      {busy ? <p className="section-state">풀이 기록을 불러오는 중...</p> : null}
      {!busy && error ? <p className="section-state is-error">{error}</p> : null}
      {!busy && !error && summary ? (
        <>
          <div className="solved-total">
            <span>전체 푼 문제</span>
            <strong>{summary.totalSolved ?? 0}</strong>
          </div>
          <div className="solved-difficulty-summary">
            {(summary.difficulties ?? []).map((item) => (
              <div key={item.difficulty}>
                <span className={difficultyClassName(item.difficulty)}>
                  {difficultyLabel(item.difficulty)}
                </span>
                <strong>{item.count}</strong>
              </div>
            ))}
          </div>
          <div className="solved-category-summary">
            <h3>카테고리별</h3>
            {(summary.categories ?? []).length === 0 ? (
              <p className="section-state">아직 푼 문제가 없습니다.</p>
            ) : (
              <div>
                {summary.categories.map((item) => (
                  <span key={item.category}>
                    {item.category} <strong>{item.count}</strong>
                  </span>
                ))}
              </div>
            )}
          </div>
        </>
      ) : null}
    </section>
  );
}
