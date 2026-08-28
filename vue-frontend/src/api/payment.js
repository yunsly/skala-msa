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

  // PENDING 신청 거절. (승인 후 회수(revoke)는 별도 기능으로, 현재 화면에는 없다.)
  reject(id, decisionReason) {
    return api.post(`/api/payments/${id}/reject`, { decisionReason })
  }
}
