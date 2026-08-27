<template>
  <span class="category-badge">
    <svg v-if="type === 'API_KEY'" class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7">
      <circle cx="8" cy="15" r="4"/><path d="M11 12l8-8M16 4l3 3M13 7l2 2"/>
    </svg>
    <svg v-else-if="type === 'DB_CREDENTIAL'" class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7">
      <ellipse cx="12" cy="6" rx="7" ry="3"/><path d="M5 6v6c0 1.7 3.1 3 7 3s7-1.3 7-3V6"/><path d="M5 12v6c0 1.7 3.1 3 7 3s7-1.3 7-3v-6"/>
    </svg>
    <svg v-else-if="type === 'SUBSCRIPTION_PLAN'" class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7">
      <rect x="3" y="5" width="18" height="14" rx="2"/><line x1="3" y1="10" x2="21" y2="10"/>
    </svg>
    <span class="label mono">{{ label }}</span>
  </span>
</template>

<script setup>
import { computed } from 'vue'

// 자산 유형 배지 — 위험도와 무관한 정보성 표시이므로 색을 쓰지 않는다.
const props = defineProps({
  type: {
    type: String,
    required: true,
    validator: (v) => ['API_KEY', 'DB_CREDENTIAL', 'SUBSCRIPTION_PLAN'].includes(v)
  }
})

const labelMap = {
  API_KEY: 'API_KEY',
  DB_CREDENTIAL: 'DB_CREDENTIAL',
  SUBSCRIPTION_PLAN: 'SUBSCRIPTION_PLAN'
}

const label = computed(() => labelMap[props.type] ?? props.type)
</script>

<style scoped>
.category-badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 3px 9px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--color-border);
  background: var(--color-bg-tertiary);
  color: var(--color-text-secondary);
}
.icon {
  width: 12px;
  height: 12px;
  flex-shrink: 0;
}
.label {
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.02em;
}
</style>
