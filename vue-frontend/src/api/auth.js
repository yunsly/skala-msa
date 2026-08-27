import api from './index.js'

export const authApi = {
  // 이메일/비밀번호 로그인. 응답 스키마는 백엔드 확정 후 확인 필요
  // (data.token / data.user 형태로 온다고 가정 — store/auth.js 참고)
  login(data) {
    return api.post('/api/users/login', data)
  }
}
