# backend

Spring Boot 기반 REST API 서버입니다.

## 실행 방법

로컬 개발은 PostgreSQL 컨테이너를 먼저 실행한 뒤 `local` 프로필로 백엔드를 실행합니다.

```bash
docker compose -f ../infra/docker-compose.yml up -d postgres
```

```bash
SPRING_PROFILES_ACTIVE=local \
JWT_SECRET=backend-dev-secret-key-for-local-development-change-this \
JWT_ACCESS_TOKEN_TTL_SECONDS=900 \
JWT_REFRESH_TOKEN_TTL_SECONDS=1209600 \
./gradlew bootRun
```

기본 실행 주소:

```text
http://localhost:8080
```

## 환경변수를 현재 터미널에만 주입해서 실행

문의 메일 전송을 실제로 사용하려면 SMTP 설정도 함께 주입합니다.

```bash
SPRING_PROFILES_ACTIVE=local \
JWT_SECRET=local-development-secret-at-least-32-bytes \
JWT_ACCESS_TOKEN_TTL_SECONDS=900 \
JWT_REFRESH_TOKEN_TTL_SECONDS=1209600 \
SMTP_HOST=smtp.gmail.com \
SMTP_PORT=587 \
SMTP_USERNAME=your-email@gmail.com \
SMTP_PASSWORD=your-app-password \
SMTP_AUTH=true \
SMTP_STARTTLS_ENABLE=true \
SUPPORT_TO_EMAIL=ypungkyu0317@gmail.com \
./gradlew bootRun
```

운영 서버에서는 `prod` 프로필과 DB 접속 정보를 환경변수로 주입합니다.

```bash
SPRING_PROFILES_ACTIVE=prod \
DB_HOST=your-db-host \
DB_PORT=5432 \
DB_NAME=ps_platform \
DB_USERNAME=your-db-user \
DB_PASSWORD=your-db-password \
JWT_SECRET=replace-with-long-random-secret \
JWT_ACCESS_TOKEN_TTL_SECONDS=900 \
JWT_REFRESH_TOKEN_TTL_SECONDS=1209600 \
SMTP_HOST=smtp.gmail.com \
SMTP_PORT=587 \
SMTP_USERNAME=your-email@gmail.com \
SMTP_PASSWORD=your-app-password \
SMTP_AUTH=true \
SMTP_STARTTLS_ENABLE=true \
SUPPORT_TO_EMAIL=ypungkyu0317@gmail.com \
SUPPORT_FROM_EMAIL=your-email@gmail.com \
./gradlew bootRun
```

## 테스트

```bash
./gradlew test
```

## 로컬 seed 데이터

개발용 seed 데이터가 필요하면 `backend/seed-data.sql` 파일을 로컬에 만들면 됩니다.
이 파일은 개인 개발 데이터이므로 커밋하지 않습니다.
