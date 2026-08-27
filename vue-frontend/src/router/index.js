import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/store/auth.js'

// KeyNexus 화면 구성 (docs/requirements_specification.md v3.0.0 기준, 총 9개)
// 역할: ADMIN(전사 관리자) / LEADER(프로젝트 리더) / MEMBER(개발자)
const routes = [
  {
    path: '/',
    name: 'Landing',
    component: () => import('@/views/LandingView.vue')
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/LoginView.vue'),
    meta: { guestOnly: true }
  },
  {
    // auth-server OAuth2 Authorization Code Flow의 redirect_uri 대상.
    path: '/callback',
    name: 'Callback',
    component: () => import('@/views/CallbackView.vue')
  },
  {
    path: '/projects',
    name: 'ProjectCatalog',
    component: () => import('@/views/ProjectCatalogView.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/projects/:id(\\d+)',
    name: 'ProjectDetail',
    // 자산 목록 자체는 승인된 멤버/ADMIN에게만 노출되어야 하지만, 이는 정적 role이
    // 아니라 "이 프로젝트의 내 멤버십 상태"라는 리소스 단위 권한이라 라우터 가드로는
    // 판별할 수 없다 (API 응답을 봐야 함). 화면 내부에서 조건부 렌더링으로 처리한다.
    component: () => import('@/views/ProjectDetailView.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/projects/:id(\\d+)/assets/new',
    name: 'AssetCreate',
    component: () => import('@/views/AssetCreateView.vue'),
    meta: { requiresAuth: true, allowedRoles: ['LEADER', 'ADMIN'] }
  },
  {
    // 화면 정의표에 명시된 경로를 그대로 따름(자산 상세이지만 /courses/:id).
    path: '/courses/:id(\\d+)',
    name: 'AssetDetail',
    // ProjectDetail과 동일한 이유로, "승인된 멤버/ADMIN만" 조건은 화면 내부에서 처리.
    component: () => import('@/views/AssetDetailView.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/my-projects',
    name: 'MyProjects',
    component: () => import('@/views/MyProjectsView.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/approval',
    name: 'ApprovalQueue',
    component: () => import('@/views/ApprovalQueueView.vue'),
    meta: { requiresAuth: true, allowedRoles: ['LEADER', 'ADMIN'] }
  },
  {
    path: '/risk-dashboard',
    name: 'RiskDashboard',
    // ADMIN 전용 — LEADER는 여기 접근 못하고 ProjectDetailView에서 본인 프로젝트
    // 현황만 확인한다.
    component: () => import('@/views/RiskDashboardView.vue'),
    meta: { requiresAuth: true, allowedRoles: ['ADMIN'] }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior() {
    return { top: 0 }
  }
})

// 인증/권한 가드
router.beforeEach((to) => {
  const auth = useAuthStore()

  if (to.meta.requiresAuth && !auth.isAuthenticated) {
    return { name: 'Login' }
  }

  if (to.meta.guestOnly && auth.isAuthenticated) {
    return { name: 'ProjectCatalog' }
  }

  // allowedRoles는 KeyNexus 역할(ADMIN/LEADER/MEMBER) 기준이다. 실제 로그인 사용자는
  // 백엔드 STUDENT/INSTRUCTOR로 들어오므로 store/auth.js의 keyNexusRole(매핑: LEADER=
  // INSTRUCTOR, MEMBER=STUDENT)로 비교한다.
  if (to.meta.allowedRoles && !to.meta.allowedRoles.includes(auth.keyNexusRole)) {
    return { name: 'ProjectCatalog' }
  }
})

export default router
