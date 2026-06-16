<template>
  <div class="course-detail-page">
    <div v-if="loading" :aria-label="t('courseDetail.loading')" class="loading-layout">
      <section class="loading-main">
        <div class="skeleton-line skeleton-kicker shimmer"></div>
        <div class="skeleton-title shimmer"></div>
        <div class="skeleton-cover shimmer"></div>
        <div class="skeleton-line shimmer"></div>
        <div class="skeleton-line short shimmer"></div>
      </section>
      <aside class="action-rail skeleton-rail shimmer"></aside>
    </div>

    <div v-else-if="!course" class="empty-state">
      <BookOpen :size="36" stroke-width="1.4"/>
      <h1>{{ t('courseDetail.courseNotFound') }}</h1>
      <button class="secondary-button" type="button" @click="router.push('/courses')">
        {{ t('courseDetail.browseCourses') }}
      </button>
    </div>

    <div v-else class="detail-layout">
      <main class="detail-canvas">
        <section class="course-main-column">
          <button class="back-link" type="button" @click="router.push('/courses')">
            <ArrowLeft :size="16" stroke-width="1.8"/>
            {{ t('courseDetail.backToCourses') }}
          </button>

          <section class="course-hero">
            <div class="hero-kicker">Academic Course</div>
            <h1>{{ course.title }}</h1>
            <p class="course-description">{{ course.description || t('courseDetail.noDescription') }}</p>

            <dl class="metadata-grid">
              <div class="metadata-item">
                <dt>{{ t('courseDetail.level') }}</dt>
                <dd>
                  <School :size="16" stroke-width="1.7"/>
                  {{ levelLabel }}
                </dd>
              </div>
              <div class="metadata-item">
                <dt>{{ t('courseDetail.totalClassHours') }}</dt>
                <dd>
                  <Clock :size="16" stroke-width="1.7"/>
                  {{ totalClassHoursLabel }}
                </dd>
              </div>
              <div class="metadata-item">
                <dt>{{ t('courseDetail.access') }}</dt>
                <dd>
                  <Globe v-if="course.isPublic === 1" :size="16" stroke-width="1.7"/>
                  <LockKeyhole v-else :size="16" stroke-width="1.7"/>
                  {{ visibilityLabel }}
                </dd>
              </div>
              <div class="metadata-item">
                <dt>{{ t('courseDetail.format') }}</dt>
                <dd>
                  <DoorOpen :size="16" stroke-width="1.7"/>
                  {{ courseTypeLabel || t('courseDetail.toBeArranged') }}
                </dd>
              </div>
              <div class="metadata-item">
                <dt>{{ t('courseDetail.semester') }}</dt>
                <dd>
                  <CalendarDays :size="16" stroke-width="1.7"/>
                  {{ course.semester || t('courseDetail.toBeArranged') }}
                </dd>
              </div>
              <div class="metadata-item">
                <dt>{{ t('courseDetail.location') }}</dt>
                <dd>
                  <MapPin :size="16" stroke-width="1.7"/>
                  {{ course.location || t('courseDetail.toBeArranged') }}
                </dd>
              </div>
            </dl>

            <button
                :disabled="primaryActionDisabled"
                class="primary-action hero-action"
                type="button"
                @click="handlePrimaryAction"
            >
              {{ primaryActionLabel }}
            </button>
          </section>

          <nav :aria-label="t('courseDetail.contentTabs')" class="tab-nav">
            <router-link
                v-for="tab in visibleTabs"
                :key="tab.key"
                :class="{active: activeTabKey === tab.key}"
                :to="'/courses/' + courseId + '/' + tab.key"
                class="tab-btn"
            >
              {{ tab.label }}
            </router-link>
          </nav>

          <router-view
              :assistants="assistantOnlyInfos"
              :banks="courseBanks"
              :can-access-course-content="canAccessCourseContent"
              :can-comment="canComment"
              :can-manage-assistants="canManageAssistants"
              :can-manage-course="canManageCourse"
              :chapter-tree="chapterTree"
              :chapters-loading="chaptersLoading"
              :course="course"
              :course-id="courseId"
              :create-request-key="classSessionCreateRequestKey"
              :current-user-id="authStore.user?.id"
              :files="courseFiles"
              :format-date="formatDate"
              :is-admin="isAdmin"
              :is-student="isStudent"
              :students="courseStudents"
              v-bind="activeListPaginationProps"
              @refresh="reloadActiveTabData"
              @open-chapter-editor="openChapterEditor"
              @select-chapter="handleChapterSelect"
              @edit-chapter="handleEditChapter"
              @delete-chapter="handleDeleteChapter"
              @add-child-chapter="handleAddChildChapter"
              @refresh-banks="reloadBanks"
              @refresh-files="reloadFiles"
              @refresh-students="reloadStudents"
              @refresh-course="reloadCourse"
              @page-change="handleListPageChange"
          />
        </section>

        <aside class="course-side-column">
          <div class="cover-frame">
            <img
                :alt="course.title"
                :src="course.coverUrl || courseCoverFallbackUrl"
                @error="useFallbackImage($event, courseCoverFallbackUrl)"
            />
          </div>

          <section class="teaching-team-panel">
            <div class="panel-kicker">{{ t('courseDetail.teachingTeam') }}</div>
            <div class="team-lead">
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
            <div class="assistant-row">
              <span>{{ t('courseDetail.assistantsTab') }} ({{ assistantOnlyInfos.length }})</span>
              <div class="assistant-stack">
                <div
                    v-for="assistant in assistantOnlyInfos.slice(0, 2)"
                    :key="assistant.id"
                    class="assistant-chip"
                >
                  <UserAvatarLink
                      :avatar-url="assistant.avatarUrl"
                      :display-name="assistant.displayName"
                      :role="2"
                      :show-name="false"
                      :user-id="assistant.id"
                      size="small"
                  />
                </div>
                <div v-if="assistantOnlyInfos.length === 0" class="assistant-chip empty">
                  <User :size="16" stroke-width="1.7"/>
                </div>
              </div>
            </div>
          </section>

          <section class="enrollment-panel">
            <div class="capacity-block">
              <span class="year-badge">{{ course.semester || 'Academic Year' }}</span>
              <h3>{{ t('courseDetail.capacity') }}</h3>
              <div class="capacity-header">
                <span>{{ course.currentStudents }} {{ t('courseDetail.enrolled') }}</span>
                <span>{{ maxStudentsLabel }}</span>
              </div>
              <div class="capacity-track">
                <span :style="{width: capacityPercent + '%'}"></span>
              </div>
            </div>

            <div v-if="canManageCourse" class="admin-actions">
              <h3>Administration</h3>
              <button class="secondary-action" type="button" @click="openEditModal">
                <Pencil :size="18" stroke-width="1.8"/>
                {{ t('courseDetail.editCourse') }}
              </button>
              <button class="secondary-action" type="button" @click="openChapterEditor">
                <Plus :size="18" stroke-width="1.8"/>
                {{ t('chapter.addChapter') }}
              </button>
              <button class="secondary-action" type="button" @click="goToCourseBanks">
                <Database :size="18" stroke-width="1.8"/>
                {{ t('courseDetail.manageBanks') }}
              </button>
              <button class="secondary-action" type="button" @click="openClassSessionCreator">
                <Presentation :size="18" stroke-width="1.8"/>
                {{ t('courseDetail.classSession.createAction') }}
              </button>
            </div>

            <div class="date-list">
              <div>
                <span>{{ t('courseDetail.created') }}:</span>
                <strong>{{ formatDate(course.createdAt) }}</strong>
              </div>
              <div>
                <span>{{ t('courseDetail.updated') }}:</span>
                <strong>{{ course.updatedAt ? formatDate(course.updatedAt) : t('courseDetail.noUpdates') }}</strong>
              </div>
            </div>
          </section>
        </aside>
      </main>
    </div>

    <ChapterEditor
        :chapter="editingChapter"
        :course-id="courseId"
        :parent-chapter-id="parentChapterId"
        :visible="showChapterEditor"
        @close="closeChapterEditor"
        @save="handleSaveChapter"
    />

    <CourseFormModal
        :can-edit-course-status="canEditCourseStatus"
        :course="course"
        :visible="showEditModal"
        mode="edit"
        @close="showEditModal = false"
        @updated="handleCourseUpdated"
    />
  </div>
</template>

<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import {
  ArrowLeft,
  BookOpen,
  CalendarDays,
  Clock,
  Database,
  DoorOpen,
  Globe,
  LockKeyhole,
  MapPin,
  MessageCircle,
  Pencil,
  Plus,
  Presentation,
  School,
  User,
  UserCheck,
  Users,
} from 'lucide-vue-next'

import {createChapter, deleteChapter, getChapterTree, updateChapter} from '@/features/course/api/chapter'
import {enroll, getCourse, getCourseEnrollments, listCourseFiles, updateCourse} from '@/features/course/api/course'
import {getQuestionBanks} from '@/features/question-bank/api/questionBank'
import {notify} from '@/shared/composables/useGlobalNotification'
import {useAuthStore} from '@/features/auth/stores/auth'

import type {Chapter, CreateChapterRequest, UpdateChapterRequest} from '@/features/course/types/chapter'
import type {CourseDetail, CourseFile, Enrollment, UpdateCourseRequest,} from '@/features/course/types/course'
import type {QuestionBank} from '@/features/question-bank/types/questionBank'

import ChapterEditor from '@/features/course/components/ChapterEditor.vue'
import CourseFormModal from '@/features/course/components/CourseFormModal.vue'
import {confirmDialog} from '@/shared/composables/useConfirmDialog'
import {recordCourseVisit} from '@/shared/composables/useRecentCourses'
import UserAvatarLink from '@/shared/components/UserAvatarLink.vue'

type TabKey = 'overview' | 'chapters' | 'forums' | 'banks' | 'files' | 'students' | 'assistants'
type TeacherInfo = NonNullable<CourseDetail['teacherInfos']>[number]
const DETAIL_PAGE_SIZE = 10

const {t, locale} = useI18n()
const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const courseId = computed(() => String(route.params.id || ''))

const loading = ref(true)
const chaptersLoading = ref(false)
const banksLoading = ref(false)
const filesLoading = ref(false)
const studentsLoading = ref(false)
const enrolling = ref(false)
const course = ref<CourseDetail | null>(null)
const chapterTree = ref<Chapter[]>([])
const courseBanks = ref<QuestionBank[]>([])
const courseFiles = ref<CourseFile[]>([])
const courseStudents = ref<Enrollment[]>([])
const chaptersLoaded = ref(false)
const banksLoaded = ref(false)
const filesLoaded = ref(false)
const studentsLoaded = ref(false)
const banksPage = ref(1)
const banksTotal = ref(0)
const filesPage = ref(1)
const filesTotal = ref(0)
const studentsPage = ref(1)
const studentsTotal = ref(0)
const showChapterEditor = ref(false)
const showEditModal = ref(false)
const editingChapter = ref<Chapter | null>(null)
const parentChapterId = ref<string | null>(null)
const classSessionCreateRequestKey = ref(0)

const courseCoverFallbackUrl = '/assets/course-cover-default.png'

const isAdmin = computed(() => authStore.user?.role === 0)
const isStudent = computed(() => authStore.user?.role === 1)
const isPublished = computed(() => course.value?.status === 1)
const canEditCourseStatus = computed(() => {
  const userId = authStore.user?.id
  return Boolean(isAdmin.value || (userId && course.value?.teacherId === userId))
})
const canManageCourse = computed(() => {
  const userId = authStore.user?.id
  if (!userId || !course.value) return isAdmin.value
  return isAdmin.value || course.value.teacherId === userId || Boolean(course.value.teacherIds?.includes(userId))
})
const canManageAssistants = computed(() => {
  const userId = authStore.user?.id
  if (!userId || !course.value) return isAdmin.value
  return isAdmin.value || course.value.teacherId === userId
})
const canComment = computed(() => {
  const userId = authStore.user?.id
  if (!userId || !course.value) return false
  return isAdmin.value
      || course.value.teacherId === userId
      || Boolean(course.value.enrolled)
      || Boolean(course.value.teacherIds?.includes(userId))
})
const isFull = computed(() => {
  if (!course.value || course.value.maxStudents <= 0) return false
  return course.value.currentStudents >= course.value.maxStudents
})
const showEnrollButton = computed(() => isStudent.value && !course.value?.enrolled)
const isPublicPublishedCourse = computed(() => course.value?.isPublic === 1 && isPublished.value)
const canAccessCourseContent = computed(() => {
  return canManageCourse.value || Boolean(course.value?.enrolled) || isPublicPublishedCourse.value
})
const canViewStudents = computed(() => canManageCourse.value || isPublicPublishedCourse.value)

const assistantOnlyInfos = computed<TeacherInfo[]>(() => {
  if (!course.value?.teacherInfos) return []
  return course.value.teacherInfos.filter(info => info.id !== course.value?.teacherId)
})

const capacityPercent = computed(() => {
  if (!course.value || course.value.maxStudents <= 0) return 0
  return Math.min((course.value.currentStudents / course.value.maxStudents) * 100, 100)
})
const levelLabel = computed(() => {
  const labels: Record<number, string> = {
    1: t('courses.level.beginner'),
    2: t('courses.level.intermediate'),
    3: t('courses.level.advanced'),
  }
  return labels[course.value?.level || 0] || t('courseDetail.levelUnknown')
})
const totalClassHoursLabel = computed(() => {
  const hours = course.value?.totalClassHours
  if (hours == null || hours <= 0) return t('courseDetail.toBeArranged')
  return `${hours} ${t('courseDetail.classHoursUnit')}`
})
const courseTypeLabel = computed(() => {
  if (course.value?.courseType === null || course.value?.courseType === undefined) return ''
  const labels: Record<number, string> = {
    0: t('courses.courseType.required'),
    1: t('courses.courseType.elective'),
  }
  return labels[course.value.courseType] || ''
})
const visibilityLabel = computed(() => {
  if (!course.value) return ''
  return course.value.isPublic === 1 ? t('courses.visibility.public') : t('courses.visibility.private')
})
const enrollLabel = computed(() => {
  if (enrolling.value) return t('courseDetail.enrolling')
  if (!isPublished.value) return t('courseDetail.notOpen')
  if (isFull.value) return t('courseDetail.full')
  return t('courseDetail.enrollNow')
})
const primaryActionLabel = computed(() => {
  if (showEnrollButton.value) return enrollLabel.value
  if (canAccessCourseContent.value) return t('courseDetail.continueLearning')
  return emptyActionLabel.value
})
const primaryActionDisabled = computed(() => {
  if (showEnrollButton.value) return enrolling.value || isFull.value || !isPublished.value
  return !canAccessCourseContent.value
})
const maxStudentsLabel = computed(() => {
  if (!course.value || course.value.maxStudents <= 0) return t('courseDetail.unlimited')
  return `${course.value.maxStudents} Max`
})
const emptyActionLabel = computed(() => {
  if (!firstChapter.value) return t('courseDetail.noChapterAction')
  return t('courseDetail.viewOnly')
})

function flattenChapters(chapters: Chapter[]): Chapter[] {
  return chapters.flatMap(chapter => [chapter, ...flattenChapters(chapter.children || [])])
}

const flatChapters = computed(() => flattenChapters(chapterTree.value))
const firstChapter = computed(() => flatChapters.value.find(chapter => chapter.status === 1) || flatChapters.value[0] || null)

const tabs = computed(() => [
  {key: 'overview' as const, label: t('courseDetail.overviewTab'), icon: BookOpen, roles: [0, 1, 2]},
  {key: 'chapters' as const, label: t('courseDetail.chaptersTab'), icon: BookOpen, roles: [0, 1, 2]},
  {key: 'forums' as const, label: t('courseDetail.discussionTab'), icon: MessageCircle, roles: [0, 1, 2]},
  {key: 'banks' as const, label: t('courseDetail.practiceTab'), icon: Database, roles: [0, 1, 2]},
  {key: 'files' as const, label: t('courseDetail.filesTab'), icon: Database, roles: [0, 1, 2]},
  {key: 'students' as const, label: t('courseDetail.studentsTab'), icon: Users, roles: [0, 1, 2], requiresViewStudents: true},
  {key: 'assistants' as const, label: t('courseDetail.assistantsTab'), icon: UserCheck, roles: [0, 2]},
])
const visibleTabs = computed(() => {
  const role = authStore.user?.role ?? 1
  return tabs.value.filter(tab => tab.roles.includes(role) && (!tab.requiresViewStudents || canViewStudents.value))
})

const activeTabKey = computed(() => {
  const path = route.path
  const match = path.match(/\/courses\/[^/]+\/([^/]+)/)
  if (match) {
    const key = match[1] as TabKey
    if (tabs.value.some(tab => tab.key === key)) return key
  }
  return 'overview'
})
const isActiveTabVisible = computed(() => visibleTabs.value.some(tab => tab.key === activeTabKey.value))

const activeListPage = computed(() => {
  switch (activeTabKey.value) {
    case 'banks':
      return banksPage.value
    case 'files':
      return filesPage.value
    case 'students':
      return studentsPage.value
    default:
      return 1
  }
})

const activeListTotal = computed(() => {
  switch (activeTabKey.value) {
    case 'banks':
      return banksTotal.value
    case 'files':
      return filesTotal.value
    case 'students':
      return studentsTotal.value
    default:
      return 0
  }
})

const activeListLoading = computed(() => {
  switch (activeTabKey.value) {
    case 'banks':
      return banksLoading.value
    case 'files':
      return filesLoading.value
    case 'students':
      return studentsLoading.value
    default:
      return false
  }
})

const activeListPaginationProps = computed(() => {
  if (!['banks', 'files', 'students'].includes(activeTabKey.value)) {
    return {}
  }
  return {
    page: activeListPage.value,
    size: DETAIL_PAGE_SIZE,
    total: activeListTotal.value,
    loading: activeListLoading.value,
  }
})

watch(
  courseId,
  (newCourseId, oldCourseId) => {
    if (newCourseId !== oldCourseId) resetCourseData()
    void loadCourseDetail(newCourseId)
  },
  {immediate: true},
)

watch(activeTabKey, () => {
  if (!course.value || loading.value) return
  if (!isActiveTabVisible.value) {
    void router.replace({name: 'course-overview', params: {id: courseId.value}})
    return
  }
  void ensureActiveTabData()
})

watch(isActiveTabVisible, (visible) => {
  if (!course.value || loading.value || visible) return
  void router.replace({name: 'course-overview', params: {id: courseId.value}})
})

function resetCourseData() {
  course.value = null
  chapterTree.value = []
  courseBanks.value = []
  courseFiles.value = []
  courseStudents.value = []
  chaptersLoading.value = false
  banksLoading.value = false
  filesLoading.value = false
  studentsLoading.value = false
  chaptersLoaded.value = false
  banksLoaded.value = false
  filesLoaded.value = false
  studentsLoaded.value = false
  banksPage.value = 1
  banksTotal.value = 0
  filesPage.value = 1
  filesTotal.value = 0
  studentsPage.value = 1
  studentsTotal.value = 0
}

async function loadCourseDetail(targetCourseId = courseId.value) {
  loading.value = true
  try {
    const courseData = await getCourse(targetCourseId)
    if (targetCourseId !== courseId.value) return

    course.value = courseData
    recordCourseVisit({
      id: courseData.id,
      title: courseData.title,
      coverUrl: courseData.coverUrl,
      teacherName: courseData.teacherName,
    })
    if (!isActiveTabVisible.value) {
      await router.replace({name: 'course-overview', params: {id: courseId.value}})
    }
  } finally {
    if (targetCourseId === courseId.value) loading.value = false
  }

  if (targetCourseId === courseId.value) await ensureActiveTabData()
}

async function handleEnroll() {
  if (!course.value || enrolling.value || isFull.value || !isPublished.value) return
  enrolling.value = true
  try {
    await enroll({courseId: courseId.value})
    course.value.enrolled = true
    course.value.currentStudents += 1
    notify.success(t('courseDetail.alert.enrollSuccess'))
  } catch {
    notify.error(t('courseDetail.alert.enrollFailed'))
  } finally {
    enrolling.value = false
  }
}

function openEditModal() {
  showEditModal.value = true
}

function handlePrimaryAction() {
  if (showEnrollButton.value) {
    void handleEnroll()
    return
  }
  if (canAccessCourseContent.value) {
    router.push({name: 'course-class-sessions', params: {id: courseId.value}})
  }
}

async function handleCourseUpdated(request: UpdateCourseRequest) {
  if (!course.value) return
  try {
    await updateCourse(course.value.id, request)
    notify.success(t('courseDetail.alert.updateSuccess'))
    showEditModal.value = false
    await loadCourseDetail()
  } catch {
    notify.error(t('courseDetail.alert.updateFailed'))
  }
}

function openChapterEditor() {
  editingChapter.value = null
  parentChapterId.value = null
  showChapterEditor.value = true
}

function handleChapterSelect(chapter: Chapter) {
  router.push({
    name: 'chapter-detail',
    params: {courseId: courseId.value},
    query: {chapterId: chapter.id},
  })
}

function handleEditChapter(chapter: Chapter) {
  editingChapter.value = chapter
  parentChapterId.value = null
  showChapterEditor.value = true
}

async function handleDeleteChapter(chapter: Chapter) {
  if (!(await confirmDialog({message: t('chapter.confirmDelete'), confirmVariant: 'danger'}))) return
  try {
    await deleteChapter(chapter.id)
    notify.success(t('courseDetail.alert.deleteChapterSuccess'))
    await reloadChapters()
  } catch {
    notify.error(t('courseDetail.alert.deleteChapterFailed'))
  }
}

function handleAddChildChapter(chapter: Chapter) {
  parentChapterId.value = chapter.id
  editingChapter.value = null
  showChapterEditor.value = true
}

function closeChapterEditor() {
  showChapterEditor.value = false
  editingChapter.value = null
  parentChapterId.value = null
}

async function handleSaveChapter(data: CreateChapterRequest | UpdateChapterRequest) {
  try {
    if (editingChapter.value) {
      await updateChapter(editingChapter.value.id, data as UpdateChapterRequest)
      notify.success(t('courseDetail.alert.updateChapterSuccess'))
    } else {
      await createChapter(data as CreateChapterRequest)
      notify.success(t('courseDetail.alert.createChapterSuccess'))
    }
    closeChapterEditor()
    await reloadChapters()
  } catch {
    notify.error(t('courseDetail.alert.saveChapterFailed'))
  }
}

function goToCourseBanks() {
  router.push({name: 'course-question-banks', params: {courseId: courseId.value}})
}

async function openClassSessionCreator() {
  if (route.name !== 'course-class-sessions') {
    await router.push({name: 'course-class-sessions', params: {id: courseId.value}, query: {create: '1'}})
  }
  classSessionCreateRequestKey.value += 1
}

async function ensureActiveTabData() {
  switch (activeTabKey.value) {
    case 'chapters':
      if (!chaptersLoaded.value && !chaptersLoading.value) await reloadChapters()
      break
    case 'banks':
      if (!banksLoaded.value && !banksLoading.value) await reloadBanks()
      break
    case 'files':
      if (!filesLoaded.value && !filesLoading.value) await reloadFiles()
      break
    case 'students':
      if (!studentsLoaded.value && !studentsLoading.value) await reloadStudents()
      break
  }
}

async function reloadActiveTabData() {
  switch (activeTabKey.value) {
    case 'overview':
      await reloadCourse()
      break
    case 'chapters':
      await reloadChapters()
      break
    case 'banks':
      await reloadBanks()
      break
    case 'files':
      await reloadFiles()
      break
    case 'students':
      await Promise.all([reloadStudents(), reloadCourse()])
      break
    case 'assistants':
      await reloadCourse()
      break
  }
}

async function handleListPageChange(page: number) {
  switch (activeTabKey.value) {
    case 'banks':
      await reloadBanks(page)
      break
    case 'files':
      await reloadFiles(page)
      break
    case 'students':
      await reloadStudents(page)
      break
  }
}

async function reloadChapters() {
  const targetCourseId = courseId.value
  chaptersLoading.value = true
  try {
    const tree = await getChapterTree(targetCourseId)
    if (targetCourseId !== courseId.value) return
    chapterTree.value = tree
    chaptersLoaded.value = true
  } finally {
    if (targetCourseId === courseId.value) chaptersLoading.value = false
  }
}

async function reloadBanks(page = banksPage.value) {
  const targetCourseId = courseId.value
  if (banksLoading.value) return
  banksLoading.value = true
  try {
    let nextPage = page
    let resp = await getQuestionBanks({courseId: targetCourseId, page: nextPage, size: DETAIL_PAGE_SIZE})
    while ((resp.records || []).length === 0 && resp.total > 0 && nextPage > 1) {
      nextPage -= 1
      resp = await getQuestionBanks({courseId: targetCourseId, page: nextPage, size: DETAIL_PAGE_SIZE})
    }
    if (targetCourseId !== courseId.value) return
    courseBanks.value = resp.records || []
    banksPage.value = resp.page
    banksTotal.value = resp.total
    banksLoaded.value = true
  } finally {
    if (targetCourseId === courseId.value) banksLoading.value = false
  }
}

async function reloadFiles(page = filesPage.value) {
  const targetCourseId = courseId.value
  if (filesLoading.value) return
  filesLoading.value = true
  try {
    let nextPage = page
    let resp = await listCourseFiles(targetCourseId, nextPage, DETAIL_PAGE_SIZE)
    while ((resp.records || []).length === 0 && resp.total > 0 && nextPage > 1) {
      nextPage -= 1
      resp = await listCourseFiles(targetCourseId, nextPage, DETAIL_PAGE_SIZE)
    }
    if (targetCourseId !== courseId.value) return
    courseFiles.value = resp.records || []
    filesPage.value = resp.page
    filesTotal.value = resp.total
    filesLoaded.value = true
  } finally {
    if (targetCourseId === courseId.value) filesLoading.value = false
  }
}

async function reloadStudents(page = studentsPage.value) {
  const targetCourseId = courseId.value
  if (studentsLoading.value) return
  if (!canViewStudents.value) {
    courseStudents.value = []
    studentsTotal.value = 0
    studentsLoaded.value = true
    return
  }
  studentsLoading.value = true
  try {
    let nextPage = page
    let resp = await getCourseEnrollments(targetCourseId, nextPage, DETAIL_PAGE_SIZE)
    while ((resp.records || []).length === 0 && resp.total > 0 && nextPage > 1) {
      nextPage -= 1
      resp = await getCourseEnrollments(targetCourseId, nextPage, DETAIL_PAGE_SIZE)
    }
    if (targetCourseId !== courseId.value) return
    courseStudents.value = resp.records || []
    studentsPage.value = resp.page
    studentsTotal.value = resp.total
    studentsLoaded.value = true
  } catch {
    if (targetCourseId === courseId.value) {
      courseStudents.value = []
      studentsTotal.value = 0
    }
  } finally {
    if (targetCourseId === courseId.value) studentsLoading.value = false
  }
}

async function reloadCourse() {
  const targetCourseId = courseId.value
  const courseData = await getCourse(targetCourseId)
  if (targetCourseId === courseId.value) course.value = courseData
}

function formatDate(dateStr?: string | null) {
  if (!dateStr) return '-'
  const date = new Date(dateStr)
  if (Number.isNaN(date.getTime())) return '-'
  return new Intl.DateTimeFormat(String(locale.value), {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(date)
}

function useFallbackImage(event: Event, fallback: string) {
  const image = event.target as HTMLImageElement
  if (image.dataset.fallbackApplied === 'true') return
  image.dataset.fallbackApplied = 'true'
  image.src = fallback
}
</script>

<style scoped>
.course-detail-page {
  width: 100%;
  max-width: 1280px;
  margin: 0 auto;
  padding: 10px 0 48px;
  background: var(--color-surface-canvas);
  box-shadow: 0 0 0 100vmax var(--color-surface-canvas);
  clip-path: inset(0 -100vmax);
  color: var(--color-on-surface);
}

.back-link {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  min-height: 32px;
  padding: 0;
  margin-bottom: 24px;
  background: none;
  border: 0;
  color: var(--color-muted);
  cursor: pointer;
  font-family: var(--font-label);
  font-size: 13px;
  font-weight: 700;
  transition: color 0.2s ease, transform 0.2s ease;
}

.back-link:hover,
.back-link:focus-visible {
  color: var(--color-on-surface);
  outline: none;
}

.back-link:active,
.primary-action:active,
.secondary-action:active,
.btn-add:active,
.btn-practice:active,
.text-action:active {
  transform: translateY(1px);
}

.detail-layout {
  display: block;
}

.detail-canvas {
  display: grid;
  grid-template-columns: repeat(12, minmax(0, 1fr));
  gap: 48px;
  align-items: start;
}

.course-main-column,
.course-side-column {
  display: flex;
  flex-direction: column;
  min-width: 0;
  gap: 32px;
}

.course-main-column {
  grid-column: span 7;
}

.course-side-column {
  grid-column: span 5;
}

.back-link {
  gap: 8px;
  min-height: 18px;
  margin: 0 0 -8px;
  color: var(--color-muted);
  font-size: 12px;
  font-weight: 400;
  line-height: 1;
  letter-spacing: 0;
  text-transform: none;
}

.course-hero {
  display: flex;
  min-height: 0;
  flex-direction: column;
  gap: 16px;
  padding: 0;
  background: transparent;
  border: 0;
  border-radius: 0;
}

.hero-kicker,
.metadata-item dt,
.panel-kicker,
.admin-actions h3,
.panel-header span,
.rail-label,
.overview-item span,
.hero-facts dt,
.chapter-summary span {
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 12px;
  font-weight: 400;
  line-height: 1;
  letter-spacing: 0.05em;
  text-transform: uppercase;
}

.course-hero h1 {
  max-width: 680px;
  margin: 0;
  color: var(--color-on-surface);
  font-family: var(--font-heading);
  font-size: clamp(36px, 4.3vw, 48px);
  font-weight: 400;
  letter-spacing: 0;
  line-height: 1.3;
  text-wrap: balance;
}

.course-description {
  max-width: 65ch;
  margin: 0;
  color: var(--color-on-surface-variant);
  font-family: var(--font-body);
  font-size: 18px;
  font-weight: 400;
  line-height: 1.6;
}

.metadata-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin: 16px 0 0;
  padding: 0;
}

.metadata-item {
  min-width: 0;
  padding: 12px;
  background: var(--color-surface-container);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
}

.metadata-item dt {
  margin: 0 0 8px;
}

.metadata-item dd {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  margin: 0;
  overflow: hidden;
  color: var(--color-on-surface);
  font-family: var(--font-body);
  font-size: 14px;
  font-weight: 500;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.metadata-item svg {
  flex: 0 0 auto;
}

.primary-action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 46px;
  width: auto;
  padding: 0 32px;
  align-self: flex-start;
  background: var(--color-primary);
  border: 1px solid var(--color-primary);
  border-radius: var(--radius-sm);
  color: var(--color-on-primary);
  cursor: pointer;
  font-family: var(--font-label);
  font-size: 14px;
  font-weight: 400;
  letter-spacing: 0.05em;
  line-height: 1;
  text-transform: none;
  transition: background 0.2s ease, border-color 0.2s ease, opacity 0.2s ease, transform 0.2s ease;
}

.hero-action {
  width: 100%;
  align-self: stretch;
  margin-top: 4px;
}

.primary-action:hover:not(:disabled) {
  background: var(--color-primary-soft);
  border-color: var(--color-primary-soft);
}

.primary-action:disabled {
  cursor: not-allowed;
  opacity: 0.46;
}

.tab-nav {
  display: flex;
  gap: 32px;
  padding: 0;
  overflow-x: auto;
  background: transparent;
  border: 0;
  border-bottom: 1px solid var(--color-outline-light);
  border-radius: 0;
}

.tab-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  min-width: max-content;
  min-height: 42px;
  padding: 0 0 11px;
  background: transparent;
  border: 0;
  border-bottom: 2px solid transparent;
  border-radius: 0;
  color: var(--color-muted);
  cursor: pointer;
  font-family: var(--font-label);
  font-size: 14px;
  font-weight: 400;
  line-height: 1;
  text-decoration: none;
  transition: background 0.2s ease, color 0.2s ease;
}

.tab-btn:hover,
.tab-btn:focus-visible {
  background: transparent;
  color: var(--color-on-surface);
}

.tab-btn.active {
  background: transparent;
  border-bottom-color: var(--color-primary);
  color: var(--color-on-surface);
}

.tab-content {
  min-height: 360px;
}

.tab-panel {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.cover-frame {
  width: 100%;
  aspect-ratio: 4 / 3;
  overflow: hidden;
  background: var(--color-surface-container-high);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
}

.cover-frame img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.teaching-team-panel,
.enrollment-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 16px;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
}

.team-lead {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--color-outline-light);
}

.team-copy {
  min-width: 0;
}

.team-copy strong {
  display: block;
  overflow: hidden;
  color: var(--color-on-surface);
  font-family: var(--font-label);
  font-size: 14px;
  font-weight: 400;
  line-height: 1.2;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.team-copy span,
.assistant-row > span {
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 14px;
  line-height: 1.4;
}

.assistant-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.assistant-stack {
  display: flex;
  flex-direction: row-reverse;
  justify-content: flex-end;
}

.assistant-chip {
  margin-left: -8px;
}

.assistant-chip:last-child {
  margin-left: 0;
}

.assistant-chip.empty {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  margin-left: 0;
  background: var(--color-surface-container-high);
  border: 1px solid var(--color-outline-light);
  border-radius: 50%;
  color: var(--color-muted);
}

.capacity-block,
.admin-actions,
.date-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.capacity-block,
.admin-actions {
  padding-bottom: 16px;
  border-bottom: 1px solid var(--color-outline-light);
}

.year-badge {
  display: inline-flex;
  width: fit-content;
  min-height: 24px;
  align-items: center;
  padding: 0 8px;
  background: var(--color-surface-container);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  color: var(--color-on-surface);
  font-family: var(--font-label);
  font-size: 12px;
  font-weight: 500;
  line-height: 1;
}

.capacity-block h3 {
  margin: 0;
  color: var(--color-on-surface);
  font-family: var(--font-label);
  font-size: 14px;
  font-weight: 400;
}

.capacity-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 14px;
  line-height: 1.4;
}

.capacity-track {
  height: 4px;
  overflow: hidden;
  background: var(--color-surface-container-high);
  border-radius: 999px;
}

.capacity-track span {
  display: block;
  height: 100%;
  background: var(--color-primary);
}

.admin-actions h3 {
  margin: 0 0 2px;
}

.secondary-action {
  display: inline-flex;
  align-items: center;
  justify-content: flex-start;
  gap: 7px;
  min-height: 40px;
  width: 100%;
  padding: 0 12px;
  background: transparent;
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  color: var(--color-on-surface);
  cursor: pointer;
  font-family: var(--font-label);
  font-size: 14px;
  font-weight: 400;
  text-decoration: none;
  transition: background 0.2s ease, border-color 0.2s ease, color 0.2s ease, transform 0.2s ease;
}

.secondary-action:hover,
.secondary-action:focus-visible {
  background: var(--color-surface-container);
  border-color: var(--color-outline);
}

.date-list {
  padding-top: 0;
  border-top: 0;
}

.date-list div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.date-list span,
.date-list strong {
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 14px;
  font-weight: 400;
  line-height: 1.4;
}

.date-list strong {
  color: var(--color-on-surface);
  font-variant-numeric: tabular-nums;
}

.panel-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 18px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--color-outline-light);
}

.panel-header h2 {
  margin: 6px 0 0;
  color: var(--color-on-surface);
  font-family: var(--font-heading);
  font-size: 28px;
  font-weight: 400;
  line-height: 1.3;
}

.empty-state {
  min-height: 420px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 18px;
  color: var(--color-muted);
  text-align: center;
}

.empty-state h1 {
  margin: 0;
  color: var(--color-on-surface);
  font-family: var(--font-heading);
  font-size: clamp(32px, 5vw, 48px);
  font-weight: 400;
  line-height: 1.3;
}

.secondary-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  min-height: 44px;
  padding: 0 22px;
  background: transparent;
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  color: var(--color-on-surface);
  cursor: pointer;
  font-family: var(--font-label);
  font-size: 12px;
  font-weight: 800;
  text-decoration: none;
  transition: background 0.2s ease, border-color 0.2s ease, color 0.2s ease, transform 0.2s ease;
}

.secondary-button:hover {
  background: var(--color-surface-container-high);
  border-color: var(--color-outline);
}

.loading-layout {
  display: grid;
  grid-template-columns: minmax(0, 7fr) minmax(0, 5fr);
  gap: 48px;
  align-items: start;
}

.loading-main,
.skeleton-rail {
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
}

.loading-main {
  display: grid;
  gap: 16px;
  padding: 28px;
}

.skeleton-kicker {
  width: 140px;
  height: 16px;
}

.skeleton-title {
  width: min(760px, 90%);
  height: 88px;
  border-radius: var(--radius-sm);
}

.skeleton-cover {
  width: 100%;
  aspect-ratio: 4 / 3;
  border-radius: var(--radius-sm);
}

.skeleton-line {
  width: min(680px, 100%);
  height: 18px;
  border-radius: var(--radius-sm);
}

.skeleton-line.short {
  width: min(420px, 68%);
}

.skeleton-rail {
  min-height: 360px;
}

.shimmer {
  background: linear-gradient(
      110deg,
      var(--color-surface-container-high) 8%,
      var(--color-surface-canvas) 18%,
      var(--color-surface-container-high) 33%
  );
  background-size: 200% 100%;
}

@media (max-width: 1180px) {
  .detail-canvas,
  .loading-layout {
    grid-template-columns: 1fr;
    gap: 32px;
  }

  .course-main-column,
  .course-side-column {
    grid-column: auto;
  }
}

@media (max-width: 760px) {
  .course-detail-page {
    padding-bottom: 32px;
  }

  .course-main-column,
  .course-side-column {
    gap: 24px;
  }

  .course-hero h1 {
    font-size: 32px;
  }

  .course-description {
    font-size: 16px;
  }

  .metadata-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .hero-action {
    width: 100%;
  }

  .tab-nav {
    gap: 24px;
  }
}

@media (max-width: 520px) {
  .metadata-grid {
    grid-template-columns: 1fr;
  }

  .assistant-row,
  .date-list div {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
