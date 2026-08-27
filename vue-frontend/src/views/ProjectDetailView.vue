<template>
  <div class="stub-page">
    <p class="eyebrow">/projects/:id</p>
    <h1>프로젝트 상세</h1>
    <p class="desc">프로젝트 메타데이터 + 자산 목록(승인 멤버/ADMIN만 노출), LEADER/ADMIN이면 [자산 등록] 버튼 — 5단계에서 구현 예정.</p>
  </div>
</template>

<script setup>
// TODO(5단계): ProjectDetailView 구현
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
