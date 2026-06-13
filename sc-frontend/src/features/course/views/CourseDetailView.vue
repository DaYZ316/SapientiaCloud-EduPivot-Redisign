<template>
  <div class="course-detail-page">
    <button class="back-link" @click="router.push('/courses')">
      <ArrowLeft :size="16"/>
      {{ t('courseDetail.backToCourses') }}
    </button>

    <div v-if="loading" class="detail-grid" aria-label="Loading course">
      <section class="primary-column">
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
      <button class="secondary-button" @click="router.push('/courses')">{{ t('courseDetail.browseCourses') }}</button>
    </div>

    <div v-else class="detail-layout">
      <div class="course-hero">
        <div class="hero-content">
          <div class="metadata-chips">
            <span class="chip chip-solid">{{ getLevelLabel(course.level) }}</span>
            <span class="chip chip-muted">{{ getStatusLabel(course.status) }}</span>
          </div>
          <h1>{{ course.title }}</h1>
          <p class="course-description">{{ course.description || t('courseDetail.noDescription') }}</p>
          <div class="teacher-row">
            <div class="teacher-avatar">
              <img :src="course.teacherAvatar || teacherFallbackUrl" alt="Instructor avatar"/>
            </div>
            <div>
              <p class="teacher-label">{{ t('courseDetail.instructor') }}</p>
              <strong>{{ course.teacherName || t('courseDetail.unknownTeacher') }}</strong>
            </div>
          </div>
        </div>
        <div class="hero-cover">
          <img :src="course.coverUrl || courseCoverFallbackUrl" alt="Course cover"/>
        </div>
      </div>

      <div class="detail-grid">
        <section class="primary-column">
          <div class="tab-nav">
            <button
              v-for="tab in visibleTabs"
              :key="tab.key"
              class="tab-btn"
              :class="{active: activeTab === tab.key}"
              @click="activeTab = tab.key"
            >
              <component :is="tab.icon" :size="16"/>
              {{ tab.label }}
            </button>
          </div>

          <div class="tab-content">
            <div v-if="activeTab === 'chapters'" class="tab-panel">
              <div class="panel-header">
                <h3>{{ t('chapter.title') }}</h3>
                <button v-if="isTeacher" class="btn-add" @click="showChapterEditor = true">
                  <Plus :size="14"/>
                  {{ t('chapter.addChapter') }}
                </button>
              </div>
              <ChapterTree
                :chapters="chapterTree"
                :is-editable="isTeacher"
                @select="handleChapterSelect"
                @edit="handleEditChapter"
                @delete="handleDeleteChapter"
                @add-child="handleAddChildChapter"
              />
            </div>

            <div v-if="activeTab === 'forums'" class="tab-panel">
              <div class="panel-header">
                <h3>{{ t('forum.commentsTitle') }}</h3>
              </div>
              <CourseComments :course-id="courseId" :can-comment="canComment"/>
            </div>

            <div v-if="activeTab === 'banks'" class="tab-panel">
              <div class="panel-header">
                <h3>{{ t('questionBank.title') }}</h3>
                <button v-if="isTeacher" class="btn-add" @click="router.push('/courses/' + courseId + '/question-banks')">
                  <Plus :size="14"/>
                  {{ t('questionBank.newBank') }}
                </button>
              </div>
              <div v-if="courseBanks.length === 0" class="empty-tab">
                <Database :size="28" stroke-width="1.4"/>
                <p>{{ t('questionBank.noBanks') }}</p>
              </div>
              <div v-else class="bank-list">
                <div
                  v-for="bank in courseBanks"
                  :key="bank.id"
                  class="bank-item"
                  @click="router.push('/question-banks/' + bank.id)"
                >
                  <div class="bank-icon"><Database :size="18"/></div>
                  <div class="bank-info">
                    <h4>{{ bank.bankName }}</h4>
                    <p>{{ bank.questionCount }} {{ t('questionBank.questionCount') }}</p>
                  </div>
                  <button v-if="isStudent" class="btn-practice" @click.stop="router.push('/question-banks/' + bank.id + '/practice')">
                    {{ t('questionBank.practice') }}
                  </button>
                </div>
              </div>
            </div>

            <div v-if="activeTab === 'files'" class="tab-panel">
              <div class="panel-header">
                <h3>{{ t('courseDetail.files') }}</h3>
              </div>
              <div v-if="courseFiles.length === 0" class="empty-tab">
                <FolderOpen :size="28" stroke-width="1.4"/>
                <p>{{ t('chapter.noAttachments') }}</p>
              </div>
              <div v-else class="file-list">
                <a
                  v-for="file in courseFiles"
                  :key="file.id"
                  :href="file.url || '#'"
                  target="_blank"
                  class="file-item"
                >
                  <FileDown :size="16"/>
                  <span>{{ file.displayName }}</span>
                  <span class="file-visibility">{{ file.visibility }}</span>
                </a>
              </div>
            </div>
            <div v-if="activeTab === 'students'" class="tab-panel">
              <div class="panel-header">
                <h3>{{ t('courseDetail.studentsTab') }}</h3>
                <span class="count-badge">{{ courseStudents.length }}</span>
              </div>
              <div v-if="courseStudents.length === 0" class="empty-tab">
                <Users :size="28" stroke-width="1.4"/>
                <p>{{ t('courseDetail.noStudents') }}</p>
              </div>
              <div v-else class="member-list">
                <div v-for="s in courseStudents" :key="s.id" class="member-item">
                  <div class="member-avatar">
                    <img v-if="s.avatarUrl" :src="s.avatarUrl" alt=""/>
                    <User v-else :size="18"/>
                  </div>
                  <div class="member-info">
                    <strong>{{ s.displayName || s.email }}</strong>
                    <span>{{ s.email }}</span>
                  </div>
                  <span class="member-status" :class="'status-' + s.status">{{ EnrollmentStatus[s.status] || '--' }}</span>
                </div>
              </div>
            </div>

            <div v-if="activeTab === 'assistants'" class="tab-panel">
              <div class="panel-header">
                <h3>{{ t('courseDetail.assistantsTab') }}</h3>
                <span class="count-badge">{{ courseAssistants.length }}</span>
              </div>
              <div v-if="courseAssistants.length === 0" class="empty-tab">
                <UserCheck :size="28" stroke-width="1.4"/>
                <p>{{ t('courseDetail.noAssistants') }}</p>
              </div>
              <div v-else class="member-list">
                <div v-for="a in courseAssistants" :key="a.id" class="member-item">
                  <div class="member-avatar">
                    <img v-if="a.avatarUrl" :src="a.avatarUrl" alt=""/>
                    <User v-else :size="18"/>
                  </div>
                  <div class="member-info">
                    <strong>{{ a.displayName || a.email }}</strong>
                    <span>{{ a.email }}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </section>

        <aside class="action-rail">
          <section>
            <h2>{{ t('courseDetail.enrollment') }}</h2>
            <div class="capacity-header">
              <span>{{ t('courseDetail.capacity') }}</span>
              <strong>{{ course.currentStudents }}{{ capacitySuffix }} {{ t('courseDetail.students') }}</strong>
            </div>
            <div class="capacity-track">
              <span :style="{width:  + capacityPercent + '%'}"></span>
            </div>
          </section>

          <div class="date-list">
            <div>
              <span><CalendarDays :size="18"/> {{ t('courseDetail.created') }}</span>
              <strong>{{ formatDate(course.createdAt) }}</strong>
            </div>
            <div>
              <span><RefreshCw :size="18"/> {{ t('courseDetail.updated') }}</span>
              <strong>{{ course.updatedAt ? formatDate(course.updatedAt) : t('courseDetail.noUpdates') }}</strong>
            </div>
          </div>

          <button
            v-if="canEnroll"
            class="enroll-button"
            :disabled="enrolling || isFull || course.enrolled"
            @click="handleEnroll"
          >
            {{ enrollLabel }}
          </button>
        </aside>
      </div>
    </div>

    <ChapterEditor
      :visible="showChapterEditor"
      :course-id="courseId"
      :chapter="editingChapter"
      :parent-chapter-id="parentChapterId"
      @close="closeChapterEditor"
      @save="handleSaveChapter"
    />
  </div>
</template>

<script lang="ts" setup>
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import {
  ArrowLeft, BookOpen, CalendarDays, RefreshCw, Plus,
  MessageCircle, Users, UserCheck, User, Database, FolderOpen, FileDown,
} from 'lucide-vue-next'

import {enroll, getCourse, listCourseFiles, getCourseEnrollments} from '@/features/course/api/course'
import {getChapterTree, createChapter, updateChapter, deleteChapter} from '@/features/course/api/chapter'
import {getCourseQuestionBanks} from '@/features/question-bank/api/questionBank'
import {notify} from '@/shared/composables/useGlobalNotification'
import {useAuthStore} from '@/features/auth/stores/auth'

import type {Chapter, CreateChapterRequest, UpdateChapterRequest} from '@/features/course/types/chapter'
import type {QuestionBank} from '@/features/question-bank/types/questionBank'
import type {CourseFile} from '@/features/course/types/course'
import {EnrollmentStatus} from '@/features/course/types/course'

import ChapterTree from '@/features/course/components/ChapterTree.vue'
import ChapterEditor from '@/features/course/components/ChapterEditor.vue'
import CourseComments from '@/features/forum/components/CourseComments.vue'


const {t} = useI18n()
const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const courseId = route.params.id as string

const loading = ref(true)
const enrolling = ref(false)
const course = ref<any>(null)
const chapterTree = ref<Chapter[]>([])
const courseBanks = ref<QuestionBank[]>([])
const courseFiles = ref<CourseFile[]>([])
const courseStudents = ref<any[]>([])
const courseAssistants = ref<any[]>([])
const activeTab = ref('chapters')
const showChapterEditor = ref(false)
const editingChapter = ref<Chapter | null>(null)
const parentChapterId = ref<string | null>(null)

const isTeacher = computed(() => authStore.user?.role === 2)
const isStudent = computed(() => authStore.user?.role === 1)
const isAdmin = computed(() => authStore.user?.role === 0)

const canEnroll = computed(() => isStudent.value && !course.value?.enrolled)
const canComment = computed(() => {
  const userId = authStore.user?.id
  if (!userId || !course.value) return false
  return isAdmin.value
    || course.value.teacherId === userId
    || Boolean(course.value.enrolled)
    || Boolean(course.value.teacherIds?.includes(userId))
})
const isFull = computed(() => course.value && course.value.maxStudents > 0 && course.value.currentStudents >= course.value.maxStudents)

const capacityPercent = computed(() => {
  if (!course.value || !course.value.maxStudents) return 0
  return Math.min((course.value.currentStudents / course.value.maxStudents) * 100, 100)
})
const capacitySuffix = computed(() => course.value?.maxStudents ? '/' + course.value.maxStudents : '')

const enrollLabel = computed(() => {
  if (course.value?.enrolled) return t('courseDetail.enrolled')
  if (isFull.value) return t('courseDetail.full')
  return t('courseDetail.enroll')
})

const courseCoverFallbackUrl = '/assets/course-cover-fallback.png'
const teacherFallbackUrl = '/assets/avatar-teacher-default.png'

const tabs = [
  {key: 'chapters', label: '章节', icon: BookOpen, roles: [0, 1, 2]},
  {key: 'forums', label: '评论', icon: MessageCircle, roles: [0, 1, 2]},
  {key: 'banks', label: '题库', icon: Database, roles: [0, 1, 2]},
  {key: 'files', label: '文件', icon: FolderOpen, roles: [0, 1, 2]},
  {key: 'students', label: t('courseDetail.studentsTab'), icon: Users, roles: [0, 2]},
  {key: 'assistants', label: t('courseDetail.assistantsTab'), icon: UserCheck, roles: [0, 2]},
]

const visibleTabs = computed(() => {
  const role = authStore.user?.role ?? 1
  return tabs.filter(tab => tab.roles.includes(role))
})

function getLevelLabel(level: number) {
  const labels: Record<number, string> = {1: '初级', 2: '中级', 3: '高级'}
  return labels[level] || '未知'
}

function getStatusLabel(status: number) {
  const labels: Record<number, string> = {0: '草稿', 1: '已发布', 2: '已归档'}
  return labels[status] || '未知'
}

function formatDate(dateStr: string) {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleDateString()
}

onMounted(async () => {
  try {
const [courseData, chapters, banks, files, studentsResp] = await Promise.all([
      getCourse(courseId),
      getChapterTree(courseId),
      getCourseQuestionBanks(courseId),
      listCourseFiles(courseId),
      getCourseEnrollments(courseId, 1, 200),
    ])
    course.value = courseData
    chapterTree.value = chapters || []
    courseBanks.value = banks || []
    courseFiles.value = files || []
    courseStudents.value = studentsResp?.records || []
    courseAssistants.value = courseData.teacherInfos || []





  } finally {
    loading.value = false
  }
})

async function handleEnroll() {
  enrolling.value = true
  try {
    await enroll({courseId})
    if (course.value) course.value.enrolled = true
    if (course.value) course.value.currentStudents++
    notify.success('选课成功')
  } catch {
    notify.error('选课失败')
  } finally {
    enrolling.value = false
  }
}

function handleChapterSelect(chapter: Chapter) {
  router.push('/courses/' + courseId + '/chapters/' + chapter.id)
}

function handleEditChapter(chapter: Chapter) {
  editingChapter.value = chapter
  showChapterEditor.value = true
}

async function handleDeleteChapter(chapter: Chapter) {
  if (!confirm(t('chapter.confirmDelete'))) return
  try {
    await deleteChapter(chapter.id)
    notify.success('删除成功')
    chapterTree.value = await getChapterTree(courseId)
  } catch {
    notify.error('删除失败')
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
      notify.success('更新成功')
    } else {
      await createChapter(data as CreateChapterRequest)
      notify.success('创建成功')
    }
    closeChapterEditor()
    chapterTree.value = await getChapterTree(courseId)
  } catch {
    notify.error('操作失败')
  }
}
</script>

<style scoped>
.course-detail-page { max-width: 100%; }

.back-link {
  display: inline-flex; align-items: center; gap: 6px; padding: 0;
  border: none; background: none; color: var(--color-muted);
  font-family: var(--font-body); font-size: 13px; cursor: pointer; margin-bottom: 32px;
}
.back-link:hover { color: var(--color-on-surface); }

.detail-layout { display: flex; flex-direction: column; gap: 32px; }

.course-hero {
  display: grid; grid-template-columns: 1fr 400px; gap: 40px;
  padding: 32px; background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light); border-radius: var(--radius-lg);
}

.hero-content { display: flex; flex-direction: column; gap: 16px; }
.hero-content h1 { margin: 0; font-family: var(--font-heading); font-size: 40px; font-weight: 600; color: var(--color-on-surface); line-height: 1.2; }
.course-description { margin: 0; font-family: var(--font-body); font-size: 15px; color: var(--color-muted); line-height: 1.6; }

.metadata-chips { display: flex; gap: 8px; }
.chip { font-family: var(--font-body); font-size: 12px; font-weight: 600; padding: 4px 12px; border-radius: 6px; }
.chip-solid { background: var(--color-primary); color: var(--color-on-primary); }
.chip-muted { background: var(--color-surface-container-high); color: var(--color-muted); }

.teacher-row { display: flex; align-items: center; gap: 12px; margin-top: 8px; }
.teacher-avatar { width: 40px; height: 40px; border-radius: 50%; overflow: hidden; background: var(--color-surface-container-high); }
.teacher-avatar img { width: 100%; height: 100%; object-fit: cover; }
.teacher-label { margin: 0; font-family: var(--font-body); font-size: 12px; color: var(--color-muted); }
.teacher-row strong { font-family: var(--font-body); font-size: 15px; color: var(--color-on-surface); }

.hero-cover { border-radius: var(--radius-lg); overflow: hidden; aspect-ratio: 16/9; }
.hero-cover img { width: 100%; height: 100%; object-fit: cover; }

.detail-grid { display: grid; grid-template-columns: 1fr 320px; gap: 32px; }

.tab-nav {
  display: flex; gap: 2px; margin-bottom: 24px;
  background: var(--color-surface-card); border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-md); padding: 4px;
}

.tab-btn {
  flex: 1; display: inline-flex; align-items: center; justify-content: center; gap: 6px;
  padding: 10px 16px; border: none; background: none; color: var(--color-muted);
  font-family: var(--font-body); font-size: 13px; font-weight: 500;
  cursor: pointer; border-radius: var(--radius-sm); transition: background 0.15s, color 0.15s;
}
.tab-btn.active { background: var(--color-surface-container-high); color: var(--color-on-surface); }

.tab-content { min-height: 300px; }

.tab-panel { display: flex; flex-direction: column; gap: 16px; }

.panel-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.panel-header h3 { margin: 0; font-family: var(--font-heading); font-size: 20px; font-weight: 600; color: var(--color-on-surface); }

.btn-add {
  display: inline-flex; align-items: center; gap: 6px; padding: 6px 14px;
  border: 1px solid var(--color-outline-light); border-radius: var(--radius-sm);
  background: none; color: var(--color-on-surface); font-family: var(--font-body);
  font-size: 12px; font-weight: 600; cursor: pointer; transition: background 0.15s;
}
.btn-add:hover { background: var(--color-surface-container); }

.empty-tab {
  display: flex; flex-direction: column; align-items: center; gap: 12px;
  padding: 48px 24px; color: var(--color-muted); text-align: center;
}
.empty-tab p { margin: 0; font-family: var(--font-body); font-size: 14px; }

.forum-list, .bank-list, .file-list { display: flex; flex-direction: column; gap: 8px; }

.forum-item, .bank-item, .file-item {
  display: flex; align-items: center; gap: 12px; padding: 14px 16px;
  background: var(--color-surface-card); border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm); cursor: pointer; transition: border-color 0.15s; text-decoration: none; color: inherit;
}
.forum-item:hover, .bank-item:hover, .file-item:hover { border-color: var(--color-on-surface); }

.forum-icon, .bank-icon {
  width: 36px; height: 36px; display: grid; place-items: center;
  background: var(--color-surface-container-high); border-radius: 10px; color: var(--color-on-surface); flex-shrink: 0;
}

.forum-info, .bank-info { flex: 1; min-width: 0; }
.forum-info h4, .bank-info h4 { margin: 0; font-family: var(--font-body); font-size: 14px; font-weight: 600; color: var(--color-on-surface); }
.forum-info p, .bank-info p { margin: 2px 0 0; font-family: var(--font-body); font-size: 12px; color: var(--color-muted); }

.forum-stats { font-family: var(--font-body); font-size: 12px; color: var(--color-muted); }

.btn-practice {
  padding: 6px 14px; border: 1px solid var(--color-outline-light); border-radius: var(--radius-sm);
  background: none; color: var(--color-on-surface); font-family: var(--font-body);
  font-size: 12px; font-weight: 600; cursor: pointer; transition: background 0.15s;
}
.btn-practice:hover { background: var(--color-surface-container); }

.file-item span { font-family: var(--font-body); font-size: 13px; color: var(--color-on-surface); }
.file-visibility { font-size: 11px !important; color: var(--color-muted) !important; margin-left: auto; }

.action-rail {
  display: flex; flex-direction: column; gap: 24px;
  background: var(--color-surface-card); border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-lg); padding: 28px; position: sticky; top: 24px; height: fit-content;
}
.action-rail section { display: flex; flex-direction: column; gap: 12px; }
.action-rail h2 { margin: 0; font-family: var(--font-heading); font-size: 20px; font-weight: 600; color: var(--color-on-surface); }

.capacity-header { display: flex; justify-content: space-between; align-items: baseline; }
.capacity-header span { font-family: var(--font-body); font-size: 13px; color: var(--color-muted); }
.capacity-header strong { font-family: var(--font-body); font-size: 16px; font-weight: 400; color: var(--color-primary); }

.capacity-track { height: 4px; overflow: hidden; background: var(--color-surface-container-high); }
.capacity-track span { display: block; height: 100%; background: var(--color-primary); transition: width 0.2s ease; }

.date-list { display: flex; flex-direction: column; gap: 12px; padding: 24px 0; border-top: 1px solid var(--color-outline-light); border-bottom: 1px solid var(--color-outline-light); }
.date-list div { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.date-list span { display: inline-flex; align-items: center; gap: 8px; font-family: var(--font-body); font-size: 13px; color: var(--color-muted); }
.date-list strong { color: var(--color-primary); font-family: var(--font-body); font-size: 14px; font-weight: 400; }

.enroll-button, .secondary-button {
  min-height: 48px; border-radius: var(--radius-sm); font-family: var(--font-body);
  font-size: 12px; font-weight: 600; letter-spacing: 0.1em; text-transform: uppercase;
  cursor: pointer; transition: background 0.2s, color 0.2s, opacity 0.2s;
}
.enroll-button { width: 100%; padding: 16px 24px; border: none; background: var(--color-primary); color: var(--color-on-primary); }
.enroll-button:hover:not(:disabled) { background: var(--color-outline); }
.enroll-button:disabled { opacity: 0.5; cursor: not-allowed; }

.secondary-button { padding: 0 24px; border: 1px solid var(--color-primary); background: transparent; color: var(--color-primary); }
.secondary-button:hover { background: var(--color-primary); color: var(--color-on-primary); }

.empty-state { min-height: 420px; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 18px; color: var(--color-on-surface-variant); }
.empty-state h1 { margin: 0; color: var(--color-primary); font-family: var(--font-heading); font-size: clamp(32px, 5vw, 48px); font-weight: 600; }

.skeleton-title { width: min(760px, 90%); height: 86px; border-radius: 24px; }
.skeleton-cover { aspect-ratio: 16 / 9; width: 100%; border-radius: 48px; }
.skeleton-line { width: min(680px, 100%); height: 20px; border-radius: 8px; }
.skeleton-line.short { width: min(420px, 68%); }
.skeleton-rail { min-height: 360px; }

.shimmer { background: linear-gradient(110deg, var(--color-surface-container-high) 8%, var(--color-surface-canvas) 18%, var(--color-surface-container-high) 33%); background-size: 200% 100%; animation: shimmer 1.4s ease-in-out infinite; }
@keyframes shimmer { to { background-position-x: -200%; } }

@media (max-width: 1024px) {
  .course-hero { grid-template-columns: 1fr; }
  .detail-grid { grid-template-columns: 1fr; }
  .action-rail { position: static; }
}

@media (max-width: 640px) {
  .course-hero { padding: 20px; }
  .hero-content h1 { font-size: 28px; }
  .tab-btn { font-size: 12px; padding: 8px 10px; }
}
.member-list { display: flex; flex-direction: column; gap: 0; }
.member-item {
  display: flex; align-items: center; gap: 12px;
  padding: 10px 0; border-bottom: 1px solid var(--color-border, #2a2a2a);
}
.member-item:last-child { border-bottom: none; }
.member-avatar {
  width: 36px; height: 36px; border-radius: 50%; overflow: hidden;
  background: var(--color-surface, #1a1a1a); display: flex; align-items: center; justify-content: center;
  flex-shrink: 0; color: var(--color-muted);
}
.member-avatar img { width: 100%; height: 100%; object-fit: cover; }
.member-info { flex: 1; min-width: 0; }
.member-info strong { display: block; font-size: 14px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.member-info span { font-size: 12px; color: var(--color-muted); }
.count-badge {
  background: var(--color-surface, #1a1a1a); padding: 2px 8px; border-radius: 10px;
  font-size: 12px; color: var(--color-muted);
}
</style>
