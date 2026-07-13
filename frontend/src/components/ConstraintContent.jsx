import { MarkdownContent } from './MarkdownContent.jsx';

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

function constraintMarkdown(value) {
  if (/`|\$/.test(value) || !shouldRenderMath(value)) {
    return value;
  }

  return `$${toLatex(value)}$`;
}

export function ConstraintContent({ value }) {
  const text = String(value ?? '').trim();

  if (!text) {
    return null;
  }

  return (
    <MarkdownContent
      source={constraintMarkdown(text)}
      emptyMessage={null}
      className="constraint-markdown"
    />
  );
}
