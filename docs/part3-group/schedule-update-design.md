# 일정 수정 설계

## API 선택

일정 편집은 상세 화면의 수정 가능한 필드를 모두 저장하는 유스케이스이므로 `PUT` 전체 교체를 사용한다. 부분 수정은 누락과 명시적 `null`을 구분할 공통 방식이 없고 불필요한 역직렬화 계층이 필요해 채택하지 않았다.

상세 필드·오류는 [`api.md`](api.md)를 따른다. 클라이언트는 유지할 선택값도 다시 보내야 하며, 빠진 선택값은 `null`로 교체된다.

## 변경과 보존

- 변경: `title`, `scheduledAt`, `location`, `onlineLink`, `content`, `materials`, `updatedAt`
- 보존: `id`, `studyGroup`, `creatorId`, `responseDeadline`, `createdAt`

응답 마감은 참석 응답 정책과 연결되므로 전용 PATCH로만 변경한다.

## 권한과 시간

- 활성 그룹의 활성 `LEADER`·`MANAGER`만 수정한다. 등록자 여부는 권한에 영향을 주지 않는다.
- 기존 일정과 새 `scheduledAt`은 모두 `now`보다 미래여야 한다.
- 기존 `responseDeadline`이 있으면 새 `scheduledAt`보다 같거나 빨라야 한다.
- 이미 지난 응답 마감은 값을 보존하는 한 허용한다.

검증 순서는 그룹 → 그룹원 → 탈퇴 → 종료 그룹 → 역할 → 그룹에 속한 일정 → 기존 일정 시작 여부 → 새 시간이다. 기존 일정이 시작됐으면 `SCHEDULE_UPDATE_NOT_ALLOWED`, 새 시간 관계가 틀리면 `INVALID_SCHEDULE_TIME`이다.

## 구현 경계

- Controller는 principal ID, 경로 식별자와 검증된 DTO만 전달한다.
- DTO는 생성 요청과 같은 문자열 정규화·길이를 적용하되 `responseDeadline`을 포함하지 않는다.
- Service는 쓰기 트랜잭션에서 주입된 `Clock`의 동일한 `now`로 검증한다.
- Entity 변경 메서드는 수정 대상과 `updatedAt`만 바꾸며 JPA 변경 감지를 사용한다.
- 기존 그룹 제한 단건 Repository 조회를 재사용한다.
- 버전 컬럼이 없으므로 동시 수정은 마지막 커밋이 반영되는 기존 방식을 유지한다.

## 검증 요점

- 역할·그룹 상태·그룹 소속·기존 및 새 시간 경계를 구분한다.
- 수정 후 등록자·마감·생성 시각이 보존되고 실패 시 Entity가 바뀌지 않는다.
- 실제 JPA 변경 감지와 HTTP `200 ApiResponse<ScheduleResponse>` 계약을 검증한다.
