# judge-worker

## 환경 변수 기반 설정

`judge-worker`는 실행 시 다음 환경변수를 사용합니다.

- `BACKEND_BASE_URL`  
  - 백엔드 API 주소 (예: `http://localhost:8080`)
- `JUDGE_WORKER_POLL_INTERVAL_MS`  
  - judge-job 폴링 간격(밀리초)
- `JUDGE_WORKER_STALE_AFTER_SECONDS`  
  - 실행 중인 채점 작업이 해당 시간(초) 이상 멈춰 있으면 QUEUED로 재큐합니다.  
- `JAVA_DOCKER_IMAGE`  
  - Java 채점용 Docker 이미지 (예: `ps-platform/judge-java:17`)

## Java 채점 이미지 빌드

Java 실행 시간과 메모리 사용량을 안정적으로 측정하려면 `/usr/bin/time`이 포함된
커스텀 채점 이미지를 사용합니다.

```bash
# repository root
docker build -t ps-platform/judge-java:17 -f infra/judge-images/java17/Dockerfile .
```

실제 값은 코드에 박아두지 않고 `.env` 계열 파일로 관리합니다.

## 실행 방법

```bash
cd judge-worker
BACKEND_BASE_URL=http://localhost:8080 \
JUDGE_WORKER_POLL_INTERVAL_MS=1000 \
JUDGE_WORKER_STALE_AFTER_SECONDS=60 \
JAVA_DOCKER_IMAGE=ps-platform/judge-java:17 \
go run ./cmd/judge-worker
```

또는 `export`로 분리해서 내보내도 됩니다.

```bash
export BACKEND_BASE_URL=http://localhost:8080
export JUDGE_WORKER_POLL_INTERVAL_MS=1000
export JUDGE_WORKER_STALE_AFTER_SECONDS=60
export JAVA_DOCKER_IMAGE=ps-platform/judge-java:17
go run ./cmd/judge-worker
```

## 채점 실행기 문서

- Java 실행기 기준으로 채점 파이프라인/판정 매핑/다른 언어 확장 체크리스트는
  [executor-spec.md](./executor-spec.md)에서 확인합니다.
