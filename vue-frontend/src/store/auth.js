import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi } from '@/api/auth.js'

export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref(sessionStorage.getItem('access_token') || null)
  const user = ref(JSON.parse(sessionStorage.getItem('user') || 'null'))

  const isAuthenticated = computed(() => !!accessToken.value)
  const isAdmin = computed(() => user.value?.role === 'ADMIN')
  const isLeader = computed(() => user.value?.role === 'LEADER')
  const isMember = computed(() => user.value?.role === 'MEMBER')

  function setToken(token) {
    accessToken.value = token
    sessionStorage.setItem('access_token', token)
  }

  function setUser(userData) {
    user.value = userData
    sessionStorage.setItem('user', JSON.stringify(userData))
  }

  // 이메일/비밀번호 로그인. 응답 스키마는 백엔드 확정 후 확인 필요 —
  // data.token / data.user 우선, 없으면 대안 필드 탐색.
  async function login({ email, password }) {
    const res = await authApi.login({ email, password })
    console.log('[AuthStore] login response =', res.data)

    const payload = res.data?.data ?? res.data
    const token = payload?.token ?? payload?.accessToken
    const userData = payload?.user

    if (!token || !userData) {
      throw new Error('로그인 응답 형식이 올바르지 않습니다.')
    }

    setToken(token)
    setUser(userData)
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

  return {
    accessToken,
    user,
    isAuthenticated,
    isAdmin,
    isLeader,
    isMember,
    setToken,
    setUser,
    login,
    logout
  }
})
