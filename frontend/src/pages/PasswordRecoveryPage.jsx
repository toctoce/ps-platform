import { useState } from 'react';
import { request } from '../api/client.js';
import { BRAND_LOGO_SRC, BRAND_NAME } from '../brand.js';
import { EmailVerificationFields } from '../components/EmailVerificationFields.jsx';

export function PasswordRecoveryPage({ setMessage, navigate }) {
  const [busy, setBusy] = useState(false);
  const [verificationToken, setVerificationToken] = useState('');
  const [form, setForm] = useState({ email: '', password: '', confirmPassword: '' });

  function update(field, value) {
    setForm((current) => ({ ...current, [field]: value }));
  }

  async function resetPassword(event) {
    event.preventDefault();
    if (!verificationToken) {
      setMessage('이메일 인증을 완료해 주세요.');
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

    setBusy(true);
    try {
      const response = await request('/api/password-recovery/reset', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          email: form.email.trim(),
          verificationToken,
          newPassword: form.password,
        }),
      });
      setMessage(response?.message ?? '비밀번호가 변경되었습니다.');
      navigate('/login');
    } catch (error) {
      setMessage(error.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="auth-page">
      <div className="auth-brand">
        <span className="site-logo-mark">
          <img src={BRAND_LOGO_SRC} alt="" />
        </span>
        <strong>{BRAND_NAME}</strong>
        <p>가입한 이메일로 비밀번호를 다시 설정하세요</p>
      </div>
      <form className="panel form-panel auth-form" onSubmit={resetPassword}>
        <div className="panel-header">
          <h2>비밀번호 찾기</h2>
        </div>

        <EmailVerificationFields
          email={form.email}
          onEmailChange={(email) => update('email', email)}
          onVerificationTokenChange={setVerificationToken}
          requestPath="/api/password-recovery/verifications"
          confirmPath="/api/password-recovery/verifications/confirm"
          setMessage={setMessage}
        />

        {verificationToken ? (
          <>
            <label>
              새 비밀번호
              <input
                type="password"
                value={form.password}
                onChange={(event) => update('password', event.target.value)}
                autoComplete="new-password"
              />
            </label>
            <label>
              새 비밀번호 확인
              <input
                type="password"
                value={form.confirmPassword}
                onChange={(event) => update('confirmPassword', event.target.value)}
                autoComplete="new-password"
              />
            </label>
            <button className="primary-button" type="submit" disabled={busy}>
              비밀번호 변경
            </button>
          </>
        ) : null}
      </form>
      <button className="auth-back-link" type="button" onClick={() => navigate('/')}>
        ← 홈으로 돌아가기
      </button>
    </section>
  );
}
