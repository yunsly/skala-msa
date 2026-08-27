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
          <p>계정으로 로그인하고 프로젝트별 접근 현황을 확인하세요.</p>
          <ul class="feature-list">
            <li v-for="f in features" :key="f"><span class="dot"></span>{{ f }}</li>
          </ul>
        </div>
      </div>

      <!-- 우측 -->
      <div class="login-right">
        <div class="login-box">
          <router-link to="/" class="back-link">← 홈으로</router-link>

          <!-- 로그인 -->
          <div v-if="!showRegister" class="section">
            <h1>로그인</h1>
            <p class="sub">사내 SSO(OAuth2) 계정으로 로그인합니다.</p>
            <button class="btn btn-primary btn-full" @click="handleOAuth">로그인</button>
            <div class="switch-link">
              계정이 없으신가요?
              <button type="button" class="text-btn" @click="showRegister = true">회원가입</button>
            </div>
          </div>

          <!-- 회원가입 -->
          <div v-else class="section">
            <h1>회원가입</h1>
            <p class="sub">계정 생성 후 로그인 버튼으로 접속하세요.</p>
            <form class="form" @submit.prevent="handleRegister">
              <div class="form-group">
                <label class="form-label" for="name">이름</label>
                <input id="name" v-model.trim="registerForm.name" type="text" class="form-input" placeholder="홍길동" required />
              </div>
              <div class="form-group">
                <label class="form-label" for="reg-email">이메일</label>
                <input id="reg-email" v-model.trim="registerForm.email" type="email" class="form-input" placeholder="name@company.com" required />
              </div>
              <div class="form-group">
                <label class="form-label" for="reg-password">비밀번호</label>
                <input id="reg-password" v-model="registerForm.password" type="password" class="form-input" placeholder="8자 이상" required />
              </div>
              <div class="form-group">
                <label class="form-label" for="role">역할</label>
                <select id="role" v-model="registerForm.role" class="form-input">
                  <option value="STUDENT">개발자 (STUDENT)</option>
                  <option value="INSTRUCTOR">프로젝트 관리자 (INSTRUCTOR)</option>
                </select>
              </div>

              <div v-if="error" class="error-msg">{{ error }}</div>
              <div v-if="success" class="success-msg">{{ success }}</div>

              <button type="submit" class="btn btn-primary btn-full" :disabled="loading">
                <span v-if="loading">가입 중...</span>
                <span v-else>회원가입</span>
              </button>
            </form>
            <div class="switch-link">
              이미 계정이 있으신가요?
              <button type="button" class="text-btn" @click="showRegister = false">로그인</button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useAuthStore } from '@/store/auth.js'
import { authApi } from '@/api/auth.js'

const auth = useAuthStore()

const showRegister = ref(false)
const loading = ref(false)
const error = ref('')
const success = ref('')

const registerForm = ref({ name: '', email: '', password: '', role: 'STUDENT' })

const features = [
  '프로젝트별 Credential 한눈에 파악',
  '요청 → 승인 흐름으로 안전한 접근',
  '규칙 기반 위험도로 선제 대응'
]

function handleOAuth() {
  auth.redirectToLogin()
}

async function handleRegister() {
  error.value = ''
  success.value = ''
  loading.value = true

  try {
    await authApi.register(registerForm.value)
    success.value = '회원가입 완료! 로그인 버튼으로 접속하세요.'
    registerForm.value = { name: '', email: '', password: '', role: 'STUDENT' }
    setTimeout(() => {
      showRegister.value = false
      success.value = ''
    }, 2000)
  } catch (e) {
    console.error('[LoginView] 회원가입 실패:', e)
    error.value = e.response?.data?.message || '회원가입에 실패했습니다.'
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
  overflow-y: auto;
}
.login-box { width: 100%; max-width: 380px; }
.back-link {
  display: inline-block;
  font-size: 13px;
  color: var(--color-text-secondary);
  margin-bottom: 32px;
}
.back-link:hover { color: var(--color-primary); }

.section { display: flex; flex-direction: column; gap: 16px; }
.section h1 { font-size: 22px; font-weight: 700; color: var(--color-text-primary); margin-bottom: 2px; }
.sub { font-size: 13px; color: var(--color-text-secondary); margin-bottom: 4px; }

.form { display: flex; flex-direction: column; gap: 14px; }
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

.switch-link {
  text-align: center;
  font-size: 13px;
  color: var(--color-text-secondary);
  margin-top: 4px;
}
.text-btn {
  background: none;
  border: none;
  color: var(--color-primary);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  padding: 0 2px;
  text-decoration: underline;
}
.error-msg {
  padding: 10px 14px;
  background: var(--color-danger-light);
  border: 1px solid var(--color-danger);
  border-radius: var(--radius-md);
  font-size: 13px;
  color: var(--color-danger);
}
.success-msg {
  padding: 10px 14px;
  background: var(--color-success-light);
  border: 1px solid var(--color-success);
  border-radius: var(--radius-md);
  font-size: 13px;
  color: var(--color-success);
}

@media (max-width: 860px) {
  .login-layout { grid-template-columns: 1fr; }
  .login-left { display: none; }
}
</style>
