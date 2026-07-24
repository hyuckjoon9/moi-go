# Back Office 구조

## 경계

관리자 기능은 `com.mycom.myapp.admin` 모듈에 둔다. 조회는 관리자 전용 DTO projection으로
처리하고, 상태 변경은 소유 도메인의 공개 관리 포트를 호출한다. 일반 사용자 Controller·Service에
관리자 분기를 추가하거나 관리자가 도메인 내부 Entity를 직접 수정하지 않는다.

```text
AdminController -> AdminService -> 관리 포트
                              -> AdminAuditLog
AdminQueryRepository -> JdbcClient projection -> 관리자 DTO
```

- `Admin*Controller`: `/api/admin/**` 요청, 검증, 인증 관리자 ID 전달
- `Admin*Service`: 상태 전이·트랜잭션·운영 이력 조정
- `Admin*QueryRepository`: 목록·상세·대시보드 집계 조회
- `AdminAuditLog`: 성공한 조치의 불변 이력

## 화면

`/backoffice/index.html`이 현재 운영 콘솔의 진입점이다. `backoffice.js`가 대시보드와 회원
관리 뷰를 전환한다.

- `backoffice.css`는 Back Office의 화면 토큰과 컴포넌트 스타일을 소유한다.
- `style.css`에 남아 있는 Back Office 호환 규칙은 같은 CSS 변수(`--bo-sidebar-width`,
  `--bo-layout-space`, `--bo-content-space`)를 사용해야 한다. 이 규칙이 별도 폭·여백을
  지정하면 화면 토큰을 덮어쓰므로 금지한다.
- 정적 CSS 변경 시 `index.html`의 stylesheet 버전 문자열을 함께 갱신해 브라우저 캐시가
  이전 레이아웃을 재사용하지 않게 한다.

## 보안과 상태 변경

`/api/admin/**`는 서버에서 `ADMIN` 권한을 검사한다. 정적 화면 접근 허용은 데이터 권한을
대체하지 않는다. 회원 상태 변경은 대상과 현재 상태를 조회한 뒤 도메인 포트를 호출하고, 변경된
경우에만 운영 이력을 저장한다. 충돌 시 `409`를 반환한다.

## 검증 기준

- Controller: 권한, 입력값, 응답 형식
- Service: 상태 전이, 멱등성, 이력 생성
- Repository: 필터, 관리자 우선 정렬, 페이지
- 화면 자산: CSS 토큰, 캐시 버전, 테마 대비, 운영 이력 표시
