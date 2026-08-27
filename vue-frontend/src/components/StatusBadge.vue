<template>
  <span class="status-badge" :class="statusClass">
    <span class="dot"></span>
    {{ label }}
  </span>
</template>

<script setup>
import { computed } from 'vue'

// 접근 신청(Access Request) / 멤버십 상태 표시.
// PENDING(승인 대기) · ACTIVE(활성) · REJECTED(거절) · CANCELLED(회수)
const props = defineProps({
  status: {
    type: String,
    required: true,
    validator: (v) => ['PENDING', 'ACTIVE', 'REJECTED', 'CANCELLED'].includes(v)
  }
})

const labelMap = {
  PENDING: '승인 대기',
  ACTIVE: '활성',
  REJECTED: '거절됨',
  CANCELLED: '회수됨'
}

const label = computed(() => labelMap[props.status] ?? props.status)
const statusClass = computed(() => `status-${props.status.toLowerCase()}`)
</script>

<style scoped>
.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 10px;
  border-radius: 20px;
  font-size: 11px;
  font-weight: 600;
  white-space: nowrap;
}
.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
}

.status-active {
  background: var(--color-success-light);
  color: var(--color-success);
}
.status-active .dot { background: var(--color-success); }

.status-pending {
  background: var(--color-warning-light);
  color: var(--color-warning);
}
.status-pending .dot { background: var(--color-warning); }

.status-rejected {
  background: var(--color-danger-light);
  color: var(--color-danger);
}
.status-rejected .dot { background: var(--color-danger); }

.status-cancelled {
  background: var(--color-bg-tertiary);
  color: var(--color-text-muted);
}
.status-cancelled .dot { background: var(--color-text-muted); }
</style>
