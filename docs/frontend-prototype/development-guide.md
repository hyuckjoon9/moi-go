# Frontend Prototype 개발 가이드

## 문서 목적

이 문서는 Moi-Go 백엔드 저장소 안에서 수업 시간에 사용한 HTML, CSS, JavaScript 방식으로
프론트 프로토타입을 만들 때의 기준을 정리한다. 현재 프로젝트 문서에는 프론트 전용 규칙이
없으므로, 이 문서를 프로토타입 작업의 기준으로 삼는다.

## 기본 방향

- 프론트 프로토타입은 백엔드 기능 검증과 팀 시연을 위한 화면 흐름 확인을 목표로 한다.
- 별도 프론트 프레임워크를 도입하지 않고 HTML, CSS, JavaScript만 사용한다.
- API 호출은 브라우저 `fetch`를 사용한다.
- 백엔드 기능 코드를 함께 수정하지 않는다. 필요한 API 변경은 해당 파트와 합의 후 별도 PR로 진행한다.
- 브랜치는 `develop`에서 생성하고, `feature/frontend-...` 이름을 사용한다.

## 권장 위치

수업 방식과 Spring Boot 프로젝트 구조를 고려하면 프로토타입은 다음 위치에 둔다.

```text
src/main/resources/static/
├── index.html
├── css/
│   └── style.css
└── js/
    ├── api.js
    ├── auth.js
    └── app.js
```

Spring Boot는 `src/main/resources/static` 아래 파일을 정적 리소스로 제공한다. 서버가
`localhost:8080`에서 실행 중이면 `index.html`은 다음 주소로 접근할 수 있다.

```text
http://localhost:8080/index.html
```

## `src/main/resources/static` 방식 검토

### 장점

- 별도 프론트 개발 서버 없이 Spring Boot 실행만으로 화면을 확인할 수 있다.
- 백엔드 API와 같은 origin에서 동작하므로 기본적인 CORS 문제를 피할 수 있다.
- 수업에서 사용한 HTML, CSS, JavaScript 방식과 잘 맞는다.
- 팀 시연용 MVP 화면을 빠르게 만들 수 있다.

### 단점

- 프론트 코드가 커지면 파일 구조와 상태 관리가 복잡해진다.
- Spring Boot 서버를 재시작하거나 브라우저 캐시를 비워야 변경 확인이 헷갈릴 수 있다.
- 장기 운영용 프론트로 확장하기에는 React, Vue 같은 별도 프론트 구조보다 불리하다.

### 결론

현재 단계에서는 `src/main/resources/static` 방식이 적절하다. 이번 작업은 완성형 프론트가
아니라 백엔드 API를 연결해보는 프로토타입이므로, HTML/CSS/JavaScript 정적 화면으로 먼저
진행한다. 추후 화면 규모가 커지거나 별도 배포가 필요해지면 `frontend/` 디렉터리와 프론트
빌드 도구 도입을 다시 검토한다.

## 화면 우선순위

현재 백엔드 API 기준으로 다음 순서로 화면을 만든다.

1. 로그인과 회원가입
2. 내 정보 조회와 수정
3. 모집글 목록, 상세, 작성
4. 그룹 홈 조회
5. 일정 목록, 상세, 작성, 수정
6. 출석 응답과 출석 현황

아직 API가 확정되지 않은 기능은 임시 더미 화면으로 표시하고, 실제 API 연결은 해당 파트의
PR이 `develop`에 머지된 뒤 진행한다.

## JavaScript 파일 역할

```text
api.js
```

- `fetch` 공통 함수를 둔다.
- API base URL, JSON header, 인증 header 처리를 담당한다.
- `ApiResponse` 형식의 `success`, `data`, `message`를 공통으로 처리한다.

```text
auth.js
```

- 로그인, 회원가입, 로그아웃, 토큰 저장과 삭제를 담당한다.
- access token과 refresh token은 프로토타입 단계에서 `localStorage`에 저장한다.
- 운영 서비스 기준으로는 보안상 재검토가 필요하다.

```text
app.js
```

- 화면 이벤트 처리와 DOM 갱신을 담당한다.
- API 세부 호출 로직을 직접 길게 넣지 않고 `api.js`, `auth.js` 함수를 호출한다.

## 인증 처리 규칙

로그인 성공 시 응답의 `accessToken`, `refreshToken`을 저장한다.

```javascript
localStorage.setItem("accessToken", result.data.accessToken);
localStorage.setItem("refreshToken", result.data.refreshToken);
```

인증이 필요한 API는 `Authorization` 헤더를 사용한다.

```javascript
headers: {
  "Content-Type": "application/json",
  "Authorization": `Bearer ${localStorage.getItem("accessToken")}`
}
```

로그아웃 시에는 서버에 refresh token 삭제 요청을 보낸 뒤 localStorage의 token도 삭제한다.

## API 응답 처리 규칙

백엔드 응답은 공통 `ApiResponse` 형식을 따른다.

```json
{
  "success": true,
  "data": {},
  "message": null
}
```

프론트에서는 `success=false`일 때 `message`를 화면에 표시한다. 프로토타입에서는 alert 또는
화면 상단 메시지 영역을 사용한다.

## 작업 규칙

1. `develop`을 최신화한다.
2. `feature/frontend-prototype` 또는 세부 기능명을 포함한 브랜치를 만든다.
3. `src/main/resources/static` 아래에 HTML, CSS, JavaScript 파일을 추가한다.
4. 백엔드 서버를 실행하고 `http://localhost:8080/index.html`에서 확인한다.
5. 변경 범위가 프론트 정적 파일과 문서에만 있는지 확인한다.
6. PR 대상은 `develop`으로 한다.

## 검증 방법

Spring Boot 서버 실행 후 브라우저에서 다음 흐름을 확인한다.

- 회원가입
- 로그인
- 내 정보 조회
- 모집글 목록 조회
- 인증이 필요한 API 호출 시 `Authorization` 헤더 포함 여부
- 로그아웃 후 token 삭제 여부

정적 파일만 수정한 경우에도 백엔드 컴파일이 깨지지 않는지 확인하기 위해 최소한 다음 명령을
실행한다.

```powershell
.\gradlew.bat spotlessCheck
```

필요하면 전체 테스트를 실행한다.

```powershell
.\gradlew.bat test
```

## Back Office 분리 원칙

운영자 기능은 사용자 서비스 화면에 섞지 않고 별도 Back Office 진입점에서 다룬다.

```text
src/main/resources/static/backoffice/index.html
```

주의사항:

- 사용자 서비스 화면에서 admin 권한으로 바로 로그인하는 흐름을 만들지 않는다.
- admin 계정, 비밀번호, 토큰을 HTML이나 JavaScript에 하드코딩하지 않는다.
- Back Office는 별도 URL과 별도 화면 구조를 가진다.
- 현재 백엔드에 운영자 전용 권한 API가 확정되지 않았으므로, Back Office는 조회 중심 프로토타입으로 유지한다.
- 실제 운영자 권한 검증은 백엔드에서 별도 API 또는 권한 정책이 확정된 뒤 연결한다.

현재 Back Office 프로토타입 URL은 다음과 같다.

```text
http://localhost:8080/backoffice/index.html
```