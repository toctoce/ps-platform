import { marked } from 'marked';
import { useMemo } from 'react';

function sanitizeHtml(html) {
  const parser = new DOMParser();
  const doc = parser.parseFromString(html, 'text/html');

  doc.querySelectorAll('script, style').forEach((node) => {
    node.remove();
  });

  doc.querySelectorAll('*').forEach((node) => {
    Array.from(node.attributes).forEach((attribute) => {
      const name = attribute.name.toLowerCase();
      const value = attribute.value ?? '';

      if (name.startsWith('on')) {
        node.removeAttribute(attribute.name);
        return;
      }

      if (name === 'href' && /^\s*javascript:/i.test(value)) {
        node.setAttribute(attribute.name, '#');
      }
    });
  });

  return doc.body.innerHTML;
}

export function MarkdownContent({ source, emptyMessage = '등록된 내용이 없습니다.' }) {
  const text = source?.trim();

  if (!text) {
    return <p className="empty">{emptyMessage}</p>;
  }

  const html = useMemo(() => {
    const rawHtml = marked.parse(text, {
      gfm: true,
      breaks: true,
      mangle: false,
      headerIds: false,
    });

    return sanitizeHtml(rawHtml);
  }, [text]);

  return <div className="markdown-content" dangerouslySetInnerHTML={{ __html: html }} />;
}
