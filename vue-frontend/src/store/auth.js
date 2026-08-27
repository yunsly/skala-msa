import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi } from '@/api/auth.js'

const AUTH_SERVER_URL = import.meta.env.VITE_AUTH_SERVER_URL || 'http://localhost:8080'

// 실제 운영 중인 백엔드(auth-server OAuth2 + user-service)에 맞춘 인증 스토어.
// 직접 로그인(POST /api/users/login)은 여전히 없다 — user-service는
// OAuth2 Resource Server라 로그인은 auth-server의 Authorization Code Flow로
// 처리된다(user-service/.../SecurityConfig.java의 oauth2ResourceServer 설정 확인).
//
// 역할 값은 과도기라 두 세대가 섞여 들어올 수 있다:
// - 레거시: STUDENT/INSTRUCTOR (팀 합의 매핑: LEADER=INSTRUCTOR, MEMBER=STUDENT)
// - 신규: user-service의 V4__user_role_domain.sql 마이그레이션이 DB 값을
//   ADMIN/LEADER/MEMBER로 직접 바꾼다 — 이 마이그레이션이 배포되면 role이
//   이미 "LEADER"/"MEMBER"로 내려오므로 그대로 통과시켜야 한다.
// 아래 매핑은 두 세대 값을 전부 받아 같은 결과로 정규화한다.
const BACKEND_ROLE_TO_KEYNEXUS_ROLE = {
  INSTRUCTOR: 'LEADER',
  STUDENT: 'MEMBER',
  LEADER: 'LEADER',
  MEMBER: 'MEMBER',
  ADMIN: 'ADMIN'
}

export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref(sessionStorage.getItem('access_token') || null)
  const user = ref(JSON.parse(sessionStorage.getItem('user') || 'null'))

  const isAuthenticated = computed(() => !!accessToken.value)

  const keyNexusRole = computed(() => BACKEND_ROLE_TO_KEYNEXUS_ROLE[user.value?.role] ?? null)
  const isAdmin = computed(() => keyNexusRole.value === 'ADMIN')
  const isLeader = computed(() => keyNexusRole.value === 'LEADER')
  const isMember = computed(() => keyNexusRole.value === 'MEMBER')

  function setToken(token) {
    accessToken.value = token
    sessionStorage.setItem('access_token', token)
  }

  function setUser(userData) {
    user.value = userData
    sessionStorage.setItem('user', JSON.stringify(userData))
  }

  async function fetchUser() {
    try {
      const res = await authApi.getMe()
      console.log('[AuthStore] /me response =', res.data)

      const userData = res?.data?.data ?? res?.data

      if (!userData || typeof userData !== 'object') {
        throw new Error('사용자 정보 형식이 올바르지 않습니다.')
      }

      setUser(userData)
    } catch (error) {
      console.error('[AuthStore] 사용자 정보 조회 실패:', error)
      logout(false)
    }
  }

  function logout(redirect = true) {
    accessToken.value = null
    user.value = null
    sessionStorage.removeItem('access_token')
    sessionStorage.removeItem('user')

    if (redirect) {
      // 프론트 토큰만 지우면 auth-server 의 SSO 세션(JSESSIONID)이 남아, 다시 로그인
      // 버튼을 눌렀을 때 아이디/비밀번호 입력 없이 곧바로 재로그인된다.
      // auth-server 세션까지 끊으려면 top-level 네비게이션으로 /logout 을 호출해야 한다
      // (SameSite=Lax 쿠키라 fetch/iframe 으로는 세션이 안 끊긴다).
      // 로그아웃 후 auth-server 기본 로그인 페이지(/login?logout)에 착지한다.
      // 재로그인은 이 페이지가 아니라 SPA(홈)에서 시도해야 정상 흐름을 탄다
      // — logoutSuccessUrl 을 SPA 로 돌리려면 프리빌트 auth-server 이미지 수정 필요.
      window.location.href = `${AUTH_SERVER_URL}/logout`
    }
  }

  // OAuth2 Authorization Code Flow
  function redirectToLogin() {
    const params = new URLSearchParams({
      response_type: 'code',
      client_id: import.meta.env.VITE_CLIENT_ID,
      redirect_uri: import.meta.env.VITE_REDIRECT_URI,
      scope: 'openid profile read write'
    })

    window.location.href = `${AUTH_SERVER_URL}/oauth2/authorize?${params.toString()}`
  }

  async function handleCallback(code) {
    const res = await authApi.exchangeCode(code)
    console.log('[AuthStore] token response =', res.data)

    const token = res?.data?.access_token

    if (!token) {
      throw new Error('액세스 토큰을 받지 못했습니다.')
    }

    setToken(token)
    await fetchUser()
  }

  return {
    accessToken,
    user,
    isAuthenticated,
    keyNexusRole,
    isAdmin,
    isLeader,
    isMember,
    setToken,
    setUser,
    fetchUser,
    logout,
    redirectToLogin,
    handleCallback
  }
})
