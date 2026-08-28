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
      logoutAuthServerSession()
    }
  }

  // auth-server 의 SSO 세션(JSESSIONID)까지 끊는다.
  // - 프론트 토큰만 지우면 세션이 남아 다음 로그인에서 자격증명을 안 묻는다.
  // - auth-server 의 /logout 은 항상 자기 로그인 페이지(/login?logout)로 리다이렉트되고
  //   (prebuilt 이미지라 logoutSuccessUrl 변경 불가), 그 페이지에서 로그인하면
  //   게이트웨이 루트(/)로 튕겨 401 이 뜬다.
  // - 그래서 /logout 은 잠깐 뜨는 작은 창에서 처리해 세션만 끊고, 본 창은 SPA 로그인으로 보낸다.
  //   (SameSite=Lax 쿠키라 iframe/fetch 로는 세션이 안 끊겨 top-level 창 이동이 필요하다.)
  function logoutAuthServerSession() {
    const backToLogin = () => { window.location.href = '/login' }
    let popup = null
    try {
      popup = window.open(`${AUTH_SERVER_URL}/logout`, 'keynexus-logout', 'width=420,height=320')
    } catch (e) {
      popup = null
    }

    if (popup) {
      setTimeout(() => {
        try { popup.close() } catch (e) { /* noop */ }
        backToLogin()
      }, 1200)
    } else {
      // 팝업이 차단된 경우: 전체 페이지로 /logout (auth 로그인 페이지에 착지 —
      // 사용자는 다시 SPA 로 돌아와 로그인해야 한다).
      window.location.href = `${AUTH_SERVER_URL}/logout`
    }
  }

  // OAuth2 Authorization Code Flow.
  // 로그인 진입(authorize 요청 + 자격증명 제출)은 LoginView 가 same-origin 프록시로
  // 직접 처리한다. 여기서는 받은 code 를 토큰으로 교환하는 뒷부분만 담당한다.
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
    handleCallback
  }
})
