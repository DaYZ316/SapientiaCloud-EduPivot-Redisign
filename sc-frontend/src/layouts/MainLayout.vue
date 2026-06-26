<template>
  <div
      :class="[
      layoutShellClass,
      { 'sidebar-collapsed': isCollapsibleSidebar && uiPreferences.sidebarCollapsed },
      { 'layout-ai-mode': isAiPage },
      { 'layout-fullscreen': isFullscreenPage },
    ]"
      class="layout"
  >
    <!-- Top Navigation Bar -->
    <header v-if="!isFullscreenPage && !isSidebarShell" class="top-nav">
      <div class="nav-container">
        <!-- Logo -->
        <button
            :aria-label="modeSwitchAriaLabel"
            :disabled="isSwitchingAiMode"
            class="nav-logo mode-switch"
            type="button"
            @click="toggleAiMode"
        >
          <img :src="brandLogoSrc" alt="" class="brand-mark"/>
          <span>{{ modeSwitchLabel }}</span>
        </button>

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
          <button :class="{ active: isNotificationsPage }" class="btn-icon" @click="$router.push('/notifications')">
            <Bell :size="20" stroke-width="1.8"/>
            <span v-if="unreadCount > 0" class="notification-badge"></span>
          </button>
          <div class="user-menu" @click="showUserMenu = !showUserMenu">
            <div :class="{ 'user-avatar--image': Boolean(authStore.user?.avatarUrl) }" class="user-avatar">
              <img v-if="authStore.user?.avatarUrl" :src="authStore.user.avatarUrl" alt="Avatar"/>
              <span v-else>{{ avatarInitials }}</span>
            </div>
            <span class="user-name">{{ authStore.user?.displayName || 'User' }}</span>
            <ChevronDown :size="16" stroke-width="1.8"/>
          </div>

          <!-- Dropdown Menu -->
          <div v-if="showUserMenu" class="dropdown-menu">
            <router-link class="dropdown-item" to="/profile" @click="showUserMenu = false">
              <UserCircle :size="16"/>
              <span>{{ t('common.menu.profile') }}</span>
            </router-link>
            <router-link class="dropdown-item" to="/settings" @click="showUserMenu = false">
              <Settings :size="16"/>
              <span>{{ t('common.menu.settings') }}</span>
            </router-link>
            <template v-if="isAdmin">
              <div class="dropdown-divider"></div>
              <router-link class="dropdown-item" to="/admin/users" @click="showUserMenu = false">
                <Users :size="16"/>
                <span>{{ t('common.menu.users') }}</span>
              </router-link>
              <router-link class="dropdown-item" to="/admin/students" @click="showUserMenu = false">
                <GraduationCap :size="16"/>
                <span>{{ t('common.menu.students') }}</span>
              </router-link>
              <router-link class="dropdown-item" to="/admin/teachers" @click="showUserMenu = false">
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
    <aside v-else-if="!isFullscreenPage && isSidebarShell" aria-label="Primary navigation" class="side-nav">
      <div class="side-nav-header">
        <button
            :aria-label="modeSwitchAriaLabel"
            :disabled="isSwitchingAiMode"
            class="nav-logo side-logo mode-switch"
            type="button"
            @click="toggleAiMode"
        >
          <img :src="brandLogoSrc" alt="" class="brand-mark"/>
          <span>{{ modeSwitchLabel }}</span>
        </button>
        <button
            :aria-label="uiPreferences.sidebarCollapsed ? t('common.layout.expandSidebar') : t('common.layout.collapseSidebar')"
            class="btn-icon sidebar-collapse-btn"
            type="button"
            @click="uiPreferences.toggleSidebarCollapsed"
        >
          <PanelLeftOpen v-if="uiPreferences.sidebarCollapsed" :size="22" stroke-width="1.8"/>
          <PanelLeftClose v-else :size="22" stroke-width="1.8"/>
        </button>
      </div>

      <template v-if="isAiPage">
        <AiSourcePanel class="side-ai-panel"/>
      </template>
      <template v-else>
        <nav class="side-nav-links">
          <router-link
              v-for="item in navItems"
              :key="item.path"
              :aria-label="uiPreferences.sidebarCollapsed ? item.label : undefined"
              :title="uiPreferences.sidebarCollapsed ? item.label : undefined"
              :to="item.path"
              class="side-nav-link"
          >
            <component :is="item.icon" :size="18" stroke-width="1.8"/>
            <span>{{ item.label }}</span>
          </router-link>
        </nav>

        <div v-if="recentCourses.length > 0" class="side-recent-section">
          <div class="side-recent-header">
            <Clock :size="14" stroke-width="1.8"/>
            <span>{{ t('common.navigation.recentCourses') }}</span>
          </div>
          <nav class="side-recent-links">
            <router-link
                v-for="item in recentCourses"
                :key="item.id"
                :title="uiPreferences.sidebarCollapsed ? item.title : undefined"
                :to="`/courses/${item.id}`"
                class="side-recent-link"
            >
              <div class="recent-cover">
                <img :src="getCourseCoverUrl(item.coverUrl)" alt="" @error="handleCourseCoverError"/>
              </div>
              <span class="recent-title">{{ item.title }}</span>
            </router-link>
          </nav>
        </div>

      </template>

      <div class="side-nav-footer">
        <button :class="{ active: isNotificationsPage }" class="btn-icon" @click="$router.push('/notifications')">
          <Bell :size="20" stroke-width="1.8"/>
          <span v-if="unreadCount > 0" class="notification-badge"></span>
        </button>
        <div class="user-menu side-user-menu" @click="showUserMenu = !showUserMenu">
          <div :class="{ 'user-avatar--image': Boolean(authStore.user?.avatarUrl) }" class="user-avatar">
            <img v-if="authStore.user?.avatarUrl" :src="authStore.user.avatarUrl" alt="Avatar"/>
            <span v-else>{{ avatarInitials }}</span>
          </div>
          <span class="user-name">{{ authStore.user?.displayName || 'User' }}</span>
          <ChevronDown :size="16" stroke-width="1.8"/>
        </div>

        <div v-if="showUserMenu" class="dropdown-menu side-dropdown">
          <router-link class="dropdown-item" to="/profile" @click="showUserMenu = false">
            <UserCircle :size="16"/>
            <span>{{ t('common.menu.profile') }}</span>
          </router-link>
          <router-link class="dropdown-item" to="/settings" @click="showUserMenu = false">
            <Settings :size="16"/>
            <span>{{ t('common.menu.settings') }}</span>
          </router-link>
          <template v-if="isAdmin">
            <div class="dropdown-divider"></div>
            <router-link class="dropdown-item" to="/admin/users" @click="showUserMenu = false">
              <Users :size="16"/>
              <span>{{ t('common.menu.users') }}</span>
            </router-link>
            <router-link class="dropdown-item" to="/admin/students" @click="showUserMenu = false">
              <GraduationCap :size="16"/>
              <span>{{ t('common.menu.students') }}</span>
            </router-link>
            <router-link class="dropdown-item" to="/admin/teachers" @click="showUserMenu = false">
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

    <button
      v-if="!isFullscreenPage && isAiPage && uiPreferences.sidebarCollapsed"
      :aria-label="t('common.layout.expandSidebar')"
      class="ai-sidebar-pull-tab"
      type="button"
      @click="uiPreferences.setSidebarCollapsed(false)"
    >
      <PanelLeftOpen
        :size="22"
        stroke-width="1.9"
      />
    </button>

    <!-- Main Content -->
    <main class="main-content">
      <div class="content-container">
        <router-view/>
      </div>
    </main>
    <GlobalAiDrawer
      v-if="!isFullscreenPage && !isAiPage"
      ref="globalAiDrawerRef"
    />
    <AiTrailLauncher
      v-if="aiReturnLauncherVisible"
      ref="aiReturnLauncherRef"
      :interactive="false"
      aria-hidden="true"
      start-at-center
      tabindex="-1"
    />
    <AiModeTransitionOverlay ref="aiModeTransitionOverlayRef"/>
  </div>
</template>

<script lang="ts" setup>
import {computed, nextTick, onMounted, onUnmounted, provide, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter, type RouteLocationRaw} from 'vue-router'
import {
  Bell,
  BookOpen,
  ChevronDown,
  ClipboardList,
  Clock,
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
import {useRecentCourses} from '@/shared/composables/useRecentCourses'
import {useAuthStore} from '@/features/auth/stores/auth'
import {useUiPreferencesStore} from '@/features/settings/stores/uiPreferences'
import {useAiStore} from '@/features/ai/stores/ai'
import {aiModeNavigationKey} from '@/features/ai/composables/useAiModeNavigation'
import GlobalAiDrawer from '@/features/ai/components/GlobalAiDrawer.vue'
import AiTrailLauncher from '@/features/ai/components/AiTrailLauncher.vue'
import AiModeTransitionOverlay from '@/features/ai/components/AiModeTransitionOverlay.vue'
import AiSourcePanel from '@/features/ai/components/AiSourcePanel.vue'
import {getAvatarInitials} from '@/shared/utils/avatar'
import {getCourseCoverUrl, handleCourseCoverError} from '@/shared/utils/courseCover'

type GlobalAiDrawerInstance = InstanceType<typeof GlobalAiDrawer>
type AiTrailLauncherInstance = InstanceType<typeof AiTrailLauncher>
type AiModeTransitionOverlayInstance = InstanceType<typeof AiModeTransitionOverlay>

function preloadAiWorkspace() {
  return import('@/features/ai/views/AiWorkspaceView.vue')
}

const router = useRouter()
const authStore = useAuthStore()
const uiPreferences = useUiPreferencesStore()
const aiStore = useAiStore()
const {t} = useI18n()
const {recentCourses} = useRecentCourses()

const showUserMenu = ref(false)
const isSwitchingAiMode = ref(false)
const aiReturnLauncherVisible = ref(false)
const globalAiDrawerRef = ref<GlobalAiDrawerInstance | null>(null)
const aiReturnLauncherRef = ref<AiTrailLauncherInstance | null>(null)
const aiModeTransitionOverlayRef = ref<AiModeTransitionOverlayInstance | null>(null)
const {unreadCount} = useUnreadCount()

provide(aiModeNavigationKey, switchToAi)

const isAdmin = computed(() => authStore.user?.role === 0)
const isTeacher = computed(() => authStore.user?.role === 2)

const avatarInitials = computed(() => getAvatarInitials(authStore.user?.displayName))
const brandLogoSrc = computed(() =>
    uiPreferences.resolvedTheme === 'dark'
        ? '/assets/project-logo-dark.png'
        : '/assets/project-logo-light.png',
)
const isAiPage = computed(() => {
    const routeName = router.currentRoute.value.name
    return routeName === 'ai-workspace' || routeName === 'ai-history' || routeName === 'ai-favorites'
})
const isSidebarShell = computed(() => uiPreferences.isSidebarLayout || isAiPage.value)
const isCollapsibleSidebar = computed(() => isSidebarShell.value)
const layoutShellClass = computed(() => isSidebarShell.value ? 'layout-sidebar' : 'layout-topbar')
const modeSwitchLabel = computed(() => isAiPage.value ? t('common.brand.title') : t('common.navigation.ai'))
const modeSwitchAriaLabel = computed(() =>
    isAiPage.value ? t('common.layout.switchToApp') : t('common.layout.switchToAi'),
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
const isFullscreenPage = computed(() => router.currentRoute.value.meta.fullscreen === true)

function handleClickOutside(event: MouseEvent) {
  const target = event.target as HTMLElement
  if (!target.closest('.user-menu') && !target.closest('.dropdown-menu')) {
    showUserMenu.value = false
  }
}

onMounted(() => {
  document.addEventListener('click', handleClickOutside)
})

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
})

async function handleLogout() {
  showUserMenu.value = false
  await authStore.logout()
  await router.replace('/login')
}

async function toggleAiMode() {
  if (isSwitchingAiMode.value) return

  if (isAiPage.value) {
    await switchBackToApp()
    return
  }

  await switchToAi()
}

async function switchToAi(target: RouteLocationRaw = '/ai') {
  if (isSwitchingAiMode.value) return

  isSwitchingAiMode.value = true
  try {
    await Promise.all([
      aiModeTransitionOverlayRef.value?.playOutward() ?? Promise.resolve(),
      globalAiDrawerRef.value?.playLauncherCenterTransition() ?? Promise.resolve(),
      preloadAiWorkspace(),
      preloadAiWorkspaceData(),
    ])
    await router.push(target)
    await nextTick()
    aiModeTransitionOverlayRef.value?.hide()
  } catch (error) {
    globalAiDrawerRef.value?.finishLauncherCenterTransition()
    aiModeTransitionOverlayRef.value?.hide()
    throw error
  } finally {
    isSwitchingAiMode.value = false
  }
}

async function switchBackToApp() {
  isSwitchingAiMode.value = true
  aiReturnLauncherVisible.value = true
  await nextTick()

  try {
    await Promise.all([
      aiModeTransitionOverlayRef.value?.playInward() ?? Promise.resolve(),
      aiReturnLauncherRef.value?.playReturnTransition() ?? Promise.resolve(),
    ])
    aiReturnLauncherVisible.value = false
    await nextTick()
    await router.push('/dashboard')
    await nextTick()
    aiModeTransitionOverlayRef.value?.hide()
  } catch (error) {
    aiReturnLauncherRef.value?.finishTransition()
    aiModeTransitionOverlayRef.value?.hide()
    throw error
  } finally {
    aiReturnLauncherVisible.value = false
    isSwitchingAiMode.value = false
  }
}

async function preloadAiWorkspaceData() {
  await aiStore.ensureConversationsLoaded()
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

.mode-switch {
  max-width: 100%;
  padding: 0;
  background: transparent;
  border: 0;
  cursor: pointer;
  text-align: left;
}

.mode-switch:hover {
  color: var(--color-primary);
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
  background: var(--color-primary);
  color: var(--color-on-primary);
  font-family: var(--font-heading);
  font-size: 12px;
  font-weight: 700;
}

.user-avatar--image {
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

.layout-ai-mode {
  --font-ai-sans: MiSans, 'Noto Sans SC', 'Microsoft YaHei', DengXian, 'Segoe UI', system-ui, sans-serif;
  --font-ai-mono: 'Cascadia Code', 'Cascadia Mono', Consolas, monospace;
  --font-heading: 'Bodoni Moda', 'Georgia', serif;
  --font-body: var(--font-ai-sans);
  --font-label: var(--font-ai-sans);
  font-family: var(--font-ai-sans);
}

.layout-ai-mode .side-nav {
  width: 248px;
  padding: 20px 16px;
  background: var(--color-surface-card);
}

.layout-ai-mode .user-name,
.layout-ai-mode .dropdown-item {
  font-family: var(--font-ai-sans);
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

.layout-ai-mode .side-nav-header {
  margin-bottom: 18px;
}

.layout-ai-mode .side-logo {
  flex: 1;
  width: auto;
  min-height: 48px;
  padding: 8px 10px;
}

.side-logo span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.side-ai-panel {
  min-height: 0;
}

.sidebar-collapse-btn {
  flex: 0 0 32px;
  width: 32px;
  height: 32px;
  border: 0;
  background: transparent;
  color: var(--color-muted);
}

.sidebar-collapse-btn:hover,
.sidebar-collapse-btn:focus-visible {
  background: transparent;
  color: var(--color-on-surface);
}

.ai-sidebar-pull-tab {
  display: none;
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

/* ---- Recent Courses ---- */

.side-recent-section {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid var(--color-outline-light);
}

.side-recent-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 0 14px;
  margin-bottom: 6px;
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--color-muted);
}

.side-recent-links {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.side-recent-link {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 14px;
  border-radius: 10px;
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 13px;
  font-weight: 400;
  color: var(--color-muted);
  text-decoration: none;
  transition: all 0.2s;
}

.side-recent-link:hover {
  background: var(--color-surface-canvas);
  color: var(--color-on-surface);
}

.side-recent-link.router-link-active {
  background: var(--color-primary);
  color: var(--color-on-primary);
}

.recent-cover {
  width: 24px;
  height: 24px;
  border-radius: 6px;
  overflow: hidden;
  flex-shrink: 0;
  background: var(--color-surface-canvas);
}

.recent-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.recent-title {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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

.layout-ai-mode .main-content {
  margin-left: 248px;
  padding: 0;
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

.layout-sidebar.sidebar-collapsed .side-recent-header span,
.layout-sidebar.sidebar-collapsed .side-recent-link .recent-title {
  display: none;
}

.layout-sidebar.sidebar-collapsed .side-recent-header {
  justify-content: center;
  padding: 0;
}

.layout-sidebar.sidebar-collapsed .side-recent-link {
  justify-content: center;
  padding: 8px;
}

.layout-sidebar.sidebar-collapsed .side-nav-link {
  justify-content: center;
  padding: 12px;
}

.layout-sidebar.sidebar-collapsed .side-nav-footer {
  flex-direction: column;
  gap: 8px;
}

.layout-ai-mode.sidebar-collapsed .side-ai-panel :deep(.quick-actions) {
  padding-bottom: 12px;
}

.layout-ai-mode.sidebar-collapsed .side-ai-panel :deep(.quick-action) {
  justify-content: center;
  padding: 12px;
}

.layout-ai-mode.sidebar-collapsed .side-ai-panel :deep(.quick-action span),
.layout-ai-mode.sidebar-collapsed .side-ai-panel :deep(.session-list) {
  display: none;
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

.layout-ai-mode .content-container {
  max-width: none;
  padding: 0;
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

  .layout-sidebar .side-recent-section {
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

  .layout-sidebar:not(.sidebar-collapsed) .side-recent-section {
    display: block;
  }

  .layout-sidebar:not(.sidebar-collapsed) .side-nav-footer {
    width: 100%;
    margin-top: auto;
    margin-left: 0;
    padding-top: 16px;
    border-top: 1px solid var(--color-outline-light);
  }

  .layout-ai-mode .side-nav {
    position: fixed;
    top: 0;
    bottom: 0;
    left: 0;
    width: 248px;
    height: 100dvh;
    flex-direction: column;
    align-items: stretch;
    padding: 20px 16px;
    border-right: 1px solid var(--color-outline-light);
    border-bottom: none;
    box-shadow: none;
    animation: none;
  }

  .layout-ai-mode .side-nav-header {
    flex: none;
    margin-bottom: 18px;
  }

  .layout-ai-mode .side-logo {
    width: auto;
    flex: 1;
    padding: 8px 10px;
  }

  .layout-ai-mode .side-ai-panel {
    display: flex;
  }

  .layout-ai-mode .main-content {
    margin-left: 248px;
    padding: 0;
  }

  .layout-ai-mode.sidebar-collapsed .side-nav {
    width: 84px;
    padding: 20px 12px;
  }

  .layout-ai-mode.sidebar-collapsed .main-content {
    margin-left: 84px;
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

  .layout-ai-mode .side-nav {
    position: fixed;
    top: 0;
    bottom: 0;
    left: 0;
    z-index: 130;
    width: min(248px, 82vw);
    height: 100dvh;
    padding: 20px 16px;
    border-right: 1px solid var(--color-outline-light);
    background: var(--color-surface-card);
    box-shadow: 16px 0 40px rgba(15, 23, 42, 0.16);
  }

  .layout-ai-mode .main-content {
    margin-left: 0;
    padding: 0;
  }

  .layout-ai-mode.sidebar-collapsed .side-nav {
    width: 0;
    padding: 0;
    overflow: hidden;
    border-right: 0;
  }

  .layout-ai-mode.sidebar-collapsed .main-content {
    margin-left: 0;
  }

  .layout-ai-mode.sidebar-collapsed .ai-sidebar-pull-tab {
    position: fixed;
    top: calc(env(safe-area-inset-top) + 96px);
    left: 0;
    z-index: 125;
    display: grid;
    width: 42px;
    height: 56px;
    place-items: center;
    padding: 0;
    border: 1px solid var(--color-outline-light);
    border-left: 0;
    border-radius: 0 14px 14px 0;
    background: var(--color-surface-card);
    box-shadow: 10px 8px 24px rgba(15, 23, 42, 0.14);
    color: var(--color-on-surface);
    cursor: pointer;
    transition: transform 0.18s ease, color 0.18s ease, background 0.18s ease;
  }

  .layout-ai-mode.sidebar-collapsed .ai-sidebar-pull-tab:hover,
  .layout-ai-mode.sidebar-collapsed .ai-sidebar-pull-tab:focus-visible {
    background: var(--color-primary);
    color: var(--color-on-primary);
    transform: translateX(2px);
  }

  .layout-ai-mode .content-container {
    padding: 0;
  }

  .user-name {
    display: none;
  }
}

.layout-fullscreen {
  min-height: 100dvh;
  overflow: hidden;
  background: #0b1020;
}

.layout-fullscreen .main-content,
.layout-sidebar.layout-fullscreen .main-content,
.layout-sidebar.sidebar-collapsed.layout-fullscreen .main-content {
  margin-left: 0;
  min-height: 100dvh;
  padding: 0;
}

.layout-fullscreen .content-container {
  width: 100vw;
  max-width: none;
  height: 100dvh;
  margin: 0;
  padding: 0;
}
</style>
