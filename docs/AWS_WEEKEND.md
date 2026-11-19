# 주말 AWS 배포 실행안

상태: 실행 구성과 절차 준비. 실제 AWS 배포, RDS 연결, AWS에서의 복구는 아직 미검증이다.

## 이번 범위

서울 리전의 단일 EC2 Docker 앱 + 비공개 RDS MySQL 데모. `prod,stub`으로 먼저 확인한다. 인증 없는 앱이므로 8080을 인터넷에 공개하지 않고 SSM 포트 포워딩으로 접속한다. 다중 고객사, 고가용성, 오토스케일링은 이번 범위에 포함하지 않는다.

선행 조건: 새 Java/MySQL 테스트 통과, 본인 AWS 계정 접속, 허용 실습 예산과 자원 종료 시각 확정. 현재 대화 환경에는 AWS 실행 권한과 계정 설정이 없다. 비밀키를 대화에 붙여 넣지 않는다.

## 1. 자원 준비

- ECR 저장소 `waterapi`, 커밋 SHA별 이미지 태그.
- EC2 Amazon Linux 2023 x86_64, Docker/Compose, SSM Agent. 앱 빌드는 로컬에서 한다.
- EC2 역할: SSM 관리, 해당 ECR 이미지 읽기. 인바운드 22/8080 공개 불필요. 외부 HTTPS 통신 경로는 필요하다.
- RDS MySQL 8.4, 같은 VPC의 private DB subnet group, Public access No.
- RDS 보안 그룹: 3306의 소스는 EC2 보안 그룹만 허용.
- 빈 DB `waterapi`, 앱 전용 계정 준비. Flyway DDL 실행에 필요한 권한 범위를 확인한다.
- EC2/RDS/EBS/공인 IPv4 비용을 생성 직전 콘솔에서 확인하고 종료일을 기록한다. 무료라고 가정하지 않는다.

## 2. 검증한 코드 이미지 업로드

로컬에 AWS CLI 로그인과 Docker가 필요하다. 아래 값은 본인 계정으로 설정한다.

```bash
export AWS_REGION=ap-northeast-2
export AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
export ECR_REGISTRY="$AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com"
export IMAGE_TAG=$(git rev-parse HEAD)
aws ecr get-login-password --region "$AWS_REGION" | docker login --username AWS --password-stdin "$ECR_REGISTRY"
docker build --platform linux/amd64 -t "$ECR_REGISTRY/waterapi:$IMAGE_TAG" .
docker push "$ECR_REGISTRY/waterapi:$IMAGE_TAG"
docker logout "$ECR_REGISTRY"
```

## 3. EC2의 RDS 인증서와 환경 설정

SSM으로 EC2에 접속한다. Docker/Compose 설치는 AWS와 Docker의 현재 설치 문서를 따른다. 아래 파일은 저장소 밖에 둔다.

```bash
sudo install -d -m 750 -o "$(id -u)" -g "$(id -g)" /opt/waterapi
mkdir -p /opt/waterapi/certs
cd /opt/waterapi/certs
curl --fail --show-error https://truststore.pki.rds.amazonaws.com/ap-northeast-2/ap-northeast-2-bundle.pem -o rds-ca-bundle.pem
awk '/-----BEGIN CERTIFICATE-----/{n++; inside=1} inside{print > ("rds-ca-" n ".pem")} /-----END CERTIFICATE-----/{inside=0}' rds-ca-bundle.pem
mkdir -p roots
for cert in rds-ca-[0-9]*.pem; do
  subject=$(openssl x509 -in "$cert" -noout -subject -nameopt RFC2253 | sed 's/^subject=//')
  issuer=$(openssl x509 -in "$cert" -noout -issuer -nameopt RFC2253 | sed 's/^issuer=//')
  if [ "$subject" = "$issuer" ]; then cp "$cert" roots/; fi
done
docker run --rm -v /opt/waterapi/certs:/certs eclipse-temurin:17-jdk sh -c 'for cert in /certs/roots/rds-ca-[0-9]*.pem; do keytool -importcert -noprompt -alias "$(basename "$cert" .pem)" -file "$cert" -keystore /certs/rds-truststore.p12 -storetype PKCS12 -storepass changeit; done'
chmod 644 /opt/waterapi/certs/rds-truststore.p12
```

중간 인증서는 truststore에 등록하지 않고 self-signed 루트 인증서만 등록한다.

`changeit`은 공개 CA 인증서만 담긴 truststore의 무결성 비밀번호이며 DB 비밀번호가 아니다. 반복 생성 시 기존 truststore에 같은 alias를 다시 넣지 않는다.

`deploy/runtime.env.example`을 `/opt/waterapi/runtime.env`로 복사해 실제 RDS endpoint/DB 계정을 편집하고 `chmod 600`을 적용한다. JDBC `sslMode=VERIFY_IDENTITY`를 유지한다. `image.env`에는 `WATERAPI_APP_IMAGE=<ECR 주소>:<검증한 SHA>` 한 줄만 넣는다.

## 4. 실행과 확인

EC2에 적용한 저장소의 루트에서 ECR 로그인 후:

```bash
docker compose --env-file /opt/waterapi/image.env -f deploy/compose.aws.yaml pull
docker compose --env-file /opt/waterapi/image.env -f deploy/compose.aws.yaml up -d
curl --fail http://127.0.0.1:8080/actuator/health
docker compose --env-file /opt/waterapi/image.env -f deploy/compose.aws.yaml logs --tail=100
```

로컬 PC에 AWS CLI와 Session Manager plugin을 준비하고 본인 EC2 ID로:

```bash
aws ssm start-session --region ap-northeast-2 --target i-REPLACE --document-name AWS-StartPortForwardingSession --parameters '{"portNumber":["8080"],"localPortNumber":["8080"]}'
```

브라우저 `http://localhost:8080`에서 시설 등록 → 점검 등록 → Stub 분석 → 수정 → 분석 사라짐 → 재분석을 확인한다. 처리 상태는 수정 전후 동일해야 한다. 앱 컨테이너 재생성 후 데이터 유지도 확인한다.

## 5. 실패와 복구

헬스체크 실패 시 컨테이너 상태, 로그, SG/DNS, JDBC/TLS, Flyway 이력 순서로 확인한다. 잘못된 인증서 문제를 useSSL=false로 피하지 않는다.

첫 배포 성공 이미지 태그를 기록한 뒤 두 번째 이미지 교체를 실습한다. 실패하면 `image.env`를 이전 태그로 바꾸고 pull/up -d, health, 데이터 확인을 다시 수행한다. 이번 변경에는 DB 스키마 변경이 없다. 이미지 복구는 DB 복구가 아니며 무중단/자동 롤백이라고 표현하지 않는다.

## 완료 기록

| 확인 항목 | 실제 결과 기입 |
| --- | --- |
| 커밋, ECR 태그/다이제스트 | 미확인 |
| Java/MySQL CI URL | 미확인 |
| EC2/RDS 기동, 프로필, 시각 | 미확인 |
| Flyway 성공, health UP | 미확인 |
| 저장/조회/수정 후 분석 삭제 | 미확인 |
| 컨테이너 재생성 후 데이터 유지 | 미확인 |
| AWS 이전 이미지 복구 | 미확인 |
| 종료 일시, 잔여 자원 확인 | 미확인 |

토요일 오전 배포, 오후 검증/증거 정리와 지원서 확정을 목표로 한다. AWS 연결 문제로 일정이 밀리면 확인된 로컬 복구 경험으로 먼저 지원하고, 미완료 AWS 경험은 추가하지 않는다. 공고 마감은 11월 22일 23:59이며 11월 21일 제출을 목표로 한다.

## 참고

- https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/connect-with-systems-manager-session-manager.html
- https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/UsingWithRDS.SSL.html
- https://docs.aws.amazon.com/AmazonECS/latest/developerguide/create-container-image.html
