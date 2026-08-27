<template>
  <div class="page">
    <AppNav />

    <main class="content">
      <div class="content-header">
        <h1>위험도 대시보드</h1>
        <p class="sub">규칙 기반 위험도 엔진이 산출한 전사 통합 리포트입니다.</p>
      </div>

      <!-- 로딩 -->
      <div v-if="loading" class="state-box">불러오는 중... (프로젝트별 위험도 분석에는 다소 시간이 걸릴 수 있습니다)</div>

      <!-- 에러 -->
      <div v-else-if="error" class="state-box">
        <p>{{ error }}</p>
        <button type="button" class="btn btn-secondary" @click="load">다시 시도</button>
      </div>

      <template v-else>
        <!-- 경고 배너 -->
        <div v-if="report.criticalAssets.length > 0" class="banner">
          <span class="banner-dot"></span>
          전사 기준 즉시 조치가 필요한 <strong>CRITICAL</strong> 등급 자산 {{ report.criticalAssets.length }}개
        </div>
        <div v-else class="banner banner-ok">현재 CRITICAL 등급 자산이 없습니다.</div>

        <!-- 통합 위험 자산 리스트 -->
        <section class="section">
          <h2 class="section-title">전사 위험 자산 <span class="count mono">{{ report.allAssets.length }}</span></h2>

          <div v-if="report.allAssets.length === 0" class="state-box small">분석된 위험 자산이 없습니다.</div>
          <div v-else class="list">
            <router-link
              v-for="a in report.allAssets"
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
            <h2 class="section-title">만료 임박 API Key <span class="count mono">{{ report.expiringApiKeys.length }}</span></h2>
            <ul v-if="report.expiringApiKeys.length" class="mini-list">
              <li v-for="a in report.expiringApiKeys" :key="a.id">
                <router-link :to="`/courses/${a.id}`">{{ a.title }}</router-link>
                <span class="mono muted">{{ a.projectName }}</span>
              </li>
            </ul>
            <p v-else class="empty-mini">없음</p>
          </section>

          <section class="section">
            <h2 class="section-title">회전 필요 Key <span class="count mono">{{ report.rotationNeeded.length }}</span></h2>
            <ul v-if="report.rotationNeeded.length" class="mini-list">
              <li v-for="a in report.rotationNeeded" :key="a.id">
                <router-link :to="`/courses/${a.id}`">{{ a.title }}</router-link>
                <span class="mono muted">{{ a.projectName }}</span>
              </li>
            </ul>
            <p v-else class="empty-mini">없음</p>
          </section>

          <section class="section">
            <h2 class="section-title">갱신 임박 구독 Plan <span class="count mono">{{ report.renewalDueSubscriptions.length }}</span></h2>
            <ul v-if="report.renewalDueSubscriptions.length" class="mini-list">
              <li v-for="a in report.renewalDueSubscriptions" :key="a.id">
                <router-link :to="`/courses/${a.id}`">{{ a.title }}</router-link>
                <span class="mono muted">{{ a.projectName }}</span>
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
import { ref, onMounted } from 'vue'
import AppNav from '@/components/AppNav.vue'
import RiskBadge from '@/components/RiskBadge.vue'
import { recommendApi } from '@/api/recommend.js'

const loading = ref(true)
const error = ref('')
const report = ref({
  totalProjects: 0,
  criticalAssets: [],
  allAssets: [],
  expiringApiKeys: [],
  rotationNeeded: [],
  renewalDueSubscriptions: []
})

async function load() {
  loading.value = true
  error.value = ''

  try {
    report.value = await recommendApi.getEnterpriseRiskReport()
  } catch (e) {
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
.content-header { margin-bottom: 20px; }
.content-header h1 { font-size: 20px; font-weight: 700; color: var(--color-text-primary); }
.sub { font-size: 12.5px; color: var(--color-text-secondary); margin-top: 4px; }

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
  margin-bottom: 28px;
}
.banner-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--color-danger);
  flex-shrink: 0;
}
.banner-ok {
  background: var(--color-success-light);
  border-color: var(--color-success);
  color: var(--color-success);
}

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
.mini-list { list-style: none; display: flex; flex-direction: column; gap: 8px; padding: 0; margin: 0; }
.mini-list li {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  font-size: 12.5px;
  padding: 8px 0;
  border-bottom: 1px solid var(--color-border);
}
.mini-list a { color: var(--color-text-primary); }
.mini-list a:hover { color: var(--color-primary); }
.muted { color: var(--color-text-muted); font-size: 11px; white-space: nowrap; }
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
}
</style>
