# Moi-Go

Moi-Go는 스터디원 모집부터 실제 스터디 운영, 출석 및 활동 기록까지 한곳에서 관리하는 서비스입니다.

## 목차

* [기술 스택](#기술-스택)
* [개발 환경](#개발-환경)
* [최초 개발 설정](#최초-개발-설정)
* [실행](#실행)
* [테스트](#테스트)
* [코드 포맷](#코드-포맷)
* [협업 규칙](#협업-규칙)
* [팀원](#팀원)

## 기술 스택

* Backend: Java, Spring Boot, Spring Security, Spring Data JPA
* Database: MySQL
* Test: JUnit 5, MySQL
* Quality: Spotless, GitHub Actions

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

저장소를 복제한 뒤 최초 1회 실행합니다.

```bash
git config core.hooksPath .githooks
```

등록 여부는 다음 명령어로 확인합니다.

```bash
git config --get core.hooksPath
```

정상적으로 등록된 경우 다음과 같이 출력됩니다.

```text
.githooks
```

이후 커밋할 때 Spotless가 자동으로 코드 포맷을 적용합니다.

## 실행

### 1. 로컬 DB 설정

처음 실행하기 전에 예시 파일을 복사한 뒤 본인의 MySQL 연결 정보를 입력합니다.

#### macOS / Linux / Git Bash

```bash
cp src/main/resources/application-local.example.properties \
  src/main/resources/application-local.properties
```

#### Windows PowerShell

```powershell
Copy-Item src/main/resources/application-local.example.properties `
  src/main/resources/application-local.properties
```

#### Windows 명령 프롬프트

```cmd
copy src\main\resources\application-local.example.properties ^
  src\main\resources\application-local.properties
```

`application-local.properties`는 Git에 포함되지 않습니다.

### 2. 애플리케이션 실행

#### macOS / Linux / Git Bash

```bash
./gradlew bootRun
```

#### Windows PowerShell / 명령 프롬프트

```powershell
.\gradlew.bat bootRun
```

## 테스트

테스트 실행 환경과 방법은 [테스트 가이드](docs/testing/test-guide.md)를 참고합니다.

전체 테스트에는 로컬 MySQL 테스트 DB가 필요합니다. 테스트용 DB 계정 정보는 설정 파일에 저장하지 않고 환경변수로 전달합니다.

#### macOS / Linux / Git Bash

```bash
SPRING_DATASOURCE_USERNAME=YOUR_MYSQL_USERNAME \
SPRING_DATASOURCE_PASSWORD=YOUR_MYSQL_PASSWORD \
./gradlew test
```

#### Windows PowerShell

```powershell
$env:SPRING_DATASOURCE_USERNAME = "YOUR_MYSQL_USERNAME"
$env:SPRING_DATASOURCE_PASSWORD = "YOUR_MYSQL_PASSWORD"
.\gradlew.bat test
```

#### Windows 명령 프롬프트

```cmd
set SPRING_DATASOURCE_USERNAME=YOUR_MYSQL_USERNAME
set SPRING_DATASOURCE_PASSWORD=YOUR_MYSQL_PASSWORD
.\gradlew.bat test
```

마지막에 `BUILD SUCCESSFUL`이 출력되면 테스트가 성공한 것입니다.

## 코드 포맷

커밋 시 Git Hook이 자동으로 코드 포맷을 적용합니다.

수동으로 포맷 검사를 실행하려면 다음 명령어를 사용합니다.

### macOS / Linux / Git Bash

```bash
./gradlew spotlessCheck
```

### Windows PowerShell / 명령 프롬프트

```powershell
.\gradlew.bat spotlessCheck
```

마지막에 `BUILD SUCCESSFUL`이 출력되면 포맷 검사가 성공한 것입니다.

## 협업 규칙

### 브랜치 규칙

| 브랜치         | 용도        |
| ----------- | --------- |
| `main`      | 배포 가능한 코드 |
| `develop`   | 개발 통합     |
| `feature/*` | 기능 개발     |
| `fix/*`     | 버그 수정     |
| `docs/*`    | 문서 작업     |

브랜치 이름은 작업 내용을 식별할 수 있도록 작성합니다.

```text
feature/member-signup
feature/group-schedule
fix/login-validation
```

### 작업 흐름

```text
develop
→ 작업 브랜치 생성
→ 개발 및 커밋
→ Pull Request 생성
→ 리뷰 및 CI 통과
→ develop 병합
```

기능 개발은 `feature/*`, 버그 수정은 `fix/*`, 문서 작업은 `docs/*` 브랜치를 사용합니다.

### 커밋 규칙

| 타입         | 용도         |
| ---------- | ---------- |
| `feat`     | 기능 추가      |
| `fix`      | 버그 수정      |
| `refactor` | 리팩터링       |
| `test`     | 테스트 추가·수정  |
| `docs`     | 문서 수정      |
| `chore`    | 설정 및 기타 작업 |

커밋 메시지는 다음 형식을 사용합니다.

```text
타입: 변경 내용
```

예시는 다음과 같습니다.

```text
feat: 그룹 일정 생성 기능 추가
fix: 로그인 검증 오류 수정
chore: Spotless 및 CI 설정
```

## 팀원

| 이름  | 역할      |
| --- | ------- |
| 권혁준 | Backend |
| 임수환 | Backend |
| 장지원 | Backend |
| 정자비 | Backend |
