import { useState } from 'react';
import { Code2 } from 'lucide-react';
import { request } from '../api/client.js';

export function LoginPage({ onLoginSuccess, setMessage, isAuthenticated, navigate }) {
  const [busy, setBusy] = useState(false);
  const [form, setForm] = useState({ email: '', password: '' });

  async function login(event) {
    event.preventDefault();
    if (!form.email.trim() || !form.password.trim()) {
      setMessage('이메일/비밀번호를 입력해 주세요.');
      return;
    }

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
      if (error.message.includes('401') || error.message.includes('Invalid email or password')) {
        setMessage('아이디 또는 비밀번호가 올바르지 않습니다.');
        return;
      }
      setMessage(error.message);
    } finally {
      setBusy(false);
    }
  }

  function update(field, value) {
    setForm((current) => ({ ...current, [field]: value }));
  }

  return (
    <section className="auth-page">
      <div className="auth-brand">
        <span className="site-logo-mark">
          <Code2 size={18} />
        </span>
        <strong>빵구리즘</strong>
        <p>다시 만나서 반가워요</p>
      </div>
      <form className="panel form-panel auth-form" onSubmit={login}>
        <div className="panel-header">
          <h2>로그인</h2>
        </div>

        {isAuthenticated ? (
          <p className="empty">이미 로그인되어 있습니다.</p>
        ) : (
          <>
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
          </>
        )}

        <p className="auth-switch">
          아직 계정이 없으신가요?
          <button type="button" onClick={() => navigate('/signup')}>
            회원가입
          </button>
        </p>
      </form>
      <button className="auth-back-link" type="button" onClick={() => navigate('/')}>
        ← 홈으로 돌아가기
      </button>
    </section>
  );
}
