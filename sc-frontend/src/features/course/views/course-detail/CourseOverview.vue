<template>
  <section class="tab-panel course-overview-workbench">
    <section class="overview-ledger">
      <div class="overview-copy">
        <span class="section-kicker">Course status</span>
        <h2>{{ course.title }}</h2>
        <p>{{ course.description || '暂未添加课程简介。' }}</p>
      </div>

      <dl class="overview-facts">
        <div v-for="fact in overviewFacts" :key="fact.label">
          <dt>{{ fact.label }}</dt>
          <dd>{{ fact.value }}</dd>
        </div>
      </dl>

      <div class="progress-block">
        <div class="progress-header">
          <span>学习进度 · {{ progressRatioLabel }}</span>
          <strong>{{ progressPercent }}%</strong>
        </div>
        <div class="progress-track">
          <span :style="{width: `${progressPercent}%`}"></span>
        </div>
      </div>
    </section>

    <section class="next-action-panel">
      <div class="next-action-main">
        <span class="section-kicker">Next action</span>
        <h3>{{ nextActionTitle }}</h3>
        <p>{{ nextActionDescription }}</p>

        <div v-if="canManageCourse" class="action-grid">
          <button class="primary-command" type="button" @click="emit('openChapterEditor')">
            <Plus :size="15" stroke-width="1.8"/>
            创建章节
          </button>
          <button class="secondary-command" type="button" @click="emit('openClassSessionCreator')">
            <Presentation :size="15" stroke-width="1.8"/>
            管理课堂
          </button>
          <button class="secondary-command" type="button" @click="navigateTo('course-banks')">
            <Database :size="15" stroke-width="1.8"/>
            管理题库
          </button>
          <button class="secondary-command" type="button" @click="navigateTo('course-files')">
            <FileDown :size="15" stroke-width="1.8"/>
            上传资料
          </button>
        </div>

        <div v-else-if="showEnrollAction" class="action-grid">
          <button
              :disabled="enrollDisabled"
              class="primary-command"
              type="button"
              @click="emit('enroll')"
          >
            {{ enrollActionLabel }}
          </button>
          <button class="secondary-command" type="button" @click="navigateTo('course-chapters')">
            先看课程结构
          </button>
        </div>

        <div v-else-if="canAccessCourseContent" class="action-grid">
          <button class="primary-command" type="button" @click="continueLearning">
            <BookOpen :size="15" stroke-width="1.8"/>
            继续学习
          </button>
          <button
              :disabled="!canEnterClassSessions"
              class="secondary-command"
              type="button"
              @click="navigateTo('course-class-sessions')"
          >
            <Presentation :size="15" stroke-width="1.8"/>
            进入课堂
          </button>
          <button class="secondary-command" type="button" @click="navigateTo('course-banks')">
            <Database :size="15" stroke-width="1.8"/>
            查看题库
          </button>
        </div>

        <div v-else class="action-grid">
          <button class="primary-command" disabled type="button">选课后访问</button>
          <button class="secondary-command" type="button" @click="navigateTo('course-chapters')">
            查看可公开内容
          </button>
        </div>
      </div>

      <div class="role-summary">
        <div v-for="item in roleSummaries" :key="item.label" class="role-row">
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
        </div>
      </div>
    </section>

    <section class="navigation-section">
      <div class="section-heading">
        <div>
          <span class="section-kicker">Course navigation</span>
          <h3>课程功能导航</h3>
        </div>
        <p>每个入口只跳转到已有功能页，并显示当前可用状态。</p>
      </div>

      <div class="navigation-grid">
        <article
            v-for="entry in navigationEntries"
            :key="entry.key"
            :class="{unavailable: !entry.available}"
            class="navigation-entry"
        >
          <div class="entry-icon">
            <component :is="entry.icon" :size="17" stroke-width="1.7"/>
          </div>
          <div class="entry-copy">
            <div class="entry-title-row">
              <h4>{{ entry.title }}</h4>
              <span :class="{muted: !entry.available}" class="entry-state">{{ entry.reason }}</span>
            </div>
            <p>{{ entry.description }}</p>
            <strong>{{ entry.summary }}</strong>
          </div>
          <button
              :disabled="!entry.available"
              class="entry-action"
              type="button"
              @click="handleEntryAction(entry)"
          >
            {{ entry.actionLabel }}
          </button>
        </article>
      </div>
    </section>

    <section class="teaching-team-summary">
      <div>
        <span class="section-kicker">{{ t('courseDetail.teachingTeam') }}</span>
        <h3>教学团队摘要</h3>
        <p>教师信息保留为辅助摘要，主体操作已前移到课程导航。</p>
      </div>

      <div class="team-row">
        <UserAvatarLink
            :avatar-url="course.teacherAvatar"
            :display-name="course.teacherName || t('courseDetail.unknownTeacher')"
            :role="2"
            :show-name="false"
            :user-id="course.teacherId"
            size="large"
        />
        <div class="team-copy">
          <strong>{{ course.teacherName || t('courseDetail.unknownTeacher') }}</strong>
          <span>{{ t('courseDetail.primaryInstructor') }}</span>
        </div>
      </div>

      <div class="team-metrics">
        <div>
          <span>{{ t('courseDetail.assistantsTab') }}</span>
          <strong>{{ assistants.length }}</strong>
        </div>
        <div>
          <span>答疑安排</span>
          <strong>以课程公告为准</strong>
        </div>
      </div>
    </section>
  </section>
</template>

<script lang="ts" setup>
import {computed} from 'vue'
import type {Component} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import {
  BookOpen,
  ClipboardList,
  Database,
  FileDown,
  MessageCircle,
  Plus,
  Presentation,
  UserCheck,
  Users,
} from 'lucide-vue-next'
import type {Chapter} from '@/features/course/types/chapter'
import {ClassSessionStatus, type ClassSession} from '@/features/course/types/classSession'
import type {CourseDetail, CourseFile, Enrollment} from '@/features/course/types/course'
import type {QuestionBank} from '@/features/question-bank/types/questionBank'
import UserAvatarLink from '@/shared/components/UserAvatarLink.vue'

type TeacherInfo = NonNullable<CourseDetail['teacherInfos']>[number]
type EntryAction = 'route' | 'createChapter'

interface NavigationEntry {
  key: string
  title: string
  description: string
  summary: string
  actionLabel: string
  available: boolean
  reason: string
  icon: Component
  action: EntryAction
  routeName?: string
}

const props = withDefaults(defineProps<{
  course: CourseDetail
  courseId: string
  chapterTree: Chapter[]
  chaptersLoading?: boolean
  classSessions: ClassSession[]
  classSessionsLoaded?: boolean
  classSessionsLoading?: boolean
  banks: QuestionBank[]
  banksLoaded?: boolean
  banksTotal?: number
  files: CourseFile[]
  filesLoaded?: boolean
  filesTotal?: number
  students: Enrollment[]
  studentsLoaded?: boolean
  studentsTotal?: number
  assistants: TeacherInfo[]
  canManageCourse: boolean
  canAccessCourseContent: boolean
  canEnterClassSessions: boolean
  canViewLivePractices: boolean
  canViewStudents: boolean
  canViewAssistants: boolean
  isStudent: boolean
  isAdmin: boolean
  enrolling?: boolean
}>(), {
  chaptersLoading: false,
  classSessionsLoaded: false,
  classSessionsLoading: false,
  banksLoaded: false,
  banksTotal: 0,
  filesLoaded: false,
  filesTotal: 0,
  studentsLoaded: false,
  studentsTotal: 0,
  enrolling: false,
})

const emit = defineEmits<{
  enroll: []
  openChapterEditor: []
  openClassSessionCreator: []
  selectChapter: [chapter: Chapter]
}>()

const {t} = useI18n()
const router = useRouter()

function flattenChapters(chapters: Chapter[]): Chapter[] {
  return chapters.flatMap(chapter => [chapter, ...flattenChapters(chapter.children || [])])
}

function clampPercent(value: number | null | undefined) {
  const numberValue = Number(value ?? 0)
  if (Number.isNaN(numberValue)) return 0
  return Math.max(0, Math.min(100, Math.round(numberValue)))
}

const flatChapters = computed(() => flattenChapters(props.chapterTree))
const publishedChapters = computed(() => flatChapters.value.filter(chapter => chapter.status === 1))
const firstOpenChapter = computed(() => {
  if (props.canManageCourse) return flatChapters.value[0] || null
  return publishedChapters.value[0] || null
})
const isPublished = computed(() => props.course.status === 1)
const isFull = computed(() => props.course.maxStudents > 0 && props.course.currentStudents >= props.course.maxStudents)
const showEnrollAction = computed(() => props.isStudent && !props.course.enrolled)
const enrollDisabled = computed(() => props.enrolling || !isPublished.value || isFull.value)
const bankCount = computed(() => props.banksLoaded ? props.banksTotal : props.banks.length)
const fileCount = computed(() => props.filesLoaded ? props.filesTotal : props.files.length)
const studentCount = computed(() => props.studentsLoaded ? props.studentsTotal : props.course.currentStudents)
const hasCourseProgressStats = computed(() =>
  props.course.publishedClassSessionCount !== undefined && props.course.publishedClassSessionCount !== null,
)
const countedClassSessionCount = computed(() => {
  if (hasCourseProgressStats.value) return props.course.publishedClassSessionCount
  if (!props.classSessionsLoaded || props.classSessionsLoading) return 0
  return props.classSessions.filter(session => session.status !== ClassSessionStatus.PREPARING).length
})
const totalClassHours = computed(() => Math.max(0, props.course.totalClassHours || 0))
const progressPercent = computed(() => {
  if (props.course.courseProgress !== undefined && props.course.courseProgress !== null) {
    return clampPercent(props.course.courseProgress)
  }
  if (!props.classSessionsLoaded || props.classSessionsLoading) return 0
  if (totalClassHours.value === 0) return 0
  return clampPercent((countedClassSessionCount.value / totalClassHours.value) * 100)
})
const progressRatioLabel = computed(() => {
  if (!hasCourseProgressStats.value && (!props.classSessionsLoaded || props.classSessionsLoading)) return '统计中'
  if (totalClassHours.value === 0) return `${countedClassSessionCount.value} / ${t('courseDetail.toBeArranged')}`
  return `${countedClassSessionCount.value} / ${totalClassHours.value} ${t('courseDetail.classHoursUnit')}`
})

const enrollActionLabel = computed(() => {
  if (props.enrolling) return '选课中...'
  if (!isPublished.value) return '暂未开放'
  if (isFull.value) return '名额已满'
  return '选课'
})

const statusLabel = computed(() => {
  if (props.course.status === 1) return '已发布'
  if (props.course.status === 2) return '已归档'
  return '草稿'
})

const visibilityLabel = computed(() => props.course.isPublic === 1 ? '公开课程' : '私有课程')
const totalClassHoursLabel = computed(() => {
  if (!props.course.totalClassHours || props.course.totalClassHours <= 0) return t('courseDetail.toBeArranged')
  return `${props.course.totalClassHours} ${t('courseDetail.classHoursUnit')}`
})
const capacityLabel = computed(() => {
  if (props.course.maxStudents <= 0) return `${props.course.currentStudents} / ${t('courseDetail.unlimited')}`
  return `${props.course.currentStudents} / ${props.course.maxStudents}`
})

const overviewFacts = computed(() => [
  {label: t('courseDetail.instructor'), value: props.course.teacherName || t('courseDetail.unknownTeacher')},
  {label: t('courseDetail.semester'), value: props.course.semester || t('courseDetail.toBeArranged')},
  {label: t('courseDetail.location'), value: props.course.location || t('courseDetail.toBeArranged')},
  {label: t('courseDetail.totalClassHours'), value: totalClassHoursLabel.value},
  {label: t('courseDetail.access'), value: visibilityLabel.value},
  {label: t('courseDetail.status'), value: statusLabel.value},
  {label: t('courseDetail.capacity'), value: capacityLabel.value},
  {
    label: '已上课时',
    value: hasCourseProgressStats.value || (props.classSessionsLoaded && !props.classSessionsLoading)
      ? `${countedClassSessionCount.value} ${t('courseDetail.classHoursUnit')}`
      : '统计中',
  },
])

const nextActionTitle = computed(() => {
  if (props.canManageCourse) return '维护课程内容'
  if (showEnrollAction.value) return '加入课程'
  if (props.canAccessCourseContent) return '继续学习'
  return '选课后解锁内容'
})

const nextActionDescription = computed(() => {
  if (props.canManageCourse) return '创建章节、管理课堂、维护题库和资料，让学生进入清晰的学习路径。'
  if (showEnrollAction.value) return '选课后可访问章节、讨论、题库和课程资料。'
  if (props.canAccessCourseContent) return '从章节、课堂或题库继续推进当前课程。'
  return '当前账号暂不能访问课程内容，完成选课或等待课程公开后再进入。'
})

const roleSummaries = computed(() => [
  {
    label: '学生状态',
    value: props.course.enrolled ? '已选课' : props.isStudent ? '未选课' : '非学生账号',
  },
  {
    label: '教学权限',
    value: props.canManageCourse ? props.isAdmin ? '管理员可管理' : '教学团队可管理' : '不可管理',
  },
  {
    label: '内容访问',
    value: props.canAccessCourseContent ? '可访问' : '选课后可访问',
  },
])

const chapterSummary = computed(() => {
  if (props.chaptersLoading) return '正在加载章节'
  if (flatChapters.value.length === 0) {
    return props.canManageCourse ? '暂无章节，可创建第一个章节' : '等待教师发布章节'
  }
  return `${flatChapters.value.length} 个章节 · ${publishedChapters.value.length} 个已发布`
})

const banksSummary = computed(() => {
  if (!props.banksLoaded) return '进入后查看题库数量'
  if (bankCount.value === 0) return props.canManageCourse ? '暂无题库，可新建' : '等待教师发布题库'
  return `${bankCount.value} 个题库`
})

const filesSummary = computed(() => {
  if (!props.filesLoaded) return '进入后查看资料数量'
  if (fileCount.value === 0) return props.canManageCourse ? '暂无资料，可上传' : '等待教师上传资料'
  return `${fileCount.value} 份资料`
})

const navigationEntries = computed<NavigationEntry[]>(() => {
  const entries: NavigationEntry[] = [
    {
      key: 'chapters',
      title: '章节学习',
      description: '查看课程章节与学习材料。',
      summary: chapterSummary.value,
      actionLabel: flatChapters.value.length === 0 && props.canManageCourse ? '创建章节' : '进入章节',
      available: props.canManageCourse || (props.canAccessCourseContent && publishedChapters.value.length > 0),
      reason: flatChapters.value.length === 0
        ? props.canManageCourse ? '可创建' : '等待发布'
        : props.canAccessCourseContent || props.canManageCourse ? '可用' : '选课后可用',
      icon: BookOpen,
      action: flatChapters.value.length === 0 && props.canManageCourse ? 'createChapter' : 'route',
      routeName: 'course-chapters',
    },
    {
      key: 'class-sessions',
      title: '课堂安排 / 3D 教室',
      description: '查看课堂安排并进入 3D 教室。',
      summary: countedClassSessionCount.value > 0
        ? `已上 ${countedClassSessionCount.value} ${t('courseDetail.classHoursUnit')}`
        : props.canManageCourse ? '还未开始上课，可创建安排' : '等待教师开课',
      actionLabel: '进入课堂',
      available: props.canEnterClassSessions,
      reason: props.canEnterClassSessions ? '可用' : '选课后可用',
      icon: Presentation,
      action: 'route',
      routeName: 'course-class-sessions',
    },
    {
      key: 'forums',
      title: '讨论区',
      description: '课程讨论、提问与助教答疑。',
      summary: '进入后查看主题与未读',
      actionLabel: '进入讨论',
      available: props.canAccessCourseContent,
      reason: props.canAccessCourseContent ? '可用' : '选课后可用',
      icon: MessageCircle,
      action: 'route',
      routeName: 'course-forums',
    },
  ]

  entries.push({
    key: 'banks',
    title: props.canManageCourse ? '题库管理' : '课程题库',
    description: props.canManageCourse ? '维护课程题库与题目内容。' : '查看课程题库中的题目内容。',
    summary: banksSummary.value,
    actionLabel: props.canManageCourse ? '管理题库' : '查看题库',
    available: props.canManageCourse || (props.canAccessCourseContent && (!props.banksLoaded || bankCount.value > 0)),
    reason: props.canManageCourse
      ? '可管理'
      : props.canAccessCourseContent && (!props.banksLoaded || bankCount.value > 0) ? '可查看' : '等待发布',
    icon: Database,
    action: 'route',
    routeName: 'course-banks',
  })

  if (props.canViewLivePractices) {
    entries.push({
      key: 'live-practices',
      title: '直播练习',
      description: '查看实时练习活动与提交反馈。',
      summary: '当前无进行中活动',
      actionLabel: '查看活动',
      available: props.canAccessCourseContent,
      reason: props.canAccessCourseContent ? '可用' : '当前不可见',
      icon: ClipboardList,
      action: 'route',
      routeName: 'course-live-practices',
    })
  }

  entries.push({
    key: 'files',
    title: '课程资料',
    description: '查看讲义、模板与参考资料。',
    summary: filesSummary.value,
    actionLabel: props.canManageCourse ? '上传资料' : '查看资料',
    available: props.canManageCourse || (props.canAccessCourseContent && (!props.filesLoaded || fileCount.value > 0)),
    reason: props.canManageCourse
      ? '可管理'
      : props.canAccessCourseContent && (!props.filesLoaded || fileCount.value > 0) ? '可用' : '等待上传',
    icon: FileDown,
    action: 'route',
    routeName: 'course-files',
  })

  if (props.canViewStudents) {
    entries.push({
      key: 'students',
      title: '学生名单',
      description: '查看已加入课程的学生与学习状态。',
      summary: `${studentCount.value} 名学生`,
      actionLabel: '查看名单',
      available: true,
      reason: props.canManageCourse ? '教学团队可见' : '公开课程可见',
      icon: Users,
      action: 'route',
      routeName: 'course-students',
    })
  }

  if (props.canViewAssistants) {
    entries.push({
      key: 'assistants',
      title: '助教团队',
      description: '查看参与课程支持的助教成员。',
      summary: `${props.assistants.length} 名助教`,
      actionLabel: '查看团队',
      available: true,
      reason: props.canManageCourse ? '教学团队可见' : '公开课程可见',
      icon: UserCheck,
      action: 'route',
      routeName: 'course-assistants',
    })
  }

  return entries
})

function navigateTo(routeName: string) {
  router.push({name: routeName, params: {id: props.courseId}})
}

function continueLearning() {
  if (firstOpenChapter.value && props.canAccessCourseContent) {
    emit('selectChapter', firstOpenChapter.value)
    return
  }
  navigateTo('course-chapters')
}

function handleEntryAction(entry: NavigationEntry) {
  if (!entry.available) return
  if (entry.action === 'createChapter') {
    emit('openChapterEditor')
    return
  }
  if (entry.routeName) navigateTo(entry.routeName)
}
</script>

<style scoped>
.course-overview-workbench {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}

.overview-ledger,
.next-action-panel,
.navigation-section,
.teaching-team-summary {
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: 0;
}

.overview-ledger {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(320px, 0.85fr);
  gap: 0;
}

.overview-copy {
  padding: var(--space-md);
  border-right: 1px solid var(--color-outline-light);
}

.section-kicker,
.overview-facts dt,
.progress-header span,
.role-row span,
.entry-state,
.team-metrics span,
.section-heading p {
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 12px;
  font-weight: 400;
  letter-spacing: 0.05em;
  line-height: 1;
  text-transform: uppercase;
}

.overview-copy h2,
.section-heading h3,
.teaching-team-summary h3 {
  margin: var(--space-xs) 0 0;
  color: var(--color-on-surface);
  font-family: var(--font-heading);
  font-size: 28px;
  font-weight: 400;
  line-height: 1.3;
}

.overview-copy p,
.next-action-main p,
.entry-copy p,
.teaching-team-summary p {
  margin: var(--space-sm) 0 0;
  color: var(--color-on-surface-variant);
  font-family: var(--font-body);
  font-size: 14px;
  line-height: 1.55;
}

.overview-facts {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin: 0;
  border-bottom: 1px solid var(--color-outline-light);
}

.overview-facts div {
  min-width: 0;
  padding: var(--space-sm);
  border-right: 1px solid var(--color-outline-light);
  border-bottom: 1px solid var(--color-outline-light);
}

.overview-facts div:nth-child(2n) {
  border-right: 0;
}

.overview-facts div:nth-last-child(-n + 2) {
  border-bottom: 0;
}

.overview-facts dt,
.overview-facts dd {
  margin: 0;
}

.overview-facts dd {
  margin-top: var(--space-xs);
  overflow-wrap: anywhere;
  color: var(--color-on-surface);
  font-family: var(--font-body);
  font-size: 13px;
  font-weight: 700;
  line-height: 1.35;
}

.progress-block {
  grid-column: 2;
  padding: var(--space-sm);
}

.progress-header {
  display: flex;
  justify-content: space-between;
  gap: var(--space-sm);
  margin-bottom: var(--space-xs);
}

.progress-header strong {
  color: var(--color-on-surface);
  font-family: var(--font-body);
  font-size: 13px;
  font-variant-numeric: tabular-nums;
}

.progress-track {
  height: 4px;
  overflow: hidden;
  background: var(--color-surface-container-high);
  border-radius: 0;
}

.progress-track span {
  display: block;
  height: 100%;
  background: var(--color-primary);
}

.next-action-panel {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 260px;
}

.next-action-main {
  min-width: 0;
  padding: var(--space-md);
  border-right: 1px solid var(--color-outline-light);
}

.next-action-main h3 {
  margin: var(--space-xs) 0 0;
  color: var(--color-on-surface);
  font-family: var(--font-heading);
  font-size: 24px;
  font-weight: 400;
  line-height: 1.3;
}

.action-grid {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-sm);
  margin-top: var(--space-md);
}

.primary-command,
.secondary-command,
.entry-action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  min-height: 38px;
  padding: 0 var(--space-sm);
  border-radius: 0;
  cursor: pointer;
  font-family: var(--font-label);
  font-size: 12px;
  font-weight: 800;
  text-decoration: none;
  transition: background 0.2s ease, border-color 0.2s ease, color 0.2s ease, opacity 0.2s ease, transform 0.2s ease;
}

.primary-command {
  background: var(--color-primary);
  border: 1px solid var(--color-primary);
  color: var(--color-on-primary);
}

.secondary-command,
.entry-action {
  background: transparent;
  border: 1px solid var(--color-outline-light);
  color: var(--color-on-surface);
}

.primary-command:hover:not(:disabled) {
  background: var(--color-primary-soft);
  border-color: var(--color-primary-soft);
}

.secondary-command:hover:not(:disabled),
.entry-action:hover:not(:disabled) {
  background: var(--color-surface-container-high);
  border-color: var(--color-outline);
}

.primary-command:active,
.secondary-command:active,
.entry-action:active {
  transform: translateY(1px);
}

.primary-command:disabled,
.secondary-command:disabled,
.entry-action:disabled {
  cursor: not-allowed;
  opacity: 0.46;
}

.role-summary {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.role-row {
  display: grid;
  gap: var(--space-xs);
  padding: var(--space-sm);
  border-bottom: 1px solid var(--color-outline-light);
}

.role-row:last-child {
  border-bottom: 0;
}

.role-row strong {
  color: var(--color-on-surface);
  font-family: var(--font-body);
  font-size: 13px;
  line-height: 1.35;
}

.navigation-section {
  padding: var(--space-md);
}

.section-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-md);
  padding-bottom: var(--space-md);
  border-bottom: 1px solid var(--color-outline-light);
}

.section-heading p {
  max-width: 34ch;
  margin: 0;
  letter-spacing: 0;
  line-height: 1.35;
  text-align: right;
  text-transform: none;
}

.navigation-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  border-top: 1px solid var(--color-outline-light);
  border-left: 1px solid var(--color-outline-light);
}

.navigation-entry {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr);
  gap: var(--space-sm);
  align-items: start;
  min-width: 0;
  padding: var(--space-sm);
  border-right: 1px solid var(--color-outline-light);
  border-bottom: 1px solid var(--color-outline-light);
}

.navigation-entry.unavailable {
  opacity: 0.68;
}

.entry-icon {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  background: var(--color-surface-container);
  border: 1px solid var(--color-outline-light);
  border-radius: 0;
  color: var(--color-muted);
}

.entry-copy {
  min-width: 0;
}

.entry-title-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-sm);
}

.entry-title-row h4 {
  margin: 0;
  overflow-wrap: anywhere;
  color: var(--color-on-surface);
  font-family: var(--font-body);
  font-size: 15px;
  font-weight: 800;
  line-height: 1.25;
}

.entry-state {
  flex: 0 0 auto;
  padding: 4px 7px;
  background: var(--color-surface-container-high);
  border: 1px solid var(--color-outline-light);
  border-radius: 0;
  font-size: 10px;
}

.entry-state.muted {
  color: var(--color-on-surface-variant);
}

.entry-copy p {
  display: -webkit-box;
  overflow: hidden;
  margin-top: var(--space-xs);
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.entry-copy strong {
  display: block;
  margin-top: var(--space-sm);
  color: var(--color-on-surface);
  font-family: var(--font-body);
  font-size: 13px;
  font-weight: 700;
  line-height: 1.35;
}

.entry-action {
  grid-column: 2;
  justify-self: start;
  min-height: 34px;
  margin-top: var(--space-xs);
}

.teaching-team-summary {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(180px, 240px) minmax(220px, 280px);
  gap: var(--space-md);
  align-items: center;
  padding: var(--space-md);
}

.team-row {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: var(--space-sm);
}

.team-copy {
  min-width: 0;
}

.team-copy strong {
  display: block;
  overflow: hidden;
  color: var(--color-on-surface);
  font-family: var(--font-body);
  font-size: 14px;
  font-weight: 800;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.team-copy span {
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 13px;
}

.team-metrics {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  border-top: 1px solid var(--color-outline-light);
  border-left: 1px solid var(--color-outline-light);
}

.team-metrics div {
  min-width: 0;
  padding: var(--space-sm);
  border-right: 1px solid var(--color-outline-light);
  border-bottom: 1px solid var(--color-outline-light);
}

.team-metrics strong {
  display: block;
  margin-top: var(--space-xs);
  overflow-wrap: anywhere;
  color: var(--color-on-surface);
  font-family: var(--font-body);
  font-size: 13px;
  font-weight: 700;
  line-height: 1.35;
}

@media (max-width: 1180px) {
  .overview-ledger,
  .next-action-panel,
  .teaching-team-summary {
    grid-template-columns: 1fr;
  }

  .overview-copy,
  .next-action-main {
    border-right: 0;
    border-bottom: 1px solid var(--color-outline-light);
  }

  .progress-block {
    grid-column: auto;
  }
}

@media (max-width: 760px) {
  .overview-facts,
  .navigation-grid,
  .team-metrics {
    grid-template-columns: 1fr;
  }

  .overview-facts div,
  .overview-facts div:nth-child(2n),
  .overview-facts div:nth-last-child(-n + 2) {
    border-right: 0;
    border-bottom: 1px solid var(--color-outline-light);
  }

  .overview-facts div:last-child {
    border-bottom: 0;
  }

  .section-heading {
    align-items: flex-start;
    flex-direction: column;
  }

  .section-heading p {
    max-width: none;
    text-align: left;
  }

  .navigation-entry {
    grid-template-columns: 30px minmax(0, 1fr);
  }

  .entry-title-row {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
