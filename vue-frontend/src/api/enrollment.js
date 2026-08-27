import api from './index.js'

export const enrollmentApi = {
  // 프로젝트 접근 권한 신청 (reason 필수)
  requestAccess(projectId, reason) {
    return api.post('/api/enrollments', { projectId, reason })
  },

  // 내가 속한(ACTIVE)/신청 대기(PENDING) 프로젝트 목록
  getMyProjects() {
    return api.get('/api/enrollments/my-projects')
  }
}
