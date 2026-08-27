<template>
  <div class="stub-page">
    <p class="eyebrow">/approval · LEADER/ADMIN</p>
    <h1>접근 승인 큐</h1>
    <p class="desc">내 프로젝트로 들어온 접근 신청 승인/거절(decisionReason 입력) — 9단계에서 구현 예정.</p>
  </div>
</template>

<script setup>
// TODO(9단계): ApprovalQueueView 구현
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
