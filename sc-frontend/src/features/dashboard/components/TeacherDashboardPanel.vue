<template>
  <section class="teacher-live-panel">
    <div>
      <span class="dashboard-status-chip live">
        {{
          teacher.liveSession ? t('success.dashboard.status.teachingLive') : t('success.dashboard.status.todayTeaching')
        }}
      </span>
      <h2>{{ teacher.liveSession?.title || t('success.dashboard.empty.noLiveSession') }}</h2>
      <p>
        {{
          teacher.liveSession ? formatSessionRange(teacher.liveSession, String(locale)) : t('success.dashboard.copy.teacherLiveFallback')
        }}
      </p>
    </div>
    <RouterLink
        :to="teacher.liveSession ? `/class-sessions/${teacher.liveSession.id}` : '/teacher/courses?role=primary'"
        class="dashboard-command-button primary"
    >
      <MonitorUp :size="18" stroke-width="1.8"/>
      <span>{{
          teacher.liveSession ? t('success.dashboard.actions.enterClassroom3d') : t('success.dashboard.actions.maintainCourseContent')
        }}</span>
    </RouterLink>
  </section>

  <section :aria-label="t('success.dashboard.aria.teacherMetrics')"
           class="metric-grid metric-grid--six teacher-metric-grid">
    <article v-for="metric in teacherMetrics" :key="metric.label" class="dashboard-metric-cell">
      <span>{{ metric.label }}</span>
      <strong>{{ metric.value }}</strong>
      <small>{{ metric.meta }}</small>
    </article>
  </section>

  <section class="teacher-grid">
    <div class="teacher-main-column">
      <article class="workbench-panel teacher-panel--today">
        <DashboardPanelHeader
            :action-label="t('success.dashboard.actions.classSessions')"
            :title="t('success.dashboard.panels.todayTeaching')"
            to="/teacher/courses?role=primary"
        />
        <div class="session-list">
          <RouterLink
              v-for="session in teacher.sessions"
              :key="session.id"
              :to="`/class-sessions/${session.id}`"
              class="session-row"
          >
            <div>
              <strong>{{ session.title }}</strong>
              <span>{{ formatSessionRange(session, String(locale)) }}</span>
            </div>
            <span :class="{live: session.status === ClassSessionStatus.LIVE}" class="dashboard-status-chip">
              {{ session.statusText || session.liveStatusText }}
            </span>
          </RouterLink>
          <DashboardEmptyState v-if="teacher.sessions.length === 0" :text="t('success.dashboard.empty.todaySessions')"/>
        </div>
      </article>

      <article class="workbench-panel teacher-panel--courses">
        <DashboardPanelHeader
            :action-label="t('success.dashboard.actions.allCourses')"
            :title="t('success.dashboard.panels.courseOps')"
            to="/teacher/courses?role=primary"
        />
        <div class="course-ops-grid">
          <RouterLink v-for="course in teacher.courses" :key="course.id" :to="`/courses/${course.id}/overview`"
                      class="course-op">
            <div class="course-op-main">
              <strong>{{ course.title }}</strong>
              <span class="course-op-percent">{{ clampPercent(course.courseProgress) }}%</span>
            </div>
            <div class="dashboard-progress-track">
              <span :style="{width: `${clampPercent(course.courseProgress)}%`}"></span>
            </div>
            <div class="course-op-stats">
              <span class="course-op-stat">
                <span class="course-op-stat-value">{{ course.currentStudents }} / {{ course.maxStudents }}</span>
                <span class="course-op-stat-label">{{ t('success.dashboard.labels.studentCapacity') }}</span>
              </span>
              <span class="course-op-stat">
                <span class="course-op-stat-value">{{ course.publishedClassSessionCount }}</span>
                <span class="course-op-stat-label">{{ t('success.dashboard.labels.publishedSessions') }}</span>
              </span>
            </div>
          </RouterLink>
          <DashboardEmptyState v-if="teacher.courses.length === 0" :text="t('success.dashboard.empty.primaryCourses')"/>
        </div>
      </article>

      <article class="workbench-panel teacher-panel--learning">
        <DashboardPanelHeader :title="t('success.dashboard.panels.learningOps')"/>
        <div class="chart-pair">
          <DashboardChart
              :aria-label="t('success.dashboard.chart.teacherProgressAria')"
              :empty-text="t('success.dashboard.empty.teacherProgress')"
              :has-data="teacherHasProgress"
              :option="teacherProgressOption"
              height="214px"
          />
          <DashboardChart
              :aria-label="t('success.dashboard.chart.teacherCapacityAria')"
              :empty-text="t('success.dashboard.empty.teacherCapacity')"
              :has-data="teacherHasProgress"
              :option="teacherCapacityOption"
              height="214px"
          />
        </div>
        <div class="insight-inline">
          <div v-for="insight in teacher.insights" :key="insight.label" class="insight-pill">
            <span>{{ insight.label }}</span>
            <strong>{{ insight.value }}</strong>
          </div>
        </div>
      </article>

      <article class="workbench-panel teacher-panel--sessions">
        <DashboardPanelHeader :title="t('success.dashboard.panels.sessionCoverage')"/>
        <div class="chart-pair">
          <DashboardChart
              :aria-label="t('success.dashboard.chart.teacherSessionAria')"
              :empty-text="t('success.dashboard.empty.teacherSessionsChart')"
              :has-data="teacherHasProgress"
              :option="teacherSessionOption"
              height="212px"
          />
          <DashboardChart
              :aria-label="t('success.dashboard.chart.teacherCoverageAria')"
              :empty-text="t('success.dashboard.empty.teacherCoverage')"
              :has-data="teacherHasCoverage"
              :option="teacherCoverageOption"
              height="212px"
          />
        </div>
      </article>
    </div>

    <div class="teacher-side-column">
      <article class="workbench-panel teacher-panel--actions">
        <DashboardPanelHeader :title="t('success.dashboard.panels.teachingActions')"/>
        <div class="compact-action-grid">
          <template v-for="action in teacherProductionActions" :key="action.label">
            <RouterLink v-if="action.kind === 'link'" :to="action.to" class="compact-action">
              <component :is="action.icon" :size="17" stroke-width="1.8"/>
              <span>{{ action.label }}</span>
            </RouterLink>
            <button v-else class="compact-action" type="button" @click="openCoursePicker(action.courseAction)">
              <component :is="action.icon" :size="17" stroke-width="1.8"/>
              <span>{{ action.label }}</span>
            </button>
          </template>
        </div>
      </article>

      <article class="workbench-panel teacher-panel--capacity">
        <DashboardPanelHeader :title="t('success.dashboard.panels.capacityRanking')"/>
        <div class="capacity-list">
          <RouterLink
              v-for="course in teacherCapacityRanking"
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
          <DashboardEmptyState v-if="teacherCapacityRanking.length === 0"
                               :text="t('success.dashboard.empty.courseCapacity')"/>
        </div>
      </article>

      <article class="workbench-panel teacher-panel--resources">
        <DashboardPanelHeader :title="t('success.dashboard.panels.resourceCoverage')"/>
        <div class="summary-stack">
          <div v-for="metric in teacherResourceSummary" :key="metric.label" class="summary-metric">
            <span>{{ metric.label }}</span>
            <strong>{{ metric.value }}</strong>
            <small>{{ metric.meta }}</small>
          </div>
        </div>
      </article>

      <article class="workbench-panel teacher-panel--assistant">
        <DashboardPanelHeader :title="t('success.dashboard.panels.tianshuAssistant')"/>
        <div class="ai-command-grid">
          <template
              v-for="action in teacherAiActions"
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

      <article class="workbench-panel teacher-panel--notifications">
        <DashboardPanelHeader :title="t('success.dashboard.panels.recentNotifications')"/>
        <DashboardTimelineRows
            :empty-text="t('success.dashboard.empty.timeline')"
            :items="teacher.pending"
            class="pending-timeline"
        />
      </article>
    </div>
  </section>

  <Teleport to="body">
    <div v-if="activeCourseAction" class="dashboard-course-picker-backdrop" @click.self="closeCoursePicker">
      <section aria-modal="true" class="dashboard-course-picker" role="dialog">
        <header class="dashboard-course-picker-header">
          <div>
            <span>{{ t('success.dashboard.panels.teachingActions') }}</span>
            <h2>{{ activeCourseAction.label }}</h2>
          </div>
          <button
              :aria-label="t('courseDetail.close')"
              class="dashboard-course-picker-close"
              type="button"
              @click="closeCoursePicker"
          >
            <X :size="18" stroke-width="1.8"/>
          </button>
        </header>

        <div v-if="teacherCourseOptions.length > 0" class="dashboard-course-picker-list">
          <button
              v-for="option in teacherCourseOptions"
              :key="option.course.id"
              class="dashboard-course-picker-row"
              type="button"
              @click="handleCourseSelected(option.course)"
          >
            <BookOpen :size="18" stroke-width="1.8"/>
            <span>
              <strong>{{ option.course.title }}</strong>
              <small>{{ option.meta }}</small>
            </span>
            <em>{{ option.roleLabel }}</em>
          </button>
        </div>
        <DashboardEmptyState v-else :text="t('success.dashboard.empty.primaryCourses')"/>
      </section>
    </div>
  </Teleport>
</template>

<script lang="ts" setup>
import {BookOpen, FileUp, Library, MonitorUp, PencilLine, Sparkles, X,} from 'lucide-vue-next'
import {type Component, computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {type RouteLocationRaw, RouterLink, useRouter} from 'vue-router'

import {useAiModeNavigation} from '@/features/ai/composables/useAiModeNavigation'
import {ClassSessionStatus} from '@/features/course/types/classSession'
import type {Course} from '@/features/course/types/course'
import type {TeacherDashboard} from '@/features/dashboard/api/dashboard'
import DashboardChart from '@/features/dashboard/components/DashboardChart.vue'
import DashboardEmptyState from '@/features/dashboard/components/DashboardEmptyState.vue'
import DashboardPanelHeader from '@/features/dashboard/components/DashboardPanelHeader.vue'
import DashboardTimelineRows from '@/features/dashboard/components/DashboardTimelineRows.vue'
import {barOption, lineOption} from '@/features/dashboard/utils/dashboardChartOptions'
import {
  capacityPercent,
  clampPercent,
  formatDashboardNumber,
  formatSessionRange,
  type Metric,
  shortLabel,
} from '@/features/dashboard/utils/dashboardFormatters'
import {notify} from '@/shared/composables/useGlobalNotification'

const props = defineProps<{
  teacher: TeacherDashboard
}>()

const {t, locale} = useI18n()
const router = useRouter()
const enterAiMode = useAiModeNavigation()

type TeacherCourseAction = 'createChapter' | 'uploadCourseware' | 'createQuestionBank'
type TeacherProductionAction =
    | { kind: 'link'; label: string; to: string; icon: Component }
    | { kind: 'course'; label: string; courseAction: TeacherCourseAction; icon: Component }
type TeacherAiAction =
    | { kind: 'ai'; label: string; description: string; to: RouteLocationRaw }
    | { kind: 'notice'; label: string; description: string; notice: string }

const TIANSHU_NOTICE_DURATION = 6000

interface CoursePickerOption {
  course: Course
  roleLabel: string
  meta: string
}

const teacherMetrics = computed<Metric[]>(() => {
  const students = props.teacher.primaryCourses.reduce((sum, course) => sum + course.currentStudents, 0)
  const sessions = props.teacher.primaryCourses.reduce((sum, course) => sum + course.publishedClassSessionCount, 0)

  return [
    {
      label: t('success.dashboard.metrics.teacher.primaryCourses.label'),
      value: formatDashboardNumber(props.teacher.primaryCourses.length, String(locale.value)),
      meta: t('success.dashboard.metrics.teacher.primaryCourses.meta'),
    },
    {
      label: t('success.dashboard.metrics.teacher.assistantCourses.label'),
      value: formatDashboardNumber(props.teacher.assistantCourses.length, String(locale.value)),
      meta: t('success.dashboard.metrics.teacher.assistantCourses.meta'),
    },
    {
      label: t('success.dashboard.metrics.teacher.students.label'),
      value: formatDashboardNumber(students, String(locale.value)),
      meta: t('success.dashboard.metrics.teacher.students.meta'),
    },
    {
      label: t('success.dashboard.metrics.teacher.publishedSessions.label'),
      value: formatDashboardNumber(sessions, String(locale.value)),
      meta: t('success.dashboard.metrics.teacher.publishedSessions.meta'),
    },
    {
      label: t('success.dashboard.metrics.teacher.recentNotifications.label'),
      value: formatDashboardNumber(props.teacher.pending.length, String(locale.value)),
      meta: t('success.dashboard.metrics.teacher.recentNotifications.meta'),
    },
    {
      label: t('success.dashboard.metrics.teacher.unread.label'),
      value: formatDashboardNumber(props.teacher.notifications.unreadTeaching, String(locale.value)),
      meta: t('success.dashboard.metrics.teacher.unread.meta'),
    },
  ]
})

const teacherProgressOption = computed(() => barOption(
    props.teacher.primaryCourses.map((course) => shortLabel(course.title)),
    props.teacher.primaryCourses.map((course) => clampPercent(course.courseProgress)),
    t('success.dashboard.chart.series.courseProgress'),
    '%',
))

const teacherCapacityOption = computed(() => barOption(
    props.teacher.primaryCourses.map((course) => shortLabel(course.title)),
    props.teacher.primaryCourses.map((course) => capacityPercent(course)),
    t('success.dashboard.chart.series.capacityRate'),
    '%',
))

const teacherSessionOption = computed(() => lineOption(
    props.teacher.primaryCourses.map((course) => shortLabel(course.title)),
    props.teacher.primaryCourses.map((course) => course.publishedClassSessionCount),
    t('success.dashboard.chart.series.publishedSessions'),
))

const teacherCoverageOption = computed(() => barOption(
    props.teacher.questionCoverage.map((item) => shortLabel(item.courseTitle)),
    props.teacher.questionCoverage.map((item) => item.questionCount),
    t('success.dashboard.chart.series.questionCount'),
))

const teacherResourceSummary = computed<Metric[]>(() => {
  const totalBanks = props.teacher.questionCoverage.reduce((sum, item) => sum + item.bankCount, 0)
  const totalQuestions = props.teacher.questionCoverage.reduce((sum, item) => sum + item.questionCount, 0)
  const coveredCourses = props.teacher.questionCoverage.filter((item) => item.bankCount > 0).length

  return [
    {
      label: t('success.dashboard.metrics.teacherResource.banks.label'),
      value: formatDashboardNumber(totalBanks, String(locale.value)),
      meta: t('success.dashboard.metrics.teacherResource.banks.meta'),
    },
    {
      label: t('success.dashboard.metrics.teacherResource.questions.label'),
      value: formatDashboardNumber(totalQuestions, String(locale.value)),
      meta: t('success.dashboard.metrics.teacherResource.questions.meta'),
    },
    {
      label: t('success.dashboard.metrics.teacherResource.coveredCourses.label'),
      value: formatDashboardNumber(coveredCourses, String(locale.value)),
      meta: t('success.dashboard.metrics.teacherResource.coveredCourses.meta'),
    },
  ]
})

const teacherCapacityRanking = computed(() => props.teacher.capacityRanking.length
    ? props.teacher.capacityRanking
    : [...props.teacher.primaryCourses]
        .sort((a, b) => capacityPercent(b) - capacityPercent(a))
        .slice(0, 5)
        .map((course) => ({
          id: course.id,
          title: course.title,
          value: capacityPercent(course),
          detail: t('success.dashboard.labels.studentCount', {
            current: course.currentStudents,
            max: course.maxStudents || '--',
          }),
        })))

const teacherHasProgress = computed(() => props.teacher.primaryCourses.length > 0)
const teacherHasCoverage = computed(() => props.teacher.questionCoverage.some((item) => item.questionCount > 0))

const coursePickerAction = ref<TeacherCourseAction | null>(null)

const teacherProductionActions = computed<TeacherProductionAction[]>(() => [
  {
    kind: 'link',
    label: t('success.dashboard.actions.createCourse'),
    to: '/teacher/courses?role=primary',
    icon: BookOpen
  },
  {
    kind: 'course',
    label: t('success.dashboard.actions.createChapter'),
    courseAction: 'createChapter',
    icon: PencilLine
  },
  {
    kind: 'course',
    label: t('success.dashboard.actions.uploadCourseware'),
    courseAction: 'uploadCourseware',
    icon: FileUp
  },
  {
    kind: 'course',
    label: t('success.dashboard.actions.createQuestionBank'),
    courseAction: 'createQuestionBank',
    icon: Library
  },
  {kind: 'link', label: t('success.dashboard.actions.aiQuestionGeneration'), to: '/ai?mode=QUESTION', icon: Sparkles},
])

const activeCourseAction = computed(() => teacherProductionActions.value.find((action) => (
    action.kind === 'course' && action.courseAction === coursePickerAction.value
)) || null)

const teacherCourseOptions = computed<CoursePickerOption[]>(() => {
  const options: CoursePickerOption[] = []
  const seen = new Set<string>()

  const addCourses = (courses: Course[], roleLabel: string) => {
    courses.forEach((course) => {
      if (seen.has(course.id)) return
      seen.add(course.id)
      options.push({
        course,
        roleLabel,
        meta: t('success.dashboard.labels.studentCount', {
          current: course.currentStudents,
          max: course.maxStudents || '--',
        }),
      })
    })
  }

  addCourses(props.teacher.primaryCourses, t('success.dashboard.metrics.teacher.primaryCourses.label'))
  addCourses(props.teacher.assistantCourses, t('success.dashboard.metrics.teacher.assistantCourses.label'))

  return options
})

const teacherAiActions = computed<TeacherAiAction[]>(() => [
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
    description: t('success.dashboard.actions.tianshuTeacherGradingHint'),
    notice: t('success.dashboard.actions.tianshuTeacherGradingNotice'),
  },
  {
    kind: 'notice',
    label: t('success.dashboard.actions.tianshuClassMinutes'),
    description: t('success.dashboard.actions.tianshuClassMinutesHint'),
    notice: t('success.dashboard.actions.tianshuClassMinutesNotice'),
  },
])

function openCoursePicker(action: TeacherCourseAction) {
  coursePickerAction.value = action
}

function closeCoursePicker() {
  coursePickerAction.value = null
}

function handleAiAction(action: TeacherAiAction) {
  if (action.kind !== 'ai') return
  void enterAiMode(action.to)
}

function showTianshuNotice(action: TeacherAiAction) {
  if (action.kind !== 'notice') return
  notify.info(action.notice, {duration: TIANSHU_NOTICE_DURATION})
}

function handleCourseSelected(course: Course) {
  const action = coursePickerAction.value
  closeCoursePicker()

  if (action === 'createChapter') {
    void router.push({name: 'chapter-detail', params: {courseId: course.id}, query: {create: '1'}})
    return
  }

  if (action === 'uploadCourseware') {
    void router.push({name: 'course-files', params: {id: course.id}, query: {upload: '1'}})
    return
  }

  if (action === 'createQuestionBank') {
    void router.push({name: 'course-question-banks', params: {courseId: course.id}, query: {create: '1'}})
  }
}
</script>

