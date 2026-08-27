<template>
  <div class="login-page">
    <div class="login-layout">
      <!-- 좌측 브랜딩 -->
      <div class="login-left">
        <div class="brand">
          <span class="brand-mark">K</span>
          <span class="brand-name">KeyNexus</span>
        </div>
        <div class="brand-content">
          <h2>사내 Credential을<br>안전하게 관리하세요</h2>
          <p>프로젝트 계정으로 로그인하고 접근 현황을 확인하세요.</p>
          <ul class="feature-list">
            <li v-for="f in features" :key="f"><span class="dot"></span>{{ f }}</li>
          </ul>
        </div>
      </div>

      <!-- 우측 로그인 폼 -->
      <div class="login-right">
        <div class="login-box">
          <router-link to="/" class="back-link">← 홈으로</router-link>

          <h1>로그인</h1>
          <p class="sub">사내 계정 이메일과 비밀번호를 입력하세요.</p>

          <form class="form" @submit.prevent="handleSubmit">
            <div class="form-group">
              <label class="form-label" for="email">이메일</label>
              <input
                id="email"
                v-model.trim="form.email"
                type="email"
                class="form-input"
                placeholder="name@company.com"
                autocomplete="username"
                required
              />
            </div>
            <div class="form-group">
              <label class="form-label" for="password">비밀번호</label>
              <input
                id="password"
                v-model="form.password"
                type="password"
                class="form-input"
                placeholder="••••••••"
                autocomplete="current-password"
                required
              />
            </div>

            <div v-if="error" class="error-msg">{{ error }}</div>

            <button type="submit" class="btn btn-primary btn-full" :disabled="loading">
              <span v-if="loading">로그인 중...</span>
              <span v-else>로그인</span>
            </button>
          </form>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/store/auth.js'

const router = useRouter()
const auth = useAuthStore()

const form = ref({ email: '', password: '' })
const loading = ref(false)
const error = ref('')

const features = [
  '프로젝트별 Credential 한눈에 파악',
  '요청 → 승인 흐름으로 안전한 접근',
  '규칙 기반 위험도로 선제 대응'
]

async function handleSubmit() {
  error.value = ''
  loading.value = true

  try {
    await auth.login(form.value)
    router.push({ name: 'ProjectCatalog' })
  } catch (e) {
    console.error('[LoginView] 로그인 실패:', e)
    error.value = e.response?.data?.message || '이메일 또는 비밀번호가 올바르지 않습니다.'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  background: var(--color-bg-primary);
}
.login-layout {
  display: grid;
  grid-template-columns: 1fr 1fr;
  width: 100%;
  min-height: 100vh;
}

.login-left {
  background: var(--color-bg-secondary);
  border-right: 1px solid var(--color-border);
  padding: 48px;
  display: flex;
  flex-direction: column;
  gap: 48px;
}
.brand { display: flex; align-items: center; gap: 9px; }
.brand-mark {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  background: var(--color-primary);
  color: #fff;
  font-family: var(--font-mono);
  font-weight: 700;
  font-size: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.brand-name { font-size: 16px; font-weight: 700; color: var(--color-text-primary); }

.brand-content h2 {
  font-size: 26px;
  font-weight: 700;
  color: var(--color-text-primary);
  line-height: 1.4;
  margin-bottom: 12px;
}
.brand-content p {
  font-size: 13.5px;
  color: var(--color-text-secondary);
  margin-bottom: 26px;
}
.feature-list { list-style: none; display: flex; flex-direction: column; gap: 12px; padding: 0; margin: 0; }
.feature-list li {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  color: var(--color-text-secondary);
}
.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--color-primary);
  flex-shrink: 0;
}

.login-right {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px;
}
.login-box { width: 100%; max-width: 380px; }
.back-link {
  display: inline-block;
  font-size: 13px;
  color: var(--color-text-secondary);
  margin-bottom: 32px;
}
.back-link:hover { color: var(--color-primary); }

.login-box h1 { font-size: 22px; font-weight: 700; color: var(--color-text-primary); margin-bottom: 6px; }
.sub { font-size: 13px; color: var(--color-text-secondary); margin-bottom: 24px; }

.form { display: flex; flex-direction: column; gap: 16px; }
.form-group { display: flex; flex-direction: column; gap: 6px; }
.form-label { font-size: 12.5px; font-weight: 500; color: var(--color-text-secondary); }
.form-input {
  padding: 10px 14px;
  border: 1.5px solid var(--color-border);
  border-radius: var(--radius-md);
  font-size: 14px;
  font-family: var(--font-sans);
  color: var(--color-text-primary);
  background: var(--color-bg-secondary);
  transition: var(--transition);
  outline: none;
}
.form-input::placeholder { color: var(--color-text-muted); }
.form-input:focus { border-color: var(--color-primary); box-shadow: 0 0 0 3px var(--color-primary-light); }

.btn-full { width: 100%; padding: 12px; font-size: 15px; justify-content: center; margin-top: 4px; }

.error-msg {
  padding: 10px 14px;
  background: var(--color-danger-light);
  border: 1px solid var(--color-danger);
  border-radius: var(--radius-md);
  font-size: 13px;
  color: var(--color-danger);
}

@media (max-width: 860px) {
  .login-layout { grid-template-columns: 1fr; }
  .login-left { display: none; }
}
</style>
