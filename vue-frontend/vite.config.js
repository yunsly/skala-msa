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
    // 시연 시 같은 네트워크의 다른 사람들이 LAN IP로 접속할 수 있도록 개방.
    // 프록시 target(http://localhost:8080)은 dev 서버 프로세스 자체가 로컬 백엔드로
    // 요청하는 것이라 host 설정과 무관하게 그대로 유지.
    host: '0.0.0.0',
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