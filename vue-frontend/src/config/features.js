// 백엔드 API가 아직 연동되지 않은 화면을 "준비 중"으로 표시하기 위한 플래그.
//
// - approvalQueue : payment-service 의 승인 대기/승인/거절 API (GET /api/payments/pending,
//                   POST /api/payments/{id}/approve · /revoke) 배포 후 true
// - riskDashboard : recommend-service 의 전사 위험도 API (feature/fr-05) 병합·배포 후 true
//
// 플래그를 true 로 바꾸면 각 화면이 실제 API 를 호출하는 원래 동작으로 복귀한다.
export const FEATURES = {
  approvalQueue: false,
  riskDashboard: true
}
