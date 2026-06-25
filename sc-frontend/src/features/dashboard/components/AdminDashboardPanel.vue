<template>
  <section v-if="risks.length" class="risk-strip">
    <article v-for="alert in risks" :key="alert.title" class="risk-row">
      <component :is="alert.icon" :size="18" stroke-width="1.8" />
      <div>
        <strong>{{ alert.title }}</strong>
        <span>{{ alert.detail }}</span>
      </div>
      <span class="dashboard-status-chip" :class="alert.level">{{ alert.status }}</span>
    </article>
  </section>

  <section class="metric-grid metric-grid--seven" :aria-label="t('success.dashboard.aria.adminMetrics')">
    <article v-for="metric in adminMetrics" :key="metric.label" class="dashboard-metric-cell">
      <span>{{ metric.label }}</span>
      <strong>{{ metric.value }}</strong>
      <small>{{ metric.meta }}</small>
    </article>
  </section>

  <section class="admin-grid">
    <div class="dashboard-main-column">
      <article class="workbench-panel admin-panel--account">
        <DashboardPanelHeader
          :title="t('success.dashboard.panels.accountStatus')"
          :action-label="t('success.dashboard.actions.userManagement')"
          to="/admin/users"
        />
        <div class="chart-summary-layout">
          <DashboardChart
            :option="adminUserRoleOption"
            :has-data="adminHasUsers"
            height="248px"
            :aria-label="t('success.dashboard.chart.userRoleAria')"
            :empty-text="t('success.dashboard.empty.userRoleData')"
          />
          <div class="summary-stack">
            <div class="ratio-line">
              <span>{{ t('success.dashboard.labels.oauthAccounts') }}</span>
              <strong>{{ users.oauthPercent }}%</strong>
            </div>
            <div class="dashboard-progress-track">
              <span :style="{width: `${users.oauthPercent}%`}"></span>
            </div>
            <div class="ratio-line muted">
              <span>{{ t('success.dashboard.labels.localAccounts') }}</span>
              <strong>{{ 100 - users.oauthPercent }}%</strong>
            </div>
            <div class="account-flags">
              <span>{{ t('success.dashboard.labels.incompleteProfiles', {count: users.incompleteProfiles}) }}</span>
              <span>{{ t('success.dashboard.labels.disabledUsers', {count: users.disabledUsers}) }}</span>
            </div>
            <div class="compact-list subtle-list">
              <div v-for="login in loginActivity" :key="login.id" class="compact-row">
                <span>{{ login.name }}</span>
                <small>{{ login.time }}</small>
              </div>
            </div>
          </div>
        </div>
      </article>

      <article class="workbench-panel admin-panel--course-notifications">
        <DashboardPanelHeader
          :title="t('success.dashboard.panels.courseNotifications')"
          :action-label="t('success.dashboard.actions.courseManagement')"
          to="/course-management"
        />
        <div class="chart-pair">
          <DashboardChart
            :option="adminCourseStatusOption"
            :has-data="adminHasCourseStatus"
            height="230px"
            :aria-label="t('success.dashboard.chart.courseStatusAria')"
            :empty-text="t('success.dashboard.empty.courseStatusData')"
          />
          <DashboardChart
            :option="adminNotificationOption"
            :has-data="adminHasNotifications"
            height="230px"
            :aria-label="t('success.dashboard.chart.notificationsAria')"
            :empty-text="t('success.dashboard.empty.notificationData')"
          />
        </div>
        <div class="resource-summary">
          <div v-for="item in admin.resources" :key="item.label" class="summary-chip">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
          </div>
        </div>
      </article>

      <article class="workbench-panel admin-panel--recent-activity">
        <DashboardPanelHeader :title="t('success.dashboard.panels.recentActivity')" />
        <DashboardTimelineRows
          :items="admin.activities"
          :empty-text="t('success.dashboard.empty.timeline')"
        />
      </article>
    </div>

    <div class="dashboard-side-column">
      <article class="workbench-panel admin-panel--shortcuts">
        <DashboardPanelHeader :title="t('success.dashboard.panels.adminShortcuts')" />
        <div class="action-grid">
          <RouterLink v-for="action in adminActions" :key="action.label" :to="action.to" class="action-tile">
            <component :is="action.icon" :size="18" stroke-width="1.8" />
            <span>{{ action.label }}</span>
            <ArrowRight :size="16" stroke-width="1.8" />
          </RouterLink>
        </div>
      </article>

      <article class="workbench-panel admin-panel--course-capacity">
        <DashboardPanelHeader
          :title="t('success.dashboard.panels.courseCapacity')"
          :action-label="t('success.dashboard.actions.courseManagement')"
          to="/course-management"
        />
        <div class="capacity-list">
          <RouterLink
            v-for="course in admin.courseCapacity"
            :key="course.id"
            :to="`/courses/${course.id}/overview`"
            class="capacity-row"
          >
            <div>
              <strong>{{ course.title }}</strong>
              <span>{{ course.detail }}</span>
            </div>
            <strong>{{ course.value }}%</strong>
          </RouterLink>
          <DashboardEmptyState v-if="admin.courseCapacity.length === 0" :text="t('success.dashboard.empty.courseCapacity')" />
        </div>
      </article>

      <article class="workbench-panel admin-panel--notification-center">
        <DashboardPanelHeader
          :title="t('success.dashboard.panels.notificationCenter')"
          :action-label="t('success.dashboard.actions.viewAll')"
          to="/notifications"
        />
        <DashboardNotificationRows :notifications="admin.notifications.recent" />
      </article>
    </div>
  </section>
</template>

<script lang="ts" setup>
import {
    AlertTriangle,
    ArrowRight,
    BookOpen,
    Bot,
    GraduationCap,
    Megaphone,
    ShieldCheck,
    UserCog,
    Users,
} from 'lucide-vue-next'
import type {Component} from 'vue'
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import {RouterLink} from 'vue-router'

import type {AdminDashboard} from '@/features/dashboard/api/dashboard'
import DashboardChart from '@/features/dashboard/components/DashboardChart.vue'
import DashboardEmptyState from '@/features/dashboard/components/DashboardEmptyState.vue'
import DashboardNotificationRows from '@/features/dashboard/components/DashboardNotificationRows.vue'
import DashboardPanelHeader from '@/features/dashboard/components/DashboardPanelHeader.vue'
import DashboardTimelineRows from '@/features/dashboard/components/DashboardTimelineRows.vue'
import {barOption, donutOption} from '@/features/dashboard/utils/dashboardChartOptions'
import {
    formatDashboardDateTime,
    formatDashboardNumber,
    normalizeDashboardLevel,
    toChartData,
    type ActionLink,
    type Metric,
} from '@/features/dashboard/utils/dashboardFormatters'

const props = defineProps<{
    admin: AdminDashboard
}>()

const {t, locale} = useI18n()

const users = computed(() => props.admin.users)

const loginActivity = computed(() => users.value.recentUsers.slice(0, 5).map((user) => ({
    id: user.id,
    name: user.name || user.email || 'User',
    time: formatDashboardDateTime(user.lastLoginAt || user.createdAt, String(locale.value)),
})))

const risks = computed(() => props.admin.risks.map((item) => ({
    title: item.title,
    detail: item.detail,
    status: item.status,
    level: normalizeDashboardLevel(item.level),
    icon: resolveRiskIcon(item.icon),
})))

const adminMetrics = computed<Metric[]>(() => [
    {
        label: t('success.dashboard.metrics.admin.totalUsers.label'),
        value: formatDashboardNumber(users.value.totalUsers, String(locale.value)),
        meta: t('success.dashboard.metrics.admin.totalUsers.meta', {count: users.value.todayUsers}),
    },
    {
        label: t('success.dashboard.metrics.admin.students.label'),
        value: formatDashboardNumber(users.value.students, String(locale.value)),
        meta: t('success.dashboard.metrics.admin.students.meta'),
    },
    {
        label: t('success.dashboard.metrics.admin.teachers.label'),
        value: formatDashboardNumber(users.value.teachers, String(locale.value)),
        meta: t('success.dashboard.metrics.admin.teachers.meta'),
    },
    {
        label: t('success.dashboard.metrics.admin.courses.label'),
        value: formatDashboardNumber(props.admin.courses ?? 0, String(locale.value)),
        meta: t('success.dashboard.metrics.admin.courses.meta'),
    },
    {
        label: t('success.dashboard.metrics.admin.publishedCourses.label'),
        value: formatDashboardNumber(props.admin.publishedCourses ?? 0, String(locale.value)),
        meta: t('success.dashboard.metrics.admin.publishedCourses.meta'),
    },
    {
        label: t('success.dashboard.metrics.admin.disabledUsers.label'),
        value: formatDashboardNumber(users.value.disabledUsers, String(locale.value)),
        meta: t('success.dashboard.metrics.admin.disabledUsers.meta'),
    },
    {
        label: t('success.dashboard.metrics.admin.unread.label'),
        value: formatDashboardNumber(props.admin.notifications.unreadTotal, String(locale.value)),
        meta: t('success.dashboard.metrics.admin.unread.meta', {count: props.admin.notifications.unreadSystem}),
    },
])

const adminUserRoleOption = computed(() => donutOption([
    {label: t('success.dashboard.labels.students'), value: users.value.students},
    {label: t('success.dashboard.labels.teachers'), value: users.value.teachers},
    {
        label: t('success.dashboard.labels.adminsOther'),
        value: Math.max(0, users.value.totalUsers - users.value.students - users.value.teachers),
    },
], t('success.dashboard.chart.series.accountStructure')))

const adminCourseStatusOption = computed(() => barOption(
    toChartData(props.admin.courseStatus).map((item) => item.label),
    toChartData(props.admin.courseStatus).map((item) => item.value),
    t('success.dashboard.chart.series.courseCount'),
))

const adminNotificationOption = computed(() => donutOption([
    {label: t('success.dashboard.labels.systemUnread'), value: props.admin.notifications.unreadSystem},
    {label: t('success.dashboard.labels.teachingUnread'), value: props.admin.notifications.unreadTeaching},
    {label: t('success.dashboard.labels.recentRead'), value: props.admin.notifications.recent.filter((item) => item.read).length},
], t('success.dashboard.chart.series.notificationStructure')))

const adminHasUsers = computed(() => users.value.totalUsers > 0)
const adminHasCourseStatus = computed(() => props.admin.courseStatus.some((item) => item.value > 0))
const adminHasNotifications = computed(() => props.admin.notifications.unreadTotal > 0 || props.admin.notifications.recent.length > 0)

const adminActions = computed<ActionLink[]>(() => [
    {label: t('success.dashboard.actions.userManagement'), to: '/admin/users', icon: UserCog},
    {label: t('success.dashboard.actions.studentList'), to: '/admin/students', icon: GraduationCap},
    {label: t('success.dashboard.actions.teacherList'), to: '/admin/teachers', icon: Users},
    {label: t('success.dashboard.actions.courseManagement'), to: '/course-management', icon: BookOpen},
    {label: t('success.dashboard.actions.publishNotification'), to: '/notifications', icon: Megaphone},
    {label: t('success.dashboard.actions.aiWorkspace'), to: '/ai', icon: Bot},
])

function resolveRiskIcon(icon?: string | null): Component {
    if (icon === 'AlertTriangle') return AlertTriangle
    if (icon === 'Users') return Users
    return ShieldCheck
}
</script>

