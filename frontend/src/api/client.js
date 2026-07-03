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
    const message = body && typeof body === 'object' && 'message' in body ? body.message : body;
    throw new Error(`${response.status} ${response.statusText}${message ? `: ${message}` : ''}`);
  }
  return safeJsonRequest(response);
}
