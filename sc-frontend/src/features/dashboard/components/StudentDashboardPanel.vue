<template>
  <section :class="{'student-live-panel--list': studentOngoingSessions.length > 0}" class="student-live-panel">
    <div class="student-live-content">
      <span class="dashboard-status-chip live">
        {{
          studentOngoingSessions.length > 0 ? t('success.dashboard.status.inProgress') : t('success.dashboard.status.continueLearning')
        }}
      </span>
      <h2>{{
          studentOngoingSessions.length > 0 ? t('success.dashboard.copy.studentOngoingTitle') : student.continueCourse?.courseTitle || t('success.dashboard.copy.studentLiveTitle')
        }}</h2>
      <p>
        {{
          studentOngoingSessions.length > 0 ? t('success.dashboard.copy.studentOngoingSubtitle', {count: studentOngoingSessions.length}) : studentContinueText
        }}
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
            <span class="dashboard-status-chip live">{{
                session.statusText || t('success.dashboard.status.inProgress')
              }}</span>
          </span>
        </RouterLink>
      </div>
    </div>
    <RouterLink
        v-if="studentOngoingSessions.length === 0"
        :to="studentContinueRoute"
        class="dashboard-command-button primary"
    >
      <DoorOpen :size="18" stroke-width="1.8"/>
      <span>{{ t('success.dashboard.actions.continueLearning') }}</span>
    </RouterLink>
  </section>

  <section :aria-label="t('success.dashboard.aria.studentMetrics')" class="metric-grid metric-grid--five">
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
            :action-label="t('success.dashboard.actions.notifications')"
            :title="t('success.dashboard.panels.todayTodos')"
            to="/notifications"
        />
        <DashboardTimelineRows
            :empty-text="t('success.dashboard.empty.todos')"
            :items="student.todos"
        />
      </article>

      <article class="workbench-panel student-panel--progress">
        <DashboardPanelHeader
            :action-label="t('success.dashboard.actions.myEnrollments')"
            :title="t('success.dashboard.panels.learningProgress')"
            to="/my-enrollments"
        />
        <div class="chart-pair chart-pair--wide">
          <DashboardChart
              :aria-label="t('success.dashboard.chart.studentProgressAria')"
              :empty-text="t('success.dashboard.empty.studentProgress')"
              :has-data="studentHasEnrollments"
              :option="studentProgressDonutOption"
              height="250px"
          />
          <DashboardChart
              :aria-label="t('success.dashboard.chart.studentProgressRankAria')"
              :empty-text="t('success.dashboard.empty.studentProgressRank')"
              :has-data="studentHasEnrollments"
              :option="studentProgressRankOption"
              height="250px"
          />
        </div>
      </article>

      <article class="workbench-panel student-panel--course-progress">
        <DashboardPanelHeader
            :action-label="t('success.dashboard.actions.myEnrollments')"
            :title="t('success.dashboard.panels.myCourseProgress')"
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
              <span>{{
                  t('success.dashboard.labels.publishedSessionCount', {count: enrollment.coursePublishedClassSessionCount})
                }}</span>
            </div>
            <div class="dashboard-progress-track">
              <span :style="{width: `${clampPercent(enrollment.courseProgress)}%`}"></span>
            </div>
            <small>{{ clampPercent(enrollment.courseProgress) }}%</small>
          </RouterLink>
          <DashboardEmptyState v-if="student.enrollments.length === 0"
                               :text="t('success.dashboard.empty.enrollments')"/>
        </div>
      </article>

      <article class="workbench-panel student-panel--practice-performance">
        <DashboardPanelHeader :title="t('success.dashboard.panels.practicePerformance')"/>
        <DashboardChart
            :aria-label="t('success.dashboard.chart.studentPracticeAria')"
            :empty-text="t('success.dashboard.empty.practiceHistory')"
            :has-data="studentHasPractice"
            :option="studentPracticeOption"
            height="236px"
        />
        <div class="insight-inline">
          <div v-for="metric in studentPracticeSummary" :key="metric.label" class="insight-pill">
            <span>{{ metric.label }}</span>
            <strong>{{ metric.value }}</strong>
          </div>
        </div>
      </article>

      <article class="workbench-panel student-panel--recent-learning">
        <DashboardPanelHeader :title="t('success.dashboard.panels.recentLearning')"/>
        <div class="recent-course-list">
          <RouterLink v-for="course in recentCourses" :key="course.id" :to="`/courses/${course.id}/overview`"
                      class="recent-course-row">
            <strong>{{ course.title }}</strong>
            <span>{{ course.teacherName || t('success.dashboard.labels.teacherUnset') }}</span>
            <small>{{ formatDashboardRelativeTime(course.visitedAt, String(locale)) }}</small>
          </RouterLink>
          <DashboardEmptyState v-if="recentCourses.length === 0" :text="t('success.dashboard.empty.recentCourses')"/>
        </div>
      </article>
    </div>

    <div class="dashboard-side-column">
      <article class="workbench-panel student-panel--assistant">
        <DashboardPanelHeader :title="t('success.dashboard.panels.learningAssistant')"/>
        <div class="ai-command-grid">
          <template
              v-for="action in studentAiActions"
              :key="action.label"
          >
            <button
                v-if="action.kind === 'ai'"
                class="ai-command"
                type="button"
                @click="handleAiAction(action)"
            >
              <strong>{{ action.label }}</strong>
              <small>{{ action.description }}</small>
            </button>
            <button
                v-else
                class="ai-command"
                type="button"
                @click="showTianshuNotice(action)"
            >
              <strong>{{ action.label }}</strong>
              <small>{{ action.description }}</small>
            </button>
          </template>
        </div>
      </article>

      <article class="workbench-panel student-panel--session-timeline">
        <DashboardPanelHeader :title="t('success.dashboard.panels.recentClassTimeline')"/>
        <DashboardChart
            :aria-label="t('success.dashboard.chart.studentSessionAria')"
            :empty-text="t('success.dashboard.empty.recentSessions')"
            :has-data="studentHasSessions"
            :option="studentSessionOption"
            height="250px"
        />
      </article>

      <article class="workbench-panel student-panel--practice-bank">
        <DashboardPanelHeader :title="t('success.dashboard.panels.practiceBank')"/>
        <div class="practice-focus">
          <Target :size="24" stroke-width="1.8"/>
          <strong>{{ student.practiceFocus.title }}</strong>
          <p>{{ student.practiceFocus.detail }}</p>
          <RouterLink :to="student.practiceFocus.to" class="dashboard-command-button">
            {{ t('success.dashboard.actions.startPractice') }}
          </RouterLink>
        </div>
      </article>

      <article class="workbench-panel student-panel--recommendations">
        <DashboardPanelHeader
            :action-label="t('success.dashboard.actions.discoverCourses')"
            :title="t('success.dashboard.panels.publicRecommendations')"
            to="/courses"
        />
        <div class="recommend-list">
          <RouterLink v-for="course in student.recommendations" :key="course.id" :to="`/courses/${course.id}/overview`"
                      class="recommend-row">
            <strong>{{ course.title }}</strong>
            <span>{{ course.teacherName || t('success.dashboard.labels.sapientiaTeacher') }}</span>
          </RouterLink>
          <DashboardEmptyState v-if="student.recommendations.length === 0"
                               :text="t('success.dashboard.empty.recommendations')"/>
        </div>
      </article>
    </div>
  </section>
</template>

<script lang="ts" setup>
import {DoorOpen, Target,} from 'lucide-vue-next'
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import {type RouteLocationRaw, RouterLink} from 'vue-router'

import {useAiModeNavigation} from '@/features/ai/composables/useAiModeNavigation'
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
  type Metric,
  progressBuckets,
  shortLabel,
} from '@/features/dashboard/utils/dashboardFormatters'
import {notify} from '@/shared/composables/useGlobalNotification'
import {useRecentCourses} from '@/shared/composables/useRecentCourses'

const props = defineProps<{
  student: StudentDashboard
}>()

const {t, locale} = useI18n()
const {recentCourses} = useRecentCourses()
const enterAiMode = useAiModeNavigation()

type StudentAiAction =
    | { kind: 'ai'; label: string; description: string; to: RouteLocationRaw }
    | { kind: 'notice'; label: string; description: string; notice: string }

const TIANSHU_NOTICE_DURATION = 6000

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
    kind: 'ai',
    label: t('success.dashboard.actions.tianshuQuestionGeneration'),
    description: t('success.dashboard.actions.tianshuQuestionGenerationHint'),
    to: {name: 'ai-workspace', query: {mode: 'QUESTION'}},
  },
  {
    kind: 'ai',
    label: t('success.dashboard.actions.tianshuPaperGeneration'),
    description: t('success.dashboard.actions.tianshuPaperGenerationHint'),
    to: {name: 'ai-workspace', query: {mode: 'PAPER'}},
  },
  {
    kind: 'notice',
    label: t('success.dashboard.actions.tianshuGrading'),
    description: t('success.dashboard.actions.tianshuStudentGradingHint'),
    notice: t('success.dashboard.actions.tianshuStudentGradingNotice'),
  },
  {
    kind: 'notice',
    label: t('success.dashboard.actions.tianshuClassMinutes'),
    description: t('success.dashboard.actions.tianshuClassMinutesHint'),
    notice: t('success.dashboard.actions.tianshuClassMinutesNotice'),
  },
])

function handleAiAction(action: StudentAiAction) {
  if (action.kind !== 'ai') return
  void enterAiMode(action.to)
}

function showTianshuNotice(action: StudentAiAction) {
  if (action.kind !== 'notice') return
  notify.info(action.notice, {duration: TIANSHU_NOTICE_DURATION})
}
</script>

