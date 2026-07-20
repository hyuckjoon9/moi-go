# 스터디 그룹 API

## 엔드포인트 목록

그룹 생성은 외부 HTTP 엔드포인트가 아니라 Part2가 호출하는 내부 서비스 계약으로 먼저
구현한다. 그룹 홈과 일정 엔드포인트는 해당 기능 구현 시 추가한다.

## 내부 서비스 계약

### 모집 결과 기반 그룹 생성

- 호출자: Part2 모집·신청 유스케이스
- 진입점: `StudyGroupCreationService.create(CreateStudyGroupCommand)`
- 반환값: 생성되었거나 이미 존재하는 그룹의 `Long` ID

| 입력 | 타입 | 규칙 |
| --- | --- | --- |
| `postId` | `Long` | 필수, 그룹 생성의 멱등 키 |
| `groupName` | `String` | 필수, 양끝 공백 제거 후 빈 값 불가 |
| `leaderUserId` | `Long` | 필수, 최초 `LEADER` |
| `approvedUserIds` | `List<Long>` | 필수 목록, 빈 목록 허용, null 원소 불가 |

- 같은 `postId` 재요청은 최초 그룹과 그룹원을 유지하고 기존 그룹 ID를 반환한다.
- 모집장은 `LEADER`, 중복과 모집장을 제거한 승인 회원은 `MEMBER`로 등록한다.
- 그룹과 초기 그룹원은 하나의 트랜잭션에서 생성한다.
- Part2가 모집글과 사용자 식별자의 유효성을 보장한다.
- Part3는 Part1·Part2 Repository를 직접 조회하지 않는다.
