import { useEffect, useState } from 'react';
import { request } from '../api/client.js';

function formatExperience(entry) {
  if (entry.requiredExperience === 0) {
    return 'MAX';
  }
  const current = Number(entry.currentLevelExperience ?? 0).toLocaleString('ko-KR');
  const required = Number(entry.requiredExperience ?? 0).toLocaleString('ko-KR');
  return `${current} / ${required} XP`;
}

export function RankingPage() {
  const [rankings, setRankings] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let active = true;

    request('/api/rankings')
      .then((data) => {
        if (active) {
          setRankings(Array.isArray(data) ? data : []);
        }
      })
      .catch((requestError) => {
        if (active) {
          setError(requestError.message);
        }
      })
      .finally(() => {
        if (active) {
          setLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, []);

  return (
    <section className="open-page ranking-page">
      <header className="open-page-header">
        <h1>랭킹</h1>
      </header>

      {loading ? <p className="ranking-state">랭킹을 불러오는 중입니다.</p> : null}
      {!loading && error ? <p className="ranking-state error-state">{error}</p> : null}
      {!loading && !error && rankings.length === 0 ? (
        <p className="ranking-state">아직 경험치를 획득한 사용자가 없습니다.</p>
      ) : null}
      {!loading && !error && rankings.length > 0 ? (
        <table className="ranking-table">
          <thead>
            <tr>
              <th scope="col">순위</th>
              <th scope="col">닉네임</th>
              <th scope="col">레벨</th>
              <th scope="col">레벨 경험치</th>
            </tr>
          </thead>
          <tbody>
            {rankings.map((entry, index) => (
              <tr className={entry.rank <= 3 ? `is-top-${entry.rank}` : ''} key={`${entry.username}-${index}`}>
                <td className="ranking-position">{entry.rank}</td>
                <td className="ranking-username">{entry.username}</td>
                <td className="ranking-level">Lv. {entry.level}</td>
                <td className="ranking-experience">{formatExperience(entry)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      ) : null}
    </section>
  );
}
