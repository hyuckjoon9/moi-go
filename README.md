<div align="center">

  # 스터디 모집부터 운영과 기록까지 한곳에서, Moi-Go

<img src="https://github.com/user-attachments/assets/4fc17a16-b28a-4d06-a43f-d6b1888786bf" alt="Moi-Go Banner" width="100%" />

</div>



## 📌 프로젝트 개요

| 구분        | 내용                            |
| --------- | ----------------------------- |
| **프로젝트명** | Moi-Go                        |
| **팀명**    | 모이고                    |
| **개발 기간** | 2026.07.16 ~ 2026.07.27 |
| **개발 인원** | 4명                            |
| **팀원** | 권혁준 · 임수환 · 장지원 · 정자비 |

<br>

## 💡 프로젝트 소개

**Moi-Go**는 스터디원 모집부터 그룹 운영, 일정 관리, 출석 확인, 활동 기록까지 하나의 흐름으로 관리하는 스터디 운영 서비스입니다.

기존에는 모집, 일정 공유, 참석 확인, 출석 관리와 활동 기록이 여러 도구에 분산되어 있어 운영 과정과 기록을 한눈에 파악하기 어려웠습니다.

Moi-Go는 다음 흐름을 하나의 서비스 안에서 연결합니다.

```text
회원가입·로그인
    ↓
모집글 작성
    ↓
참가 신청·승인
    ↓
스터디 그룹 생성
    ↓
일정 등록·참석 응답
    ↓
출석 체크
    ↓
활동 기록·회고 작성
```

<br>

## 🚀 실행 방법

아래 순서대로 실행하면 로컬 MySQL 데이터베이스, 샘플 데이터, 애플리케이션 서버를 차례로 준비할 수 있습니다.

### 1. 사전 준비

다음을 설치한 뒤 MySQL Server가 실행 중인지 확인합니다.

* Git
* JDK 21
* MySQL Server 8.0 이상 — 설치를 마치고 서버가 실행 중이어야 합니다.

```text
java -version    # 21 버전인지 확인
mysql --version
```

### 2. 프로젝트 내려받기

```bash
git clone https://github.com/hyuckjoon9/moi-go.git
cd moi-go
```

### 3. MySQL 및 샘플 데이터 준비

아래 명령을 실행하면 MySQL의 비밀번호 입력을 요구합니다. MySQL 설치 시 설정한 `root` 비밀번호를 입력하세요. 먼저 `moigo` 데이터베이스를 만들고, 저장소의 [`sql/moigo_schema_seed.sql`](sql/moigo_schema_seed.sql)로 테이블과 샘플 데이터를 넣습니다.

macOS / Linux:

```bash
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS moigo CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -u root -p moigo < sql/moigo_schema_seed.sql
```

Windows PowerShell:

```powershell
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS moigo CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
cmd /d /c "mysql -u root -p moigo < sql\moigo_schema_seed.sql"
```

Windows 명령 프롬프트(CMD):

```cmd
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS moigo CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -u root -p moigo < sql\moigo_schema_seed.sql
```

> **주의:** `sql/moigo_schema_seed.sql`은 기존 테이블을 삭제한 뒤 샘플 데이터로 다시 생성합니다. 기존 로컬 데이터를 보존해야 한다면 실행하지 마세요.

### 4. 로컬 DB 설정 파일 만들기

예제 설정 파일을 복사해 Git에서 제외되는 로컬 설정 파일을 만듭니다. 이 파일에는 MySQL 접속 URL(`localhost:3306/moigo`)이 미리 들어 있습니다.

macOS / Linux:

```bash
cp src/main/resources/application-local.example.properties src/main/resources/application-local.properties
```

Windows PowerShell:

```powershell
Copy-Item src\main\resources\application-local.example.properties src\main\resources\application-local.properties
```

Windows 명령 프롬프트(CMD):

```cmd
copy src\main\resources\application-local.example.properties src\main\resources\application-local.properties
```

### 5. 서버 실행

서버 실행 전 같은 터미널에서 MySQL 계정을 환경 변수로 설정합니다. 비밀번호는 화면에 표시되지 않습니다. 실행이 끝나면 브라우저에서 [http://localhost:8080](http://localhost:8080)으로 접속하세요.

macOS / Linux:

```bash
export SPRING_DATASOURCE_USERNAME=root
read -rsp "MySQL root password: " SPRING_DATASOURCE_PASSWORD; echo
export SPRING_DATASOURCE_PASSWORD
./gradlew bootRun
```

Windows PowerShell:

```powershell
$env:SPRING_DATASOURCE_USERNAME = "root"
$mysqlPassword = Read-Host "MySQL root password" -AsSecureString
$env:SPRING_DATASOURCE_PASSWORD = [System.Net.NetworkCredential]::new("", $mysqlPassword).Password
.\gradlew.bat bootRun
```

Windows 명령 프롬프트(CMD):

```cmd
set "SPRING_DATASOURCE_USERNAME=root"
set /p "SPRING_DATASOURCE_PASSWORD=MySQL root password: "
.\gradlew.bat bootRun
```

> CMD에서는 비밀번호가 입력 중 화면에 표시됩니다. 가려서 입력하려면 PowerShell 명령을 사용하세요.

### 6. Back Office 접속

서버 실행 후 [http://localhost:8080/backoffice/index.html](http://localhost:8080/backoffice/index.html)로 이동해 아래 샘플 관리자 계정으로 로그인합니다.

| 구분 | 값 |
| --- | --- |
| 이메일 | `admin@moigo.test` |
| 비밀번호 | `Admin1234!` |

<details>
<summary><b>문제 해결</b></summary>

<br>

* `mysql` 명령을 찾을 수 없으면 MySQL 설치 경로의 `bin` 폴더를 운영체제의 `PATH`에 추가한 뒤 새 터미널을 여세요.
* `Can't connect to MySQL server`가 나오면 MySQL Server 서비스가 실행 중인지 확인하세요. Windows 기본 서비스 이름은 보통 `MySQL80`이며, 관리자 권한 CMD에서 `net start MySQL80`으로 시작할 수 있습니다.
* 3306 포트를 다른 포트로 사용 중이면 `SPRING_DATASOURCE_URL` 환경 변수에 실제 포트를 포함한 JDBC URL을 설정해야 합니다.
* macOS / Linux에서 `./gradlew: Permission denied`가 발생하면 `chmod +x gradlew`를 한 번 실행한 뒤 서버 실행 명령을 다시 입력합니다.

</details>

<br>

## ✨ 핵심 기능

<details>
<summary><b>🔐 회원 및 인증</b></summary>

<br>

* 회원가입 및 로그인
* JWT 기반 Access Token·Refresh Token 인증
* 로그인 사용자 정보 조회 및 수정
* 프로필 이미지 등록
* 토큰 재발급 및 로그아웃
* 관리자에 의해 정지된 회원의 인증 및 서비스 이용 제한

</details>

<details>
<summary><b>📢 스터디 모집 및 참가 신청</b></summary>

<br>

* 스터디 모집글 작성·조회·수정·삭제
* 카테고리 및 상태별 모집글 조회
* 모집 마감·재개·종료
* 스터디 참가 신청 및 신청 내역 조회
* 모집장의 지원자 조회·승인·거절
* 승인된 지원자의 그룹원 자동 등록

</details>

<details>
<summary><b>👥 스터디 그룹 관리</b></summary>

<br>

* 모집글을 기반으로 스터디 그룹 생성
* 모집글별 중복 그룹 생성 방지
* 그룹원 중복 가입 방지
* 그룹장·운영진·일반 구성원 역할 관리
* 사용자가 참여 중인 그룹 목록 조회
* 그룹 홈에서 구성원과 일정 정보 통합 조회

</details>

<details>
<summary><b>📅 그룹 일정 관리</b></summary>

<br>

* 그룹 일정 생성·목록 조회·상세 조회
* 예정 일정과 지난 일정 구분 조회
* 일정 내용·장소·온라인 링크·준비물 관리
* 일정 수정 및 참석 응답 마감 시간 변경
* 일정 시작 여부와 관련 기록을 고려한 삭제 제한
* 그룹 역할에 따른 일정 관리 권한 검증

</details>

<details>
<summary><b>✅ 참석 및 출석 관리</b></summary>

<br>

* 일정별 참석·불참·미정 응답
* 응답 마감 시간 검증
* 그룹 운영진의 실제 출석 체크
* 출석·지각·결석·인정 결석 상태 관리
* 사용자의 개인 출석률 조회
* 그룹장과 운영진의 전체 그룹원 출석률 조회

</details>

<details>
<summary><b>📝 활동 기록 및 회고</b></summary>

<br>

* 일정별 활동 기록 작성
* 학습 주제와 진행 내용 관리
* 과제와 다음 일정 준비 사항 기록
* 참고 자료 링크 관리
* 그룹원별 한 줄 회고 작성
* 출석 및 활동 이력 보존

</details>

<details>
<summary><b>🛡️ Back Office</b></summary>

<br>

* 관리자 전용 로그인 및 접근 제어
* 전체 회원 검색·상세 조회
* 회원 계정 정지 및 복구
* 모집글 숨김 및 복구
* 그룹·일정·출석·활동 기록 통합 조회
* 관리자 작업 이력 조회
* 운영 데이터 조회 기능과 상태 변경 기능 분리

</details>

<br>

## 🛠️ 기술 스택

### Backend

| 구분          | 기술                                                                                                                                                                                                                                     |
| ----------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Language    | ![Java](https://img.shields.io/badge/Java_21-007396?style=flat-square\&logo=openjdk\&logoColor=white)                                                                                                                                  |
| Framework   | ![Spring Boot](https://img.shields.io/badge/Spring_Boot_4.1-6DB33F?style=flat-square\&logo=springboot\&logoColor=white) ![Spring MVC](https://img.shields.io/badge/Spring_MVC-6DB33F?style=flat-square\&logo=spring\&logoColor=white)  |
| Security    | ![Spring Security](https://img.shields.io/badge/Spring_Security-6DB33F?style=flat-square\&logo=springsecurity\&logoColor=white) ![JWT](https://img.shields.io/badge/JWT-000000?style=flat-square\&logo=jsonwebtokens\&logoColor=white) |
| Persistence | ![Spring Data JPA](https://img.shields.io/badge/Spring_Data_JPA-6DB33F?style=flat-square\&logo=spring\&logoColor=white)                                                                                                                |
| Database    | ![MySQL](https://img.shields.io/badge/MySQL_8-4479A1?style=flat-square\&logo=mysql\&logoColor=white)                                                                                                                                   |
| Build       | ![Gradle](https://img.shields.io/badge/Gradle_9.5-02303A?style=flat-square\&logo=gradle\&logoColor=white)                                                                                                                              |

### Frontend

| 구분       | 기술                                                                                                                |
| -------- | ----------------------------------------------------------------------------------------------------------------- |
| Markup   | ![HTML5](https://img.shields.io/badge/HTML5-E34F26?style=flat-square\&logo=html5\&logoColor=white)                |
| Style    | ![CSS3](https://img.shields.io/badge/CSS3-1572B6?style=flat-square\&logo=css3\&logoColor=white)                   |
| Language | ![JavaScript](https://img.shields.io/badge/JavaScript-F7DF1E?style=flat-square\&logo=javascript\&logoColor=black) |

### Test · Quality · CI

| 구분         | 기술                                                                                                                           |
| ---------- | ---------------------------------------------------------------------------------------------------------------------------- |
| Test       | ![JUnit5](https://img.shields.io/badge/JUnit_5-25A162?style=flat-square\&logo=junit5\&logoColor=white)                       |
| Coverage   | ![JaCoCo](https://img.shields.io/badge/JaCoCo-89.26%25-brightgreen?style=flat-square)                                        |
| Code Style | ![Spotless](https://img.shields.io/badge/Spotless-Code_Formatting-4285F4?style=flat-square)                                  |
| CI         | ![GitHub Actions](https://img.shields.io/badge/GitHub_Actions-2088FF?style=flat-square\&logo=githubactions\&logoColor=white) |

### Collaboration

| 구분              | 도구                                                                                                                                                                                                 |
| --------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Version Control | ![Git](https://img.shields.io/badge/Git-F05032?style=flat-square\&logo=git\&logoColor=white) ![GitHub](https://img.shields.io/badge/GitHub-181717?style=flat-square\&logo=github\&logoColor=white) |
| Documentation   | ![Notion](https://img.shields.io/badge/Notion-000000?style=flat-square\&logo=notion\&logoColor=white)                                                                                              |
| Communication   | ![Slack](https://img.shields.io/badge/Slack-4A154B?style=flat-square\&logo=slack\&logoColor=white)                                                                                                 |

<br>

## 🏗️ 설계 및 협업 특징

### 모듈러 모놀리스 구조

기능을 회원·모집·그룹·일정·출석·활동 도메인으로 분리하되 하나의 Spring Boot 애플리케이션으로 구성했습니다.

각 도메인은 자신의 비즈니스 로직과 데이터 접근 책임을 가지며, 다른 도메인의 내부 구현에 대한 직접 의존을 최소화했습니다.

### 내부 계약 인터페이스 기반 연동

파트 간 기능 연동에는 내부 계약 인터페이스를 사용했습니다.

```text
모집 참가 승인
    ↓
그룹 생성·그룹원 등록 계약 호출
    ↓
그룹·일정 도메인에서 처리
```

이를 통해 호출하는 도메인은 상대 도메인의 구현 방식이 아니라 명시된 입력·출력 규약에 의존하도록 구성했습니다.

### 테스트와 코드 품질 자동화

* JUnit 5 기반 단위·통합 테스트 작성
* JaCoCo 기반 라인 커버리지 측정
* Spotless 기반 코드 포맷 통일
* Git Hook을 통한 커밋 전 포맷 적용
* GitHub Actions를 통한 Pull Request 테스트와 포맷 검사 자동화

<br>

## 📊 테스트 커버리지

업로드된 JaCoCo 리포트 기준 전체 라인 커버리지는 **89.26%**입니다.

| 지표          |       커버리지 |
| ----------- | ---------: |
| Line        | **89.26%** |
| Instruction |     87.36% |
| Branch      |     75.44% |
| Method      |     83.12% |
| Class       |     93.57% |

> 커버리지 수치는 현재 JaCoCo 리포트를 기준으로 작성되었습니다. 자동 연동 배지 적용 후에는 최신 빌드 결과를 기준으로 갱신됩니다.

<br>

## 👨‍💻 팀원 및 역할 분담

| 팀원      | 주요 역할                                                                         | GitHub                                       |
| ------- | ----------------------------------------------------------------------------- | -------------------------------------------- |
| **권혁준** | 그룹·일정 기능 및 파트 간 연동 설계<br>Back Office 백엔드·프론트엔드 구현<br>코드 품질·CI 자동화 구축<br>기능 QA | [@hyuckjoon9](https://github.com/hyuckjoon9) |
| **임수환** | 회원·인증·공통 기능 구현<br>사용자 프론트엔드 구현<br>API 요청·응답 명세 정리 및 파트 간 연동 조율<br>테스트·문서 작성   | [@soohwanlim](https://github.com/soohwanlim) |
| **장지원** | 모집글·참가 신청 기능 구현<br>JaCoCo 기반 테스트 커버리지 구축<br>주요 기능 병합 단계별 통합 QA<br>결함 관리 주도    | [@noodle0325](https://github.com/noodle0325) |
| **정자비** | 참석 응답·출석 체크·출석률 기능 구현<br>활동 기록·회고 리뷰 기능 구현<br>Back Office QA<br>UI·기능 불일치 검증  | [@mercy0704](https://github.com/mercy0704)   |

<br>

## 📚 프로젝트 문서

| 문서                                             | 설명                            |
| ---------------------------------------------- | ----------------------------- |
| [Part 1 — 회원·인증·공통](docs/part1-auth/)          | 회원, JWT 인증, 공통 응답·보안 설계       |
| [Part 2 — 모집글·참가 신청](docs/part2-recruitment/)  | 모집글, 참가 신청, 그룹 생성 연동          |
| [Part 3 — 그룹·일정](docs/part3-group/)            | 그룹 운영, 일정 관리, 파트 간 내부 계약      |
| [Part 4 — 참석·출석·활동 기록](docs/part4-attendance/) | 참석 응답, 출석, 출석률, 활동 기록·리뷰      |
| [Back Office — 서비스 운영 관리](docs/backoffice/)    | 회원 제재, 모집글 노출 관리, 그룹 운영 현황 조회 |
| [테스트 가이드](docs/testing/test-guide.md)          | 테스트 환경 설정 및 실행 방법             |
| [데이터베이스 스키마](sql/moigo_schema_seed.sql)        | 테이블 스키마 및 초기 데이터              |


<br>

## 🌿 브랜치 전략

```text
main
 └── develop
      ├── feature/*
      ├── fix/*
      ├── test/*
      └── docs/*
```

* `main`: 배포 가능한 안정 버전
* `develop`: 개발 기능 통합
* `feature/*`: 기능 개발
* `fix/*`: 버그 수정
* `test/*`: 테스트 작업
* `docs/*`: 문서 작업

모든 기능은 작업 브랜치에서 개발한 뒤 Pull Request, CI 검사와 코드 리뷰를 거쳐 `develop`에 병합했습니다. MVP 완료 후 `develop`을 `main`으로 병합합니다.

<br>

<div align="center">

### 🤝 모이고, 함께 운영하고, 기록하다.

</div>
