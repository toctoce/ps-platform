import { difficultyClassName, difficultyLabel, problemCategoryNames } from '../problem/problemUtils.js';

export function ProblemTable({
  problems,
  busy = false,
  emptyMessage = '등록된 문제가 없습니다.',
  navigate,
  showCategories = true,
}) {
  const rows = Array.isArray(problems) ? problems : [];

  return (
    <div className={`landing-problem-table${showCategories ? '' : ' hide-category-column'}`}>
      <div className="problem-table-head">
        <span>제목</span>
        {showCategories ? <span>카테고리</span> : null}
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
            {showCategories ? (
              <span>{problemCategoryNames(problem).filter((item) => item !== '미분류').join(', ') || '-'}</span>
            ) : null}
            <span className={difficultyClassName(problem.difficulty)}>{difficultyLabel(problem.difficulty)}</span>
          </button>
        ))
      )}
    </div>
  );
}
