import api from './index.js'
import axios from 'axios'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'

// 실제 운영 중인 백엔드(user-service, auth-server) 규격에 맞춘 인증 API.
// user-service에는 /api/users/login이 없고, 로그인은 auth-server의
// OAuth2 Authorization Code Flow로 처리된다. 회원가입만 인증 없이 호출 가능.
export const authApi = {
  // OAuth2 Authorization Code -> Access Token 교환
  // CLIENT_SECRET_BASIC: Authorization 헤더에 client_id:client_secret을 Base64로 인코딩
  exchangeCode(code) {
    const clientId = import.meta.env.VITE_CLIENT_ID
    const clientSecret = import.meta.env.VITE_CLIENT_SECRET
    const redirectUri = import.meta.env.VITE_REDIRECT_URI
    const credentials = btoa(`${clientId}:${clientSecret}`)

    const body = new URLSearchParams({
      grant_type: 'authorization_code',
      code,
      redirect_uri: redirectUri
    })

    return axios.post(
      `${API_BASE_URL}/oauth2/token`,
      body.toString(),
      {
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded',
          'Authorization': `Basic ${credentials}`
        }
      }
    )
  },

  // 내 정보 조회 (X-User-Id 기반, 인증 필요)
  getMe() {
    return api.get('/api/users/me')
  },

  // 회원가입 (인증 불필요) — role은 현재 백엔드 User.Role 기준 STUDENT | INSTRUCTOR
  register(data) {
    return api.post('/api/users/register', data)
  }
}
