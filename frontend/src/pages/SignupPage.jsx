import { useState } from 'react';
import { Code2 } from 'lucide-react';
import { request } from '../api/client.js';
import { BRAND_LOGO_SRC, BRAND_NAME } from '../brand.js';
import { EmailVerificationFields } from '../components/EmailVerificationFields.jsx';

export function SignupPage({ setMessage, navigate }) {
  const [busy, setBusy] = useState(false);
  const [verificationToken, setVerificationToken] = useState('');
  const [form, setForm] = useState({
    email: '',
    username: '',
    password: '',
    confirmPassword: '',
  });

  async function signup(event) {
    event.preventDefault();
    if (!form.email.trim() || !form.username.trim() || !form.password.trim() || !form.confirmPassword.trim()) {
      setMessage('모든 항목을 입력해 주세요.');
      return;
    }
    if (form.password.length < 8) {
      setMessage('비밀번호는 8자 이상이어야 합니다.');
      return;
    }
    if (form.password !== form.confirmPassword) {
      setMessage('비밀번호 확인이 일치하지 않습니다.');
      return;
    }
    if (!verificationToken) {
      setMessage('이메일 인증을 완료해 주세요.');
      return;
    }

    setBusy(true);
    try {
      await request('/api/users', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          email: form.email.trim(),
          username: form.username.trim(),
          password: form.password,
          verificationToken,
        }),
      });
      setMessage('회원가입이 완료되었습니다. 로그인해 주세요.');
      navigate('/login');
    } catch (error) {
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
        <img className="auth-brand-logo" src={BRAND_LOGO_SRC} alt={BRAND_NAME} />
        <p>계정을 만들고 문제 풀이를 시작하세요</p>
      </div>
      <form className="panel form-panel auth-form" onSubmit={signup}>
        <div className="panel-header">
          <Code2 size={18} />
          <h2>회원가입</h2>
        </div>

        <EmailVerificationFields
          email={form.email}
          onEmailChange={(email) => update('email', email)}
          onVerificationTokenChange={setVerificationToken}
          requestPath="/api/email-verifications"
          confirmPath="/api/email-verifications/confirm"
          setMessage={setMessage}
        />
        <label>
          닉네임
          <input
            type="text"
            value={form.username}
            onChange={(event) => update('username', event.target.value)}
            autoComplete="username"
          />
        </label>
        <label>
          비밀번호
          <input
            type="password"
            value={form.password}
            onChange={(event) => update('password', event.target.value)}
            autoComplete="new-password"
          />
        </label>
        <label>
          비밀번호 확인
          <input
            type="password"
            value={form.confirmPassword}
            onChange={(event) => update('confirmPassword', event.target.value)}
            autoComplete="new-password"
          />
        </label>

        <button className="primary-button" type="submit" disabled={busy}>
          회원가입
        </button>
        <button
          className="ghost-button with-text"
          type="button"
          onClick={() => navigate('/login')}
        >
          로그인으로 이동
        </button>
      </form>
    </section>
  );
}
