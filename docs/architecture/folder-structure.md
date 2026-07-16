# 목표 아키텍처 폴더 구조

이 문서는 Moi-Go 백엔드의 목표 패키지 구조와 각 구성 요소의 책임을 정의한다. 도메인별 패키지를 최상위에 배치하고, 각 도메인 안에서는 API 계층부터 영속 계층까지 동일한 구조를 유지한다.

## 전체 구조

```text
src/
├── main/
│   ├── java/com/mycom/myapp/
│   │   ├── MoiGoApplication.java
│   │   ├── activity/
│   │   │   ├── controller/ActivityController.java
│   │   │   ├── dto/
│   │   │   │   ├── request/
│   │   │   │   │   ├── ActivityRecordCreateRequest.java
│   │   │   │   │   └── ActivityReviewCreateRequest.java
│   │   │   │   └── response/ActivityRecordResponse.java
│   │   │   ├── entity/
│   │   │   │   ├── ActivityRecord.java
│   │   │   │   └── ActivityReview.java
│   │   │   ├── repository/
│   │   │   │   ├── ActivityRecordRepository.java
│   │   │   │   └── ActivityReviewRepository.java
│   │   │   └── service/ActivityService.java
│   │   ├── application/
│   │   │   ├── controller/JoinApplicationController.java
│   │   │   ├── dto/
│   │   │   │   ├── request/JoinApplicationCreateRequest.java
│   │   │   │   └── response/JoinApplicationResponse.java
│   │   │   ├── entity/
│   │   │   │   ├── ApplicationStatus.java
│   │   │   │   └── JoinApplication.java
│   │   │   ├── repository/JoinApplicationRepository.java
│   │   │   └── service/JoinApplicationService.java
│   │   ├── attendance/
│   │   │   ├── controller/AttendanceController.java
│   │   │   ├── dto/
│   │   │   │   ├── request/
│   │   │   │   │   ├── AttendanceAnswerRequest.java
│   │   │   │   │   └── AttendanceCheckRequest.java
│   │   │   │   └── response/
│   │   │   │       ├── AttendanceSummaryResponse.java
│   │   │   │       └── MyAttendanceRateResponse.java
│   │   │   ├── entity/
│   │   │   │   ├── AttendanceAnswer.java
│   │   │   │   ├── AttendanceRecord.java
│   │   │   │   ├── AttendanceResponse.java
│   │   │   │   └── AttendanceStatus.java
│   │   │   ├── repository/
│   │   │   │   ├── AttendanceRecordRepository.java
│   │   │   │   └── AttendanceResponseRepository.java
│   │   │   └── service/AttendanceService.java
│   │   ├── auth/
│   │   │   ├── controller/AuthController.java
│   │   │   ├── dto/
│   │   │   │   ├── request/
│   │   │   │   │   ├── LoginRequest.java
│   │   │   │   │   └── ReissueRequest.java
│   │   │   │   └── response/TokenResponse.java
│   │   │   ├── entity/RefreshToken.java
│   │   │   ├── repository/RefreshTokenRepository.java
│   │   │   └── service/AuthService.java
│   │   ├── global/
│   │   │   ├── config/
│   │   │   │   ├── JpaConfig.java
│   │   │   │   └── SecurityConfig.java
│   │   │   ├── entity/BaseTimeEntity.java
│   │   │   ├── exception/
│   │   │   │   ├── BusinessException.java
│   │   │   │   ├── ErrorCode.java
│   │   │   │   └── GlobalExceptionHandler.java
│   │   │   ├── response/ApiResponse.java
│   │   │   └── security/
│   │   │       ├── CustomUserDetailsService.java
│   │   │       └── jwt/
│   │   │           ├── JwtAuthenticationFilter.java
│   │   │           ├── JwtProperties.java
│   │   │           └── JwtTokenProvider.java
│   │   ├── member/
│   │   │   ├── controller/MemberController.java
│   │   │   ├── dto/
│   │   │   │   ├── request/
│   │   │   │   │   ├── MemberCreateRequest.java
│   │   │   │   │   └── MemberUpdateRequest.java
│   │   │   │   └── response/MemberResponse.java
│   │   │   ├── entity/
│   │   │   │   ├── Member.java
│   │   │   │   ├── MemberRole.java
│   │   │   │   └── MemberStatus.java
│   │   │   ├── repository/MemberRepository.java
│   │   │   └── service/MemberService.java
│   │   ├── recruitment/
│   │   │   ├── controller/RecruitmentController.java
│   │   │   ├── dto/
│   │   │   │   ├── request/
│   │   │   │   │   ├── RecruitmentCreateRequest.java
│   │   │   │   │   └── RecruitmentUpdateRequest.java
│   │   │   │   └── response/RecruitmentResponse.java
│   │   │   ├── entity/
│   │   │   │   ├── RecruitmentPost.java
│   │   │   │   └── RecruitmentStatus.java
│   │   │   ├── repository/RecruitmentRepository.java
│   │   │   └── service/RecruitmentService.java
│   │   ├── schedule/
│   │   │   ├── controller/ScheduleController.java
│   │   │   ├── dto/
│   │   │   │   ├── request/ScheduleCreateRequest.java
│   │   │   │   └── response/ScheduleResponse.java
│   │   │   ├── entity/StudySchedule.java
│   │   │   ├── repository/StudyScheduleRepository.java
│   │   │   └── service/ScheduleService.java
│   │   └── study/
│   │       ├── controller/StudyGroupController.java
│   │       ├── dto/
│   │       │   ├── request/StudyGroupUpdateRequest.java
│   │       │   └── response/StudyGroupHomeResponse.java
│   │       ├── entity/
│   │       │   ├── GroupMember.java
│   │       │   ├── GroupRole.java
│   │       │   ├── GroupStatus.java
│   │       │   └── StudyGroup.java
│   │       ├── repository/
│   │       │   ├── GroupMemberRepository.java
│   │       │   └── StudyGroupRepository.java
│   │       └── service/StudyGroupService.java
│   └── resources/
│       ├── application-test.yml
│       └── application.properties
└── test/
    └── java/com/mycom/myapp/
        ├── MoiGoApplicationTests.java
        └── support/
            ├── config/TestSecurityConfig.java
            └── fixture/MemberFixture.java
```

## 패키지 구성 원칙

### 도메인 패키지

비즈니스 기능은 `activity`, `application`, `attendance`, `auth`, `member`, `recruitment`, `schedule`, `study`처럼 도메인별로 분리한다. 다른 도메인의 내부 구현에 직접 의존하지 않고, 필요한 협력은 서비스 계층에서 명확하게 수행한다.

각 도메인은 가능한 한 같은 하위 구조를 따른다.

| 패키지 | 책임 |
| --- | --- |
| `controller` | HTTP 요청을 받고 요청 DTO를 검증한 뒤 서비스 호출 결과를 응답으로 반환한다. 비즈니스 규칙은 두지 않는다. |
| `service` | 유스케이스를 수행하고 트랜잭션 경계를 관리한다. 도메인 규칙과 여러 리포지토리의 협력을 조정한다. |
| `repository` | JPA 등 영속성 기술을 통해 엔티티를 조회·저장한다. |
| `entity` | 데이터베이스 테이블과 도메인 상태를 표현한다. 상태 변경과 도메인 규칙은 엔티티에 가깝게 둔다. |
| `dto/request` | API 입력 전용 객체다. 엔티티를 요청 본문으로 직접 노출하지 않는다. |
| `dto/response` | API 출력 전용 객체다. 필요한 데이터만 외부에 전달한다. |

## 공통 모듈

`global`은 특정 도메인에 속하지 않는 공통 기능을 둔다.

| 패키지 | 책임 |
| --- | --- |
| `config` | JPA, Spring Security 등 애플리케이션 공통 설정을 관리한다. |
| `entity` | 생성·수정 시각처럼 여러 엔티티가 함께 사용하는 기반 엔티티를 제공한다. |
| `exception` | 비즈니스 예외, 오류 코드, 전역 예외 처리를 일관되게 제공한다. |
| `response` | 공통 API 응답 형식을 제공한다. |
| `security` | 사용자 인증 정보 조회 및 JWT 기반 인증 처리를 담당한다. |

## 도메인별 책임

| 도메인 | 책임 |
| --- | --- |
| `activity` | 활동 기록과 활동 리뷰를 생성·조회·관리한다. |
| `application` | 스터디 가입 신청과 신청 상태를 관리한다. |
| `attendance` | 출석 확인, 응답, 출석 기록 및 출석률 요약을 관리한다. |
| `auth` | 로그인, 토큰 재발급, 리프레시 토큰을 관리한다. |
| `member` | 회원 정보, 역할, 회원 상태를 관리한다. |
| `recruitment` | 스터디 모집 게시글과 모집 상태를 관리한다. |
| `schedule` | 스터디 일정을 생성·조회·관리한다. |
| `study` | 스터디 그룹, 그룹 구성원, 그룹 역할과 상태를 관리한다. |

## 요청 처리 흐름

일반적인 요청은 다음 순서로 처리한다.

```text
Client → Controller → Request DTO 검증 → Service → Repository → Entity/Database
                                      ↓
Client ← Response DTO 변환 ← Service 결과 ←─────────────────────────
```

예외는 `GlobalExceptionHandler`에서 공통 응답 형식으로 변환한다. 인증이 필요한 요청은 컨트롤러에 도달하기 전에 JWT 인증 필터가 처리한다.

## 리소스와 테스트

- `src/main/resources/application.properties`: 기본 애플리케이션 설정을 둔다.
- `src/main/resources/application-test.yml`: 테스트 프로파일 설정을 둔다.
- `src/test/java/.../support/config`: 테스트 전용 설정을 둔다.
- `src/test/java/.../support/fixture`: 테스트 데이터 생성을 재사용 가능한 픽스처로 관리한다.
- 각 도메인의 테스트는 해당 도메인 패키지와 대응하는 위치에 배치한다.

## 의존성 규칙

1. `controller`는 `service`와 DTO에만 의존한다.
2. `service`는 엔티티·리포지토리·다른 도메인의 공개 서비스에 의존할 수 있다.
3. `repository`는 엔티티의 영속성 처리만 담당하며, 서비스나 컨트롤러에 의존하지 않는다.
4. 엔티티는 API DTO 또는 컨트롤러에 의존하지 않는다.
5. 공통 기능은 `global`에 두되, 특정 도메인의 비즈니스 규칙은 `global`로 이동하지 않는다.
