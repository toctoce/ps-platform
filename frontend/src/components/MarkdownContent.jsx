import ReactMarkdown from 'react-markdown';
import rehypeKatex from 'rehype-katex';
import remarkBreaks from 'remark-breaks';
import remarkGfm from 'remark-gfm';
import remarkMath from 'remark-math';

const markdownComponents = {
  a: ({ children, node: _node, ...props }) => (
    <a {...props} target="_blank" rel="noopener noreferrer">
      {children}
    </a>
  ),
  // 문제 데이터에 포함된 외부 이미지가 사용자 추적 요청을 만들지 않도록 렌더링하지 않는다.
  img: () => null,
};

export function MarkdownContent({ source, emptyMessage = '등록된 내용이 없습니다.', className = '' }) {
  const text = source?.trim();

  if (!text) {
    return emptyMessage ? <p className="empty">{emptyMessage}</p> : null;
  }

  return (
    <div className={`markdown-content ${className}`.trim()}>
      <ReactMarkdown
        skipHtml
        remarkPlugins={[remarkGfm, remarkBreaks, remarkMath]}
        rehypePlugins={[rehypeKatex]}
        components={markdownComponents}
      >
        {text}
      </ReactMarkdown>
    </div>
  );
}
