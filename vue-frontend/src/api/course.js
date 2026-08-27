import api from './index.js'

export const courseApi = {
  getCourses(params) {
    return api.get('/api/courses', { params })
  },

  getAll(params) {
    return api.get('/api/courses', { params })
  },

  getById(id) {
    return api.get(`/api/courses/${id}`)
  },

  create(data) {
    return api.post('/api/courses', data)
  },

  update(id, data) {
    return api.put(`/api/courses/${id}`, data)
  },

  // 부작용이 있는 호출(평문 노출 + last_accessed_at 갱신 + 감사 로그 기록)이므로
  // SecretViewer의 "표시" 클릭 시에만 명시적으로 호출한다. 자동 프리페치 금지.
  // 응답 스키마는 백엔드 확정 후 확인 필요.
  getSecret(id) {
    return api.get(`/api/courses/${id}/secret`)
  }
}