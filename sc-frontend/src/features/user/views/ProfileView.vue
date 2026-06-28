<template>
  <div class="profile-page">
    <section v-if="missingRoleProfileFields.length > 0" class="profile-alert">
      <AlertCircle :size="20" stroke-width="1.8"/>
      <div class="profile-alert-copy">
        <strong>{{ t('profile.roleProfileAlert.title') }}</strong>
        <p>
          {{
            t('profile.roleProfileAlert.description', {
              fields: missingRoleProfileFields.join(t('profile.roleProfileAlert.separator')),
            })
          }}
        </p>
      </div>
      <router-link class="profile-alert-action" to="/settings">
        <Pencil :size="15" stroke-width="1.8"/>
        {{ t('profile.roleProfileAlert.action') }}
      </router-link>
    </section>

    <section class="profile-hero">
      <div :class="{ 'profile-avatar--image': Boolean(user?.avatarUrl) }" class="profile-avatar">
        <img v-if="user?.avatarUrl" :alt="`${displayName} avatar`" :src="user.avatarUrl"/>
        <span v-else>{{ initials }}</span>
      </div>

      <div class="profile-intro">
        <div class="profile-title-row">
          <h1>{{ displayName }}</h1>
          <span class="role-pill">{{ roleLabel }}</span>
        </div>
        <p class="profile-bio">{{ profileBio }}</p>
        <div class="profile-contact">
          <span>
            <Mail :size="15" stroke-width="1.8"/>
            {{ user?.email || t('profile.noEmail') }}
          </span>
          <span>
            <Phone :size="15" stroke-width="1.8"/>
            {{ user?.phone || t('profile.noPhone') }}
          </span>
          <span>
            <CalendarDays :size="15" stroke-width="1.8"/>
            {{ t('profile.joined') }} {{ formatMonth(user?.createdAt) }}
          </span>
        </div>
      </div>

      <div class="profile-actions">
        <router-link class="btn-primary" to="/settings">
          <Pencil :size="15" stroke-width="1.8"/>
          {{ t('profile.editProfile') }}
        </router-link>
        <router-link :to="roleConfig.secondaryActionPath" class="btn-secondary">
          <component :is="roleConfig.secondaryActionIcon" :size="15" stroke-width="1.8"/>
          {{ roleConfig.secondaryActionLabel }}
        </router-link>
      </div>
    </section>

    <section class="profile-shell">
      <div class="profile-main-column">
        <section class="profile-panel role-identity">
          <div class="panel-heading">
            <div class="panel-icon">
              <component :is="roleConfig.icon" :size="20" stroke-width="1.8"/>
            </div>
            <div>
              <p class="panel-label">{{ roleConfig.identityLabel }}</p>
              <h2>{{ roleConfig.identityTitle }}</h2>
            </div>
          </div>

          <div class="identity-grid">
            <div
                v-for="item in identityDetails"
                :key="item.label"
                :class="{ 'identity-item--wide': item.wide }"
                class="identity-item"
            >
              <span>{{ item.label }}</span>
              <strong>{{ item.value }}</strong>
            </div>
          </div>
        </section>

        <section v-if="profileKind === 'student'" class="profile-panel">
          <div class="section-header">
            <div>
              <p class="panel-label">{{ t('profile.studentSection.workloadLabel') }}</p>
              <h2>{{ t('profile.studentSection.workloadTitle') }}</h2>
            </div>
            <router-link class="btn-text" to="/my-enrollments">{{
                t('profile.studentSection.viewEnrollments')
              }}
            </router-link>
          </div>

          <div v-if="studentLoading" class="state-list">
            <div v-for="index in 3" :key="index" class="skeleton-row"></div>
          </div>
          <div v-else-if="studentError" class="inline-state inline-state--error">
            <AlertCircle :size="18" stroke-width="1.8"/>
            <span>{{ studentError }}</span>
          </div>
          <div v-else-if="studentCourseRows.length > 0" class="activity-list">
            <article v-for="course in studentCourseRows" :key="course.id" class="activity-row">
              <div class="activity-copy">
                <h3>{{ course.title }}</h3>
                <p>{{ course.meta }}</p>
              </div>
              <span class="status-token">{{ course.status }}</span>
            </article>
          </div>
          <div v-else class="inline-state">
            <BookOpen :size="18" stroke-width="1.8"/>
            <span>{{ t('profile.studentSection.noData') }}</span>
          </div>
        </section>

        <section v-else-if="profileKind === 'teacher'" class="profile-panel">
          <div class="section-header">
            <div>
              <p class="panel-label">{{ t('profile.teacherSection.operationsLabel') }}</p>
              <h2>{{ t('profile.teacherSection.operationsTitle') }}</h2>
            </div>
            <router-link class="btn-text" to="/courses">{{ t('common.navigation.courses') }}</router-link>
          </div>

          <div v-if="teacherLoading" class="state-list">
            <div v-for="index in 3" :key="index" class="skeleton-row"></div>
          </div>
          <div v-else-if="teacherError" class="inline-state inline-state--error">
            <AlertCircle :size="18" stroke-width="1.8"/>
            <span>{{ teacherError }}</span>
          </div>
          <div v-else-if="teacherCourseRows.length > 0" class="course-list">
            <article v-for="course in teacherCourseRows" :key="course.id" class="course-row">
              <div class="course-row-main">
                <h3>{{ course.title }}</h3>
                <p>{{ course.meta }}</p>
              </div>
              <div :aria-label="`${course.title} capacity`" class="course-progress">
                <span :style="{ width: `${course.progress}%` }"></span>
              </div>
              <span class="status-token">{{ course.status }}</span>
            </article>
          </div>
          <div v-else class="inline-state">
            <BookOpen :size="18" stroke-width="1.8"/>
            <span>{{ t('profile.teacherSection.noData') }}</span>
          </div>
        </section>

        <section v-else class="profile-panel">
          <div class="section-header">
            <div>
              <p class="panel-label">{{ t('profile.userSection.readinessLabel') }}</p>
              <h2>{{ t('profile.userSection.readinessTitle') }}</h2>
            </div>
            <router-link class="btn-text" to="/settings">{{ t('profile.userSection.updateProfile') }}</router-link>
          </div>

          <div class="completion-block">
            <div class="completion-value">{{ profileCompletion }}%</div>
            <div class="completion-track">
              <span :style="{ width: `${profileCompletion}%` }"></span>
            </div>
            <p>{{ completionSummary }}</p>
          </div>

          <div class="suggestion-list">
            <span v-for="suggestion in accountSuggestions" :key="suggestion">{{ suggestion }}</span>
          </div>
        </section>
      </div>

      <aside class="profile-side-column">
        <section class="profile-panel metrics-panel">
          <p class="panel-label">{{ roleConfig.metricsLabel }}</p>
          <div class="metrics-grid">
            <div v-for="metric in metrics" :key="metric.label" class="metric-item">
              <strong>{{ metric.value }}</strong>
              <span>{{ metric.label }}</span>
              <small>{{ metric.note }}</small>
            </div>
          </div>
        </section>

        <section class="profile-panel details-panel">
          <div class="section-header section-header--compact">
            <div>
              <p class="panel-label">{{ t('profile.preferences.label') }}</p>
              <h2>{{ t('profile.preferences.title') }}</h2>
            </div>
            <Settings :size="20" stroke-width="1.8"/>
          </div>

          <div class="detail-list">
            <div v-for="item in preferenceDetails" :key="item.label" class="detail-row">
              <span>{{ item.label }}</span>
              <strong>{{ item.value }}</strong>
            </div>
          </div>
        </section>

        <section class="profile-panel details-panel">
          <div class="section-header section-header--compact">
            <div>
              <p class="panel-label">{{ t('profile.security.label') }}</p>
              <h2>{{ t('profile.security.title') }}</h2>
            </div>
            <ShieldCheck :size="20" stroke-width="1.8"/>
          </div>

          <div class="detail-list">
            <div v-for="item in securityDetails" :key="item.label" class="detail-row">
              <span>{{ item.label }}</span>
              <strong>{{ item.value }}</strong>
            </div>
          </div>

          <p class="security-note">{{ roleConfig.securityNote }}</p>
        </section>
      </aside>
    </section>
  </div>
</template>

<script lang="ts" setup>
import {computed, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {
  AlertCircle,
  BookOpen,
  BriefcaseBusiness,
  CalendarDays,
  GraduationCap,
  Mail,
  Pencil,
  Phone,
  Settings,
  ShieldCheck,
  UserRound,
} from 'lucide-vue-next'

import {getMyEnrollments, getTeacherCourses} from '@/features/course/api/course'
import {useAuthStore} from '@/features/auth/stores/auth'
import {getCurrentUser} from '@/features/user/api/user'
import type {Course, Enrollment} from '@/features/course/types/course'

type ProfileKind = 'user' | 'student' | 'teacher'

interface DetailItem {
  label: string
  value: string
  wide?: boolean
}

interface MetricItem {
  label: string
  value: string
  note: string
}

interface RoleConfig {
  icon: typeof UserRound
  identityLabel: string
  identityTitle: string
  metricsLabel: string
  secondaryActionLabel: string
  secondaryActionPath: string
  secondaryActionIcon: typeof Settings
  securityNote: string
}

const authStore = useAuthStore()
const {t} = useI18n()

const enrollments = ref<Enrollment[]>([])
const studentTotal = ref(0)
const studentLoading = ref(false)
const studentError = ref('')

const teacherCourses = ref<Course[]>([])
const teacherCourseTotal = ref(0)
const teacherLoading = ref(false)
const teacherError = ref('')

const user = computed(() => authStore.user)

const profileKind = computed<ProfileKind>(() => {
  if (user.value?.role === 1 || user.value?.studentInfo) {
    return 'student'
  }

  if (user.value?.role === 2 || user.value?.teacherInfo) {
    return 'teacher'
  }

  return 'user'
})

const roleConfig = computed<RoleConfig>(() => {
  if (profileKind.value === 'student') {
    return {
      icon: GraduationCap,
      identityLabel: t('profile.identity.studentLabel'),
      identityTitle: t('profile.identity.studentTitle'),
      metricsLabel: t('profile.metrics.studentLabel'),
      secondaryActionLabel: t('profile.secondaryAction.student'),
      secondaryActionPath: '/my-enrollments',
      secondaryActionIcon: GraduationCap,
      securityNote: t('profile.security.note.student'),
    }
  }

  if (profileKind.value === 'teacher') {
    return {
      icon: BriefcaseBusiness,
      identityLabel: t('profile.identity.teacherLabel'),
      identityTitle: t('profile.identity.teacherTitle'),
      metricsLabel: t('profile.metrics.teacherLabel'),
      secondaryActionLabel: t('profile.secondaryAction.teacher'),
      secondaryActionPath: '/teacher/courses?role=primary',
      secondaryActionIcon: BookOpen,
      securityNote: t('profile.security.note.teacher'),
    }
  }

  return {
    icon: UserRound,
    identityLabel: t('profile.identity.userLabel'),
    identityTitle: t('profile.identity.userTitle'),
    metricsLabel: t('profile.metrics.userLabel'),
    secondaryActionLabel: t('profile.secondaryAction.user'),
    secondaryActionPath: '/settings',
    secondaryActionIcon: Settings,
    securityNote: t('profile.security.note.user'),
  }
})

const displayName = computed(() => user.value?.displayName || 'User')

const roleLabel = computed(() => getRoleName(user.value?.role))

const initials = computed(() => {
  const source = displayName.value || user.value?.email || 'User'
  const words = source.trim().split(/\s+/).filter(Boolean)

  if (words.length >= 2) {
    return `${words[0][0]}${words[1][0]}`.toUpperCase()
  }

  return source.slice(0, 2).toUpperCase()
})

const profileBio = computed(() => {
  if (user.value?.bio) return user.value.bio
  if (profileKind.value === 'student') return t('profile.bio.student')
  if (profileKind.value === 'teacher') return t('profile.bio.teacher')
  return t('profile.bio.user')
})

const missingRoleProfileFields = computed(() => {
  if (profileKind.value === 'student') {
    const studentInfo = user.value?.studentInfo
    return [
      {label: t('profile.identityFields.grade'), value: studentInfo?.grade},
      {label: t('profile.identityFields.major'), value: studentInfo?.major},
      {label: t('profile.identityFields.school'), value: studentInfo?.school},
    ].filter(item => !hasText(item.value)).map(item => item.label)
  }

  if (profileKind.value === 'teacher') {
    const teacherInfo = user.value?.teacherInfo
    return [
      {label: t('profile.identityFields.department'), value: teacherInfo?.department},
      {label: t('profile.identityFields.title'), value: teacherInfo?.title},
      {label: t('profile.identityFields.school'), value: teacherInfo?.school},
    ].filter(item => !hasText(item.value)).map(item => item.label)
  }

  return []
})

const identityDetails = computed<DetailItem[]>(() => {
  if (profileKind.value === 'student') {
    const studentInfo = user.value?.studentInfo
    return [
      {label: t('profile.identityFields.studentNo'), value: valueOrDash(studentInfo?.studentNo)},
      {label: t('profile.identityFields.grade'), value: valueOrDash(studentInfo?.grade)},
      {label: t('profile.identityFields.major'), value: valueOrDash(studentInfo?.major)},
      {label: t('profile.identityFields.school'), value: valueOrDash(studentInfo?.school), wide: true},
      {label: t('profile.identityFields.emailVerified'), value: formatBoolean(user.value?.emailVerified)},
      {label: t('profile.identityFields.accountStatus'), value: valueOrDash(user.value?.status)},
    ]
  }

  if (profileKind.value === 'teacher') {
    const teacherInfo = user.value?.teacherInfo
    return [
      {label: t('profile.identityFields.employeeNo'), value: valueOrDash(teacherInfo?.employeeNo)},
      {label: t('profile.identityFields.department'), value: valueOrDash(teacherInfo?.department)},
      {label: t('profile.identityFields.title'), value: valueOrDash(teacherInfo?.title)},
      {label: t('profile.identityFields.school'), value: valueOrDash(teacherInfo?.school), wide: true},
      {label: t('profile.identityFields.emailVerified'), value: formatBoolean(user.value?.emailVerified)},
      {label: t('profile.identityFields.accountStatus'), value: valueOrDash(user.value?.status)},
    ]
  }

  return [
    {label: t('profile.identityFields.userId'), value: shortenId(user.value?.id), wide: true},
    {label: t('profile.identityFields.displayName'), value: valueOrDash(user.value?.displayName)},
    {label: t('profile.identityFields.email'), value: valueOrDash(user.value?.email)},
    {label: t('profile.identityFields.phone'), value: valueOrDash(user.value?.phone)},
    {label: t('profile.identityFields.accountStatus'), value: valueOrDash(user.value?.status)},
    {label: t('profile.identityFields.emailVerified'), value: formatBoolean(user.value?.emailVerified)},
  ]
})

const studentCourseRows = computed(() => (
    enrollments.value.slice(0, 4).map((enrollment) => ({
      id: enrollment.id,
      title: enrollment.courseTitle || 'Untitled course',
      meta: enrollment.completedAt
          ? t('profile.format.completed', {date: formatDateTime(enrollment.completedAt)})
          : t('profile.format.enrolled', {date: formatDateTime(enrollment.enrolledAt)}),
      status: getEnrollmentStatus(enrollment.status),
    }))
))

const teacherCourseRows = computed(() => (
    teacherCourses.value.slice(0, 4).map((course) => ({
      id: course.id,
      title: course.title,
      meta: t('profile.format.enrolledShort', {count: course.currentStudents}) + (course.maxStudents > 0 ? t('profile.format.ofMax', {max: course.maxStudents}) : ''),
      status: getCourseStatus(course.status),
      progress: getCourseCapacity(course),
    }))
))

const metrics = computed<MetricItem[]>(() => {
  if (profileKind.value === 'student') {
    const completedCount = enrollments.value.filter((enrollment) => enrollment.status === 2).length
    const activeCount = enrollments.value.filter((enrollment) => enrollment.status === 1).length

    return [
      {
        label: t('profile.metricsFields.enrolledCourses'),
        value: formatNumber(studentTotal.value),
        note: t('profile.metricsNotes.fromEnrollment')
      },
      {
        label: t('profile.metricsFields.activeCourses'),
        value: formatNumber(activeCount),
        note: t('profile.metricsNotes.currentlyLearning')
      },
      {
        label: t('profile.metricsFields.completed'),
        value: formatNumber(completedCount),
        note: t('profile.metricsNotes.loadedRecords')
      },
      {label: t('profile.metricsFields.certificates'), value: '--', note: t('profile.metricsNotes.waitingCertificate')},
    ]
  }

  if (profileKind.value === 'teacher') {
    const activeStudents = teacherCourses.value.reduce((total, course) => total + course.currentStudents, 0)
    const publishedCount = teacherCourses.value.filter((course) => course.status === 1).length

    return [
      {
        label: t('profile.metricsFields.coursesTaught'),
        value: formatNumber(teacherCourseTotal.value),
        note: t('profile.metricsNotes.ownedRecords')
      },
      {
        label: t('profile.metricsFields.visibleStudents'),
        value: formatNumber(activeStudents),
        note: t('profile.metricsNotes.fromLoadedCourses')
      },
      {
        label: t('profile.metricsFields.published'),
        value: formatNumber(publishedCount),
        note: t('profile.metricsNotes.activeRecords')
      },
      {label: t('profile.metricsFields.pendingReviews'), value: '--', note: t('profile.metricsNotes.waitingReview')},
    ]
  }

  return [
    {
      label: t('profile.metricsFields.completion'),
      value: `${profileCompletion.value}%`,
      note: t('profile.metricsNotes.profileFields')
    },
    {
      label: t('profile.metricsFields.loginCount'),
      value: formatNumber(user.value?.loginCount),
      note: t('profile.metricsNotes.accountActivity')
    },
    {
      label: t('profile.metricsFields.providers'),
      value: formatNumber(user.value?.linkedProviders?.length),
      note: t('profile.metricsNotes.linkedMethods')
    },
    {label: t('profile.metricsFields.role'), value: roleLabel.value, note: t('profile.metricsNotes.currentScope')},
  ]
})

const preferenceDetails = computed<DetailItem[]>(() => [
  {label: t('profile.preferences.theme'), value: formatTheme(user.value?.theme)},
  {label: t('profile.preferences.notifications'), value: formatBoolean(user.value?.notificationEnabled)},
  {label: t('profile.preferences.locale'), value: valueOrDash(user.value?.locale)},
  {label: t('profile.preferences.createdProvider'), value: valueOrDash(user.value?.createdProvider)},
  {label: t('profile.preferences.linkedProviders'), value: formatProviders(user.value?.linkedProviders)},
])

const securityDetails = computed<DetailItem[]>(() => [
  {label: t('profile.security.lastLogin'), value: formatDateTime(user.value?.lastLoginAt)},
  {label: t('profile.security.lastProvider'), value: valueOrDash(user.value?.lastLoginProvider)},
  {label: t('profile.security.lastIP'), value: maskIp(user.value?.lastLoginIp)},
  {label: t('profile.security.createdIP'), value: maskIp(user.value?.createdIp)},
])

const profileCompletion = computed(() => {
  const fields = [
    user.value?.displayName,
    user.value?.email,
    user.value?.phone,
    user.value?.bio,
    user.value?.avatarUrl,
    user.value?.locale,
    user.value?.theme,
  ]

  const filled = fields.filter(Boolean).length
  return Math.round((filled / fields.length) * 100)
})

const accountSuggestions = computed(() => {
  const suggestions: string[] = []

  if (!user.value?.phone) suggestions.push(t('profile.suggestions.addPhone'))
  if (!user.value?.bio) suggestions.push(t('profile.suggestions.writeBio'))
  if (!user.value?.avatarUrl) suggestions.push(t('profile.suggestions.uploadAvatar'))
  if (!user.value?.theme) suggestions.push(t('profile.suggestions.chooseTheme'))

  return suggestions.length > 0 ? suggestions : [t('profile.suggestions.complete')]
})

const completionSummary = computed(() => {
  if (profileCompletion.value >= 90) return t('profile.completionSummary.good')
  return t('profile.completionSummary.incomplete')
})

onMounted(async () => {
  try {
    authStore.setUser(await getCurrentUser())
  } catch {
    // Keep the locally cached profile when refresh is unavailable.
  }
})

watch(profileKind, (kind) => {
  if (kind === 'student') {
    loadStudentData()
    return
  }

  if (kind === 'teacher') {
    loadTeacherData()
    return
  }

  enrollments.value = []
  teacherCourses.value = []
}, {immediate: true})

async function loadStudentData() {
  studentLoading.value = true
  studentError.value = ''

  try {
    const response = await getMyEnrollments(1, 20)
    enrollments.value = response.records
    studentTotal.value = response.total
  } catch {
    enrollments.value = []
    studentTotal.value = 0
    studentError.value = t('profile.error.enrollmentUnavailable')
  } finally {
    studentLoading.value = false
  }
}

async function loadTeacherData() {
  teacherLoading.value = true
  teacherError.value = ''

  try {
    const response = await getTeacherCourses(1, 20)
    teacherCourses.value = response.records
    teacherCourseTotal.value = response.total
  } catch {
    teacherCourses.value = []
    teacherCourseTotal.value = 0
    teacherError.value = t('profile.error.teachingUnavailable')
  } finally {
    teacherLoading.value = false
  }
}

function getRoleName(role?: number | null): string {
  switch (role) {
    case 0:
      return 'ADMIN'
    case 1:
      return 'STUDENT'
    case 2:
      return 'TEACHER'
    default:
      return 'USER'
  }
}

function getEnrollmentStatus(status: number): string {
  const map: Record<number, string> = {
    0: t('profile.status.pending'),
    1: t('profile.status.active'),
    2: t('profile.status.completed'),
    3: t('profile.status.dropped'),
  }
  return map[status] ?? t('profile.status.unknown')
}

function getCourseStatus(status: number): string {
  const map: Record<number, string> = {
    0: t('profile.status.draft'),
    1: t('profile.status.published'),
    2: t('profile.status.archived'),
  }
  return map[status] ?? t('profile.status.unknown')
}

function getCourseCapacity(course: Course): number {
  if (course.maxStudents <= 0) {
    return 100
  }

  return Math.min(Math.round((course.currentStudents / course.maxStudents) * 100), 100)
}

function valueOrDash(value?: string | number | boolean | null): string {
  if (value === undefined || value === null || value === '') {
    return '-'
  }

  return String(value)
}

function hasText(value?: string | null): boolean {
  return Boolean(value?.trim())
}

function formatBoolean(value?: boolean | null): string {
  return value ? t('profile.format.enabled') : t('profile.format.disabled')
}

function formatTheme(theme?: string | null): string {
  if (!theme) return '-'
  return theme.charAt(0).toUpperCase() + theme.slice(1)
}

function formatProviders(providers?: string[] | null): string {
  return providers?.length ? providers.join(', ') : '-'
}

function formatNumber(value?: number | null): string {
  if (value === undefined || value === null) {
    return '--'
  }

  return new Intl.NumberFormat('en-US').format(value)
}

function formatMonth(dateStr?: string | null): string {
  if (!dateStr) return 'Unknown'

  return new Intl.DateTimeFormat('en-US', {
    month: 'short',
    year: 'numeric',
  }).format(new Date(dateStr))
}

function formatDateTime(dateStr?: string | null): string {
  if (!dateStr) return '-'

  return new Intl.DateTimeFormat('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(dateStr))
}

function shortenId(id?: string | null): string {
  if (!id) return '-'
  return id.length > 18 ? `${id.slice(0, 8)}...${id.slice(-6)}` : id
}

function maskIp(ip?: string | null): string {
  if (!ip) return '-'
  const parts = ip.split('.')

  if (parts.length !== 4) {
    return ip
  }

  return `${parts[0]}.${parts[1]}.${parts[2]}.*`
}
</script>

<style scoped>
.profile-page {
  display: flex;
  flex-direction: column;
  gap: 24px;
  color: var(--color-on-surface);
}

.profile-alert {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 16px;
  align-items: center;
  padding: 18px 22px;
  border: 1px solid color-mix(in srgb, var(--color-warning, #a96b00) 35%, var(--color-outline-light));
  border-radius: var(--radius-lg);
  background: color-mix(in srgb, var(--color-warning, #a96b00) 10%, var(--color-surface-card));
  color: var(--color-on-surface);
}

.profile-alert > svg {
  color: var(--color-warning, #a96b00);
}

.profile-alert-copy {
  min-width: 0;
}

.profile-alert-copy strong {
  display: block;
  margin-bottom: 4px;
  font-family: var(--font-label);
  font-size: 14px;
  font-weight: 800;
}

.profile-alert-copy p {
  margin: 0;
  color: var(--color-on-surface-variant);
  font-size: 14px;
  line-height: 1.5;
}

.profile-alert-action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  min-height: 38px;
  padding: 8px 14px;
  border: 1px solid var(--color-on-surface);
  border-radius: var(--radius-sm);
  color: var(--color-on-surface);
  font-family: var(--font-label);
  font-size: 13px;
  font-weight: 800;
  text-decoration: none;
  white-space: nowrap;
}

.profile-alert-action:hover {
  background: var(--color-on-surface);
  color: var(--color-on-primary);
}

.profile-hero,
.profile-panel {
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-xl);
}

.profile-hero {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 28px;
  align-items: center;
  padding: 32px;
}

.profile-avatar {
  width: 104px;
  height: 104px;
  display: grid;
  place-items: center;
  overflow: hidden;
  border: 1px solid var(--color-primary);
  border-radius: 32px;
  background: var(--color-primary);
  color: var(--color-on-primary);
  font-family: var(--font-heading);
  font-size: 36px;
  font-weight: 700;
  letter-spacing: -0.02em;
  font-variant-numeric: tabular-nums;
}

.profile-avatar--image {
  background: var(--color-surface-canvas);
}

.profile-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.profile-intro {
  min-width: 0;
}

.panel-label {
  margin: 0 0 8px;
  font-family: var(--font-label);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.1em;
  color: var(--color-muted);
  text-transform: uppercase;
}

.profile-title-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.profile-title-row h1 {
  margin: 0;
  font-family: var(--font-heading);
  font-size: clamp(36px, 5vw, 56px);
  font-weight: 400;
  line-height: 1.3;
  letter-spacing: 0;
  text-wrap: balance;
}

.role-pill,
.status-token {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 28px;
  padding: 5px 12px;
  border-radius: var(--radius-pill);
  background: var(--color-primary);
  color: var(--color-on-primary);
  font-family: var(--font-label);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  white-space: nowrap;
}

.profile-bio {
  max-width: 720px;
  margin: 0 0 18px;
  color: var(--color-on-surface-variant);
  font-family: var(--font-body);
  font-size: 16px;
  line-height: 1.65;
  text-wrap: pretty;
}

.profile-contact {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.profile-contact span {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  min-height: 34px;
  padding: 7px 12px;
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-pill);
  color: var(--color-muted);
  font-size: 14px;
}

.profile-actions {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 12px;
}

.profile-actions :deep(.btn-primary),
.profile-actions :deep(.btn-secondary) {
  min-height: 44px;
  text-decoration: none;
  white-space: nowrap;
}

.profile-shell {
  display: grid;
  grid-template-columns: minmax(0, 1.48fr) minmax(320px, 0.72fr);
  gap: 24px;
  align-items: start;
}

.profile-main-column,
.profile-side-column {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.profile-panel {
  padding: 28px;
}

.panel-heading,
.section-header {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  align-items: flex-start;
  margin-bottom: 24px;
}

.panel-heading {
  justify-content: flex-start;
  align-items: center;
}

.section-header--compact {
  align-items: center;
  margin-bottom: 18px;
}

.panel-heading h2,
.section-header h2 {
  margin: 0;
  font-family: var(--font-heading);
  font-size: 32px;
  font-weight: 400;
  line-height: 1.1;
  letter-spacing: 0;
}

.panel-icon {
  width: 44px;
  height: 44px;
  display: grid;
  place-items: center;
  border: 1px solid var(--color-primary);
  border-radius: 16px;
  background: var(--color-primary);
  color: var(--color-on-primary);
  flex: 0 0 auto;
}

.identity-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.identity-item,
.metric-item,
.detail-row {
  min-width: 0;
  border: 1px solid var(--color-outline-light);
  background: var(--color-surface-canvas);
}

.identity-item {
  padding: 18px;
  border-radius: var(--radius-md);
}

.identity-item--wide {
  grid-column: 1 / -1;
}

.identity-item span,
.detail-row span,
.metric-item span,
.metric-item small {
  display: block;
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 12px;
  font-weight: 400;
}

.identity-item span,
.detail-row span,
.metric-item span {
  margin-bottom: 7px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.identity-item strong,
.detail-row strong {
  display: block;
  overflow-wrap: anywhere;
  color: var(--color-on-surface);
  font-family: var(--font-body);
  font-size: 15px;
  font-weight: 700;
}

.activity-list,
.course-list,
.state-list,
.detail-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.activity-row,
.course-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 16px;
  align-items: center;
  padding: 18px;
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-md);
  background: var(--color-surface-card);
}

.course-row {
  grid-template-columns: minmax(0, 1fr) 128px auto;
}

.activity-copy h3,
.course-row-main h3 {
  margin: 0 0 6px;
  color: var(--color-on-surface);
  font-family: var(--font-body);
  font-size: 17px;
  font-weight: 700;
}

.activity-copy p,
.course-row-main p {
  margin: 0;
  color: var(--color-muted);
  font-size: 14px;
}

.course-progress,
.completion-track {
  height: 6px;
  overflow: hidden;
  background: var(--color-outline-light);
}

.course-progress span,
.completion-track span {
  display: block;
  height: 100%;
  background: var(--color-primary);
}

.inline-state {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 64px;
  padding: 18px;
  border: 1px dashed var(--color-outline-variant);
  border-radius: var(--radius-md);
  color: var(--color-muted);
  font-size: 14px;
}

.inline-state--error {
  border-color: var(--color-outline);
  color: var(--color-error);
}

.skeleton-row {
  height: 74px;
  border-radius: var(--radius-md);
  background: linear-gradient(90deg, var(--color-surface-container) 0%, var(--color-surface-container-high) 45%, var(--color-surface-container) 100%);
  background-size: 220% 100%;
  animation: profile-shimmer 1.4s ease-in-out infinite;
}

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.metric-item {
  padding: 18px;
  border-radius: var(--radius-md);
}

.metric-item strong,
.completion-value {
  display: block;
  margin-bottom: 4px;
  color: var(--color-on-surface);
  font-family: var(--font-heading);
  font-size: 36px;
  font-weight: 700;
  line-height: 1;
  letter-spacing: 0;
  font-variant-numeric: tabular-nums;
}

.metric-item small {
  margin-top: 10px;
  font-family: var(--font-body);
  font-weight: 500;
  letter-spacing: 0;
  text-transform: none;
}

.detail-row {
  display: grid;
  grid-template-columns: minmax(96px, 0.65fr) minmax(0, 1fr);
  gap: 12px;
  align-items: center;
  padding: 13px 14px;
  border-radius: var(--radius-sm);
}

.detail-row span {
  margin-bottom: 0;
}

.detail-row strong {
  text-align: right;
}

.completion-block {
  padding: 22px;
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-md);
  background: var(--color-surface-canvas);
}

.completion-value {
  font-size: 52px;
}

.completion-track {
  margin: 14px 0;
}

.completion-block p {
  max-width: 620px;
  margin: 0;
  color: var(--color-muted);
  line-height: 1.6;
}

.suggestion-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 16px;
}

.suggestion-list span {
  display: inline-flex;
  min-height: 32px;
  align-items: center;
  padding: 6px 12px;
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-pill);
  color: var(--color-on-surface-variant);
  font-size: 13px;
  font-weight: 400;
}

.security-note {
  margin: 18px 0 0;
  padding-top: 18px;
  border-top: 1px solid var(--color-outline-light);
  color: var(--color-muted);
  font-size: 14px;
  line-height: 1.6;
}

@keyframes profile-shimmer {
  0% {
    background-position: 120% 0;
  }

  100% {
    background-position: -120% 0;
  }
}

@media (max-width: 1100px) {
  .profile-hero,
  .profile-shell {
    grid-template-columns: 1fr;
  }

  .profile-actions {
    flex-direction: row;
    flex-wrap: wrap;
  }

  .profile-shell {
    gap: 24px;
  }
}

@media (max-width: 720px) {
  .profile-alert {
    grid-template-columns: auto minmax(0, 1fr);
  }

  .profile-alert-action {
    grid-column: 1 / -1;
    width: 100%;
  }

  .profile-hero,
  .profile-panel {
    border-radius: var(--radius-lg);
    padding: 22px;
  }

  .profile-avatar {
    width: 88px;
    height: 88px;
    border-radius: 24px;
    font-size: 30px;
  }

  .profile-title-row h1 {
    font-size: 36px;
  }

  .profile-actions,
  .profile-actions :deep(.btn-primary),
  .profile-actions :deep(.btn-secondary) {
    width: 100%;
  }

  .profile-actions :deep(.btn-primary),
  .profile-actions :deep(.btn-secondary) {
    justify-content: center;
  }

  .identity-grid,
  .metrics-grid {
    grid-template-columns: 1fr;
  }

  .course-row {
    grid-template-columns: 1fr;
  }

  .course-progress {
    width: 100%;
  }

  .detail-row {
    grid-template-columns: 1fr;
  }

  .detail-row strong {
    text-align: left;
  }
}
</style>
