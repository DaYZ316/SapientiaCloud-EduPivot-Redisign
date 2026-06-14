<template>
  <section class="tab-panel">
    <div class="panel-header">
      <div>
        <span>{{ t('courseDetail.membersTab') }}</span>
        <h2>{{ t('courseDetail.assistantsTab') }}</h2>
      </div>
      <div class="header-right">
        <span class="count-badge">{{ assistants.length }}</span>
        <button v-if="canManageCourse" class="btn-add" type="button" @click="showInviteDialog = true">
          <Plus :size="14" stroke-width="2"/>
          {{ t('courseDetail.inviteAssistant') }}
        </button>
      </div>
    </div>

    <!-- 待处理邀请 -->
    <div v-if="canManageCourse && pendingInvitations.length > 0" class="pending-section">
      <h4>{{ t('courseDetail.pendingInvitations') }}</h4>
      <div class="invitation-list">
        <div v-for="inv in pendingInvitations" :key="inv.id" class="invitation-item">
          <div class="member-avatar">
            <User :size="16" stroke-width="1.7"/>
          </div>
          <div class="member-info">
            <strong>{{ inv.inviteeName || inv.inviteeId }}</strong>
            <span>{{ formatDate(inv.createdAt) }}</span>
          </div>
          <button
            class="btn-icon"
            type="button"
            :title="t('courseDetail.withdrawInvitation')"
            @click="handleWithdraw(inv)"
          >
            <X :size="14" stroke-width="1.8"/>
          </button>
        </div>
      </div>
    </div>

    <div v-if="assistants.length === 0 && pendingInvitations.length === 0" class="empty-tab">
      <UserCheck :size="28" stroke-width="1.4"/>
      <h3>{{ t('courseDetail.noAssistantsTitle') }}</h3>
      <p>{{ t('courseDetail.noAssistants') }}</p>
    </div>
    <div v-else-if="assistants.length > 0" class="member-list">
      <div v-for="assistant in assistants" :key="assistant.id" class="member-item">
        <div class="member-avatar">
          <img
            :src="assistant.avatarUrl || teacherFallbackUrl"
            :alt="assistant.displayName || assistant.id"
            @error="useFallbackImage($event, teacherFallbackUrl)"
          />
        </div>
        <div class="member-info">
          <strong>{{ assistant.displayName || assistant.id }}</strong>
          <span>{{ t('courseDetail.assistantInstructor') }}</span>
        </div>
        <button
          v-if="canManageCourse"
          class="btn-icon danger"
          type="button"
          :title="t('courseDetail.removeAssistant')"
          @click="handleRemove(assistant)"
        >
          <Trash2 :size="14" stroke-width="1.8"/>
        </button>
      </div>
    </div>

    <!-- 邀请对话框 -->
    <Teleport to="body">
      <div v-if="showInviteDialog" class="modal-overlay" @click.self="closeInviteDialog">
        <div class="modal-content">
          <h3>{{ t('courseDetail.inviteAssistant') }}</h3>
          <form @submit.prevent="handleInvite">
            <label>
              {{ t('courseDetail.searchTeacher') }}
              <div class="search-input-wrapper">
                <input
                  v-model="searchKeyword"
                  type="text"
                  :placeholder="t('courseDetail.searchTeacher')"
                  @input="handleSearch"
                />
              </div>
            </label>
            <div v-if="searchResults.length > 0" class="search-results">
              <div
                v-for="teacher in searchResults"
                :key="teacher.id"
                class="search-result-item"
                :class="{selected: selectedInviteeId === teacher.id}"
                @click="selectInvitee(teacher)"
              >
                <div class="member-avatar small">
                  <img
                    v-if="teacher.avatarUrl"
                    :src="teacher.avatarUrl"
                    :alt="teacher.displayName || teacher.id"
                    @error="useFallbackImage($event, teacherFallbackUrl)"
                  />
                  <User v-else :size="14" stroke-width="1.7"/>
                </div>
                <div>
                  <strong>{{ teacher.displayName || teacher.id }}</strong>
                  <span v-if="teacher.email">{{ teacher.email }}</span>
                </div>
              </div>
            </div>
            <label>
              {{ t('courseDetail.invitationMessage') }}
              <textarea v-model="inviteMessage" rows="3" maxlength="500"></textarea>
            </label>
            <div class="modal-actions">
              <button type="button" class="btn-cancel" @click="closeInviteDialog">{{ t('courseDetail.cancel') }}</button>
              <button type="submit" class="btn-submit" :disabled="inviting || !selectedInviteeId">
                {{ t('courseDetail.save') }}
              </button>
            </div>
          </form>
        </div>
      </div>
    </Teleport>
  </section>
</template>

<script lang="ts" setup>
import {onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {Plus, Trash2, User, UserCheck, X} from 'lucide-vue-next'
import {sendInvitation, getSentInvitations, withdrawInvitation} from '@/features/course/api/invitation'
import {updateCourse} from '@/features/course/api/course'
import {listTeachers} from '@/features/user/api/user'
import {notify} from '@/shared/composables/useGlobalNotification'
import type {CourseDetail} from '@/features/course/types/course'
import type {CourseInvitation} from '@/features/course/types/invitation'
import type {UserProfile} from '@/features/user/types/user'

type TeacherInfo = NonNullable<CourseDetail['teacherInfos']>[number]

const props = defineProps<{
  courseId: string
  course: CourseDetail
  assistants: TeacherInfo[]
  canManageCourse?: boolean
  formatDate: (dateStr?: string | null) => string
}>()

const emit = defineEmits<{
  refresh: []
}>()

const {t} = useI18n()

const teacherFallbackUrl = '/assets/avatar-teacher-default.png'

const showInviteDialog = ref(false)
const inviting = ref(false)
const searchKeyword = ref('')
const searchResults = ref<UserProfile[]>([])
const selectedInviteeId = ref<string | null>(null)
const inviteMessage = ref('')
const pendingInvitations = ref<CourseInvitation[]>([])

onMounted(loadPendingInvitations)

async function loadPendingInvitations() {
  if (!props.canManageCourse) return
  try {
    const resp = await getSentInvitations({status: 0, size: 100})
    pendingInvitations.value = (resp.records || []).filter(inv => inv.courseId === props.courseId)
  } catch {
    pendingInvitations.value = []
  }
}

let searchTimeout: ReturnType<typeof setTimeout> | null = null

function handleSearch() {
  if (searchTimeout) clearTimeout(searchTimeout)
  if (!searchKeyword.value.trim()) {
    searchResults.value = []
    return
  }
  searchTimeout = setTimeout(async () => {
    try {
      const resp = await listTeachers({keyword: searchKeyword.value, size: 10})
      const existingIds = new Set([
        props.course.teacherId,
        ...props.assistants.map(a => a.id),
      ])
      searchResults.value = (resp.records || []).filter(u => !existingIds.has(u.id))
    } catch {
      searchResults.value = []
    }
  }, 300)
}

function selectInvitee(user: UserProfile) {
  selectedInviteeId.value = user.id
  searchKeyword.value = user.displayName || user.email || user.id
  searchResults.value = []
}

async function handleInvite() {
  if (!selectedInviteeId.value) return
  inviting.value = true
  try {
    await sendInvitation({
      courseId: props.courseId,
      inviteeId: selectedInviteeId.value,
      message: inviteMessage.value || undefined,
    })
    notify.success(t('courseDetail.invitationSent'))
    closeInviteDialog()
    await loadPendingInvitations()
  } catch {
    notify.error(t('courseDetail.saveChapterFailed'))
  } finally {
    inviting.value = false
  }
}

function closeInviteDialog() {
  showInviteDialog.value = false
  searchKeyword.value = ''
  searchResults.value = []
  selectedInviteeId.value = null
  inviteMessage.value = ''
}

async function handleWithdraw(inv: CourseInvitation) {
  if (!confirm(t('courseDetail.confirmWithdraw'))) return
  try {
    await withdrawInvitation(inv.id)
    notify.success(t('courseDetail.invitationWithdrawn'))
    await loadPendingInvitations()
  } catch {
    notify.error(t('courseDetail.saveChapterFailed'))
  }
}

async function handleRemove(assistant: TeacherInfo) {
  if (!confirm(t('courseDetail.confirmRemoveAssistant'))) return
  try {
    const remainingIds = props.assistants
      .filter(a => a.id !== assistant.id)
      .map(a => a.id)
    await updateCourse(props.courseId, {assistantIds: remainingIds, isPublic: props.course.isPublic})
    notify.success(t('courseDetail.assistantRemoved'))
    emit('refresh')
  } catch {
    notify.error(t('courseDetail.saveChapterFailed'))
  }
}

function useFallbackImage(event: Event, fallback: string) {
  const image = event.target as HTMLImageElement
  if (image.dataset.fallbackApplied === 'true') return
  image.dataset.fallbackApplied = 'true'
  image.src = fallback
}
</script>

<style scoped>
.panel-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 18px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--color-outline-light);
}

.panel-header span {
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 12px;
  font-weight: 600;
  line-height: 1;
  letter-spacing: 0.05em;
  text-transform: uppercase;
}

.panel-header h2 {
  margin: 6px 0 0;
  color: var(--color-on-surface);
  font-family: var(--font-heading);
  font-size: 28px;
  font-weight: 400;
  line-height: 1.3;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.count-badge {
  padding: 3px 8px;
  background: var(--color-surface-container-high);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 11px;
  font-weight: 800;
}

.btn-add {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  min-height: 38px;
  padding: 0 14px;
  background: transparent;
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  color: var(--color-on-surface);
  cursor: pointer;
  font-family: var(--font-label);
  font-size: 12px;
  font-weight: 800;
  transition: background 0.2s ease, border-color 0.2s ease;
}

.btn-add:hover {
  background: var(--color-surface-container-high);
  border-color: var(--color-outline);
}

.pending-section {
  margin-top: 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--color-outline-light);
}

.pending-section h4 {
  margin: 0 0 10px;
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.05em;
  text-transform: uppercase;
}

.invitation-list {
  display: grid;
  gap: 8px;
}

.invitation-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  background: var(--color-surface-container);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
}

.member-list {
  display: grid;
  gap: 10px;
  margin-top: 16px;
}

.member-item {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 62px;
  padding: 10px 12px;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
}

.member-avatar {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  flex: 0 0 auto;
  overflow: hidden;
  background: var(--color-surface-container-high);
  border: 1px solid var(--color-outline-light);
  border-radius: 50%;
  color: var(--color-muted);
}

.member-avatar.small {
  width: 32px;
  height: 32px;
}

.member-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.member-info {
  min-width: 0;
  flex: 1;
}

.member-info strong {
  display: block;
  overflow: hidden;
  color: var(--color-on-surface);
  font-family: var(--font-body);
  font-size: 14px;
  font-weight: 800;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.member-info span {
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 13px;
  line-height: 1.45;
}

.btn-icon {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  padding: 0;
  background: transparent;
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  color: var(--color-muted);
  cursor: pointer;
  transition: background 0.2s ease, border-color 0.2s ease, color 0.2s ease;
}

.btn-icon:hover {
  background: var(--color-surface-container-high);
  border-color: var(--color-outline);
  color: var(--color-on-surface);
}

.btn-icon.danger:hover {
  color: #ef4444;
}

.empty-tab {
  display: grid;
  min-height: 220px;
  place-items: center;
  align-content: center;
  gap: 10px;
  padding: 42px 24px;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  color: var(--color-muted);
  text-align: center;
}

.empty-tab h3 {
  margin: 0;
  color: var(--color-on-surface);
  font-family: var(--font-heading);
  font-size: 24px;
  font-weight: 600;
}

.empty-tab p {
  max-width: 44ch;
  margin: 0;
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 14px;
  line-height: 1.55;
}

.modal-overlay {
  position: fixed;
  inset: 0;
  display: grid;
  place-items: center;
  background: rgba(0, 0, 0, 0.4);
  z-index: 1000;
}

.modal-content {
  width: min(480px, 90vw);
  padding: 24px;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-md);
}

.modal-content h3 {
  margin: 0 0 20px;
  color: var(--color-on-surface);
  font-family: var(--font-heading);
  font-size: 20px;
  font-weight: 600;
}

.modal-content form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.modal-content label {
  display: flex;
  flex-direction: column;
  gap: 6px;
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 13px;
  font-weight: 600;
}

.modal-content input,
.modal-content textarea {
  padding: 10px 12px;
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  background: var(--color-surface-container);
  color: var(--color-on-surface);
  font-family: var(--font-body);
  font-size: 14px;
}

.modal-content input:focus,
.modal-content textarea:focus {
  outline: none;
  border-color: var(--color-primary);
}

.search-input-wrapper {
  position: relative;
}

.search-results {
  display: grid;
  gap: 4px;
  max-height: 200px;
  overflow-y: auto;
  padding: 4px;
  background: var(--color-surface-container);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
}

.search-result-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: background 0.15s ease;
}

.search-result-item:hover {
  background: var(--color-surface-container-high);
}

.search-result-item.selected {
  background: var(--color-primary-soft, var(--color-surface-container-high));
}

.search-result-item strong {
  display: block;
  color: var(--color-on-surface);
  font-size: 13px;
  font-weight: 600;
}

.search-result-item span {
  display: block;
  color: var(--color-muted);
  font-size: 12px;
}

.modal-actions {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
  margin-top: 4px;
}

.btn-cancel {
  padding: 10px 20px;
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}

.btn-cancel:hover {
  background: var(--color-surface-container);
  border-color: var(--color-outline);
}

.btn-submit {
  padding: 10px 20px;
  border: none;
  border-radius: var(--radius-sm);
  background: var(--color-primary);
  color: var(--color-on-primary);
  font-family: var(--font-label);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}

.btn-submit:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

@media (max-width: 760px) {
  .panel-header {
    flex-direction: column;
    align-items: stretch;
  }

  .header-right {
    justify-content: flex-end;
  }
}
</style>
