import api from './index.js'

export const courseApi = {
  // 전사 프로젝트 목록. 랜딩 페이지 요약 통계와 ProjectCatalogView에서 공용으로 쓴다.
  // 응답 스키마는 백엔드 확정 후 확인 필요 — 프로젝트별 assetCount/criticalAssetCount
  // 같은 요약 필드가 함께 내려온다고 가정한다(비어 있으면 통계 없이 표시).
  getProjects() {
    return api.get('/api/courses/projects')
  },

  // 신규 프로젝트 생성 (LEADER/ADMIN 전용). 화면 정의표에 별도 화면은 없지만
  // API 계약에 있고 ProjectCatalogView의 "새 프로젝트" 모달에서 사용한다.
  createProject(data) {
    return api.post('/api/courses/projects', data)
  },

  // 프로젝트 내 자산 목록. { projectId } 형태로 호출.
  getAssets(params) {
    return api.get('/api/courses', { params })
  },

  getAsset(id) {
    return api.get(`/api/courses/${id}`)
  },

  // 신규 자산 등록 (LEADER/ADMIN 전용) — { projectId, title, description, category,
  // provider, planName?, secretValue }
  createAsset(data) {
    return api.post('/api/courses', data)
  },

  // 부작용이 있는 호출(평문 노출 + last_accessed_at 갱신 + 감사 로그 기록)이므로
  // SecretViewer의 "표시" 클릭 시에만 명시적으로 호출한다. 자동 프리페치 금지.
  //
  // 백엔드에 전용 GET /api/courses/{id}/secret 엔드포인트가 아직 없다(현재 500).
  // 상세 응답(GET /api/courses/{id})이 이미 복호화된 secretValue 를 포함하므로
  // 임시로 그걸 재사용한다. 전용 엔드포인트가 생기면 이 한 줄만 되돌리면 된다.
  getSecret(id) {
    return api.get(`/api/courses/${id}`)
  }
}