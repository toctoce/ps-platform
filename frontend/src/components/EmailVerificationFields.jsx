import { useState } from 'react';
import { request } from '../api/client.js';

export function EmailVerificationFields({
  email,
  onEmailChange,
  onVerificationTokenChange,
  requestPath,
  confirmPath,
  setMessage,
}) {
  const [busy, setBusy] = useState(false);
  const [requested, setRequested] = useState(false);
  const [code, setCode] = useState('');
  const [verified, setVerified] = useState(false);

  function resetVerification(nextEmail) {
    setRequested(false);
    setCode('');
    setVerified(false);
    onEmailChange(nextEmail);
    onVerificationTokenChange('');
  }

  async function requestCode() {
    const normalizedEmail = email.trim();
    if (!normalizedEmail) {
      setMessage('이메일을 입력해 주세요.');
      return;
    }

    setBusy(true);
    try {
      const response = await request(requestPath, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: normalizedEmail }),
      });
      setRequested(true);
      setCode('');
      setVerified(false);
      onVerificationTokenChange('');
      setMessage(response?.message ?? '인증번호를 전송했습니다.');
    } catch (error) {
      setMessage(error.message);
    } finally {
      setBusy(false);
    }
  }

  async function confirmCode() {
    if (!/^\d{6}$/.test(code)) {
      setMessage('6자리 인증번호를 입력해 주세요.');
      return;
    }

    setBusy(true);
    try {
      const response = await request(confirmPath, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: email.trim(), code }),
      });
      setVerified(true);
      onVerificationTokenChange(response.verificationToken);
      setMessage('이메일 인증이 완료되었습니다.');
    } catch (error) {
      setMessage(error.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <label>
        이메일
        <span className="auth-inline-field">
          <input
            type="email"
            value={email}
            onChange={(event) => resetVerification(event.target.value)}
            autoComplete="email"
            disabled={verified}
          />
          {verified ? (
            <button
              className="auth-field-button"
              type="button"
              onClick={() => resetVerification(email)}
            >
              변경
            </button>
          ) : (
            <button
              className="auth-field-button"
              type="button"
              onClick={requestCode}
              disabled={busy}
            >
              {requested ? '재전송' : '인증번호 받기'}
            </button>
          )}
        </span>
      </label>
      {requested ? (
        <label>
          인증번호
          <span className="auth-inline-field">
            <input
              type="text"
              value={code}
              onChange={(event) => setCode(event.target.value.replace(/\D/g, '').slice(0, 6))}
              inputMode="numeric"
              autoComplete="one-time-code"
              maxLength={6}
              disabled={verified}
            />
            <button
              className={`auth-field-button ${verified ? 'is-verified' : ''}`}
              type="button"
              onClick={confirmCode}
              disabled={busy || verified}
            >
              {verified ? '인증 완료' : '확인'}
            </button>
          </span>
        </label>
      ) : null}
    </>
  );
}
