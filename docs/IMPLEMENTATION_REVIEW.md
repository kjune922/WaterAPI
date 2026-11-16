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
- git diff --check: 통과.
- Gradle 테스트: 실행 시도했으나 배포 파일 다운로드가 Network is unreachable로 실패. 테스트 통과를 확인하지 못함.
- Docker, MySQL 마이그레이션: 이 환경에서 실행 결과 미확인.
- GitHub CI: 테스트와 MySQL 기동 검증 작업을 추가. Git push 인증 실패 및 연결 앱의 쓰기 권한 403으로 업로드되지 않아 실행되지 않음.
- 실제 OpenAI 응답, AWS 배포: 미실행.
- 현재 AI 호출은 트랜잭션 내부에 있다. 동시 분석 시 중복 저장은 DB UNIQUE로 막지만, 외부 API 중복 호출과 일지 수정 경쟁은 아직 해결하지 않았다.
- 공개 서비스용 인증, CSRF 보호, 호출 제한은 아직 미구현. 현재 배포 구성은 접근을 제한한 개인 데모 범위로 사용한다.
- 기존 application-secret.properties가 Git에 추적되고 있었다. 현재 파일 삭제만으로 과거 노출이 없어지지 않는다. 사용했던 실제 DB 비밀번호는 교체해야 한다.

지원서에는 아직 'AWS 운영 완료', '동시성 해결', '테스트 전체 통과'라고 쓰지 않는다.


## 7. 다운로드한 소스를 기존 Git 저장소에 반영하기
압축에는 Git 이력과 개인 설정 파일이 포함되어 있지 않다.
기존 로컬 저장소의 미커밋 작업을 먼저 보관한 뒤 별도 브랜치에서 변경을 비교한다.
    git switch -c portfolio/dataspira-ready
압축의 WaterAPI 폴더 내용을 기존 저장소에 복사한다. 기존 .git 폴더는 그대로 둔다.
application-secret.properties는 압축에 없으므로 기존 파일이 추적 중이면 다음 명령으로 추적만 해제한다.
    git rm --cached --ignore-unmatch src/main/resources/application-secret.properties
기존 로컬 파일은 개인 보관할 수 있으나 이번 설정은 환경변수를 사용한다.
    git diff --check
    ./gradlew test
    git add .
    git commit -m "Prepare AI validation and container verification"
    git push -u origin portfolio/dataspira-ready
Windows PowerShell에서는 ./gradlew 대신 .\gradlew.bat를 사용할 수 있다.
GitHub Actions에서 두 작업의 결과를 확인한 뒤 main 반영을 결정한다.
