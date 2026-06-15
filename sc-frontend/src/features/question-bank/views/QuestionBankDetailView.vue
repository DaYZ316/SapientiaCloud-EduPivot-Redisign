<template>
  <div class="bank-detail-page">
    <button class="back-link" @click="router.back()">
      <ArrowLeft :size="16"/>
      {{ t('questionBank.backToBank') }}
    </button>

    <div v-if="bank" class="bank-header">
      <div class="header-info">
        <h1>{{ bank.bankName }}</h1>
        <p v-if="bank.description">{{ bank.description }}</p>
        <div class="bank-meta">
          <span class="difficulty" :class="'diff-' + bank.difficulty">{{ difficultyName(bank.difficulty) }}</span>
          <span>{{ bank.questionCount }} {{ t('questionBank.questionCount') }}</span>
        </div>
      </div>
      <div class="header-actions">
        <button v-if="isStudent && bank.questionCount > 0" class="btn-primary" @click="router.push('/question-banks/' + bankId + '/practice')">
          {{ t('questionBank.startPractice') }}
        </button>
        <button v-if="isTeacher" class="btn-secondary" @click="showQuestionEditor = true">
          <Plus :size="14"/>
          {{ t('questionBank.newQuestion') }}
        </button>
      </div>
    </div>

    <div v-if="loading" class="loading-list">
      <div v-for="i in 3" :key="i" class="skeleton-card shimmer"></div>
    </div>

    <div v-else-if="questions.length === 0" class="empty-state">
      <FileText :size="36" stroke-width="1.4"/>
      <h3>{{ t('questionBank.noQuestions') }}</h3>
      <p>{{ t('questionBank.noQuestionsDesc') }}</p>
    </div>

    <div v-else class="questions-list">
      <QuestionCard
        v-for="question in questions"
        :key="question.id"
        :question="question"
      />
    </div>

    <div v-if="showQuestionEditor" class="editor-overlay" @click.self="showQuestionEditor = false">
      <div class="editor-dialog">
        <div class="editor-header">
          <h2>{{ t('questionBank.newQuestion') }}</h2>
          <button class="close-btn" @click="showQuestionEditor = false"><X :size="18"/></button>
        </div>
        <div class="editor-body">
          <div class="field">
            <label>{{ t('questionBank.questionTitle') }} *</label>
            <input v-model="newQuestion.questionTitle" type="text" class="input"/>
          </div>
          <div class="field">
            <label>{{ t('questionBank.questionContent') }}</label>
            <textarea v-model="newQuestion.questionContent" class="textarea" rows="4"></textarea>
          </div>
          <div class="field-row">
            <div class="field">
              <label>{{ t('questionBank.questionType') }}</label>
              <select v-model="newQuestion.questionType" class="input">
                <option :value="0">单选题</option>
                <option :value="1">多选题</option>
                <option :value="2">判断题</option>
                <option :value="3">填空题</option>
                <option :value="4">简答题</option>
              </select>
            </div>
            <div class="field">
              <label>{{ t('questionBank.score') }}</label>
              <input v-model.number="newQuestion.score" type="number" class="input" min="0" step="0.5"/>
            </div>
          </div>
          <div class="field-row">
            <div class="field">
              <label>{{ t('questionBank.difficulty') }}</label>
              <select v-model="newQuestion.difficulty" class="input">
                <option :value="1">简单</option>
                <option :value="2">中等</option>
                <option :value="3">困难</option>
              </select>
            </div>
            <div class="field">
              <label>{{ t('questionBank.estimatedTime') }}</label>
              <input v-model.number="newQuestion.estimatedTime" type="number" class="input" min="1"/>
            </div>
          </div>
        </div>
        <div class="editor-footer">
          <button class="btn-cancel" @click="showQuestionEditor = false">{{ t('chapter.cancel') }}</button>
          <button class="btn-save" :disabled="!newQuestion.questionTitle.trim()" @click="handleCreateQuestion">{{ t('chapter.save') }}</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import {onMounted, ref, computed} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import {ArrowLeft, Plus, FileText, X} from 'lucide-vue-next'
import {getQuestionBank, getQuestions, createQuestion} from '@/features/question-bank/api/questionBank'
import {QuestionDifficulty} from '@/features/question-bank/types/questionBank'
import type {QuestionBank, Question} from '@/features/question-bank/types/questionBank'
import {useAuthStore} from '@/features/auth/stores/auth'
import {notify} from '@/shared/composables/useGlobalNotification'
import QuestionCard from '@/features/question-bank/components/QuestionCard.vue'

const {t} = useI18n()
const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const bankId = route.params.id as string
const loading = ref(true)
const bank = ref<QuestionBank | null>(null)
const questions = ref<Question[]>([])
const showQuestionEditor = ref(false)

const isTeacher = computed(() => authStore.user?.role === 2 || authStore.user?.role === 0)
const isStudent = computed(() => authStore.user?.role === 1)

const newQuestion = ref({
  questionTitle: '',
  questionContent: '',
  questionType: 0,
  score: 1,
  difficulty: 2,
  estimatedTime: null as number | null,
})

function difficultyName(d: number) {
  return QuestionDifficulty[d] || '未知'
}

onMounted(async () => {
  try {
    const [bankData, questionsData] = await Promise.all([
      getQuestionBank(bankId),
      getQuestions({questionBankId: bankId, page: 1, size: 100}),
    ])
    bank.value = bankData
    questions.value = questionsData.records || []
  } finally {
    loading.value = false
  }
})

async function handleCreateQuestion() {
  if (!bank.value) return
  try {
    await createQuestion({
      questionBankId: bankId,
      courseId: bank.value.courseId,
      questionTitle: newQuestion.value.questionTitle,
      questionContent: newQuestion.value.questionContent || undefined,
      questionType: newQuestion.value.questionType,
      score: newQuestion.value.score,
      difficulty: newQuestion.value.difficulty,
      estimatedTime: newQuestion.value.estimatedTime || undefined,
    })
    showQuestionEditor.value = false
    notify.success('题目创建成功')
    const data = await getQuestions({questionBankId: bankId, page: 1, size: 100})
    questions.value = data.records || []
  } catch {
    notify.error('创建失败')
  }
}
</script>

<style scoped>
.bank-detail-page { max-width: 100%; }
.back-link { display: inline-flex; align-items: center; gap: 6px; padding: 0; border: none; background: none; color: var(--color-muted); font-family: var(--font-body); font-size: 13px; cursor: pointer; margin-bottom: 24px; }
.back-link:hover { color: var(--color-on-surface); }
.bank-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 32px; }
.header-info h1 { margin: 0 0 8px; font-family: var(--font-heading); font-size: 36px; font-weight: 400; line-height: 1.3; color: var(--color-on-surface); }
.header-info p { margin: 0 0 12px; font-family: var(--font-body); font-size: 15px; color: var(--color-muted); }
.bank-meta { display: flex; gap: 16px; font-family: var(--font-body); font-size: 14px; color: var(--color-muted); }
.difficulty.diff-1 { color: #22c55e; }
.difficulty.diff-2 { color: #eab308; }
.difficulty.diff-3 { color: #ef4444; }
.header-actions { display: flex; gap: 12px; }
.btn-primary, .btn-secondary { display: inline-flex; align-items: center; gap: 6px; padding: 10px 20px; border: none; border-radius: var(--radius-sm); font-family: var(--font-body); font-size: 13px; font-weight: 400; cursor: pointer; }
.btn-primary { background: var(--color-primary); color: var(--color-on-primary); }
.btn-primary:hover { background: var(--color-primary-soft); }
.btn-secondary { background: var(--color-surface-container); color: var(--color-on-surface); border: 1px solid var(--color-outline-light); }
.btn-secondary:hover { background: var(--color-surface-container-high); }
.questions-list { display: flex; flex-direction: column; gap: 12px; }
.empty-state { display: flex; flex-direction: column; align-items: center; gap: 12px; padding: 80px 32px; text-align: center; color: var(--color-muted); }
.empty-state h3 { margin: 0; font-family: var(--font-heading); font-size: 22px; font-weight: 400; color: var(--color-on-surface); }
.empty-state p { margin: 0; font-family: var(--font-body); font-size: 15px; }
.loading-list { display: flex; flex-direction: column; gap: 12px; }
.skeleton-card { height: 100px; border-radius: 16px; }
.shimmer { background: linear-gradient(110deg, var(--color-surface-container-high) 8%, var(--color-surface-canvas) 18%, var(--color-surface-container-high) 33%); background-size: 200% 100%; animation: shimmer 1.4s ease-in-out infinite; }
@keyframes shimmer { to { background-position-x: -200%; } }
.editor-overlay { position: fixed; inset: 0; background: var(--color-overlay); display: grid; place-items: center; z-index: 1000; padding: 24px; }
.editor-dialog { width: 100%; max-width: 600px; max-height: 90vh; background: var(--color-surface-card); border: 1px solid var(--color-outline-light); border-radius: var(--radius-lg); display: flex; flex-direction: column; overflow: hidden; }
.editor-header { display: flex; align-items: center; justify-content: space-between; padding: 24px 28px 16px; }
.editor-header h2 { margin: 0; font-family: var(--font-heading); font-size: 22px; font-weight: 400; color: var(--color-on-surface); }
.close-btn { display: grid; place-items: center; width: 32px; height: 32px; border: none; background: none; color: var(--color-muted); cursor: pointer; border-radius: 8px; }
.close-btn:hover { background: var(--color-surface-container); color: var(--color-on-surface); }
.editor-body { flex: 1; padding: 0 28px 16px; display: flex; flex-direction: column; gap: 16px; overflow-y: auto; }
.field { display: flex; flex-direction: column; gap: 6px; }
.field label { font-family: var(--font-body); font-size: 12px; font-weight: 400; letter-spacing: 0.05em; text-transform: uppercase; color: var(--color-muted); }
.input, .textarea, select.input { width: 100%; padding: 10px 14px; background: var(--color-surface-container); border: 1px solid var(--color-outline-light); border-radius: var(--radius-sm); color: var(--color-on-surface); font-family: var(--font-body); font-size: 14px; outline: none; }
.input:focus, .textarea:focus, select.input:focus { border-color: var(--color-on-surface); }
.textarea { resize: vertical; min-height: 80px; }
.field-row { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.editor-footer { display: flex; justify-content: flex-end; gap: 12px; padding: 16px 28px 24px; border-top: 1px solid var(--color-outline-light); }
.btn-cancel, .btn-save { padding: 10px 20px; border: none; border-radius: var(--radius-sm); font-family: var(--font-body); font-size: 13px; font-weight: 400; cursor: pointer; }
.btn-cancel { background: var(--color-surface-container); color: var(--color-on-surface); }
.btn-save { background: var(--color-primary); color: var(--color-on-primary); }
.btn-save:disabled { opacity: 0.5; cursor: not-allowed; }
</style>

