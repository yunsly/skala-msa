import api from './index.js'

export const paymentApi = {
  // 내 프로젝트로 들어온 승인 대기 목록. 백엔드가 X-User-Id 기준으로
  // "내가 리더인 프로젝트"의 요청만 내려준다고 가정.
  getPending() {
    return api.get('/api/payments/pending')
  },

  approve(id, decisionReason) {
    return api.post(`/api/payments/${id}/approve`, { decisionReason })
  },

  // 계약 문서엔 revoke의 body가 명시되어 있지 않지만, 이슈 #17 요구사항이
  // "승인 또는 거절 사유를 입력해 결정을 처리"라고 명시해 approve와 동일하게
  // decisionReason을 보낸다 — 백엔드가 이 필드를 안 받으면 무시될 뿐이라 안전하다.
  revoke(id, decisionReason) {
    return api.post(`/api/payments/${id}/revoke`, { decisionReason })
  }
}
