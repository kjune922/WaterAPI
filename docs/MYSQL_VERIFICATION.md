# 수정 정합성과 목록 조회 검증

작성: 2026-11-18. 실행 결과는 `VERIFICATION_20261118.md`에서 관리한다.

## 검증할 업무 규칙

점검일지 수정 요청이 성공하면 내용과 날짜를 변경하고 기존 분석을 삭제한다. 같은 값을 다시 제출해도 분석을 무효화하는 현재 규칙을 유지한다. 처리 상태는 현장 작업 상태이므로 그대로 둔다.

`InspectionMutationMySqlTest`는 테스트 메서드에 `@Transactional`을 붙이지 않는다. 준비 데이터는 JDBC로 커밋하고, Spring 프록시를 통한 서비스 호출이 끝난 뒤 JDBC로 다시 조회한다. 영속성 컨텍스트의 객체를 보는 검사와 실제 커밋 이후 DB 검사를 구분한다.

- 성공: 내용/날짜 변경, 대상 분석 삭제, 다른 점검과 분석 보존, 처리 상태 유지.
- 실패: MySQL BEFORE DELETE 트리거가 실제 SQL 오류를 내도록 하고, 내용/날짜/분석이 모두 이전 값인지 확인.
- 점검 삭제: 분석과 점검 삭제, 시설과 다른 점검 보존. 분석 삭제 실패 시 두 기록 보존.
- 분석 없는 점검, 없는 점검, 동일 내용 수정도 확인.

## 조회 개선 범위

대상은 `/inspections` 목록의 `inspection.facility.name` 접근이다. 기본 LAZY 매핑은 유지하고 목록의 `findAll(Pageable)`, `findByProcessingStatus`에만 `@EntityGraph(attributePaths = "facility")`를 적용한다.

ManyToOne 단일 연관을 조인하므로 점검 한 건이 시설 여러 행으로 늘어나지 않는다. 컬렉션 fetch join의 페이지 제한 문제와 구분한다. count 조회는 유지한다. 다른 화면과 대시보드까지 최적화한 것은 아니다.

`InspectionQueryMySqlTest`의 조건:

| 항목 | 값 |
| --- | --- |
| 데이터 | 서로 다른 시설 30개, 점검 30개 |
| 상태 | PENDING 15개, COMPLETED 15개 |
| 조회 | 전체 목록, PENDING 필터 각각 |
| 페이지 | 첫 페이지 10건, 점검일/ID 내림차순 |
| 비교 | 기존 방식과 같은 JPQL 대조군, 변경한 Repository |
| 캐시 | 각 측정 전 flush/clear, Hibernate 통계 초기화 |
| 확인 | 결과/순서/총건수 동일, 다음 페이지 중복 없음 |
| 측정 SQL | MySQL 8.4.13에서 대조군 12개, 개선 2개 확인 (2026-11-18) |

Hibernate `getPrepareStatementCount()`로 목록, count, 시설 조회 SQL을 센다. 이 검증은 SQL 왕복 수를 비교하며 지연시간 개선율이나 대용량 처리량을 측정하지 않는다. 시설을 공유하는 실제 데이터에서는 추가 조회 수가 달라질 수 있다.

## 실행

GitHub Actions `mysql-test`는 매 실행마다 MySQL 8.4와 빈 `waterapi_verification` DB를 만든다. Flyway V1 실행 후 JPA validate 상태로 테스트한다. CI 전용 root 비밀번호는 폐기 가능한 서비스 컨테이너에만 사용하며 운영 계정이 아니다.

로컬 PowerShell에서 기존 업무 DB를 재사용하지 말고 검증 전용 MySQL을 준비한 뒤:

```powershell
$env:WATERAPI_TEST_DB_URL='jdbc:mysql://127.0.0.1:3307/waterapi_verification?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=Asia/Seoul'
$env:WATERAPI_TEST_DB_USERNAME='root'
# 비밀번호는 로컬 환경변수로만 설정
.\gradlew.bat clean test mysqlTest bootJar
```

테스트는 데이터를 지우므로 DB 이름이 `_verification`으로 끝나는지 검사한다. 트리거 테스트용 권한을 가진 전용 DB 계정이 필요하다. 일반 `test`는 mysql 태그를 제외하며 H2 테스트를 계속 실행한다.

결과: `build/reports/tests/test/index.html`, `build/reports/tests/mysqlTest/index.html`. 로그의 `QUERY_COMPARISON`과 XML 결과를 함께 보관한다.
