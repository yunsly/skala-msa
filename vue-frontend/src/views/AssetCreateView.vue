<template>
  <div class="stub-page">
    <p class="eyebrow">/projects/:id/assets/new · LEADER/ADMIN</p>
    <h1>자산 등록</h1>
    <p class="desc">신규 자산(API_KEY/DB_CREDENTIAL/SUBSCRIPTION_PLAN) 등록 폼 — 6단계에서 구현 예정.</p>
  </div>
</template>

<script setup>
// TODO(6단계): AssetCreateView 구현
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
