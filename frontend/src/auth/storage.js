const authStorageKey = 'ps-platform-auth';

export function getStoredAuth() {
  const raw = localStorage.getItem(authStorageKey);
  if (!raw) {
    return null;
  }

  try {
    const parsed = JSON.parse(raw);
    return {
      accessToken: parsed.accessToken || '',
      refreshToken: parsed.refreshToken || '',
      user: parsed.user || null,
    };
  } catch (_error) {
    return null;
  }
}

export function saveStoredAuth(auth) {
  localStorage.setItem(authStorageKey, JSON.stringify(auth));
}

export function clearStoredAuth() {
  localStorage.removeItem(authStorageKey);
}
