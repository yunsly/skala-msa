<template>
  <div class="page">
    <AppNav />

    <main class="content" v-if="!pageLoading">
      <!-- 에러/없음 -->
      <div v-if="pageError" class="state-box">
        <p>{{ pageError }}</p>
        <router-link to="/projects" class="btn btn-secondary">프로젝트 카탈로그로</router-link>
      </div>

      <template v-else>
        <div class="breadcrumb mono">
          <router-link to="/projects">프로젝트 카탈로그</router-link> / {{ project.name }}
        </div>

        <div class="header-row">
          <div>
            <h1>{{ project.name }}</h1>
            <p class="desc">{{ project.description || '설명이 없습니다.' }}</p>
            <div class="meta-row">
              <span>리더 · {{ project.leaderName || (project.ownerId != null ? `#${project.ownerId}` : '-') }}</span>
              <span>멤버 {{ project.activeMemberCount ?? project.enrollmentCount ?? project.memberCount ?? 0 }}명</span>
              <span>자산 {{ project.assetCount ?? assets.length }}개</span>
            </div>
          </div>
          <router-link
            v-if="canManage"
            :to="`/projects/${projectId}/assets/new`"
            class="btn btn-primary"
          >
            자산 등록
          </router-link>
        </div>

        <!-- 승인 안 된 멤버: 자산 목록 비노출 -->
        <div v-if="!canViewAssets" class="access-gate">
          <template v-if="myStatus === 'PENDING'">
            <StatusBadge status="PENDING" />
            <p>접근 승인 대기 중입니다. 승인되면 이 프로젝트의 자산 목록을 볼 수 있습니다.</p>
          </template>
          <template v-else>
            <p>이 프로젝트의 자산 목록은 승인된 멤버만 볼 수 있습니다.</p>
            <button type="button" class="btn btn-primary" :disabled="requesting" @click="handleRequest">
              <span v-if="requesting">신청 중...</span>
              <span v-else>접근 권한 신청</span>
            </button>
            <p v-if="requestError" class="error-msg">{{ requestError }}</p>
          </template>
        </div>

        <!-- 자산 목록 -->
        <template v-else>
          <h2 class="section-title">자산 목록</h2>

          <div v-if="assetsLoading" class="grid">
            <div v-for="i in 4" :key="i" class="skeleton-card"></div>
          </div>
          <div v-else-if="assetsError" class="state-box">
            <p>{{ assetsError }}</p>
            <button type="button" class="btn btn-secondary" @click="loadAssets">다시 시도</button>
          </div>
          <div v-else-if="assets.length === 0" class="state-box">
            <p>등록된 자산이 없습니다.</p>
          </div>
          <div v-else class="grid">
            <router-link
              v-for="a in assets"
              :key="a.id"
              :to="`/courses/${a.id}`"
              class="asset-card"
            >
              <CategoryBadge :type="a.category" />
              <h3 class="asset-title">{{ a.title }}</h3>
              <p class="asset-desc">{{ a.description || '설명이 없습니다.' }}</p>
              <p v-if="a.provider || a.planName" class="asset-provider mono">
                {{ [a.provider, a.planName].filter(Boolean).join(' · ') }}
              </p>
            </router-link>
          </div>
        </template>
      </template>
    </main>

    <main v-else class="content">
      <div class="state-box">불러오는 중...</div>
    </main>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import AppNav from '@/components/AppNav.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import CategoryBadge from '@/components/CategoryBadge.vue'
import { useAuthStore } from '@/store/auth.js'
import { courseApi } from '@/api/course.js'
import { enrollmentApi } from '@/api/enrollment.js'

const route = useRoute()
const auth = useAuthStore()

const projectId = computed(() => route.params.id)

const pageLoading = ref(true)
const pageError = ref('')
const project = ref({})
const myStatus = ref(null) // null | 'PENDING' | 'ACTIVE'

const assets = ref([])
const assetsLoading = ref(false)
const assetsError = ref('')

const isProjectLeader = computed(() => {
  const leaderId = project.value.leaderId ?? project.value.leader_id ?? project.value.ownerId
  return leaderId != null && String(leaderId) === String(auth.user?.id)
})
const canViewAssets = computed(() => isProjectLeader.value || myStatus.value === 'ACTIVE' || auth.isAdmin)
const canManage = computed(() => isProjectLeader.value || auth.isAdmin)

const requesting = ref(false)
const requestError = ref('')

// 프로젝트 단건 조회 API가 계약에 없어(백엔드 프로젝트 계층 작업 중), 목록에서
// projectId로 찾는 방식으로 임시 구현. 단건 조회 엔드포인트가 생기면 교체 필요.
async function loadProject() {
  pageLoading.value = true
  pageError.value = ''

  try {
    const [projectsRes, myRes] = await Promise.all([
      courseApi.getProjects(),
      enrollmentApi.getMyProjects().catch((e) => {
        console.error('[ProjectDetailView] 내 프로젝트 현황 조회 실패:', e)
        return { data: [] }
      })
    ])

    const projects = Array.isArray(projectsRes.data?.data)
      ? projectsRes.data.data
      : Array.isArray(projectsRes.data)
        ? projectsRes.data
        : []

    const found = projects.find((p) => String(p.id) === String(projectId.value))
    if (!found) {
      pageError.value = '프로젝트를 찾을 수 없습니다.'
      return
    }
    project.value = found

    const myProjectsData = myRes.data?.data ?? {}
    const myProjects = [
      ...(myProjectsData.activeProjects ?? []),
      ...(myProjectsData.pendingProjects ?? []),
      ...(myProjectsData.cancelledProjects ?? [])
    ]
    const match = myProjects.find((m) => String(m.projectId ?? m.id) === String(projectId.value))
    myStatus.value = match?.status ?? null

    if (isProjectLeader.value || myStatus.value === 'ACTIVE' || auth.isAdmin) {
      await loadAssets()
    }
  } catch (e) {
    console.error('[ProjectDetailView] 프로젝트 조회 실패:', e)
    pageError.value = '프로젝트 정보를 불러오지 못했습니다.'
  } finally {
    pageLoading.value = false
  }
}

async function loadAssets() {
  assetsLoading.value = true
  assetsError.value = ''

  try {
    const res = await courseApi.getAssets({ projectId: projectId.value })
    assets.value = Array.isArray(res.data?.data) ? res.data.data : Array.isArray(res.data) ? res.data : []
  } catch (e) {
    console.error('[ProjectDetailView] 자산 목록 조회 실패:', e)
    assetsError.value = '자산 목록을 불러오지 못했습니다.'
  } finally {
    assetsLoading.value = false
  }
}

async function handleRequest() {
  requestError.value = ''
  requesting.value = true

  try {
    await enrollmentApi.requestAccess(projectId.value, '프로젝트 자산 접근이 필요합니다.')
    myStatus.value = 'PENDING'
  } catch (e) {
    console.error('[ProjectDetailView] 접근 신청 실패:', e)
    requestError.value = e.response?.data?.message || '접근 신청에 실패했습니다.'
  } finally {
    requesting.value = false
  }
}

onMounted(loadProject)
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: var(--color-bg-secondary);
}
.content {
  max-width: 1160px;
  margin: 0 auto;
  padding: 32px 24px 60px;
}
.breadcrumb {
  font-size: 11px;
  color: var(--color-text-muted);
  margin-bottom: 16px;
}
.breadcrumb a { color: var(--color-text-muted); }
.breadcrumb a:hover { color: var(--color-primary); }

.header-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 28px;
}
.header-row h1 { font-size: 22px; font-weight: 700; color: var(--color-text-primary); margin-bottom: 8px; }
.desc { font-size: 13px; color: var(--color-text-secondary); line-height: 1.6; max-width: 640px; margin-bottom: 12px; }
.meta-row { display: flex; gap: 16px; font-size: 12px; color: var(--color-text-muted); }

.access-gate {
  background: var(--color-bg-primary);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: 40px 24px;
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  color: var(--color-text-secondary);
  font-size: 13px;
}

.section-title { font-size: 15px; font-weight: 700; color: var(--color-text-primary); margin-bottom: 14px; }

.grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14px;
}
.asset-card {
  background: var(--color-bg-primary);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: 16px 18px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.asset-card:hover { border-color: var(--color-border-hover); }
.asset-title { font-size: 14px; font-weight: 600; color: var(--color-text-primary); }
.asset-desc {
  font-size: 12px;
  color: var(--color-text-secondary);
  line-height: 1.55;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.asset-provider { font-size: 11px; color: var(--color-text-muted); }

.skeleton-card {
  height: 120px;
  border-radius: var(--radius-lg);
  background: linear-gradient(90deg, var(--color-bg-tertiary) 25%, var(--color-bg-secondary) 50%, var(--color-bg-tertiary) 75%);
  background-size: 200% 100%;
  animation: shimmer 1.4s infinite;
}
@keyframes shimmer { to { background-position: -200% 0; } }

.state-box {
  text-align: center;
  padding: 60px 0;
  color: var(--color-text-muted);
  font-size: 13px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
}
.error-msg { color: var(--color-danger); font-size: 12px; }

@media (max-width: 900px) {
  .grid { grid-template-columns: 1fr; }
}
</style>
