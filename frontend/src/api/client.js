function safeJsonRequest(response) {
  return response.text().then((text) => {
    if (!text) {
      return null;
    }
    try {
      return JSON.parse(text);
    } catch (_error) {
      return text;
    }
  });
}

export async function request(path, options = {}) {
  const response = await fetch(path, options);
  if (!response.ok) {
    const body = await safeJsonRequest(response);
    throw new Error(`${response.status} ${response.statusText}${body ? `: ${body}` : ''}`);
  }
  return safeJsonRequest(response);
}
