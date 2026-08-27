import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi } from '@/api/auth.js'

const AUTH_SERVER_URL = import.meta.env.VITE_AUTH_SERVER_URL || 'http://localhost:8080'

// 실제 운영 중인 백엔드(auth-server OAuth2 + user-service STUDENT/INSTRUCTOR)에
// 맞춘 인증 스토어. KeyNexus 문서상의 ADMIN/LEADER/MEMBER, 직접 로그인
// (POST /api/users/login)은 아직 백엔드에 없어 여기서는 쓰지 않는다.
//
// 팀 합의(역할 매핑): LEADER = INSTRUCTOR, MEMBER = STUDENT.
// ADMIN은 이 둘과 무관한 전사 최상위 권한자 개념이라 별도로 두되, User.Role enum이
// STUDENT/INSTRUCTOR뿐이라 지금 백엔드로는 ADMIN 계정 자체가 생성될 수 없다 — 추후
// 백엔드에 ADMIN이 추가되면 자연히 인식되도록 매핑만 해둔다.
const BACKEND_ROLE_TO_KEYNEXUS_ROLE = {
  INSTRUCTOR: 'LEADER',
  STUDENT: 'MEMBER'
}

export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref(sessionStorage.getItem('access_token') || null)
  const user = ref(JSON.parse(sessionStorage.getItem('user') || 'null'))

  const isAuthenticated = computed(() => !!accessToken.value)

  const keyNexusRole = computed(() => {
    const raw = user.value?.role
    if (raw === 'ADMIN') return 'ADMIN'
    return BACKEND_ROLE_TO_KEYNEXUS_ROLE[raw] ?? null
  })
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
      window.location.href = '/login'
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
