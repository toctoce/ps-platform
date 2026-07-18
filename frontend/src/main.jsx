import { StrictMode, useCallback, useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { LogIn, LogOut, MessageCircle, Settings, ShieldCheck, UserPlus, UserRound, X } from 'lucide-react';
import 'katex/dist/katex.min.css';
import './styles.css';
import { configureAuthSession, request } from './api/client.js';
import { clearStoredAuth, getStoredAuth, saveStoredAuth } from './auth/storage.js';
import { BRAND_LOGO_SRC, BRAND_NAME } from './brand.js';
import { ADMIN_TABS, AdminPage } from './pages/AdminPage.jsx';
import { CategoryPage } from './pages/CategoryPage.jsx';
import { HomePage } from './pages/HomePage.jsx';
import { LoginPage } from './pages/LoginPage.jsx';
import { MyPage } from './pages/MyPage.jsx';
import { PasswordRecoveryPage } from './pages/PasswordRecoveryPage.jsx';
import { ProblemDetailPage } from './pages/ProblemDetailPage.jsx';
import { ProblemListPage } from './pages/ProblemListPage.jsx';
import { RandomProblemPage } from './pages/RandomProblemPage.jsx';
import { SignupPage } from './pages/SignupPage.jsx';
import { parseRoute } from './routing.js';

const SUPPORT_INQUIRY_TYPES = [
  { value: 'BUG', label: '버그' },
  { value: 'ACCOUNT', label: '계정' },
  { value: 'PROBLEM_OR_JUDGE', label: '문제/채점' },
  { value: 'OTHER', label: '기타' },
];

const TOAST_VISIBLE_MS = 2600;
const TOAST_FADE_MS = 400;
const MAX_TOASTS = 5;
const GUEST_ONLY_ROUTES = new Set(['login', 'signup', 'password-recovery']);

let nextToastId = 0;

function Toast({ toast, onRemove }) {
  const [isLeaving, setIsLeaving] = useState(false);

  useEffect(() => {
    const fadeTimer = window.setTimeout(() => setIsLeaving(true), TOAST_VISIBLE_MS);
    const removeTimer = window.setTimeout(
      () => onRemove(toast.id),
      TOAST_VISIBLE_MS + TOAST_FADE_MS,
    );

    return () => {
      window.clearTimeout(fadeTimer);
      window.clearTimeout(removeTimer);
    };
  }, [onRemove, toast.id]);

  return (
    <div className={`app-toast ${isLeaving ? 'is-leaving' : ''}`}>
      <span>{toast.message}</span>
    </div>
  );
}

function App() {
  const [route, setRoute] = useState(parseRoute);
  const [auth, setAuth] = useState(() => getStoredAuth());
  const [authChecked, setAuthChecked] = useState(() => !getStoredAuth()?.accessToken);
  const [toasts, setToasts] = useState([]);
  const [userMenuOpen, setUserMenuOpen] = useState(false);
  const [adminTab, setAdminTab] = useState('overview');
  const [supportInquiryOpen, setSupportInquiryOpen] = useState(false);
  const [supportInquiryBusy, setSupportInquiryBusy] = useState(false);
  const [supportInquiryForm, setSupportInquiryForm] = useState({
    email: '',
    subject: '',
    type: 'OTHER',
    message: '',
  });

  const isAuthenticated = Boolean(auth?.accessToken);
  const isAdmin = auth?.user?.role === 'ADMIN';
  const shouldHideGuestRoute = isAuthenticated && GUEST_ONLY_ROUTES.has(route.name);
  const shouldRedirectGuestRoute = authChecked && shouldHideGuestRoute;

  const removeToast = useCallback((toastId) => {
    setToasts((current) => current.filter((toast) => toast.id !== toastId));
  }, []);

  const setMessage = useCallback((message) => {
    const text = String(message ?? '').trim();
    if (!text) {
      return;
    }

    const toast = { id: ++nextToastId, message: text };
    setToasts((current) => [...current.slice(-(MAX_TOASTS - 1)), toast]);
  }, []);

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

  function openSupportInquiry() {
    setSupportInquiryForm((current) => ({
      ...current,
      email: current.email || auth?.user?.email || '',
    }));
    setUserMenuOpen(false);
    setSupportInquiryOpen(true);
  }

  function closeSupportInquiry() {
    setSupportInquiryOpen(false);
  }

  function updateSupportInquiryForm(field, value) {
    setSupportInquiryForm((current) => ({ ...current, [field]: value }));
  }

  async function submitSupportInquiry(event) {
    event.preventDefault();
    if (!supportInquiryForm.email.trim() || !supportInquiryForm.subject.trim() || !supportInquiryForm.message.trim()) {
      setMessage('이메일, 제목, 문의 내용을 입력해 주세요.');
      return;
    }

    setSupportInquiryBusy(true);
    try {
      await request('/api/support/inquiries', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(auth?.accessToken ? { Authorization: `Bearer ${auth.accessToken}` } : {}),
        },
        body: JSON.stringify({
          email: supportInquiryForm.email.trim(),
          subject: supportInquiryForm.subject.trim(),
          type: supportInquiryForm.type,
          message: supportInquiryForm.message.trim(),
        }),
      });
      setMessage('문의가 접수되었습니다.');
      setSupportInquiryForm({
        email: auth?.user?.email || '',
        subject: '',
        type: 'OTHER',
        message: '',
      });
      closeSupportInquiry();
    } catch (error) {
      setMessage(error.message);
    } finally {
      setSupportInquiryBusy(false);
    }
  }

  function navigate(path, options = {}) {
    const historyMethod = options.replace ? 'replaceState' : 'pushState';
    window.history[historyMethod](null, '', path);
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
      setAuthChecked(true);
      return;
    }

    try {
      const user = await request('/api/auth/me', {
        headers: { Authorization: `Bearer ${auth.accessToken}` },
      });
      applyAuth({ ...(getStoredAuth() ?? auth), user });
    } catch (error) {
      clearAuth();
      setMessage('자동 로그인 세션이 만료되어 로그아웃됩니다.');
    } finally {
      setAuthChecked(true);
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
      setMessage('로그아웃 중 오류가 발생해 로그인 정보를 초기화합니다.');
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

  function updateCurrentAuthUser(user) {
    applyAuth({ ...auth, user });
  }

  function handlePasswordChanged() {
    clearAuth();
    setUserMenuOpen(false);
    setMessage('비밀번호를 변경했습니다. 다시 로그인해 주세요.');
    navigate('/login?returnTo=%2Fmypage');
  }

  useEffect(() => {
    configureAuthSession({
      getAuth: () => getStoredAuth(),
      onRefresh: applyAuth,
      onRefreshFailure: () => {
        clearAuth();
        setMessage('로그인 세션이 만료되었습니다. 다시 로그인해 주세요.');
      },
    });
  }, [auth]);

  useEffect(() => {
    checkCurrentUser();

    function handlePopState() {
      setUserMenuOpen(false);
      setRoute(parseRoute());
    }

    window.addEventListener('popstate', handlePopState);
    return () => window.removeEventListener('popstate', handlePopState);
  }, []);

  useEffect(() => {
    if (!shouldRedirectGuestRoute) {
      return;
    }
    navigate(getLoginReturnTarget(), { replace: true });
  }, [shouldRedirectGuestRoute]);

  return (
    <main className="app-shell">
      <header className={`site-header ${route.name === 'admin' ? 'is-admin' : ''}`}>
        <button className="site-logo" type="button" onClick={() => navigate('/')}>
          <span className="site-logo-mark">
            <img src={BRAND_LOGO_SRC} alt="" />
          </span>
          <strong>{BRAND_NAME}</strong>
        </button>

        {route.name === 'admin' && isAdmin ? (
          <div className="admin-site-navigation">
            <strong className="admin-site-title">관리자 페이지</strong>
            <nav className="admin-tabs" aria-label="관리자 메뉴">
              {ADMIN_TABS.map(({ id, label, icon: Icon }) => (
                <button
                  className={adminTab === id ? 'is-active' : ''}
                  key={id}
                  type="button"
                  onClick={() => setAdminTab(id)}
                >
                  <Icon size={15} /> {label}
                </button>
              ))}
            </nav>
          </div>
        ) : null}

        <div className="site-actions">
          {route.name === 'home' && isAdmin ? (
            <button className="home-admin-button" type="button" onClick={() => navigate('/admin')}>
              <ShieldCheck size={16} />
              관리자 페이지
            </button>
          ) : null}
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
                  <button type="button" onClick={openSupportInquiry}>
                    <MessageCircle size={16} />
                    문의하기
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
                  <button type="button" onClick={openSupportInquiry}>
                    <MessageCircle size={16} />
                    문의하기
                  </button>
                </>
              )}
            </div>
          ) : null}
          </div>
        </div>
      </header>
      {toasts.length > 0 ? (
        <div className="app-toast-stack" aria-live="polite" aria-atomic="false">
          {toasts.map((toast) => (
            <Toast key={toast.id} toast={toast} onRemove={removeToast} />
          ))}
        </div>
      ) : null}
      <section className="app-content">
        {shouldHideGuestRoute ? null : route.name === 'login' ? (
          <LoginPage
            onLoginSuccess={handleLoginSuccess}
            setMessage={setMessage}
            navigate={navigate}
          />
        ) : route.name === 'signup' ? (
          <SignupPage
            setMessage={setMessage}
            navigate={navigate}
          />
        ) : route.name === 'password-recovery' ? (
          <PasswordRecoveryPage
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
            onAuthUserChange={updateCurrentAuthUser}
            onLogout={logout}
            onPasswordChanged={handlePasswordChanged}
            onDeleteAccount={deleteCurrentAccount}
          />
        ) : route.name === 'admin' ? (
          <AdminPage
            auth={auth}
            setMessage={setMessage}
            navigate={navigate}
            activeTab={adminTab}
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
            auth={auth}
            isAuthenticated={isAuthenticated}
          />
        ) : (
          <HomePage
            navigate={navigate}
            setMessage={setMessage}
          />
        )}
      </section>
      {supportInquiryOpen ? (
        <div className="support-inquiry-backdrop" role="presentation" onMouseDown={closeSupportInquiry}>
          <section
            className="support-inquiry-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="support-inquiry-title"
            onMouseDown={(event) => event.stopPropagation()}
          >
            <header className="support-inquiry-header">
              <h2 id="support-inquiry-title">문의하기</h2>
              <button
                className="ghost-button ide-small-button"
                type="button"
                aria-label="문의 모달 닫기"
                onClick={closeSupportInquiry}
              >
                <X size={14} />
              </button>
            </header>
            <form className="support-inquiry-form" onSubmit={submitSupportInquiry}>
              <label>
                이메일
                <input
                  type="email"
                  value={supportInquiryForm.email}
                  onChange={(event) => updateSupportInquiryForm('email', event.target.value)}
                  autoComplete="email"
                />
              </label>
              <label>
                문의 종류
                <select
                  value={supportInquiryForm.type}
                  onChange={(event) => updateSupportInquiryForm('type', event.target.value)}
                >
                  {SUPPORT_INQUIRY_TYPES.map((item) => (
                    <option key={item.value} value={item.value}>
                      {item.label}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                제목
                <input
                  value={supportInquiryForm.subject}
                  onChange={(event) => updateSupportInquiryForm('subject', event.target.value)}
                  maxLength={120}
                />
              </label>
              <label>
                문의 내용
                <textarea
                  value={supportInquiryForm.message}
                  onChange={(event) => updateSupportInquiryForm('message', event.target.value)}
                  maxLength={4000}
                  placeholder="확인할 수 있도록 상황을 구체적으로 적어주세요."
                />
              </label>
              <div className="support-inquiry-actions">
                <button className="ghost-button" type="button" onClick={closeSupportInquiry}>
                  취소
                </button>
                <button className="primary-button" type="submit" disabled={supportInquiryBusy}>
                  보내기
                </button>
              </div>
            </form>
          </section>
        </div>
      ) : null}
    </main>
  );
}

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
