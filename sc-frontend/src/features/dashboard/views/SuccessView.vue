<template>
  <main class="dashboard-workbench">
    <section class="workbench-heading" aria-labelledby="dashboard-title">
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

    <section v-if="isLoading" class="dashboard-skeleton" aria-live="polite">
      <div v-for="item in 8" :key="item" class="skeleton-block"></div>
    </section>

    <template v-else-if="role === 0">
      <section class="risk-strip">
        <article v-for="alert in adminData.risks" :key="alert.title" class="risk-row">
          <component :is="alert.icon" :size="18" stroke-width="1.8"/>
          <div>
            <strong>{{ alert.title }}</strong>
            <span>{{ alert.detail }}</span>
          </div>
          <span class="status-chip" :class="alert.level">{{ alert.status }}</span>
        </article>
      </section>

      <section class="metric-grid metric-grid--seven" aria-label="平台总览指标">
        <article v-for="metric in adminMetrics" :key="metric.label" class="metric-cell">
          <span>{{ metric.label }}</span>
          <strong>{{ metric.value }}</strong>
          <small>{{ metric.meta }}</small>
        </article>
      </section>

      <section class="admin-grid">
        <article class="workbench-panel span-7">
          <PanelHeader title="账号结构与状态" action-label="用户管理" to="/admin/users"/>
          <div class="chart-summary-layout">
            <DashboardChart
                :option="adminUserRoleOption"
                :has-data="adminHasUsers"
                height="248px"
                aria-label="用户角色分布"
                empty-text="暂无用户结构数据"
            />
            <div class="summary-stack">
              <div class="ratio-line">
                <span>OAuth 账号</span>
                <strong>{{ adminData.oauthPercent }}%</strong>
              </div>
              <div class="progress-track">
                <span :style="{width: `${adminData.oauthPercent}%`}"></span>
              </div>
              <div class="ratio-line muted">
                <span>本地账号</span>
                <strong>{{ 100 - adminData.oauthPercent }}%</strong>
              </div>
              <div class="account-flags">
                <span>资料待完善 {{ adminData.incompleteProfiles }}</span>
                <span>禁用账号 {{ adminData.disabledUsers }}</span>
              </div>
              <div class="compact-list subtle-list">
                <div v-for="login in adminData.loginActivity.slice(0, 3)" :key="login.id" class="compact-row">
                  <span>{{ login.name }}</span>
                  <small>{{ login.time }}</small>
                </div>
              </div>
            </div>
          </div>
        </article>

        <article class="workbench-panel span-5">
          <PanelHeader title="管理快捷入口"/>
          <div class="action-grid">
            <RouterLink v-for="action in adminActions" :key="action.label" :to="action.to" class="action-tile">
              <component :is="action.icon" :size="18" stroke-width="1.8"/>
              <span>{{ action.label }}</span>
              <ArrowRight :size="16" stroke-width="1.8"/>
            </RouterLink>
          </div>
        </article>

        <article class="workbench-panel span-7">
          <PanelHeader title="课程与通知分布" action-label="课程管理" to="/course-management"/>
          <div class="chart-pair">
            <DashboardChart
                :option="adminCourseStatusOption"
                :has-data="adminHasCourseStatus"
                height="230px"
                aria-label="课程状态分布"
                empty-text="暂无课程状态数据"
            />
            <DashboardChart
                :option="adminNotificationOption"
                :has-data="adminHasNotifications"
                height="230px"
                aria-label="通知类型和未读分布"
                empty-text="暂无通知分布数据"
            />
          </div>
          <div class="resource-summary">
            <div v-for="item in adminData.resources" :key="item.label" class="summary-chip">
              <span>{{ item.label }}</span>
              <strong>{{ item.value }}</strong>
            </div>
          </div>
        </article>

        <article class="workbench-panel span-5">
          <PanelHeader title="课程容量 Top" action-label="课程管理" to="/course-management"/>
          <div class="capacity-list">
            <RouterLink
                v-for="course in adminData.courseCapacity"
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
            <EmptyState v-if="adminData.courseCapacity.length === 0" text="暂无课程容量数据"/>
          </div>
        </article>

        <article class="workbench-panel span-5">
          <PanelHeader title="系统通知中心" action-label="查看全部" to="/notifications"/>
          <NotificationRows :notifications="recentNotifications"/>
        </article>

        <article class="workbench-panel span-12">
          <PanelHeader title="最近平台活动"/>
          <TimelineRows :items="adminData.activities"/>
        </article>
      </section>
    </template>

    <template v-else-if="role === 2">
      <section class="teacher-live-panel">
        <div>
          <span class="status-chip live">{{ teacherData.liveSession ? '正在上课' : '今日教学' }}</span>
          <h2>{{ teacherData.liveSession?.title || '暂无正在直播的课堂' }}</h2>
          <p>
            {{ teacherData.liveSession ? formatSessionRange(teacherData.liveSession) : '查看今日安排，继续备课或发布新的课堂内容。' }}
          </p>
        </div>
        <RouterLink
            class="command-button primary"
            :to="teacherData.liveSession ? `/class-sessions/${teacherData.liveSession.id}` : '/course-management'"
        >
          <MonitorUp :size="18" stroke-width="1.8"/>
          <span>{{ teacherData.liveSession ? '进入 3D 教室' : '维护课程内容' }}</span>
        </RouterLink>
      </section>

      <section class="metric-grid metric-grid--six" aria-label="教学概览指标">
        <article v-for="metric in teacherMetrics" :key="metric.label" class="metric-cell">
          <span>{{ metric.label }}</span>
          <strong>{{ metric.value }}</strong>
          <small>{{ metric.meta }}</small>
        </article>
      </section>

      <section class="teacher-grid">
        <article class="workbench-panel span-8">
          <PanelHeader title="今日教学安排" action-label="课程课堂" to="/course-management"/>
          <div class="session-list">
            <RouterLink
                v-for="session in teacherData.sessions"
                :key="session.id"
                :to="`/class-sessions/${session.id}`"
                class="session-row"
            >
              <div>
                <strong>{{ session.title }}</strong>
                <span>{{ formatSessionRange(session) }}</span>
              </div>
              <span class="status-chip" :class="{live: session.liveStatus === ClassLiveStatus.LIVE}">
                {{ session.liveStatusText || session.statusText }}
              </span>
            </RouterLink>
            <EmptyState v-if="teacherData.sessions.length === 0" text="今天暂无课堂安排"/>
          </div>
        </article>

        <article class="workbench-panel span-8">
          <PanelHeader title="课程运营卡片" action-label="全部课程" to="/teacher/courses?role=primary"/>
          <div class="course-ops-grid">
            <RouterLink v-for="course in teacherData.courses" :key="course.id" :to="`/courses/${course.id}/overview`" class="course-op">
              <div class="course-op-main">
                <strong>{{ course.title }}</strong>
                <span class="course-op-percent">{{ clampPercent(course.courseProgress) }}%</span>
              </div>
              <div class="progress-track">
                <span :style="{width: `${clampPercent(course.courseProgress)}%`}"></span>
              </div>
              <div class="course-op-stats">
                <span class="course-op-stat">
                  <span class="course-op-stat-value">{{ course.currentStudents }} / {{ course.maxStudents }}</span>
                  <span class="course-op-stat-label">学生容量</span>
                </span>
                <span class="course-op-stat">
                  <span class="course-op-stat-value">{{ course.publishedClassSessionCount }}</span>
                  <span class="course-op-stat-label">已发布课堂</span>
                </span>
              </div>
            </RouterLink>
            <EmptyState v-if="teacherData.courses.length === 0" text="还没有主讲课程"/>
          </div>
        </article>

        <article class="workbench-panel span-4">
          <PanelHeader title="教学动作"/>
          <div class="compact-action-grid">
            <RouterLink v-for="action in teacherProductionActions" :key="action.label" :to="action.to" class="compact-action">
              <component :is="action.icon" :size="17" stroke-width="1.8"/>
              <span>{{ action.label }}</span>
            </RouterLink>
          </div>
        </article>

        <article class="workbench-panel span-8">
          <PanelHeader title="学情与课程运营"/>
          <div class="chart-pair">
            <DashboardChart
                :option="teacherProgressOption"
                :has-data="teacherHasProgress"
                height="240px"
                aria-label="课程进度分布"
                empty-text="暂无课程进度数据"
            />
            <DashboardChart
                :option="teacherCapacityOption"
                :has-data="teacherHasProgress"
                height="240px"
                aria-label="课程容量利用率"
                empty-text="暂无容量利用率数据"
            />
          </div>
          <div class="insight-inline">
            <div v-for="insight in teacherData.insights" :key="insight.label" class="insight-pill">
              <span>{{ insight.label }}</span>
              <strong>{{ insight.value }}</strong>
            </div>
          </div>
        </article>

        <article class="workbench-panel span-4">
          <PanelHeader title="容量排行"/>
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
            <EmptyState v-if="teacherCapacityRanking.length === 0" text="暂无课程容量数据"/>
          </div>
        </article>

        <article class="workbench-panel span-8">
          <PanelHeader title="课堂节奏与题库覆盖"/>
          <div class="chart-pair">
            <DashboardChart
                :option="teacherSessionOption"
                :has-data="teacherHasProgress"
                height="236px"
                aria-label="课堂发布节奏"
                empty-text="暂无课堂发布数据"
            />
            <DashboardChart
                :option="teacherCoverageOption"
                :has-data="teacherHasCoverage"
                height="236px"
                aria-label="题库资源覆盖"
                empty-text="暂无题库覆盖数据"
            />
          </div>
        </article>

        <article class="workbench-panel span-4">
          <PanelHeader title="资源覆盖摘要"/>
          <div class="summary-stack">
            <div v-for="metric in teacherResourceSummary" :key="metric.label" class="summary-metric">
              <span>{{ metric.label }}</span>
              <strong>{{ metric.value }}</strong>
              <small>{{ metric.meta }}</small>
            </div>
          </div>
        </article>

        <article class="workbench-panel span-6">
          <PanelHeader title="近期通知"/>
          <TimelineRows :items="teacherData.pending" class="pending-timeline"/>
        </article>

        <article class="workbench-panel span-6">
          <PanelHeader title="天枢助手"/>
          <div class="ai-command-grid">
            <RouterLink v-for="action in teacherAiActions" :key="action.label" :to="action.to" class="ai-command">
              <component :is="action.icon" :size="18" stroke-width="1.8"/>
              <span>{{ action.label }}</span>
            </RouterLink>
          </div>
        </article>
      </section>
    </template>

    <template v-else>
      <section class="student-live-panel">
        <div>
          <span class="status-chip live">{{ studentData.liveSession ? '正在进行' : '继续学习' }}</span>
          <h2>{{ studentData.liveSession?.title || studentData.continueCourse?.courseTitle || '今天从一门课程开始' }}</h2>
          <p>
            {{ studentData.liveSession ? formatSessionRange(studentData.liveSession) : studentContinueText }}
          </p>
        </div>
        <RouterLink
            class="command-button primary"
            :to="studentData.liveSession ? `/class-sessions/${studentData.liveSession.id}` : studentContinueRoute"
        >
          <DoorOpen :size="18" stroke-width="1.8"/>
          <span>{{ studentData.liveSession ? '进入 3D 教室' : '继续学习' }}</span>
        </RouterLink>
      </section>

      <section class="metric-grid metric-grid--five" aria-label="学习概览指标">
        <article v-for="metric in studentMetrics" :key="metric.label" class="metric-cell">
          <span>{{ metric.label }}</span>
          <strong>{{ metric.value }}</strong>
          <small>{{ metric.meta }}</small>
        </article>
      </section>

      <section class="student-grid">
        <article class="workbench-panel span-7">
          <PanelHeader title="今日待办" action-label="通知" to="/notifications"/>
          <TimelineRows :items="studentData.todos"/>
        </article>

        <article class="workbench-panel span-5">
          <PanelHeader title="天枢学习助手"/>
          <div class="ai-command-grid">
            <RouterLink v-for="action in studentAiActions" :key="action.label" :to="action.to" class="ai-command">
              <component :is="action.icon" :size="18" stroke-width="1.8"/>
              <span>{{ action.label }}</span>
            </RouterLink>
          </div>
        </article>

        <article class="workbench-panel span-8">
          <PanelHeader title="学习进度分析" action-label="我的选课" to="/my-enrollments"/>
          <div class="chart-pair chart-pair--wide">
            <DashboardChart
                :option="studentProgressDonutOption"
                :has-data="studentHasEnrollments"
                height="250px"
                aria-label="学习进度分布"
                empty-text="暂无学习进度数据"
            />
            <DashboardChart
                :option="studentProgressRankOption"
                :has-data="studentHasEnrollments"
                height="250px"
                aria-label="课程进度排行"
                empty-text="暂无课程进度排行"
            />
          </div>
        </article>

        <article class="workbench-panel span-4">
          <PanelHeader title="近期课堂时间轴"/>
          <DashboardChart
              :option="studentSessionOption"
              :has-data="studentHasSessions"
              height="250px"
              aria-label="近期课堂时间轴"
              empty-text="暂无近期课堂"
          />
        </article>

        <article class="workbench-panel span-8">
          <PanelHeader title="我的课程进度" action-label="我的选课" to="/my-enrollments"/>
          <div class="course-progress-list">
            <RouterLink
                v-for="enrollment in studentData.enrollments"
                :key="enrollment.id"
                :to="`/courses/${enrollment.courseId}/overview`"
                class="progress-row"
            >
              <div>
                <strong>{{ enrollment.courseTitle || '未命名课程' }}</strong>
                <span>{{ enrollment.coursePublishedClassSessionCount }} 节课堂已发布</span>
              </div>
              <div class="progress-track">
                <span :style="{width: `${clampPercent(enrollment.courseProgress)}%`}"></span>
              </div>
              <small>{{ clampPercent(enrollment.courseProgress) }}%</small>
            </RouterLink>
            <EmptyState v-if="studentData.enrollments.length === 0" text="还没有选课记录"/>
          </div>
        </article>

        <article class="workbench-panel span-4">
          <PanelHeader title="练习与题库"/>
          <div class="practice-focus">
            <Target :size="24" stroke-width="1.8"/>
            <strong>{{ studentData.practiceFocus.title }}</strong>
            <p>{{ studentData.practiceFocus.detail }}</p>
            <RouterLink class="command-button" :to="studentData.practiceFocus.to">开始练习</RouterLink>
          </div>
        </article>

        <article class="workbench-panel span-7">
          <PanelHeader title="练习表现"/>
          <DashboardChart
              :option="studentPracticeOption"
              :has-data="studentHasPractice"
              height="236px"
              aria-label="练习正确率趋势"
              empty-text="暂无练习历史"
          />
          <div class="insight-inline">
            <div v-for="metric in studentPracticeSummary" :key="metric.label" class="insight-pill">
              <span>{{ metric.label }}</span>
              <strong>{{ metric.value }}</strong>
            </div>
          </div>
        </article>

        <article class="workbench-panel span-7">
          <PanelHeader title="最近学习"/>
          <div class="recent-course-list">
            <RouterLink v-for="course in recentCourses" :key="course.id" :to="`/courses/${course.id}/overview`" class="recent-course-row">
              <strong>{{ course.title }}</strong>
              <span>{{ course.teacherName || '教师未设置' }}</span>
              <small>{{ formatRelativeTime(course.visitedAt) }}</small>
            </RouterLink>
            <EmptyState v-if="recentCourses.length === 0" text="访问课程后会在这里出现继续入口"/>
          </div>
        </article>

        <article class="workbench-panel span-5">
          <PanelHeader title="公开课程推荐" action-label="发现课程" to="/courses"/>
          <div class="recommend-list">
            <RouterLink v-for="course in studentData.recommendations" :key="course.id" :to="`/courses/${course.id}/overview`" class="recommend-row">
              <strong>{{ course.title }}</strong>
              <span>{{ course.teacherName || 'SapientiaCloud 教师' }}</span>
            </RouterLink>
            <EmptyState v-if="studentData.recommendations.length === 0" text="暂无公开课程推荐"/>
          </div>
        </article>
      </section>
    </template>
  </main>
</template>

<script lang="ts" setup>
import {
    AlertTriangle,
    ArrowRight,
    BookOpen,
    Bot,
    ClipboardCheck,
    DoorOpen,
    FileUp,
    GraduationCap,
    Library,
    Megaphone,
    MonitorUp,
    PencilLine,
    ShieldCheck,
    Sparkles,
    Target,
    UserCog,
    Users,
} from 'lucide-vue-next'
import type {EChartsCoreOption} from 'echarts/core'
import type {Component} from 'vue'
import {computed, defineComponent, h, onMounted, ref, watch} from 'vue'
import {RouterLink} from 'vue-router'

import {useAuthStore} from '@/features/auth/stores/auth'
import type {ClassSession} from '@/features/course/types/classSession'
import {ClassLiveStatus} from '@/features/course/types/classSession'
import type {Course, Enrollment} from '@/features/course/types/course'
import {
    getMyDashboard,
    type AdminDashboard,
    type DashboardNotificationItem,
    type DashboardNotificationSummary,
    type DashboardResponse,
    type DashboardTimelineItem,
    type DashboardUserSummary,
    type StudentDashboard,
    type TeacherDashboard,
} from '@/features/dashboard/api/dashboard'
import DashboardChart from '@/features/dashboard/components/DashboardChart.vue'
import type {Notification, UnreadCount} from '@/features/notification/api/notification'
import type {PracticeSession} from '@/features/question-bank/types/practiceSession'
import {useRecentCourses} from '@/shared/composables/useRecentCourses'

interface Metric {
    label: string
    value: string
    meta: string
}

interface ActionLink {
    label: string
    to: string
    icon: Component
}

interface TimelineItem {
    title: string
    detail: string
    time: string
    level?: 'normal' | 'warning' | 'critical' | 'live'
}

interface RiskItem {
    title: string
    detail: string
    status: string
    level: 'warning' | 'critical' | 'normal'
    icon: Component
}

interface AdminResource {
    label: string
    value: string
    status: string
    note: string
}

interface ChartDatum {
    label: string
    value: number
}

interface CapacityItem {
    id: string
    title: string
    value: number
    detail: string
}

interface QuestionCoverage {
    courseId: string
    courseTitle: string
    bankCount: number
    questionCount: number
}

const authStore = useAuthStore()
const {recentCourses} = useRecentCourses()

const isLoading = ref(true)
const unreadCount = ref<UnreadCount>({total: 0, system: 0, teaching: 0})
const recentNotifications = ref<Notification[]>([])

const adminData = ref({
    totalUsers: 0,
    students: 0,
    teachers: 0,
    courses: 0,
    publishedCourses: 0,
    todayUsers: 0,
    disabledUsers: 0,
    incompleteProfiles: 0,
    oauthPercent: 0,
    loginActivity: [] as { id: string; name: string; ip: string; time: string }[],
    resources: [] as AdminResource[],
    courseStatus: [] as ChartDatum[],
    courseCapacity: [] as CapacityItem[],
    activities: [] as TimelineItem[],
    risks: [] as RiskItem[],
})

const studentData = ref({
    enrollments: [] as Enrollment[],
    sessions: [] as ClassSession[],
    recommendations: [] as Course[],
    liveSession: null as ClassSession | null,
    continueCourse: null as Enrollment | null,
    practiceFocus: {
        title: '题库练习',
        detail: '从最近课程中选择一个题库，继续巩固薄弱知识点。',
        to: '/courses',
        icon: 'Target',
    },
    practiceSessions: [] as PracticeSession[],
    todos: [] as TimelineItem[],
})

const teacherData = ref({
    primaryCourses: [] as Course[],
    assistantCourses: [] as Course[],
    courses: [] as Course[],
    sessions: [] as ClassSession[],
    liveSession: null as ClassSession | null,
    pending: [] as TimelineItem[],
    insights: [] as { label: string; value: string }[],
    questionCoverage: [] as QuestionCoverage[],
    capacityRanking: [] as CapacityItem[],
})

const role = computed(() => authStore.user?.role ?? 1)

const displayName = computed(() => authStore.user?.displayName || authStore.user?.email || 'SapientiaCloud')

const roleLabel = computed(() => {
    if (role.value === 0) return '管理员'
    if (role.value === 2) return '教师'
    return '学生'
})

const greetingTitle = computed(() => {
    if (role.value === 0) return `欢迎回来，${displayName.value} 管理员`
    if (role.value === 2) return `欢迎回来，${displayName.value} 老师`
    return `欢迎回来，${displayName.value} 同学`
})

const roleCopy = computed(() => {
    if (role.value === 0) {
        return {
            kicker: '平台运行中枢',
            title: '管理后台概览',
            subtitle: '优先查看异常、平台规模和最近运营事件。',
        }
    }
    if (role.value === 2) {
        return {
            kicker: '教学运营台',
            title: '教师工作台',
            subtitle: '围绕备课、上课、反馈和学情跟踪组织今日动作。',
        }
    }
    return {
        kicker: '学习工作台',
        title: '学生首页',
        subtitle: '继续学习、处理待办，并留意课程与课堂通知。',
    }
})

const adminMetrics = computed<Metric[]>(() => [
    {label: '用户总数', value: formatNumber(adminData.value.totalUsers), meta: `今日新增 ${adminData.value.todayUsers}`},
    {label: '学生数', value: formatNumber(adminData.value.students), meta: '学习端账号'},
    {label: '教师数', value: formatNumber(adminData.value.teachers), meta: '教学端账号'},
    {label: '课程数', value: formatNumber(adminData.value.courses), meta: '全部状态'},
    {label: '已发布课程', value: formatNumber(adminData.value.publishedCourses), meta: '学生可见'},
    {label: '禁用账号', value: formatNumber(adminData.value.disabledUsers), meta: '需要审计'},
    {label: '未读通知', value: formatNumber(unreadCount.value.total), meta: `系统 ${unreadCount.value.system}`},
])

const studentMetrics = computed<Metric[]>(() => {
    const enrollments = studentData.value.enrollments
    const completed = enrollments.filter(item => item.status === 2).length
    const inProgress = enrollments.filter(item => item.status === 1).length
    const avgProgress = enrollments.length
        ? Math.round(enrollments.reduce((sum, item) => sum + clampPercent(item.courseProgress), 0) / enrollments.length)
        : 0

    return [
        {label: '已选课程', value: formatNumber(enrollments.length), meta: '当前学习范围'},
        {label: '进行中', value: formatNumber(inProgress), meta: '可继续学习'},
        {label: '已完成', value: formatNumber(completed), meta: '结业记录'},
        {label: '平均进度', value: `${avgProgress}%`, meta: '课程进度'},
        {label: '未读通知', value: formatNumber(unreadCount.value.total), meta: `教学 ${unreadCount.value.teaching}`},
    ]
})

const teacherMetrics = computed<Metric[]>(() => {
    const students = teacherData.value.primaryCourses.reduce((sum, course) => sum + course.currentStudents, 0)
    const sessions = teacherData.value.primaryCourses.reduce((sum, course) => sum + course.publishedClassSessionCount, 0)

    return [
        {label: '主讲课程', value: formatNumber(teacherData.value.primaryCourses.length), meta: '负责内容'},
        {label: '助教课程', value: formatNumber(teacherData.value.assistantCourses.length), meta: '协作授课'},
        {label: '在学学生', value: formatNumber(students), meta: '主讲课程'},
        {label: '已发布课堂', value: formatNumber(sessions), meta: '课堂资源'},
        {label: '近期通知', value: formatNumber(teacherData.value.pending.length), meta: '通知摘要'},
        {label: '未读通知', value: formatNumber(unreadCount.value.teaching), meta: '教学通知'},
    ]
})

const adminUserRoleOption = computed<EChartsCoreOption>(() => donutOption([
    {label: '学生', value: adminData.value.students},
    {label: '教师', value: adminData.value.teachers},
    {label: '管理员/其他', value: Math.max(0, adminData.value.totalUsers - adminData.value.students - adminData.value.teachers)},
], '账号结构'))

const adminCourseStatusOption = computed<EChartsCoreOption>(() => barOption(
    adminData.value.courseStatus.map(item => item.label),
    adminData.value.courseStatus.map(item => item.value),
    '课程数量',
))

const adminNotificationOption = computed<EChartsCoreOption>(() => donutOption([
    {label: '系统未读', value: unreadCount.value.system},
    {label: '教学未读', value: unreadCount.value.teaching},
    {label: '近期已读', value: recentNotifications.value.filter(item => item.isRead).length},
], '通知结构'))

const teacherProgressOption = computed<EChartsCoreOption>(() => barOption(
    teacherData.value.primaryCourses.map(course => shortLabel(course.title)),
    teacherData.value.primaryCourses.map(course => clampPercent(course.courseProgress)),
    '课程进度',
    '%',
))

const teacherCapacityOption = computed<EChartsCoreOption>(() => barOption(
    teacherData.value.primaryCourses.map(course => shortLabel(course.title)),
    teacherData.value.primaryCourses.map(course => capacityPercent(course)),
    '容量利用率',
    '%',
))

const teacherSessionOption = computed<EChartsCoreOption>(() => lineOption(
    teacherData.value.primaryCourses.map(course => shortLabel(course.title)),
    teacherData.value.primaryCourses.map(course => course.publishedClassSessionCount),
    '已发布课堂',
))

const teacherCoverageOption = computed<EChartsCoreOption>(() => barOption(
    teacherData.value.questionCoverage.map(item => shortLabel(item.courseTitle)),
    teacherData.value.questionCoverage.map(item => item.questionCount),
    '题目数量',
))

const studentProgressDonutOption = computed<EChartsCoreOption>(() => {
    const buckets = progressBuckets(studentData.value.enrollments.map(item => item.courseProgress))
    return donutOption(buckets, '学习进度')
})

const studentProgressRankOption = computed<EChartsCoreOption>(() => barOption(
    studentData.value.enrollments.map(item => shortLabel(item.courseTitle || '未命名课程')),
    studentData.value.enrollments.map(item => clampPercent(item.courseProgress)),
    '课程进度',
    '%',
))

const studentSessionOption = computed<EChartsCoreOption>(() => lineOption(
    studentData.value.sessions.map(session => formatMonthDay(session.scheduledStartAt)),
    studentData.value.sessions.map((_, index) => index + 1),
    '近期课堂',
))

const studentPracticeOption = computed<EChartsCoreOption>(() => lineOption(
    studentData.value.practiceSessions.slice(-6).map(session => formatMonthDay(session.startedAt)),
    studentData.value.practiceSessions.slice(-6).map(session => accuracyPercent(session)),
    '练习正确率',
    '%',
))

const teacherResourceSummary = computed<Metric[]>(() => {
    const totalBanks = teacherData.value.questionCoverage.reduce((sum, item) => sum + item.bankCount, 0)
    const totalQuestions = teacherData.value.questionCoverage.reduce((sum, item) => sum + item.questionCount, 0)
    const coveredCourses = teacherData.value.questionCoverage.filter(item => item.bankCount > 0).length

    return [
        {label: '题库总数', value: formatNumber(totalBanks), meta: '主讲课程'},
        {label: '题目总数', value: formatNumber(totalQuestions), meta: '题库聚合'},
        {label: '覆盖课程', value: formatNumber(coveredCourses), meta: '已有题库'},
    ]
})

const studentPracticeSummary = computed<Metric[]>(() => {
    const sessions = studentData.value.practiceSessions
    const completed = sessions.filter(item => item.status === 1).length
    const avgAccuracy = sessions.length
        ? Math.round(sessions.reduce((sum, item) => sum + accuracyPercent(item), 0) / sessions.length)
        : 0

    return [
        {label: '练习次数', value: formatNumber(sessions.length), meta: '历史记录'},
        {label: '已完成', value: formatNumber(completed), meta: '完成练习'},
        {label: '平均正确率', value: `${avgAccuracy}%`, meta: '按练习统计'},
    ]
})

const adminHasUsers = computed(() => adminData.value.totalUsers > 0)
const adminHasCourseStatus = computed(() => adminData.value.courseStatus.some(item => item.value > 0))
const adminHasNotifications = computed(() => unreadCount.value.total > 0 || recentNotifications.value.length > 0)
const teacherHasProgress = computed(() => teacherData.value.primaryCourses.length > 0)
const teacherHasCoverage = computed(() => teacherData.value.questionCoverage.some(item => item.questionCount > 0))
const studentHasEnrollments = computed(() => studentData.value.enrollments.length > 0)
const studentHasSessions = computed(() => studentData.value.sessions.length > 0)
const studentHasPractice = computed(() => studentData.value.practiceSessions.length > 0)
const teacherCapacityRanking = computed(() => teacherData.value.capacityRanking.length
    ? teacherData.value.capacityRanking
    : [...teacherData.value.primaryCourses]
        .sort((a, b) => capacityPercent(b) - capacityPercent(a))
        .slice(0, 5)
        .map(course => ({
            id: course.id,
            title: course.title,
            value: capacityPercent(course),
            detail: course.currentStudents + '/' + (course.maxStudents || '--') + ' 名学生',
        })))

const studentContinueRoute = computed(() => {
    const course = studentData.value.continueCourse
    return course ? `/courses/${course.courseId}/overview` : '/courses'
})

const studentContinueText = computed(() => {
    const course = studentData.value.continueCourse
    if (!course) return '还没有学习记录，可以从公开课程或我的选课开始。'
    return `${course.courseTitle || '课程'} 已完成 ${clampPercent(course.courseProgress)}%，下一步进入课程章节。`
})

const adminActions: ActionLink[] = [
    {label: '用户管理', to: '/admin/users', icon: UserCog},
    {label: '学生列表', to: '/admin/students', icon: GraduationCap},
    {label: '教师列表', to: '/admin/teachers', icon: Users},
    {label: '课程管理', to: '/course-management', icon: BookOpen},
    {label: '通知发布', to: '/notifications', icon: Megaphone},
    {label: 'AI 工作区', to: '/ai', icon: Bot},
]

const teacherProductionActions: ActionLink[] = [
    {label: '新建课程', to: '/course-management', icon: BookOpen},
    {label: '新建章节', to: '/teacher/courses?role=primary', icon: PencilLine},
    {label: '上传课件', to: '/teacher/courses?role=primary', icon: FileUp},
    {label: '创建题库', to: '/teacher/courses?role=primary', icon: Library},
    {label: 'AI 智能出题', to: '/ai', icon: Sparkles},
]

const teacherAiActions: ActionLink[] = [
    {label: '基于课程资源出题', to: '/ai', icon: Sparkles},
    {label: '总结论坛问题', to: '/ai/history', icon: ClipboardCheck},
    {label: '生成课堂练习', to: '/ai', icon: Target},
    {label: '课程知识库问答', to: '/ai/favorites', icon: Bot},
]

const studentAiActions = computed<ActionLink[]>(() => [
    {label: '继续上次对话', to: '/ai/history', icon: Bot},
    {label: '解释错题', to: studentData.value.practiceFocus.to, icon: Target},
    {label: '生成复习提纲', to: '/ai', icon: Sparkles},
    {label: '基于当前课程提问', to: studentContinueRoute.value, icon: BookOpen},
])

const PanelHeader = defineComponent({
    props: {
        title: {type: String, required: true},
        actionLabel: {type: String, default: ''},
        to: {type: String, default: ''},
    },
    setup(props) {
        return () => h('header', {class: 'panel-header'}, [
            h('h2', props.title),
            props.to
                ? h(RouterLink, {class: 'panel-link', to: props.to}, () => props.actionLabel)
                : null,
        ])
    },
})

const EmptyState = defineComponent({
    props: {
        text: {type: String, required: true},
    },
    setup(props) {
        return () => h('div', {class: 'empty-state'}, props.text)
    },
})

const NotificationRows = defineComponent({
    props: {
        notifications: {type: Array as () => Notification[], required: true},
    },
    setup(props) {
        return () => props.notifications.length
            ? h('div', {class: 'notification-rows'}, props.notifications.map(item => h('div', {
                class: ['notification-row', item.isRead ? '' : 'unread'],
                key: item.id,
            }, [
                h('span', item.type === 1 ? '教学' : '系统'),
                h('strong', item.title),
                h('small', formatDateTime(item.createdAt)),
            ])))
            : h(EmptyState, {text: '暂无通知'})
    },
})

const TimelineRows = defineComponent({
    props: {
        items: {type: Array as () => TimelineItem[], required: true},
    },
    setup(props) {
        return () => props.items.length
            ? h('div', {class: 'timeline-rows'}, props.items.map((item, index) => h('div', {
                class: ['timeline-row', item.level || 'normal'],
                key: `${item.title}-${index}`,
            }, [
                h('span', {class: 'timeline-marker'}),
                h('div', [
                    h('strong', item.title),
                    h('small', item.detail),
                ]),
                h('time', item.time),
            ])))
            : h(EmptyState, {text: '暂无待办'})
    },
})

onMounted(loadDashboard)

watch(
    () => authStore.user?.role,
    () => loadDashboard(),
)

async function loadDashboard() {
    isLoading.value = true
    try {
        const dashboard = await getMyDashboard()
        applyDashboardResponse(dashboard)
    } finally {
        isLoading.value = false
    }
}

function applyDashboardResponse(response: DashboardResponse) {
    if (response.role === 0 && response.admin) {
        applyNotifications(response.admin.notifications)
        applyAdminDashboard(response.admin)
        return
    }

    if (response.role === 2 && response.teacher) {
        applyNotifications(response.teacher.notifications)
        applyTeacherDashboard(response.teacher)
        return
    }

    if (response.role === 1 && response.student) {
        applyNotifications(response.student.notifications)
        applyStudentDashboard(response.student)
        return
    }

    applyNotifications(null)
}

function applyNotifications(summary: DashboardNotificationSummary | null) {
    unreadCount.value = {
        total: summary?.unreadTotal ?? 0,
        system: summary?.unreadSystem ?? 0,
        teaching: summary?.unreadTeaching ?? 0,
    }
    recentNotifications.value = (summary?.recent ?? []).map(toNotification)
}

function applyAdminDashboard(admin: AdminDashboard) {
    const users = admin.users ?? emptyUserSummary()
    adminData.value = {
        totalUsers: users.totalUsers,
        students: users.students,
        teachers: users.teachers,
        courses: admin.courses ?? 0,
        publishedCourses: admin.publishedCourses ?? 0,
        todayUsers: users.todayUsers,
        disabledUsers: users.disabledUsers,
        incompleteProfiles: users.incompleteProfiles,
        oauthPercent: users.oauthPercent,
        loginActivity: users.recentUsers.slice(0, 5).map(user => ({
            id: user.id,
            name: user.name || user.email || 'User',
            ip: '-',
            time: formatDateTime(user.lastLoginAt || user.createdAt),
        })),
        resources: (admin.resources ?? []).map(item => ({
            label: item.label,
            value: item.value,
            status: item.meta,
            note: item.meta,
        })),
        courseStatus: toChartData(admin.courseStatus),
        courseCapacity: admin.courseCapacity ?? [],
        activities: toTimelineItems(admin.activities ?? []),
        risks: (admin.risks ?? []).map(item => ({
            title: item.title,
            detail: item.detail,
            status: item.status,
            level: normalizeRiskLevel(item.level),
            icon: resolveRiskIcon(item.icon),
        })),
    }
}

function applyTeacherDashboard(teacher: TeacherDashboard) {
    teacherData.value = {
        primaryCourses: teacher.primaryCourses ?? [],
        assistantCourses: teacher.assistantCourses ?? [],
        courses: teacher.courses ?? [],
        sessions: teacher.sessions ?? [],
        liveSession: teacher.liveSession ?? null,
        pending: toTimelineItems(teacher.pending ?? []),
        insights: teacher.insights ?? [],
        questionCoverage: teacher.questionCoverage ?? [],
        capacityRanking: teacher.capacityRanking ?? [],
    }
}

function applyStudentDashboard(student: StudentDashboard) {
    studentData.value = {
        enrollments: student.enrollments ?? [],
        sessions: student.sessions ?? [],
        recommendations: student.recommendations ?? [],
        liveSession: student.liveSession ?? null,
        continueCourse: student.continueCourse ?? null,
        practiceFocus: student.practiceFocus ?? defaultPracticeFocus(),
        practiceSessions: student.practiceSessions ?? [],
        todos: toTimelineItems(student.todos ?? []),
    }
}

function emptyUserSummary(): DashboardUserSummary {
    return {
        totalUsers: 0,
        students: 0,
        teachers: 0,
        admins: 0,
        disabledUsers: 0,
        todayUsers: 0,
        incompleteProfiles: 0,
        oauthPercent: 0,
        roleDistribution: [],
        recentUsers: [],
    }
}

function defaultPracticeFocus() {
    return {
        title: '题库练习',
        detail: '选择一门课程后继续练习。',
        to: '/courses',
        icon: 'Target',
    }
}

function toNotification(item: DashboardNotificationItem): Notification {
    return {
        id: item.id,
        type: item.type ?? 0,
        title: item.title,
        content: '',
        senderId: null,
        targetType: 0,
        createdAt: item.createdAt,
        updatedAt: null,
        isRead: item.read,
        readAt: item.read ? item.createdAt : null,
    }
}

function toChartData(items: Array<{ label: string; value: number }> = []): ChartDatum[] {
    return items.map(item => ({label: item.label, value: item.value}))
}

function toTimelineItems(items: DashboardTimelineItem[]): TimelineItem[] {
    return items.map(item => ({
        title: item.title,
        detail: item.detail,
        time: item.time,
        level: normalizeTimelineLevel(item.level),
    }))
}

function normalizeTimelineLevel(level?: string | null): TimelineItem['level'] {
    if (level === 'warning' || level === 'critical' || level === 'live') return level
    return 'normal'
}

function normalizeRiskLevel(level?: string | null): RiskItem['level'] {
    if (level === 'warning' || level === 'critical') return level
    return 'normal'
}

function resolveRiskIcon(icon?: string | null): Component {
    if (icon === 'AlertTriangle') return AlertTriangle
    if (icon === 'Users') return Users
    return ShieldCheck
}

function donutOption(data: ChartDatum[], name: string): EChartsCoreOption {
    return {
        legend: {
            bottom: 0,
            left: 'center',
        },
        series: [
            {
                name,
                type: 'pie',
                radius: ['54%', '72%'],
                center: ['50%', '43%'],
                avoidLabelOverlap: true,
                label: {
                    color: 'inherit',
                    formatter: '{b}\n{c}',
                },
                labelLine: {
                    length: 10,
                    length2: 8,
                },
                data: data.filter(item => item.value > 0).map(item => ({
                    name: item.label,
                    value: item.value,
                })),
            },
        ],
    }
}

function barOption(labels: string[], values: number[], name: string, suffix = ''): EChartsCoreOption {
    return {
        tooltip: {
            trigger: 'axis',
            valueFormatter: (value: unknown) => `${value}${suffix}`,
        },
        xAxis: {
            type: 'category',
            data: labels,
            axisLabel: {
                color: 'inherit',
                interval: 0,
                rotate: labels.some(label => label.length > 5) ? 24 : 0,
            },
            axisLine: {
                lineStyle: {
                    color: 'rgba(255,255,255,0.16)',
                },
            },
            axisTick: {
                show: false,
            },
        },
        yAxis: {
            type: 'value',
            axisLabel: {
                color: 'inherit',
                formatter: `{value}${suffix}`,
            },
            splitLine: {
                lineStyle: {
                    color: 'rgba(255,255,255,0.08)',
                },
            },
        },
        series: [
            {
                name,
                type: 'bar',
                data: values,
                barMaxWidth: 28,
                itemStyle: {
                    borderRadius: [4, 4, 0, 0],
                },
            },
        ],
    }
}

function lineOption(labels: string[], values: number[], name: string, suffix = ''): EChartsCoreOption {
    return {
        tooltip: {
            trigger: 'axis',
            valueFormatter: (value: unknown) => `${value}${suffix}`,
        },
        xAxis: {
            type: 'category',
            boundaryGap: false,
            data: labels,
            axisLabel: {
                color: 'inherit',
            },
            axisLine: {
                lineStyle: {
                    color: 'rgba(255,255,255,0.16)',
                },
            },
            axisTick: {
                show: false,
            },
        },
        yAxis: {
            type: 'value',
            axisLabel: {
                color: 'inherit',
                formatter: `{value}${suffix}`,
            },
            splitLine: {
                lineStyle: {
                    color: 'rgba(255,255,255,0.08)',
                },
            },
        },
        series: [
            {
                name,
                type: 'line',
                smooth: true,
                symbolSize: 7,
                areaStyle: {
                    opacity: 0.12,
                },
                data: values,
            },
        ],
    }
}

function progressBuckets(values: Array<number | null | undefined>): ChartDatum[] {
    return [
        {label: '0-30%', value: values.filter(value => clampPercent(value) < 30).length},
        {label: '30-70%', value: values.filter(value => clampPercent(value) >= 30 && clampPercent(value) < 70).length},
        {label: '70-100%', value: values.filter(value => clampPercent(value) >= 70).length},
    ]
}

function capacityPercent(course: Course) {
    if (!course.maxStudents) return 0
    return clampPercent((course.currentStudents / course.maxStudents) * 100)
}

function accuracyPercent(session: PracticeSession) {
    if (!session.answeredCount) return 0
    return clampPercent((session.correctCount / session.answeredCount) * 100)
}

function shortLabel(value: string) {
    return value.length > 8 ? `${value.slice(0, 8)}…` : value
}

function clampPercent(value?: number | null) {
    if (value == null || Number.isNaN(value)) return 0
    return Math.max(0, Math.min(100, Math.round(value)))
}

function formatNumber(value: number) {
    return new Intl.NumberFormat('zh-CN').format(value)
}

function formatDateTime(value?: string | null) {
    if (!value) return '-'
    const date = new Date(value)
    if (Number.isNaN(date.getTime())) return '-'
    return new Intl.DateTimeFormat('zh-CN', {
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
    }).format(date)
}

function formatSessionRange(session: ClassSession) {
    return `${formatDateTime(session.scheduledStartAt)} - ${formatDateTime(session.scheduledEndAt)}`
}

function formatMonthDay(value?: string | null) {
    if (!value) return '-'
    const date = new Date(value)
    if (Number.isNaN(date.getTime())) return '-'
    return new Intl.DateTimeFormat('zh-CN', {
        month: '2-digit',
        day: '2-digit',
    }).format(date)
}

function formatRelativeTime(timestamp: number) {
    const diff = Date.now() - timestamp
    const minute = 60 * 1000
    const hour = 60 * minute
    const day = 24 * hour
    if (diff < hour) return `${Math.max(1, Math.round(diff / minute))} 分钟前`
    if (diff < day) return `${Math.round(diff / hour)} 小时前`
    return `${Math.round(diff / day)} 天前`
}
</script>

<style scoped>
.dashboard-workbench {
  display: flex;
  flex-direction: column;
  gap: 28px;
  max-width: 1440px;
  margin: 0 auto;
  color: var(--color-on-surface);
}

.workbench-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 32px;
  padding-bottom: 18px;
  border-bottom: 1px solid var(--color-outline-light);
}

.workbench-kicker {
  margin: 0 0 8px;
  font-family: var(--font-label);
  font-size: 12px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--color-muted);
}

.workbench-heading h1 {
  margin: 0;
  font-family: var(--font-heading);
  font-size: clamp(34px, 4vw, 56px);
  font-weight: 500;
  line-height: 1.08;
  color: var(--color-on-surface);
  text-wrap: balance;
}

.workbench-subtitle {
  max-width: 560px;
  margin: 12px 0 0;
  color: var(--color-muted);
  line-height: 1.6;
  text-wrap: pretty;
}

.identity-strip {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  min-height: 40px;
  padding: 0 12px;
  border: 1px solid var(--color-outline-light);
  color: var(--color-muted);
  white-space: nowrap;
}

.identity-role,
.status-chip {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 3px 9px;
  background: var(--color-surface-container);
  border: 1px solid var(--color-outline-light);
  color: var(--color-on-surface);
  font-family: var(--font-label);
  font-size: 12px;
  line-height: 1;
}

.status-chip.warning {
  border-color: color-mix(in srgb, var(--color-primary) 40%, var(--color-outline-light));
}

.status-chip.critical {
  border-color: var(--color-error);
  color: #ffb4ab;
}

.status-chip.live {
  background: var(--color-primary);
  color: var(--color-on-primary);
}

.dashboard-skeleton,
.metric-grid,
.admin-grid,
.teacher-grid,
.student-grid {
  display: grid;
  gap: 18px;
}

.dashboard-skeleton {
  grid-template-columns: repeat(4, 1fr);
}

.skeleton-block {
  min-height: 152px;
  border: 1px solid var(--color-outline-light);
  background: linear-gradient(90deg, var(--color-surface-card), var(--color-surface-container), var(--color-surface-card));
  background-size: 220% 100%;
  animation: dashboard-pulse 1.4s ease-in-out infinite;
}

.metric-grid--seven {
  grid-template-columns: repeat(7, minmax(0, 1fr));
}

.metric-grid--six {
  grid-template-columns: repeat(6, minmax(0, 1fr));
}

.metric-grid--five {
  grid-template-columns: repeat(5, minmax(0, 1fr));
}

.metric-cell,
.workbench-panel,
.risk-row,
.teacher-live-panel,
.student-live-panel {
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
}

.metric-cell {
  min-width: 0;
  min-height: 132px;
  padding: 18px 18px 20px;
}

.metric-cell span,
.metric-cell small {
  display: block;
  color: var(--color-muted);
  font-size: 12px;
}

.metric-cell strong {
  display: block;
  margin: 8px 0;
  font-family: var(--font-heading);
  font-size: clamp(28px, 3vw, 42px);
  font-weight: 600;
  line-height: 1;
  font-variant-numeric: tabular-nums;
}

.admin-grid,
.teacher-grid,
.student-grid {
  grid-template-columns: repeat(12, minmax(0, 1fr));
  align-items: start;
}

.teacher-grid {
  row-gap: 28px;
}

.span-4 {
  grid-column: span 4;
}

.span-5 {
  grid-column: span 5;
}

.span-6 {
  grid-column: span 6;
}

.span-7 {
  grid-column: span 7;
}

.span-8 {
  grid-column: span 8;
}

.span-12 {
  grid-column: span 12;
}

.workbench-panel {
  min-width: 0;
  padding: 22px;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 16px;
  margin-bottom: 16px;
  border-bottom: 1px solid var(--color-outline-light);
}

.panel-header h2 {
  margin: 0;
  font-family: var(--font-heading);
  font-size: 24px;
  font-weight: 500;
}

.panel-link {
  color: var(--color-muted);
  font-size: 13px;
  text-decoration: none;
}

.panel-link:hover {
  color: var(--color-primary);
}

.risk-strip {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
}

.risk-row {
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 14px;
  min-height: 82px;
  padding: 18px;
}

.risk-row strong,
.risk-row span,
.compact-row span,
.compact-row strong,
.compact-row small,
.timeline-row strong,
.timeline-row small,
.timeline-row time {
  display: block;
  min-width: 0;
}

.risk-row strong,
.timeline-row strong,
.progress-row strong,
.course-op strong,
.recent-course-row strong,
.recommend-row strong,
.session-row strong {
  color: var(--color-on-surface);
  overflow-wrap: anywhere;
}

.risk-row span,
.timeline-row small,
.compact-row small,
.progress-row span,
.course-op span,
.course-op small,
.recent-course-row span,
.recent-course-row small,
.recommend-row span,
.session-row span {
  color: var(--color-muted);
  font-size: 13px;
}

.split-board {
  display: grid;
  grid-template-columns: minmax(0, 0.9fr) minmax(0, 1.1fr);
  gap: 24px;
}

.chart-summary-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(220px, 0.8fr);
  gap: 22px;
  align-items: stretch;
}

.chart-pair {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
}

.chart-pair--wide {
  grid-template-columns: minmax(220px, 0.8fr) minmax(0, 1.2fr);
}

.ratio-stack,
.summary-stack,
.compact-list,
.notification-rows,
.timeline-rows,
.session-list,
.course-progress-list,
.recent-course-list,
.recommend-list,
.action-stack,
.insight-stack {
  display: flex;
  flex-direction: column;
}

.summary-stack {
  gap: 12px;
}

.ratio-line,
.account-flags,
.compact-row,
.notification-row,
.timeline-row,
.session-row,
.progress-row,
.recent-course-row,
.recommend-row,
.action-row,
.insight-row,
.capacity-row {
  border-bottom: 1px solid var(--color-outline-light);
}

.ratio-line,
.account-flags,
.compact-row,
.notification-row,
.session-row,
.progress-row,
.recent-course-row,
.recommend-row,
.action-row,
.insight-row,
.capacity-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 13px 0;
}

.ratio-line:first-child {
  padding-top: 0;
}

.ratio-line.muted {
  color: var(--color-muted);
}

.account-flags {
  align-items: flex-start;
  flex-direction: column;
  color: var(--color-muted);
  font-size: 13px;
}

.subtle-list {
  margin-top: 4px;
  border-top: 1px solid var(--color-outline-light);
}

.progress-track {
  height: 5px;
  overflow: hidden;
  background: var(--color-surface-canvas);
}

.progress-track span {
  display: block;
  height: 100%;
  background: var(--color-primary);
}

.action-grid,
.course-ops-grid,
.ai-command-grid,
.compact-action-grid {
  display: grid;
  gap: 12px;
}

.action-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.action-tile,
.ai-command,
.command-button,
.action-row,
.compact-action,
.capacity-row {
  color: var(--color-on-surface);
  text-decoration: none;
  transition: background 0.2s ease, color 0.2s ease, transform 0.2s ease;
}

.action-tile {
  min-height: 78px;
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 10px;
  padding: 15px;
  background: var(--color-surface-container);
  border: 1px solid var(--color-outline-light);
}

.action-tile:hover,
.action-row:hover,
.ai-command:hover,
.command-button:hover,
.session-row:hover,
.progress-row:hover,
.recent-course-row:hover,
.recommend-row:hover,
.course-op:hover,
.compact-action:hover,
.capacity-row:hover {
  background: var(--color-surface-container-high);
}

.resource-summary {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
  margin-top: 18px;
}

.summary-chip,
.summary-metric,
.insight-pill,
.compact-action {
  min-width: 0;
  background: var(--color-surface-container);
  border: 1px solid var(--color-outline-light);
}

.summary-chip {
  display: grid;
  gap: 6px;
  padding: 12px;
}

.summary-chip span,
.summary-metric span,
.summary-metric small,
.insight-pill span,
.capacity-row span {
  color: var(--color-muted);
  font-size: 12px;
}

.summary-chip strong,
.summary-metric strong,
.insight-pill strong,
.capacity-row > strong {
  font-family: var(--font-heading);
  font-variant-numeric: tabular-nums;
}

.table-head,
.table-row {
  display: grid;
  grid-template-columns: 1.4fr 0.7fr 0.8fr 1.2fr;
  gap: 12px;
  align-items: center;
  min-height: 46px;
  border-bottom: 1px solid var(--color-outline-light);
}

.table-head {
  color: var(--color-muted);
  font-size: 12px;
}

.table-row:last-child,
.ratio-line:last-child,
.account-flags:last-child,
.compact-row:last-child,
.notification-row:last-child,
.timeline-row:last-child,
.session-row:last-child,
.progress-row:last-child,
.recent-course-row:last-child,
.recommend-row:last-child,
.action-row:last-child,
.insight-row:last-child,
.capacity-row:last-child {
  border-bottom: none;
}

.teacher-live-panel,
.student-live-panel {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 32px;
  padding: 28px;
}

.teacher-live-panel h2,
.student-live-panel h2 {
  margin: 12px 0 8px;
  font-family: var(--font-heading);
  font-size: clamp(28px, 3vw, 42px);
  font-weight: 500;
}

.teacher-live-panel p,
.student-live-panel p,
.practice-focus p {
  max-width: 68ch;
  margin: 0;
  color: var(--color-muted);
  line-height: 1.6;
}

.command-button {
  min-height: 44px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 10px 14px;
  border: 1px solid var(--color-outline-light);
  background: transparent;
  font-size: 14px;
}

.command-button.primary {
  background: var(--color-primary);
  color: var(--color-on-primary);
}

.session-row,
.progress-row,
.recent-course-row,
.recommend-row,
.course-op {
  color: inherit;
  text-decoration: none;
}

.session-row {
  min-height: 58px;
}

.session-row > div {
  display: grid;
  gap: 6px;
  min-width: 0;
}

.course-ops-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.course-op {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 156px;
  padding: 18px;
  background: var(--color-surface-container);
  border: 1px solid var(--color-outline-light);
}

.course-op-main {
  display: grid;
  grid-template-columns: minmax(0, 1fr) max-content;
  align-items: start;
  gap: 14px;
}

.course-op-main strong {
  line-height: 1.45;
}

.course-op .course-op-percent {
  color: var(--color-on-surface);
  font-family: var(--font-heading);
  font-size: 20px;
  font-variant-numeric: tabular-nums;
}

.course-op .progress-track {
  height: 6px;
}

.course-op-stats {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-top: auto;
}

.course-op-stat {
  display: grid;
  gap: 4px;
  min-width: 0;
  padding-top: 12px;
  border-top: 1px solid var(--color-outline-light);
}

.course-op .course-op-stat-value {
  color: var(--color-on-surface);
  font-size: 15px;
  font-weight: 650;
  font-variant-numeric: tabular-nums;
}

.course-op .course-op-stat-label {
  color: var(--color-muted);
  font-size: 12px;
}

.insight-inline {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-top: 18px;
}

.insight-pill {
  display: grid;
  gap: 6px;
  padding: 12px;
}

.summary-metric {
  display: grid;
  gap: 5px;
  padding: 14px;
}

.summary-metric strong {
  font-size: 28px;
  line-height: 1;
}

.compact-action-grid {
  grid-template-columns: 1fr;
}

.compact-action {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 48px;
  padding: 12px;
}

.capacity-list {
  display: flex;
  flex-direction: column;
}

.capacity-row {
  min-height: 58px;
}

.capacity-row div {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.capacity-row strong {
  overflow-wrap: anywhere;
}

.insight-row strong {
  font-family: var(--font-heading);
  font-size: 28px;
  font-variant-numeric: tabular-nums;
}

.timeline-row {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) minmax(92px, auto);
  align-items: center;
  gap: 12px;
  min-height: 58px;
  padding: 12px 0;
}

.timeline-marker {
  width: 8px;
  height: 8px;
  border: 1px solid var(--color-primary);
}

.timeline-row.warning .timeline-marker,
.timeline-row.critical .timeline-marker {
  border-color: var(--color-error);
}

.timeline-row.live .timeline-marker {
  background: var(--color-primary);
}

.timeline-row time {
  color: var(--color-muted);
  font-size: 12px;
  white-space: nowrap;
}

.pending-timeline :deep(.timeline-row) {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) max-content;
  align-items: start;
  gap: 14px;
  min-height: 0;
  padding: 14px 0;
  border-bottom: 1px solid var(--color-outline-light);
}

.pending-timeline :deep(.timeline-row:last-child) {
  padding-bottom: 0;
  border-bottom: none;
}

.pending-timeline :deep(.timeline-marker) {
  width: 8px;
  height: 8px;
  margin-top: 6px;
  border: 1px solid var(--color-primary);
}

.pending-timeline :deep(.timeline-row.warning .timeline-marker),
.pending-timeline :deep(.timeline-row.critical .timeline-marker) {
  border-color: var(--color-error);
}

.pending-timeline :deep(.timeline-row.live .timeline-marker) {
  background: var(--color-primary);
}

.pending-timeline :deep(.timeline-row > div) {
  display: grid;
  gap: 5px;
  min-width: 0;
}

.pending-timeline :deep(.timeline-row strong),
.pending-timeline :deep(.timeline-row small),
.pending-timeline :deep(.timeline-row time) {
  display: block;
  min-width: 0;
  line-height: 1.45;
}

.pending-timeline :deep(.timeline-row strong) {
  color: var(--color-on-surface);
  font-size: 14px;
  font-weight: 650;
  overflow-wrap: anywhere;
}

.pending-timeline :deep(.timeline-row small) {
  color: var(--color-muted);
  font-size: 13px;
  overflow-wrap: anywhere;
}

.pending-timeline :deep(.timeline-row time) {
  color: var(--color-muted);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.ai-command-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.ai-command {
  min-height: 88px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 12px;
  padding: 16px;
  background: var(--color-surface-container);
  border: 1px solid var(--color-outline-light);
}

.practice-focus {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.practice-focus strong {
  font-family: var(--font-heading);
  font-size: 24px;
  font-weight: 500;
}

.progress-row {
  display: grid;
  grid-template-columns: minmax(0, 1.3fr) minmax(120px, 0.8fr) auto;
}

.empty-state {
  display: grid;
  min-height: 104px;
  padding: 18px;
  place-items: center;
  border: 1px dashed var(--color-outline-light);
  color: var(--color-muted);
  text-align: center;
}

@keyframes dashboard-pulse {
  0% {
    background-position: 220% 0;
  }
  100% {
    background-position: -220% 0;
  }
}

@media (max-width: 1280px) {
  .metric-grid--seven,
  .metric-grid--six,
  .metric-grid--five,
  .risk-strip {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .span-4,
  .span-5,
  .span-6,
  .span-7,
  .span-8 {
    grid-column: span 6;
  }
}

@media (max-width: 860px) {
  .dashboard-workbench {
    gap: 20px;
  }

  .workbench-heading,
  .teacher-live-panel,
  .student-live-panel {
    align-items: stretch;
    flex-direction: column;
    gap: 18px;
  }

  .workbench-heading {
    padding-bottom: 16px;
  }

  .dashboard-skeleton,
  .metric-grid--seven,
  .metric-grid--six,
  .metric-grid--five,
  .risk-strip,
  .action-grid,
  .course-ops-grid,
  .ai-command-grid,
  .chart-summary-layout,
  .chart-pair,
  .chart-pair--wide,
  .resource-summary,
  .insight-inline,
  .split-board {
    grid-template-columns: 1fr;
  }

  .span-4,
  .span-5,
  .span-6,
  .span-7,
  .span-8,
  .span-12 {
    grid-column: span 12;
  }

  .table-head {
    display: none;
  }

  .table-row,
  .progress-row,
  .timeline-row {
    grid-template-columns: 1fr;
    align-items: start;
  }

  .identity-strip {
    width: 100%;
    justify-content: space-between;
  }

  .metric-cell,
  .workbench-panel,
  .risk-row,
  .teacher-live-panel,
  .student-live-panel {
    padding: 16px;
  }
}
</style>
