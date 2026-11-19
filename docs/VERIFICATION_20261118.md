# 2026-11-18 검증 결과

## 코드와 실행 결과

작업 기준 main: `c0821b66fff221e9f9d862170e264f908cf69510`.
작업 브랜치: `portfolio/verification-20261118`. 이 문서의 변경은 작업 사본이며 원격 main 반영과 새 CI 실행은 아직 완료하지 않았다.

| 확인 | 결과 |
| --- | --- |
| Java 17, Gradle 9.7.1 컴파일/bootJar | 성공 |
| 기존 Java/H2/웹 테스트 | 59개, 실패 0, 오류 0 |
| MySQL 8.4.13 실제 DB 테스트 | 9개, 실패 0, 오류 0 |
| Flyway V1 → Hibernate validate | 성공 |
| 수정/삭제와 분석 정합성 | 7개 성공, DB 트리거 오류에 의한 롤백 포함 |
| 전체 목록 SQL | 대조군 12개 → EntityGraph 2개 |
| PENDING 필터 SQL | 대조군 12개 → EntityGraph 2개 |
| 조회 결과/순서/총건수/다음 페이지 | 동일 결과, 다음 페이지 중복 없음 |
| Docker 재실행 | 이번 환경에서는 미실행. 11/17 사용자 검증 기록과 구분 |
| AWS 배포 | 미실행, 계정 연결과 자원 설정 필요 |
| 원격 push/PR | 연결 앱 쓰기 403, Git push 인증 불가로 미완료 |

조회 조건은 시설/점검 각각 30개, 서로 다른 시설, 첫 페이지 10건이다. 기존 저장소와 동일한 fetch 없는 JPQL을 대조군으로 삼고 변경한 Repository와 비교했다. 전후 캐시를 비웠다. SQL 왕복 수 비교이며 응답 시간 83% 개선 또는 실사용 대규모 트래픽 결과로 해석하지 않는다.

## 실제 DB 롤백 검증

테스트 전체를 트랜잭션으로 감싸지 않았다. 준비 행을 커밋한 뒤 서비스 메서드를 호출하고 새 JDBC 조회로 최종 DB 값을 확인했다. `BEFORE DELETE` 트리거가 `SQLSTATE 45000` 오류를 발생시켜 분석 삭제를 거부했을 때 점검 내용과 날짜 변경도 롤백됐다. 정상 수정은 대상 분석만 삭제하고 다른 점검/분석과 처리 상태를 보존했다.

## 실행 환경의 보완과 제한

Linux 작업 환경에 Java 17 JDK와 저장소 지정 Gradle 9.7.1을 별도 준비했다. 초기 Mockito 동적 attach가 제한되어 테스트 JVM 시작 때 Byte Buddy agent를 명시적으로 로드한 임시 Gradle init 설정으로 재실행했다. 이 환경 설정은 제품 코드에 추가하지 않았다.

Docker가 없어 MySQL 공식 8.4.13 바이너리를 검증 전용으로 실행했다. Unix socket 대신 루프백 TCP를 사용했고, 테스트와 동일 실행 세션에서 서버를 구동했다. 테스트 DB와 계정은 폐기 가능한 로컬 검증용이다. 실제 테스트 완료 후 서버를 종료했다.

따라서 “실제 MySQL에서 검증”은 확인했지만 “새 Docker CI 통과”나 “AWS에서 검증”은 별개다. 새 CI에는 동일 mysqlTest를 MySQL 8.4 서비스 컨테이너에서 실행하도록 구성했다.

## 재현과 근거

- `src/test/java/com/kjune922/waterapi/inspection/InspectionMutationMySqlTest.java`
- `src/test/java/com/kjune922/waterapi/inspection/InspectionQueryMySqlTest.java`
- `docs/MYSQL_VERIFICATION.md`
- `docs/evidence/verification-summary.txt`
- `build/reports/tests/test`, `build/reports/tests/mysqlTest`는 로컬 생성물이다.

이후 본인 PC에서 변경 적용 → CI 통과 → 직접 설명/재현 → AWS 배포 → 지원서 최종 반영 순으로 마무리한다.
