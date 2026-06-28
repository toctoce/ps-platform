# Judge Worker Executor 구현 사양

본 문서는 `judge-worker`의 채점 실행기(Executor) 구현을 위한 사양입니다.  
현재 구현은 `JavaExecutor`이므로, 아래 내용은 Java 동작을 기준으로 작성하며
다음 언어 추가 시 동일한 계약을 따르기 위한 가이드를 제공합니다.

## 1. 책임 범위

- Backend에서 내려주는 채점 작업(`JudgeJobAssignment`)을 받아
  - 소스 코드 컴파일(언어별),
  - 테스트케이스 실행,
  - 판정 결과(`ACCEPTED`, `WRONG_ANSWER`, `COMPILE_ERROR`, `RUNTIME_ERROR`,
    `TIME_LIMIT_EXCEEDED`, `MEMORY_LIMIT_EXCEEDED`)를 반환합니다.
- 판정 메시지는 `JudgeMessage`로 backend에 전달되어 사용자에게 노출되므로
  규격화된 문자열 포맷을 유지해야 합니다.
- `judge-worker`는 실행기 내부에 판정 정책/격리/리소스 제어를 담고, backend에는
  채점 실행 자체를 위임하지 않습니다. (`AGENTS.md` 핵심 규칙)

## 2. 입력 데이터 계약

`judge-worker/internal/backend/types.go`의 `JudgeJobAssignment`를 입력으로 받습니다.

- `Language` (예: `"java"`)
- `SourceCode` (문자열)
- `TimeLimitMs` (실행 시간 제한, 음수/0이면 기본값 적용)
- `MemoryLimitMb` (메모리 제한, 음수/0이면 기본값 적용)
- `TestCases[]`  
  - `Input`
  - `ExpectedOutput`

참고: 현재 메인 루프는 Java만 사용합니다.

- 주입부 (`cmd/judge-worker/main.go`)  
  `sandbox.NewJavaExecutor(cfg.JavaDockerImage)`

## 3. Executor 인터페이스 계약

```go
type Executor interface {
    Execute(ctx context.Context, job backend.JudgeJobAssignment) (ExecutionResult, error)
}
```

`ExecutionResult`:

- `Verdict`: 위의 6개 문자열 중 하나
- `Message`: 실패 사유. 성공 시 보통 빈 문자열

### 반환 규칙

- `error != nil`은 실행기 자체의 내부 오류(코드 버그, I/O 오류, 실행기 의존성 실패 등)
  를 의미하고, 판정 문자열만으로 표현되지 않는 실패입니다.
- 정상 판정은 `error == nil`로 반환하고 `Verdict`로 상태를 전달합니다.

## 4. Java 실행기 동작(현재 구현 기준)

파일: `judge-worker/internal/sandbox/executor.go`

### 상수/기본값

- `Verdict` 상수는 backend enum와 일치해야 함
- `defaultTimeLimitMs = 1000`
- `defaultMemoryLimitMb = 128`
- `javaCompileTimeout = 10s` (컴파일 타임아웃)
- `javaSourceFile = "Main.java"`
- `containerWorkspaceDir = "/workspace"`
- `judgeMessageMaxBytes = 1024` (메시지 길이 제한)

### 실행 순서

1. 소스코드 공백 검사
   - 빈 코드: 즉시 `COMPILE_ERROR("source code is empty")`
2. 언어 검사
   - `Language`가 `java`가 아니면 `error("unsupported language: ...")`
3. 임시 폴더 생성 후 `Main.java` 저장 (권한 `0o600`)
4. 시간/메모리 기본값 보정
5. 컴파일
   - `docker run --rm -i --network=none --memory <X>m --memory-swap <X>m -v <tmp>:/workspace -w /workspace <image> javac Main.java`
   - 실행 컨텍스트는 `context.WithTimeout(ctx, 10s)`
6. 테스트케이스 순차 실행 (첫 실패 시 즉시 반환)
   - 각 TC마다 `docker run ... <image> java Main`
   - 표준입력으로 `JudgeTestCase.Input` 전달
   - 실행 컨텍스트는 `timeLimit`(문제별 ms)
7. 각 TC 종료 후 출력 비교
   - `judge.EvaluateOutput(ExpectedOutput, stdout)` 사용
   - 정규화 규칙은 `judge.NormalizeOutput`에 따름(EOF/개행/공백 정리)
8. 모든 TC 통과 시 `ACCEPTED`

## 5. 판정 매핑 규칙

### 컴파일 단계

- `context deadline` 또는 `context deadline exceeded` 감지 → `TIME_LIMIT_EXCEEDED("compile timed out")`
- exit code != 0
  - `isMemoryLimitExceeded(...) == true` → `MEMORY_LIMIT_EXCEEDED("memory limit exceeded on compile")`
  - else → `COMPILE_ERROR`
    - 메시지는 stderr/stdout 우선순위 첫 비어있지 않은 값
    - 둘 다 비어 있으면 `"compile error"`

### 실행 단계 (TC별)

- 실행 컨텍스트/커맨드 타임아웃 감지 → `TIME_LIMIT_EXCEEDED("time limit exceeded on test case N")`
- exit code != 0
  - `isMemoryLimitExceeded(...) == true` → `MEMORY_LIMIT_EXCEEDED("memory limit exceeded on test case N")`
  - else → `RUNTIME_ERROR`
    - 메시지: `runtime error on test case N: <stderr/stdout non-empty>`
    - 출력이 비면 `runtime error on test case N`
- 출력 비교 실패 → `WRONG_ANSWER`
  - 메시지: `wrong answer on test case N: expected=<normalized>, actual=<normalized>`
- 정상 종료 + 정답 → 다음 TC 진행

### 메시지 길이 제한

- `clipMessage`로 최대 `1024`바이트로 자르고 초과분 뒤에 `...` 추가
- WA/에러/컴파일 메시지 모두 동일 규칙 적용

## 6. 메모리 초과 판정 규칙

`executor.go`의 `isMemoryLimitExceeded` 판정:

- `exitCode == 137`이면 메모리 초과로 판단
- 출력 문자열에서 다음 패턴 감지 시 메모리 초과로 판단
  - `out of memory`
  - `outofmemoryerror`
  - `java.lang.outofmemoryerror`
  - `java heap space`
  - `metaspace`
  - (`exceeded` + `memory`) 조합

## 7. 테스트 표준 (Executor 공통)

새 executor를 추가할 때 최소한 다음 케이스는 구현해야 한다.  
`judge-worker/internal/sandbox/executor_test.go`에서 Java 기준으로 이미 일부 존재.

- 입력 검증
  - 소스 비어있음 → `COMPILE_ERROR`
  - 미지원 언어 → `error`
- 컴파일 결과
  - 성공
  - 실패 + stderr 출력
  - 실패 + 출력 없음(디폴트 메시지)
  - 컴파일 timeout
  - 컴파일 메모리 초과(exit 137/메모리 문자열)
- 실행 결과
  - AC
  - WA (메시지 포함: TC 번호, expected/actual)
  - Runtime Error (상세 메시지/미상세)
  - Runtime→Memory 구분(OutOfMemory 패턴, exit 137)
  - 실행 timeout(컨텍스트 timeout, command timeout)
  - 기본값 fallback (`timeLimit`, `memory` 미지정 시 기본값 적용)
- 명령 호출 계약
  - 테스트에서 `fakeCommandRunner`로 호출 횟수/인자(특히 `--memory`, `--memory-swap`, `--network=none`) 검증

## 8. 다른 언어 확장 시 구현 체크리스트

각 언어 executor는 공통 인터페이스를 따르되 언어별 단계만 달리합니다.

- `type <Lang>Executor struct { ... runner commandRunner ... }`
- 생성자
  - 운영: `New<Lang>Executor(image string)`
  - 테스트: `New<Lang>ExecutorWithRunner(runner commandRunner, image string)`
- `Execute`에서 `Language` 비교(대/소문자 무관 비교)
- 소스 파일명/컴파일 명령/실행 명령을 언어별로 명시
- `runTestCase`/`compile`에서 동일한 판정 매핑 규약 적용
- 출력 비교는 `judge.EvaluateOutput` 사용(공통 정규화)
- 리소스 제약(`--memory`, `--memory-swap`, `--network=none`) 유지
- 시간 제한은 문제별 `TimeLimitMs`, 기본값 정책, 컴파일 전용 timeout을 명시
- `isMemoryLimitExceeded` 공통 또는 언어별 보강:
  - Java처럼 OOM 텍스트가 없을 수 있으므로 언어별 패턴 추가 필요 가능

## 9. 명령 실행기 추상화

`commandRunner` 인터페이스와 `dockerCommandRunner` 구현 덕분에 실행기 테스트가 실제 docker 없이 가능.

- 테스트에서는 `fakeCommandRunner`, timeout 시뮬레이션은 `timeoutAwareRunner` 사용.
- `docker` 호출은 현재 `Run(ctx,"docker", args, workDir, stdin)` 형태.

### 현재 구현 파일

- `judge-worker/internal/sandbox/executor.go`
- `judge-worker/internal/sandbox/executor_test.go`

## 10. 다음 작업 포인트 (권장)

- Java 외 언어 추가 시 `main.go`에서 언어별 executor 라우팅(현재는 Java 고정) 추가
- 언어별 Docker 이미지 환경변수 추가(`JUDGE_WORKER_<LANG>_DOCKER_IMAGE` 패턴 권장)
- 공통 스펙을 맞춘 다언어 실행기 추상화(컴파일/실행 단계 템플릿) 고려
