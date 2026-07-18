import { useState } from 'react';
import { request } from '../api/client.js';
import { BRAND_LOGO_SRC, BRAND_NAME } from '../brand.js';

export function LoginPage({ onLoginSuccess, setMessage, navigate }) {
  const [busy, setBusy] = useState(false);
  const [form, setForm] = useState({ email: '', password: '' });
  const [loginError, setLoginError] = useState('');

  async function login(event) {
    event.preventDefault();
    if (!form.email.trim() || !form.password.trim()) {
      const message = '이메일/비밀번호를 입력해 주세요.';
      setLoginError(message);
      setMessage(message);
      return;
    }

    setLoginError('');
    setBusy(true);
    try {
      const auth = await request('/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          provider: 'local',
          credentials: {
            email: form.email,
            password: form.password,
          },
        }),
      });
      if (!auth) {
        setMessage('로그인 응답이 비어 있습니다.');
        return;
      }
      if (!auth.accessToken) {
        setMessage('로그인 응답 포맷이 올바르지 않습니다.');
        return;
      }
      onLoginSuccess(auth);
    } catch (error) {
      if (error.status === 401 || error.message.includes('Invalid email or password')) {
        const message = '아이디 또는 비밀번호가 올바르지 않습니다.';
        setLoginError(message);
        setMessage(message);
        return;
      }
      setLoginError('로그인 중 오류가 발생했습니다.');
      setMessage(error.message);
    } finally {
      setBusy(false);
    }
  }

  function update(field, value) {
    setLoginError('');
    setForm((current) => ({ ...current, [field]: value }));
  }

  return (
    <section className="auth-page">
      <div className="auth-brand">
        <span className="site-logo-mark">
          <img src={BRAND_LOGO_SRC} alt="" />
        </span>
        <strong>{BRAND_NAME}</strong>
        <p>다시 만나서 반가워요</p>
      </div>
      <form className="panel form-panel auth-form" onSubmit={login}>
        <div className="panel-header">
          <h2>로그인</h2>
        </div>

        {loginError ? (
          <div className="auth-alert" role="alert">
            {loginError}
          </div>
        ) : null}
        <label>
          이메일
          <input
            type="email"
            value={form.email}
            onChange={(event) => update('email', event.target.value)}
            autoComplete="username"
          />
        </label>
        <label>
          비밀번호
          <input
            type="password"
            value={form.password}
            onChange={(event) => update('password', event.target.value)}
            autoComplete="current-password"
          />
        </label>
        <button className="primary-button" type="submit" disabled={busy}>
          로그인
        </button>

        <p className="auth-switch">
          아직 계정이 없으신가요?
          <button type="button" onClick={() => navigate('/signup')}>
            회원가입
          </button>
        </p>
      </form>
      <button className="auth-back-link" type="button" onClick={() => navigate('/password-recovery')}>
        비밀번호 찾기
      </button>
    </section>
  );
}
