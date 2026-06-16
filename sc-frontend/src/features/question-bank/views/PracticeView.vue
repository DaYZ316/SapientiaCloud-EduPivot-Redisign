<template>
  <div class="practice-view">
    <button class="back-link" @click="handleBack">
      <ArrowLeft :size="16"/>
      {{ t('questionBank.backToBank') }}
    </button>

    <div v-if="showResult && session" class="result-container">
      <PracticeResult :session="session" @back="handleBack" @retry="startNewSession"/>
    </div>

    <div v-else-if="session && questions.length > 0" class="practice-container">
      <div class="practice-header">
        <h2>{{ bankName }}</h2>
        <PracticeProgress :current="currentIndex + 1" :total="session.totalQuestions"/>
      </div>

      <div v-if="currentQuestion" class="question-panel">
        <div class="question-meta">
          <span class="type-badge">{{ typeName(currentQuestion.questionType) }}</span>
          <span class="score-badge">{{ currentQuestion.score }} {{ t('questionBank.score') }}</span>
        </div>

        <h3 class="question-title">{{ currentQuestion.questionTitle }}</h3>
        <p v-if="currentQuestion.questionContent" class="question-content">{{ currentQuestion.questionContent }}</p>

        <div v-if="currentQuestion.questionType <= 2" class="options-list">
          <label
              v-for="option in (currentQuestion.options || [])"
              :key="option.id"
              :class="{
              selected: selectedOptions.includes(option.id),
              correct: showFeedback && option.isCorrect === 1,
              wrong: showFeedback && selectedOptions.includes(option.id) && option.isCorrect === 0,
            }"
              class="option-item"
          >
            <input
                v-if="currentQuestion.questionType === 1"
                v-model="selectedOptions"
                :disabled="showFeedback"
                :value="option.id"
                type="checkbox"
            />
            <input
                v-else
                v-model="selectedOptions"
                :disabled="showFeedback"
                :value="option.id"
                name="question-option"
                type="radio"
            />
            <span class="option-label">{{ option.optionLabel }}</span>
            <span class="option-content">{{ option.optionContent }}</span>
            <Check v-if="showFeedback && option.isCorrect === 1" :size="16" class="check-icon"/>
          </label>
        </div>

        <div v-else class="text-answer">
          <textarea
              v-model="textAnswer"
              :disabled="showFeedback"
              :placeholder="currentQuestion.questionType === 3 ? '填写答案...' : '输入你的回答...'"
              class="answer-textarea"
              rows="4"
          ></textarea>
        </div>

        <div v-if="showFeedback && currentQuestion.options" class="explanations">
          <div
              v-for="option in currentQuestion.options.filter(o => o.explanation)"
              :key="option.id"
              class="explanation-item"
          >
            <span class="option-label">{{ option.optionLabel }}</span>: {{ option.explanation }}
          </div>
        </div>

        <div class="practice-actions">
          <button v-if="!showFeedback" :disabled="!canSubmit" class="btn-submit" @click="handleSubmit">
            {{ t('questionBank.submitAnswer') }}
          </button>
          <button v-else class="btn-next" @click="handleNext">
            {{
              currentIndex < session.totalQuestions - 1 ? t('questionBank.nextQuestion') : t('questionBank.completePractice')
            }}
          </button>
        </div>
      </div>
    </div>

    <div v-else class="start-container">
      <div class="start-card">
        <BookOpen :size="48" stroke-width="1.4"/>
        <h2>{{ t('questionBank.startPractice') }}</h2>
        <p>{{ session?.totalQuestions || 0 }} 道题</p>
        <button class="btn-primary" @click="startNewSession">
          {{ t('questionBank.startPractice') }}
        </button>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import {ArrowLeft, BookOpen, Check} from 'lucide-vue-next'
import {getQuestionBank, getQuestions} from '@/features/question-bank/api/questionBank'
import type {Question} from '@/features/question-bank/types/questionBank'
import {QuestionType} from '@/features/question-bank/types/questionBank'
import {
  completePracticeSession,
  createPracticeSession,
  getPracticeSession,
  submitAnswer
} from '@/features/question-bank/api/practiceSession'
import type {PracticeSession} from '@/features/question-bank/types/practiceSession'
import {notify} from '@/shared/composables/useGlobalNotification'
import PracticeProgress from '@/features/question-bank/components/PracticeProgress.vue'
import PracticeResult from '@/features/question-bank/components/PracticeResult.vue'

const {t} = useI18n()
const route = useRoute()
const router = useRouter()
// const authStore = useAuthStore()

const bankId = route.params.id as string
const session = ref<PracticeSession | null>(null)
const questions = ref<Question[]>([])
const currentIndex = ref(0)
const selectedOptions = ref<string[]>([])
const textAnswer = ref('')
const showFeedback = ref(false)
const showResult = ref(false)
const bankName = ref('')

const currentQuestion = computed(() => questions.value[currentIndex.value] || null)
const canSubmit = computed(() => {
  if (!currentQuestion.value) return false
  if (currentQuestion.value.questionType <= 2) {
    return selectedOptions.value.length > 0
  }
  return textAnswer.value.trim().length > 0
})

function typeName(type: number) {
  return QuestionType[type] || '未知'
}

onMounted(async () => {
  try {
    const bank = await getQuestionBank(bankId)
    bankName.value = bank.bankName
  } catch {
    notify.error('加载题库失败')
  }
})

async function startNewSession() {
  try {
    showResult.value = false
    currentIndex.value = 0
    selectedOptions.value = []
    textAnswer.value = ''
    showFeedback.value = false

    const sessionId = await createPracticeSession({
      questionBankId: bankId,
      courseId: currentQuestion.value?.courseId || '',
      sessionType: 0,
    })

    const data = await getPracticeSession(sessionId)
    session.value = data

    const questionData = await getQuestions({questionBankId: bankId, status: 1, page: 1, size: 100})
    questions.value = questionData.records || []
  } catch {
    notify.error('创建练习会话失败')
  }
}

async function handleSubmit() {
  if (!session.value || !currentQuestion.value) return
  try {
    const answer = await submitAnswer(session.value.id, {
      questionId: currentQuestion.value.id,
      selectedOptionIds: currentQuestion.value.questionType <= 2 ? selectedOptions.value : undefined,
      textAnswer: currentQuestion.value.questionType >= 3 ? textAnswer.value : undefined,
    })

    showFeedback.value = true

    session.value.answeredCount += 1
    session.value.earnedScore += answer.earnedScore
    if (answer.isCorrect === 1) {
      session.value.correctCount += 1
    }
  } catch {
    notify.error('提交失败')
  }
}

function handleNext() {
  if (currentIndex.value < questions.value.length - 1) {
    currentIndex.value++
    selectedOptions.value = []
    textAnswer.value = ''
    showFeedback.value = false
  } else {
    completeSession()
  }
}

async function completeSession() {
  if (!session.value) return
  try {
    await completePracticeSession(session.value.id)
    session.value.status = 1
    showResult.value = true
  } catch {
    notify.error('完成练习失败')
  }
}

function handleBack() {
  router.push('/question-banks/' + bankId)
}
</script>

<style scoped>
.practice-view {
  max-width: 100%;
}

.back-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0;
  border: none;
  background: none;
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 13px;
  cursor: pointer;
  margin-bottom: 32px;
}

.back-link:hover {
  color: var(--color-on-surface);
}

.practice-container {
  max-width: 720px;
}

.practice-header {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-bottom: 32px;
}

.practice-header h2 {
  margin: 0;
  font-family: var(--font-heading);
  font-size: 28px;
  font-weight: 400;
  line-height: 1.3;
  color: var(--color-on-surface);
}

.question-panel {
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding: 32px;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-lg);
}

.question-meta {
  display: flex;
  gap: 8px;
}

.type-badge, .score-badge {
  font-family: var(--font-body);
  font-size: 11px;
  font-weight: 400;
  padding: 3px 10px;
  border-radius: 4px;
}

.type-badge {
  background: var(--color-surface-container-high);
  color: var(--color-on-surface);
}

.score-badge {
  background: var(--color-surface-container-high);
  color: var(--color-on-surface);
}

.question-title {
  margin: 0;
  font-family: var(--font-heading);
  font-size: 22px;
  font-weight: 400;
  color: var(--color-on-surface);
  line-height: 1.4;
}

.question-content {
  margin: 0;
  font-family: var(--font-body);
  font-size: 15px;
  color: var(--color-muted);
  line-height: 1.6;
}

.options-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.option-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  background: var(--color-surface-container);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: all 0.15s;
}

.option-item:hover {
  border-color: var(--color-on-surface);
}

.option-item.selected {
  border-color: var(--color-primary);
  background: var(--color-surface-container-high);
}

.option-item.correct {
  border-color: #22c55e;
  background: rgba(34, 197, 94, 0.08);
}

.option-item.wrong {
  border-color: #ef4444;
  background: rgba(239, 68, 68, 0.08);
}

.option-label {
  font-family: var(--font-body);
  font-size: 14px;
  font-weight: 400;
  color: var(--color-on-surface);
  min-width: 24px;
}

.option-content {
  font-family: var(--font-body);
  font-size: 14px;
  color: var(--color-on-surface);
  flex: 1;
}

.check-icon {
  color: #22c55e;
  flex-shrink: 0;
}

.answer-textarea {
  width: 100%;
  padding: 12px 14px;
  background: var(--color-surface-container);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  color: var(--color-on-surface);
  font-family: var(--font-body);
  font-size: 14px;
  outline: none;
  resize: vertical;
}

.answer-textarea:focus {
  border-color: var(--color-on-surface);
}

.explanations {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 16px;
  background: var(--color-surface-container);
  border-radius: var(--radius-sm);
}

.explanation-item {
  font-family: var(--font-body);
  font-size: 13px;
  color: var(--color-muted);
}

.practice-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}

.btn-submit, .btn-next {
  padding: 10px 24px;
  border: none;
  border-radius: var(--radius-sm);
  font-family: var(--font-body);
  font-size: 13px;
  font-weight: 400;
  cursor: pointer;
}

.btn-submit {
  background: var(--color-primary);
  color: var(--color-on-primary);
}

.btn-submit:hover:not(:disabled) {
  background: var(--color-primary-soft);
}

.btn-submit:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-next {
  background: var(--color-primary);
  color: var(--color-on-primary);
}

.btn-next:hover {
  background: var(--color-primary-soft);
}

.start-container {
  display: grid;
  place-items: center;
  min-height: 400px;
}

.start-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  padding: 48px;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-lg);
  text-align: center;
}

.start-card h2 {
  margin: 0;
  font-family: var(--font-heading);
  font-size: 24px;
  color: var(--color-on-surface);
}

.start-card p {
  margin: 0;
  font-family: var(--font-body);
  font-size: 14px;
  color: var(--color-muted);
}

.btn-primary {
  padding: 12px 32px;
  border: none;
  border-radius: var(--radius-sm);
  background: var(--color-primary);
  color: var(--color-on-primary);
  font-family: var(--font-body);
  font-size: 14px;
  font-weight: 400;
  cursor: pointer;
}

.btn-primary:hover {
  background: var(--color-primary-soft);
}

.result-container {
  max-width: 600px;
  margin: 0 auto;
}
</style>

