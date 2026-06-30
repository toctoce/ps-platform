import { difficultyLabel, problemCategoryNames } from '../problem/problemUtils.js';

export function ProblemRow({ problem, navigate }) {
  const categories = problemCategoryNames(problem);
  return (
    <button className="problem-row" type="button" onClick={() => navigate(`/problems/${problem.id}`)}>
      <div className="category-chip-list">
        {categories.map((category) => (
          <span className="category-chip" key={`${problem.id}-${category}`}>
            {category}
          </span>
        ))}
      </div>
      <div className="problem-title-row">
        <strong>{problem.title}</strong>
        <span className="difficulty-chip">{difficultyLabel(problem.difficulty)}</span>
      </div>
    </button>
  );
}
