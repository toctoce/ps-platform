import { useCallback, useEffect, useState } from 'react';
import {
  Activity,
  AlertTriangle,
  Archive,
  BookOpen,
  FileClock,
  FolderCog,
  Gauge,
  History,
  Plus,
  RotateCcw,
  Save,
  ShieldCheck,
  Trash2,
  Users,
} from 'lucide-react';
import { request } from '../api/client.js';
import { DIFFICULTY_OPTIONS, difficultyLabel } from '../problem/problemUtils.js';
import {
  PROBLEM_REPORT_STATUSES,
  problemReportStatusLabel,
  problemReportTypeLabel,
} from '../problem/reportUtils.js';

export const ADMIN_TABS = [
  { id: 'overview', label: '개요', icon: Gauge },
  { id: 'problems', label: '문제', icon: BookOpen },
  { id: 'users', label: '회원', icon: Users },
  { id: 'operations', label: '제출 · 채점', icon: Activity },
  { id: 'reports', label: '신고', icon: AlertTriangle },
  { id: 'categories', label: '카테고리', icon: FolderCog },
  { id: 'audit', label: '감사 로그', icon: History },
];

const ADMIN_JUDGE_POLL_INTERVAL_MS = 2000;

const EMPTY_PROBLEM = {
  title: '',
  description: '',
  solution: '',
  inputDescription: '',
  inputConstraints: '',
  outputDescription: '',
  difficulty: 'NORMAL',
  categories: '',
  timeLimitMs: 1000,
  memoryLimitMb: 128,
};

function authHeaders(auth, json = false) {
  return {
    ...(json ? { 'Content-Type': 'application/json' } : {}),
    ...(auth?.accessToken ? { Authorization: `Bearer ${auth.accessToken}` } : {}),
  };
}

function formatDate(value) {
  return value ? new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) : '-';
}

function mapProblemToForm(problem) {
  return {
    title: problem.title ?? '',
    description: problem.description ?? '',
    solution: problem.solution ?? '',
    inputDescription: problem.inputDescription ?? '',
    inputConstraints: problem.inputConstraints ?? '',
    outputDescription: problem.outputDescription ?? '',
    difficulty: problem.difficulty ?? 'NORMAL',
    categories: (problem.categories ?? []).join(', '),
    timeLimitMs: problem.timeLimitMs ?? 1000,
    memoryLimitMb: problem.memoryLimitMb ?? 128,
  };
}

function problemPayload(form) {
  return {
    ...form,
    title: form.title.trim(),
    timeLimitMs: Number(form.timeLimitMs),
    memoryLimitMb: Number(form.memoryLimitMb),
    categories: [...new Set(form.categories.split(',').map((value) => value.trim()).filter(Boolean))],
  };
}

export function AdminPage({ auth, setMessage, navigate, activeTab }) {
  const isAuthenticated = Boolean(auth?.accessToken);
  const isAdmin = auth?.user?.role === 'ADMIN';

  if (!isAuthenticated || !isAdmin) {
    return (
      <section className="home-page admin-access-page">
        <ShieldCheck size={28} />
        <h1>관리자 페이지</h1>
        <p>{isAuthenticated ? '관리자 권한이 필요합니다.' : '로그인 후 이용할 수 있습니다.'}</p>
        {!isAuthenticated && (
          <button className="primary-button" type="button" onClick={() => navigate('/login?returnTo=%2Fadmin')}>
            로그인
          </button>
        )}
      </section>
    );
  }

  return (
    <section className="home-page admin-page">
      <div className="admin-workspace">
        {activeTab === 'overview' && <OverviewPanel auth={auth} setMessage={setMessage} />}
        {activeTab === 'problems' && <ProblemsPanel auth={auth} setMessage={setMessage} />}
        {activeTab === 'users' && <UsersPanel auth={auth} setMessage={setMessage} />}
        {activeTab === 'operations' && <OperationsPanel auth={auth} setMessage={setMessage} />}
        {activeTab === 'reports' && <ReportsPanel auth={auth} setMessage={setMessage} />}
        {activeTab === 'categories' && <CategoriesPanel auth={auth} setMessage={setMessage} />}
        {activeTab === 'audit' && <AuditPanel auth={auth} setMessage={setMessage} />}
      </div>
    </section>
  );
}

function OverviewPanel({ auth, setMessage }) {
  const [dashboard, setDashboard] = useState(null);

  useEffect(() => {
    request('/api/admin/dashboard', { headers: authHeaders(auth) })
      .then(setDashboard)
      .catch((error) => setMessage(error.message));
  }, [auth, setMessage]);

  const metrics = [
    ['운영 문제', dashboard?.activeProblems],
    ['활성 회원', dashboard?.activeUsers],
    ['전체 제출', dashboard?.submissions],
    ['미처리 신고', dashboard?.pendingReports],
    ['대기 작업', dashboard?.queuedJudgeJobs],
    ['실행 작업', dashboard?.runningJudgeJobs],
  ];

  return (
    <section>
      <AdminSectionTitle title="운영 현황" description="현재 서비스의 주요 수치입니다." />
      <div className="admin-metric-grid">
        {metrics.map(([label, value]) => (
          <article className="admin-metric" key={label}>
            <span>{label}</span>
            <strong>{value ?? '-'}</strong>
          </article>
        ))}
      </div>
    </section>
  );
}

function ProblemsPanel({ auth, setMessage }) {
  const [problems, setProblems] = useState([]);
  const [selectedId, setSelectedId] = useState(null);
  const [detail, setDetail] = useState(null);
  const [form, setForm] = useState(EMPTY_PROBLEM);
  const [creating, setCreating] = useState(false);
  const [busy, setBusy] = useState(false);

  const loadProblems = useCallback(async () => {
    try {
      const data = await request('/api/admin/problems?includeArchived=true', { headers: authHeaders(auth) });
      setProblems(data ?? []);
    } catch (error) {
      setMessage(error.message);
    }
  }, [auth, setMessage]);

  const loadDetail = useCallback(async (problemId) => {
    try {
      const data = await request(`/api/admin/problems/${problemId}`, { headers: authHeaders(auth) });
      setDetail(data);
      setForm(mapProblemToForm(data));
      setCreating(false);
    } catch (error) {
      setMessage(error.message);
    }
  }, [auth, setMessage]);

  useEffect(() => {
    loadProblems();
  }, [loadProblems]);

  useEffect(() => {
    if (selectedId) {
      loadDetail(selectedId);
    }
  }, [loadDetail, selectedId]);

  function startCreate() {
    setSelectedId(null);
    setDetail(null);
    setForm(EMPTY_PROBLEM);
    setCreating(true);
  }

  async function saveProblem(event) {
    event.preventDefault();
    setBusy(true);
    try {
      if (creating) {
        const created = await request('/api/admin/problems', {
          method: 'POST',
          headers: authHeaders(auth, true),
          body: JSON.stringify({ ...problemPayload(form), testCases: [], examples: [] }),
        });
        setMessage('문제를 등록했습니다.');
        await loadProblems();
        setSelectedId(created.id);
      } else {
        const updated = await request(`/api/admin/problems/${detail.id}`, {
          method: 'PUT',
          headers: authHeaders(auth, true),
          body: JSON.stringify(problemPayload(form)),
        });
        setDetail(updated);
        setForm(mapProblemToForm(updated));
        setMessage('문제를 수정했습니다.');
        await loadProblems();
      }
    } catch (error) {
      setMessage(error.message);
    } finally {
      setBusy(false);
    }
  }

  async function changeArchiveState() {
    const archived = Boolean(detail.archivedAt);
    if (!archived && !window.confirm('문제를 보관하면 일반 문제 목록에서 숨겨집니다. 계속할까요?')) {
      return;
    }
    setBusy(true);
    try {
      await request(`/api/admin/problems/${detail.id}${archived ? '/restore' : ''}`, {
        method: archived ? 'POST' : 'DELETE',
        headers: authHeaders(auth),
      });
      setMessage(archived ? '문제를 복원했습니다.' : '문제를 보관했습니다.');
      await Promise.all([loadProblems(), loadDetail(detail.id)]);
    } catch (error) {
      setMessage(error.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <section>
      <AdminSectionTitle title="문제 관리" description="문제 본문, 공개 예제, 채점용 테스트케이스를 관리합니다." />
      <div className="admin-split-layout">
        <aside className="admin-list-pane">
          <button className="primary-button admin-full-button" type="button" onClick={startCreate}>
            <Plus size={16} /> 새 문제
          </button>
          <div className="admin-select-list">
            {problems.map((problem) => (
              <button
                className={selectedId === problem.id ? 'is-active' : ''}
                key={problem.id}
                type="button"
                onClick={() => setSelectedId(problem.id)}
              >
                <strong>{problem.id}. {problem.title}</strong>
                <span>
                  {difficultyLabel(problem.difficulty)} · 예제 {problem.exampleCount} · 채점 {problem.testCaseCount}
                  {problem.archivedAt ? ' · 보관됨' : ''}
                </span>
              </button>
            ))}
          </div>
        </aside>

        <main className="admin-detail-pane">
          {!creating && !detail ? (
            <p className="admin-empty">수정할 문제를 선택하거나 새 문제를 등록하세요.</p>
          ) : (
            <>
              <form className="admin-form" onSubmit={saveProblem}>
                <div className="admin-editor-heading">
                  <h3>{creating ? '새 문제 등록' : `${detail.id}. ${detail.title}`}</h3>
                  <div>
                    {!creating && (
                      <button className="ghost-button with-text" type="button" onClick={changeArchiveState} disabled={busy}>
                        {detail.archivedAt ? <RotateCcw size={15} /> : <Archive size={15} />}
                        {detail.archivedAt ? '복원' : '보관'}
                      </button>
                    )}
                    <button className="primary-button" type="submit" disabled={busy}>
                      <Save size={15} /> 저장
                    </button>
                  </div>
                </div>
                <ProblemFields form={form} setForm={setForm} />
              </form>

              {!creating && detail && (
                <div className="admin-case-grid">
                  <CaseEditor
                    auth={auth}
                    kind="examples"
                    title="공개 예제"
                    problemId={detail.id}
                    items={detail.examples}
                    setMessage={setMessage}
                    reload={() => loadDetail(detail.id)}
                  />
                  <CaseEditor
                    auth={auth}
                    kind="test-cases"
                    title="채점용 테스트케이스"
                    problemId={detail.id}
                    items={detail.testCases}
                    setMessage={setMessage}
                    reload={() => loadDetail(detail.id)}
                  />
                </div>
              )}
            </>
          )}
        </main>
      </div>
    </section>
  );
}

function ProblemFields({ form, setForm }) {
  const update = (field, value) => setForm((current) => ({ ...current, [field]: value }));
  return (
    <>
      <div className="admin-form-grid">
        <label>제목<input value={form.title} onChange={(event) => update('title', event.target.value)} required /></label>
        <label>난이도<select value={form.difficulty} onChange={(event) => update('difficulty', event.target.value)}>
          {DIFFICULTY_OPTIONS.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
        </select></label>
        <label>시간 제한(ms)<input type="number" min="1" value={form.timeLimitMs} onChange={(event) => update('timeLimitMs', event.target.value)} /></label>
        <label>메모리 제한(MB)<input type="number" min="1" value={form.memoryLimitMb} onChange={(event) => update('memoryLimitMb', event.target.value)} /></label>
        <label className="admin-wide-field">카테고리(쉼표 구분)<input value={form.categories} onChange={(event) => update('categories', event.target.value)} /></label>
      </div>
      <label>문제 설명<textarea value={form.description} onChange={(event) => update('description', event.target.value)} required /></label>
      <label>입력 설명<textarea value={form.inputDescription} onChange={(event) => update('inputDescription', event.target.value)} required /></label>
      <label>입력 제한<textarea value={form.inputConstraints} onChange={(event) => update('inputConstraints', event.target.value)} /></label>
      <label>출력 설명<textarea value={form.outputDescription} onChange={(event) => update('outputDescription', event.target.value)} required /></label>
      <label>해설(Markdown)<textarea className="admin-tall-textarea" value={form.solution} onChange={(event) => update('solution', event.target.value)} /></label>
    </>
  );
}

function CaseEditor({ auth, kind, title, problemId, items, setMessage, reload }) {
  const isExample = kind === 'examples';
  const empty = { input: '', expectedOutput: '', ...(isExample ? { description: '' } : {}) };
  const [draft, setDraft] = useState(empty);

  async function save(item) {
    const isNew = !item.id;
    try {
      await request(`/api/admin/problems/${problemId}/${kind}${isNew ? '' : `/${item.id}`}`, {
        method: isNew ? 'POST' : 'PUT',
        headers: authHeaders(auth, true),
        body: JSON.stringify(item),
      });
      setMessage(`${title}을 저장했습니다.`);
      if (isNew) setDraft(empty);
      await reload();
    } catch (error) {
      setMessage(error.message);
    }
  }

  async function remove(item) {
    if (!window.confirm(`${title} 항목을 삭제할까요?`)) return;
    try {
      await request(`/api/admin/problems/${problemId}/${kind}/${item.id}`, {
        method: 'DELETE',
        headers: authHeaders(auth),
      });
      setMessage(`${title} 항목을 삭제했습니다.`);
      await reload();
    } catch (error) {
      setMessage(error.message);
    }
  }

  return (
    <section className="admin-case-section">
      <h3>{title} <span>{items.length}</span></h3>
      {items.map((item, index) => (
        <CaseRow key={item.id} item={item} index={index} isExample={isExample} onSave={save} onDelete={remove} />
      ))}
      <div className="admin-case-row is-new">
        <strong>추가</strong>
        <textarea value={draft.input} onChange={(event) => setDraft({ ...draft, input: event.target.value })} placeholder="입력" />
        <textarea value={draft.expectedOutput} onChange={(event) => setDraft({ ...draft, expectedOutput: event.target.value })} placeholder="예상 출력" />
        {isExample && <textarea value={draft.description} onChange={(event) => setDraft({ ...draft, description: event.target.value })} placeholder="설명(선택)" />}
        <button className="ghost-button with-text" type="button" onClick={() => save(draft)}><Plus size={14} /> 추가</button>
      </div>
    </section>
  );
}

function CaseRow({ item, index, isExample, onSave, onDelete }) {
  const [draft, setDraft] = useState(item);
  useEffect(() => setDraft(item), [item]);
  return (
    <div className="admin-case-row">
      <strong>#{index + 1}</strong>
      <textarea value={draft.input} onChange={(event) => setDraft({ ...draft, input: event.target.value })} aria-label={`케이스 ${index + 1} 입력`} />
      <textarea value={draft.expectedOutput} onChange={(event) => setDraft({ ...draft, expectedOutput: event.target.value })} aria-label={`케이스 ${index + 1} 출력`} />
      {isExample && <textarea value={draft.description ?? ''} onChange={(event) => setDraft({ ...draft, description: event.target.value })} aria-label={`예제 ${index + 1} 설명`} />}
      <div className="admin-row-actions">
        <button className="ghost-button" type="button" onClick={() => onSave(draft)} title="저장"><Save size={14} /></button>
        <button className="ghost-button admin-danger-button" type="button" onClick={() => onDelete(item)} title="삭제"><Trash2 size={14} /></button>
      </div>
    </div>
  );
}

function UsersPanel({ auth, setMessage }) {
  const [users, setUsers] = useState([]);
  const load = useCallback(() => request('/api/admin/users', { headers: authHeaders(auth) })
    .then(setUsers).catch((error) => setMessage(error.message)), [auth, setMessage]);
  useEffect(() => { load(); }, [load]);

  async function saveUser(user) {
    try {
      await request(`/api/admin/users/${user.id}`, {
        method: 'PATCH', headers: authHeaders(auth, true),
        body: JSON.stringify({ role: user.role, suspended: Boolean(user.suspendedAt) }),
      });
      setMessage('회원 상태를 변경했습니다.');
      await load();
    } catch (error) { setMessage(error.message); }
  }

  const update = (id, field, value) => setUsers((current) => current.map((user) => (
    user.id === id ? { ...user, [field]: value } : user
  )));

  return (
    <section>
      <AdminSectionTitle title="회원 관리" description="회원 정보와 풀이 활동을 조회하고 권한 및 이용 상태를 관리합니다." />
      <div className="admin-table-wrap">
        <table className="admin-table">
          <thead><tr><th>회원</th><th>상태</th><th>권한</th><th>푼 문제</th><th>제출 / 정답</th><th>가입일</th><th /></tr></thead>
          <tbody>{users.map((user) => (
            <tr key={user.id}>
              <td><strong>{user.username}</strong><small>{user.email}</small></td>
              <td>{user.deletedAt ? '탈퇴' : user.suspendedAt ? '정지' : '활성'}</td>
              <td><select value={user.role} disabled={Boolean(user.deletedAt)} onChange={(event) => update(user.id, 'role', event.target.value)}><option value="USER">USER</option><option value="ADMIN">ADMIN</option></select></td>
              <td>{user.solvedProblemCount}</td><td>{user.submissionCount} / {user.acceptedSubmissionCount}</td><td>{formatDate(user.createdAt)}</td>
              <td><div className="admin-row-actions">
                {!user.deletedAt && <button className="ghost-button with-text" type="button" onClick={() => update(user.id, 'suspendedAt', user.suspendedAt ? null : new Date().toISOString())}>{user.suspendedAt ? '정지 해제' : '정지'}</button>}
                {!user.deletedAt && <button className="ghost-button" type="button" onClick={() => saveUser(user)} title="저장"><Save size={14} /></button>}
              </div></td>
            </tr>
          ))}</tbody>
        </table>
      </div>
    </section>
  );
}

function OperationsPanel({ auth, setMessage }) {
  const [submissions, setSubmissions] = useState([]);
  const [jobs, setJobs] = useState([]);
  const hasActiveJobs = jobs.some((job) => job.status === 'QUEUED' || job.status === 'RUNNING');
  const load = useCallback(async ({ silent = false } = {}) => {
    try {
      const [nextSubmissions, nextJobs] = await Promise.all([
        request('/api/admin/submissions', { headers: authHeaders(auth) }),
        request('/api/admin/judge-jobs', { headers: authHeaders(auth) }),
      ]);
      setSubmissions(nextSubmissions);
      setJobs(nextJobs);
    } catch (error) {
      if (!silent) {
        setMessage(error.message);
      }
    }
  }, [auth, setMessage]);
  useEffect(() => { load(); }, [load]);
  useEffect(() => {
    if (!hasActiveJobs) {
      return undefined;
    }

    const pollingTimer = window.setInterval(() => {
      load({ silent: true });
    }, ADMIN_JUDGE_POLL_INTERVAL_MS);

    return () => window.clearInterval(pollingTimer);
  }, [hasActiveJobs, load]);

  async function requeue(jobId) {
    try {
      await request(`/api/admin/judge-jobs/${jobId}/requeue`, { method: 'POST', headers: authHeaders(auth) });
      setMessage('채점 작업을 다시 대기열에 넣었습니다.');
      await load();
    } catch (error) { setMessage(error.message); }
  }

  return (
    <section className="admin-stack">
      <div><AdminSectionTitle title="채점 작업" description="최근 작업 상태를 확인하고 종료된 작업을 다시 대기시킬 수 있습니다." />
        <div className="admin-table-wrap"><table className="admin-table"><thead><tr><th>ID</th><th>종류</th><th>문제</th><th>제출</th><th>상태</th><th>결과</th><th>생성</th><th /></tr></thead>
          <tbody>{jobs.map((job) => <tr key={job.id}><td>#{job.id}</td><td>{job.jobKind}</td><td>{job.problemId ?? '-'}</td><td>{job.submissionId ?? '-'}</td><td>{job.status}</td><td>{job.verdict ?? '-'}</td><td>{formatDate(job.createdAt)}</td><td>{job.status !== 'RUNNING' && <button className="ghost-button" type="button" title="다시 채점" onClick={() => requeue(job.id)}><RotateCcw size={14} /></button>}</td></tr>)}</tbody>
        </table></div></div>
      <div><AdminSectionTitle title="최근 제출" description="최근 500건의 제출과 측정 결과입니다." />
        <div className="admin-table-wrap"><table className="admin-table"><thead><tr><th>ID</th><th>회원</th><th>문제</th><th>언어</th><th>결과</th><th>시간</th><th>메모리</th><th>제출일</th></tr></thead>
          <tbody>{submissions.map((submission) => <tr key={submission.id}><td>#{submission.id}</td><td>{submission.username ?? '비회원'}</td><td>{submission.problemId}. {submission.problemTitle}</td><td>{submission.language}</td><td>{submission.verdict ?? submission.status}</td><td>{submission.executionTimeMs == null ? '-' : `${submission.executionTimeMs}ms`}</td><td>{submission.memoryMb == null ? '-' : `${submission.memoryMb}MB`}</td><td>{formatDate(submission.createdAt)}</td></tr>)}</tbody>
        </table></div></div>
    </section>
  );
}

function ReportsPanel({ auth, setMessage }) {
  const [reports, setReports] = useState([]);
  const [reportView, setReportView] = useState('pending');
  const load = useCallback(() => request('/api/admin/problem-reports', { headers: authHeaders(auth) })
    .then((data) => setReports((data ?? []).map((report) => ({
      ...report,
      persistedStatus: report.status,
    }))))
    .catch((error) => setMessage(error.message)), [auth, setMessage]);
  useEffect(() => { load(); }, [load]);
  const update = (id, field, value) => setReports((current) => current.map((report) => report.id === id ? { ...report, [field]: value } : report));
  const pendingReports = reports.filter((report) => ['PENDING', 'REVIEWING'].includes(report.persistedStatus));
  const processedReports = reports.filter((report) => ['RESOLVED', 'REJECTED'].includes(report.persistedStatus));
  const visibleReports = reportView === 'pending' ? pendingReports : processedReports;

  async function save(report) {
    try {
      await request(`/api/admin/problem-reports/${report.id}`, {
        method: 'PATCH', headers: authHeaders(auth, true),
        body: JSON.stringify({ status: report.status, adminNote: report.adminNote ?? '' }),
      });
      setMessage('신고 처리 상태를 저장했습니다.');
      await load();
    } catch (error) { setMessage(error.message); }
  }

  return (
    <section>
      <AdminSectionTitle title="문제 신고" description="접수된 신고를 검토하고 처리 결과를 기록합니다." />
      <div className="report-view-tabs" role="tablist" aria-label="신고 처리 상태">
        <button
          className={reportView === 'pending' ? 'is-active' : ''}
          type="button"
          role="tab"
          aria-selected={reportView === 'pending'}
          onClick={() => setReportView('pending')}
        >
          미처리 <span>{pendingReports.length}</span>
        </button>
        <button
          className={reportView === 'processed' ? 'is-active' : ''}
          type="button"
          role="tab"
          aria-selected={reportView === 'processed'}
          onClick={() => setReportView('processed')}
        >
          처리 <span>{processedReports.length}</span>
        </button>
      </div>
      <div className="report-list">
        {visibleReports.length === 0 ? (
          <p className="admin-empty">
            {reportView === 'pending' ? '미처리 신고가 없습니다.' : '처리된 신고가 없습니다.'}
          </p>
        ) : visibleReports.map((report) => (
          <article className="report-item" key={report.id}>
            <div className="report-item-main">
              <strong>{report.problemId}. {report.problemTitle}</strong>
              <span>{problemReportTypeLabel(report.type)} · {report.reporterUsername} · {formatDate(report.createdAt)}</span>
              <p>{report.message}</p>
            </div>
            <div className="report-review-controls">
              <label>
                상태
                <select value={report.status} onChange={(event) => update(report.id, 'status', event.target.value)}>
                  {PROBLEM_REPORT_STATUSES.map((status) => (
                    <option key={status.value} value={status.value}>{status.label}</option>
                  ))}
                </select>
              </label>
              <label>
                관리자 메모
                <textarea value={report.adminNote ?? ''} onChange={(event) => update(report.id, 'adminNote', event.target.value)} />
              </label>
              <button className="primary-button" type="button" onClick={() => save(report)}>
                <Save size={14} /> {problemReportStatusLabel(report.status)} 저장
              </button>
            </div>
          </article>
        ))}
      </div>
    </section>
  );
}

function CategoriesPanel({ auth, setMessage }) {
  const [categories, setCategories] = useState([]);
  const [newCategory, setNewCategory] = useState({ name: '', displayOrder: 0, active: true });
  const load = useCallback(() => request('/api/admin/categories', { headers: authHeaders(auth) }).then(setCategories).catch((error) => setMessage(error.message)), [auth, setMessage]);
  useEffect(() => { load(); }, [load]);
  const update = (id, field, value) => setCategories((current) => current.map((category) => category.id === id ? { ...category, [field]: value } : category));
  async function save(category) {
    try {
      await request(`/api/admin/categories/${category.id}`, { method: 'PUT', headers: authHeaders(auth, true), body: JSON.stringify(category) });
      setMessage('카테고리를 수정했습니다.'); await load();
    } catch (error) { setMessage(error.message); }
  }
  async function create() {
    try {
      await request('/api/admin/categories', { method: 'POST', headers: authHeaders(auth, true), body: JSON.stringify(newCategory) });
      setNewCategory({ name: '', displayOrder: 0, active: true });
      setMessage('카테고리를 추가했습니다.'); await load();
    } catch (error) { setMessage(error.message); }
  }
  return <section><AdminSectionTitle title="카테고리 관리" description="이름, 노출 순서, 활성 상태를 관리합니다." /><div className="admin-inline-create"><input value={newCategory.name} placeholder="새 카테고리 이름" onChange={(event) => setNewCategory({ ...newCategory, name: event.target.value })} /><input type="number" value={newCategory.displayOrder} aria-label="새 카테고리 노출 순서" onChange={(event) => setNewCategory({ ...newCategory, displayOrder: Number(event.target.value) })} /><button className="primary-button" type="button" onClick={create}><Plus size={14} /> 추가</button></div><div className="admin-table-wrap"><table className="admin-table"><thead><tr><th>ID</th><th>이름</th><th>문제 수</th><th>순서</th><th>노출</th><th /></tr></thead><tbody>{categories.map((category) => <tr key={category.id}><td>{category.id}</td><td><input value={category.name} onChange={(event) => update(category.id, 'name', event.target.value)} /></td><td>{category.problemCount}</td><td><input type="number" value={category.displayOrder} onChange={(event) => update(category.id, 'displayOrder', Number(event.target.value))} /></td><td><input type="checkbox" checked={category.active} onChange={(event) => update(category.id, 'active', event.target.checked)} /></td><td><button className="ghost-button" type="button" onClick={() => save(category)} title="저장"><Save size={14} /></button></td></tr>)}</tbody></table></div></section>;
}

function AuditPanel({ auth, setMessage }) {
  const [logs, setLogs] = useState([]);
  useEffect(() => {
    request('/api/admin/audit-logs', { headers: authHeaders(auth) }).then(setLogs).catch((error) => setMessage(error.message));
  }, [auth, setMessage]);
  return <section><AdminSectionTitle title="관리자 감사 로그" description="관리자가 변경한 대상과 작업 시각을 확인합니다." /><div className="admin-table-wrap"><table className="admin-table"><thead><tr><th>시각</th><th>관리자</th><th>작업</th><th>대상</th><th>상세</th></tr></thead><tbody>{logs.map((log) => <tr key={log.id}><td>{formatDate(log.createdAt)}</td><td>{log.adminUsername}</td><td>{log.action}</td><td>{log.targetType} {log.targetId ?? ''}</td><td>{log.detail ?? '-'}</td></tr>)}</tbody></table></div></section>;
}

function AdminSectionTitle({ title, description }) {
  return <div className="admin-section-title"><div><h2>{title}</h2><p>{description}</p></div><FileClock size={18} /></div>;
}
