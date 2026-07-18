import { useEffect, useState } from 'react';
import { SolvedProblemList } from '../components/SolvedProblemList.jsx';
import { SolvedProblemSummary } from '../components/SolvedProblemSummary.jsx';

export function MyPage({ auth, isAuthenticated, navigate }) {
  const [solvedCategories, setSolvedCategories] = useState([]);

  useEffect(() => {
    if (!isAuthenticated) {
      navigate('/login?returnTo=%2Fmypage', { replace: true });
    }
  }, [isAuthenticated]);

  if (!isAuthenticated) {
    return null;
  }

  return (
    <section className="open-page my-page">
      <header className="open-page-header">
        <h1>마이페이지</h1>
      </header>
      <SolvedProblemSummary
        accessToken={auth.accessToken}
        onCategoriesLoaded={setSolvedCategories}
      />
      <SolvedProblemList
        accessToken={auth.accessToken}
        categories={solvedCategories}
        navigate={navigate}
      />
    </section>
  );
}
