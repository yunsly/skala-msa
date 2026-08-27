<template>
  <div class="page">
    <AppNav />

    <main class="content">
      <div class="breadcrumb mono">
        <router-link to="/projects">프로젝트 카탈로그</router-link> /
        <router-link :to="`/projects/${projectId}`">{{ projectName || `프로젝트 #${projectId}` }}</router-link> /
        자산 등록
      </div>

      <div class="header-row">
        <h1>자산 등록</h1>
        <p class="sub">이 프로젝트에 새로운 Credential 자산을 등록합니다.</p>
      </div>

      <div class="form-card">
        <form class="form" @submit.prevent="handleSubmit">
          <div class="form-group">
            <label class="form-label" for="title">자산명</label>
            <input
              id="title"
              v-model.trim="form.title"
              type="text"
              class="form-input"
              placeholder="예: AWS Production IAM Admin Key"
              maxlength="100"
            />
          </div>

          <div class="form-group">
            <label class="form-label" for="description">설명</label>
            <textarea
              id="description"
              v-model.trim="form.description"
              class="form-textarea"
              rows="4"
              placeholder="자산의 용도, 접근 시 유의사항 등을 입력하세요."
            ></textarea>
          </div>

          <div class="form-row">
            <div class="form-group">
              <label class="form-label" for="category">자산 유형</label>
              <select id="category" v-model="form.category" class="form-select">
                <option disabled value="">유형을 선택하세요</option>
                <option v-for="opt in categoryOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
              </select>
            </div>
            <div class="form-group">
              <label class="form-label" for="provider">제공자</label>
              <input
                id="provider"
                v-model.trim="form.provider"
                type="text"
                class="form-input"
                placeholder="예: AWS, MariaDB, Toss"
              />
            </div>
          </div>

          <div v-if="form.category === 'SUBSCRIPTION_PLAN'" class="form-group">
            <label class="form-label" for="planName">플랜명</label>
            <input
              id="planName"
              v-model.trim="form.planName"
              type="text"
              class="form-input"
              placeholder="예: Business Pro"
            />
          </div>

          <div class="form-group">
            <label class="form-label" for="secretValue">Secret 값</label>
            <input
              id="secretValue"
              v-model="form.secretValue"
              type="password"
              class="form-input mono"
              placeholder="등록 후에는 마스킹 처리되어 노출됩니다."
              autocomplete="new-password"
            />
            <p class="hint">등록 즉시 암호화되어 저장되고, 이후에는 승인된 멤버만 화면에서 "표시"로 확인할 수 있습니다.</p>
          </div>

          <div v-if="validationError" class="error-box">{{ validationError }}</div>
          <div v-if="submitError" class="error-box">{{ submitError }}</div>
          <div v-if="submitSuccess" class="success-box">{{ submitSuccess }}</div>

          <div class="form-actions">
            <router-link :to="`/projects/${projectId}`" class="btn btn-secondary">취소</router-link>
            <button type="submit" class="btn btn-primary" :disabled="submitting">
              <span v-if="submitting">등록 중...</span>
              <span v-else>자산 등록</span>
            </button>
          </div>
        </form>
      </div>
    </main>
  </div>
</template>

<script setup>
import { reactive, ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppNav from '@/components/AppNav.vue'
import { courseApi } from '@/api/course.js'

const route = useRoute()
const router = useRouter()

const projectId = computed(() => route.params.id)
const projectName = ref('')

const form = reactive({
  title: '',
  description: '',
  category: '',
  provider: '',
  planName: '',
  secretValue: ''
})

const categoryOptions = [
  { label: 'API_KEY', value: 'API_KEY' },
  { label: 'DB_CREDENTIAL', value: 'DB_CREDENTIAL' },
  { label: 'SUBSCRIPTION_PLAN', value: 'SUBSCRIPTION_PLAN' }
]

const submitting = ref(false)
const validationError = ref('')
const submitError = ref('')
const submitSuccess = ref('')

// 프로젝트 단건 조회 API가 없어 브레드크럼용 이름만 목록에서 찾아온다(실패해도
// 등록 자체에는 영향 없음 — projectId만 있으면 됨).
async function loadProjectName() {
  try {
    const res = await courseApi.getProjects()
    const projects = Array.isArray(res.data?.data) ? res.data.data : Array.isArray(res.data) ? res.data : []
    const found = projects.find((p) => String(p.id) === String(projectId.value))
    projectName.value = found?.name ?? ''
  } catch (e) {
    console.error('[AssetCreateView] 프로젝트 이름 조회 실패:', e)
  }
}
onMounted(loadProjectName)

function validateForm() {
  validationError.value = ''

  if (!form.title) {
    validationError.value = '자산명을 입력해 주세요.'
    return false
  }
  if (!form.description) {
    validationError.value = '설명을 입력해 주세요.'
    return false
  }
  if (!form.category) {
    validationError.value = '자산 유형을 선택해 주세요.'
    return false
  }
  if (!form.provider) {
    validationError.value = '제공자를 입력해 주세요.'
    return false
  }
  if (!form.secretValue) {
    validationError.value = 'Secret 값을 입력해 주세요.'
    return false
  }
  return true
}

async function handleSubmit() {
  submitError.value = ''
  submitSuccess.value = ''

  if (!validateForm()) return

  submitting.value = true

  try {
    const res = await courseApi.createAsset({
      projectId: projectId.value,
      title: form.title,
      description: form.description,
      category: form.category,
      provider: form.provider,
      planName: form.category === 'SUBSCRIPTION_PLAN' ? form.planName : undefined,
      secretValue: form.secretValue
    })

    submitSuccess.value = '자산이 성공적으로 등록되었습니다.'

    const createdId = res.data?.data?.id ?? res.data?.id

    setTimeout(() => {
      if (createdId) {
        router.push(`/courses/${createdId}`)
      } else {
        router.push(`/projects/${projectId.value}`)
      }
    }, 600)
  } catch (e) {
    console.error('[AssetCreateView] 자산 등록 실패:', e)
    submitError.value = e.response?.data?.message || '자산 등록에 실패했습니다.'
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: var(--color-bg-secondary);
}
.content {
  max-width: 640px;
  margin: 0 auto;
  padding: 32px 24px 60px;
}
.breadcrumb {
  font-size: 11px;
  color: var(--color-text-muted);
  margin-bottom: 16px;
}
.breadcrumb a { color: var(--color-text-muted); }
.breadcrumb a:hover { color: var(--color-primary); }

.header-row { margin-bottom: 20px; }
.header-row h1 { font-size: 20px; font-weight: 700; color: var(--color-text-primary); }
.sub { font-size: 12.5px; color: var(--color-text-secondary); margin-top: 4px; }

.form-card {
  background: var(--color-bg-primary);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: 24px;
}
.form { display: flex; flex-direction: column; gap: 18px; }
.form-row { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.form-group { display: flex; flex-direction: column; gap: 8px; }
.form-label { font-size: 12.5px; font-weight: 600; color: var(--color-text-primary); }

.form-input,
.form-textarea,
.form-select {
  width: 100%;
  border: 1.5px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-secondary);
  color: var(--color-text-primary);
  padding: 10px 13px;
  font-size: 13.5px;
  font-family: var(--font-sans);
  outline: none;
  box-sizing: border-box;
}
.form-input.mono { font-family: var(--font-mono); }
.form-input::placeholder,
.form-textarea::placeholder { color: var(--color-text-muted); }
.form-input:focus,
.form-textarea:focus,
.form-select:focus {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px var(--color-primary-light);
}
.form-textarea { resize: vertical; min-height: 100px; line-height: 1.5; }

.hint { font-size: 11px; color: var(--color-text-muted); line-height: 1.5; }

.error-box {
  background: var(--color-danger-light);
  border: 1px solid var(--color-danger);
  color: var(--color-danger);
  border-radius: var(--radius-md);
  padding: 10px 13px;
  font-size: 12.5px;
}
.success-box {
  background: var(--color-success-light);
  border: 1px solid var(--color-success);
  color: var(--color-success);
  border-radius: var(--radius-md);
  padding: 10px 13px;
  font-size: 12.5px;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 4px;
}

@media (max-width: 560px) {
  .form-row { grid-template-columns: 1fr; }
}
</style>
