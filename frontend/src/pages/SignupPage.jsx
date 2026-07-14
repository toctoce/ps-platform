import { useState } from 'react';
import { Code2 } from 'lucide-react';
import { request } from '../api/client.js';

export function SignupPage({ setMessage, navigate }) {
  const [busy, setBusy] = useState(false);
  const [verificationBusy, setVerificationBusy] = useState(false);
  const [verificationRequested, setVerificationRequested] = useState(false);
  const [verificationToken, setVerificationToken] = useState('');
  const [form, setForm] = useState({
    email: '',
    verificationCode: '',
    username: '',
    password: '',
    confirmPassword: '',
  });

  async function requestVerificationCode() {
    const email = form.email.trim();
    if (!email) {
      setMessage('이메일을 입력해 주세요.');
      return;
    }

    setVerificationBusy(true);
    try {
      const response = await request('/api/email-verifications', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email }),
      });
      setVerificationRequested(true);
      setVerificationToken('');
      setForm((current) => ({ ...current, verificationCode: '' }));
      setMessage(response?.message ?? '인증번호를 전송했습니다.');
    } catch (error) {
      setMessage(error.message);
    } finally {
      setVerificationBusy(false);
    }
  }

  async function confirmVerificationCode() {
    const code = form.verificationCode.trim();
    if (!/^\d{6}$/.test(code)) {
      setMessage('6자리 인증번호를 입력해 주세요.');
      return;
    }

    setVerificationBusy(true);
    try {
      const response = await request('/api/email-verifications/confirm', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: form.email.trim(), code }),
      });
      setVerificationToken(response.verificationToken);
      setMessage('이메일 인증이 완료되었습니다.');
    } catch (error) {
      setMessage(error.message);
    } finally {
      setVerificationBusy(false);
    }
  }

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
    if (field === 'email') {
      setVerificationRequested(false);
      setVerificationToken('');
      setForm((current) => ({ ...current, email: value, verificationCode: '' }));
      return;
    }
    setForm((current) => ({ ...current, [field]: value }));
  }

  return (
    <section className="auth-page">
      <div className="auth-brand">
        <span className="site-logo-mark">
          <Code2 size={18} />
        </span>
        <strong>빵구리즘</strong>
        <p>계정을 만들고 문제 풀이를 시작하세요</p>
      </div>
      <form className="panel form-panel auth-form" onSubmit={signup}>
        <div className="panel-header">
          <Code2 size={18} />
          <h2>회원가입</h2>
        </div>

        <label>
          이메일
          <span className="auth-inline-field">
            <input
              type="email"
              value={form.email}
              onChange={(event) => update('email', event.target.value)}
              autoComplete="email"
              disabled={Boolean(verificationToken)}
            />
            {verificationToken ? (
              <button
                className="auth-field-button"
                type="button"
                onClick={() => update('email', form.email)}
              >
                변경
              </button>
            ) : (
              <button
                className="auth-field-button"
                type="button"
                onClick={requestVerificationCode}
                disabled={verificationBusy}
              >
                {verificationRequested ? '재전송' : '인증번호 받기'}
              </button>
            )}
          </span>
        </label>
        {verificationRequested ? (
          <label>
            인증번호
            <span className="auth-inline-field">
              <input
                type="text"
                value={form.verificationCode}
                onChange={(event) => update('verificationCode', event.target.value.replace(/\D/g, '').slice(0, 6))}
                inputMode="numeric"
                autoComplete="one-time-code"
                maxLength={6}
                disabled={Boolean(verificationToken)}
              />
              <button
                className={`auth-field-button ${verificationToken ? 'is-verified' : ''}`}
                type="button"
                onClick={confirmVerificationCode}
                disabled={verificationBusy || Boolean(verificationToken)}
              >
                {verificationToken ? '인증 완료' : '확인'}
              </button>
            </span>
          </label>
        ) : null}
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
