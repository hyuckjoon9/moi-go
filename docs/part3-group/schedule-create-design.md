# 일정 생성 설계

## 목적

활성 그룹의 `LEADER`·`MANAGER`가 미래 일정을 생성하고 저장된 전체 일정을 반환한다. API 필드·상태·오류는 [`api.md`](api.md), 매핑은 [`erd.md`](erd.md)를 기준으로 한다.

## 핵심 결정

- `location`과 `onlineLink`는 모두 선택이며, 둘 다 없으면 장소 미정이다.
- `onlineLink`는 URL만 강제하지 않고 온라인 접속 정보를 일반 문자열로 저장한다.
- 선택 문자열은 공백 제거 후 빈 값이면 `null`로 바꾼다.
- 별도의 일정 유형이나 장소 확정 상태 Enum은 두지 않는다.
- 현재 시각은 주입된 `Clock`에서 요청당 한 번 구한다.

## 계층 책임

- Controller: principal ID와 검증된 요청을 Service에 전달하고 `201 Created`를 반환한다.
- Request DTO: 문자열 정규화, 필수값과 길이를 검증한다.
- Service: 그룹·그룹원·역할·시간을 검증하고 쓰기 트랜잭션을 관리한다.
- Entity: `responseDeadline <= scheduledAt` 불변식을 보장하고 공개 setter를 두지 않는다.
- Repository: 일정을 저장하며 외부 사용자 대신 `creatorId`를 유지한다.

Service 검증 순서는 그룹 존재 → 그룹원 기록 → 탈퇴 → 종료 그룹 → 관리 역할 → 시간이다. `scheduledAt > now`이고, 마감이 있으면 `now < responseDeadline <= scheduledAt`이어야 한다.

## 검증 요점

- 활성 `LEADER`·`MANAGER`만 생성하고 `MEMBER`, 비회원, 탈퇴 회원, 종료 그룹은 거부한다.
- 장소 미정과 온·오프라인 조합, 문자열 경계와 정규화를 검증한다.
- 현재·과거 일정과 잘못된 마감을 거부하고 마감과 일정 시각이 같은 값은 허용한다.
- 저장된 모든 필드와 생성·수정 시각을 응답으로 변환한다.
