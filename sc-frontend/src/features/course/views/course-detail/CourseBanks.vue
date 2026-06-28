<template>
  <section class="tab-panel">
    <div class="panel-header">
      <div>
        <h2>{{ t('questionBank.title') }}</h2>
        <p>{{ t('courseDetail.banksDescription') }}</p>
      </div>
      <button v-if="canManageCourse" class="btn-add" type="button" @click="openCreateDialog">
        <Plus :size="14" stroke-width="2"/>
        {{ t('courseDetail.newBank') }}
      </button>
    </div>
    <CourseTabLoadingSkeleton
        v-if="loading"
        :actions="canManageCourse ? 2 : 0"
        :count="4"
    />
    <div v-else-if="banks.length === 0" class="empty-tab">
      <Database :size="28" stroke-width="1.4"/>
      <h3>{{ t('courseDetail.noBanksTitle') }}</h3>
      <p>{{ t('questionBank.noBanks') }}</p>
    </div>
    <div v-else class="bank-list">
      <article
          v-for="bank in banks"
          :key="bank.id"
          :class="{'is-readonly': !canAccessCourseContent}"
          class="bank-item"
          @click="handleBankOpen(bank.id)"
      >
        <div class="bank-info">
          <h3>{{ bank.bankName }}</h3>
          <p>{{ bank.description || t('courseDetail.noBankDescription') }}</p>
          <div class="item-meta">
            <span>{{ bank.questionCount }} {{ t('questionBank.questionCount') }}</span>
            <span>{{ bankTypeName(bank.bankType) }}</span>
            <span :class="'diff-' + bank.difficulty" class="difficulty">{{ difficultyName(bank.difficulty) }}</span>
            <span>{{ formatDate(bank.updatedAt || bank.createdAt) }}</span>
          </div>
        </div>
        <div class="bank-actions" @click.stop>
          <button
              v-if="canManageCourse"
              :title="t('courseDetail.editBank')"
              class="btn-icon"
              type="button"
              @click="startEdit(bank)"
          >
            <Pencil :size="14" stroke-width="1.8"/>
          </button>
          <button
              v-if="canManageCourse"
              :title="t('courseDetail.deleteBank')"
              class="btn-icon danger"
              type="button"
              @click="handleDelete(bank)"
          >
            <Trash2 :size="14" stroke-width="1.8"/>
          </button>
        </div>
      </article>
    </div>
    <BasePagination
        v-if="total > 0"
        :aria-label="t('courseDetail.pagination')"
        :disabled="loading"
        :next-title="t('courseDetail.nextPage')"
        :page="page"
        :previous-title="t('courseDetail.previousPage')"
        :size="size"
        :total="total"
        @change="emit('page-change', $event)"
    />

    <!-- 创建/编辑题库对话�?-->
    <QuestionBankEditorDialog
        :bank="editingBank"
        :submitting="submitting"
        :visible="showEditorDialog"
        @close="closeDialog"
        @submit="handleSubmit"
    />
  </section>
</template>

<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import {Database, Pencil, Plus, Trash2} from 'lucide-vue-next'
import {createQuestionBank, deleteQuestionBank, updateQuestionBank} from '@/features/question-bank/api/questionBank'
import {confirmDialog} from '@/shared/composables/useConfirmDialog'
import {notify} from '@/shared/composables/useGlobalNotification'
import BasePagination from '@/shared/components/BasePagination.vue'
import CourseTabLoadingSkeleton from '@/features/course/components/CourseTabLoadingSkeleton.vue'
import type {CourseDetail} from '@/features/course/types/course'
import type {QuestionBank} from '@/features/question-bank/types/questionBank'
import {QuestionDifficulty} from '@/features/question-bank/types/questionBank'
import QuestionBankEditorDialog, {
  type QuestionBankEditorPayload,
} from '@/features/question-bank/components/QuestionBankEditorDialog.vue'

const props = withDefaults(defineProps<{
  courseId: string
  course: CourseDetail
  banks: QuestionBank[]
  page?: number
  size?: number
  total?: number
  loading?: boolean
  canManageCourse: boolean
  canAccessCourseContent: boolean
  isStudent: boolean
  formatDate: (dateStr?: string | null) => string
}>(), {
  page: 1,
  size: 10,
  total: 0,
  loading: false,
})

const emit = defineEmits<{
  refresh: []
  'page-change': [page: number]
}>()

const {t} = useI18n()
const router = useRouter()

const showEditorDialog = ref(false)
const submitting = ref(false)
const editingBank = ref<QuestionBank | null>(null)

function handleBankOpen(bankId: string) {
  if (!props.canAccessCourseContent) return
  router.push('/question-banks/' + bankId)
}

function bankTypeName(type: number) {
  const labels: Record<number, string> = {
    0: t('questionBank.bankTypePractice'),
    1: t('questionBank.bankTypeExam'),
    2: t('questionBank.bankTypeHomework'),
  }
  return labels[type] || t('questionBank.defaultBankType')
}

function difficultyName(difficulty: number) {
  return QuestionDifficulty[difficulty] || t('questionBank.unknown')
}

function openCreateDialog() {
  editingBank.value = null
  showEditorDialog.value = true
}

function startEdit(bank: QuestionBank) {
  editingBank.value = bank
  showEditorDialog.value = true
}

function closeDialog() {
  showEditorDialog.value = false
  editingBank.value = null
}

async function handleSubmit(payload: QuestionBankEditorPayload) {
  submitting.value = true
  try {
    if (editingBank.value) {
      await updateQuestionBank(editingBank.value.id, payload)
      notify.success(t('courseDetail.bankUpdated'))
    } else {
      await createQuestionBank({
        courseId: props.courseId,
        ...payload,
      })
      notify.success(t('courseDetail.bankCreated'))
    }
    closeDialog()
    emit('refresh')
  } catch {
    notify.error(t('courseDetail.saveChapterFailed'))
  } finally {
    submitting.value = false
  }
}

async function handleDelete(bank: QuestionBank) {
  if (!(await confirmDialog({message: t('courseDetail.confirmDeleteBank'), confirmVariant: 'danger'}))) return
  try {
    await deleteQuestionBank(bank.id)
    notify.success(t('courseDetail.bankDeleted'))
    emit('refresh')
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
  text-decoration: none;
  transition: background 0.2s ease, border-color 0.2s ease, color 0.2s ease, transform 0.2s ease;
}

.btn-add:hover {
  background: var(--color-surface-container-high);
  border-color: var(--color-outline);
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

.bank-list {
  display: grid;
  gap: 10px;
}

.bank-item {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 14px;
  align-items: center;
  padding: 16px;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: background 0.2s ease, border-color 0.2s ease;
}

.bank-item:hover {
  background: var(--color-surface-container);
  border-color: var(--color-outline);
}

.bank-item.is-readonly {
  cursor: default;
}

.bank-item.is-readonly:hover {
  background: var(--color-surface-card);
  border-color: var(--color-outline-light);
}

.bank-info {
  min-width: 0;
}

.bank-info h3 {
  overflow: hidden;
  margin: 0;
  color: var(--color-on-surface);
  font-family: var(--font-body);
  font-size: 15px;
  font-weight: 800;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.bank-info p {
  display: -webkit-box;
  overflow: hidden;
  margin: 4px 0 8px;
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 13px;
  line-height: 1.45;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.item-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 13px;
  line-height: 1.45;
}

.difficulty.diff-1 {
  color: #22c55e;
}

.difficulty.diff-2 {
  color: #eab308;
}

.difficulty.diff-3 {
  color: #ef4444;
}

.bank-actions {
  display: flex;
  align-items: center;
  gap: 6px;
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

  .bank-item {
    grid-template-columns: 1fr;
  }
}
</style>
