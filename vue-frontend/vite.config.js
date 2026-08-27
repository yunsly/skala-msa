import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },
  server: {
    host: 'localhost',
    port: 3000,
    strictPort: true,
    proxy: {
      // OAuth2 Authorization Code Flow가 이메일/비밀번호 로그인(POST /api/users/login)으로
      // 대체되면서 /oauth2, /login, /logout, /userinfo 프록시는 더 이상 쓰이지 않는다.
      // 특히 /login은 SPA 라우트(LoginView.vue)와 이름이 겹쳐서 그대로 두면 브라우저에서
      // /login으로 직접 진입(새로고침 포함)할 때 Vue 앱이 아니라 백엔드로 프록시되어
      // 화면이 아예 뜨지 않는 문제가 있었다.
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        secure: false
      }
    }
  }
})