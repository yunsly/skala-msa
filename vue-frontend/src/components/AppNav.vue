<template>
  <header class="app-nav">
    <div class="nav-inner">
      <router-link to="/projects" class="brand">
        <span class="brand-mark">K</span>
        <span class="brand-name">KeyNexus</span>
      </router-link>

      <nav class="nav-links">
        <router-link to="/projects" class="nav-link" :class="{ active: route.path.startsWith('/projects') }">프로젝트</router-link>
        <router-link to="/my-projects" class="nav-link" :class="{ active: route.path === '/my-projects' }">내 프로젝트</router-link>
        <router-link v-if="auth.isLeader" to="/approval" class="nav-link" :class="{ active: route.path === '/approval' }">승인 대기</router-link>
        <router-link v-if="auth.isAdmin || auth.isLeader" to="/risk-dashboard" class="nav-link" :class="{ active: route.path === '/risk-dashboard' }">위험도 대시보드</router-link>
      </nav>

      <div class="nav-actions">
        <span class="role-pill mono">{{ auth.isLeader ? 'LEADER' : 'MEMBER' }}</span>
        <span class="avatar mono">{{ auth.user?.name?.charAt(0) || '?' }}</span>
        <button type="button" class="btn btn-ghost btn-sm" @click="handleLogout">로그아웃</button>
      </div>
    </div>
  </header>
</template>

<script setup>
import { useRoute } from 'vue-router'
import { useAuthStore } from '@/store/auth.js'

const route = useRoute()
const auth = useAuthStore()

function handleLogout() {
  auth.logout()
}
</script>

<style scoped>
.app-nav {
  position: sticky;
  top: 0;
  z-index: 20;
  background: var(--color-bg-primary);
  border-bottom: 1px solid var(--color-border);
}
.nav-inner {
  max-width: 1160px;
  margin: 0 auto;
  padding: 0 24px;
  height: 56px;
  display: flex;
  align-items: center;
  gap: 28px;
}
.brand {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}
.brand-mark {
  width: 24px;
  height: 24px;
  border-radius: 5px;
  background: var(--color-primary);
  color: #fff;
  font-family: var(--font-mono);
  font-weight: 700;
  font-size: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.brand-name {
  font-size: 14px;
  font-weight: 700;
  color: var(--color-text-primary);
}
.nav-links {
  display: flex;
  gap: 4px;
  flex: 1;
  min-width: 0;
}
.nav-link {
  padding: 6px 12px;
  border-radius: var(--radius-sm);
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-secondary);
  transition: var(--transition);
  white-space: nowrap;
  flex-shrink: 0;
}
.nav-link:hover,
.nav-link.active {
  color: var(--color-primary);
  background: var(--color-primary-light);
}
.nav-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}
.role-pill {
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.04em;
  color: var(--color-text-secondary);
  border: 1px solid var(--color-border);
  border-radius: 4px;
  padding: 3px 8px;
}
.avatar {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: var(--color-bg-tertiary);
  border: 1px solid var(--color-border);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 700;
  color: var(--color-text-primary);
}
.btn-sm { padding: 6px 12px; font-size: 12.5px; }

@media (max-width: 640px) {
  .nav-inner {
    flex-wrap: wrap;
    height: auto;
    padding: 10px 16px;
    row-gap: 8px;
  }
  .brand { order: 1; }
  .nav-actions { order: 2; margin-left: auto; }
  .nav-links {
    order: 3;
    width: 100%;
    flex: none;
    overflow-x: auto;
    padding-bottom: 2px;
  }
}
</style>
