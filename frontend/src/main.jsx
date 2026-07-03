import { StrictMode, useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { Code2, LogIn, LogOut, Settings, ShieldCheck, UserPlus, UserRound } from 'lucide-react';
import './styles.css';
import { request } from './api/client.js';
import { clearStoredAuth, getStoredAuth, saveStoredAuth } from './auth/storage.js';
import { AdminPage } from './pages/AdminPage.jsx';
import { CategoryPage } from './pages/CategoryPage.jsx';
import { HomePage } from './pages/HomePage.jsx';
import { LoginPage } from './pages/LoginPage.jsx';
import { MyPage } from './pages/MyPage.jsx';
import { ProblemDetailPage } from './pages/ProblemDetailPage.jsx';
import { ProblemListPage } from './pages/ProblemListPage.jsx';
import { RandomProblemPage } from './pages/RandomProblemPage.jsx';
import { SignupPage } from './pages/SignupPage.jsx';
import { parseRoute } from './routing.js';

function App() {
  const [route, setRoute] = useState(parseRoute);
  const [auth, setAuth] = useState(() => getStoredAuth());
  const [message, setMessage] = useState('');
  const [userMenuOpen, setUserMenuOpen] = useState(false);

  const isAuthenticated = Boolean(auth?.accessToken);
  const isAdmin = auth?.user?.role === 'ADMIN';

  function getLoginReturnTarget() {
    const params = new URLSearchParams(window.location.search);
    const returnTo = params.get('returnTo') ?? params.get('url');
    if (!returnTo || !returnTo.startsWith('/') || returnTo.startsWith('//')) {
      return '/';
    }
    return returnTo;
  }

  function navigateToLogin(returnTo = `${window.location.pathname}${window.location.search}`) {
    navigate(`/login?returnTo=${encodeURIComponent(returnTo)}`);
  }

  function getCurrentPath() {
    const currentPath = `${window.location.pathname}${window.location.search}`;
    return currentPath || '/';
  }

  function applyAuth(nextAuth) {
    setAuth(nextAuth);
    saveStoredAuth(nextAuth);
  }

  function clearAuth() {
    setAuth(null);
    clearStoredAuth();
  }

  function navigate(path) {
    window.history.pushState(null, '', path);
    setUserMenuOpen(false);
    setRoute(parseRoute());
  }

  function handleLoginSuccess(authResponse) {
    applyAuth({
      accessToken: authResponse.accessToken,
      refreshToken: authResponse.refreshToken,
      user: authResponse.user,
    });
    setMessage('로그인에 성공했습니다.');
    navigate(getLoginReturnTarget());
  }

  async function checkCurrentUser() {
    if (!auth?.accessToken) {
      return;
    }

    try {
      const user = await request('/api/auth/me', {
        headers: { Authorization: `Bearer ${auth.accessToken}` },
      });
      applyAuth({ ...auth, user });
    } catch (error) {
      clearAuth();
      setMessage(`자동 로그인 세션이 만료되어 로그아웃됩니다. (${error.message})`);
    }
  }

  async function logout() {
    try {
      if (auth?.refreshToken) {
        await request('/api/auth/logout', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ refreshToken: auth.refreshToken }),
        });
      }
      setMessage('로그아웃했습니다.');
    } catch (error) {
      setMessage(`로그아웃 중 오류가 발생해 로그인 정보를 초기화합니다. (${error.message})`);
    } finally {
      clearAuth();
      setUserMenuOpen(false);
      navigate(getCurrentPath());
    }
  }

  async function deleteCurrentAccount() {
    if (!auth?.accessToken) {
      setMessage('로그인 후 회원 탈퇴를 진행할 수 있습니다.');
      return;
    }

    await request('/api/auth/me', {
      method: 'DELETE',
      headers: { Authorization: `Bearer ${auth.accessToken}` },
    });
    clearAuth();
    setUserMenuOpen(false);
    setMessage('회원 탈퇴가 완료되었습니다.');
    navigate('/');
  }

  useEffect(() => {
    checkCurrentUser();

    function handlePopState() {
      setUserMenuOpen(false);
      setRoute(parseRoute());
    }

    window.addEventListener('popstate', handlePopState);
    return () => window.removeEventListener('popstate', handlePopState);
  }, []);

  return (
    <main className="app-shell">
      <header className="site-header">
        <button className="site-logo" type="button" onClick={() => navigate('/')}>
          <span className="site-logo-mark">
            <Code2 size={18} />
          </span>
          <strong>빵구리즘</strong>
        </button>

        <div className="site-actions">
          <div className="user-menu">
            <button
              className={isAuthenticated ? 'icon-action' : 'user-menu-trigger'}
              type="button"
              onClick={() => {
                if (!isAuthenticated) {
                  navigateToLogin();
                  return;
                }
                setUserMenuOpen((current) => !current);
              }}
              title="사용자 메뉴"
              aria-expanded={userMenuOpen}
            >
              {isAuthenticated ? <UserRound size={20} /> : 'Log in'}
            </button>
          {userMenuOpen ? (
            <div className="user-menu-panel">
              {isAuthenticated ? (
                <>
                  <div className="user-menu-profile">
                    <strong>{auth?.user?.username ?? '사용자'}</strong>
                    <span>{auth?.user?.email ?? '로그인됨'}</span>
                  </div>
                  <button type="button" onClick={() => navigate('/mypage')}>
                    <UserRound size={16} />
                    마이페이지
                  </button>
                  {isAdmin ? (
                    <button type="button" onClick={() => navigate('/admin')}>
                      <ShieldCheck size={16} />
                      관리자 페이지
                    </button>
                  ) : null}
                  <button
                    type="button"
                    onClick={() => {
                      setUserMenuOpen(false);
                      setMessage('설정 화면은 추가 개발 예정입니다.');
                    }}
                  >
                    <Settings size={16} />
                    설정
                  </button>
                  <button type="button" onClick={logout}>
                    <LogOut size={16} />
                    로그아웃
                  </button>
                </>
              ) : (
                <>
                  <button type="button" onClick={() => navigateToLogin()}>
                    <LogIn size={16} />
                    로그인
                  </button>
                  <button type="button" onClick={() => navigate('/signup')}>
                    <UserPlus size={16} />
                    회원가입
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      setUserMenuOpen(false);
                      setMessage('로그인 후 설정을 사용할 수 있습니다.');
                    }}
                  >
                    <Settings size={16} />
                    설정
                  </button>
                </>
              )}
            </div>
          ) : null}
          </div>
        </div>
      </header>
      <section className="app-content">
        {route.name === 'login' ? (
          <LoginPage
            onLoginSuccess={handleLoginSuccess}
            setMessage={setMessage}
            isAuthenticated={isAuthenticated}
            navigate={navigate}
          />
        ) : route.name === 'signup' ? (
          <SignupPage
            setMessage={setMessage}
            navigate={navigate}
          />
        ) : route.name === 'category-list' ? (
          <CategoryPage
            navigate={navigate}
            setMessage={setMessage}
            category={null}
          />
        ) : route.name === 'category-detail' ? (
          <CategoryPage
            category={route.category}
            navigate={navigate}
            setMessage={setMessage}
          />
        ) : route.name === 'random' ? (
          <RandomProblemPage
            navigate={navigate}
            setMessage={setMessage}
          />
        ) : route.name === 'my-page' ? (
          <MyPage
            auth={auth}
            isAuthenticated={isAuthenticated}
            setMessage={setMessage}
            navigate={navigate}
            onDeleteAccount={deleteCurrentAccount}
          />
        ) : route.name === 'admin' ? (
          <AdminPage
            auth={auth}
            setMessage={setMessage}
            navigate={navigate}
          />
        ) : route.name === 'problem-detail' ? (
          <ProblemDetailPage
            problemId={route.problemId}
            navigate={navigate}
            setMessage={setMessage}
            isAuthenticated={isAuthenticated}
            auth={auth}
          />
        ) : route.name === 'problem-list' ? (
          <ProblemListPage
            navigate={navigate}
            setMessage={setMessage}
          />
        ) : (
          <HomePage
            navigate={navigate}
            setMessage={setMessage}
          />
        )}
      </section>
    </main>
  );
}

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
