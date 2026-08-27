<template>
  <div class="page">
    <AppNav />

    <main class="content">
      <div class="content-header">
        <h1>승인 대기</h1>
        <p class="sub">내 프로젝트로 들어온 접근 신청을 승인하거나 거절하세요.</p>
      </div>

      <!-- 준비 중 (백엔드 승인 API 미연동) -->
      <div v-if="!FEATURES.approvalQueue" class="state-box prep-box">
        <p class="prep-title">승인 대기 기능은 준비 중입니다.</p>
        <p>payment-service 의 승인 API 연동 후 활성화됩니다.</p>
      </div>

      <!-- 로딩 -->
      <div v-else-if="loading" class="state-box">불러오는 중...</div>

      <!-- 에러 -->
      <div v-else-if="error" class="state-box">
        <p>{{ error }}</p>
        <button type="button" class="btn btn-secondary" @click="loadAll">다시 시도</button>
      </div>

      <!-- 빈 상태 -->
      <div v-else-if="items.length === 0" class="state-box">
        <p>대기 중인 접근 신청이 없습니다.</p>
      </div>

      <div v-else class="list">
        <div v-for="item in items" :key="item.id" class="row">
          <div class="row-main">
            <div class="row-top">
              <h3>{{ item.projectName }}</h3>
              <StatusBadge status="PENDING" />
            </div>
            <p class="requester">{{ item.requesterName }} · <span class="mono">{{ formatDate(item.requestedAt) }}</span></p>
            <p class="reason">"{{ item.reason || '사유가 입력되지 않았습니다.' }}"</p>
          </div>

          <div class="row-actions">
            <button type="button" class="btn btn-secondary btn-sm" :disabled="item._busy" @click="openReject(item)">거절</button>
            <button type="button" class="btn btn-primary btn-sm" :disabled="item._busy" @click="openApprove(item)">승인</button>
          </div>
        </div>
      </div>
    </main>

    <!-- 승인 모달 -->
    <div v-if="approveModal.open" class="modal-backdrop" @click.self="closeApprove">
      <div ref="approveModalEl" class="modal" role="dialog" aria-modal="true" aria-labelledby="approve-modal-title">
        <h2 id="approve-modal-title">접근 승인</h2>
        <p class="modal-sub">{{ approveModal.item?.requesterName }} · {{ approveModal.item?.projectName }}</p>

        <form @submit.prevent="submitApprove">
          <label class="form-label" for="decisionReason">승인 메모</label>
          <textarea
            id="decisionReason"
            ref="approveReasonInput"
            v-model.trim="approveModal.reason"
            class="form-textarea"
            rows="3"
            placeholder="승인 사유나 참고 사항을 입력하세요."
            required
          ></textarea>

          <div v-if="approveModal.error" class="error-msg">{{ approveModal.error }}</div>

          <div class="modal-actions">
            <button type="button" class="btn btn-secondary" @click="closeApprove">취소</button>
            <button type="submit" class="btn btn-primary" :disabled="approveModal.loading">
              <span v-if="approveModal.loading">처리 중...</span>
              <span v-else>승인</span>
            </button>
          </div>
        </form>
      </div>
    </div>

    <!-- 거절 확인 -->
    <div v-if="rejectModal.open" class="modal-backdrop" @click.self="closeReject">
      <div ref="rejectModalEl" class="modal" role="dialog" aria-modal="true" aria-labelledby="reject-modal-title">
        <h2 id="reject-modal-title">접근 거절</h2>
        <p class="modal-sub">{{ rejectModal.item?.requesterName }} · {{ rejectModal.item?.projectName }}</p>

        <form @submit.prevent="submitReject">
          <label class="form-label" for="rejectReason">거절 사유</label>
          <textarea
            id="rejectReason"
            ref="rejectReasonInput"
            v-model.trim="rejectModal.reason"
            class="form-textarea"
            rows="3"
            placeholder="거절 사유를 입력하세요."
            required
          ></textarea>

          <div v-if="rejectModal.error" class="error-msg">{{ rejectModal.error }}</div>

          <div class="modal-actions">
            <button type="button" class="btn btn-secondary" @click="closeReject">취소</button>
            <button type="submit" class="btn btn-danger" :disabled="rejectModal.loading">
              <span v-if="rejectModal.loading">처리 중...</span>
              <span v-else>거절</span>
            </button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import AppNav from '@/components/AppNav.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import { paymentApi } from '@/api/payment.js'
import { FEATURES } from '@/config/features.js'

const loading = ref(true)
const error = ref('')
const items = ref([])

function formatDate(value) {
  if (!value) return ''
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return value
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

async function loadAll() {
  loading.value = true
  error.value = ''

  try {
    const res = await paymentApi.getPending()
    const raw = Array.isArray(res.data?.data) ? res.data.data : Array.isArray(res.data) ? res.data : []
    items.value = raw.map((r) => ({
      id: r.id,
      projectId: r.projectId,
      projectName: r.projectName ?? r.project?.name ?? `프로젝트 #${r.projectId}`,
      requesterName: r.userName ?? r.requesterName ?? r.user?.name ?? `사용자 #${r.userId}`,
      reason: r.reason,
      requestedAt: r.requestedAt ?? r.createdAt,
      _busy: false
    }))
  } catch (e) {
    console.error('[ApprovalQueueView] 승인 대기 목록 조회 실패:', e)
    error.value = '승인 대기 목록을 불러오지 못했습니다.'
  } finally {
    loading.value = false
  }
}
onMounted(() => {
  if (FEATURES.approvalQueue) loadAll()
  else loading.value = false
})

// 모달 접근성: ESC로 닫기, 열릴 때 첫 포커스 대상으로 이동, 닫히면 트리거로 복귀,
// Tab이 모달 밖으로 새지 않게 포커스 트랩. (ProjectCatalogView #9와 동일 패턴 — #17
// 요구사항의 "키보드 접근성"까지 커버)
let lastFocusedEl = null

function trapTab(e, containerEl) {
  if (e.key !== 'Tab' || !containerEl) return
  const focusables = containerEl.querySelectorAll(
    'button:not(:disabled), [href], input, select, textarea, [tabindex]:not([tabindex="-1"])'
  )
  if (focusables.length === 0) return
  const first = focusables[0]
  const last = focusables[focusables.length - 1]

  if (e.shiftKey && document.activeElement === first) {
    e.preventDefault()
    last.focus()
  } else if (!e.shiftKey && document.activeElement === last) {
    e.preventDefault()
    first.focus()
  }
}

function handleModalKeydown(e) {
  if (e.key === 'Escape') {
    if (approveModal.value.open) closeApprove()
    if (rejectModal.value.open) closeReject()
    return
  }
  if (approveModal.value.open) trapTab(e, approveModalEl.value)
  if (rejectModal.value.open) trapTab(e, rejectModalEl.value)
}
onMounted(() => window.addEventListener('keydown', handleModalKeydown))
onUnmounted(() => window.removeEventListener('keydown', handleModalKeydown))

// 승인
const approveModal = ref({ open: false, item: null, reason: '', loading: false, error: '' })
const approveReasonInput = ref(null)
const approveModalEl = ref(null)

function openApprove(item) {
  lastFocusedEl = document.activeElement
  approveModal.value = { open: true, item, reason: '', loading: false, error: '' }
  nextTick(() => approveReasonInput.value?.focus())
}
function closeApprove() {
  approveModal.value.open = false
  lastFocusedEl?.focus?.()
}
async function submitApprove() {
  approveModal.value.error = ''
  approveModal.value.loading = true

  try {
    await paymentApi.approve(approveModal.value.item.id, approveModal.value.reason)
    items.value = items.value.filter((i) => i.id !== approveModal.value.item.id)
    closeApprove()
  } catch (e) {
    console.error('[ApprovalQueueView] 승인 실패:', e)
    approveModal.value.error = e.response?.data?.message || '승인 처리에 실패했습니다.'
  } finally {
    approveModal.value.loading = false
  }
}

// 거절
const rejectModal = ref({ open: false, item: null, reason: '', loading: false, error: '' })
const rejectReasonInput = ref(null)
const rejectModalEl = ref(null)

function openReject(item) {
  lastFocusedEl = document.activeElement
  rejectModal.value = { open: true, item, reason: '', loading: false, error: '' }
  nextTick(() => rejectReasonInput.value?.focus())
}
function closeReject() {
  rejectModal.value.open = false
  lastFocusedEl?.focus?.()
}
async function submitReject() {
  rejectModal.value.error = ''
  rejectModal.value.loading = true

  try {
    await paymentApi.revoke(rejectModal.value.item.id, rejectModal.value.reason)
    items.value = items.value.filter((i) => i.id !== rejectModal.value.item.id)
    closeReject()
  } catch (e) {
    console.error('[ApprovalQueueView] 거절 실패:', e)
    rejectModal.value.error = e.response?.data?.message || '거절 처리에 실패했습니다.'
  } finally {
    rejectModal.value.loading = false
  }
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: var(--color-bg-secondary);
}
.content {
  max-width: 900px;
  margin: 0 auto;
  padding: 32px 24px 60px;
}
.content-header { margin-bottom: 24px; }
.content-header h1 { font-size: 20px; font-weight: 700; color: var(--color-text-primary); }
.sub { font-size: 12.5px; color: var(--color-text-secondary); margin-top: 4px; }

.list { display: flex; flex-direction: column; gap: 10px; }
.row {
  display: flex;
  align-items: center;
  gap: 16px;
  background: var(--color-bg-primary);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: 16px 18px;
}
.row-main { flex: 1; min-width: 0; }
.row-top { display: flex; align-items: center; gap: 10px; margin-bottom: 4px; }
.row-top h3 { font-size: 14px; font-weight: 600; color: var(--color-text-primary); }
.requester { font-size: 12px; color: var(--color-text-secondary); margin-bottom: 4px; }
.reason { font-size: 12.5px; color: var(--color-text-primary); font-style: italic; }
.row-actions { display: flex; gap: 8px; flex-shrink: 0; }
.btn-sm { padding: 7px 14px; font-size: 12.5px; }
.btn-danger {
  background: var(--color-bg-primary);
  color: var(--color-danger);
  border: 1.5px solid var(--color-danger);
}
.btn-danger:hover { background: var(--color-danger-light); }

.state-box {
  text-align: center;
  padding: 80px 0;
  color: var(--color-text-muted);
  font-size: 13px;
}
.prep-box { display: flex; flex-direction: column; gap: 6px; }
.prep-title { font-size: 14px; font-weight: 600; color: var(--color-text-secondary); }

/* 모달 (ProjectCatalogView와 동일 패턴) */
.modal-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 50;
  padding: 20px;
}
.modal {
  width: 100%;
  max-width: 420px;
  background: var(--color-bg-secondary);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: 24px;
}
.modal h2 { font-size: 17px; font-weight: 700; color: var(--color-text-primary); margin-bottom: 4px; }
.modal-sub { font-size: 12.5px; color: var(--color-text-secondary); margin-bottom: 16px; }
.modal form { display: flex; flex-direction: column; gap: 8px; }
.form-label { font-size: 12px; font-weight: 500; color: var(--color-text-secondary); margin-top: 8px; }
.form-textarea {
  width: 100%;
  padding: 9px 12px;
  border: 1.5px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-primary);
  color: var(--color-text-primary);
  font-size: 13px;
  font-family: var(--font-sans);
  outline: none;
  resize: vertical;
}
.form-textarea:focus { border-color: var(--color-primary); box-shadow: 0 0 0 3px var(--color-primary-light); }
.error-msg {
  margin-top: 8px;
  padding: 8px 12px;
  background: var(--color-danger-light);
  border: 1px solid var(--color-danger);
  border-radius: var(--radius-md);
  font-size: 12px;
  color: var(--color-danger);
}
.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 16px;
}
</style>
