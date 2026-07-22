import { Boxes, Calculator, GitBranch, Network, Repeat2, Search } from 'lucide-react';
import { HOME_MASCOT_SRC } from '../brand.js';

const ROADMAP_MILESTONES = [
  { title: '자료구조', category: '자료구조', icon: Boxes, current: true },
  { title: '수학', category: '수학', icon: Calculator },
  { title: 'DP', category: '동적 계획법', icon: GitBranch },
  { title: '브루트포스', category: '완전탐색', icon: Search },
  { title: '재귀', category: '재귀', icon: Repeat2 },
  { title: '그래프 탐색', category: '그래프탐색', icon: Network },
];

function formatExperience(value) {
  return Number(value ?? 0).toLocaleString('ko-KR');
}

export function HomePage({ auth, navigate }) {
  const user = auth?.user;
  const level = user?.level ?? 1;
  const currentExperience = user?.currentLevelExperience ?? 0;
  const requiredExperience = user?.requiredExperience ?? 50;
  const progressPercent = user?.progressPercent ?? 0;
  const isMaxLevel = requiredExperience === 0;

  return (
    <section className="landing-page home-landing-page">
      <section className="home-progress-section">
        <div className="home-progress-inner">
          <aside className="home-profile-card">
            <img className="home-profile-symbol" src={HOME_MASCOT_SRC} alt="개발자 캐릭터" />
            <strong className="home-profile-name">{user?.username ?? 'GUEST'}</strong>
            <div className="home-profile-level">
              <span>Lv.</span>
              <strong>{level}</strong>
            </div>
            <div className="home-level-progress">
              <div className="home-level-progress-meta">
                <span>진행도</span>
                <strong>{isMaxLevel ? 'MAX' : `${Math.min(100, Math.max(0, progressPercent))}%`}</strong>
              </div>
              <div
                className="home-level-progress-track"
                role="progressbar"
                aria-label="현재 레벨 경험치"
                aria-valuemin="0"
                aria-valuemax={isMaxLevel ? 100 : requiredExperience}
                aria-valuenow={isMaxLevel ? 100 : currentExperience}
              >
                <span style={{ width: `${isMaxLevel ? 100 : Math.min(100, Math.max(0, progressPercent))}%` }} />
              </div>
              <div className="home-profile-experience">
                <span>XP</span>
                <strong>
                  {isMaxLevel
                    ? 'MAX LEVEL'
                    : `${formatExperience(currentExperience)} / ${formatExperience(requiredExperience)} XP`}
                </strong>
              </div>
            </div>
          </aside>

          <section className="home-roadmap-panel">
            <div className="home-roadmap-map">
              {ROADMAP_MILESTONES.map((milestone, index) => {
                const Icon = milestone.icon;
                return (
                  <div
                    className={`home-roadmap-milestone${milestone.current ? ' is-current' : ''}`}
                    key={milestone.title}
                  >
                    <button
                      className="home-roadmap-marker"
                      type="button"
                      onClick={() => navigate(`/categories/${encodeURIComponent(milestone.category)}`)}
                      aria-label={`${milestone.title} 문제 보기`}
                    >
                      <Icon size={19} aria-hidden="true" />
                      <span>{index + 1}</span>
                    </button>
                    <span className="home-roadmap-label">
                      <strong>{milestone.title}</strong>
                    </span>
                  </div>
                );
              })}
            </div>
          </section>
        </div>
      </section>
    </section>
  );
}
