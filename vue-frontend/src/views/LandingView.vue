<template>
  <div class="landing">
    <!-- 공개 헤더 -->
    <header class="top-bar">
      <div class="top-bar-inner">
        <div class="brand">
          <span class="brand-mark">K</span>
          <span class="brand-name">KeyNexus</span>
        </div>
        <router-link to="/login" class="btn btn-primary btn-sm">로그인</router-link>
      </div>
    </header>

    <!-- 히어로 -->
    <section class="hero">
      <div class="hero-inner">
        <span class="eyebrow">Credential & Digital Asset Governance</span>
        <h1>흩어진 Credential을,<br>프로젝트 단위로 통제하세요</h1>
        <p class="lead">
          AWS IAM Key, DB 접속정보, 외부 API Secret 같은 디지털 인증 자산을 프로젝트
          단위로 가시화하고, 요청 · 승인 · 활성화 · 회수 전 과정을 하나의 창구에서 관리합니다.
        </p>
        <router-link to="/login" class="btn btn-primary btn-lg">로그인하고 시작하기</router-link>
      </div>
    </section>

    <!-- 요약 통계 -->
    <section class="stats">
      <div class="stats-inner">
        <div class="stat-card">
          <span class="stat-label">전사 프로젝트</span>
          <span class="stat-value mono">{{ statsLoaded ? stats.projectCount : '—' }}</span>
        </div>
        <div class="stat-card">
          <span class="stat-label">등록된 자산</span>
          <span class="stat-value mono">{{ statsLoaded ? stats.assetCount : '—' }}</span>
        </div>
        <div class="stat-card critical">
          <span class="stat-label">Critical 등급 자산</span>
          <span class="stat-value mono">{{ statsLoaded ? stats.criticalCount : '—' }}</span>
        </div>
      </div>
      <p v-if="!statsLoaded" class="stats-hint">로그인하면 전사 통계를 실시간으로 확인할 수 있습니다.</p>
    </section>

    <!-- 핵심 가치 -->
    <section class="features">
      <div class="features-inner">
        <div class="feature">
          <svg class="feature-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6">
            <circle cx="12" cy="12" r="3"/><path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7Z"/>
          </svg>
          <h3>단일 창구 가시화</h3>
          <p>프로젝트를 열면 운영에 필요한 모든 Credential과 책임자를 즉시 확인합니다.</p>
        </div>
        <div class="feature">
          <svg class="feature-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6">
            <rect x="5" y="11" width="14" height="9" rx="1.5"/><path d="M8 11V7a4 4 0 0 1 8 0v4"/>
          </svg>
          <h3>최소 권한 원칙</h3>
          <p>모든 접근은 신청 → 승인 단계를 거치며, 미인가 사용자의 평문 조회를 차단합니다.</p>
        </div>
        <div class="feature">
          <svg class="feature-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6">
            <path d="M3 12h4l3 8 4-16 3 8h4"/>
          </svg>
          <h3>규칙 기반 위험도 분석</h3>
          <p>만료 임박·회전 경과·활성 멤버 수·접근 거절 이력을 점수화해 등급을 매깁니다.</p>
        </div>
        <div class="feature">
          <svg class="feature-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6">
            <path d="M12 3l8 4v5c0 5-3.4 8.4-8 9-4.6-.6-8-4-8-9V7l8-4Z"/>
          </svg>
          <h3>영구적 기업 자산화</h3>
          <p>프로젝트 종료 시 개인에게 흩어진 인증 정보를 기업 관리 자산으로 즉시 전환·회수합니다.</p>
        </div>
      </div>
    </section>

    <footer class="footer">
      <span class="brand-name">KeyNexus</span>
      <span class="mono footer-copy">© 2026 KeyNexus</span>
    </footer>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useAuthStore } from '@/store/auth.js'
import { courseApi } from '@/api/course.js'

const auth = useAuthStore()

const statsLoaded = ref(false)
const stats = ref({ projectCount: 0, assetCount: 0, criticalCount: 0 })

// 비로그인 방문자를 위한 공개 통계 엔드포인트는 아직 없어 보임 — 로그인 상태일 때만
// 프로젝트 목록을 받아 클라이언트에서 합산한다. 백엔드에 공개 통계 엔드포인트가
// 필요한지, 또는 프로젝트 목록 응답에 assetCount/criticalAssetCount가 이미 포함되는지
// 확인 필요.
async function loadStats() {
  if (!auth.isAuthenticated) return

  try {
    const res = await courseApi.getProjects()
    const projects = Array.isArray(res.data?.data) ? res.data.data : Array.isArray(res.data) ? res.data : []

    stats.value = {
      projectCount: projects.length,
      assetCount: projects.reduce((sum, p) => sum + Number(p.assetCount ?? 0), 0),
      criticalCount: projects.reduce((sum, p) => sum + Number(p.criticalAssetCount ?? 0), 0)
    }
    statsLoaded.value = true
  } catch (e) {
    console.error('[LandingView] 요약 통계 조회 실패:', e)
  }
}

onMounted(loadStats)
</script>

<style scoped>
.landing {
  min-height: 100vh;
  background: var(--color-bg-primary);
  color: var(--color-text-primary);
}

.top-bar {
  border-bottom: 1px solid var(--color-border);
  position: sticky;
  top: 0;
  background: var(--color-bg-primary);
  z-index: 10;
}
.top-bar-inner {
  max-width: 1120px;
  margin: 0 auto;
  padding: 0 24px;
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.brand {
  display: flex;
  align-items: center;
  gap: 9px;
}
.brand-mark {
  width: 26px;
  height: 26px;
  border-radius: 6px;
  background: var(--color-primary);
  color: #fff;
  font-family: var(--font-mono);
  font-weight: 700;
  font-size: 13px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.brand-name {
  font-size: 15px;
  font-weight: 700;
}
.btn-sm { padding: 7px 16px; font-size: 13px; }
.btn-lg { padding: 12px 26px; font-size: 15px; }

.hero {
  padding: 88px 24px 64px;
  border-bottom: 1px solid var(--color-border);
}
.hero-inner {
  max-width: 720px;
  margin: 0 auto;
  text-align: center;
}
.eyebrow {
  display: inline-block;
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--color-primary);
  background: var(--color-primary-light);
  padding: 4px 12px;
  border-radius: 20px;
  margin-bottom: 20px;
}
.hero h1 {
  font-size: 36px;
  font-weight: 700;
  line-height: 1.3;
  letter-spacing: -0.5px;
  margin-bottom: 18px;
}
.lead {
  font-size: 15px;
  color: var(--color-text-secondary);
  line-height: 1.75;
  margin-bottom: 32px;
}

.stats {
  padding: 40px 24px;
  border-bottom: 1px solid var(--color-border);
}
.stats-inner {
  max-width: 1120px;
  margin: 0 auto;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}
.stat-card {
  background: var(--color-bg-secondary);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: 22px 24px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.stat-card.critical {
  border-color: var(--color-danger);
}
.stat-label {
  font-size: 12px;
  color: var(--color-text-secondary);
}
.stat-value {
  font-size: 28px;
  font-weight: 700;
}
.stat-card.critical .stat-value {
  color: var(--color-danger);
}
.stats-hint {
  text-align: center;
  font-size: 12px;
  color: var(--color-text-muted);
  margin-top: 14px;
}

.features {
  padding: 64px 24px;
}
.features-inner {
  max-width: 1120px;
  margin: 0 auto;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
}
.feature {
  padding: 24px 20px;
  background: var(--color-bg-secondary);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
}
.feature-icon {
  width: 24px;
  height: 24px;
  color: var(--color-primary);
  margin-bottom: 14px;
}
.feature h3 {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 8px;
}
.feature p {
  font-size: 12.5px;
  color: var(--color-text-secondary);
  line-height: 1.6;
}

.footer {
  border-top: 1px solid var(--color-border);
  padding: 24px;
  max-width: 1120px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.footer-copy {
  font-size: 11px;
  color: var(--color-text-muted);
}

@media (max-width: 900px) {
  .features-inner { grid-template-columns: repeat(2, 1fr); }
  .stats-inner { grid-template-columns: 1fr; }
}
</style>
