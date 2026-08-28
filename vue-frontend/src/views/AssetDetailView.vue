<template>
  <div class="page">
    <AppNav />

    <main class="content" v-if="!pageLoading">
      <div v-if="pageError" class="state-box">
        <p>{{ pageError }}</p>
        <router-link to="/projects" class="btn btn-secondary">프로젝트 카탈로그로</router-link>
      </div>

      <template v-else-if="!canView">
        <div class="access-gate">
          <p>이 자산은 승인된 프로젝트 멤버만 볼 수 있습니다.</p>
          <router-link :to="`/projects/${asset.projectId}`" class="btn btn-primary">프로젝트로 이동</router-link>
        </div>
      </template>

      <template v-else>
        <router-link :to="`/projects/${asset.projectId}`" class="back-link mono">← 프로젝트로 돌아가기</router-link>

        <div class="detail-layout">
          <div class="detail-main">
            <CategoryBadge :type="asset.category" />
            <h1>{{ asset.title }}</h1>
            <p class="desc">{{ asset.description || '설명이 없습니다.' }}</p>

            <div class="meta-panel">
              <div class="meta-row">
                <span class="k">제공자</span>
                <span class="v">{{ asset.provider || '-' }}</span>
              </div>
              <div v-if="asset.planName" class="meta-row">
                <span class="k">플랜</span>
                <span class="v">{{ asset.planName }}</span>
              </div>
              <div v-if="asset.expiresAt" class="meta-row">
                <span class="k">만료일</span>
                <span class="v mono">{{ formatDate(asset.expiresAt) }}</span>
              </div>
              <div class="meta-row">
                <span class="k">등록일</span>
                <span class="v mono">{{ formatDate(asset.createdAt) || '-' }}</span>
              </div>
              <div class="meta-row" style="border-bottom: none;">
                <span class="k">최근 조회</span>
                <span class="v mono">{{ formatDate(lastAccessedAt) || '조회 기록 없음' }}</span>
              </div>
            </div>
          </div>

          <div class="detail-side">
            <div class="side-card">
              <div class="side-title">Secret Value</div>
              <SecretViewer :asset-id="assetId" @revealed="handleRevealed" />
            </div>
          </div>
        </div>
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
import CategoryBadge from '@/components/CategoryBadge.vue'
import SecretViewer from '@/components/SecretViewer.vue'
import { useAuthStore } from '@/store/auth.js'
import { courseApi } from '@/api/course.js'
import { enrollmentApi } from '@/api/enrollment.js'

const route = useRoute()
const auth = useAuthStore()

const assetId = computed(() => route.params.id)

const pageLoading = ref(true)
const pageError = ref('')
const asset = ref({})
const canView = ref(false)
const lastAccessedAt = ref(null)

function formatDate(value) {
  if (!value) return ''
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return value
  // toISOString()은 UTC로 변환해버려 타임존이 없는 서버 타임스탬프(KST 가정)를
  // 그대로 보여줘야 하는 이 화면에는 안 맞는다. 로컬 값 그대로 포맷팅한다.
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

function handleRevealed(payload) {
  const value = payload?.lastAccessedAt ?? payload?.last_accessed_at
  if (value) lastAccessedAt.value = value
}

async function loadAsset() {
  pageLoading.value = true
  pageError.value = ''

  try {
    const assetRes = await courseApi.getAsset(assetId.value)
    const found = assetRes.data?.data ?? assetRes.data
    if (!found || typeof found !== 'object') {
      pageError.value = '자산을 찾을 수 없습니다.'
      return
    }
    asset.value = found
    lastAccessedAt.value = found.lastAccessedAt ?? found.last_accessed_at ?? null

    if (auth.isAdmin) {
      canView.value = true
      return
    }

    // 이 자산이 속한 프로젝트의 리더인지 / 승인된(ACTIVE) 멤버인지 확인.
    // 프로젝트 단건 조회 API가 없어 목록에서 찾는다(다른 화면과 동일 패턴).
    const [projectsRes, myRes] = await Promise.all([
      courseApi.getProjects(),
      enrollmentApi.getMyProjects().catch((e) => {
        console.error('[AssetDetailView] 내 프로젝트 현황 조회 실패:', e)
        return { data: [] }
      })
    ])

    const projects = Array.isArray(projectsRes.data?.data)
      ? projectsRes.data.data
      : Array.isArray(projectsRes.data)
        ? projectsRes.data
        : []
    const project = projects.find((p) => String(p.id) === String(found.projectId))
    const leaderId = project?.leaderId ?? project?.leader_id ?? project?.ownerId
    const isLeader = leaderId != null && String(leaderId) === String(auth.user?.id)

    const myProjectsData = myRes.data?.data ?? {}
    const myProjects = [
      ...(myProjectsData.activeProjects ?? []),
      ...(myProjectsData.pendingProjects ?? []),
      ...(myProjectsData.cancelledProjects ?? [])
    ]
    const match = myProjects.find((m) => String(m.projectId ?? m.id) === String(found.projectId))

    canView.value = isLeader || match?.status === 'ACTIVE'
  } catch (e) {
    console.error('[AssetDetailView] 자산 조회 실패:', e)
    pageError.value = '자산 정보를 불러오지 못했습니다.'
  } finally {
    pageLoading.value = false
  }
}

onMounted(loadAsset)
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: var(--color-bg-secondary);
}
.content {
  max-width: 1000px;
  margin: 0 auto;
  padding: 32px 24px 60px;
}
.back-link {
  display: inline-block;
  font-size: 11px;
  color: var(--color-text-muted);
  margin-bottom: 20px;
}
.back-link:hover { color: var(--color-primary); }

.detail-layout {
  display: grid;
  grid-template-columns: 1fr 340px;
  gap: 28px;
  align-items: start;
}
.detail-main h1 { font-size: 22px; font-weight: 700; color: var(--color-text-primary); margin: 12px 0 10px; }
.desc { font-size: 13px; color: var(--color-text-secondary); line-height: 1.7; max-width: 560px; margin-bottom: 20px; }

.meta-panel {
  background: var(--color-bg-primary);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: 4px 18px;
  max-width: 480px;
}
.meta-row {
  display: flex;
  justify-content: space-between;
  padding: 10px 0;
  border-bottom: 1px solid var(--color-border);
  font-size: 12.5px;
}
.meta-row .k { color: var(--color-text-secondary); }
.meta-row .v { color: var(--color-text-primary); font-weight: 500; }

.side-card {
  background: var(--color-bg-primary);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: 18px;
}
.side-title {
  font-size: 11px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--color-text-secondary);
  margin-bottom: 10px;
}

.access-gate {
  background: var(--color-bg-primary);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: 48px 24px;
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  color: var(--color-text-secondary);
  font-size: 13px;
}

.state-box {
  text-align: center;
  padding: 80px 0;
  color: var(--color-text-muted);
  font-size: 13px;
}

@media (max-width: 800px) {
  .detail-layout { grid-template-columns: 1fr; }
}
</style>
