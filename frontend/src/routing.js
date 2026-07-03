export function parseRoute() {
  const path = (window.location.pathname || '/').replace(/\/+$/, '') || '/';
  if (path === '/login') {
    return { name: 'login' };
  }
  if (path === '/signup') {
    return { name: 'signup' };
  }
  if (path === '/categories') {
    return { name: 'category-list' };
  }
  const categoryMatch = path.match(/^\/categories\/(.+)$/);
  if (categoryMatch) {
    return {
      name: 'category-detail',
      category: decodeURIComponent(categoryMatch[1]),
    };
  }
  if (path === '/random') {
    return { name: 'random' };
  }
  if (path === '/mypage' || path === '/my') {
    return { name: 'my-page' };
  }
  if (path === '/admin') {
    return { name: 'admin' };
  }
  const detailMatch = path.match(/^\/problems\/(\d+)$/);
  if (detailMatch) {
    return { name: 'problem-detail', problemId: detailMatch[1] };
  }
  if (path === '/problems') {
    return { name: 'problem-list' };
  }
  if (path === '/home' || path === '/') {
    return { name: 'home' };
  }
  return { name: 'home' };
}
