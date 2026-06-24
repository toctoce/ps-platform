# AGENTS.md

## 프로젝트

이 프로젝트는 PS Platform입니다.

여기서 PS는 Problem Solving을 의미합니다.

현재 서비스 이름은 빵구리즘이지만, 나중에 변경될 수 있습니다.

PS Platform은 알고리즘 문제 풀이와 온라인 채점을 제공하는 플랫폼입니다.

## 애플리케이션

이 프로젝트는 세 개의 주요 애플리케이션을 포함하는 monorepo입니다.

* frontend: React + Vite 기반 프론트엔드 애플리케이션
* backend: Spring Boot 기반 REST API 서버
* judge-worker: 격리된 환경에서 코드 실행과 채점을 담당하는 worker 서비스

## 핵심 아키텍처 규칙

backend는 사용자가 제출한 코드를 직접 실행하면 안 됩니다.

backend는 judge job을 생성합니다.

judge-worker는 judge job을 소비하고, 제출된 코드를 격리된 환경에서 실행합니다.

## 디렉터리 책임

* frontend: 사용자 인터페이스
* backend: API, 도메인 로직, 영속성 처리
* judge-worker: 코드 실행과 채점
* docs: 제품, 아키텍처, 인프라, 보안 문서
* infra: Docker Compose, 배포, 인프라 파일
* harness: 검증 스크립트와 작업 파일

## 개발 규칙

* frontend는 단순하게 유지합니다.
* backend와 judge-worker의 정확성을 우선합니다.
* 비즈니스 로직을 frontend에 두지 않습니다.
* 코드 실행 로직을 backend에 두지 않습니다.
* 검증을 통과시키기 위해 테스트를 삭제하거나 약화하지 않습니다.
* 작고 리뷰하기 쉬운 변경을 선호합니다.

## 검증

작업을 마치기 전에 다음 명령을 실행합니다.

./harness/verify.sh

## 완료 기준

* 검증이 통과합니다.
* 변경된 파일을 요약합니다.
* 남아 있는 위험 요소를 언급합니다.
