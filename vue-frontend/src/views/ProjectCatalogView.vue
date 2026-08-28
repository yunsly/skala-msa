<template>
  <div class="page">
    <AppNav />

    <main class="content">
      <div class="content-header">
        <div>
          <h1>프로젝트 카탈로그</h1>
          <p class="sub">전사 프로젝트를 탐색하고 접근 권한을 신청하세요.</p>
        </div>
        <button v-if="auth.isLeader" type="button" class="btn btn-primary" @click="openCreateModal">
          새 프로젝트
        </button>
      </div>

      <div class="search-row">
        <input
          v-model.trim="query"
          type="text"
          class="search-input"
          placeholder="프로젝트명, 설명으로 검색"
        />
      </div>

      <!-- 로딩 -->
      <div v-if="loading" class="grid">
        <div v-for="i in 6" :key="i" class="skeleton-card"></div>
      </div>

      <!-- 에러 -->
      <div v-else-if="error" class="state-box">
        <p>{{ error }}</p>
        <button type="button" class="btn btn-secondary" @click="loadAll">다시 시도</button>
      </div>

      <!-- 빈 상태 -->
      <div v-else-if="filteredProjects.length === 0" class="state-box">
        <p>{{ query ? '검색 결과가 없습니다.' : '등록된 프로젝트가 없습니다.' }}</p>
      </div>

      <!-- 목록 -->
      <div v-else class="grid">
        <div v-for="p in filteredProjects" :key="p.id" class="project-card">
          <router-link :to="`/projects/${p.id}`" class="card-main">
            <div class="card-top">
              <h3 class="card-title">{{ p.name }}</h3>
              <span v-if="isMine(p)" class="mine-badge mono">내 프로젝트</span>
            </div>
            <p class="card-desc">{{ p.description || '설명이 없습니다.' }}</p>
            <div class="card-meta">
              <span>리더 · {{ p.leaderName || (p.ownerId != null ? `#${p.ownerId}` : '-') }}</span>
              <span>자산 {{ p.assetCount ?? 0 }}개</span>
              <span>멤버 {{ p.activeMemberCount ?? p.enrollmentCount ?? p.memberCount ?? 0 }}명</span>
            </div>
          </router-link>

          <div class="card-action">
            <template v-if="isMine(p)">
              <router-link :to="`/projects/${p.id}`" class="btn btn-secondary btn-sm">프로젝트 관리</router-link>
            </template>
            <template v-else-if="myStatus(p.id) === 'ACTIVE'">
              <StatusBadge status="ACTIVE" />
              <router-link :to="`/projects/${p.id}`" class="btn btn-secondary btn-sm">바로가기</router-link>
            </template>
            <template v-else-if="myStatus(p.id) === 'PENDING'">
              <StatusBadge status="PENDING" />
              <button type="button" class="btn btn-ghost btn-sm" disabled>신청됨</button>
            </template>
            <template v-else>
              <button type="button" class="btn btn-primary btn-sm" @click="openRequestModal(p)">접근 신청</button>
            </template>
          </div>
        </div>
      </div>
    </main>

    <!-- 접근 신청 모달 -->
    <div v-if="requestModal.open" class="modal-backdrop" @click.self="closeRequestModal">
      <div class="modal" role="dialog" aria-modal="true" aria-labelledby="request-modal-title">
        <h2 id="request-modal-title">접근 권한 신청</h2>
        <p class="modal-sub">{{ requestModal.project?.name }}</p>

        <form @submit.prevent="submitRequest">
          <label class="form-label" for="reason">신청 사유</label>
          <textarea
            id="reason"
            ref="requestReasonInput"
            v-model.trim="requestModal.reason"
            class="form-textarea"
            rows="4"
            placeholder="이 프로젝트 접근이 필요한 이유를 입력하세요."
            required
          ></textarea>

          <div v-if="requestModal.error" class="error-msg">{{ requestModal.error }}</div>

          <div class="modal-actions">
            <button type="button" class="btn btn-secondary" @click="closeRequestModal">취소</button>
            <button type="submit" class="btn btn-primary" :disabled="requestModal.loading">
              <span v-if="requestModal.loading">신청 중...</span>
              <span v-else>신청</span>
            </button>
          </div>
        </form>
      </div>
    </div>

    <!-- 새 프로젝트 모달 -->
    <div v-if="createModal.open" class="modal-backdrop" @click.self="closeCreateModal">
      <div class="modal" role="dialog" aria-modal="true" aria-labelledby="create-modal-title">
        <h2 id="create-modal-title">새 프로젝트</h2>

        <form @submit.prevent="submitCreate">
          <label class="form-label" for="proj-name">프로젝트명</label>
          <input id="proj-name" ref="createNameInput" v-model.trim="createModal.name" type="text" class="form-input" placeholder="예: Payment Platform" required />

          <label class="form-label" for="proj-desc">설명</label>
          <textarea
            id="proj-desc"
            v-model.trim="createModal.description"
            class="form-textarea"
            rows="4"
            placeholder="프로젝트 목적과 범위를 입력하세요."
          ></textarea>

          <div v-if="createModal.error" class="error-msg">{{ createModal.error }}</div>

          <div class="modal-actions">
            <button type="button" class="btn btn-secondary" @click="closeCreateModal">취소</button>
            <button type="submit" class="btn btn-primary" :disabled="createModal.loading">
              <span v-if="createModal.loading">생성 중...</span>
              <span v-else>생성</span>
            </button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import AppNav from '@/components/AppNav.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import { useAuthStore } from '@/store/auth.js'
import { courseApi } from '@/api/course.js'
import { enrollmentApi } from '@/api/enrollment.js'

const auth = useAuthStore()

const loading = ref(true)
const error = ref('')
const query = ref('')
const projects = ref([])
const myProjects = ref([]) // GET /api/enrollments/my-projects 결과 — projectId별 상태 조회용

const filteredProjects = computed(() => {
  if (!query.value) return projects.value
  const q = query.value.toLowerCase()
  return projects.value.filter(
    (p) => p.name?.toLowerCase().includes(q) || p.description?.toLowerCase().includes(q)
  )
})

function isMine(project) {
  const leaderId = project.leaderId ?? project.leader_id ?? project.ownerId
  return leaderId != null && String(leaderId) === String(auth.user?.id)
}

function myStatus(projectId) {
  const match = myProjects.value.find((m) => String(m.projectId ?? m.id) === String(projectId))
  return match?.status ?? null
}

async function loadAll() {
  loading.value = true
  error.value = ''

  try {
    const [projectsRes, myRes] = await Promise.all([
      courseApi.getProjects(),
      enrollmentApi.getMyProjects().catch((e) => {
        // 내 신청 현황은 부가 정보라 실패해도 카탈로그 자체는 보여준다.
        console.error('[ProjectCatalogView] 내 프로젝트 현황 조회 실패:', e)
        return { data: [] }
      })
    ])

    projects.value = Array.isArray(projectsRes.data?.data)
      ? projectsRes.data.data
      : Array.isArray(projectsRes.data)
        ? projectsRes.data
        : []

    const myRaw = myRes.data?.data ?? myRes.data ?? []
    // 백엔드는 { activeProjects[], pendingProjects[] } 로 그룹핑해 내려준다(플랫 배열도 허용).
    myProjects.value = Array.isArray(myRaw)
      ? myRaw
      : [
          ...(myRaw.activeProjects ?? []),
          ...(myRaw.pendingProjects ?? []),
          ...(myRaw.cancelledProjects ?? [])
        ]
  } catch (e) {
    console.error('[ProjectCatalogView] 프로젝트 목록 조회 실패:', e)
    error.value = '프로젝트 목록을 불러오지 못했습니다.'
  } finally {
    loading.value = false
  }
}

onMounted(loadAll)

// 모달 접근성: ESC로 닫기, 열릴 때 첫 입력으로 포커스 이동, 닫히면 트리거로 포커스 복귀.
// (요구사항: "모달 접근성" 테스트 항목 — #9)
let lastFocusedEl = null

function handleEscape(e) {
  if (e.key !== 'Escape') return
  if (requestModal.value.open) closeRequestModal()
  if (createModal.value.open) closeCreateModal()
}
onMounted(() => window.addEventListener('keydown', handleEscape))
onUnmounted(() => window.removeEventListener('keydown', handleEscape))

// 접근 신청 모달
const requestModal = ref({ open: false, project: null, reason: '', loading: false, error: '' })
const requestReasonInput = ref(null)

function openRequestModal(project) {
  lastFocusedEl = document.activeElement
  requestModal.value = { open: true, project, reason: '', loading: false, error: '' }
  nextTick(() => requestReasonInput.value?.focus())
}
function closeRequestModal() {
  requestModal.value.open = false
  lastFocusedEl?.focus?.()
}
async function submitRequest() {
  requestModal.value.error = ''
  requestModal.value.loading = true

  try {
    await enrollmentApi.requestAccess(requestModal.value.project.id, requestModal.value.reason)
    myProjects.value.push({ projectId: requestModal.value.project.id, status: 'PENDING' })
    closeRequestModal()
  } catch (e) {
    console.error('[ProjectCatalogView] 접근 신청 실패:', e)
    requestModal.value.error = e.response?.data?.message || '접근 신청에 실패했습니다.'
  } finally {
    requestModal.value.loading = false
  }
}

// 새 프로젝트 모달 (LEADER 전용)
const createModal = ref({ open: false, name: '', description: '', loading: false, error: '' })
const createNameInput = ref(null)

function openCreateModal() {
  lastFocusedEl = document.activeElement
  createModal.value = { open: true, name: '', description: '', loading: false, error: '' }
  nextTick(() => createNameInput.value?.focus())
}
function closeCreateModal() {
  createModal.value.open = false
  lastFocusedEl?.focus?.()
}
async function submitCreate() {
  createModal.value.error = ''
  createModal.value.loading = true

  try {
    await courseApi.createProject({
      name: createModal.value.name,
      description: createModal.value.description
    })
    closeCreateModal()
    await loadAll()
  } catch (e) {
    console.error('[ProjectCatalogView] 프로젝트 생성 실패:', e)
    createModal.value.error = e.response?.data?.message || '프로젝트 생성에 실패했습니다.'
  } finally {
    createModal.value.loading = false
  }
}
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
.content-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 20px;
}
.content-header h1 { font-size: 20px; font-weight: 700; color: var(--color-text-primary); }
.sub { font-size: 12.5px; color: var(--color-text-secondary); margin-top: 4px; }

.search-row { margin-bottom: 20px; }
.search-input {
  width: 100%;
  max-width: 360px;
  padding: 9px 14px;
  border: 1.5px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-primary);
  color: var(--color-text-primary);
  font-size: 13px;
  outline: none;
}
.search-input::placeholder { color: var(--color-text-muted); }
.search-input:focus { border-color: var(--color-primary); box-shadow: 0 0 0 3px var(--color-primary-light); }

.grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14px;
}

.project-card {
  background: var(--color-bg-primary);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.card-main {
  padding: 18px 18px 14px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: 1;
}
.card-top { display: flex; justify-content: space-between; align-items: flex-start; gap: 8px; }
.card-title { font-size: 15px; font-weight: 600; color: var(--color-text-primary); }
.mine-badge {
  font-size: 9.5px;
  color: var(--color-primary);
  background: var(--color-primary-light);
  border-radius: 4px;
  padding: 2px 7px;
  white-space: nowrap;
  flex-shrink: 0;
}
.card-desc {
  font-size: 12.5px;
  color: var(--color-text-secondary);
  line-height: 1.55;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.card-meta {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  font-size: 11px;
  color: var(--color-text-muted);
  margin-top: 4px;
}
.card-action {
  padding: 12px 18px;
  border-top: 1px solid var(--color-border);
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}
.btn-sm { padding: 6px 12px; font-size: 12.5px; }

.skeleton-card {
  height: 150px;
  border-radius: var(--radius-lg);
  background: linear-gradient(90deg, var(--color-bg-tertiary) 25%, var(--color-bg-secondary) 50%, var(--color-bg-tertiary) 75%);
  background-size: 200% 100%;
  animation: shimmer 1.4s infinite;
}
@keyframes shimmer { to { background-position: -200% 0; } }

.state-box {
  text-align: center;
  padding: 80px 0;
  color: var(--color-text-muted);
  font-size: 13px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
}

/* 모달 */
.modal-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 50;
  padding: 20px;
}
.modal {
  width: 100%;
  max-width: 420px;
  background: var(--color-bg-secondary);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: 24px;
}
.modal h2 { font-size: 17px; font-weight: 700; color: var(--color-text-primary); margin-bottom: 4px; }
.modal-sub { font-size: 12.5px; color: var(--color-text-secondary); margin-bottom: 16px; }
.modal form { display: flex; flex-direction: column; gap: 8px; }
.form-label { font-size: 12px; font-weight: 500; color: var(--color-text-secondary); margin-top: 8px; }
.form-input,
.form-textarea {
  width: 100%;
  padding: 9px 12px;
  border: 1.5px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-primary);
  color: var(--color-text-primary);
  font-size: 13px;
  font-family: var(--font-sans);
  outline: none;
  resize: vertical;
}
.form-input:focus,
.form-textarea:focus { border-color: var(--color-primary); box-shadow: 0 0 0 3px var(--color-primary-light); }
.error-msg {
  margin-top: 8px;
  padding: 8px 12px;
  background: var(--color-danger-light);
  border: 1px solid var(--color-danger);
  border-radius: var(--radius-md);
  font-size: 12px;
  color: var(--color-danger);
}
.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 16px;
}

@media (max-width: 900px) {
  .grid { grid-template-columns: 1fr; }
}
</style>
