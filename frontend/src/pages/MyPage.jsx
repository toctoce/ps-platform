import { useEffect, useState } from 'react';
import { request } from '../api/client.js';

function formatJoinedAt(value) {
  if (!value) {
    return '확인 불가';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
  }).format(date);
}

export function MyPage({
  auth,
  isAuthenticated,
  setMessage,
  navigate,
  onAuthUserChange,
  onLogout,
  onPasswordChanged,
  onDeleteAccount,
}) {
  const [editingUsername, setEditingUsername] = useState(false);
  const [username, setUsername] = useState(auth?.user?.username ?? '');
  const [usernameBusy, setUsernameBusy] = useState(false);
  const [passwordOpen, setPasswordOpen] = useState(false);
  const [passwords, setPasswords] = useState({ currentPassword: '', newPassword: '', confirmPassword: '' });
  const [passwordBusy, setPasswordBusy] = useState(false);
  const [deleteBusy, setDeleteBusy] = useState(false);

  useEffect(() => {
    if (!isAuthenticated) {
      navigate('/login?returnTo=%2Fmypage', { replace: true });
    }
  }, [isAuthenticated]);

  useEffect(() => {
    setUsername(auth?.user?.username ?? '');
  }, [auth?.user?.username]);

  async function updateUsername(event) {
    event.preventDefault();
    if (!username.trim()) {
      setMessage('닉네임을 입력해 주세요.');
      return;
    }
    setUsernameBusy(true);
    try {
      const user = await request('/api/auth/me/username', {
        method: 'PATCH',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${auth.accessToken}`,
        },
        body: JSON.stringify({ username: username.trim() }),
      });
      onAuthUserChange(user);
      setEditingUsername(false);
      setMessage('닉네임을 변경했습니다.');
    } catch (error) {
      setMessage(error.message);
    } finally {
      setUsernameBusy(false);
    }
  }

  async function changePassword(event) {
    event.preventDefault();
    if (passwords.newPassword.length < 8) {
      setMessage('새 비밀번호는 8자 이상이어야 합니다.');
      return;
    }
    if (passwords.newPassword !== passwords.confirmPassword) {
      setMessage('새 비밀번호가 서로 일치하지 않습니다.');
      return;
    }
    setPasswordBusy(true);
    try {
      await request('/api/auth/me/password', {
        method: 'PATCH',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${auth.accessToken}`,
        },
        body: JSON.stringify({
          currentPassword: passwords.currentPassword,
          newPassword: passwords.newPassword,
        }),
      });
      onPasswordChanged();
    } catch (error) {
      setMessage(error.message);
      setPasswordBusy(false);
    }
  }

  async function deleteAccount() {
    if (!window.confirm('회원 탈퇴 후에는 계정을 다시 사용할 수 없습니다. 계속할까요?')) {
      return;
    }

    setDeleteBusy(true);
    try {
      await onDeleteAccount();
    } catch (error) {
      setMessage(error.message);
      setDeleteBusy(false);
    }
  }

  if (!isAuthenticated) {
    return null;
  }

  return (
    <section className="open-page my-page">
      <header className="open-page-header">
        <h1>마이페이지</h1>
      </header>

      <section className="my-page-section" aria-labelledby="account-heading">
        <h2 id="account-heading">계정 정보</h2>
        <div className="profile-list">
          <div className="profile-row">
            <span>닉네임</span>
            {editingUsername ? (
              <form className="profile-inline-form" onSubmit={updateUsername}>
                <input
                  value={username}
                  onChange={(event) => setUsername(event.target.value)}
                  autoComplete="nickname"
                  aria-label="새 닉네임"
                />
                <button className="primary-button compact-button" type="submit" disabled={usernameBusy}>저장</button>
                <button
                  className="text-button"
                  type="button"
                  onClick={() => {
                    setUsername(auth.user?.username ?? '');
                    setEditingUsername(false);
                  }}
                >
                  취소
                </button>
              </form>
            ) : (
              <div className="profile-value-actions">
                <strong>{auth.user?.username ?? '확인 불가'}</strong>
                <button className="text-button" type="button" onClick={() => setEditingUsername(true)}>수정</button>
              </div>
            )}
          </div>
          <div className="profile-row">
            <span>이메일</span>
            <strong>{auth.user?.email ?? '확인 불가'}</strong>
          </div>
          <div className="profile-row">
            <span>가입일</span>
            <strong>{formatJoinedAt(auth.user?.createdAt)}</strong>
          </div>
          <div className="profile-row">
            <span>비밀번호</span>
            <div className="profile-value-actions">
              <strong>********</strong>
              <button className="text-button" type="button" onClick={() => setPasswordOpen((current) => !current)}>
                {passwordOpen ? '닫기' : '변경'}
              </button>
            </div>
          </div>
        </div>

        {passwordOpen ? (
          <form className="password-change-form" onSubmit={changePassword}>
            <label>
              <span>현재 비밀번호</span>
              <input
                type="password"
                value={passwords.currentPassword}
                onChange={(event) => setPasswords((current) => ({ ...current, currentPassword: event.target.value }))}
                autoComplete="current-password"
              />
            </label>
            <label>
              <span>새 비밀번호</span>
              <input
                type="password"
                value={passwords.newPassword}
                onChange={(event) => setPasswords((current) => ({ ...current, newPassword: event.target.value }))}
                autoComplete="new-password"
              />
            </label>
            <label>
              <span>새 비밀번호 확인</span>
              <input
                type="password"
                value={passwords.confirmPassword}
                onChange={(event) => setPasswords((current) => ({ ...current, confirmPassword: event.target.value }))}
                autoComplete="new-password"
              />
            </label>
            <button className="primary-button compact-button" type="submit" disabled={passwordBusy}>
              비밀번호 변경
            </button>
          </form>
        ) : null}

        <div className="account-actions">
          <button className="ghost-button with-text" type="button" onClick={onLogout}>로그아웃</button>
          <button
            className="danger-link-button"
            type="button"
            onClick={deleteAccount}
            disabled={deleteBusy}
          >
            회원 탈퇴
          </button>
        </div>
      </section>
    </section>
  );
}
