<template>
  <section class="tab-panel class-session-panel">
    <header class="workspace-header">
      <div>
        <span class="workspace-kicker">{{ t('courseDetail.classSession.tabKicker') }}</span>
        <h2>{{ t('courseDetail.classSessionsTab') }}</h2>
        <p>{{ course.description || t('courseDetail.noDescription') }}</p>
      </div>
    </header>

    <div class="session-metrics">
      <div v-for="metric in sessionMetrics" :key="metric.label" class="metric-item">
        <span>{{ metric.label }}</span>
        <strong>{{ metric.value }}</strong>
      </div>
    </div>

    <div class="session-workspace">
      <div class="session-list-column">
        <button
          v-if="canManageCourse"
          class="session-create-row"
          type="button"
          @click="openCreate"
        >
          <span class="create-row-icon">
            <Plus :size="15" stroke-width="2" />
          </span>
          <span>{{ t('courseDetail.classSession.createAction') }}</span>
        </button>

        <div v-if="loading" class="session-state">
          <LoaderCircle :size="24" stroke-width="1.7" />
          <p>{{ t('courseDetail.classSession.loading') }}</p>
        </div>

        <div v-else-if="loadFailed" class="session-state">
          <CircleAlert :size="28" stroke-width="1.5" />
          <h3>{{ t('courseDetail.classSession.loadFailed') }}</h3>
          <button class="btn-add" type="button" @click="loadSessions">
            {{ t('courseDetail.classSession.retry') }}
          </button>
        </div>

        <div v-else-if="sessions.length === 0" class="empty-tab">
          <Presentation :size="28" stroke-width="1.4" />
          <h3>{{ t('courseDetail.classSession.emptyTitle') }}</h3>
          <p>{{ canManageCourse ? t('courseDetail.classSession.emptyTeacher') : t('courseDetail.classSession.emptyStudent') }}</p>
        </div>

        <div v-else class="session-list">
          <article
            v-for="session in sessions"
            :key="session.id"
            class="session-item"
            :class="{ selected: previewSession?.id === session.id || editingSession?.id === session.id }"
            @click="selectSession(session)"
          >
            <div class="session-main">
              <div class="session-heading">
                <h3>{{ session.title }}</h3>
                <span class="status-badge" :class="statusClass(session)">
                  {{ session.statusText || fallbackStatusText(session.status) }}
                </span>
              </div>
              <p>{{ session.description || t('courseDetail.classSession.noDescription') }}</p>
              <div class="session-meta">
                <span>
                  <Clock :size="14" stroke-width="1.8" />
                  {{ formatDateTime(session.scheduledStartAt) }} - {{ formatDateTime(session.scheduledEndAt) }}
                </span>
                <span>
                  <DoorOpen :size="14" stroke-width="1.8" />
                  {{ roomSizeLabel(session.roomSize) }}
                </span>
              </div>
            </div>

            <div class="session-actions">
              <button
                v-if="canManageCourse"
                class="btn-icon danger"
                type="button"
                :title="t('courseDetail.classSession.deleteAction')"
                :disabled="busySessionId === session.id"
                @click.stop="handleDelete(session)"
              >
                <Trash2 :size="14" stroke-width="1.8" />
              </button>
            </div>
          </article>
        </div>
      </div>

      <aside v-if="canManageCourse || previewSession" class="session-editor-panel">
        <form class="session-form" @submit.prevent="handleSubmit">
          <div class="editor-heading">
            <h3>{{ panelTitle }}</h3>
            <div class="form-actions">
              <button v-if="!isPreviewing" type="submit" class="btn-add primary" :disabled="submitting">
                {{ submitting ? t('courseDetail.saving') : t('courseDetail.save') }}
              </button>
              <button
                v-if="editingSession"
                type="button"
                class="btn-secondary"
                :disabled="busySessionId === editingSession.id"
                @click="handlePublish(editingSession)"
              >
                {{ t('courseDetail.classSession.publishAction') }}
              </button>
              <button
                v-if="previewSession?.publishedAt"
                type="button"
                class="btn-add primary"
                @click="enterSession(previewSession)"
              >
                {{ t('courseDetail.classSession.enterAction') }}
              </button>
            </div>
          </div>

          <p v-if="errorMessage" class="form-error">{{ errorMessage }}</p>

          <section class="form-section">
            <div class="section-title">
              <span>01</span>
              <h4>{{ t('courses.modal.sections.basic') }}</h4>
            </div>

            <label class="form-group">
              <span>{{ t('courseDetail.classSession.titleLabel') }}</span>
              <input
                v-model="form.title"
                class="input-field"
                type="text"
                maxlength="200"
                :disabled="isPreviewing"
                :placeholder="t('courseDetail.classSession.titlePlaceholder')"
              />
            </label>

            <div class="form-grid">
              <label class="form-group">
                <span>{{ t('courseDetail.classSession.startAtLabel') }}</span>
                <BaseDatePicker
                  v-if="!isPreviewing"
                  v-model="form.scheduledStartAt"
                  class="modal-date-control"
                  show-time
                  default-time="09:00"
                  :placeholder="t('courseDetail.classSession.startAtLabel')"
                />
                <input
                  v-else
                  class="input-field"
                  type="text"
                  :value="formatDateTime(previewSession?.scheduledStartAt)"
                  disabled
                />
              </label>
              <label class="form-group">
                <span>{{ t('courseDetail.classSession.endAtLabel') }}</span>
                <BaseDatePicker
                  v-if="!isPreviewing"
                  v-model="form.scheduledEndAt"
                  class="modal-date-control"
                  show-time
                  default-time="10:00"
                  :placeholder="t('courseDetail.classSession.endAtLabel')"
                />
                <input
                  v-else
                  class="input-field"
                  type="text"
                  :value="formatDateTime(previewSession?.scheduledEndAt)"
                  disabled
                />
              </label>
            </div>

            <label class="form-group">
              <span>{{ t('courseDetail.classSession.descriptionLabel') }}</span>
              <textarea
                v-model="form.description"
                class="input-field"
                rows="4"
                maxlength="5000"
                :disabled="isPreviewing"
                :placeholder="t('courseDetail.classSession.descriptionPlaceholder')"
              ></textarea>
            </label>
          </section>

          <section class="form-section">
            <div class="section-title">
              <span>02</span>
              <h4>{{ t('courseDetail.classSession.roomSizeLabel') }}</h4>
            </div>

            <div class="room-spec-grid">
              <button
                v-for="spec in roomSpecs"
                :key="spec.value"
                class="room-spec"
                :class="{ active: form.roomSize === spec.value }"
                type="button"
                :disabled="isPreviewing"
                @click="form.roomSize = spec.value"
              >
                <span class="spec-check">
                  <Check v-if="form.roomSize === spec.value" :size="12" stroke-width="2.2" />
                </span>
                <strong>{{ spec.label }}</strong>
                <small>{{ t('courseDetail.capacity') }} {{ spec.capacity }}</small>
              </button>
            </div>

            <div class="seat-preview">
              <div class="preview-header">
                <span>{{ selectedRoomSpec.label }}</span>
                <strong>{{ selectedRoomSpec.capacity }}</strong>
              </div>

              <div v-if="form.roomSize === ClassRoomSize.XLARGE" class="arc-preview">
                <span
                  v-for="dot in arcDots"
                  :key="dot.id"
                  class="seat-dot"
                  :style="{ left: dot.x + '%', top: dot.y + '%' }"
                ></span>
              </div>
              <div
                v-else
                class="grid-preview"
                :style="{ gridTemplateColumns: `repeat(${selectedRoomSpec.cols}, minmax(0, 1fr))` }"
              >
                <span v-for="seat in selectedRoomSpec.seats" :key="seat" class="seat-dot"></span>
              </div>
            </div>
          </section>
        </form>
      </aside>

      <aside v-else class="session-editor-panel preview-only">
        <div class="editor-heading">
          <span>01</span>
          <div>
            <h3>{{ t('courseDetail.classSession.roomSizeLabel') }}</h3>
            <p>{{ t('courseDetail.classSession.emptyStudent') }}</p>
          </div>
        </div>
        <div class="seat-preview">
          <div class="preview-header">
            <span>{{ selectedRoomSpec.label }}</span>
            <strong>{{ selectedRoomSpec.capacity }}</strong>
          </div>
          <div
            class="grid-preview"
            :style="{ gridTemplateColumns: `repeat(${selectedRoomSpec.cols}, minmax(0, 1fr))` }"
          >
            <span v-for="seat in selectedRoomSpec.seats" :key="seat" class="seat-dot"></span>
          </div>
        </div>
      </aside>
    </div>
  </section>
</template>

<script lang="ts" setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import {
  Check,
  CircleAlert,
  Clock,
  DoorOpen,
  LoaderCircle,
  Plus,
  Presentation,
  Trash2,
} from 'lucide-vue-next'

import {
  createClassSession,
  deleteClassSession,
  getCourseClassSessions,
  publishClassSession,
  updateClassSession,
} from '@/features/course/api/classSession'
import BaseDatePicker from '@/shared/components/BaseDatePicker.vue'
import { confirmDialog } from '@/shared/composables/useConfirmDialog'
import { notify } from '@/shared/composables/useGlobalNotification'
import {
  ClassRoomSize,
  ClassRoomSizeLabel,
  ClassSessionStatus,
  type ClassSession,
  type ClassSessionFormPayload,
} from '@/features/course/types/classSession'
import type { CourseDetail } from '@/features/course/types/course'

const props = withDefaults(defineProps<{
  courseId: string
  course: CourseDetail
  canManageCourse: boolean
  createRequestKey?: number
}>(), {
  createRequestKey: 0,
})

const { t, locale } = useI18n()
const router = useRouter()

const sessions = ref<ClassSession[]>([])
const loading = ref(false)
const loadFailed = ref(false)
const submitting = ref(false)
const busySessionId = ref<string | null>(null)
const editingSession = ref<ClassSession | null>(null)
const previewSession = ref<ClassSession | null>(null)
const errorMessage = ref('')

const form = reactive({
  title: '',
  description: '',
  scheduledStartAt: '',
  scheduledEndAt: '',
  roomSize: ClassRoomSize.MEDIUM as number,
})

const roomSpecs = computed(() => [
  {
    value: ClassRoomSize.SMALL,
    label: t('courseDetail.classSession.roomSmall'),
    capacity: 12,
    rows: 4,
    cols: 3,
  },
  {
    value: ClassRoomSize.MEDIUM,
    label: t('courseDetail.classSession.roomMedium'),
    capacity: 64,
    rows: 8,
    cols: 8,
  },
  {
    value: ClassRoomSize.LARGE,
    label: t('courseDetail.classSession.roomLarge'),
    capacity: 160,
    rows: 10,
    cols: 16,
  },
  {
    value: ClassRoomSize.XLARGE,
    label: t('courseDetail.classSession.roomXLarge'),
    capacity: 250,
    rows: 0,
    cols: 0,
  },
])

const selectedRoomSpec = computed(() => {
  const spec = roomSpecs.value.find(item => item.value === form.roomSize) || roomSpecs.value[1]
  return {
    ...spec,
    seats: Array.from({ length: spec.rows * spec.cols }, (_, index) => index),
  }
})

const arcDots = computed(() => {
  const dots: Array<{ id: number; x: number; y: number }> = []
  let id = 0

  for (let ring = 0; ring < 5; ring += 1) {
    const count = 8 + ring * 4
    const radius = 20 + ring * 7
    for (let index = 0; index < count; index += 1) {
      const angle = 210 + (120 * index) / Math.max(count - 1, 1)
      const radians = (angle * Math.PI) / 180
      dots.push({
        id,
        x: 50 + Math.cos(radians) * radius,
        y: 72 + Math.sin(radians) * radius * 0.72,
      })
      id += 1
    }
  }

  return dots
})

const sessionMetrics = computed(() => [
  {
    label: t('courses.status.draft'),
    value: sessions.value.filter(session => !session.publishedAt).length,
  },
  {
    label: t('courseDetail.classSession.statusUpcoming'),
    value: sessions.value.filter(session => session.publishedAt && session.status === ClassSessionStatus.UPCOMING).length,
  },
  {
    label: t('courseDetail.classSession.statusLive'),
    value: sessions.value.filter(session => session.status === ClassSessionStatus.LIVE).length,
  },
  {
    label: t('courseDetail.classSession.statusFinished'),
    value: sessions.value.filter(session => session.status === ClassSessionStatus.FINISHED).length,
  },
])

const isPreviewing = computed(() => Boolean(previewSession.value))
const panelTitle = computed(() => {
  if (previewSession.value) return previewSession.value.title
  return editingSession.value ? t('courseDetail.classSession.editTitle') : t('courseDetail.classSession.createTitle')
})

onMounted(() => {
  resetForm()
  void loadSessions()
  if (props.createRequestKey > 0 && props.canManageCourse) {
    openCreate()
  }
})

watch(() => props.courseId, () => {
  void loadSessions()
})

watch(() => props.createRequestKey, (next, previous) => {
  if (next !== previous && props.canManageCourse) {
    openCreate()
  }
})

async function loadSessions() {
  loading.value = true
  loadFailed.value = false
  try {
    const response = await getCourseClassSessions(props.courseId, 1, 50)
    const editingId = editingSession.value?.id
    const previewId = previewSession.value?.id
    sessions.value = response.records || []
    if (editingId) {
      const nextEditing = sessions.value.find(session => session.id === editingId) || null
      if (nextEditing && isDraft(nextEditing)) {
        editingSession.value = nextEditing
        fillFormFromSession(nextEditing)
      } else {
        editingSession.value = null
        previewSession.value = nextEditing
        if (nextEditing) {
          fillFormFromSession(nextEditing)
        }
      }
    }
    if (previewId) {
      const nextPreview = sessions.value.find(session => session.id === previewId) || null
      previewSession.value = nextPreview
      if (nextPreview) {
        fillFormFromSession(nextPreview)
      }
    }
  } catch {
    loadFailed.value = true
  } finally {
    loading.value = false
  }
}

function resetForm() {
  errorMessage.value = ''
  form.title = ''
  form.description = ''
  form.scheduledStartAt = ''
  form.scheduledEndAt = ''
  form.roomSize = ClassRoomSize.MEDIUM
}

function openCreate() {
  previewSession.value = null
  editingSession.value = null
  resetForm()
}

function selectSession(session: ClassSession) {
  errorMessage.value = ''
  if (isDraft(session) && props.canManageCourse) {
    previewSession.value = null
    editingSession.value = session
  } else {
    previewSession.value = session
    editingSession.value = null
  }
  fillFormFromSession(session)
}

function fillFormFromSession(session: ClassSession) {
  form.title = session.title
  form.description = session.description || ''
  form.scheduledStartAt = toDatetimeLocalValue(session.scheduledStartAt)
  form.scheduledEndAt = toDatetimeLocalValue(session.scheduledEndAt)
  form.roomSize = normalizedRoomSize(session.roomSize)
}

async function handleSubmit() {
  const title = form.title.trim()
  if (!title) {
    errorMessage.value = t('courseDetail.classSession.titleRequired')
    return
  }
  if (!form.scheduledStartAt || !form.scheduledEndAt) {
    errorMessage.value = t('courseDetail.classSession.timeRequired')
    return
  }

  const startDate = new Date(form.scheduledStartAt)
  const endDate = new Date(form.scheduledEndAt)
  if (Number.isNaN(startDate.getTime()) || Number.isNaN(endDate.getTime())) {
    errorMessage.value = t('courseDetail.classSession.timeRequired')
    return
  }
  if (endDate <= startDate) {
    errorMessage.value = t('courseDetail.classSession.endAfterStart')
    return
  }

  const payload: ClassSessionFormPayload = {
    title,
    description: form.description.trim() || undefined,
    scheduledStartAt: startDate.toISOString(),
    scheduledEndAt: endDate.toISOString(),
    roomSize: normalizedRoomSize(form.roomSize),
  }

  errorMessage.value = ''
  submitting.value = true
  try {
    if (editingSession.value) {
      const editingId = editingSession.value.id
      await updateClassSession(editingSession.value.id, payload)
      notify.success(t('courseDetail.classSession.updated'))
      await loadSessions()
      const updatedSession = sessions.value.find(session => session.id === editingId)
      if (updatedSession) {
        selectSession(updatedSession)
      }
    } else {
      await createClassSession({ ...payload, courseId: props.courseId })
      notify.success(t('courseDetail.classSession.created'))
      openCreate()
      await loadSessions()
    }
  } catch {
    notify.error(t('courseDetail.classSession.saveFailed'))
  } finally {
    submitting.value = false
  }
}

async function handlePublish(session: ClassSession) {
  if (!(await confirmDialog({ message: t('courseDetail.classSession.confirmPublish') }))) return
  busySessionId.value = session.id
  try {
    await publishClassSession(session.id)
    notify.success(t('courseDetail.classSession.published'))
    await loadSessions()
    const publishedSession = sessions.value.find(item => item.id === session.id)
    if (publishedSession) {
      previewSession.value = publishedSession
      editingSession.value = null
      fillFormFromSession(publishedSession)
    }
  } catch {
    notify.error(t('courseDetail.classSession.publishFailed'))
  } finally {
    busySessionId.value = null
  }
}

async function handleDelete(session: ClassSession) {
  if (!(await confirmDialog({ message: t('courseDetail.classSession.confirmDelete'), confirmVariant: 'danger' }))) return
  busySessionId.value = session.id
  try {
    await deleteClassSession(session.id)
    notify.success(t('courseDetail.classSession.deleted'))
    if (editingSession.value?.id === session.id || previewSession.value?.id === session.id) {
      openCreate()
    }
    await loadSessions()
  } catch {
    notify.error(t('courseDetail.classSession.deleteFailed'))
  } finally {
    busySessionId.value = null
  }
}

function enterSession(session: ClassSession) {
  router.push({ name: 'class-session-room', params: { sessionId: session.id } })
}

function isDraft(session: ClassSession) {
  return !session.publishedAt
}

function statusClass(session: ClassSession) {
  if (!session.publishedAt) return 'draft'
  if (session.status === ClassSessionStatus.LIVE) return 'live'
  if (session.status === ClassSessionStatus.FINISHED) return 'finished'
  return 'upcoming'
}

function fallbackStatusText(status: number) {
  const labels: Record<number, string> = {
    [ClassSessionStatus.PREPARING]: t('courseDetail.classSession.statusPreparing'),
    [ClassSessionStatus.UPCOMING]: t('courseDetail.classSession.statusUpcoming'),
    [ClassSessionStatus.LIVE]: t('courseDetail.classSession.statusLive'),
    [ClassSessionStatus.FINISHED]: t('courseDetail.classSession.statusFinished'),
  }
  return labels[status] || t('courseDetail.statusUnknown')
}

function roomSizeLabel(roomSize: number) {
  const labels: Record<number, string> = {
    [ClassRoomSize.SMALL]: t('courseDetail.classSession.roomSmall'),
    [ClassRoomSize.MEDIUM]: t('courseDetail.classSession.roomMedium'),
    [ClassRoomSize.LARGE]: t('courseDetail.classSession.roomLarge'),
    [ClassRoomSize.XLARGE]: t('courseDetail.classSession.roomXLarge'),
  }
  return labels[roomSize] || t('courseDetail.unknown')
}

function formatDateTime(value?: string | null) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '-'
  return new Intl.DateTimeFormat(String(locale.value), {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
}

function toDatetimeLocalValue(value?: string | null) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  const localDate = new Date(date.getTime() - date.getTimezoneOffset() * 60000)
  return localDate.toISOString().slice(0, 16)
}

function normalizedRoomSize(value: number) {
  return ClassRoomSizeLabel[value] == null ? ClassRoomSize.MEDIUM : value
}
</script>

<style scoped>
.class-session-panel {
  display: grid;
  gap: 18px;
}

.workspace-header {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 18px;
  align-items: end;
  padding-bottom: 18px;
  border-bottom: 1px solid var(--color-outline-light);
}

.workspace-kicker,
.metric-item span,
.editor-heading span,
.section-title span,
.preview-header span {
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 12px;
  font-weight: 400;
  line-height: 1;
  letter-spacing: 0.05em;
  text-transform: uppercase;
}

.workspace-header h2 {
  margin: 8px 0 8px;
  color: var(--color-on-surface);
  font-family: var(--font-heading);
  font-size: clamp(32px, 4vw, 46px);
  font-weight: 400;
  line-height: 1.22;
}

.workspace-header p,
.editor-heading p {
  max-width: 64ch;
  margin: 0;
  color: var(--color-on-surface-variant);
  font-family: var(--font-body);
  font-size: 15px;
  line-height: 1.55;
}

.session-metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  border: 1px solid var(--color-outline-light);
  background: var(--color-surface-card);
}

.metric-item {
  display: grid;
  gap: 10px;
  min-width: 0;
  padding: 16px;
  border-left: 1px solid var(--color-outline-light);
}

.metric-item:first-child {
  border-left: 0;
}

.metric-item strong {
  color: var(--color-on-surface);
  font-family: var(--font-heading);
  font-size: 30px;
  font-weight: 400;
  line-height: 1;
}

.session-workspace {
  display: grid;
  grid-template-columns: minmax(0, 4fr) minmax(420px, 6fr);
  gap: 20px;
  align-items: start;
}

.session-list-column,
.session-editor-panel {
  min-width: 0;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
}

.session-list {
  display: grid;
}

.session-create-row {
  display: flex;
  align-items: center;
  width: 100%;
  gap: 10px;
  min-height: 68px;
  padding: 18px;
  background: transparent;
  border: 0;
  border-bottom: 1px solid var(--color-outline-light);
  color: var(--color-on-surface);
  cursor: pointer;
  font-family: var(--font-label);
  font-size: 13px;
  font-weight: 800;
  text-align: left;
  transition:
    background 0.2s ease,
    color 0.2s ease,
    transform 0.2s ease;
}

.session-create-row:hover {
  background: var(--color-surface-container);
}

.session-create-row:active {
  transform: translateY(1px);
}

.create-row-icon {
  display: grid;
  flex: 0 0 auto;
  width: 32px;
  height: 32px;
  place-items: center;
  background: var(--color-primary);
  color: var(--color-on-primary);
}

.session-item {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 16px;
  align-items: center;
  padding: 18px;
  border-top: 1px solid var(--color-outline-light);
  cursor: pointer;
  transition:
    background 0.2s ease,
    border-color 0.2s ease;
}

.session-item:first-child {
  border-top: 0;
}

.session-item.selected,
.session-item:hover {
  background: var(--color-surface-container);
}

.session-main {
  min-width: 0;
}

.session-heading {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.session-heading h3 {
  overflow: hidden;
  margin: 0;
  color: var(--color-on-surface);
  font-family: var(--font-body);
  font-size: 16px;
  font-weight: 800;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-main p {
  display: -webkit-box;
  overflow: hidden;
  margin: 6px 0 10px;
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 13px;
  line-height: 1.45;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.session-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 13px;
}

.session-meta span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.status-badge {
  flex-shrink: 0;
  padding: 4px 8px;
  border: 1px solid var(--color-outline-light);
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 12px;
  font-weight: 800;
  line-height: 1;
}

.status-badge.live {
  border-color: #22c55e;
  color: #22c55e;
}

.status-badge.finished {
  color: var(--color-outline);
}

.status-badge.draft {
  border-color: var(--color-on-surface);
  color: var(--color-on-surface);
}

.session-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

.btn-add,
.btn-secondary {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  min-height: 38px;
  padding: 0 14px;
  background: transparent;
  border: 1px solid var(--color-outline-light);
  border-radius: 0;
  color: var(--color-on-surface);
  cursor: pointer;
  font-family: var(--font-label);
  font-size: 12px;
  font-weight: 800;
  text-decoration: none;
  transition:
    background 0.2s ease,
    border-color 0.2s ease,
    color 0.2s ease,
    transform 0.2s ease;
}

.btn-add.primary {
  background: var(--color-primary);
  border-color: var(--color-primary);
  color: var(--color-on-primary);
}

.btn-add:hover,
.btn-secondary:hover {
  background: var(--color-surface-container-high);
  border-color: var(--color-outline);
}

.btn-add.primary:hover {
  background: var(--color-on-surface);
  border-color: var(--color-on-surface);
  color: var(--color-on-primary);
}

.btn-add:active,
.btn-secondary:active,
.btn-icon:active,
.room-spec:active {
  transform: translateY(1px);
}

.btn-add:disabled,
.btn-icon:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-icon {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  padding: 0;
  background: transparent;
  border: 1px solid var(--color-outline-light);
  color: var(--color-muted);
  cursor: pointer;
  transition:
    background 0.2s ease,
    border-color 0.2s ease,
    color 0.2s ease,
    transform 0.2s ease;
}

.btn-icon:hover {
  background: var(--color-surface-container-high);
  border-color: var(--color-outline);
  color: var(--color-on-surface);
}

.btn-icon.danger:hover {
  color: var(--color-error);
}

.session-editor-panel {
  position: sticky;
  top: 18px;
  display: grid;
  gap: 0;
}

.editor-heading {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 14px;
  align-items: center;
  padding: 18px;
  border-bottom: 1px solid var(--color-outline-light);
}

.editor-heading h3 {
  margin: 0;
  color: var(--color-on-surface);
  font-family: var(--font-heading);
  font-size: 32px;
  font-weight: 400;
  line-height: 1.15;
}

.session-form,
.form-section {
  display: grid;
}

.form-section {
  gap: 16px;
  padding: 18px;
  border-bottom: 1px solid var(--color-outline-light);
}

.section-title {
  display: flex;
  align-items: baseline;
  gap: 12px;
}

.section-title h4 {
  margin: 0;
  color: var(--color-on-surface);
  font-family: var(--font-heading);
  font-size: 21px;
  font-weight: 400;
  line-height: 1.25;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 13px;
}

.form-group span {
  color: var(--color-on-surface);
  font-weight: 700;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.input-field {
  width: 100%;
  min-height: 46px;
  padding: 10px 12px;
  box-sizing: border-box;
  background: var(--color-surface-canvas);
  border: 1px solid var(--color-outline-light);
  border-radius: 0;
  color: var(--color-on-surface);
  font-family: var(--font-body);
  font-size: 15px;
  line-height: 1.5;
  outline: none;
  transition: border-color 0.2s ease;
}

.input-field:focus {
  border-color: var(--color-outline);
}

.input-field::placeholder {
  color: var(--color-muted);
}

.input-field:disabled {
  opacity: 1;
  color: var(--color-on-surface);
  cursor: default;
}

textarea.input-field {
  min-height: 108px;
  resize: vertical;
}

textarea.input-field:focus {
  border-color: var(--color-outline);
}

.form-group :deep(.modal-date-control) {
  width: 100%;
  min-width: 0;
}

.form-group :deep(.modal-date-control .base-date-picker-trigger) {
  min-height: 46px;
  padding: 0 12px;
  background: var(--color-surface-canvas);
  border: 1px solid var(--color-outline-light);
  border-radius: 0;
  color: var(--color-on-surface);
  font-family: var(--font-body);
  font-size: 15px;
  font-weight: 500;
  line-height: 1;
  transition:
    border-color 0.2s,
    color 0.2s;
}

.form-group :deep(.modal-date-control .base-date-picker-trigger:hover),
.form-group :deep(.modal-date-control .base-date-picker-trigger:focus) {
  background: var(--color-surface-canvas);
  border-color: var(--color-outline);
  box-shadow: none;
}

.form-group :deep(.modal-date-control .base-date-picker-trigger.placeholder span) {
  color: var(--color-muted);
}

.form-group :deep(.modal-date-control .base-date-picker-popover),
.form-group :deep(.modal-date-control .base-date-picker-control),
.form-group :deep(.modal-date-control .base-date-picker-nav button),
.form-group :deep(.modal-date-control .base-date-picker-footer button),
.form-group :deep(.modal-date-control .base-date-picker-year-toolbar button),
.form-group :deep(.modal-date-control .base-date-picker-year),
.form-group :deep(.modal-date-control .base-date-picker-month),
.form-group :deep(.modal-date-control .base-date-picker-day),
.form-group :deep(.modal-date-control .base-date-picker-time) {
  border-radius: 0;
}

.room-spec-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.room-spec {
  position: relative;
  display: grid;
  gap: 7px;
  min-height: 84px;
  padding: 13px;
  text-align: left;
  background: var(--color-surface-canvas);
  border: 1px solid var(--color-outline-light);
  color: var(--color-on-surface);
  cursor: pointer;
  transition:
    background 0.2s ease,
    border-color 0.2s ease,
    transform 0.2s ease;
}

.room-spec:hover,
.room-spec.active {
  background: var(--color-surface-container);
  border-color: var(--color-on-surface);
}

.room-spec:disabled {
  cursor: default;
}

.room-spec:disabled:not(.active) {
  opacity: 0.6;
}

.room-spec strong {
  padding-right: 28px;
  font-family: var(--font-body);
  font-size: 14px;
  line-height: 1.25;
}

.room-spec small {
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 12px;
  line-height: 1;
}

.spec-check {
  position: absolute;
  top: 10px;
  right: 10px;
  display: grid;
  place-items: center;
  width: 18px;
  height: 18px;
  border: 1px solid var(--color-outline-light);
}

.room-spec.active .spec-check {
  border-color: var(--color-on-surface);
  background: var(--color-on-surface);
  color: var(--color-on-primary);
}

.seat-preview {
  display: grid;
  gap: 12px;
  padding: 14px;
  background: var(--color-surface-canvas);
  border: 1px solid var(--color-outline-light);
}

.preview-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
}

.preview-header strong {
  color: var(--color-on-surface);
  font-family: var(--font-heading);
  font-size: 24px;
  font-weight: 400;
  line-height: 1;
}

.grid-preview {
  display: grid;
  gap: 4px;
  min-height: 164px;
  align-content: center;
}

.seat-dot {
  display: block;
  width: 100%;
  aspect-ratio: 1;
  min-width: 4px;
  background: var(--color-surface-container-highest);
  border: 1px solid var(--color-outline-light);
}

.arc-preview {
  position: relative;
  height: 184px;
  overflow: hidden;
  border-top: 1px solid var(--color-outline-light);
}

.arc-preview::before {
  content: '';
  position: absolute;
  left: 50%;
  bottom: 16px;
  width: 80px;
  height: 24px;
  transform: translateX(-50%);
  border: 1px solid var(--color-outline-light);
  background: var(--color-surface-container);
}

.arc-preview .seat-dot {
  position: absolute;
  width: 7px;
  min-width: 7px;
  transform: translate(-50%, -50%);
}

.form-error {
  margin: 0;
  padding: 0 18px 18px;
  color: var(--color-error);
  font-family: var(--font-body);
  font-size: 13px;
  line-height: 1.5;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.empty-tab,
.session-state {
  display: grid;
  min-height: 300px;
  place-items: center;
  align-content: center;
  gap: 10px;
  padding: 42px 24px;
  color: var(--color-muted);
  text-align: center;
}

.session-state h3,
.empty-tab h3 {
  margin: 0;
  color: var(--color-on-surface);
  font-family: var(--font-heading);
  font-size: 24px;
  font-weight: 400;
}

.session-state p,
.empty-tab p {
  max-width: 44ch;
  margin: 0;
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 14px;
  line-height: 1.55;
}

@media (max-width: 1180px) {
  .session-workspace {
    grid-template-columns: 1fr;
  }

  .session-editor-panel {
    position: static;
  }
}

@media (max-width: 760px) {
  .workspace-header,
  .session-item,
  .form-grid,
  .room-spec-grid {
    grid-template-columns: 1fr;
  }

  .session-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .metric-item:nth-child(odd) {
    border-left: 0;
  }

  .metric-item:nth-child(n + 3) {
    border-top: 1px solid var(--color-outline-light);
  }

  .session-actions,
  .form-actions {
    flex-wrap: wrap;
    justify-content: flex-start;
  }

}
</style>
