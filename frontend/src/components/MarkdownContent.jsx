function renderInline(text) {
  const parts = String(text).split(/(`[^`]+`)/g);
  return parts.map((part, index) => {
    if (part.startsWith('`') && part.endsWith('`') && part.length > 1) {
      return <code key={index}>{part.slice(1, -1)}</code>;
    }
    return part;
  });
}

function flushParagraph(blocks, paragraphLines) {
  if (paragraphLines.length === 0) {
    return;
  }
  blocks.push({
    type: 'paragraph',
    text: paragraphLines.join(' '),
  });
  paragraphLines.length = 0;
}

export function MarkdownContent({ source, emptyMessage = '등록된 내용이 없습니다.' }) {
  const text = source?.trim();
  if (!text) {
    return <p className="empty">{emptyMessage}</p>;
  }

  const blocks = [];
  const paragraphLines = [];
  const lines = text.split(/\r?\n/);

  for (let index = 0; index < lines.length; index++) {
    const line = lines[index];
    const trimmed = line.trim();

    if (trimmed === '') {
      flushParagraph(blocks, paragraphLines);
      continue;
    }

    if (trimmed.startsWith('```')) {
      flushParagraph(blocks, paragraphLines);
      const language = trimmed.slice(3).trim();
      const codeLines = [];
      index += 1;
      while (index < lines.length && !lines[index].trim().startsWith('```')) {
        codeLines.push(lines[index]);
        index += 1;
      }
      blocks.push({ type: 'code', language, text: codeLines.join('\n') });
      continue;
    }

    const heading = trimmed.match(/^(#{1,3})\s+(.+)$/);
    if (heading) {
      flushParagraph(blocks, paragraphLines);
      blocks.push({ type: 'heading', level: heading[1].length, text: heading[2] });
      continue;
    }

    if (/^[-*]\s+/.test(trimmed)) {
      flushParagraph(blocks, paragraphLines);
      const items = [];
      while (index < lines.length && /^[-*]\s+/.test(lines[index].trim())) {
        items.push(lines[index].trim().replace(/^[-*]\s+/, ''));
        index += 1;
      }
      index -= 1;
      blocks.push({ type: 'list', items });
      continue;
    }

    if (trimmed.startsWith('> ')) {
      flushParagraph(blocks, paragraphLines);
      blocks.push({ type: 'quote', text: trimmed.slice(2) });
      continue;
    }

    paragraphLines.push(trimmed);
  }
  flushParagraph(blocks, paragraphLines);

  return (
    <div className="markdown-content">
      {blocks.map((block, index) => {
        if (block.type === 'heading') {
          const Heading = `h${block.level + 1}`;
          return <Heading key={index}>{renderInline(block.text)}</Heading>;
        }
        if (block.type === 'code') {
          return (
            <pre key={index}>
              {block.language ? <span>{block.language}</span> : null}
              <code>{block.text}</code>
            </pre>
          );
        }
        if (block.type === 'list') {
          return (
            <ul key={index}>
              {block.items.map((item, itemIndex) => (
                <li key={itemIndex}>{renderInline(item)}</li>
              ))}
            </ul>
          );
        }
        if (block.type === 'quote') {
          return <blockquote key={index}>{renderInline(block.text)}</blockquote>;
        }
        return <p key={index}>{renderInline(block.text)}</p>;
      })}
    </div>
  );
}
