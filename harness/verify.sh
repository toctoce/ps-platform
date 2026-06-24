#!/bin/bash
set -e

echo "PS Platform 프로젝트 검증 시작"

echo "필수 디렉터리 확인"
test -d frontend
test -d backend
test -d judge-worker
test -d docs
test -d infra
test -d harness

echo "AGENTS.md 확인"
test -f AGENTS.md

if [ -f backend/gradlew ]; then
  echo "backend 테스트 실행"
  cd backend
  ./gradlew test
  cd ..
else
  echo "backend 테스트 건너뜀: backend/gradlew 파일이 없음"
fi

if [ -f frontend/package.json ]; then
  echo "frontend 빌드 실행"
  cd frontend
  npm install
  npm run build
  cd ..
else
  echo "frontend 빌드 건너뜀: frontend/package.json 파일이 없음"
fi

echo "변경된 파일"
git diff --stat || true

echo "검증 완료"
