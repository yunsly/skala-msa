<template>
  <div class="stub-page">
    <p class="eyebrow">/courses/:id · 승인된 멤버/ADMIN</p>
    <h1>자산 상세</h1>
    <p class="desc">자산 메타데이터, Secret 마스킹/표시(SecretViewer), 만료일, 감사 정보(last_accessed_at) — 7단계에서 구현 예정.</p>
  </div>
</template>

<script setup>
// TODO(7단계): AssetDetailView 구현 (SecretViewer 포함)
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
