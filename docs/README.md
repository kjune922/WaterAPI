# WaterAPI 구현 설명

## 점검표는 무엇을 저장하나요?

새 일지는 시설을 선택하면 해당 유형의 고정 양식을 엽니다. 각 항목을 직접 선택하고 ‘조치 필요’에는 사유를 적습니다. DB에는 작성 당시 유형과 양식 버전, 항목의 순서·코드·이름·답변·사유를 저장합니다. 수정 화면은 이 값을 읽으므로 나중에 시설 유형이 바뀌어도 과거 양식이 유지됩니다. 기존 자유서술형 일지는 기존 원문으로 조회·수정·분석할 수 있습니다. [양식 항목](../src/main/java/com/kjune922/waterapi/inspection/ChecklistItem.java) · [Flyway V3](../src/main/resources/db/migration/V3__inspection_checklist.sql)

## AI에는 어떤 값이 전달되나요?

서버가 DB에 저장된 항목과 결과, 사유, 특이사항, 실시한 조치를 모아 요청합니다. 점검자 이름은 제외합니다. ‘미점검’과 ‘해당 없음’도 입력에 그대로 남습니다. 전부 미점검·해당 없음이고 구체적인 관찰도 없으면 일지는 저장하고 분석 요청 때 ‘입력 보완 필요’를 표시합니다. 테스트용 Stub은 정해진 분석값을 반환합니다. [분석 입력 구성](../src/main/java/com/kjune922/waterapi/analysis/AnalysisInput.java)

## 늦게 도착한 응답은 어떻게 처리하나요?

AI를 호출하기 전에 일지와 답변을 한 번에 읽고 그때의 수정 번호를 기억합니다. 외부 응답을 기다리는 동안에는 일지를 잠그지 않습니다. 응답이 오면 짧은 트랜잭션에서 일지 행을 잠그고 번호를 다시 읽습니다. 번호가 같을 때만 잠금을 풀기 전에 분석을 저장합니다. 일지 수정도 같은 행을 잠그고 번호를 올리므로, 번호 확인과 저장 사이에 수정이 끼어들지 않습니다. [분석 저장 코드](../src/main/java/com/kjune922/waterapi/analysis/AnalysisResultWriter.java)

## 확정 버튼을 누르면 어디에서 처리하나요?

Controller가 요약, 이상 유형, 위험도, 권장 조치를 입력받아 검사합니다. Service는 Repository를 통해 DB에 확정을 요청합니다. AI가 생성한 네 값은 그대로 두고, 같은 분석 행에 사용자가 확정한 네 값과 시각을 저장합니다. [확정 서비스](../src/main/java/com/kjune922/waterapi/analysis/AiAnalysisService.java)

PR #3 병합 전 사용자는 집 PC의 `feature/analysis-review`에서 AI 제안을 그대로 확정한 경우와 위험도·권장 조치를 바꿔 확정한 경우의 화면을 공유했습니다. 병합 후에는 로컬 `main`을 업데이트하고 작업 트리와 커밋을 확인했습니다.

## 두 요청이 동시에 도착하면 어떻게 되나요?

DB가 값을 쓰는 순간에 점검일지 ID, 분석 ID, ‘검토 대기’ 상태를 함께 확인합니다. 첫 요청이 한 행을 ‘확정’으로 바꾸면 다음 요청이 바꿀 행은 0개입니다. 이미 확정된 분석에는 409로 응답합니다. 일지를 수정하고 재분석한 뒤 예전 화면에서 확정해도 분석 ID가 달라 새 분석에 덮어쓸 수 없습니다. [조건부 갱신 코드](../src/main/java/com/kjune922/waterapi/analysis/AiAnalysisRepository.java)

## 처리 상태와 검토 상태는 어떻게 다른가요?

점검 처리 상태는 현장 업무가 얼마나 진행됐는지, 검토 상태는 사람이 분석 결과를 확정했는지를 나타냅니다. 점검일지 수정에 성공하면 연결된 분석 행을 지우므로 AI 원본과 확정본이 함께 사라집니다. 현장 처리 상태는 유지합니다. [점검일지 수정 서비스](../src/main/java/com/kjune922/waterapi/inspection/InspectionReportService.java)

## 목록과 대시보드의 위험도는 어떤 값인가요?

검토 대기 행은 AI 제안 위험도로, 확정 행은 사용자 확정 위험도로 표시하고 DB에서 같은 기준으로 필터링합니다. 분석 시각이 같으면 ID가 큰 순서로 표시합니다. AI가 주의를 제안하고 사용자가 위험으로 확정하면 상세에서 두 값을 비교할 수 있고, 대시보드에서는 ‘확정 결과 현황 → 위험’에 한 건이 잡힙니다. 숫자를 누르면 같은 조건의 목록이 열립니다. 대기 건수는 별도 카드에 표시합니다. [목록 조회](../src/main/java/com/kjune922/waterapi/analysis/AiAnalysisRepository.java) · [대시보드 집계](../src/main/java/com/kjune922/waterapi/dashboard/DashboardService.java)

## 목록에서는 왜 시설을 함께 조회하나요?

점검일지 10건을 가져온 뒤 시설명 10개를 따로 조회하면 화면 하나를 그리기 위해 DB를 반복해서 읽습니다. 목록 쿼리에 `EntityGraph`를 적용해 점검일지와 시설을 한 번에 읽고, 전체 건수만 따로 조회합니다. [목록 조회 코드](../src/main/java/com/kjune922/waterapi/inspection/InspectionReportRepository.java) · [측정 조건과 결과](VERIFICATION.md)
