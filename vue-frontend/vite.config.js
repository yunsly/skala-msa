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
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        secure: false
      },
      // OAuth2 로그인: 로그인 폼은 SPA(LoginView)가 그리고, authorize/token/자격증명
      // 제출은 auth-server 로 프록시한다. Host 를 localhost:3000 그대로 넘겨야
      // (changeOrigin:false) auth-server 리다이렉트(Location)가 이 오리진으로 돌아온다.
      '/oauth2': {
        target: 'http://localhost:8080',
        changeOrigin: false,
        secure: false
      },
      // GET /login 은 SPA 라우트(LoginView)로 두고, POST(자격증명 제출)만 auth-server 로.
      // (예전에 /login 전체를 프록시해서 직접 진입 시 화면이 안 뜨던 문제를 bypass 로 회피)
      '/login': {
        target: 'http://localhost:8080',
        changeOrigin: false,
        secure: false,
        bypass(req) {
          if (req.method === 'GET') return '/index.html'
        }
      }
    }
  }
})