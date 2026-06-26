<template>
  <section class="tab-panel course-overview-workbench">
    <section class="overview-ledger">
      <div class="overview-copy">
        <span class="section-kicker">{{ t('courseDetail.overview.courseStatusKicker') }}</span>
        <h2>{{ course.title }}</h2>
        <p>{{ course.description || t('courseDetail.noDescription') }}</p>
      </div>

      <dl class="overview-facts">
        <div v-for="fact in overviewFacts" :key="fact.label">
          <dt>{{ fact.label }}</dt>
          <dd>{{ fact.value }}</dd>
        </div>
      </dl>

      <div class="progress-block">
        <div class="progress-header">
          <span>{{ t('courseDetail.overview.learningProgress', {progress: progressRatioLabel}) }}</span>
          <strong>{{ progressPercent }}%</strong>
        </div>
        <div class="progress-track">
          <span :style="{width: `${progressPercent}%`}"></span>
        </div>
      </div>
    </section>

    <section class="next-action-panel">
      <div class="next-action-main">
        <span class="section-kicker">{{ t('courseDetail.overview.nextActionKicker') }}</span>
        <h3>{{ nextActionTitle }}</h3>
        <p>{{ nextActionDescription }}</p>

        <div v-if="canManageCourse" class="action-grid">
          <button class="primary-command" type="button" @click="emit('openChapterEditor')">
            <Plus :size="15" stroke-width="1.8"/>
            {{ t('courseDetail.overview.createChapter') }}
          </button>
          <button class="secondary-command" type="button" @click="emit('openClassSessionCreator')">
            <Presentation :size="15" stroke-width="1.8"/>
            {{ t('courseDetail.overview.manageClasses') }}
          </button>
          <button class="secondary-command" type="button" @click="navigateTo('course-banks')">
            <Database :size="15" stroke-width="1.8"/>
            {{ t('courseDetail.overview.manageBanks') }}
          </button>
          <button class="secondary-command" type="button" @click="navigateTo('course-files')">
            <FileDown :size="15" stroke-width="1.8"/>
            {{ t('courseDetail.overview.uploadFiles') }}
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
            {{ t('courseDetail.overview.previewStructure') }}
          </button>
        </div>

        <div v-else-if="canAccessCourseContent" class="action-grid">
          <button class="primary-command" type="button" @click="continueLearning">
            <BookOpen :size="15" stroke-width="1.8"/>
            {{ t('courseDetail.overview.continueLearning') }}
          </button>
          <button
              :disabled="!canEnterClassSessions"
              class="secondary-command"
              type="button"
              @click="navigateTo('course-class-sessions')"
          >
            <Presentation :size="15" stroke-width="1.8"/>
            {{ t('courseDetail.overview.enterClassroom') }}
          </button>
          <button class="secondary-command" type="button" @click="navigateTo('course-banks')">
            <Database :size="15" stroke-width="1.8"/>
            {{ t('courseDetail.overview.viewBanks') }}
          </button>
        </div>

        <div v-else class="action-grid">
          <button class="primary-command" disabled type="button">{{ t('courseDetail.overview.lockedUntilEnrolled') }}</button>
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
          <span class="section-kicker">{{ t('courseDetail.overview.courseNavigationKicker') }}</span>
          <h3>{{ t('courseDetail.overview.courseNavigationTitle') }}</h3>
        </div>
        <p>{{ t('courseDetail.overview.courseNavigationDescription') }}</p>
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
        <h3>{{ t('courseDetail.overview.teachingTeamSummaryTitle') }}</h3>
        <p>{{ t('courseDetail.overview.teachingTeamSummaryDescription') }}</p>
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
          <span>{{ t('courseDetail.overview.qaSchedule') }}</span>
          <strong>{{ t('courseDetail.overview.seeCourseAnnouncements') }}</strong>
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
  if (!hasCourseProgressStats.value && (!props.classSessionsLoaded || props.classSessionsLoading)) return t('courseDetail.overview.calculating')
  if (totalClassHours.value === 0) return `${countedClassSessionCount.value} / ${t('courseDetail.toBeArranged')}`
  return `${countedClassSessionCount.value} / ${totalClassHours.value} ${t('courseDetail.classHoursUnit')}`
})

const enrollActionLabel = computed(() => {
  if (props.enrolling) return t('courseDetail.enrolling')
  if (!isPublished.value) return t('courseDetail.notOpen')
  if (isFull.value) return t('courseDetail.full')
  return t('courseDetail.enroll')
})

const statusLabel = computed(() => {
  if (props.course.status === 1) return t('courses.status.published')
  if (props.course.status === 2) return t('courses.status.archived')
  return t('courses.status.draft')
})

const visibilityLabel = computed(() => props.course.isPublic === 1 ? t('courses.visibility.public') : t('courses.visibility.private'))
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
    label: t('courseDetail.overview.completedClassHours'),
    value: hasCourseProgressStats.value || (props.classSessionsLoaded && !props.classSessionsLoading)
      ? `${countedClassSessionCount.value} ${t('courseDetail.classHoursUnit')}`
      : t('courseDetail.overview.calculating'),
  },
])

const nextActionTitle = computed(() => {
  if (props.canManageCourse) return t('courseDetail.overview.nextManageTitle')
  if (showEnrollAction.value) return t('courseDetail.overview.nextEnrollTitle')
  if (props.canAccessCourseContent) return t('courseDetail.overview.nextContinueTitle')
  return t('courseDetail.overview.nextLockedTitle')
})

const nextActionDescription = computed(() => {
  if (props.canManageCourse) return t('courseDetail.overview.nextManageDescription')
  if (showEnrollAction.value) return t('courseDetail.overview.nextEnrollDescription')
  if (props.canAccessCourseContent) return t('courseDetail.overview.nextContinueDescription')
  return t('courseDetail.overview.nextLockedDescription')
})

const roleSummaries = computed(() => [
  {
    label: t('courseDetail.overview.studentStatus'),
    value: props.course.enrolled
      ? t('courseDetail.overview.studentEnrolled')
      : props.isStudent ? t('courseDetail.overview.studentNotEnrolled') : t('courseDetail.overview.nonStudentAccount'),
  },
  {
    label: t('courseDetail.overview.teachingPermission'),
    value: props.canManageCourse
      ? props.isAdmin ? t('courseDetail.overview.adminCanManage') : t('courseDetail.overview.teachingTeamCanManage')
      : t('courseDetail.overview.cannotManage'),
  },
  {
    label: t('courseDetail.overview.contentAccess'),
    value: props.canAccessCourseContent ? t('courseDetail.overview.accessible') : t('courseDetail.overview.accessAfterEnrollment'),
  },
])

const chapterSummary = computed(() => {
  if (props.chaptersLoading) return t('courseDetail.overview.loadingChapters')
  if (flatChapters.value.length === 0) {
    return props.canManageCourse ? t('courseDetail.overview.noChaptersCreate') : t('courseDetail.overview.waitingChapters')
  }
  return t('courseDetail.overview.chapterCountSummary', {
    total: flatChapters.value.length,
    published: publishedChapters.value.length,
  })
})

const banksSummary = computed(() => {
  if (!props.banksLoaded) return t('courseDetail.overview.viewBankCountAfterEnter')
  if (bankCount.value === 0) return props.canManageCourse ? t('courseDetail.overview.noBanksCreate') : t('courseDetail.overview.waitingBanks')
  return t('courseDetail.overview.bankCountSummary', {count: bankCount.value})
})

const filesSummary = computed(() => {
  if (!props.filesLoaded) return t('courseDetail.overview.viewFileCountAfterEnter')
  if (fileCount.value === 0) return props.canManageCourse ? t('courseDetail.overview.noFilesUpload') : t('courseDetail.overview.waitingFiles')
  return t('courseDetail.overview.fileCountSummary', {count: fileCount.value})
})

const navigationEntries = computed<NavigationEntry[]>(() => {
  if (!props.canManageCourse && !props.canAccessCourseContent) {
    return []
  }

  const entries: NavigationEntry[] = [
    {
      key: 'chapters',
      title: t('courseDetail.overview.chaptersEntryTitle'),
      description: t('courseDetail.overview.chaptersEntryDescription'),
      summary: chapterSummary.value,
      actionLabel: flatChapters.value.length === 0 && props.canManageCourse
        ? t('courseDetail.overview.createChapter')
        : t('courseDetail.overview.enterChapters'),
      available: props.canManageCourse || (props.canAccessCourseContent && publishedChapters.value.length > 0),
      reason: flatChapters.value.length === 0
        ? props.canManageCourse ? t('courseDetail.overview.canCreate') : t('courseDetail.overview.waitingPublish')
        : props.canAccessCourseContent || props.canManageCourse ? t('courseDetail.overview.available') : t('courseDetail.overview.availableAfterEnrollment'),
      icon: BookOpen,
      action: flatChapters.value.length === 0 && props.canManageCourse ? 'createChapter' : 'route',
      routeName: 'course-chapters',
    },
    {
      key: 'class-sessions',
      title: t('courseDetail.overview.classSessionsEntryTitle'),
      description: t('courseDetail.overview.classSessionsEntryDescription'),
      summary: countedClassSessionCount.value > 0
        ? t('courseDetail.overview.completedHoursSummary', {
          count: countedClassSessionCount.value,
          unit: t('courseDetail.classHoursUnit'),
        })
        : props.canManageCourse ? t('courseDetail.overview.noClassesCreate') : t('courseDetail.overview.waitingClasses'),
      actionLabel: t('courseDetail.overview.enterClassroom'),
      available: props.canEnterClassSessions,
      reason: props.canEnterClassSessions ? t('courseDetail.overview.available') : t('courseDetail.overview.availableAfterEnrollment'),
      icon: Presentation,
      action: 'route',
      routeName: 'course-class-sessions',
    },
    {
      key: 'forums',
      title: t('courseDetail.overview.forumsEntryTitle'),
      description: t('courseDetail.overview.forumsEntryDescription'),
      summary: t('courseDetail.overview.forumsSummary'),
      actionLabel: t('courseDetail.overview.enterForums'),
      available: props.canAccessCourseContent,
      reason: props.canAccessCourseContent ? t('courseDetail.overview.available') : t('courseDetail.overview.availableAfterEnrollment'),
      icon: MessageCircle,
      action: 'route',
      routeName: 'course-forums',
    },
  ]

  entries.push({
    key: 'banks',
    title: props.canManageCourse ? t('courseDetail.overview.bankManageEntryTitle') : t('courseDetail.overview.banksEntryTitle'),
    description: props.canManageCourse ? t('courseDetail.overview.bankManageEntryDescription') : t('courseDetail.overview.banksEntryDescription'),
    summary: banksSummary.value,
    actionLabel: props.canManageCourse ? t('courseDetail.overview.manageBanks') : t('courseDetail.overview.viewBanks'),
    available: props.canManageCourse || (props.canAccessCourseContent && (!props.banksLoaded || bankCount.value > 0)),
    reason: props.canManageCourse
      ? t('courseDetail.overview.canManage')
      : props.canAccessCourseContent && (!props.banksLoaded || bankCount.value > 0) ? t('courseDetail.overview.canView') : t('courseDetail.overview.waitingPublish'),
    icon: Database,
    action: 'route',
    routeName: 'course-banks',
  })

  if (props.canViewLivePractices) {
    entries.push({
      key: 'live-practices',
      title: t('courseDetail.overview.livePracticeEntryTitle'),
      description: t('courseDetail.overview.livePracticeEntryDescription'),
      summary: t('courseDetail.overview.noActiveActivities'),
      actionLabel: t('courseDetail.overview.viewActivities'),
      available: props.canAccessCourseContent,
      reason: props.canAccessCourseContent ? t('courseDetail.overview.available') : t('courseDetail.overview.currentlyHidden'),
      icon: ClipboardList,
      action: 'route',
      routeName: 'course-live-practices',
    })
  }

  entries.push({
    key: 'files',
    title: t('courseDetail.overview.filesEntryTitle'),
    description: t('courseDetail.overview.filesEntryDescription'),
    summary: filesSummary.value,
    actionLabel: props.canManageCourse ? t('courseDetail.overview.uploadFiles') : t('courseDetail.overview.viewFiles'),
    available: props.canManageCourse || (props.canAccessCourseContent && (!props.filesLoaded || fileCount.value > 0)),
    reason: props.canManageCourse
      ? t('courseDetail.overview.canManage')
      : props.canAccessCourseContent && (!props.filesLoaded || fileCount.value > 0) ? t('courseDetail.overview.available') : t('courseDetail.overview.waitingUpload'),
    icon: FileDown,
    action: 'route',
    routeName: 'course-files',
  })

  if (props.canViewStudents) {
    entries.push({
      key: 'students',
      title: t('courseDetail.overview.studentsEntryTitle'),
      description: t('courseDetail.overview.studentsEntryDescription'),
      summary: t('courseDetail.overview.studentCountSummary', {count: studentCount.value}),
      actionLabel: t('courseDetail.overview.viewRoster'),
      available: true,
      reason: props.canManageCourse ? t('courseDetail.overview.visibleToTeachingTeam') : t('courseDetail.overview.visibleForPublicCourse'),
      icon: Users,
      action: 'route',
      routeName: 'course-students',
    })
  }

  if (props.canViewAssistants) {
    entries.push({
      key: 'assistants',
      title: t('courseDetail.overview.assistantsEntryTitle'),
      description: t('courseDetail.overview.assistantsEntryDescription'),
      summary: t('courseDetail.overview.assistantCountSummary', {count: props.assistants.length}),
      actionLabel: t('courseDetail.overview.viewTeam'),
      available: true,
      reason: props.canManageCourse ? t('courseDetail.overview.visibleToTeachingTeam') : t('courseDetail.overview.visibleForPublicCourse'),
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
