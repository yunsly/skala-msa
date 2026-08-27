<template>
  <span class="risk-badge" :class="levelClass">
    <span class="dot"></span>
    <span class="label">{{ label }}</span>
    <span v-if="score !== null" class="score mono">{{ score }}</span>
  </span>
</template>

<script setup>
import { computed } from 'vue'

// FR-05 규칙 기반 위험도 엔진(recommend-service)이 산출한 등급을 표시한다.
// "AI 추천"이 아니라 "규칙 기반 위험도"이므로 문구에 AI라는 표현을 쓰지 않는다.
const props = defineProps({
  level: {
    type: String,
    required: true,
    validator: (v) => ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'].includes(v)
  },
  score: {
    type: Number,
    default: null
  }
})

const labelMap = {
  CRITICAL: 'Critical',
  HIGH: 'High',
  MEDIUM: 'Medium',
  LOW: 'Low'
}

const label = computed(() => labelMap[props.level] ?? props.level)
const levelClass = computed(() => `level-${props.level.toLowerCase()}`)
</script>

<style scoped>
.risk-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 10px;
  border-radius: 20px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.02em;
  white-space: nowrap;
}
.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
}
.score {
  opacity: 0.85;
  font-size: 10px;
}

/* CRITICAL만 danger를 꽉 채워 사용 — 화면에 여러 개 떠도 전체가 붉어지지 않도록
   나머지 등급은 옅은 워시 배경만 쓴다. */
.level-critical {
  background: var(--color-danger);
  color: #fff;
}
.level-critical .dot { background: #fff; }

.level-high {
  background: var(--color-danger-light);
  color: var(--color-danger);
}
.level-high .dot { background: var(--color-danger); }

.level-medium {
  background: var(--color-warning-light);
  color: var(--color-warning);
}
.level-medium .dot { background: var(--color-warning); }

.level-low {
  background: var(--color-warning-light);
  color: var(--color-text-muted);
}
.level-low .dot { background: var(--color-text-muted); }
</style>
