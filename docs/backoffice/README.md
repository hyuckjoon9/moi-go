# Back Office 문서 안내

Back Office 작업은 이 문서를 시작점으로 사용한다. 현재 구현 상태를 먼저 확인하고, 변경 목적에
맞는 문서만 이어서 읽는다.

| 문서 | 용도 | 읽는 시점 |
| --- | --- | --- |
| [context.md](context.md) | 현재 구현 상태, 다음 작업, 개발 DB 기준 | 모든 Back Office 작업 시작 전 |
| [feature-spec.md](feature-spec.md) | 제공 기능과 범위 | 기능을 추가·변경할 때 |
| [architecture.md](architecture.md) | 모듈 경계, 상태 변경, 화면 소유권 | 서버·화면 구조를 변경할 때 |
| [api.md](api.md) | 관리자 HTTP 계약 | Controller·화면 API를 변경할 때 |
| [integration-guide.md](integration-guide.md) | 기존 코드·`develop` 통합 계약과 검증 | 병합·PR 갱신 전 |

## 개발 DB

Back Office를 포함한 개발 DB 초기화는
[`sql/moigo_schema_seed.sql`](../../sql/moigo_schema_seed.sql)을 사용한다. 이 파일은 테이블을
삭제하고 다시 만드는 개발 전용 시드이므로 기존 데이터를 보존해야 하는 DB에는 실행하지 않는다.

## 검증

```bash
SPRING_DATASOURCE_USERNAME=<user> \
SPRING_DATASOURCE_PASSWORD=<password> \
./gradlew test
```
