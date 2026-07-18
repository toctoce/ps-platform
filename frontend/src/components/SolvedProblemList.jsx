import { useEffect, useState } from 'react';
import { Search } from 'lucide-react';
import { request } from '../api/client.js';
import {
  DIFFICULTY_OPTIONS,
  difficultyClassName,
  difficultyLabel,
} from '../problem/problemUtils.js';

const SORT_OPTIONS = [
  { value: 'SOLVED_AT_DESC', label: '최근 해결순' },
  { value: 'SOLVED_AT_ASC', label: '오래된 해결순' },
  { value: 'PROBLEM_ID_ASC', label: '문제 번호 오름차순' },
  { value: 'PROBLEM_ID_DESC', label: '문제 번호 내림차순' },
];

function formatSolvedAt(value) {
  if (!value) {
    return '-';
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? value
    : new Intl.DateTimeFormat('ko-KR', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
    }).format(date);
}

export function SolvedProblemList({ accessToken, categories, navigate }) {
  const [draftQuery, setDraftQuery] = useState('');
  const [query, setQuery] = useState('');
  const [difficulty, setDifficulty] = useState('');
  const [category, setCategory] = useState('');
  const [sort, setSort] = useState('SOLVED_AT_DESC');
  const [page, setPage] = useState(0);
  const [result, setResult] = useState({ problems: [], totalElements: 0, totalPages: 0 });
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let active = true;
    const params = new URLSearchParams({
      query,
      category,
      sort,
      page: String(page),
      size: '10',
    });
    if (difficulty) {
      params.set('difficulty', difficulty);
    }

    setBusy(true);
    setError('');
    request(`/api/me/solved-problem-details?${params.toString()}`, {
      headers: { Authorization: `Bearer ${accessToken}` },
    })
      .then((data) => {
        if (active) {
          setResult(data);
        }
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
  }, [accessToken, category, difficulty, page, query, sort]);

  function applySearch(event) {
    event.preventDefault();
    setPage(0);
    setQuery(draftQuery.trim());
  }

  function changeFilter(setter, value) {
    setPage(0);
    setter(value);
  }

  return (
    <section className="my-page-section solved-problem-section" aria-labelledby="solved-list-heading">
      <div className="my-section-heading">
        <div>
          <h2 id="solved-list-heading">내가 푼 문제</h2>
          <span>{result.totalElements ?? 0}문제</span>
        </div>
        <form className="problem-search" onSubmit={applySearch}>
          <Search size={16} />
          <input
            value={draftQuery}
            onChange={(event) => setDraftQuery(event.target.value)}
            placeholder="제목 검색"
            aria-label="푼 문제 제목 검색"
          />
        </form>
      </div>

      <div className="solved-list-filters">
        <select
          value={difficulty}
          onChange={(event) => changeFilter(setDifficulty, event.target.value)}
          aria-label="난이도 필터"
        >
          <option value="">모든 난이도</option>
          {DIFFICULTY_OPTIONS.map((item) => (
            <option key={item.value} value={item.value}>{item.label}</option>
          ))}
        </select>
        <select
          value={category}
          onChange={(event) => changeFilter(setCategory, event.target.value)}
          aria-label="카테고리 필터"
        >
          <option value="">모든 카테고리</option>
          {categories.map((item) => <option key={item} value={item}>{item}</option>)}
        </select>
        <select
          value={sort}
          onChange={(event) => changeFilter(setSort, event.target.value)}
          aria-label="정렬 기준"
        >
          {SORT_OPTIONS.map((item) => (
            <option key={item.value} value={item.value}>{item.label}</option>
          ))}
        </select>
      </div>

      <div className="solved-problem-table">
        <div className="solved-problem-head">
          <span>문제</span>
          <span>난이도</span>
          <span>카테고리</span>
          <span>최근 해결일</span>
        </div>
        {busy ? <p className="section-state">푼 문제를 불러오는 중...</p> : null}
        {!busy && error ? <p className="section-state is-error">{error}</p> : null}
        {!busy && !error && (result.problems ?? []).length === 0 ? (
          <p className="section-state">조건에 맞는 푼 문제가 없습니다.</p>
        ) : null}
        {!busy && !error ? (result.problems ?? []).map((problem) => (
          <button
            className="solved-problem-row"
            type="button"
            key={problem.problemId}
            onClick={() => navigate(`/problems/${problem.problemId}`)}
          >
            <strong>{problem.problemId}. {problem.title}</strong>
            <span className={difficultyClassName(problem.difficulty)}>
              {difficultyLabel(problem.difficulty)}
            </span>
            <span>{problem.categories?.join(', ') || '-'}</span>
            <span>{formatSolvedAt(problem.solvedAt)}</span>
          </button>
        )) : null}
      </div>

      {(result.totalPages ?? 0) > 1 ? (
        <div className="pagination">
          <button
            className="ghost-button with-text"
            type="button"
            disabled={page === 0 || busy}
            onClick={() => setPage((current) => Math.max(0, current - 1))}
          >
            이전
          </button>
          <span>{page + 1} / {result.totalPages}</span>
          <button
            className="ghost-button with-text"
            type="button"
            disabled={page + 1 >= result.totalPages || busy}
            onClick={() => setPage((current) => current + 1)}
          >
            다음
          </button>
        </div>
      ) : null}
    </section>
  );
}
