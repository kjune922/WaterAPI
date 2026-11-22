# WaterAPI 구현 설명

## 확정 버튼을 누르면 어디에서 처리하나요?

Controller가 요약, 이상 유형, 위험도, 권장 조치를 입력받아 검사합니다. Service는 Repository를 통해 DB에 확정을 요청합니다. AI가 생성한 네 값은 그대로 두고, 같은 분석 행에 사용자가 확정한 네 값과 시각을 저장합니다. [확정 서비스](../src/main/java/com/kjune922/waterapi/analysis/AiAnalysisService.java)

PR #3 병합 전 사용자는 집 PC의 `feature/analysis-review`에서 AI 제안을 그대로 확정한 경우와 위험도·권장 조치를 바꿔 확정한 경우의 화면을 공유했습니다. 병합 후에는 로컬 `main`을 업데이트하고 작업 트리와 커밋을 확인했습니다.

## 두 요청이 동시에 도착하면 어떻게 되나요?

DB가 값을 쓰는 순간에 점검일지 ID, 분석 ID, ‘검토 대기’ 상태를 함께 확인합니다. 첫 요청이 한 행을 ‘확정’으로 바꾸면 다음 요청이 바꿀 행은 0개입니다. 이미 확정된 분석에는 409로 응답합니다. 일지를 수정하고 재분석한 뒤 예전 화면에서 확정해도 분석 ID가 달라 새 분석에 덮어쓸 수 없습니다. [조건부 갱신 코드](../src/main/java/com/kjune922/waterapi/analysis/AiAnalysisRepository.java)

## 처리 상태와 검토 상태는 어떻게 다른가요?

점검 처리 상태는 현장 업무가 얼마나 진행됐는지, 검토 상태는 사람이 분석 결과를 확정했는지를 나타냅니다. 점검일지 수정에 성공하면 연결된 분석 행을 지우므로 AI 원본과 확정본이 함께 사라집니다. 현장 처리 상태는 유지합니다. [점검일지 수정 서비스](../src/main/java/com/kjune922/waterapi/inspection/InspectionReportService.java)

## 목록에서는 왜 시설을 함께 조회하나요?

점검일지 10건을 가져온 뒤 시설명 10개를 따로 조회하면 화면 하나를 그리기 위해 DB를 반복해서 읽습니다. 목록 쿼리에 `EntityGraph`를 적용해 점검일지와 시설을 한 번에 읽고, 전체 건수만 따로 조회합니다. [목록 조회 코드](../src/main/java/com/kjune922/waterapi/inspection/InspectionReportRepository.java) · [측정 조건과 결과](VERIFICATION.md)
