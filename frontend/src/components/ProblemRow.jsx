import { difficultyClassName, difficultyLabel, problemCategoryNames } from '../problem/problemUtils.js';

export function ProblemRow({ problem, navigate, showCategories = true, showNumber = false }) {
  const categories = problemCategoryNames(problem);
  return (
    <button className="problem-row" type="button" onClick={() => navigate(`/problems/${problem.id}`)}>
      {showCategories ? (
        <div className="category-chip-list">
          {categories.map((category) => (
            <span className="category-chip" key={`${problem.id}-${category}`}>
              {category}
            </span>
          ))}
        </div>
      ) : null}
      <div className="problem-title-row">
        <strong>{showNumber ? `${problem.id}. ` : ''}{problem.title}</strong>
        <span className={difficultyClassName(problem.difficulty)}>{difficultyLabel(problem.difficulty)}</span>
      </div>
    </button>
  );
}
