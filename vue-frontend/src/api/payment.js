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

  // 계약상 revoke는 body가 명시되어 있지 않다 — PENDING 거절/ACTIVE 회수 모두
  // 이 엔드포인트로 처리된다고 가정(별도 reject 엔드포인트 없음).
  revoke(id) {
    return api.post(`/api/payments/${id}/revoke`)
  }
}
