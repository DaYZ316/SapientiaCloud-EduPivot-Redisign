<template>
  <section class="student-live-panel" :class="{'student-live-panel--list': studentOngoingSessions.length > 0}">
    <div class="student-live-content">
      <span class="dashboard-status-chip live">
        {{ studentOngoingSessions.length > 0 ? t('success.dashboard.status.inProgress') : t('success.dashboard.status.continueLearning') }}
      </span>
      <h2>{{ studentOngoingSessions.length > 0 ? t('success.dashboard.copy.studentOngoingTitle') : student.continueCourse?.courseTitle || t('success.dashboard.copy.studentLiveTitle') }}</h2>
      <p>
        {{ studentOngoingSessions.length > 0 ? t('success.dashboard.copy.studentOngoingSubtitle', {count: studentOngoingSessions.length}) : studentContinueText }}
      </p>
      <div v-if="studentOngoingSessions.length > 0" class="student-ongoing-list">
        <RouterLink
          v-for="session in studentOngoingSessions"
          :key="session.id"
          :to="`/class-sessions/${session.id}`"
          class="session-row student-ongoing-row"
        >
          <div>
            <strong>{{ session.title }}</strong>
            <span>{{ formatSessionRange(session, String(locale)) }}</span>
          </div>
          <span class="student-ongoing-meta">
            <span class="dashboard-status-chip live">{{ session.statusText || t('success.dashboard.status.inProgress') }}</span>
          </span>
        </RouterLink>
      </div>
    </div>
    <RouterLink
      v-if="studentOngoingSessions.length === 0"
      class="dashboard-command-button primary"
      :to="studentContinueRoute"
    >
      <DoorOpen :size="18" stroke-width="1.8" />
      <span>{{ t('success.dashboard.actions.continueLearning') }}</span>
    </RouterLink>
  </section>

  <section class="metric-grid metric-grid--five" :aria-label="t('success.dashboard.aria.studentMetrics')">
    <article v-for="metric in studentMetrics" :key="metric.label" class="dashboard-metric-cell">
      <span>{{ metric.label }}</span>
      <strong>{{ metric.value }}</strong>
      <small>{{ metric.meta }}</small>
    </article>
  </section>

  <section class="student-grid">
    <div class="dashboard-main-column">
      <article class="workbench-panel student-panel--todos">
        <DashboardPanelHeader
          :title="t('success.dashboard.panels.todayTodos')"
          :action-label="t('success.dashboard.actions.notifications')"
          to="/notifications"
        />
        <DashboardTimelineRows
          :items="student.todos"
          :empty-text="t('success.dashboard.empty.todos')"
        />
      </article>

      <article class="workbench-panel student-panel--progress">
        <DashboardPanelHeader
          :title="t('success.dashboard.panels.learningProgress')"
          :action-label="t('success.dashboard.actions.myEnrollments')"
          to="/my-enrollments"
        />
        <div class="chart-pair chart-pair--wide">
          <DashboardChart
            :option="studentProgressDonutOption"
            :has-data="studentHasEnrollments"
            height="250px"
            :aria-label="t('success.dashboard.chart.studentProgressAria')"
            :empty-text="t('success.dashboard.empty.studentProgress')"
          />
          <DashboardChart
            :option="studentProgressRankOption"
            :has-data="studentHasEnrollments"
            height="250px"
            :aria-label="t('success.dashboard.chart.studentProgressRankAria')"
            :empty-text="t('success.dashboard.empty.studentProgressRank')"
          />
        </div>
      </article>

      <article class="workbench-panel student-panel--course-progress">
        <DashboardPanelHeader
          :title="t('success.dashboard.panels.myCourseProgress')"
          :action-label="t('success.dashboard.actions.myEnrollments')"
          to="/my-enrollments"
        />
        <div class="course-progress-list">
          <RouterLink
            v-for="enrollment in student.enrollments"
            :key="enrollment.id"
            :to="`/courses/${enrollment.courseId}/overview`"
            class="progress-row"
          >
            <div>
              <strong>{{ enrollment.courseTitle || t('success.dashboard.labels.untitledCourse') }}</strong>
              <span>{{ t('success.dashboard.labels.publishedSessionCount', {count: enrollment.coursePublishedClassSessionCount}) }}</span>
            </div>
            <div class="dashboard-progress-track">
              <span :style="{width: `${clampPercent(enrollment.courseProgress)}%`}"></span>
            </div>
            <small>{{ clampPercent(enrollment.courseProgress) }}%</small>
          </RouterLink>
          <DashboardEmptyState v-if="student.enrollments.length === 0" :text="t('success.dashboard.empty.enrollments')" />
        </div>
      </article>

      <article class="workbench-panel student-panel--practice-performance">
        <DashboardPanelHeader :title="t('success.dashboard.panels.practicePerformance')" />
        <DashboardChart
          :option="studentPracticeOption"
          :has-data="studentHasPractice"
          height="236px"
          :aria-label="t('success.dashboard.chart.studentPracticeAria')"
          :empty-text="t('success.dashboard.empty.practiceHistory')"
        />
        <div class="insight-inline">
          <div v-for="metric in studentPracticeSummary" :key="metric.label" class="insight-pill">
            <span>{{ metric.label }}</span>
            <strong>{{ metric.value }}</strong>
          </div>
        </div>
      </article>

      <article class="workbench-panel student-panel--recent-learning">
        <DashboardPanelHeader :title="t('success.dashboard.panels.recentLearning')" />
        <div class="recent-course-list">
          <RouterLink v-for="course in recentCourses" :key="course.id" :to="`/courses/${course.id}/overview`" class="recent-course-row">
            <strong>{{ course.title }}</strong>
            <span>{{ course.teacherName || t('success.dashboard.labels.teacherUnset') }}</span>
            <small>{{ formatDashboardRelativeTime(course.visitedAt, String(locale)) }}</small>
          </RouterLink>
          <DashboardEmptyState v-if="recentCourses.length === 0" :text="t('success.dashboard.empty.recentCourses')" />
        </div>
      </article>
    </div>

    <div class="dashboard-side-column">
      <article class="workbench-panel student-panel--assistant">
        <DashboardPanelHeader :title="t('success.dashboard.panels.learningAssistant')" />
        <div class="ai-command-grid">
          <template
            v-for="action in studentAiActions"
            :key="action.label"
          >
            <RouterLink
              v-if="action.kind === 'link'"
              :to="action.to"
              class="ai-command"
            >
              <strong>{{ action.label }}</strong>
              <small>{{ action.description }}</small>
            </RouterLink>
            <button
              v-else
              class="ai-command"
              type="button"
              @click="showTianshuPending(action.label)"
            >
              <strong>{{ action.label }}</strong>
              <small>{{ action.description }}</small>
            </button>
          </template>
        </div>
      </article>

      <article class="workbench-panel student-panel--session-timeline">
        <DashboardPanelHeader :title="t('success.dashboard.panels.recentClassTimeline')" />
        <DashboardChart
          :option="studentSessionOption"
          :has-data="studentHasSessions"
          height="250px"
          :aria-label="t('success.dashboard.chart.studentSessionAria')"
          :empty-text="t('success.dashboard.empty.recentSessions')"
        />
      </article>

      <article class="workbench-panel student-panel--practice-bank">
        <DashboardPanelHeader :title="t('success.dashboard.panels.practiceBank')" />
        <div class="practice-focus">
          <Target :size="24" stroke-width="1.8" />
          <strong>{{ student.practiceFocus.title }}</strong>
          <p>{{ student.practiceFocus.detail }}</p>
          <RouterLink class="dashboard-command-button" :to="student.practiceFocus.to">
            {{ t('success.dashboard.actions.startPractice') }}
          </RouterLink>
        </div>
      </article>

      <article class="workbench-panel student-panel--recommendations">
        <DashboardPanelHeader
          :title="t('success.dashboard.panels.publicRecommendations')"
          :action-label="t('success.dashboard.actions.discoverCourses')"
          to="/courses"
        />
        <div class="recommend-list">
          <RouterLink v-for="course in student.recommendations" :key="course.id" :to="`/courses/${course.id}/overview`" class="recommend-row">
            <strong>{{ course.title }}</strong>
            <span>{{ course.teacherName || t('success.dashboard.labels.sapientiaTeacher') }}</span>
          </RouterLink>
          <DashboardEmptyState v-if="student.recommendations.length === 0" :text="t('success.dashboard.empty.recommendations')" />
        </div>
      </article>
    </div>
  </section>
</template>

<script lang="ts" setup>
import {
    DoorOpen,
    Target,
} from 'lucide-vue-next'
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import {RouterLink} from 'vue-router'

import type {StudentDashboard} from '@/features/dashboard/api/dashboard'
import DashboardChart from '@/features/dashboard/components/DashboardChart.vue'
import DashboardEmptyState from '@/features/dashboard/components/DashboardEmptyState.vue'
import DashboardPanelHeader from '@/features/dashboard/components/DashboardPanelHeader.vue'
import DashboardTimelineRows from '@/features/dashboard/components/DashboardTimelineRows.vue'
import {barOption, donutOption, lineOption} from '@/features/dashboard/utils/dashboardChartOptions'
import {
    accuracyPercent,
    clampPercent,
    formatDashboardMonthDay,
    formatDashboardNumber,
    formatDashboardRelativeTime,
    formatSessionRange,
    progressBuckets,
    shortLabel,
    type Metric,
} from '@/features/dashboard/utils/dashboardFormatters'
import {notify} from '@/shared/composables/useGlobalNotification'
import {useRecentCourses} from '@/shared/composables/useRecentCourses'

const props = defineProps<{
    student: StudentDashboard
}>()

const {t, locale} = useI18n()
const {recentCourses} = useRecentCourses()

type StudentAiAction =
    | { kind: 'link'; label: string; description: string; to: string }
    | { kind: 'pending'; label: string; description: string }

const studentMetrics = computed<Metric[]>(() => {
    const enrollments = props.student.enrollments
    const completed = enrollments.filter((item) => item.status === 2).length
    const inProgress = enrollments.filter((item) => item.status === 1).length
    const avgProgress = enrollments.length
        ? Math.round(enrollments.reduce((sum, item) => sum + clampPercent(item.courseProgress), 0) / enrollments.length)
        : 0

    return [
        {
            label: t('success.dashboard.metrics.student.enrollments.label'),
            value: formatDashboardNumber(enrollments.length, String(locale.value)),
            meta: t('success.dashboard.metrics.student.enrollments.meta'),
        },
        {
            label: t('success.dashboard.metrics.student.inProgress.label'),
            value: formatDashboardNumber(inProgress, String(locale.value)),
            meta: t('success.dashboard.metrics.student.inProgress.meta'),
        },
        {
            label: t('success.dashboard.metrics.student.completed.label'),
            value: formatDashboardNumber(completed, String(locale.value)),
            meta: t('success.dashboard.metrics.student.completed.meta'),
        },
        {
            label: t('success.dashboard.metrics.student.avgProgress.label'),
            value: `${avgProgress}%`,
            meta: t('success.dashboard.metrics.student.avgProgress.meta'),
        },
        {
            label: t('success.dashboard.metrics.student.unread.label'),
            value: formatDashboardNumber(props.student.notifications.unreadTotal, String(locale.value)),
            meta: t('success.dashboard.metrics.student.unread.meta', {count: props.student.notifications.unreadTeaching}),
        },
    ]
})

const studentContinueRoute = computed(() => {
    const course = props.student.continueCourse
    return course ? `/courses/${course.courseId}/overview` : '/courses'
})

const studentContinueText = computed(() => {
    const course = props.student.continueCourse
    if (!course) return t('success.dashboard.copy.studentContinueFallback')
    return t('success.dashboard.copy.studentContinueCourse', {
        course: course.courseTitle || t('success.dashboard.labels.course'),
        progress: clampPercent(course.courseProgress),
    })
})

const studentOngoingSessions = computed(() => (props.student.ongoingSessions?.length ?? 0) > 0
    ? props.student.ongoingSessions
    : props.student.liveSession ? [props.student.liveSession] : [])

const studentProgressDonutOption = computed(() => donutOption(
    progressBuckets(props.student.enrollments.map((item) => item.courseProgress)),
    t('success.dashboard.chart.series.learningProgress'),
))

const studentProgressRankOption = computed(() => barOption(
    props.student.enrollments.map((item) => shortLabel(item.courseTitle || t('success.dashboard.labels.untitledCourse'))),
    props.student.enrollments.map((item) => clampPercent(item.courseProgress)),
    t('success.dashboard.chart.series.courseProgress'),
    '%',
))

const studentSessionOption = computed(() => lineOption(
    props.student.sessions.map((session) => formatDashboardMonthDay(session.scheduledStartAt, String(locale.value))),
    props.student.sessions.map((_, index) => index + 1),
    t('success.dashboard.chart.series.recentSessions'),
))

const studentPracticeOption = computed(() => lineOption(
    props.student.practiceSessions.slice(-6).map((session) => formatDashboardMonthDay(session.startedAt, String(locale.value))),
    props.student.practiceSessions.slice(-6).map((session) => accuracyPercent(session)),
    t('success.dashboard.chart.series.practiceAccuracy'),
    '%',
))

const studentPracticeSummary = computed<Metric[]>(() => {
    const sessions = props.student.practiceSessions
    const completed = sessions.filter((item) => item.status === 1).length
    const avgAccuracy = sessions.length
        ? Math.round(sessions.reduce((sum, item) => sum + accuracyPercent(item), 0) / sessions.length)
        : 0

    return [
        {
            label: t('success.dashboard.metrics.practice.sessions.label'),
            value: formatDashboardNumber(sessions.length, String(locale.value)),
            meta: t('success.dashboard.metrics.practice.sessions.meta'),
        },
        {
            label: t('success.dashboard.metrics.practice.completed.label'),
            value: formatDashboardNumber(completed, String(locale.value)),
            meta: t('success.dashboard.metrics.practice.completed.meta'),
        },
        {
            label: t('success.dashboard.metrics.practice.avgAccuracy.label'),
            value: `${avgAccuracy}%`,
            meta: t('success.dashboard.metrics.practice.avgAccuracy.meta'),
        },
    ]
})

const studentHasEnrollments = computed(() => props.student.enrollments.length > 0)
const studentHasSessions = computed(() => props.student.sessions.length > 0)
const studentHasPractice = computed(() => props.student.practiceSessions.length > 0)

const studentAiActions = computed<StudentAiAction[]>(() => [
    {
        kind: 'link',
        label: t('success.dashboard.actions.tianshuQuestionGeneration'),
        description: t('success.dashboard.actions.tianshuQuestionGenerationHint'),
        to: '/ai?mode=QUESTION',
    },
    {
        kind: 'link',
        label: t('success.dashboard.actions.tianshuPaperGeneration'),
        description: t('success.dashboard.actions.tianshuPaperGenerationHint'),
        to: '/ai?mode=PAPER',
    },
    {
        kind: 'link',
        label: t('success.dashboard.actions.tianshuGrading'),
        description: t('success.dashboard.actions.tianshuGradingHint'),
        to: '/ai',
    },
    {
        kind: 'pending',
        label: t('success.dashboard.actions.tianshuClassMinutes'),
        description: t('success.dashboard.actions.tianshuClassMinutesHint'),
    },
])

function showTianshuPending(name: string) {
    notify.info(t('success.dashboard.actions.tianshuFeaturePending', {name}))
}
</script>

