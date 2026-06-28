<template>
  <section class="tab-panel">
    <div class="panel-header">
      <div>
        <h2>{{ t('courseDetail.assistantsTab') }}</h2>
        <p>{{ t('courseDetail.assistantsDescription') }}</p>
      </div>
      <div class="header-right">
        <span class="count-badge">{{ assistantCount }}</span>
        <button v-if="canManageAssistants" class="btn-add" type="button" @click="openDialog">
          <Plus :size="14" stroke-width="2"/>
          {{ isAdmin ? t('courseDetail.addAssistant') : t('courseDetail.inviteAssistant') }}
        </button>
      </div>
    </div>

    <!-- 待处理邀�?-->
    <div
        v-if="!loading && !pendingLoading && canManageAssistants && !isAdmin && pendingInvitations.length > 0"
        class="pending-section"
    >
      <h4>{{ t('courseDetail.pendingInvitations') }}</h4>
      <div class="invitation-list">
        <div v-for="inv in pendingInvitations" :key="inv.id" class="invitation-item">
          <UserAvatarLink
              :display-name="inv.inviteeName"
              :role="2"
              :show-name="false"
              :user-id="inv.inviteeId"
              size="medium"
          />
          <div class="member-info">
            <strong>{{ inv.inviteeName || inv.inviteeId }}</strong>
            <span>{{ formatDate(inv.createdAt) }}</span>
          </div>
          <button
              :title="t('courseDetail.withdrawInvitation')"
              class="btn-icon"
              type="button"
              @click="handleWithdraw(inv)"
          >
            <X :size="14" stroke-width="1.8"/>
          </button>
        </div>
      </div>
    </div>

    <CourseTabLoadingSkeleton
        v-if="loading || pendingLoading"
        :actions="0"
        :count="4"
        avatar
    />
    <div v-else-if="assistantCount === 0 && pendingInvitations.length === 0" class="empty-tab">
      <UserCheck :size="28" stroke-width="1.4"/>
      <h3>{{ t('courseDetail.noAssistantsTitle') }}</h3>
      <p>{{ t('courseDetail.noAssistants') }}</p>
    </div>
    <div v-else-if="assistantCount > 0" class="member-list">
      <div v-for="assistant in paginatedAssistants" :key="assistant.id" class="member-item">
        <UserAvatarLink
            :avatar-url="assistant.avatarUrl"
            :display-name="assistant.displayName"
            :role="2"
            :show-name="false"
            :user-id="assistant.id"
            size="medium"
        />
        <div class="member-info">
          <strong>{{ assistant.displayName || assistant.id }}</strong>
          <span>{{ t('courseDetail.assistantInstructor') }}</span>
        </div>
      </div>
    </div>

    <!-- 邀�?添加助教对话�?-->
    <BasePagination
        v-if="assistantCount > MEMBER_PAGE_SIZE"
        :aria-label="t('courseDetail.pagination')"
        :next-title="t('courseDetail.nextPage')"
        :page="currentPage"
        :previous-title="t('courseDetail.previousPage')"
        :size="MEMBER_PAGE_SIZE"
        :total="assistantCount"
        @change="currentPage = $event"
    />

    <InviteAssistantModal
        v-model="showDialog"
        :assistants="assistants"
        :course-id="courseId"
        :course-teacher-id="course.teacherId"
        :is-admin="isAdmin"
        @invited="handleInvited"
    />
  </section>
</template>

<script lang="ts" setup>
import {computed, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {Plus, UserCheck, X} from 'lucide-vue-next'
import {getSentInvitations, withdrawInvitation} from '@/features/course/api/invitation'
import {confirmDialog} from '@/shared/composables/useConfirmDialog'
import {notify} from '@/shared/composables/useGlobalNotification'
import BasePagination from '@/shared/components/BasePagination.vue'
import CourseTabLoadingSkeleton from '@/features/course/components/CourseTabLoadingSkeleton.vue'
import UserAvatarLink from '@/shared/components/UserAvatarLink.vue'
import InviteAssistantModal from '@/features/course/components/InviteAssistantModal.vue'
import type {CourseDetail} from '@/features/course/types/course'
import type {CourseInvitation} from '@/features/course/types/invitation'

type TeacherInfo = NonNullable<CourseDetail['teacherInfos']>[number]

const props = defineProps<{
  courseId: string
  course: CourseDetail
  assistants: TeacherInfo[]
  loading?: boolean
  canManageCourse?: boolean
  canManageAssistants?: boolean
  isAdmin?: boolean
  formatDate: (dateStr?: string | null) => string
}>()

const emit = defineEmits<{
  refresh: []
}>()

const {t} = useI18n()

const showDialog = ref(false)
const MEMBER_PAGE_SIZE = 10
const currentPage = ref(1)

const pendingInvitations = ref<CourseInvitation[]>([])
const pendingLoading = ref(false)

const assistantCount = computed(() => props.assistants.length)
const totalPages = computed(() => Math.max(1, Math.ceil(assistantCount.value / MEMBER_PAGE_SIZE)))

const paginatedAssistants = computed(() => {
  const start = (currentPage.value - 1) * MEMBER_PAGE_SIZE
  return props.assistants.slice(start, start + MEMBER_PAGE_SIZE)
})

watch(assistantCount, () => {
  if (currentPage.value > totalPages.value) {
    currentPage.value = totalPages.value
  }
})

onMounted(loadPendingInvitations)

async function loadPendingInvitations() {
  if (!props.canManageCourse || props.isAdmin) return
  pendingLoading.value = true
  try {
    const resp = await getSentInvitations({status: 0, size: 100})
    pendingInvitations.value = (resp.records || []).filter(inv => inv.courseId === props.courseId && inv.status === 0)
  } catch {
    pendingInvitations.value = []
  } finally {
    pendingLoading.value = false
  }
}

function openDialog() {
  showDialog.value = true
}

function handleInvited() {
  loadPendingInvitations()
  emit('refresh')
}

async function handleWithdraw(inv: CourseInvitation) {
  if (!(await confirmDialog({message: t('courseDetail.confirmWithdraw'), confirmVariant: 'danger'}))) return
  try {
    await withdrawInvitation(inv.id)
    notify.success(t('courseDetail.invitationWithdrawn'))
    await loadPendingInvitations()
  } catch {
    notify.error(t('courseDetail.saveChapterFailed'))
  }
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

.panel-header h2 {
  margin: 0;
  color: var(--color-on-surface);
  font-family: var(--font-heading);
  font-size: 28px;
  font-weight: 400;
  line-height: 1.3;
}

.panel-header p {
  max-width: 62ch;
  margin: 8px 0 0;
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 14px;
  line-height: 1.55;
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

/* 待处理邀�?*/
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
  font-weight: 400;
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

/* 助教列表 */
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
  color: var(--color-muted);
  text-align: center;
}

.empty-tab h3 {
  margin: 0;
  color: var(--color-on-surface);
  font-family: var(--font-heading);
  font-size: 24px;
  font-weight: 400;
}

.empty-tab p {
  max-width: 44ch;
  margin: 0;
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 14px;
  line-height: 1.55;
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
