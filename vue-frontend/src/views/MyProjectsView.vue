<template>
  <div class="stub-page">
    <p class="eyebrow">/my-projects</p>
    <h1>내 프로젝트</h1>
    <p class="desc">내가 속한(ACTIVE)/신청 대기(PENDING) 프로젝트 목록 — 8단계에서 구현 예정.</p>
  </div>
</template>

<script setup>
// TODO(8단계): MyProjectsView 구현
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
