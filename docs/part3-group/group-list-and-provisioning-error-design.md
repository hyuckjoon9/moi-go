# 종료 그룹 이력 조회와 그룹원 추가 오류 분리 설계

## 목표

활성 그룹원은 `GET /api/groups/me`에서 운영 중(`ACTIVE`) 및 종료(`ENDED`) 그룹을 모두 조회하고, 종료 그룹에 새 그룹원을 추가하려는 내부 호출에는 일정 관리 오류와 구별되는 오류를 반환한다.

## 조회 정책

목록의 포함 여부는 호출자의 `group_members.status`만으로 결정한다.

| 그룹원 상태 | 그룹 상태 | 목록 포함 |
| --- | --- | --- |
| `ACTIVE` | `ACTIVE` | 포함 |
| `ACTIVE` | `ENDED` | 포함 |
| `WITHDRAWN` | `ACTIVE` 또는 `ENDED` | 제외 |

역할(`LEADER`, `MANAGER`, `MEMBER`)은 포함 여부에 영향을 주지 않는다. 기존 응답의 `status` 필드로 클라이언트가 진행 중 그룹과 종료 이력을 구분한다. 정렬은 호출자의 가입 시각 내림차순, 동일 시각에는 그룹원 ID 내림차순을 유지한다.

## 구현 경계

`GroupMemberRepository`의 내 그룹 목록 메서드에서 `StudyGroupStatus` 조건을 제거한다. `StudyGroupService`는 활성 그룹원 조건만 전달한다. 종료 그룹은 `@EntityGraph`로 함께 로드한 `studyGroup`의 상태를 응답 DTO에 그대로 매핑한다.

## 오류 정책

`ErrorCode.GROUP_ENDED`는 종료 그룹의 일정 관리 제한에 계속 사용한다. `StudyGroupProvisioningService.addMember()`가 종료 그룹을 만난 경우에는 새 `GROUP_MEMBER_ADD_NOT_ALLOWED` 오류를 사용한다.

| 오류 코드 | HTTP 상태 | 메시지 | 사용처 |
| --- | --- | --- | --- |
| `GROUP_ENDED` | 409 | 종료된 그룹에서는 일정을 관리할 수 없습니다. | 일정 생성·수정·마감 변경·삭제 |
| `GROUP_MEMBER_ADD_NOT_ALLOWED` | 409 | 종료된 그룹에는 새 그룹원을 추가할 수 없습니다. | Part2의 승인 흐름이 호출하는 `addMember()` |

`ErrorCode`는 `BusinessException`의 HTTP 상태와 메시지를 결정하는 공통 계약이다. 전역 예외 처리기는 enum 이름은 내려보내지 않고 상태와 메시지를 응답으로 변환한다.

## 검증

- 서비스 테스트: 활성 그룹원에게 `ENDED` 그룹도 반환되고 응답 상태가 `ENDED`인지 확인한다.
- Repository 테스트: `ACTIVE`와 `ENDED` 그룹은 포함하고 `WITHDRAWN` 소속은 제외하며 기존 정렬을 유지하는지 확인한다.
- 프로비저닝 서비스 테스트: 종료 그룹의 `addMember()`가 `GROUP_MEMBER_ADD_NOT_ALLOWED`를 던지는지 확인한다.
- API 문서: 내 그룹 목록의 반환 범위와 상태 필드 정의를 갱신하고, 내부 그룹원 추가 오류의 설계 문서를 갱신한다.
