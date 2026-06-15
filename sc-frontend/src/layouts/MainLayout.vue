<template>
  <div
    class="layout"
    :class="[
      `layout-${uiPreferences.layoutMode}`,
      { 'sidebar-collapsed': uiPreferences.isSidebarLayout && uiPreferences.sidebarCollapsed },
    ]"
  >
    <!-- Top Navigation Bar -->
    <header v-if="!uiPreferences.isSidebarLayout" class="top-nav">
      <div class="nav-container">
        <!-- Logo -->
        <router-link to="/dashboard" class="nav-logo">
          <img :src="brandLogoSrc" alt="" class="brand-mark"/>
          <span>SapientiaCloud</span>
        </router-link>

        <!-- Nav Links -->
        <nav class="nav-links">
          <router-link
            v-for="item in navItems"
            :key="item.path"
            :to="item.path"
            class="nav-link"
          >
            <component :is="item.icon" :size="16" stroke-width="1.8"/>
            <span>{{ item.label }}</span>
          </router-link>
        </nav>

        <!-- User Section -->
        <div class="nav-user">
          <button class="btn-icon" :class="{ active: isNotificationsPage }" @click="$router.push('/notifications')">
            <Bell :size="20" stroke-width="1.8"/>
            <span v-if="unreadCount > 0" class="notification-badge"></span>
          </button>
          <div class="user-menu" @click="showUserMenu = !showUserMenu">
            <div class="user-avatar">
              <img v-if="authStore.user?.avatarUrl" :src="authStore.user.avatarUrl" alt="Avatar"/>
              <img v-else :src="defaultAvatarSrc" alt="Avatar"/>
            </div>
            <span class="user-name">{{ authStore.user?.displayName || 'User' }}</span>
            <ChevronDown :size="16" stroke-width="1.8"/>
          </div>

          <!-- Dropdown Menu -->
          <div v-if="showUserMenu" class="dropdown-menu">
            <router-link to="/profile" class="dropdown-item" @click="showUserMenu = false">
              <UserCircle :size="16"/>
              <span>{{ t('common.menu.profile') }}</span>
            </router-link>
            <router-link to="/settings" class="dropdown-item" @click="showUserMenu = false">
              <Settings :size="16"/>
              <span>{{ t('common.menu.settings') }}</span>
            </router-link>
            <template v-if="isAdmin">
              <div class="dropdown-divider"></div>
              <router-link to="/admin/users" class="dropdown-item" @click="showUserMenu = false">
                <Users :size="16"/>
                <span>{{ t('common.menu.users') }}</span>
              </router-link>
              <router-link to="/admin/students" class="dropdown-item" @click="showUserMenu = false">
                <GraduationCap :size="16"/>
                <span>{{ t('common.menu.students') }}</span>
              </router-link>
              <router-link to="/admin/teachers" class="dropdown-item" @click="showUserMenu = false">
                <BookOpen :size="16"/>
                <span>{{ t('common.menu.teachers') }}</span>
              </router-link>
            </template>
            <div class="dropdown-divider"></div>
            <button class="dropdown-item" @click="handleLogout">
              <LogOut :size="16"/>
              <span>{{ t('common.menu.logout') }}</span>
            </button>
          </div>
        </div>
      </div>
    </header>

    <!-- Side Navigation Bar -->
    <aside v-else class="side-nav" aria-label="Primary navigation">
      <div class="side-nav-header">
        <router-link to="/dashboard" class="nav-logo side-logo">
          <img :src="brandLogoSrc" alt="" class="brand-mark"/>
          <span>SapientiaCloud</span>
        </router-link>
        <button
          type="button"
          class="btn-icon sidebar-collapse-btn"
          :aria-label="uiPreferences.sidebarCollapsed ? t('common.layout.expandSidebar') : t('common.layout.collapseSidebar')"
          @click="uiPreferences.toggleSidebarCollapsed"
        >
          <PanelLeftOpen v-if="uiPreferences.sidebarCollapsed" :size="19" stroke-width="1.8"/>
          <PanelLeftClose v-else :size="19" stroke-width="1.8"/>
        </button>
      </div>

      <nav class="side-nav-links">
        <router-link
          v-for="item in navItems"
          :key="item.path"
          :to="item.path"
          class="side-nav-link"
          :aria-label="uiPreferences.sidebarCollapsed ? item.label : undefined"
          :title="uiPreferences.sidebarCollapsed ? item.label : undefined"
        >
          <component :is="item.icon" :size="18" stroke-width="1.8"/>
          <span>{{ item.label }}</span>
        </router-link>
      </nav>

      <div class="side-nav-footer">
        <button class="btn-icon" :class="{ active: isNotificationsPage }" @click="$router.push('/notifications')">
          <Bell :size="20" stroke-width="1.8"/>
          <span v-if="unreadCount > 0" class="notification-badge"></span>
        </button>
        <div class="user-menu side-user-menu" @click="showUserMenu = !showUserMenu">
          <div class="user-avatar">
            <img v-if="authStore.user?.avatarUrl" :src="authStore.user.avatarUrl" alt="Avatar"/>
            <img v-else :src="defaultAvatarSrc" alt="Avatar"/>
          </div>
          <span class="user-name">{{ authStore.user?.displayName || 'User' }}</span>
          <ChevronDown :size="16" stroke-width="1.8"/>
        </div>

        <div v-if="showUserMenu" class="dropdown-menu side-dropdown">
          <router-link to="/profile" class="dropdown-item" @click="showUserMenu = false">
            <UserCircle :size="16"/>
            <span>{{ t('common.menu.profile') }}</span>
          </router-link>
          <router-link to="/settings" class="dropdown-item" @click="showUserMenu = false">
            <Settings :size="16"/>
            <span>{{ t('common.menu.settings') }}</span>
          </router-link>
          <template v-if="isAdmin">
            <div class="dropdown-divider"></div>
            <router-link to="/admin/users" class="dropdown-item" @click="showUserMenu = false">
              <Users :size="16"/>
              <span>{{ t('common.menu.users') }}</span>
            </router-link>
            <router-link to="/admin/students" class="dropdown-item" @click="showUserMenu = false">
              <GraduationCap :size="16"/>
              <span>{{ t('common.menu.students') }}</span>
            </router-link>
            <router-link to="/admin/teachers" class="dropdown-item" @click="showUserMenu = false">
              <BookOpen :size="16"/>
              <span>{{ t('common.menu.teachers') }}</span>
            </router-link>
          </template>
          <div class="dropdown-divider"></div>
          <button class="dropdown-item" @click="handleLogout">
            <LogOut :size="16"/>
            <span>{{ t('common.menu.logout') }}</span>
          </button>
        </div>
      </div>
    </aside>

    <!-- Main Content -->
    <main class="main-content">
      <div class="content-container">
        <router-view/>
      </div>
    </main>
  </div>
</template>

<script lang="ts" setup>
import {computed, onMounted, onUnmounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import {
  Bell,
  BookOpen,
  ChevronDown,
  ClipboardList,
  GraduationCap,
  LayoutDashboard,
  LogOut,
  Mail,
  PanelLeftClose,
  PanelLeftOpen,
  Settings,
  UserCircle,
  Users,
} from 'lucide-vue-next'

import {useUnreadCount} from '@/shared/composables/useUnreadCount'
import {useAuthStore} from '@/features/auth/stores/auth'
import {useUiPreferencesStore} from '@/features/settings/stores/uiPreferences'

const router = useRouter()
const authStore = useAuthStore()
const uiPreferences = useUiPreferencesStore()
const {t} = useI18n()

const showUserMenu = ref(false)
const {unreadCount, start: startUnreadCount, stop: stopUnreadCount} = useUnreadCount()

const isAdmin = computed(() => authStore.user?.role === 0)
const isTeacher = computed(() => authStore.user?.role === 2)

const defaultAvatarSrc = computed(() => {
  switch (authStore.user?.role) {
    case 0: return '/assets/avatar-admin-default.png'
    case 2: return '/assets/avatar-teacher-default.png'
    default: return '/assets/avatar-student-default.png'
  }
})
const brandLogoSrc = computed(() =>
  uiPreferences.resolvedTheme === 'dark'
    ? '/assets/project-logo-dark.png'
    : '/assets/project-logo-light.png',
)

const navItems = computed(() => [
  {path: '/dashboard', label: t('common.navigation.dashboard'), icon: LayoutDashboard},
  {path: '/courses', label: t('common.navigation.courses'), icon: BookOpen},
  ...(authStore.user?.role === 1
    ? [{path: '/my-enrollments', label: t('common.navigation.myEnrollments'), icon: GraduationCap}]
    : []),
  ...(isTeacher.value
    ? [
        {path: '/teacher/courses?role=primary', label: t('common.navigation.myEnrollments'), icon: GraduationCap},
        {path: '/enrollment-management', label: t('common.navigation.enrollmentManagement'), icon: ClipboardList},
      ]
    : []),
  ...(isAdmin.value
    ? [{path: '/course-management', label: t('common.navigation.courseManagement'), icon: BookOpen}]
    : []),
  ...(isTeacher.value || isAdmin.value
    ? [{path: '/invitations', label: t('common.navigation.invitations'), icon: Mail}]
    : []),
])

const isNotificationsPage = computed(() => router.currentRoute.value.path === '/notifications')

function handleClickOutside(event: MouseEvent) {
  const target = event.target as HTMLElement
  if (!target.closest('.user-menu') && !target.closest('.dropdown-menu')) {
    showUserMenu.value = false
  }
}

onMounted(() => {
  startUnreadCount()
  document.addEventListener('click', handleClickOutside)
})

onUnmounted(() => {
  stopUnreadCount()
  document.removeEventListener('click', handleClickOutside)
})

async function handleLogout() {
  showUserMenu.value = false
  await authStore.logout()
  await router.replace('/login')
}
</script>

<style scoped>
.layout {
  min-height: 100vh;
  background: var(--color-surface-canvas);
}

/* ---- Top Navigation ---- */

.top-nav {
  position: sticky;
  top: 0;
  z-index: 100;
  background: var(--color-surface-card);
  border-bottom: 1px solid var(--color-outline-light);
}

.nav-container {
  max-width: 1280px;
  margin: 0 auto;
  padding: 0 64px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 64px;
}

.nav-logo {
  display: flex;
  align-items: center;
  gap: 10px;
  text-decoration: none;
  color: var(--color-on-surface);
}

.brand-mark {
  width: 34px;
  height: 34px;
  object-fit: contain;
  flex-shrink: 0;
}

.nav-logo span {
  font-family: var(--font-heading);
  font-size: 20px;
  font-weight: 400;
  letter-spacing: -0.02em;
}

.nav-links {
  display: flex;
  align-items: center;
  gap: 8px;
}

.nav-link {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  border-radius: var(--radius-sm);
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 14px;
  font-weight: 500;
  color: var(--color-muted);
  text-decoration: none;
  transition: all 0.2s;
}

.nav-link:hover {
  background: var(--color-surface-canvas);
  color: var(--color-on-surface);
}

.nav-link.router-link-active {
  background: var(--color-primary);
  color: var(--color-on-primary);
}

.nav-user {
  display: flex;
  align-items: center;
  gap: 12px;
  position: relative;
}

.btn-icon {
  width: 40px;
  height: 40px;
  display: grid;
  place-items: center;
  background: none;
  border: none;
  border-radius: 50%;
  color: var(--color-on-surface);
  cursor: pointer;
  position: relative;
  transition: all 0.2s;
}

.btn-icon:hover {
  background: var(--color-surface-canvas);
}

.btn-icon.active {
  background: var(--color-primary);
  color: var(--color-on-primary);
}

.notification-badge {
  position: absolute;
  top: 5px;
  right: 5px;
  width: 8px;
  height: 8px;
  background: var(--color-error);
  border-radius: 50%;
}

.user-menu {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px 6px 6px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: all 0.2s;
}

.user-menu:hover {
  background: var(--color-surface-canvas);
}

.user-avatar {
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  overflow: hidden;
  background: var(--color-surface-canvas);
  color: var(--color-on-surface);
}

.user-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.user-name {
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 14px;
  font-weight: 400;
  color: var(--color-on-surface);
}

/* ---- Dropdown Menu ---- */

.dropdown-menu {
  position: absolute;
  top: 100%;
  right: 0;
  margin-top: 8px;
  min-width: 200px;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: 16px;
  padding: 8px;
  z-index: 1000;
}

.dropdown-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 8px;
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 14px;
  color: var(--color-on-surface);
  text-decoration: none;
  background: none;
  border: none;
  width: 100%;
  cursor: pointer;
  transition: all 0.2s;
}

.dropdown-item:hover {
  background: var(--color-surface-canvas);
}

.dropdown-item.router-link-active {
  background: var(--color-primary);
  color: var(--color-on-primary);
}

.dropdown-item.router-link-active:hover {
  background: var(--color-primary);
}

.dropdown-divider {
  height: 1px;
  background: var(--color-outline-light);
  margin: 4px 0;
}

/* ---- Side Navigation ---- */

.side-nav {
  position: fixed;
  top: 0;
  bottom: 0;
  left: 0;
  z-index: 100;
  width: 248px;
  display: flex;
  flex-direction: column;
  padding: 20px 16px;
  background: var(--color-surface-card);
  border-right: 1px solid var(--color-outline-light);
  transition: width 0.2s, padding 0.2s;
}

.side-nav-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 28px;
}

.side-logo {
  flex: 1;
  min-width: 0;
  padding: 8px 10px;
}

.side-logo span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sidebar-collapse-btn {
  flex-shrink: 0;
}

.side-nav-links {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.side-nav-link {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  border-radius: 14px;
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 14px;
  font-weight: 400;
  color: var(--color-muted);
  text-decoration: none;
  transition: all 0.2s;
}

.side-nav-link:hover {
  background: var(--color-surface-canvas);
  color: var(--color-on-surface);
}

.side-nav-link.router-link-active {
  background: var(--color-primary);
  color: var(--color-on-primary);
}

.side-nav-footer {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: auto;
  padding-top: 16px;
  border-top: 1px solid var(--color-outline-light);
}

.side-user-menu {
  flex: 1;
  min-width: 0;
  padding-right: 10px;
}

.side-user-menu .user-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.side-dropdown {
  top: auto;
  bottom: calc(100% + 8px);
  left: 0;
  right: auto;
  margin-top: 0;
  min-width: 216px;
}

/* ---- Main Content ---- */

.main-content {
  padding: 0 64px;
}

.layout-sidebar .main-content {
  margin-left: 248px;
  padding: 0 48px;
  transition: margin-left 0.2s;
}

.layout-sidebar.sidebar-collapsed .side-nav {
  width: 84px;
  padding: 20px 12px;
}

.layout-sidebar.sidebar-collapsed .side-nav-header {
  flex-direction: column;
  gap: 12px;
}

.layout-sidebar.sidebar-collapsed .side-logo {
  flex: none;
  justify-content: center;
  padding: 8px;
}

.layout-sidebar.sidebar-collapsed .brand-mark {
  width: 36px;
  height: 36px;
}

.layout-sidebar.sidebar-collapsed .side-logo span,
.layout-sidebar.sidebar-collapsed .side-nav-link span,
.layout-sidebar.sidebar-collapsed .side-user-menu .user-name,
.layout-sidebar.sidebar-collapsed .side-user-menu > svg {
  display: none;
}

.layout-sidebar.sidebar-collapsed .side-nav-link {
  justify-content: center;
  padding: 12px;
}

.layout-sidebar.sidebar-collapsed .side-nav-footer {
  flex-direction: column;
  gap: 8px;
}

.layout-sidebar.sidebar-collapsed .side-user-menu {
  width: 40px;
  height: 40px;
  flex: none;
  justify-content: center;
  padding: 0;
}

.layout-sidebar.sidebar-collapsed .side-dropdown {
  bottom: 0;
  left: calc(100% + 8px);
}

.layout-sidebar.sidebar-collapsed .main-content {
  margin-left: 84px;
}

.content-container {
  max-width: 1280px;
  margin: 0 auto;
  padding: 40px 0;
}

/* ---- Responsive ---- */

@media (max-width: 1024px) {
  .nav-container {
    padding: 0 24px;
  }

  .main-content {
    padding: 0 24px;
  }

  .nav-links {
    display: none;
  }

  .layout-sidebar .side-nav {
    position: sticky;
    bottom: auto;
    width: 100%;
    height: 64px;
    flex-direction: row;
    align-items: center;
    padding: 0 24px;
    border-right: none;
    border-bottom: 1px solid var(--color-outline-light);
  }

  .layout-sidebar .side-logo {
    flex: 1;
    padding: 0;
  }

  .layout-sidebar .side-nav-header {
    flex: 1;
    margin-bottom: 0;
  }

  .layout-sidebar .side-nav-links {
    display: none;
  }

  .layout-sidebar .side-nav-footer {
    width: auto;
    margin-top: 0;
    margin-left: auto;
    padding-top: 0;
    border-top: none;
  }

  .layout-sidebar .side-dropdown {
    top: 100%;
    bottom: auto;
    left: auto;
    right: 0;
    margin-top: 8px;
  }

  .layout-sidebar .main-content {
    margin-left: 0;
    padding: 0 24px;
  }

  .layout-sidebar.sidebar-collapsed .side-nav {
    width: 100%;
    padding: 0 24px;
  }

  .layout-sidebar.sidebar-collapsed .side-nav-header {
    flex: 1;
    flex-direction: row;
    gap: 8px;
    margin-bottom: 0;
  }

  .layout-sidebar.sidebar-collapsed .side-logo {
    justify-content: flex-start;
    padding: 0;
  }

  .layout-sidebar.sidebar-collapsed .side-logo span,
  .layout-sidebar.sidebar-collapsed .side-user-menu > svg {
    display: block;
  }

  .layout-sidebar.sidebar-collapsed .side-nav-footer {
    flex-direction: row;
  }

  .layout-sidebar.sidebar-collapsed .side-user-menu {
    width: auto;
    height: auto;
    flex: 1;
    justify-content: flex-start;
    padding: 6px 10px 6px 6px;
  }

  .layout-sidebar.sidebar-collapsed .side-dropdown {
    top: 100%;
    bottom: auto;
    left: auto;
    right: 0;
  }

  .layout-sidebar.sidebar-collapsed .main-content {
    margin-left: 0;
  }

  .layout-sidebar:not(.sidebar-collapsed) .side-nav {
    position: fixed;
    top: 0;
    bottom: 0;
    left: 0;
    z-index: 120;
    width: min(320px, 86vw);
    height: 100dvh;
    flex-direction: column;
    align-items: stretch;
    padding: 20px 16px;
    background: var(--color-surface-card);
    border-right: 1px solid var(--color-outline-light);
    border-bottom: none;
    box-shadow: 16px 0 40px rgba(15, 23, 42, 0.16);
    animation: mobileSidebarSlideIn 0.22s ease-out both;
  }

  .layout-sidebar:not(.sidebar-collapsed) .side-nav-header {
    flex: none;
    margin-bottom: 28px;
  }

  .layout-sidebar:not(.sidebar-collapsed) .side-logo {
    flex: 1;
    padding: 8px 10px;
  }

  .layout-sidebar:not(.sidebar-collapsed) .side-nav-links {
    display: flex;
  }

  .layout-sidebar:not(.sidebar-collapsed) .side-nav-footer {
    width: 100%;
    margin-top: auto;
    margin-left: 0;
    padding-top: 16px;
    border-top: 1px solid var(--color-outline-light);
  }

  @keyframes mobileSidebarSlideIn {
    from {
      transform: translateX(-100%);
    }

    to {
      transform: translateX(0);
    }
  }
}

@media (max-width: 640px) {
  .nav-container {
    padding: 0 16px;
  }

  .main-content {
    padding: 0 16px;
  }

  .layout-sidebar .side-nav {
    padding: 0 16px;
  }

  .layout-sidebar:not(.sidebar-collapsed) .side-nav {
    padding: 20px 16px;
  }

  .layout-sidebar .main-content {
    padding: 0 16px;
  }

  .user-name {
    display: none;
  }
}
</style>
