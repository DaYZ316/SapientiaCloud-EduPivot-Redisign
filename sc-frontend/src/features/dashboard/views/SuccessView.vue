<template>
  <main class="dashboard-workbench">
    <section aria-labelledby="dashboard-title" class="workbench-heading">
      <div>
        <p class="workbench-kicker">{{ roleCopy.kicker }}</p>
        <h1 id="dashboard-title">{{ greetingTitle }}</h1>
        <p class="workbench-subtitle">{{ roleCopy.subtitle }}</p>
      </div>
      <div class="identity-strip">
        <span class="identity-role">{{ roleLabel }}</span>
        <span>{{ authStore.user?.displayName || 'SapientiaCloud' }}</span>
      </div>
    </section>

    <section v-if="loadError" class="dashboard-state-panel" role="alert">
      <strong>{{ t('success.dashboard.state.loadFailedTitle') }}</strong>
      <p>{{ t('success.dashboard.state.loadFailedDescription') }}</p>
      <button class="dashboard-command-button primary" type="button" @click="loadDashboard">
        {{ t('success.dashboard.state.retry') }}
      </button>
    </section>

    <section v-else-if="isLoading" :aria-label="t('success.dashboard.state.loading')" aria-live="polite"
             class="dashboard-loading-layout">
      <section aria-hidden="true" class="dashboard-loading-live dashboard-loading-surface">
        <div class="dashboard-loading-copy">
          <span class="dashboard-loading-pill"></span>
          <span class="dashboard-loading-line wide"></span>
          <span class="dashboard-loading-line medium"></span>
        </div>
        <span class="dashboard-loading-command"></span>
      </section>

      <section aria-hidden="true" class="dashboard-loading-metrics">
        <article v-for="item in 6" :key="item" class="dashboard-loading-metric dashboard-loading-surface">
          <span class="dashboard-loading-line short"></span>
          <span class="dashboard-loading-number"></span>
          <span class="dashboard-loading-line micro"></span>
        </article>
      </section>

      <section aria-hidden="true" class="dashboard-loading-grid">
        <div class="dashboard-loading-main">
          <article class="dashboard-loading-panel dashboard-loading-panel--large dashboard-loading-surface">
            <span class="dashboard-loading-line short"></span>
            <span class="dashboard-loading-line wide"></span>
            <div class="dashboard-loading-chart"></div>
          </article>
          <article class="dashboard-loading-panel dashboard-loading-surface">
            <span class="dashboard-loading-line medium"></span>
            <div class="dashboard-loading-list">
              <span v-for="item in 3" :key="item" class="dashboard-loading-row"></span>
            </div>
          </article>
        </div>

        <aside class="dashboard-loading-side">
          <article class="dashboard-loading-panel dashboard-loading-surface">
            <span class="dashboard-loading-line short"></span>
            <div class="dashboard-loading-action-grid">
              <span v-for="item in 4" :key="item" class="dashboard-loading-action"></span>
            </div>
          </article>
          <article class="dashboard-loading-panel dashboard-loading-panel--compact dashboard-loading-surface">
            <span class="dashboard-loading-line medium"></span>
            <div class="dashboard-loading-list">
              <span v-for="item in 4" :key="item" class="dashboard-loading-row"></span>
            </div>
          </article>
        </aside>
      </section>
    </section>

    <AdminDashboardPanel
        v-else-if="dashboard?.role === 0 && dashboard.admin"
        :admin="dashboard.admin"
    />
    <TeacherDashboardPanel
        v-else-if="dashboard?.role === 2 && dashboard.teacher"
        :teacher="dashboard.teacher"
    />
    <StudentDashboardPanel
        v-else-if="dashboard?.role === 1 && dashboard.student"
        :student="dashboard.student"
    />

    <section v-else class="dashboard-state-panel">
      <strong>{{ t('success.dashboard.state.unavailableTitle') }}</strong>
      <p>{{ t('success.dashboard.state.unavailableDescription') }}</p>
    </section>
  </main>
</template>

<script lang="ts" setup>
import {computed, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'

import {useAuthStore} from '@/features/auth/stores/auth'
import {type DashboardResponse, getMyDashboard,} from '@/features/dashboard/api/dashboard'
import AdminDashboardPanel from '@/features/dashboard/components/AdminDashboardPanel.vue'
import StudentDashboardPanel from '@/features/dashboard/components/StudentDashboardPanel.vue'
import TeacherDashboardPanel from '@/features/dashboard/components/TeacherDashboardPanel.vue'
import '@/features/dashboard/styles/dashboard.css'

const authStore = useAuthStore()
const {t} = useI18n()

const isLoading = ref(true)
const loadError = ref(false)
const dashboard = ref<DashboardResponse | null>(null)

const role = computed(() => authStore.user?.role ?? 1)
const displayName = computed(() => authStore.user?.displayName || authStore.user?.email || 'SapientiaCloud')

const roleLabel = computed(() => {
  if (role.value === 0) return t('success.dashboard.role.admin')
  if (role.value === 2) return t('success.dashboard.role.teacher')
  return t('success.dashboard.role.student')
})

const greetingTitle = computed(() => {
  if (role.value === 0) return t('success.dashboard.greeting.admin', {name: displayName.value})
  if (role.value === 2) return t('success.dashboard.greeting.teacher', {name: displayName.value})
  return t('success.dashboard.greeting.student', {name: displayName.value})
})

const roleCopy = computed(() => {
  if (role.value === 0) {
    return {
      kicker: t('success.dashboard.copy.admin.kicker'),
      subtitle: t('success.dashboard.copy.admin.subtitle'),
    }
  }
  if (role.value === 2) {
    return {
      kicker: t('success.dashboard.copy.teacher.kicker'),
      subtitle: t('success.dashboard.copy.teacher.subtitle'),
    }
  }
  return {
    kicker: t('success.dashboard.copy.student.kicker'),
    subtitle: t('success.dashboard.copy.student.subtitle'),
  }
})

onMounted(loadDashboard)

watch(
    () => authStore.user?.role,
    () => loadDashboard(),
)

async function loadDashboard() {
  isLoading.value = true
  loadError.value = false
  try {
    dashboard.value = await getMyDashboard()
  } catch {
    dashboard.value = null
    loadError.value = true
  } finally {
    isLoading.value = false
  }
}
</script>

