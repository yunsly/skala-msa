import api from './index.js'

export const courseApi = {
  // 전사 프로젝트 목록. 랜딩 페이지 요약 통계와 ProjectCatalogView에서 공용으로 쓴다.
  // 응답 스키마는 백엔드 확정 후 확인 필요 — 프로젝트별 assetCount/criticalAssetCount
  // 같은 요약 필드가 함께 내려온다고 가정한다(비어 있으면 통계 없이 표시).
  getProjects() {
    return api.get('/api/courses/projects')
  },

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