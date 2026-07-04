import { InlineMath } from 'react-katex';

function normalizeText(value) {
  return value
    .replace(/<=/g, '≤')
    .replace(/>=/g, '≥')
    .replace(/!=/g, '≠');
}

function shouldRenderMath(value) {
  return /(?:<=|>=|!=|[<>=])/.test(value) || /[A-Za-z]_[A-Za-z0-9]+/.test(value);
}

function escapeText(value) {
  return value
    .replace(/\\/g, '\\textbackslash{}')
    .replace(/[{}]/g, (match) => `\\${match}`);
}

function toLatex(value) {
  return value
    .replace(/([A-Za-z])_([A-Za-z0-9]+)/g, '$1_{$2}')
    .replace(/<=/g, '\\le ')
    .replace(/>=/g, '\\ge ')
    .replace(/!=/g, '\\ne ')
    .replace(/\*/g, '\\times ')
    .replace(/[가-힣][가-힣\s]*/g, (match) => `\\text{${escapeText(match.trimEnd())}}`);
}

export function ConstraintMath({ value }) {
  const text = String(value ?? '').trim();

  if (!text) {
    return null;
  }

  const fallback = <span className="constraint-text">{normalizeText(text)}</span>;

  if (!shouldRenderMath(text)) {
    return fallback;
  }

  return (
    <span className="constraint-math">
      <InlineMath math={toLatex(text)} renderError={() => fallback} />
    </span>
  );
}
