let formatterWorker;
let nextRequestId = 1;
const pendingRequests = new Map();

function rejectPendingRequests(error) {
  pendingRequests.forEach(({ reject }) => reject(error));
  pendingRequests.clear();
}

function createFormatterWorker() {
  const worker = new Worker(new URL('./formatter.worker.js', import.meta.url), { type: 'module' });
  worker.addEventListener('message', (event) => {
    const { id, formattedSource, error } = event.data;
    const request = pendingRequests.get(id);
    if (!request) {
      return;
    }
    pendingRequests.delete(id);
    if (error) {
      request.reject(new Error(error));
      return;
    }
    request.resolve(formattedSource);
  });
  worker.addEventListener('error', () => {
    rejectPendingRequests(new Error('코드 포맷터를 불러오지 못했습니다.'));
    worker.terminate();
    formatterWorker = null;
  });
  return worker;
}

export function formatCode(language, sourceCode) {
  if (!formatterWorker) {
    formatterWorker = createFormatterWorker();
  }

  const id = nextRequestId;
  nextRequestId += 1;
  return new Promise((resolve, reject) => {
    pendingRequests.set(id, { resolve, reject });
    formatterWorker.postMessage({ id, language, sourceCode });
  });
}

export function formatShortcutLabel() {
  const isMac = typeof navigator !== 'undefined' && /Mac|iPhone|iPad/.test(navigator.platform);
  const isLinux = typeof navigator !== 'undefined' && /Linux/.test(navigator.platform);
  if (isMac) {
    return 'Shift + Option + F';
  }
  if (isLinux) {
    return 'Ctrl + Shift + I';
  }
  return 'Shift + Alt + F';
}
