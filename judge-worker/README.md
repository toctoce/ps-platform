# judge-worker

## 환경 변수 기반 설정

`judge-worker`는 실행 시 다음 환경변수를 사용합니다.

- `BACKEND_BASE_URL`  
  - 백엔드 API 주소 (예: `http://localhost:8080`)
- `JUDGE_WORKER_POLL_INTERVAL_MS`  
  - judge-job 폴링 간격(밀리초)
- `JAVA_DOCKER_IMAGE`  
  - Java 채점용 Docker 이미지 (예: `eclipse-temurin:17-jdk`)

실제 값은 코드에 박아두지 않고 `.env` 계열 파일로 관리합니다.

## 실행 방법

```bash
cp judge-worker/.env.example judge-worker/.env
source judge-worker/.env
cd judge-worker
go run ./cmd/judge-worker
```

원하면 `env`를 한 줄로 내보내도 됩니다.

```bash
export BACKEND_BASE_URL=http://localhost:8080
export JUDGE_WORKER_POLL_INTERVAL_MS=1000
export JAVA_DOCKER_IMAGE=eclipse-temurin:17-jdk
go run ./cmd/judge-worker
```
