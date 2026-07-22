# 프로젝트명

Spring Boot 기반 4인 팀 프로젝트

## 개발 환경

| 도구              | 버전                                             |
| --------------- | ---------------------------------------------- |
| Java            | 21                                             |
| Spring Boot     | 4.1.0                                          |
| Spring Security | Spring Boot 4.1.0 의존성 관리 버전                    |
| Spring Data JPA | Spring Boot 4.1.0 의존성 관리 버전                    |
| JUnit           | JUnit 5                                        |
| Gradle          | 9.5.1                                          |
| MySQL           | 8 이상                                           |
| Spotless        | 8.8.0                                          |
| GitHub Actions  | `actions/checkout@v4`, `actions/setup-java@v4` |


## 최초 개발 설정

### 1. 저장소 복제

```bash
git clone <repository-url>
cd <project-directory>
```

### 2. Git Hook 등록

저장소를 복제한 뒤 최초 1회 실행한다.

```bash
git config core.hooksPath .githooks
```

등록 확인:

```bash
git config --get core.hooksPath
```

정상 출력:

```text
.githooks
```

이후 커밋할 때 Spotless가 자동으로 코드 포맷을 적용한다.

## 기술 스택

* Java
* Spring Boot
* Gradle
* MySQL
* GitHub Actions
* Spotless

## 실행

### Windows

```powershell
.\gradlew.bat bootRun
```

### macOS / Git Bash

```bash
./gradlew bootRun
```

## 테스트

테스트 실행 환경과 방법은 [테스트 가이드](docs/test-guide.md)를 참고합니다.

### Windows

```powershell
.\gradlew.bat test
```

### macOS / Git Bash

```bash
./gradlew test
```

## 코드 포맷

커밋 시 Git Hook이 자동으로 포맷을 적용한다.

수동 적용:

```bash
./gradlew spotlessApply
```

포맷 검사:

```bash
./gradlew spotlessCheck
```

## 브랜치 규칙

| 브랜치         | 용도        |
| ----------- | --------- |
| `main`      | 배포 가능한 코드 |
| `develop`   | 개발 통합     |
| `feature/*` | 기능 개발     |
| `fix/*`     | 버그 수정     |

```text
feature/member-signup
feature/group-schedule
fix/login-validation
```

## 작업 흐름

```text
develop
→ feature 브랜치 생성
→ 개발 및 커밋
→ Pull Request 생성
→ 리뷰 및 CI 통과
→ develop 병합
```

## 커밋 규칙

| 타입         | 용도         |
| ---------- | ---------- |
| `feat`     | 기능 추가      |
| `fix`      | 버그 수정      |
| `refactor` | 리팩터링       |
| `test`     | 테스트 추가·수정  |
| `docs`     | 문서 수정      |
| `chore`    | 설정 및 기타 작업 |

```text
feat: 그룹 일정 생성 기능 추가
fix: 로그인 검증 오류 수정
chore: Spotless 및 CI 설정
```

## 팀원

| 이름   | 역할      |
| ---- | ------- |
| 권혁준  | Backend |
| 임수환 | Backend |
| 장지원 | Backend |
| 정자비 | Backend |
