import { LayoutGrid, List, Play } from 'lucide-react';

export function HomePage({ navigate }) {
  return (
    <section className="landing-page">
      <section className="landing-hero">
        <div className="hero-copy">
          <p className="hero-line">오늘도 한 문제만 풀고 가세요.</p>
          <div className="hero-actions">
            <button className="primary-button" type="button" onClick={() => navigate('/random')}>
              <Play size={17} />
              모의 코딩테스트
            </button>
            <button className="ghost-button" type="button" onClick={() => navigate('/problems')}>
              <List size={17} />
              문제 목록
            </button>
            <button className="ghost-button" type="button" onClick={() => navigate('/categories')}>
              <LayoutGrid size={17} />
              카테고리 목록
            </button>
          </div>
        </div>
      </section>
    </section>
  );
}
