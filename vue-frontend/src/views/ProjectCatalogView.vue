<template>
  <div class="stub-page">
    <p class="eyebrow">/projects</p>
    <h1>프로젝트 카탈로그</h1>
    <p class="desc">전사 프로젝트 목록, 접근 신청 모달(reason 입력) — 4단계에서 구현 예정.</p>
  </div>
</template>

<script setup>
// TODO(4단계): ProjectCatalogView 구현
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
