import { useEffect, useState } from 'react';
import { ArrowLeft, Code2, FileText, Plus, RefreshCw, ShieldCheck, X } from 'lucide-react';
import { request } from '../api/client.js';
import { DIFFICULTY_OPTIONS, initialProblemForm } from '../problem/problemUtils.js';

export function AdminPage({ auth, setMessage, navigate }) {
  const [problems, setProblems] = useState([]);
  const [loadingProblems, setLoadingProblems] = useState(false);
  const [problemForm, setProblemForm] = useState(initialProblemForm);
  const [createBusy, setCreateBusy] = useState(false);
  const [problemId, setProblemId] = useState('');
  const [testCaseForms, setTestCaseForms] = useState([{ input: '', expectedOutput: '' }]);
  const [exampleForms, setExampleForms] = useState([{ input: '', expectedOutput: '', description: '' }]);
  const [testCaseBusy, setTestCaseBusy] = useState(false);
  const [exampleBusy, setExampleBusy] = useState(false);
  const isAuthenticated = Boolean(auth?.accessToken);
  const isAdmin = auth?.user?.role === 'ADMIN';

  function authHeaders() {
    return auth?.accessToken
      ? { Authorization: `Bearer ${auth.accessToken}` }
      : {};
  }

  async function loadProblems() {
    setLoadingProblems(true);
    try {
      const data = await request('/api/problems');
      setProblems(data);
      if (problemId && !data.some((problem) => String(problem.id) === String(problemId))) {
        setProblemId('');
      }
    } catch (error) {
      setMessage(error.message);
    } finally {
      setLoadingProblems(false);
    }
  }

  function updateProblemForm(field, value) {
    setProblemForm((current) => ({ ...current, [field]: value }));
  }

  function updateTestCaseForm(index, field, value) {
    setTestCaseForms((current) => (
      current.map((item, currentIndex) => (
        currentIndex === index ? { ...item, [field]: value } : item
      ))
    ));
  }

  function addTestCaseForm() {
    setTestCaseForms((current) => [...current, { input: '', expectedOutput: '' }]);
  }

  function removeTestCaseForm(index) {
    setTestCaseForms((current) => {
      if (current.length <= 1) {
        return current;
      }
      return current.filter((_, currentIndex) => currentIndex !== index);
    });
  }

  function updateExampleForm(index, field, value) {
    setExampleForms((current) => (
      current.map((item, currentIndex) => (
        currentIndex === index ? { ...item, [field]: value } : item
      ))
    ));
  }

  function addExampleForm() {
    setExampleForms((current) => [...current, { input: '', expectedOutput: '', description: '' }]);
  }

  function removeExampleForm(index) {
    setExampleForms((current) => {
      if (current.length <= 1) {
        return current;
      }
      return current.filter((_, currentIndex) => currentIndex !== index);
    });
  }

  async function createProblem(event) {
    event.preventDefault();
    const categories = Array.from(
      new Set(
        (problemForm.categories ?? '')
          .split(',')
          .map((category) => category.trim())
          .filter((category) => category.length > 0)
      )
    );
    setCreateBusy(true);
    try {
      const payload = {
        ...problemForm,
        categories,
        timeLimitMs: Number(problemForm.timeLimitMs),
        memoryLimitMb: Number(problemForm.memoryLimitMb),
        testCases: [],
        examples: [],
      };
      const created = await request('/api/problems', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...authHeaders() },
        body: JSON.stringify(payload),
      });
      setMessage(`문제 #${created.id}를 등록했습니다.`);
      setProblemId(String(created.id));
      setProblemForm(initialProblemForm);
      await loadProblems();
      navigate(`/problems/${created.id}`);
    } catch (error) {
      setMessage(error.message);
    } finally {
      setCreateBusy(false);
    }
  }

  async function addTestCases(event) {
    event.preventDefault();
    const targetProblemId = Number(problemId);
    if (!Number.isInteger(targetProblemId) || targetProblemId <= 0) {
      setMessage('문제를 선택해 주세요.');
      return;
    }

    const cases = testCaseForms
      .map((item) => ({
        input: item.input,
        expectedOutput: item.expectedOutput,
      }))
      .filter((item) => item.input.trim() !== '' || item.expectedOutput.trim() !== '');

    if (cases.length === 0) {
      setMessage('저장할 테스트케이스가 없습니다.');
      return;
    }

    setTestCaseBusy(true);
    try {
      await Promise.all(
        cases.map((item) => request(`/api/problems/${targetProblemId}/test-cases`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', ...authHeaders() },
          body: JSON.stringify(item),
        }))
      );
      setMessage(`문제 #${targetProblemId} 테스트케이스 ${cases.length}개를 등록했습니다.`);
      setTestCaseForms([{ input: '', expectedOutput: '' }]);
      await loadProblems();
    } catch (error) {
      setMessage(error.message);
    } finally {
      setTestCaseBusy(false);
    }
  }

  async function addExamples(event) {
    event.preventDefault();
    const targetProblemId = Number(problemId);
    if (!Number.isInteger(targetProblemId) || targetProblemId <= 0) {
      setMessage('문제를 선택해 주세요.');
      return;
    }

    const examples = exampleForms
      .map((item) => ({
        input: item.input,
        expectedOutput: item.expectedOutput,
        description: item.description.trim() === '' ? null : item.description,
      }))
      .filter((item) => item.input.trim() !== '' || item.expectedOutput.trim() !== '' || item.description !== null);

    if (examples.length === 0) {
      setMessage('저장할 예제가 없습니다.');
      return;
    }

    setExampleBusy(true);
    try {
      await Promise.all(
        examples.map((item) => request(`/api/problems/${targetProblemId}/examples`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', ...authHeaders() },
          body: JSON.stringify(item),
        }))
      );
      setMessage(`문제 #${targetProblemId} 예제 ${examples.length}개를 등록했습니다.`);
      setExampleForms([{ input: '', expectedOutput: '', description: '' }]);
      await loadProblems();
    } catch (error) {
      setMessage(error.message);
    } finally {
      setExampleBusy(false);
    }
  }

  useEffect(() => {
    if (!isAdmin) {
      return;
    }
    loadProblems();
  }, [isAdmin]);

  if (!isAuthenticated) {
    return (
      <section className="home-page">
        <section className="panel">
          <div className="panel-header">
            <ShieldCheck size={18} />
            <h2>관리자 페이지</h2>
          </div>
          <p className="empty">로그인 후 이용할 수 있습니다.</p>
          <button className="ghost-button with-text" type="button" onClick={() => navigate('/login')}>
            로그인
          </button>
        </section>
      </section>
    );
  }

  if (!isAdmin) {
    return (
      <section className="home-page">
        <section className="panel">
          <div className="panel-header">
            <ShieldCheck size={18} />
            <h2>관리자 페이지</h2>
          </div>
          <p className="empty">관리자 권한이 필요합니다.</p>
        </section>
      </section>
    );
  }

  return (
    <section className="home-page">
      <div className="admin-heading">
        <span className="site-logo-mark">
          <ShieldCheck size={18} />
        </span>
        <div>
          <h1>관리자 페이지</h1>
          <p>문제, 예제, 채점용 테스트케이스를 관리합니다.</p>
        </div>
      </div>
      <div className="page-actions">
        <button className="ghost-button with-text" type="button" onClick={() => navigate('/')}>
          <ArrowLeft size={17} />
          홈으로
        </button>
        <button className="ghost-button with-text" type="button" onClick={loadProblems} disabled={loadingProblems}>
          <RefreshCw size={17} />
          문제 목록 새로고침
        </button>
      </div>

      <section className="panel">
        <div className="panel-header">
          <Plus size={18} />
          <h2>문제 등록</h2>
        </div>
        <form className="form-panel" onSubmit={createProblem}>
          <div className="problem-form-grid">
            <label>
              제목
              <input
                value={problemForm.title}
                onChange={(event) => updateProblemForm('title', event.target.value)}
              />
            </label>
            <label>
              난이도
              <select
                value={problemForm.difficulty}
                onChange={(event) => updateProblemForm('difficulty', event.target.value)}
              >
                {DIFFICULTY_OPTIONS.map((item) => (
                  <option key={item.value} value={item.value}>
                    {item.label}
                  </option>
                ))}
              </select>
            </label>
            <label>
              시간 제한(ms)
              <input
                type="number"
                min="1"
                value={problemForm.timeLimitMs}
                onChange={(event) => updateProblemForm('timeLimitMs', event.target.value)}
              />
            </label>
            <label>
              메모리 제한(MB)
              <input
                type="number"
                min="1"
                value={problemForm.memoryLimitMb}
                onChange={(event) => updateProblemForm('memoryLimitMb', event.target.value)}
              />
            </label>
            <label>
              카테고리(쉼표 구분)
              <input
                value={problemForm.categories}
                onChange={(event) => updateProblemForm('categories', event.target.value)}
                placeholder="예: 구현, 자료구조, 그래프"
              />
            </label>
          </div>
          <label>
            설명
            <textarea
              value={problemForm.description}
              onChange={(event) => updateProblemForm('description', event.target.value)}
            />
          </label>
          <label>
            해설(Markdown)
            <textarea
              value={problemForm.solution}
              onChange={(event) => updateProblemForm('solution', event.target.value)}
            />
          </label>
          <label>
            입력 설명
            <textarea
              value={problemForm.inputDescription}
              onChange={(event) => updateProblemForm('inputDescription', event.target.value)}
            />
          </label>
          <label>
            출력 설명
            <textarea
              value={problemForm.outputDescription}
              onChange={(event) => updateProblemForm('outputDescription', event.target.value)}
            />
          </label>
          <button className="primary-button" type="submit" disabled={createBusy}>
            <Plus size={17} />
            문제 등록
          </button>
        </form>
      </section>

      <section className="panel">
        <div className="panel-header">
          <Code2 size={18} />
          <h2>테스트케이스 등록</h2>
        </div>
        <p className="helper-text">이미 등록된 문제에 대해 테스트케이스를 추가합니다.</p>
        <label>
          문제 선택
          <select value={problemId} onChange={(event) => setProblemId(event.target.value)}>
            <option value="">문제 선택</option>
            {problems.map((problem) => (
              <option key={problem.id} value={String(problem.id)}>
                #{problem.id} {problem.title}
              </option>
            ))}
          </select>
        </label>

        <form className="form-panel" onSubmit={addTestCases}>
          <div className="testcase-editor">
            <div className="panel-header compact-header split">
              <span className="heading-inline">
                <FileText size={17} />
                <h2>테스트케이스 목록</h2>
              </span>
              <button className="ghost-button" type="button" onClick={addTestCaseForm} title="테스트케이스 추가">
                <Plus size={16} />
              </button>
            </div>
            {testCaseForms.map((testCaseForm, index) => (
              <div className="testcase-fields" key={`test-case-${index}`}>
                <div className="testcase-title">
                  <strong>#{index + 1}</strong>
                  <button
                    className="ghost-button icon-only"
                    type="button"
                    onClick={() => removeTestCaseForm(index)}
                    disabled={testCaseForms.length === 1}
                    title="테스트케이스 제거"
                  >
                    <X size={16} />
                  </button>
                </div>
                <label>
                  입력
                  <textarea
                    className="case-textarea"
                    value={testCaseForm.input}
                    onChange={(event) => updateTestCaseForm(index, 'input', event.target.value)}
                  />
                </label>
                <label>
                  기대 출력
                  <textarea
                    className="case-textarea"
                    value={testCaseForm.expectedOutput}
                    onChange={(event) => updateTestCaseForm(index, 'expectedOutput', event.target.value)}
                  />
                </label>
              </div>
            ))}
          </div>
          <button className="primary-button" type="submit" disabled={testCaseBusy}>
            <Plus size={17} />
            테스트케이스 등록
          </button>
        </form>
      </section>

      <section className="panel">
        <div className="panel-header">
          <FileText size={18} />
          <h2>예제 등록</h2>
        </div>
        <p className="helper-text">문제 화면에 공개할 입출력 예제와 설명을 추가합니다. 설명은 비워둘 수 있습니다.</p>
        <label>
          문제 선택
          <select value={problemId} onChange={(event) => setProblemId(event.target.value)}>
            <option value="">문제 선택</option>
            {problems.map((problem) => (
              <option key={problem.id} value={String(problem.id)}>
                #{problem.id} {problem.title}
              </option>
            ))}
          </select>
        </label>

        <form className="form-panel" onSubmit={addExamples}>
          <div className="testcase-editor">
            <div className="panel-header compact-header split">
              <span className="heading-inline">
                <FileText size={17} />
                <h2>예제 목록</h2>
              </span>
              <button className="ghost-button" type="button" onClick={addExampleForm} title="예제 추가">
                <Plus size={16} />
              </button>
            </div>
            {exampleForms.map((exampleForm, index) => (
              <div className="testcase-fields" key={`example-${index}`}>
                <div className="testcase-title">
                  <strong>#{index + 1}</strong>
                  <button
                    className="ghost-button icon-only"
                    type="button"
                    onClick={() => removeExampleForm(index)}
                    disabled={exampleForms.length === 1}
                    title="예제 제거"
                  >
                    <X size={16} />
                  </button>
                </div>
                <label>
                  입력
                  <textarea
                    className="case-textarea"
                    value={exampleForm.input}
                    onChange={(event) => updateExampleForm(index, 'input', event.target.value)}
                  />
                </label>
                <label>
                  출력
                  <textarea
                    className="case-textarea"
                    value={exampleForm.expectedOutput}
                    onChange={(event) => updateExampleForm(index, 'expectedOutput', event.target.value)}
                  />
                </label>
                <label>
                  설명
                  <textarea
                    className="case-textarea"
                    value={exampleForm.description}
                    onChange={(event) => updateExampleForm(index, 'description', event.target.value)}
                  />
                </label>
              </div>
            ))}
          </div>
          <button className="primary-button" type="submit" disabled={exampleBusy}>
            <Plus size={17} />
            예제 등록
          </button>
        </form>
      </section>
    </section>
  );
}
