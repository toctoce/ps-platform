import { useState } from 'react';
import { Code2 } from 'lucide-react';

export function MyPage({ auth, isAuthenticated, setMessage, navigate, onDeleteAccount }) {
  const [deleteBusy, setDeleteBusy] = useState(false);

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
    return (
      <section className="home-page">
        <section className="panel">
          <div className="panel-header">
            <Code2 size={18} />
            <h2>마이페이지</h2>
          </div>
          <p className="empty">로그인 후 이용할 수 있습니다.</p>
          <button className="ghost-button with-text" type="button" onClick={() => navigate('/login')}>
            로그인
          </button>
        </section>
      </section>
    );
  }

  return (
    <section className="home-page">
      <section className="panel">
        <div className="panel-header split">
          <span className="heading-inline">
            <Code2 size={18} />
            <h2>계정 정보</h2>
          </span>
          <button
            className="ghost-button with-text"
            type="button"
            onClick={() => setMessage('계정 정보 수정 기능은 추가 개발 예정입니다.')}
          >
            수정
          </button>
        </div>
        <div className="profile-list">
          <div className="profile-row">
            <span>닉네임</span>
            <strong>{auth.user?.username ?? '확인 불가'}</strong>
          </div>
          <div className="profile-row">
            <span>가입일</span>
            <strong>{auth.user?.createdAt ?? '확인 불가'}</strong>
          </div>
        </div>
        <div className="account-danger-actions">
          <button
            className="danger-link-button"
            type="button"
            onClick={deleteAccount}
            disabled={deleteBusy}
          >
            회원탈퇴
          </button>
        </div>
      </section>
    </section>
  );
}
