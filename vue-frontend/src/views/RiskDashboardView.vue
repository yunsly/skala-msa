<template>
  <div class="page">
    <AppNav />

    <main class="content">
      <div class="content-header">
        <div>
          <h1>위험도 대시보드</h1>
          <p class="sub">규칙 기반 위험도 엔진이 산출한 전사 통합 리포트입니다.</p>
        </div>
        <div v-if="!loading && !error" class="refresh-box">
          <span class="mono analyzed-at">마지막 분석 · {{ formatDate(report.analyzedAt) }}</span>
          <button type="button" class="btn btn-secondary btn-sm" :disabled="loading" @click="load">새로고침</button>
        </div>
      </div>

      <!-- 로딩 -->
      <div v-if="loading" class="state-box">불러오는 중... (프로젝트별 위험도 분석에는 다소 시간이 걸릴 수 있습니다)</div>

      <!-- 에러 -->
      <div v-else-if="error" class="state-box">
        <p>{{ error }}</p>
        <button type="button" class="btn btn-secondary" @click="load">다시 시도</button>
      </div>

      <template v-else>
        <!-- 부분 실패 안내 -->
        <div v-if="report.failedProjectCount > 0" class="notice">
          프로젝트 {{ report.failedProjectCount }}개는 위험도 분석에 실패해 이번 리포트에서 빠졌습니다. 나머지 {{ report.totalProjects - report.failedProjectCount }}개 프로젝트 결과만 표시합니다.
        </div>

        <!-- 경고 배너 -->
        <div v-if="report.criticalAssets.length > 0" class="banner">
          <span class="banner-dot"></span>
          전사 기준 즉시 조치가 필요한 <strong>CRITICAL</strong> 등급 자산 {{ report.criticalAssets.length }}개
          <span v-if="report.highAssets.length" class="banner-sub">· HIGH 등급 {{ report.highAssets.length }}개</span>
        </div>
        <div v-else-if="report.highAssets.length > 0" class="banner banner-warn">
          CRITICAL 등급은 없지만, 검토가 필요한 <strong>HIGH</strong> 등급 자산 {{ report.highAssets.length }}개가 있습니다.
        </div>
        <div v-else class="banner banner-ok">현재 CRITICAL/HIGH 등급 자산이 없습니다.</div>

        <!-- 프로젝트 필터 -->
        <div v-if="report.projectSummaries.length > 1" class="filter-row">
          <label class="form-label" for="project-filter">프로젝트 필터</label>
          <select id="project-filter" v-model="selectedProjectId" class="form-select">
            <option :value="null">전체 프로젝트</option>
            <option v-for="p in report.projectSummaries" :key="p.projectId" :value="p.projectId">{{ p.projectName }}</option>
          </select>
        </div>

        <!-- 프로젝트별 위험 요약 -->
        <section v-if="filteredProjectSummaries.length" class="section">
          <h2 class="section-title">프로젝트별 위험 요약</h2>
          <div class="list">
            <div v-for="p in filteredProjectSummaries" :key="p.projectId" class="row">
              <div class="row-main">
                <h3>{{ p.projectName }}</h3>
                <p class="mono">CRITICAL/HIGH 자산 {{ p.criticalOrHighCount }}개</p>
              </div>
              <RiskBadge :level="p.maxLevel" :score="p.maxScore" />
            </div>
          </div>
        </section>

        <!-- 통합 위험 자산 리스트 -->
        <section class="section">
          <h2 class="section-title">전사 위험 자산 <span class="count mono">{{ filteredAssets.length }}</span></h2>

          <div v-if="filteredAssets.length === 0" class="state-box small">분석된 위험 자산이 없습니다.</div>
          <div v-else class="list">
            <router-link
              v-for="a in filteredAssets"
              :key="`${a.projectId}-${a.id}`"
              :to="`/courses/${a.id}`"
              class="row"
            >
              <div class="row-main">
                <h3>{{ a.title }}</h3>
                <p class="mono">{{ a.projectName }}</p>
              </div>
              <RiskBadge :level="a.riskLevel" :score="a.riskScore" />
            </router-link>
          </div>
        </section>

        <!-- 만료/회전/갱신 섹션 -->
        <div class="triple-grid">
          <section class="section">
            <h2 class="section-title">만료 임박 API Key <span class="count mono">{{ filteredExpiring.length }}</span></h2>
            <ul v-if="filteredExpiring.length" class="mini-list">
              <li v-for="a in filteredExpiring" :key="a.id">
                <div class="mini-top">
                  <router-link :to="`/courses/${a.id}`">{{ a.title }}</router-link>
                  <span class="mono muted">{{ a.projectName }}</span>
                </div>
                <p class="evidence">{{ a.evidence }}</p>
                <p class="action">→ {{ a.recommendedAction }}</p>
              </li>
            </ul>
            <p v-else class="empty-mini">없음</p>
          </section>

          <section class="section">
            <h2 class="section-title">회전 필요 Key <span class="count mono">{{ filteredRotation.length }}</span></h2>
            <ul v-if="filteredRotation.length" class="mini-list">
              <li v-for="a in filteredRotation" :key="a.id">
                <div class="mini-top">
                  <router-link :to="`/courses/${a.id}`">{{ a.title }}</router-link>
                  <span class="mono muted">{{ a.projectName }}</span>
                </div>
                <p class="evidence">{{ a.evidence }}</p>
                <p class="action">→ {{ a.recommendedAction }}</p>
              </li>
            </ul>
            <p v-else class="empty-mini">없음</p>
          </section>

          <section class="section">
            <h2 class="section-title">갱신 임박 구독 Plan <span class="count mono">{{ filteredRenewal.length }}</span></h2>
            <ul v-if="filteredRenewal.length" class="mini-list">
              <li v-for="a in filteredRenewal" :key="a.id">
                <div class="mini-top">
                  <router-link :to="`/courses/${a.id}`">{{ a.title }}</router-link>
                  <span class="mono muted">{{ a.projectName }}</span>
                </div>
                <p class="evidence">{{ a.evidence }}</p>
                <p class="action">→ {{ a.recommendedAction }}</p>
              </li>
            </ul>
            <p v-else class="empty-mini">없음</p>
          </section>
        </div>
      </template>
    </main>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import AppNav from '@/components/AppNav.vue'
import RiskBadge from '@/components/RiskBadge.vue'
import { recommendApi } from '@/api/recommend.js'

const loading = ref(true)
const error = ref('')
const report = ref({
  analyzedAt: null,
  totalProjects: 0,
  failedProjectCount: 0,
  criticalAssets: [],
  highAssets: [],
  allAssets: [],
  projectSummaries: [],
  expiringApiKeys: [],
  rotationNeeded: [],
  renewalDueSubscriptions: []
})

const selectedProjectId = ref(null)

function byProjectFilter(list) {
  if (selectedProjectId.value == null) return list
  return list.filter((a) => String(a.projectId) === String(selectedProjectId.value))
}

const filteredAssets = computed(() => byProjectFilter(report.value.allAssets))
const filteredExpiring = computed(() => byProjectFilter(report.value.expiringApiKeys))
const filteredRotation = computed(() => byProjectFilter(report.value.rotationNeeded))
const filteredRenewal = computed(() => byProjectFilter(report.value.renewalDueSubscriptions))
const filteredProjectSummaries = computed(() =>
  selectedProjectId.value == null
    ? report.value.projectSummaries
    : report.value.projectSummaries.filter((p) => String(p.projectId) === String(selectedProjectId.value))
)

function formatDate(value) {
  if (!value) return '-'
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return value
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

async function load() {
  loading.value = true
  error.value = ''

  try {
    report.value = await recommendApi.getEnterpriseRiskReport()
  } catch (e) {
    // recommend-service 개별 프로젝트 실패는 recommend.js 안에서 이미 흡수한다.
    // 여기로 넘어오는 에러는 프로젝트 목록(course-service) 조회 실패 등 더 근본적인
    // 문제라 리포트 자체를 못 그린다.
    console.error('[RiskDashboardView] 전사 위험도 리포트 조회 실패:', e)
    error.value = '위험도 리포트를 불러오지 못했습니다.'
  } finally {
    loading.value = false
  }
}

onMounted(load)
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
  gap: 16px;
  margin-bottom: 20px;
}
.content-header h1 { font-size: 20px; font-weight: 700; color: var(--color-text-primary); }
.sub { font-size: 12.5px; color: var(--color-text-secondary); margin-top: 4px; }
.refresh-box { display: flex; align-items: center; gap: 10px; flex-shrink: 0; }
.analyzed-at { font-size: 11px; color: var(--color-text-muted); white-space: nowrap; }
.btn-sm { padding: 6px 12px; font-size: 12.5px; }

.notice {
  background: var(--color-warning-light);
  border: 1px solid var(--color-warning);
  color: var(--color-warning);
  border-radius: var(--radius-md);
  padding: 10px 14px;
  font-size: 12.5px;
  margin-bottom: 16px;
}

.banner {
  display: flex;
  align-items: center;
  gap: 10px;
  background: var(--color-danger-light);
  border: 1px solid var(--color-danger);
  color: var(--color-danger);
  border-radius: var(--radius-lg);
  padding: 14px 18px;
  font-size: 13.5px;
  margin-bottom: 20px;
}
.banner-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--color-danger);
  flex-shrink: 0;
}
.banner-sub { opacity: 0.8; font-size: 12.5px; }
.banner-warn {
  background: var(--color-warning-light);
  border-color: var(--color-warning);
  color: var(--color-warning);
}
.banner-ok {
  background: var(--color-success-light);
  border-color: var(--color-success);
  color: var(--color-success);
}

.filter-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 20px;
}
.form-label { font-size: 12px; font-weight: 500; color: var(--color-text-secondary); }
.form-select {
  padding: 7px 12px;
  border: 1.5px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-primary);
  color: var(--color-text-primary);
  font-size: 12.5px;
  outline: none;
}
.form-select:focus { border-color: var(--color-primary); box-shadow: 0 0 0 3px var(--color-primary-light); }

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

.list { display: flex; flex-direction: column; gap: 8px; }
.row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  background: var(--color-bg-primary);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: 13px 18px;
}
.row:hover { border-color: var(--color-border-hover); }
.row-main h3 { font-size: 13.5px; font-weight: 600; color: var(--color-text-primary); margin-bottom: 2px; }
.row-main p { font-size: 11px; color: var(--color-text-muted); }

.triple-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
}
.mini-list { list-style: none; display: flex; flex-direction: column; gap: 12px; padding: 0; margin: 0; }
.mini-list li {
  padding-bottom: 10px;
  border-bottom: 1px solid var(--color-border);
}
.mini-top { display: flex; justify-content: space-between; gap: 8px; font-size: 12.5px; margin-bottom: 3px; }
.mini-list a { color: var(--color-text-primary); }
.mini-list a:hover { color: var(--color-primary); }
.muted { color: var(--color-text-muted); font-size: 11px; white-space: nowrap; }
.evidence { font-size: 11px; color: var(--color-text-secondary); margin-bottom: 2px; }
.action { font-size: 11px; color: var(--color-primary); }
.empty-mini { font-size: 12px; color: var(--color-text-muted); }

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
.state-box.small { padding: 30px 0; }

@media (max-width: 900px) {
  .triple-grid { grid-template-columns: 1fr; }
  .content-header { flex-direction: column; }
}
</style>
