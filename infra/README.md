# 인프라 실행 및 운영 작업

## PostgreSQL 실행

```bash
docker compose -f infra/docker-compose.yml up -d postgres
```

PostgreSQL 서버와 backend의 PostgreSQL 연결은 UTC를 사용합니다.

## 기존 제출 시각 마이그레이션

기존 `submissions.created_at`, `submissions.judged_at` 값이 한국 표준시로 저장된 DB에서 한 번 실행합니다.

```bash
docker exec -i ps-platform-postgres \
  psql -U ps_user -d ps_platform \
  < infra/postgres/migrations/20260713_submission_timestamps_utc.sql
```

스크립트는 기존 한국 시각을 실제 시각으로 보존하면서 `TIMESTAMP WITH TIME ZONE`으로 변환하고, DB 기본 시간대를 UTC로 설정합니다. 이미 변환된 컬럼에는 다시 시간 변환을 적용하지 않습니다.

적용 결과는 다음 명령으로 확인합니다.

```bash
docker exec -it ps-platform-postgres \
  psql -U ps_user -d ps_platform \
  -c "SHOW timezone; SELECT id, created_at, judged_at FROM submissions ORDER BY id DESC LIMIT 5;"
```
