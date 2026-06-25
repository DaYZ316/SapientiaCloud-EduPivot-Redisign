<template>
  <div class="bank-list-page">
    <div class="page-header">
      <div class="header-left">
        <button class="back-link" @click="goBack">
          <ArrowLeft :size="16"/>
        </button>
        <h1>{{ t('questionBank.title') }}</h1>
      </div>
      <button v-if="isTeacher" class="btn-primary" @click="openCreateEditor">
        <Plus :size="16"/>
        {{ t('questionBank.newBank') }}
      </button>
    </div>

    <div v-if="loading" class="loading-grid">
      <div v-for="i in 4" :key="i" class="skeleton-card shimmer"></div>
    </div>

    <div v-else-if="banks.length === 0" class="empty-state">
      <Database :size="36" stroke-width="1.4"/>
      <h3>{{ t('questionBank.noBanks') }}</h3>
      <p>{{ t('questionBank.noBanksDesc') }}</p>
    </div>

    <div v-else class="banks-grid">
      <div
          v-for="bank in banks"
          :key="bank.id"
          class="bank-card"
          @click="router.push('/question-banks/' + bank.id)"
      >
        <h3 class="bank-name">{{ bank.bankName }}</h3>
        <p v-if="bank.description" class="bank-desc">{{ bank.description }}</p>
        <div class="bank-meta">
          <span><FileText :size="14"/> {{ bank.questionCount }} {{ t('questionBank.questionCount') }}</span>
          <span>{{ bankTypeName(bank.bankType) }}</span>
          <span :class="'diff-' + bank.difficulty" class="difficulty">{{ difficultyName(bank.difficulty) }}</span>
        </div>
        <div v-if="isTeacher" class="bank-actions" @click.stop>
          <button :title="t('courseDetail.editBank')" class="btn-icon" type="button" @click="openEditEditor(bank)">
            <Pencil :size="14" stroke-width="1.8"/>
          </button>
          <button :title="t('courseDetail.deleteBank')" class="btn-icon danger" type="button" @click="handleDeleteBank(bank)">
            <Trash2 :size="14" stroke-width="1.8"/>
          </button>
        </div>
      </div>
    </div>

    <QuestionBankEditorDialog
        :bank="editingBank"
        :submitting="submitting"
        :visible="showEditor"
        @close="closeEditor"
        @submit="handleSubmit"
    />
  </div>
</template>

<script lang="ts" setup>
import {computed, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import {ArrowLeft, Database, FileText, Pencil, Plus, Trash2} from 'lucide-vue-next'
import {
  createQuestionBank,
  deleteQuestionBank,
  getCourseQuestionBanks,
  updateQuestionBank,
} from '@/features/question-bank/api/questionBank'
import type {QuestionBank} from '@/features/question-bank/types/questionBank'
import {QuestionDifficulty} from '@/features/question-bank/types/questionBank'
import {useAuthStore} from '@/features/auth/stores/auth'
import {notify} from '@/shared/composables/useGlobalNotification'
import {confirmDialog} from '@/shared/composables/useConfirmDialog'
import QuestionBankEditorDialog, {
  type QuestionBankEditorPayload,
} from '@/features/question-bank/components/QuestionBankEditorDialog.vue'

const {t} = useI18n()
const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

function goBack() {
  if (window.history.length > 1) {
    router.back()
  } else {
    router.push({name: 'dashboard'})
  }
}

const courseId = route.params.courseId as string
const loading = ref(true)
const banks = ref<QuestionBank[]>([])
const showEditor = ref(false)
const submitting = ref(false)
const editingBank = ref<QuestionBank | null>(null)
const handledCreateQuery = ref(false)

const isTeacher = computed(() => authStore.user?.role === 2 || authStore.user?.role === 0)

function difficultyName(d: number) {
  return QuestionDifficulty[d] || t('questionBank.unknown')
}

function bankTypeName(type: number) {
  const labels: Record<number, string> = {
    0: t('questionBank.bankTypePractice'),
    1: t('questionBank.bankTypeExam'),
    2: t('questionBank.bankTypeHomework'),
  }
  return labels[type] || t('questionBank.defaultBankType')
}

onMounted(async () => {
  await loadBanks()
  openEditorFromQuery()
})

watch(
    () => route.query.create,
    () => openEditorFromQuery()
)

async function loadBanks() {
  loading.value = true
  try {
    if (courseId) banks.value = await getCourseQuestionBanks(courseId)
  } finally {
    loading.value = false
  }
}

function openCreateEditor() {
  editingBank.value = null
  showEditor.value = true
}

function openEditEditor(bank: QuestionBank) {
  editingBank.value = bank
  showEditor.value = true
}

function closeEditor() {
  showEditor.value = false
  editingBank.value = null
}

function openEditorFromQuery() {
  if (handledCreateQuery.value || route.query.create !== '1' || !isTeacher.value) return
  handledCreateQuery.value = true
  openCreateEditor()
}

async function handleSubmit(payload: QuestionBankEditorPayload) {
  if (submitting.value) return
  submitting.value = true
  try {
    if (editingBank.value) {
      await updateQuestionBank(editingBank.value.id, payload)
      notify.success(t('courseDetail.bankUpdated'))
      closeEditor()
      await loadBanks()
    } else {
      const id = await createQuestionBank({
        courseId,
        ...payload,
      })
      closeEditor()
      notify.success(t('courseDetail.bankCreated'))
      router.push('/question-banks/' + id)
    }
  } catch {
    notify.error(t('courseDetail.alert.saveChapterFailed'))
  } finally {
    submitting.value = false
  }
}

async function handleDeleteBank(bank: QuestionBank) {
  if (!(await confirmDialog({message: t('courseDetail.confirmDeleteBank'), confirmVariant: 'danger'}))) return
  try {
    await deleteQuestionBank(bank.id)
    notify.success(t('courseDetail.bankDeleted'))
    await loadBanks()
  } catch {
    notify.error(t('courseDetail.alert.saveChapterFailed'))
  }
}
</script>

<style scoped>
.bank-list-page {
  max-width: 100%;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 32px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.back-link {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-muted);
  cursor: pointer;
  flex-shrink: 0;
}

.back-link:hover {
  background: var(--color-surface-container);
  color: var(--color-on-surface);
  border-color: var(--color-outline);
}

.page-header h1 {
  margin: 0;
  font-family: var(--font-heading);
  font-size: 48px;
  font-weight: 400;
  line-height: 1.3;
  color: var(--color-on-surface);
}

.btn-primary {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  border: none;
  border-radius: var(--radius-sm);
  background: var(--color-primary);
  color: var(--color-on-primary);
  font-family: var(--font-body);
  font-size: 13px;
  font-weight: 400;
  cursor: pointer;
}

.btn-primary:hover {
  background: var(--color-primary-soft);
}

.banks-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}

.bank-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 24px;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-lg);
  cursor: pointer;
  transition: border-color 0.15s;
}

.bank-card:hover {
  border-color: var(--color-on-surface);
}

.bank-actions {
  display: flex;
  gap: 8px;
  margin-top: auto;
  padding-top: 4px;
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

.bank-name {
  margin: 0;
  font-family: var(--font-heading);
  font-size: 20px;
  font-weight: 400;
  color: var(--color-on-surface);
}

.bank-desc {
  margin: 0;
  font-family: var(--font-body);
  font-size: 13px;
  color: var(--color-muted);
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.bank-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  font-family: var(--font-body);
  font-size: 12px;
  color: var(--color-muted);
}

.bank-meta span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
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

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 80px 32px;
  text-align: center;
  color: var(--color-muted);
}

.empty-state h3 {
  margin: 0;
  font-family: var(--font-heading);
  font-size: 22px;
  font-weight: 400;
  color: var(--color-on-surface);
}

.empty-state p {
  margin: 0;
  font-family: var(--font-body);
  font-size: 15px;
}

.loading-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}

.skeleton-card {
  height: 160px;
  border-radius: var(--radius-lg);
}

.shimmer {
  background: linear-gradient(110deg, var(--color-surface-container-high) 8%, var(--color-surface-canvas) 18%, var(--color-surface-container-high) 33%);
  background-size: 200% 100%;
  animation: shimmer 1.4s ease-in-out infinite;
}

@keyframes shimmer {
  to {
    background-position-x: -200%;
  }
}

</style>
