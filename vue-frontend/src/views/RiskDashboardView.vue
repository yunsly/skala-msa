<template>
  <div class="stub-page">
    <p class="eyebrow">/risk-dashboard · ADMIN 전용</p>
    <h1>위험도 대시보드</h1>
    <p class="desc">전사 프로젝트 통합 위험도·만료 알림 대시보드(FR-05, FEAT-FE-08) — 10단계에서 구현 예정.</p>
  </div>
</template>

<script setup>
// TODO(10단계): RiskDashboardView 구현
// 백엔드와 먼저 맞춰야 함: 전사 통합 엔드포인트(GET /api/recommend/analyze) 유무.
// 없으면 recommend.js 안에서 프로젝트별 analyze를 순차 호출해 클라이언트 합산.
</script>

<style scoped>
.stub-page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  background: var(--color-bg-primary);
  color: var(--color-text-primary);
  text-align: center;
  padding: 24px;
}
.eyebrow {
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--color-text-muted);
}
h1 { font-size: 20px; }
.desc {
  font-size: 13px;
  color: var(--color-text-secondary);
  max-width: 480px;
}
</style>
