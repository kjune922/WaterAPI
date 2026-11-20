# WaterAPI AWS 실행 검증, 2026-11-19

2026-11-19의 실행 로그와 화면, 직접 확인한 기능 결과를 정리했습니다. 11월 20일에는 실습 종료 후 Elastic IP 반납까지 확인했습니다. 상시 운영 상태를 보장하는 문서가 아니라 해당 시점의 수동 검증 기록입니다.

## 실행 버전과 환경

| 항목 | 확인한 값 |
| --- | --- |
| 소스 커밋 | `943ef8799dc3016f4a3d7de507808c39aaf5434c` |
| ECR 이미지 태그 | 소스 커밋 전체 SHA |
| 이미지 digest | `sha256:1b18cd04683f3302e9c50fd74f48fef931e19ceec929bd128ff681868246e319` |
| 애플리케이션 | Java 17, Spring Boot 4.1.1, `prod,stub` |
| 실행 서버 | 서울 리전 EC2, Ubuntu, t3.micro, Docker Compose |
| 데이터베이스 | 비공개 RDS MySQL 8.4.11, db.t4g.micro, Single-AZ, 암호화된 gp3 20 GiB |
| 연결 | `sslMode=VERIFY_IDENTITY`, RDS CA truststore, DB 사용자 분리 |
| 접근 | EC2의 `127.0.0.1:8080`, SSM 포트 포워딩으로 PC의 `localhost:18080`에서 확인 |

Windows PC에서 이미지를 ECR에 업로드하고 EC2에서 pull한 뒤 Compose로 실행했습니다. 저장소의 [AWS Compose 템플릿](../deploy/compose.aws.yaml)을 기반으로 EC2에 실행 설정과 인증서 파일을 준비했습니다. 비밀번호와 실제 접속 정보는 실행 환경에서 관리했습니다.

CloudGuard와 같은 EC2와 RDS 인스턴스를 사용하되 `waterapi`와 `cloudguard` DB, 앱 계정을 분리했습니다. 두 앱은 메모리와 8080 포트를 고려해 한 번에 하나씩 실행했습니다.

## 확인한 결과

| 단계 | 관찰한 결과 |
| --- | --- |
| RDS 연결 | HikariPool 연결 생성 성공, MySQL 8.4 인식 |
| 스키마 초기화 | Flyway V1 적용 성공, JPA 초기화와 앱 기동 성공 |
| 헬스체크 | `GET /actuator/health`에서 `status: UP` |
| 화면 접속 | SSM 포트 포워딩을 통해 대시보드 접근 |
| 업무 흐름 | 시설 1건, 점검일지 1건 등록, Stub 분석 1건 생성 |
| 상태 조회 | 처리 완료 1건, 위험도 CAUTION 1건 확인 |
| 수정과 재분석 | 점검 내용 수정 후 이전 분석 무효화, 수정된 내용으로 재분석 확인 |
| 컨테이너 교체 | 컨테이너 ID 변경, 다시 health UP, 데이터 유지 확인 |

기능 검증은 화면과 직접 조작 결과로, 컨테이너 교체는 전후 ID와 헬스체크 및 데이터 조회로 확인했습니다. 모든 수동 조작의 원본 캡처를 저장소에 포함한 것은 아닙니다.

컨테이너 ID 앞부분:

| 교체 전 | 교체 후 |
| --- | --- |
| `0e07d5d4` | `920a6852` |

헬스체크 응답:

```json
{"groups":["liveness","readiness"],"status":"UP"}
```

EC2 안에서 확인한 읽기 전용 요청:

```bash
curl --fail --show-error --max-time 10 \
  http://127.0.0.1:8080/actuator/health
```

앱 컨테이너 교체 후 데이터가 남는 것은 앱 외부의 RDS에 저장했기 때문입니다. 이 확인은 RDS 백업 복원이나 서로 다른 앱 이미지 버전 간 롤백 검증과는 다릅니다.

## CI와 수동 배포

소스 커밋 `943ef87`의 [Verify WaterAPI 실행](https://github.com/kjune922/WaterAPI/actions/runs/44434881044)에서 다음 세 작업이 성공했습니다.

| CI 작업 | 확인 범위 |
| --- | --- |
| `test` | Java 테스트, bootJar 생성 |
| `mysql-test` | 실제 MySQL 서비스 컨테이너에서 수정, 삭제, 롤백, 조회 테스트 |
| `mysql-smoke` | Docker Compose 기동, Flyway, JPA 스키마 검증, health UP |

11월 18일 작업 사본에서는 Java 테스트 59개, MySQL 테스트 9개를 확인했습니다. 당시 [검증 기록](VERIFICATION_20261118.md)의 원격 반영과 AWS 미완료 표시는 그 날짜의 상태입니다. 이후 main 반영과 위 CI, 11월 19일 수동 AWS 실행을 확인했습니다.

이 CI가 AWS에 이미지를 업로드하거나 서버를 자동 교체한 것은 아닙니다.

## 실습 종료

실습 종료 후 EC2와 RDS 중지 완료를 확인했습니다. 11월 20일에는 연결된 Elastic IP를 반납하고 서울 리전의 할당 목록이 비어 있는 것을 확인했습니다. 이 기록은 계정 전체 비용이 0이라는 의미가 아닙니다.

상시 공개 데모는 운영하지 않습니다. 다시 실행할 때는 RDS 상태, EC2와 SSM 상태, 해당 앱의 실행 여부를 확인해야 합니다.

## 검증 범위

- 이번 AWS 실행은 `prod,stub`입니다. Stub의 결과를 실제 AI 추론 결과로 해석하지 않습니다.
- 실제 OpenAI 호출은 [11월 17일 로컬 실행 기록](로컬실행검증.md)에 있습니다. AWS에서 실제 OpenAI API까지 호출한 결과는 아닙니다.
- AWS 템플릿은 JVM 전역 truststore를 RDS CA 파일로 지정합니다. AWS에서 OpenAI를 활성화하려면 외부 HTTPS용 공개 CA 신뢰도 유지되는지 연결 설정을 다시 확인해야 합니다.
- 별도 DB에 SQL 덤프 복원, 이전 이미지로의 롤백은 로컬에서 확인한 결과입니다. 이번 AWS 실습에서 RDS 복원, 무중단 배포, 자동 복구를 검증하지 않았습니다.
- 부하 성능과 동시 분석 요청, 수정과 분석의 경쟁 조건은 이번 검증 대상이 아닙니다.

