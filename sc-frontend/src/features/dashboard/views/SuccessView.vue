<template>
  <div class="dashboard">
    <!-- Welcome Section -->
    <div class="welcome-section">
      <h1>{{ t('success.welcomeUser', {name: authStore.user?.displayName ?? t('profile.placeholder.user')}) }}</h1>
    </div>

    <div class="dashboard-grid">
      <!-- Left Column -->
      <div class="left-column">
        <!-- Stats Cards -->
        <div class="stats-row">
          <div class="stat-card">
            <div class="stat-label">{{ t('success.enrolledCourses') }}</div>
            <div class="stat-value">--</div>
          </div>
          <div class="stat-card">
            <div class="stat-label">{{ t('success.completed') }}</div>
            <div class="stat-value">--</div>
          </div>
          <div class="stat-card">
            <div class="stat-label">{{ t('success.gpa') }}</div>
            <div class="stat-value">--</div>
          </div>
        </div>

        <!-- Recent Notifications -->
        <div class="section">
          <div class="section-header">
            <h2>{{ t('success.recentNotifications') }}</h2>
            <router-link class="btn-text" to="/notifications">{{ t('success.viewAll') }}</router-link>
          </div>
          <div class="notification-list">
            <div
                v-for="notification in recentNotifications"
                :key="notification.id"
                :class="{ unread: !notification.isRead }"
                class="notification-item"
            >
              <div :class="notification.type" class="notification-icon">
                <component :is="getNotificationIcon(notification.type)" :size="18" stroke-width="1.8"/>
              </div>
              <div class="notification-content">
                <div class="notification-title">{{ notification.title }}</div>
                <div class="notification-time">{{ notification.time }}</div>
              </div>
              <div v-if="!notification.isRead" class="unread-dot"></div>
            </div>
          </div>
        </div>
      </div>

      <!-- Right Column -->
      <div class="right-column">
        <!-- Profile Card -->
        <div class="card profile-card">
          <div class="profile-avatar">
            <img v-if="authStore.user?.avatarUrl" :src="authStore.user.avatarUrl" alt="Avatar"/>
            <img v-else :src="defaultAvatarSrc" alt="Avatar"/>
          </div>
          <h3 class="profile-name">{{ authStore.user?.displayName ?? 'User' }}</h3>
          <p class="profile-email">{{ authStore.user?.email ?? '-' }}</p>
          <span class="tag">{{ getRoleName(authStore.user?.role) }}</span>
          <router-link class="btn-secondary" to="/profile">{{ t('success.editProfile') }}</router-link>
        </div>

        <!-- Quick Actions -->
        <div class="card quick-actions">
          <h3 class="card-title">{{ t('success.quickActions') }}</h3>
          <div class="action-list">
            <router-link class="action-item" to="/courses">
              <span>{{ t('success.viewSchedule') }}</span>
              <ArrowRight :size="16"/>
            </router-link>
            <router-link class="action-item" to="/courses">
              <span>{{ t('success.submitAssignment') }}</span>
              <ArrowRight :size="16"/>
            </router-link>
            <router-link class="action-item" to="/courses">
              <span>{{ t('success.contactAdvisor') }}</span>
              <ArrowRight :size="16"/>
            </router-link>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import {ArrowRight, BookOpen, Settings} from 'lucide-vue-next'
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'

import {useAuthStore} from '@/features/auth/stores/auth'

const {t} = useI18n()
const authStore = useAuthStore()

const defaultAvatarSrc = computed(() => {
  switch (authStore.user?.role) {
    case 0:
      return '/assets/avatar-admin-default.png'
    case 2:
      return '/assets/avatar-teacher-default.png'
    default:
      return '/assets/avatar-student-default.png'
  }
})

const recentNotifications: { id: string; type: string; title: string; time: string; isRead: boolean }[] = []

function getRoleName(role?: number | null): string {
  switch (role) {
    case 0:
      return t('success.roleAdmin')
    case 1:
      return t('success.roleStudent')
    case 2:
      return t('success.roleTeacher')
    default:
      return t('success.roleUser')
  }
}

function getNotificationIcon(type: string) {
  switch (type) {
    case 'teaching':
      return BookOpen
    case 'system':
      return Settings
    default:
      return Settings
  }
}
</script>

<style scoped>
.dashboard {
  max-width: 100%;
}

/* ---- Welcome Section ---- */

.welcome-section {
  margin-bottom: 40px;
}

.welcome-section h1 {
  margin: 0;
  font-family: var(--font-heading);
  font-size: 48px;
  font-weight: 400;
  color: var(--color-on-surface);
  line-height: 1.3;
}

/* ---- Dashboard Grid ---- */

.dashboard-grid {
  display: grid;
  grid-template-columns: 1fr 360px;
  gap: 32px;
}

/* ---- Left Column ---- */

.left-column {
  display: flex;
  flex-direction: column;
  gap: 32px;
}

/* ---- Stats Row ---- */

.stats-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

.stat-card {
  padding: 24px;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: 24px;
}

.stat-label {
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 14px;
  font-weight: 500;
  color: var(--color-muted);
  margin-bottom: 8px;
}

.stat-value {
  font-family: var(--font-heading);
  font-size: 48px;
  font-weight: 700;
  color: var(--color-on-surface);
}

/* ---- Section ---- */

.section {
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: 24px;
  padding: 28px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.section-header h2 {
  margin: 0;
  font-family: var(--font-heading);
  font-size: 24px;
  font-weight: 400;
  color: var(--color-on-surface);
}

/* ---- Notification List ---- */

.notification-list {
  display: flex;
  flex-direction: column;
}

.notification-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px 0;
  border-bottom: 1px solid var(--color-surface-canvas);
}

.notification-item:last-child {
  border-bottom: none;
}

.notification-item.unread .notification-title {
  font-weight: 400;
}

.notification-icon {
  width: 40px;
  height: 40px;
  display: grid;
  place-items: center;
  border-radius: 12px;
  background: var(--color-surface-canvas);
  color: var(--color-on-surface);
  flex-shrink: 0;
}

.notification-icon.system {
  background: var(--color-surface-canvas);
}

.notification-icon.teaching {
  background: var(--color-surface-canvas);
}

.notification-content {
  flex: 1;
  min-width: 0;
}

.notification-title {
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 14px;
  color: var(--color-on-surface);
  margin-bottom: 4px;
}

.notification-time {
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 12px;
  color: var(--color-muted);
}

.unread-dot {
  width: 8px;
  height: 8px;
  background: var(--color-primary);
  border-radius: 50%;
  flex-shrink: 0;
}

/* ---- Right Column ---- */

.right-column {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

/* ---- Card ---- */

.card {
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: 24px;
  padding: 32px;
}

/* ---- Profile Card ---- */

.profile-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.profile-avatar {
  width: 80px;
  height: 80px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  overflow: hidden;
  background: var(--color-surface-canvas);
  color: var(--color-on-surface);
  margin-bottom: 16px;
}

.profile-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.profile-name {
  margin: 0 0 4px;
  font-family: var(--font-heading);
  font-size: 24px;
  font-weight: 400;
  color: var(--color-on-surface);
}

.profile-email {
  margin: 0 0 12px;
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 14px;
  color: var(--color-muted);
}

/* ---- Quick Actions ---- */

.card-title {
  margin: 0 0 20px;
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 12px;
  font-weight: 400;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--color-muted);
}

.action-list {
  display: flex;
  flex-direction: column;
}

.action-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 0;
  border-bottom: 1px solid var(--color-surface-canvas);
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 14px;
  font-weight: 500;
  color: var(--color-on-surface);
  text-decoration: none;
  transition: color 0.2s;
}

.action-item:last-child {
  border-bottom: none;
}

.action-item:hover {
  color: var(--color-muted);
}

/* ---- Responsive ---- */

@media (max-width: 1024px) {
  .dashboard-grid {
    grid-template-columns: 1fr;
  }

  .stats-row {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (max-width: 640px) {
  .welcome-section h1 {
    font-size: 32px;
  }

  .stats-row {
    grid-template-columns: 1fr;
  }
}
</style>
