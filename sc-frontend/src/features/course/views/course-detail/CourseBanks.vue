<template>
  <section class="tab-panel">
    <div class="panel-header">
      <div>
        <span>{{ t('courseDetail.practiceTab') }}</span>
        <h2>{{ t('questionBank.title') }}</h2>
      </div>
      <button v-if="canManageCourse" class="btn-add" type="button" @click="showCreateDialog = true">
        <Plus :size="14" stroke-width="2"/>
        {{ t('courseDetail.newBank') }}
      </button>
    </div>
    <div v-if="banks.length === 0" class="empty-tab">
      <Database :size="28" stroke-width="1.4"/>
      <h3>{{ t('courseDetail.noBanksTitle') }}</h3>
      <p>{{ t('questionBank.noBanks') }}</p>
    </div>
    <div v-else class="bank-list">
      <article
        v-for="bank in banks"
        :key="bank.id"
        class="bank-item"
        :class="{'is-readonly': !canAccessCourseContent}"
        @click="handleBankOpen(bank.id)"
      >
        <div class="bank-icon"><Database :size="18" stroke-width="1.8"/></div>
        <div class="bank-info">
          <h3>{{ bank.bankName }}</h3>
          <p>{{ bank.description || t('courseDetail.noBankDescription') }}</p>
          <div class="item-meta">
            <span>{{ bank.questionCount }} {{ t('questionBank.questionCount') }}</span>
            <span>{{ formatDate(bank.updatedAt || bank.createdAt) }}</span>
          </div>
        </div>
        <div class="bank-actions" @click.stop>
          <button
            v-if="isStudent && course.enrolled"
            class="btn-practice"
            type="button"
            @click="router.push('/question-banks/' + bank.id + '/practice')"
          >
            {{ t('questionBank.practice') }}
          </button>
          <button
            v-if="canManageCourse"
            class="btn-icon"
            type="button"
            :title="t('courseDetail.editBank')"
            @click="startEdit(bank)"
          >
            <Pencil :size="14" stroke-width="1.8"/>
          </button>
          <button
            v-if="canManageCourse"
            class="btn-icon danger"
            type="button"
            :title="t('courseDetail.deleteBank')"
            @click="handleDelete(bank)"
          >
            <Trash2 :size="14" stroke-width="1.8"/>
          </button>
        </div>
      </article>
    </div>

    <!-- 创建/编辑题库对话�?-->
    <Teleport to="body">
      <div v-if="showCreateDialog || showEditDialog" class="modal-overlay" @click.self="closeDialogs">
        <div class="modal-content">
          <h3>{{ showEditDialog ? t('courseDetail.editBank') : t('courseDetail.newBank') }}</h3>
          <form @submit.prevent="handleSubmit">
            <label>
              {{ t('questionBank.bankName') }} *
              <input v-model="form.bankName" type="text" required maxlength="200"/>
            </label>
            <label>
              {{ t('questionBank.description') }}
              <textarea v-model="form.description" rows="3" maxlength="2000"></textarea>
            </label>
            <label>
              {{ t('questionBank.bankType') }}
              <BaseSelect v-model="form.bankType" :options="bankTypeOptions"/>
            </label>
            <label>
              {{ t('questionBank.difficulty') }}
              <BaseSelect v-model="form.difficulty" :options="difficultyOptions"/>
            </label>
            <div class="modal-actions">
              <button type="button" class="btn-cancel" @click="closeDialogs">{{ t('courseDetail.cancel') }}</button>
              <button type="submit" class="btn-submit" :disabled="submitting || !form.bankName.trim()">
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
import {computed, reactive, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import {Database, Pencil, Plus, Trash2} from 'lucide-vue-next'
import {createQuestionBank, updateQuestionBank, deleteQuestionBank} from '@/features/question-bank/api/questionBank'
import {notify} from '@/shared/composables/useGlobalNotification'
import BaseSelect from '@/shared/components/BaseSelect.vue'
import type {CourseDetail} from '@/features/course/types/course'
import type {QuestionBank} from '@/features/question-bank/types/questionBank'

const props = defineProps<{
  courseId: string
  course: CourseDetail
  banks: QuestionBank[]
  canManageCourse: boolean
  canAccessCourseContent: boolean
  isStudent: boolean
  formatDate: (dateStr?: string | null) => string
}>()

const emit = defineEmits<{
  refresh: []
}>()

const {t} = useI18n()
const router = useRouter()

const showCreateDialog = ref(false)
const showEditDialog = ref(false)
const submitting = ref(false)
const editingBank = ref<QuestionBank | null>(null)

const form = reactive({
  bankName: '',
  description: '',
  bankType: 0,
  difficulty: 2,
})

const bankTypeOptions = computed(() => [
  {label: t('questionBank.bankTypePractice'), value: 0},
  {label: t('questionBank.bankTypeExam'), value: 1},
  {label: t('questionBank.bankTypeHomework'), value: 2},
])

const difficultyOptions = computed(() => [
  {label: t('questionBank.difficultyEasy'), value: 1},
  {label: t('questionBank.difficultyMedium'), value: 2},
  {label: t('questionBank.difficultyHard'), value: 3},
])

function handleBankOpen(bankId: string) {
  if (!props.canAccessCourseContent) return
  router.push('/question-banks/' + bankId)
}

function startEdit(bank: QuestionBank) {
  editingBank.value = bank
  form.bankName = bank.bankName
  form.description = bank.description || ''
  form.bankType = bank.bankType
  form.difficulty = bank.difficulty
  showEditDialog.value = true
}

function closeDialogs() {
  showCreateDialog.value = false
  showEditDialog.value = false
  editingBank.value = null
  form.bankName = ''
  form.description = ''
  form.bankType = 0
  form.difficulty = 2
}

async function handleSubmit() {
  if (!form.bankName.trim()) return
  submitting.value = true
  try {
    if (editingBank.value) {
      await updateQuestionBank(editingBank.value.id, {
        bankName: form.bankName,
        description: form.description || undefined,
        bankType: form.bankType,
        difficulty: form.difficulty,
      })
      notify.success(t('courseDetail.bankUpdated'))
    } else {
      await createQuestionBank({
        courseId: props.courseId,
        bankName: form.bankName,
        description: form.description || undefined,
        bankType: form.bankType,
        difficulty: form.difficulty,
      })
      notify.success(t('courseDetail.bankCreated'))
    }
    closeDialogs()
    emit('refresh')
  } catch {
    notify.error(t('courseDetail.saveChapterFailed'))
  } finally {
    submitting.value = false
  }
}

async function handleDelete(bank: QuestionBank) {
  if (!confirm(t('courseDetail.confirmDeleteBank'))) return
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

.panel-header span {
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 12px;
  font-weight: 400;
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

.btn-add,
.btn-practice {
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

.btn-add:hover,
.btn-practice:hover {
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
  grid-template-columns: auto minmax(0, 1fr) auto;
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

.bank-icon {
  display: grid;
  width: 42px;
  height: 42px;
  place-items: center;
  background: var(--color-surface-container-high);
  border-radius: var(--radius-sm);
  color: var(--color-on-surface);
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
  font-weight: 400;
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
  font-weight: 400;
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
  font-weight: 400;
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
  font-weight: 400;
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

  .bank-item {
    grid-template-columns: 1fr;
  }
}
</style>
