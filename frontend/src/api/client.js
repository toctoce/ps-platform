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

class ApiError extends Error {
  constructor(response, body) {
    const message = body && typeof body === 'object' && 'message' in body ? body.message : body;
    super(`${response.status} ${response.statusText}${message ? `: ${message}` : ''}`);
    this.name = 'ApiError';
    this.status = response.status;
    this.body = body;
  }
}

let authSession = {
  getAuth: () => null,
  onRefresh: () => {},
  onRefreshFailure: () => {},
};

let refreshPromise = null;

export function configureAuthSession(handlers) {
  authSession = {
    getAuth: handlers.getAuth ?? authSession.getAuth,
    onRefresh: handlers.onRefresh ?? authSession.onRefresh,
    onRefreshFailure: handlers.onRefreshFailure ?? authSession.onRefreshFailure,
  };
}

export async function request(path, options = {}) {
  const response = await fetch(path, options);
  if (response.status === 401 && shouldRefresh(path, options)) {
    const nextAuth = await refreshAuth();
    if (nextAuth) {
      return request(path, withRefreshedAccessToken(options, nextAuth.accessToken));
    }
  }
  if (!response.ok) {
    const body = await safeJsonRequest(response);
    throw new ApiError(response, body);
  }
  return safeJsonRequest(response);
}

function shouldRefresh(path, options) {
  if (options.skipAuthRefresh) {
    return false;
  }
  if (path === '/api/auth/login' || path === '/api/auth/refresh' || path === '/api/auth/logout') {
    return false;
  }
  return Boolean(authSession.getAuth()?.refreshToken);
}

async function refreshAuth() {
  if (!refreshPromise) {
    const refreshToken = authSession.getAuth()?.refreshToken;
    refreshPromise = fetch('/api/auth/refresh', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken }),
    })
      .then(async (response) => {
        const body = await safeJsonRequest(response);
        if (!response.ok) {
          throw new ApiError(response, body);
        }
        const nextAuth = {
          accessToken: body.accessToken,
          refreshToken: body.refreshToken,
          user: body.user,
        };
        authSession.onRefresh(nextAuth);
        return nextAuth;
      })
      .catch((error) => {
        authSession.onRefreshFailure(error);
        return null;
      })
      .finally(() => {
        refreshPromise = null;
      });
  }
  return refreshPromise;
}

function withRefreshedAccessToken(options, accessToken) {
  const headers = new Headers(options.headers ?? {});
  headers.set('Authorization', `Bearer ${accessToken}`);
  return {
    ...options,
    headers,
    skipAuthRefresh: true,
  };
}
