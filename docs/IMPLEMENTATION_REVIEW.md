# 이번 변경을 읽는 순서

이 문서는 구현 내용과 검증 상태를 구분한다. 아래 변경은 AI 도움을 받아 작성했으며, 직접 실행하고 설명할 수 있게 확인한 뒤 지원서에 반영한다.

## 1. AI 응답은 왜 검사하는가?
읽을 파일: src/main/java/com/kjune922/waterapi/analysis/AiAnalysisService.java

외부 AI가 JSON을 반환해도 빈 문자열이나 DB 길이 제한을 넘긴 값일 수 있다.
validateResponse는 위험도 존재 여부, 요약 500자, 이상 유형 100자, 권장 조치 1000자 제한을 검사한다.
실패하면 save를 호출하지 않고 502 오류 화면으로 전달한다.
검증 코드는 실제 AI와 Stub 모두에 적용된다.

테스트: AiAnalysisServiceTest의 AI 호출 실패, null 응답, 빈 문자열, 길이 초과 사례.
기존 성공, 중복 분석 차단 테스트와 비교해서 읽는다.

## 2. 외부 호출을 얼마나 기다리는가?
읽을 파일: config/OpenAiConfig.java

연결 대기는 5초, HTTP 응답 대기는 30초로 제한했다.
자동 재시도는 추가하지 않았다. 실패 후 새 요청을 보내면 유료 호출이 추가될 수 있다.
오류 로그에서 외부 응답 본문을 포함할 수 있는 예외 전체 출력을 제거했다.

## 3. 설정은 어떻게 나누었는가?
- 기본: H2 파일 DB, 고정된 Stub 응답, 외부 API 키 불필요.
- mysql,stub: 기존 로컬 MySQL 개발, Hibernate update.
- prod,stub: MySQL, Flyway 생성, Hibernate validate, 유료 호출 없음.
- prod,openai: 위 DB 구성에 실제 OpenAI 연결. 키 필요.

Stub 결과는 실제 AI 추론 결과가 아니다. 데모 설명에도 구분한다.

## 4. 스키마를 왜 파일로 관리하는가?
읽을 파일: src/main/resources/db/migration/V1__initial_schema.sql

시설, 점검일지, 분석 결과를 외래키로 연결한다.
분석 결과는 점검일지당 하나만 허용하는 UNIQUE 제약이 있다.
프로덕션 프로필은 Hibernate의 자동 테이블 변경을 끄고 스키마 일치 여부만 검사한다.
이 마이그레이션은 새 빈 MySQL DB용이다. 기존 DB에 baseline을 임의로 적용하지 않는다.
기존 데이터 이전은 백업과 스키마 비교 후 별도로 진행한다.

## 5. 직접 실행
Docker Desktop이 실행 중인 로컬 환경에서:
    cp .env.example .env
.env에 로컬용 비밀번호 두 개를 설정하고:
    docker compose up --build -d
    curl http://localhost:8080/actuator/health

브라우저 http://localhost:8080 에서 시설 등록 → 점검 등록 → 분석 → 내용 수정 → 재분석을 확인한다.
포트는 로컬 PC에만 열리고 DB 포트는 외부로 공개하지 않는다.
종료:
    docker compose down
위 명령은 DB 볼륨을 보존한다. down -v는 데이터까지 삭제하므로 초기화할 때만 사용한다.

## 6. 현재 검증 상태와 남은 과제

2026-11-17에 로컬 Docker/MySQL, 실제 OpenAI, 백업 복원, 이전 이미지 복구를 확인한 기록은 [로컬 실행 검증](로컬실행검증.md)에 있다. 초기 작성 시의 네트워크 오류는 당시 상태이며 현재 결과와 혼동하지 않는다.

2026-11-18 코드 변경과 재실행 결과는 [최신 검증 결과](VERIFICATION_20261118.md)를 기준으로 한다. GitHub의 이전 성공 실행과 이번 작업 사본의 테스트 결과도 구분한다.

- 현재 AI 호출은 트랜잭션 내부에 있다. 동시 분석 시 중복 저장은 DB UNIQUE로 막지만 외부 API 중복 호출과 일지 수정 경합은 해결하지 않았다.
- 인증, 고객사별 권한, 호출 제한은 미구현이다. AWS 데모는 SSM 포트 포워딩으로 접근한다.
- 과거 secret 파일 삭제만으로 노출된 값이 무효화되지는 않는다. 이전에 사용한 실제 비밀번호 교체 여부는 별도로 관리한다.
- 실제 AWS 배포는 미완료다. [주말 실행안](AWS_WEEKEND.md)의 완료 기록을 확보한 뒤 지원서에 반영한다.

## 7. 이번 작업을 기존 저장소에 적용

전체 저장소를 덮어쓰지 않고 제공한 `WaterAPI.patch`를 별도 브랜치에 적용한다. 패키지의 적용 안내에서 기준 커밋과 `git apply --check`를 확인한다. 로컬 수정이 있으면 먼저 별도 커밋 또는 보관하고 강제 초기화하지 않는다.

적용 후 Java 테스트, MySQL 검증, Docker smoke를 실행하고 결과를 확인한 다음 main 반영을 결정한다. 본인이 코드 흐름을 설명할 수 있도록 [학습 인수인계](LEARNING_HANDOFF.md)의 순서대로 읽는다.
