# Judge Worker Executor 구현 사양

본 문서는 `judge-worker`의 채점 실행기(Executor) 구현 사양입니다.
현재 구현은 Java, Python, C++ executor를 지원하며, 새 언어를 추가할 때도 같은 계약을 따라야 합니다.

## 1. 책임 범위

- Backend에서 내려주는 채점 작업(`JudgeJobAssignment`)을 받아
  - 소스 코드 컴파일 또는 문법 검사,
  - 테스트케이스 실행,
  - 판정 결과(`ACCEPTED`, `WRONG_ANSWER`, `COMPILE_ERROR`, `RUNTIME_ERROR`,
    `TIME_LIMIT_EXCEEDED`, `MEMORY_LIMIT_EXCEEDED`)를 반환합니다.
- 판정 메시지는 `JudgeMessage`로 backend에 전달되어 사용자에게 노출되므로
  규격화된 문자열 포맷을 유지해야 합니다.
- `judge-worker`는 실행기 내부에 판정 정책, 격리, 리소스 제어를 담고, backend에는
  코드 실행 자체를 위임하지 않습니다.

## 2. 입력 데이터 계약

`judge-worker/internal/backend/types.go`의 `JudgeJobAssignment`를 입력으로 받습니다.

- `Language`: `"java"`, `"python"`, `"cpp"` 중 하나
- `SourceCode`: 제출 코드 문자열
- `TimeLimitMs`: 실행 시간 제한, 음수/0이면 기본값 적용
- `MemoryLimitMb`: 메모리 제한, 음수/0이면 기본값 적용
- `JobKind`: `"SUBMISSION"` 또는 `"CODE_RUN"`
- `TestCases[]`
  - `Input`
  - `ExpectedOutput`

현재 메인 루프는 `LanguageExecutor`를 사용합니다.

```go
sandbox.NewLanguageExecutor(
    cfg.JavaDockerImage,
    cfg.PythonDockerImage,
    cfg.CppDockerImage,
)
```

## 3. Executor 인터페이스 계약

```go
type Executor interface {
    Execute(ctx context.Context, job backend.JudgeJobAssignment) (ExecutionResult, error)
}
```

`ExecutionResult`:

- `Verdict`: 위의 6개 verdict 문자열 중 하나
- `Message`: 실패 사유. 성공 시 보통 빈 문자열
- `CaseResults`: 테스트케이스별 verdict/stdout/message/시간/메모리

반환 규칙:

- `error != nil`은 실행기 자체의 내부 오류, I/O 오류, 의존성 실패처럼 verdict로 표현하기 어려운 실패입니다.
- 정상 판정은 `error == nil`로 반환하고 `Verdict`로 상태를 전달합니다.

## 4. 언어별 실행기 구조

파일: `judge-worker/internal/sandbox/executor.go`

공통 실행은 `dockerLanguageExecutor`가 담당하고, 언어별 차이는 `languageSpec`으로 분리합니다.

| 언어 | aliases | source file | compile command | run command |
| --- | --- | --- | --- | --- |
| Java | - | `Main.java` | `javac Main.java` | `java Main` |
| Python | `py`, `python3` | `main.py` | `python3 -m py_compile main.py` | `python3 main.py` |
| C++ | `c++` | `main.cpp` | `g++ -std=c++17 -O2 -pipe -o main main.cpp` | `./main` |

기본값:

- `defaultTimeLimitMs = 1000`
- `defaultMemoryLimitMb = 128`
- `defaultCompileTimeout = 10s`
- `containerWorkspaceDir = "/workspace"`
- `judgeMessageMaxBytes = 1024`

## 5. 실행 순서

1. `LanguageExecutor`가 `job.Language`에 맞는 executor 선택
2. 소스 코드 공백 검사
   - 빈 코드: `COMPILE_ERROR("source code is empty")`
3. 언어 검사
   - 지원하지 않는 언어: `error("unsupported language: ...")`
4. 임시 폴더 생성 후 언어별 source file 저장
   - 파일 권한: `0o600`
5. 시간/메모리 기본값 보정
6. 컴파일 또는 문법 검사
   - `docker run --rm -i --network=none --memory <X>m --memory-swap <X>m -v <tmp>:/workspace -w /workspace <image> <compile command>`
   - 실행 컨텍스트: `context.WithTimeout(ctx, 10s)`
7. 테스트케이스 실행
   - `docker run ... <image> sh -c "<metric wrapper> <run command>"`
   - 표준입력으로 `JudgeTestCase.Input` 전달
   - 실행 컨텍스트는 문제별 `TimeLimitMs`
   - `SUBMISSION`: 첫 실패 시 즉시 중단
   - `CODE_RUN`: 중간 실패가 있어도 모든 케이스 실행
8. 출력 비교
   - `judge.EvaluateOutput(ExpectedOutput, stdout)` 사용
   - 정규화 규칙은 `judge.NormalizeOutput`에 따름
9. 모든 케이스 통과 시 `ACCEPTED`

## 6. 판정 매핑 규칙

컴파일 단계:

- timeout 감지: `TIME_LIMIT_EXCEEDED("compile timed out")`
- exit code != 0
  - 메모리 초과 감지: `MEMORY_LIMIT_EXCEEDED("memory limit exceeded on compile")`
  - 그 외: `COMPILE_ERROR`
    - 메시지는 stderr/stdout 중 첫 비어있지 않은 값
    - 둘 다 비어 있으면 `"compile error"`

실행 단계:

- timeout 감지: `TIME_LIMIT_EXCEEDED("time limit exceeded on test case N")`
- exit code != 0
  - 메모리 초과 감지: `MEMORY_LIMIT_EXCEEDED("memory limit exceeded on test case N")`
  - 그 외: `RUNTIME_ERROR`
    - 출력이 있으면 `runtime error on test case N: <stderr/stdout>`
    - 출력이 없으면 `runtime error on test case N`
- 출력 비교 실패: `WRONG_ANSWER`
  - 메시지: `wrong answer on test case N: expected=<normalized>, actual=<normalized>`
- 정상 종료 + 정답: 다음 테스트케이스 진행

메시지는 `clipMessage`로 최대 `1024`바이트까지만 전달합니다.

## 7. 시간/메모리 측정 계약

실행 시간/메모리 측정은 컨테이너 내부 `/usr/bin/time` 출력 값을 우선 사용합니다.

- 실행 시간: `%e` seconds -> `executionTimeMs`
- 메모리: `%M` max RSS KB -> `memoryMb`
- `/usr/bin/time` 값이 없으면 worker 외부 실행 시간과 Docker stats 기반 메모리 값을 fallback으로 사용합니다.

현재 시간 측정은 Docker 실행 오버헤드와 컨테이너 내부 측정값이 섞일 수 있으므로, 정밀도를 더 높이는 작업은 별도 개선 과제로 둡니다.

## 8. 메모리 초과 판정 규칙

`isMemoryLimitExceeded`는 다음을 메모리 초과로 판단합니다.

- `exitCode == 137`
- 출력 문자열에 다음 패턴 포함
  - `out of memory`
  - `outofmemoryerror`
  - `java.lang.outofmemoryerror`
  - `java heap space`
  - `metaspace`
  - `exceeded`와 `memory` 조합

Python/C++ 런타임별 OOM 메시지는 실제 사례가 쌓이면 추가 보강합니다.

## 9. 채점 Docker 이미지 계약

모든 언어별 채점 이미지는 `/usr/bin/time`을 포함해야 합니다.

| 언어 | Dockerfile | 이미지 태그 |
| --- | --- | --- |
| Java | `infra/judge-images/java17/Dockerfile` | `ps-platform/judge-java:17` |
| Python | `infra/judge-images/python3/Dockerfile` | `ps-platform/judge-python:3.12` |
| C++ | `infra/judge-images/cpp17/Dockerfile` | `ps-platform/judge-cpp:17` |

빌드 명령:

```bash
# repository root
docker build -t ps-platform/judge-java:17 -f infra/judge-images/java17/Dockerfile .
docker build -t ps-platform/judge-python:3.12 -f infra/judge-images/python3/Dockerfile .
docker build -t ps-platform/judge-cpp:17 -f infra/judge-images/cpp17/Dockerfile .
```

## 10. 테스트 표준

새 언어 executor를 추가할 때 최소한 다음을 검증합니다.

- 입력 검증
  - 소스 비어있음 -> `COMPILE_ERROR`
  - 미지원 언어 -> `error`
- 컴파일 결과
  - 성공
  - 실패 + stderr 출력
  - 실패 + 출력 없음
  - 컴파일 timeout
  - 컴파일 메모리 초과
- 실행 결과
  - AC
  - WA
  - Runtime Error
  - Memory Limit Exceeded
  - Time Limit Exceeded
  - `SUBMISSION`은 첫 실패 중단
  - `CODE_RUN`은 모든 케이스 실행
- 명령 호출 계약
  - `--network=none`
  - `--memory`, `--memory-swap`
  - 언어별 source file, compile command, run command
- metric marker는 사용자 stderr 메시지에서 제거되어야 함

## 11. 다른 언어 확장 체크리스트

- `languageSpec`에 다음 값을 추가
  - `name`
  - `aliases`
  - `image`
  - `sourceFile`
  - `compileCommand`
  - `runCommand`
- `Config`에 언어별 Docker image 환경변수 추가
- `cmd/judge-worker/main.go`에서 `NewLanguageExecutor` 조립부에 연결
- `infra/judge-images/<language>/Dockerfile` 추가
- `.env.example`, README, 이 문서 갱신
- executor 테스트에 컴파일/실행 명령 계약 추가
