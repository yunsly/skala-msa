<template>
  <div class="secret-viewer">
    <div class="value-row">
      <span v-if="!revealed" class="value mono masked">{{ maskedText }}</span>
      <span v-else class="value mono revealed" :title="secretValue">{{ secretValue }}</span>

      <div class="actions">
        <button
          v-if="!revealed"
          type="button"
          class="icon-btn"
          :disabled="loading"
          :title="loading ? '조회 중...' : '표시'"
          @click="handleReveal"
        >
          <svg v-if="!loading" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
            <path d="M1 12s4-7 11-7 11 7 11 7-4 7-11 7-11-7-11-7Z"/><circle cx="12" cy="12" r="3"/>
          </svg>
          <svg v-else class="spin" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
            <path d="M12 2a10 10 0 0 1 10 10" stroke-linecap="round"/>
          </svg>
        </button>

        <template v-else>
          <button type="button" class="icon-btn" title="숨기기" @click="handleHide">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
              <path d="M17.94 17.94A10.94 10.94 0 0 1 12 19c-7 0-11-7-11-7a20.3 20.3 0 0 1 4.22-5.06M9.9 4.24A10.5 10.5 0 0 1 12 4c7 0 11 7 11 7a20.4 20.4 0 0 1-2.16 3.19M14.12 14.12A3 3 0 1 1 9.88 9.88"/>
              <line x1="1" y1="1" x2="23" y2="23"/>
            </svg>
          </button>
          <button type="button" class="icon-btn" :title="copied ? '복사됨' : '복사'" @click="handleCopy">
            <svg v-if="!copied" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
              <rect x="9" y="9" width="12" height="12" rx="1.5"/><path d="M5 15V4.5A1.5 1.5 0 0 1 6.5 3H15"/>
            </svg>
            <svg v-else viewBox="0 0 24 24" fill="none" stroke="var(--color-success)" stroke-width="2.2">
              <polyline points="4 12 9 17 20 6"/>
            </svg>
          </button>
        </template>
      </div>
    </div>

    <p v-if="error" class="hint error">{{ error }} · <button type="button" class="retry" @click="handleReveal">다시 시도</button></p>
    <p v-else-if="revealed" class="hint">(SAMPLE) 화면을 벗어나거나 새로고침하면 다시 마스킹됩니다. 조회 시점은 감사 로그에 기록됩니다.</p>
    <p v-else class="hint">표시 아이콘을 클릭하면 평문이 임시로 노출되며, 조회 이력이 감사 로그에 남습니다.</p>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { courseApi } from '@/api/course.js'

// 자산 상세(AssetDetailView)에서 사용. 기본은 마스킹, "표시" 클릭 시에만
// GET /api/courses/{assetId}/secret 을 호출한다(자동 프리페치 금지).
// 클릭할 때마다 새로 조회하도록 두어 감사 로그가 실제 열람 횟수와 어긋나지
// 않게 하고, 숨기면 메모리에 남겨두지 않고 값을 비운다.
const props = defineProps({
  assetId: {
    type: [String, Number],
    required: true
  },
  maskLength: {
    type: Number,
    default: 24
  }
})

const emit = defineEmits(['revealed'])

const revealed = ref(false)
const loading = ref(false)
const error = ref('')
const copied = ref(false)
const secretValue = ref('')

const maskedText = computed(() => '•'.repeat(props.maskLength))

async function handleReveal() {
  error.value = ''
  loading.value = true

  try {
    const res = await courseApi.getSecret(props.assetId)
    // 응답 스키마는 백엔드 확정 후 확인 필요 — data.secretValue 우선, 없으면 대안 필드 탐색
    const payload = res.data?.data ?? res.data
    const value = payload?.secretValue ?? payload?.value ?? payload?.secret

    if (!value) {
      throw new Error('Secret 값을 응답에서 찾을 수 없습니다.')
    }

    secretValue.value = value
    revealed.value = true
    emit('revealed', payload)
  } catch (e) {
    console.error('[SecretViewer] Secret 조회 실패:', e)
    error.value = e.response?.data?.message || 'Secret 조회에 실패했습니다.'
  } finally {
    loading.value = false
  }
}

function handleHide() {
  revealed.value = false
  secretValue.value = ''
  copied.value = false
}

async function handleCopy() {
  try {
    await navigator.clipboard.writeText(secretValue.value)
    copied.value = true
    setTimeout(() => { copied.value = false }, 1500)
  } catch (e) {
    console.error('[SecretViewer] 클립보드 복사 실패:', e)
  }
}
</script>

<style scoped>
.secret-viewer {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.value-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  background: var(--color-bg-primary);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 10px 12px;
}
.value {
  font-size: 13px;
  letter-spacing: 0.03em;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--color-text-primary);
}
.value.masked {
  color: var(--color-text-muted);
}
.actions {
  display: flex;
  gap: 4px;
  flex-shrink: 0;
}
.icon-btn {
  width: 26px;
  height: 26px;
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  color: var(--color-text-secondary);
}
.icon-btn:hover:not(:disabled) {
  background: var(--color-bg-tertiary);
  color: var(--color-text-primary);
}
.icon-btn:disabled {
  opacity: 0.6;
  cursor: default;
}
.icon-btn svg {
  width: 15px;
  height: 15px;
}
.spin {
  animation: spin 0.8s linear infinite;
}
@keyframes spin {
  to { transform: rotate(360deg); }
}
.hint {
  font-size: 11px;
  color: var(--color-text-muted);
  line-height: 1.5;
}
.hint.error {
  color: var(--color-danger);
}
.retry {
  background: none;
  border: none;
  color: var(--color-danger);
  text-decoration: underline;
  font-size: 11px;
  cursor: pointer;
  padding: 0;
}
</style>
