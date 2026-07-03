# backend

Spring Boot 기반 REST API 서버입니다.

## 실행 방법

```bash
cd backend
./gradlew bootRun
```

기본 실행 주소:

```text
http://localhost:8080
```

## 환경변수를 현재 터미널에만 주입해서 실행

로컬 개발 기본값이 설정되어 있으므로 필수는 아니지만, JWT 설정을 직접 지정하려면 다음처럼 실행합니다.

```bash
cd backend
JWT_SECRET=local-development-secret-at-least-32-bytes \
JWT_ACCESS_TOKEN_TTL_SECONDS=900 \
JWT_REFRESH_TOKEN_TTL_SECONDS=1209600 \
./gradlew bootRun
```

문의 메일 전송을 실제로 사용하려면 SMTP 설정도 함께 주입합니다.

```bash
cd backend
SMTP_HOST=smtp.gmail.com \
SMTP_PORT=587 \
SMTP_USERNAME=your-email@gmail.com \
SMTP_PASSWORD=your-app-password \
SMTP_AUTH=true \
SMTP_STARTTLS_ENABLE=true \
SUPPORT_TO_EMAIL=ypungkyu0317@gmail.com \
./gradlew bootRun
```

## 테스트

```bash
cd backend
./gradlew test
```

## 로컬 seed 데이터

개발용 seed 데이터가 필요하면 `backend/seed-data.sql` 파일을 로컬에 만들면 됩니다.
이 파일은 개인 개발 데이터이므로 커밋하지 않습니다.
