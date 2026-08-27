<template>
  <div class="page">
    <AppNav />

    <main class="content">
      <div class="content-header">
        <h1>내 프로젝트</h1>
        <p class="sub">내가 참여 중이거나 승인 대기 중인 프로젝트입니다.</p>
      </div>

      <!-- 로딩 -->
      <div v-if="loading" class="state-box">불러오는 중...</div>

      <!-- 에러 -->
      <div v-else-if="error" class="state-box">
        <p>{{ error }}</p>
        <button type="button" class="btn btn-secondary" @click="loadAll">다시 시도</button>
      </div>

      <!-- 빈 상태 -->
      <div v-else-if="items.length === 0" class="state-box">
        <p>아직 신청하거나 참여 중인 프로젝트가 없습니다.</p>
        <router-link to="/projects" class="btn btn-primary">프로젝트 둘러보기</router-link>
      </div>

      <template v-else>
        <section v-if="activeItems.length" class="section">
          <h2 class="section-title">참여 중 <span class="count mono">{{ activeItems.length }}</span></h2>
          <div class="list">
            <div v-for="item in activeItems" :key="item.projectId" class="row">
              <div class="row-main">
                <h3>{{ item.name }}</h3>
                <p>{{ item.description || '설명이 없습니다.' }}</p>
              </div>
              <StatusBadge status="ACTIVE" />
              <router-link :to="`/projects/${item.projectId}`" class="btn btn-secondary btn-sm">바로가기</router-link>
            </div>
          </div>
        </section>

        <section v-if="pendingItems.length" class="section">
          <h2 class="section-title">승인 대기 <span class="count mono">{{ pendingItems.length }}</span></h2>
          <div class="list">
            <div v-for="item in pendingItems" :key="item.projectId" class="row">
              <div class="row-main">
                <h3>{{ item.name }}</h3>
                <p>{{ item.description || '설명이 없습니다.' }}</p>
              </div>
              <StatusBadge status="PENDING" />
              <router-link :to="`/projects/${item.projectId}`" class="btn btn-ghost btn-sm">프로젝트 보기</router-link>
            </div>
          </div>
        </section>

        <section v-if="cancelledItems.length" class="section">
          <h2 class="section-title">회수됨 <span class="count mono">{{ cancelledItems.length }}</span></h2>
          <div class="list">
            <div v-for="item in cancelledItems" :key="item.projectId" class="row row-muted">
              <div class="row-main">
                <h3>{{ item.name }}</h3>
                <p>{{ item.description || '설명이 없습니다.' }}</p>
              </div>
              <StatusBadge status="CANCELLED" />
              <router-link :to="`/projects/${item.projectId}`" class="btn btn-ghost btn-sm">재신청</router-link>
            </div>
          </div>
        </section>
      </template>
    </main>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import AppNav from '@/components/AppNav.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import { courseApi } from '@/api/course.js'
import { enrollmentApi } from '@/api/enrollment.js'

const loading = ref(true)
const error = ref('')
const items = ref([])

const activeItems = computed(() => items.value.filter((i) => i.status === 'ACTIVE'))
const pendingItems = computed(() => items.value.filter((i) => i.status === 'PENDING'))
// CANCELLED(회수됨)도 별도 섹션으로 보여준다 — 예전엔 어디에도 안 걸려서, 회수된
// 프로젝트만 있는 사용자는 아무 섹션도 안 뜨고 빈 상태 문구도 없이 화면이 통째로
// 비어 보이는 문제가 있었다.
const cancelledItems = computed(() => items.value.filter((i) => i.status === 'CANCELLED'))

async function loadAll() {
  loading.value = true
  error.value = ''

  try {
    const myRes = await enrollmentApi.getMyProjects()
    const myProjects = Array.isArray(myRes.data?.data)
      ? myRes.data.data
      : Array.isArray(myRes.data)
        ? myRes.data
        : []

    // 응답에 프로젝트명/설명이 이미 포함돼 있으면 그대로 쓰고, projectId만 있으면
    // 프로젝트 목록에서 찾아 보강한다(단건 조회 API가 없어 다른 화면과 동일 패턴).
    // name은 있는데 description만 빠진 경우도 join이 필요하다 — 예: 승인 처리 후
    // 응답에 name만 실려오고 description은 안 오는 경우 (실제 발생 확인됨).
    const needsJoin = myProjects.some((m) => (!m.name && !m.projectName) || !m.description)
    let projectsById = {}

    if (needsJoin) {
      const projectsRes = await courseApi.getProjects()
      const projects = Array.isArray(projectsRes.data?.data)
        ? projectsRes.data.data
        : Array.isArray(projectsRes.data)
          ? projectsRes.data
          : []
      projectsById = Object.fromEntries(projects.map((p) => [String(p.id), p]))
    }

    items.value = myProjects.map((m) => {
      const projectId = m.projectId ?? m.id
      const joined = projectsById[String(projectId)] ?? {}
      return {
        projectId,
        status: m.status,
        name: m.name ?? m.projectName ?? joined.name ?? `프로젝트 #${projectId}`,
        description: m.description ?? joined.description ?? ''
      }
    })
  } catch (e) {
    console.error('[MyProjectsView] 내 프로젝트 목록 조회 실패:', e)
    error.value = '내 프로젝트 목록을 불러오지 못했습니다.'
  } finally {
    loading.value = false
  }
}

onMounted(loadAll)
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: var(--color-bg-secondary);
}
.content {
  max-width: 900px;
  margin: 0 auto;
  padding: 32px 24px 60px;
}
.content-header { margin-bottom: 24px; }
.content-header h1 { font-size: 20px; font-weight: 700; color: var(--color-text-primary); }
.sub { font-size: 12.5px; color: var(--color-text-secondary); margin-top: 4px; }

.section { margin-bottom: 28px; }
.section-title {
  font-size: 13px;
  font-weight: 700;
  color: var(--color-text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.04em;
  margin-bottom: 12px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.count {
  font-size: 11px;
  color: var(--color-text-muted);
  background: var(--color-bg-tertiary);
  border-radius: 10px;
  padding: 1px 8px;
}

.list { display: flex; flex-direction: column; gap: 10px; }
.row {
  display: flex;
  align-items: center;
  gap: 16px;
  background: var(--color-bg-primary);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: 16px 18px;
}
.row-muted { opacity: 0.7; }
.row-main { flex: 1; min-width: 0; }
.row-main h3 { font-size: 14px; font-weight: 600; color: var(--color-text-primary); margin-bottom: 3px; }
.row-main p {
  font-size: 12px;
  color: var(--color-text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.btn-sm { padding: 6px 12px; font-size: 12.5px; white-space: nowrap; }

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

@media (max-width: 640px) {
  .row { flex-wrap: wrap; }
}
</style>
