import { difficultyLabel, problemCategoryNames } from '../problem/problemUtils.js';

export function ProblemTable({
  problems,
  busy = false,
  emptyMessage = '등록된 문제가 없습니다.',
  navigate,
}) {
  const rows = Array.isArray(problems) ? problems : [];

  return (
    <div className="landing-problem-table">
      <div className="problem-table-head">
        <span>제목</span>
        <span>카테고리</span>
        <span>난이도</span>
      </div>
      {rows.length === 0 ? (
        <p className="problem-table-empty">{busy ? '불러오는 중...' : emptyMessage}</p>
      ) : (
        rows.map((problem) => (
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
  );
}
