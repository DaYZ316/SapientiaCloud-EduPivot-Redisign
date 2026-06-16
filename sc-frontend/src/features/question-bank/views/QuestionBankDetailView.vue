<template>
  <div class="bank-detail-page">
    <button class="back-link" type="button" @click="goBack">
      <ArrowLeft :size="16"/>
      {{ t('questionBank.backToBank') }}
    </button>

    <div v-if="bank" class="bank-header">
      <div class="header-info">
        <p class="breadcrumb">{{ t('questionBank.breadcrumb', {name: bank.bankName}) }}</p>
        <h1>{{ bank.bankName }}</h1>
        <p v-if="bank.description" class="bank-description">{{ bank.description }}</p>
        <div class="bank-meta">
          <span>{{ bankTypeName(bank.bankType) }}</span>
          <span>{{ difficultyName(bank.difficulty) }}</span>
          <span>{{ bank.questionCount }} {{ t('questionBank.questionCount') }}</span>
          <span>{{ formatDate(bank.updatedAt || bank.createdAt) }}</span>
        </div>
      </div>
      <div class="header-actions">
        <button v-if="isStudent && bank.questionCount > 0" class="btn-primary" type="button" @click="openPractice">
          {{ t('questionBank.startPractice') }}
        </button>
        <button v-if="isTeacher" class="btn-secondary" type="button" @click="openCreateEditor">
          <Plus :size="14"/>
          {{ t('questionBank.newQuestion') }}
        </button>
      </div>
    </div>

    <div v-if="loading" class="loading-list">
      <div v-for="i in 6" :key="i" class="skeleton-row shimmer"></div>
    </div>

    <div v-else-if="questions.length === 0" class="empty-state">
      <FileText :size="36" stroke-width="1.4"/>
      <h3>{{ t('questionBank.noQuestions') }}</h3>
      <p>{{ t('questionBank.noQuestionsDesc') }}</p>
    </div>

    <template v-else>
      <div class="question-workspace">
        <section class="question-ledger" :aria-label="t('questionBank.questionList')">
          <div class="ledger-toolbar">
            <div class="search-field">
              <Search :size="15" stroke-width="1.8"/>
              <input v-model.trim="keyword" type="search" :placeholder="t('questionBank.searchPlaceholder')"/>
            </div>
            <div class="filter-tabs" :aria-label="t('questionBank.questionList')">
              <button
                v-for="filter in filters"
                :key="filter.key"
                type="button"
                :class="{active: activeFilter === filter.key}"
                @click="activeFilter = filter.key"
              >
                {{ filter.label }}
              </button>
            </div>
          </div>

          <div class="question-count">
            <span>{{ t('questionBank.questionList') }}</span>
            <strong>{{ filteredQuestions.length }} / {{ questions.length }}</strong>
          </div>

          <div class="question-rows">
            <button
              v-for="question in filteredQuestions"
              :key="question.id"
              class="question-row"
              :class="{selected: question.id === selectedQuestionId}"
              type="button"
              @click="selectedQuestionId = question.id"
            >
              <span class="active-line" aria-hidden="true"></span>
              <span class="row-main">
                <span class="row-title">
                  {{ question.questionTitle }}
                  <span v-if="isTeacher && question.status === 0 && canManageQuestion(question)" class="status-badge draft">{{ t('questionBank.draft') }}</span>
                  <span v-if="isTeacher && question.status === 1" class="status-badge published">{{ t('questionBank.published') }}</span>
                </span>
                <span class="row-tags">
                  <span>{{ questionTypeName(question.questionType) }}</span>
                  <span>{{ difficultyName(question.difficulty) }}</span>
                  <span>{{ question.score }} {{ t('questionBank.score') }}</span>
                </span>
              </span>
              <span class="row-meta">
                <span v-if="question.estimatedTime">
                  <Clock :size="13"/>
                  {{ question.estimatedTime }}min
                </span>
                <span>
                  <Eye :size="13"/>
                  {{ question.viewCount }}
                </span>
              </span>
            </button>
          </div>

          <div v-if="filteredQuestions.length === 0" class="empty-filter">
            {{ t('questionBank.noMatchFilter') }}
          </div>
        </section>

        <aside class="question-preview" :aria-label="t('questionBank.questionPreview')">
          <template v-if="selectedQuestion">
            <div class="preview-header">
              <div>
                <span class="section-label">{{ t('questionBank.questionPreview') }}</span>
                <h2>{{ selectedQuestion.questionTitle }}</h2>
              </div>
            </div>

            <div class="preview-meta">
              <span>{{ questionTypeName(selectedQuestion.questionType) }}</span>
              <span>{{ difficultyName(selectedQuestion.difficulty) }}</span>
              <span>{{ selectedQuestion.score }} {{ t('questionBank.score') }}</span>
              <span v-if="selectedQuestion.estimatedTime">{{ t('questionBank.estimatedMinutes', {n: selectedQuestion.estimatedTime}) }}</span>
              <span>{{ t('questionBank.viewCount', {n: selectedQuestion.viewCount}) }}</span>
            </div>

            <section class="preview-section">
              <h3>{{ t('questionBank.questionStem') }}</h3>
              <p>{{ selectedQuestion.questionContent || selectedQuestion.questionTitle }}</p>
            </section>

            <section class="preview-section">
              <h3>{{ t('questionBank.options') }}</h3>
              <p v-if="selectedQuestionLoading" class="muted-text">{{ t('questionBank.loadingDetail') }}</p>
              <div v-else-if="selectedQuestion.options?.length" class="option-list">
                <div
                  v-for="option in selectedQuestion.options"
                  :key="option.id"
                  class="option-row"
                  :class="{correct: option.isCorrect === 1}"
                >
                  <span class="option-label">{{ option.optionLabel }}</span>
                  <span>{{ option.optionContent }}</span>
                </div>
              </div>
              <p v-else class="muted-text">{{ t('questionBank.noOptions') }}</p>
            </section>

            <section class="preview-section">
              <h3>{{ t('questionBank.correctAnswer') }}</h3>
              <p v-if="selectedQuestionLoading" class="muted-text">{{ t('questionBank.loadingDetail') }}</p>
              <div v-else-if="answerItems.length" class="answer-list">
                <div v-for="answer in answerItems" :key="answer" class="answer-row">
                  {{ answer }}
                </div>
              </div>
              <p v-else class="muted-text">{{ t('questionBank.noAnswer') }}</p>
            </section>

            <section class="preview-section">
              <h3>{{ t('questionBank.explanation') }}</h3>
              <p>{{ explanationText }}</p>
            </section>

            <section v-if="selectedQuestion.tags?.length" class="preview-section">
              <h3>{{ t('questionBank.tags') }}</h3>
              <div class="tag-list">
                <span v-for="tag in selectedQuestion.tags" :key="tag">{{ tag }}</span>
              </div>
            </section>

            <div class="preview-actions">
              <template v-if="canManageQuestion(selectedQuestion)">
                <template v-if="selectedQuestion.status === 0">
                  <button class="btn-outline" type="button" @click="openEditEditor">{{ t('questionBank.editQuestion') }}</button>
                  <button class="btn-primary compact" type="button" @click="handlePublishQuestion">{{ t('questionBank.publish') }}</button>
                  <button class="btn-outline danger" type="button" @click="handleDeleteQuestion">{{ t('questionBank.deleteQuestion') }}</button>
                </template>
                <template v-else-if="selectedQuestion.status === 1">
                  <button class="btn-outline danger" type="button" @click="handleDeleteQuestion">{{ t('questionBank.deleteQuestion') }}</button>
                </template>
              </template>
              <button v-if="isStudent" class="btn-primary compact" type="button" @click="openPractice">
                {{ t('questionBank.startPractice') }}
              </button>
            </div>
          </template>
        </aside>
      </div>

      <section class="bank-stats" :aria-label="t('questionBank.title')">
        <div class="stat-item">
          <span>{{ t('questionBank.statJudgement') }}</span>
          <strong>{{ judgementCount }}</strong>
        </div>
        <div class="stat-item">
          <span>{{ t('questionBank.statTextQuestion') }}</span>
          <strong>{{ textQuestionCount }}</strong>
        </div>
        <div class="stat-item">
          <span>{{ t('questionBank.statChoice') }}</span>
          <strong>{{ choiceCount }}</strong>
        </div>
        <div class="stat-item">
          <span>{{ t('questionBank.statAvgScore') }}</span>
          <strong>{{ averageScore }}</strong>
        </div>
      </section>
    </template>

    <div v-if="showQuestionEditor" class="editor-overlay" @click.self="showQuestionEditor = false">
      <div class="editor-dialog">
        <div class="editor-header">
          <h2>{{ isEditing ? t('questionBank.editQuestionTitle') : t('questionBank.newQuestion') }}</h2>
          <button class="close-btn" type="button" @click="showQuestionEditor = false"><X :size="18"/></button>
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
              <BaseSelect v-model="newQuestion.questionType" :options="questionTypeOptions" min-width="100%"/>
            </div>
            <div class="field">
              <label>{{ t('questionBank.score') }}</label>
              <BaseNumberStepper v-model="newQuestion.score" :min="0" :step="0.5"/>
            </div>
          </div>
          <div class="field-row">
            <div class="field">
              <label>{{ t('questionBank.difficulty') }}</label>
              <BaseSelect v-model="newQuestion.difficulty" :options="difficultyOptions" min-width="100%"/>
            </div>
            <div class="field">
              <label>{{ t('questionBank.estimatedTime') }}</label>
              <BaseNumberStepper v-model="newQuestion.estimatedTime" :min="1"/>
            </div>
          </div>
          <div v-if="isOptionQuestion" class="field">
            <div class="field-heading">
              <label>{{ t('questionBank.options') }}</label>
              <button v-if="canEditOptions" class="btn-inline" type="button" @click="addOption">
                <Plus :size="14"/>
                {{ t('questionBank.addOption') }}
              </button>
            </div>
            <div class="editor-option-list">
              <div v-for="(option, index) in newQuestion.options" :key="option.optionLabel" class="editor-option-row">
                <button
                  class="correct-toggle"
                  :class="{active: option.isCorrect === 1}"
                  type="button"
                  :aria-label="t('questionBank.setCorrectAria', {label: option.optionLabel})"
                  @click="toggleCorrectOption(index)"
                >
                  <Check :size="15"/>
                </button>
                <span class="editor-option-label">{{ option.optionLabel }}</span>
                <input
                  v-model="option.optionContent"
                  class="input"
                  type="text"
                  :disabled="!canEditOptions"
                  :placeholder="t('questionBank.optionContentPlaceholder')"
                />
                <button
                  v-if="canEditOptions && newQuestion.options.length > 2"
                  class="icon-button"
                  type="button"
                  :aria-label="t('questionBank.removeOption')"
                  @click="removeOption(index)"
                >
                  <Trash2 :size="15"/>
                </button>
                <input
                  v-model="option.explanation"
                  class="input option-explanation"
                  type="text"
                  :placeholder="t('questionBank.optionExplanationPlaceholder')"
                />
              </div>
            </div>
          </div>
          <div v-if="isAnswerQuestion" class="field">
            <div class="field-heading">
              <label>{{ t('questionBank.correctAnswer') }}</label>
              <button class="btn-inline" type="button" @click="addAnswer">
                <Plus :size="14"/>
                {{ t('questionBank.addAnswer') }}
              </button>
            </div>
            <div class="answer-editor-list">
              <div v-for="(answer, index) in newQuestion.answers" :key="index" class="answer-editor-row">
                <span class="answer-order">{{ index + 1 }}</span>
                <textarea
                  v-model="answer.answerContent"
                  class="textarea answer-content"
                  rows="2"
                  :placeholder="t('questionBank.answerContentPlaceholder')"
                ></textarea>
                <button
                  v-if="newQuestion.answers.length > 1"
                  class="icon-button"
                  type="button"
                  :aria-label="t('questionBank.removeAnswer')"
                  @click="removeAnswer(index)"
                >
                  <Trash2 :size="15"/>
                </button>
                <input
                  v-model="answer.explanation"
                  class="input answer-explanation"
                  type="text"
                  :placeholder="t('questionBank.answerExplanationPlaceholder')"
                />
              </div>
            </div>
          </div>
        </div>
        <div class="editor-footer">
          <button class="btn-cancel" type="button" @click="showQuestionEditor = false">{{ t('chapter.cancel') }}</button>
          <button class="btn-save" type="button" :disabled="!newQuestion.questionTitle.trim()" @click="handleCreateQuestion">{{ isEditing ? t('questionBank.saveQuestion') : t('chapter.save') }}</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import {onMounted, ref, computed, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import {ArrowLeft, Check, Clock, Eye, FileText, Plus, Search, Trash2, X} from 'lucide-vue-next'
import {getQuestionBank, getQuestions, getQuestion, createQuestion, updateQuestion, deleteQuestion, publishQuestion} from '@/features/question-bank/api/questionBank'
import type {QuestionBank, Question} from '@/features/question-bank/types/questionBank'
import BaseNumberStepper from '@/shared/components/BaseNumberStepper.vue'
import BaseSelect from '@/shared/components/BaseSelect.vue'
import {useAuthStore} from '@/features/auth/stores/auth'
import {confirmDialog} from '@/shared/composables/useConfirmDialog'
import {notify} from '@/shared/composables/useGlobalNotification'

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

type FilterKey = 'all' | 'single' | 'multiple' | 'judge' | 'blank' | 'short'
type DraftOption = {
  optionLabel: string
  optionContent: string
  isCorrect: number
  explanation: string
}
type DraftAnswer = {
  answerContent: string
  explanation: string
}

const bankId = route.params.id as string
const loading = ref(true)
const bank = ref<QuestionBank | null>(null)
const questions = ref<Question[]>([])
const selectedQuestionId = ref<string | null>(null)
const detailLoadingQuestionId = ref<string | null>(null)
const keyword = ref('')
const activeFilter = ref<FilterKey>('all')
const showQuestionEditor = ref(false)
const editingQuestionId = ref<string | null>(null)

const isAdmin = computed(() => authStore.user?.role === 0)
const isTeacher = computed(() => authStore.user?.role === 2 || isAdmin.value)
const isStudent = computed(() => authStore.user?.role === 1)
const isEditing = computed(() => editingQuestionId.value !== null)
const currentUserId = computed(() => authStore.user?.id)

const filters = computed<Array<{key: FilterKey; label: string}>>(() => [
  {key: 'all', label: t('questionBank.filterAll')},
  {key: 'single', label: t('questionBank.filterSingle')},
  {key: 'multiple', label: t('questionBank.filterMultiple')},
  {key: 'judge', label: t('questionBank.filterJudge')},
  {key: 'blank', label: t('questionBank.filterBlank')},
  {key: 'short', label: t('questionBank.filterShort')},
])

const questionTypeOptions = computed(() => [
  {label: t('questionBank.typeSingle'), value: 0},
  {label: t('questionBank.typeMultiple'), value: 1},
  {label: t('questionBank.typeJudge'), value: 2},
  {label: t('questionBank.typeBlank'), value: 3},
  {label: t('questionBank.typeShort'), value: 4},
])

const difficultyOptions = computed(() => [
  {label: t('questionBank.difficultyEasy'), value: 1},
  {label: t('questionBank.difficultyMedium'), value: 2},
  {label: t('questionBank.difficultyHard'), value: 3},
])

const defaultChoiceOptions = () => [
  createOption('A', '', 1),
  createOption('B', '', 0),
  createOption('C', '', 0),
  createOption('D', '', 0),
]

const defaultJudgementOptions = () => [
  createOption('A', t('questionBank.judgementCorrect'), 1),
  createOption('B', t('questionBank.judgementWrong'), 0),
]

const defaultAnswers = () => [
  createAnswer(),
]

const newQuestion = ref(createEmptyQuestion())

const filteredQuestions = computed(() => {
  const query = keyword.value.toLowerCase()
  return questions.value.filter((question) => {
    const matchesKeyword = !query ||
      question.questionTitle.toLowerCase().includes(query) ||
      (question.tags || []).some((tag) => tag.toLowerCase().includes(query))
    if (!matchesKeyword) return false
    if (activeFilter.value === 'single') return question.questionType === 0
    if (activeFilter.value === 'multiple') return question.questionType === 1
    if (activeFilter.value === 'judge') return question.questionType === 2
    if (activeFilter.value === 'blank') return question.questionType === 3
    if (activeFilter.value === 'short') return question.questionType === 4
    return true
  })
})

const selectedQuestion = computed(() =>
  questions.value.find((question) => question.id === selectedQuestionId.value) || filteredQuestions.value[0] || null
)

const selectedQuestionLoading = computed(() => detailLoadingQuestionId.value === selectedQuestionId.value)
const isOptionQuestion = computed(() => [0, 1, 2].includes(newQuestion.value.questionType))
const isAnswerQuestion = computed(() => [3, 4].includes(newQuestion.value.questionType))
const canEditOptions = computed(() => newQuestion.value.questionType !== 2)

const answerItems = computed(() => {
  const question = selectedQuestion.value
  if (!question) return []
  const textAnswers = (question.answers || [])
    .map((answer) => answer.answerContent.trim())
    .filter((answer) => answer.length > 0)
  const optionAnswers = (question.options || [])
    .filter((option) => option.isCorrect === 1)
    .map((option) => option.optionLabel ? option.optionLabel + '. ' + option.optionContent : option.optionContent)
  return textAnswers.length ? textAnswers : optionAnswers
})

const explanationText = computed(() => {
  const question = selectedQuestion.value
  if (!question) return ''
  const optionExplanation = question.options?.find((option) => option.isCorrect === 1 && option.explanation)?.explanation
  const answerExplanation = question.answers?.find((answer) => answer.explanation)?.explanation
  return optionExplanation || answerExplanation || t('questionBank.noExplanation')
})

const judgementCount = computed(() => questions.value.filter((question) => question.questionType === 2).length)
const textQuestionCount = computed(() => questions.value.filter((question) => question.questionType === 3 || question.questionType === 4).length)
const choiceCount = computed(() => questions.value.filter((question) => question.questionType === 0 || question.questionType === 1).length)
const averageScore = computed(() => {
  if (questions.value.length === 0) return '0'
  const total = questions.value.reduce((sum, question) => sum + Number(question.score || 0), 0)
  return (total / questions.value.length).toFixed(1)
})

function canManageQuestion(question: Question) {
  return isAdmin.value || question.sysUserId === currentUserId.value
}

function canEditQuestion(question: Question) {
  return question.status === 0 && canManageQuestion(question)
}

watch(filteredQuestions, (items) => {
  if (!items.length) {
    selectedQuestionId.value = null
    return
  }
  if (!items.some((question) => question.id === selectedQuestionId.value)) {
    selectedQuestionId.value = items[0].id
  }
})

watch(selectedQuestionId, (questionId) => {
  if (questionId) {
    void loadQuestionDetail(questionId)
  }
})

watch(showQuestionEditor, (open) => {
  if (!open) {
    editingQuestionId.value = null
  }
})

watch(() => newQuestion.value.questionType, (questionType) => {
  if (questionType === 2) {
    newQuestion.value.options = defaultJudgementOptions()
    newQuestion.value.answers = []
    return
  }
  if (questionType === 0 || questionType === 1) {
    if (newQuestion.value.options.length < 2) {
      newQuestion.value.options = defaultChoiceOptions()
    }
    newQuestion.value.answers = []
    return
  }
  newQuestion.value.options = []
  if (newQuestion.value.answers.length === 0) {
    newQuestion.value.answers = defaultAnswers()
  }
})

function createOption(optionLabel: string, optionContent = '', isCorrect = 0): DraftOption {
  return {
    optionLabel,
    optionContent,
    isCorrect,
    explanation: '',
  }
}

function createAnswer(): DraftAnswer {
  return {
    answerContent: '',
    explanation: '',
  }
}

function createEmptyQuestion() {
  return {
    questionTitle: '',
    questionContent: '',
    questionType: 0,
    score: 1,
    difficulty: 2,
    estimatedTime: 1,
    allowPartialCredit: 0,
    options: defaultChoiceOptions(),
    answers: [] as DraftAnswer[],
  }
}

function difficultyName(d: number) {
  const map: Record<number, string> = {
    1: t('questionBank.difficultyEasy'),
    2: t('questionBank.difficultyMedium'),
    3: t('questionBank.difficultyHard'),
  }
  return map[d] || t('questionBank.unknown')
}

function questionTypeName(type: number) {
  const map: Record<number, string> = {
    0: t('questionBank.typeSingle'),
    1: t('questionBank.typeMultiple'),
    2: t('questionBank.typeJudge'),
    3: t('questionBank.typeBlank'),
    4: t('questionBank.typeShort'),
  }
  return map[type] || t('questionBank.unknown')
}

function bankTypeName(type: number) {
  const map: Record<number, string> = {
    0: t('questionBank.bankTypePractice'),
    1: t('questionBank.bankTypeExam'),
    2: t('questionBank.bankTypeHomework'),
  }
  return map[type] || t('questionBank.defaultBankType')
}

function formatDate(dateStr?: string | null) {
  if (!dateStr) return t('questionBank.notUpdated')
  return new Intl.DateTimeFormat(undefined, {month: '2-digit', day: '2-digit'}).format(new Date(dateStr))
}

function openPractice() {
  router.push('/question-banks/' + bankId + '/practice')
}

function openCreateEditor() {
  editingQuestionId.value = null
  newQuestion.value = createEmptyQuestion()
  showQuestionEditor.value = true
}

function openEditEditor() {
  const question = selectedQuestion.value
  if (!question || !canEditQuestion(question)) return
  if (question.options == null || question.answers == null) {
    notify.error(t('questionBank.errorLoadDetail'))
    return
  }
  editingQuestionId.value = question.id
  newQuestion.value = {
    questionTitle: question.questionTitle,
    questionContent: question.questionContent || '',
    questionType: question.questionType,
    score: Number(question.score),
    difficulty: question.difficulty,
    estimatedTime: question.estimatedTime || 1,
    allowPartialCredit: question.allowPartialCredit,
    options: question.options.map((opt) => ({
      optionLabel: opt.optionLabel,
      optionContent: opt.optionContent,
      isCorrect: opt.isCorrect,
      explanation: opt.explanation || '',
    })),
    answers: question.answers.map((ans) => ({
      answerContent: ans.answerContent,
      explanation: ans.explanation || '',
    })),
  }
  showQuestionEditor.value = true
}

function addOption() {
  const optionLabel = String.fromCharCode(65 + newQuestion.value.options.length)
  newQuestion.value.options.push(createOption(optionLabel))
}

function removeOption(index: number) {
  const removedCorrect = newQuestion.value.options[index]?.isCorrect === 1
  newQuestion.value.options.splice(index, 1)
  newQuestion.value.options.forEach((option, optionIndex) => {
    option.optionLabel = String.fromCharCode(65 + optionIndex)
  })
  if (removedCorrect && newQuestion.value.options.length > 0) {
    newQuestion.value.options[0].isCorrect = 1
  }
}

function toggleCorrectOption(index: number) {
  if (newQuestion.value.questionType === 0 || newQuestion.value.questionType === 2) {
    newQuestion.value.options.forEach((option, optionIndex) => {
      option.isCorrect = optionIndex === index ? 1 : 0
    })
    return
  }
  newQuestion.value.options[index].isCorrect = newQuestion.value.options[index].isCorrect === 1 ? 0 : 1
}

function addAnswer() {
  newQuestion.value.answers.push(createAnswer())
}

function removeAnswer(index: number) {
  newQuestion.value.answers.splice(index, 1)
}

function buildQuestionOptions() {
  if (!isOptionQuestion.value) return undefined
  return newQuestion.value.options
    .filter((option) => option.optionContent.trim())
    .map((option) => ({
      optionLabel: option.optionLabel,
      optionContent: option.optionContent.trim(),
      isCorrect: option.isCorrect,
      explanation: option.explanation.trim() || undefined,
    }))
}

function buildQuestionAnswers() {
  if (!isAnswerQuestion.value) return undefined
  return newQuestion.value.answers
    .map((answer, index) => ({
      answerContent: answer.answerContent.trim(),
      explanation: answer.explanation.trim() || undefined,
      sortOrder: index + 1,
    }))
    .filter((answer) => answer.answerContent)
}

function validateQuestionForm() {
  if (isOptionQuestion.value) {
    const options = buildQuestionOptions() || []
    if (options.length < 2) {
      notify.error(t('questionBank.validationMinOptions'))
      return false
    }
    if (!options.some((option) => option.isCorrect === 1)) {
      notify.error(t('questionBank.validationSelectAnswer'))
      return false
    }
  }
  if (isAnswerQuestion.value && (buildQuestionAnswers() || []).length === 0) {
    notify.error(t('questionBank.validationFillAnswer'))
    return false
  }
  return true
}

async function loadQuestionDetail(questionId: string) {
  if (detailLoadingQuestionId.value === questionId) return
  const current = questions.value.find((question) => question.id === questionId)
  if (!current || (current.options != null && current.answers != null)) return
  detailLoadingQuestionId.value = questionId
  try {
    const detail = await getQuestion(questionId)
    const index = questions.value.findIndex((question) => question.id === questionId)
    if (index !== -1) {
      questions.value.splice(index, 1, {...questions.value[index], ...detail})
    }
  } catch {
    notify.error(t('questionBank.errorLoadDetail'))
  } finally {
    if (detailLoadingQuestionId.value === questionId) {
      detailLoadingQuestionId.value = null
    }
  }
}

async function loadData() {
  const [bankData, questionsData] = await Promise.all([
    getQuestionBank(bankId),
    getQuestions({questionBankId: bankId, page: 1, size: 100}),
  ])
  bank.value = bankData
  questions.value = questionsData.records || []
  selectedQuestionId.value = questions.value[0]?.id || null
  if (selectedQuestionId.value) {
    await loadQuestionDetail(selectedQuestionId.value)
  }
}

onMounted(async () => {
  try {
    await loadData()
  } finally {
    loading.value = false
  }
})

async function handleCreateQuestion() {
  if (!bank.value) return
  if (!validateQuestionForm()) return
  const options = buildQuestionOptions()
  const answers = buildQuestionAnswers()
  try {
    if (isEditing.value && editingQuestionId.value) {
      await updateQuestion(editingQuestionId.value, {
        questionTitle: newQuestion.value.questionTitle,
        questionContent: newQuestion.value.questionContent || undefined,
        questionType: newQuestion.value.questionType,
        score: newQuestion.value.score,
        difficulty: newQuestion.value.difficulty,
        estimatedTime: newQuestion.value.estimatedTime,
        allowPartialCredit: newQuestion.value.questionType === 1 ? 1 : 0,
        options,
        answers,
      })
      notify.success(t('questionBank.successUpdated'))
    } else {
      await createQuestion({
        questionBankId: bankId,
        questionTitle: newQuestion.value.questionTitle,
        questionContent: newQuestion.value.questionContent || undefined,
        questionType: newQuestion.value.questionType,
        score: newQuestion.value.score,
        difficulty: newQuestion.value.difficulty,
        estimatedTime: newQuestion.value.estimatedTime,
        allowPartialCredit: newQuestion.value.questionType === 1 ? 1 : 0,
        options,
        answers,
      })
      notify.success(t('questionBank.successCreated'))
    }
    showQuestionEditor.value = false
    editingQuestionId.value = null
    newQuestion.value = createEmptyQuestion()
    await loadData()
  } catch {
    notify.error(isEditing.value ? t('questionBank.errorUpdateFailed') : t('questionBank.errorCreateFailed'))
  }
}

async function handlePublishQuestion() {
  const question = selectedQuestion.value
  if (!question || !canEditQuestion(question)) return
  try {
    await publishQuestion(question.id)
    notify.success(t('questionBank.successPublished'))
    await loadData()
  } catch {
    notify.error(t('questionBank.errorPublishFailed'))
  }
}

async function handleDeleteQuestion() {
  const question = selectedQuestion.value
  if (!question || !canManageQuestion(question)) return
  if (!(await confirmDialog({message: t('questionBank.confirmDeleteQuestion'), confirmVariant: 'danger'}))) return
  try {
    await deleteQuestion(question.id)
    notify.success(t('questionBank.successDeleted'))
    const currentIndex = filteredQuestions.value.findIndex((q) => q.id === question.id)
    await loadData()
    const remaining = filteredQuestions.value
    if (remaining.length === 0) {
      selectedQuestionId.value = null
    } else if (currentIndex < remaining.length) {
      selectedQuestionId.value = remaining[currentIndex].id
    } else {
      selectedQuestionId.value = remaining[remaining.length - 1].id
    }
  } catch {
    notify.error(t('questionBank.errorDeleteFailed'))
  }
}
</script>

<style scoped>
.bank-detail-page {
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
  margin-bottom: 24px;
}

.back-link:hover {
  color: var(--color-on-surface);
}

.bank-header {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  align-items: flex-start;
  padding-bottom: 24px;
  border-bottom: 1px solid var(--color-outline-light);
  margin-bottom: 24px;
}

.header-info {
  min-width: 0;
}

.breadcrumb,
.section-label,
.question-count span,
.stat-item span,
.preview-section h3 {
  font-family: var(--font-label);
  font-size: 12px;
  font-weight: 400;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--color-muted);
}

.breadcrumb {
  margin: 0 0 10px;
}

.header-info h1 {
  margin: 0;
  font-family: var(--font-heading);
  font-size: clamp(32px, 4vw, 48px);
  font-weight: 400;
  line-height: 1.1;
  color: var(--color-on-surface);
  text-wrap: balance;
}

.bank-description {
  max-width: 68ch;
  margin: 12px 0 0;
  font-family: var(--font-body);
  font-size: 15px;
  line-height: 1.55;
  color: var(--color-muted);
}

.bank-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 16px;
}

.bank-meta span,
.preview-meta span,
.row-tags span,
.tag-list span {
  display: inline-flex;
  align-items: center;
  min-height: 26px;
  padding: 0 9px;
  background: var(--color-surface-container);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  color: var(--color-on-surface-variant);
  font-family: var(--font-label);
  font-size: 12px;
  line-height: 1;
}

.header-actions {
  display: flex;
  gap: 10px;
  flex-shrink: 0;
}

.btn-primary,
.btn-secondary,
.btn-outline,
.btn-cancel,
.btn-save {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 40px;
  padding: 0 18px;
  border-radius: var(--radius-sm);
  font-family: var(--font-label);
  font-size: 13px;
  font-weight: 400;
  cursor: pointer;
  transition: background 0.2s ease, border-color 0.2s ease, color 0.2s ease, transform 0.2s ease;
}

.btn-primary,
.btn-save {
  border: 1px solid var(--color-primary);
  background: var(--color-primary);
  color: var(--color-on-primary);
}

.btn-primary:hover:not(:disabled),
.btn-save:hover:not(:disabled) {
  background: var(--color-primary-soft);
}

.btn-secondary,
.btn-outline,
.btn-cancel {
  border: 1px solid var(--color-outline-light);
  background: transparent;
  color: var(--color-on-surface);
}

.btn-secondary:hover,
.btn-outline:hover:not(:disabled),
.btn-cancel:hover {
  background: var(--color-surface-container-high);
  border-color: var(--color-outline);
}

.btn-outline:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.btn-outline.danger {
  color: var(--color-error);
  border-color: var(--color-error);
}

.btn-outline.danger:hover {
  background: var(--color-surface-container-high);
  border-color: var(--color-error);
}

.status-badge {
  display: inline-flex;
  align-items: center;
  min-height: 18px;
  padding: 0 6px;
  border-radius: var(--radius-sm);
  font-family: var(--font-label);
  font-size: 10px;
  font-weight: 400;
  letter-spacing: 0.04em;
  vertical-align: middle;
  margin-left: 6px;
}

.status-badge.draft {
  background: var(--color-surface-container);
  border: 1px solid var(--color-outline-light);
  color: var(--color-muted);
}

.status-badge.published {
  background: var(--color-surface-container-high);
  border: 1px solid var(--color-outline);
  color: var(--color-on-surface);
}

.compact {
  min-height: 36px;
}

.question-workspace {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(360px, 42%);
  gap: 16px;
  align-items: start;
}

.question-ledger,
.question-preview,
.bank-stats {
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
}

.ledger-toolbar {
  display: grid;
  grid-template-columns: minmax(240px, 1fr) auto;
  gap: 12px;
  align-items: center;
  padding: 16px;
  border-bottom: 1px solid var(--color-outline-light);
}

.search-field {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 42px;
  padding: 0 12px;
  background: var(--color-surface-canvas);
  border: 1px solid var(--color-outline-light);
  color: var(--color-muted);
}

.search-field input {
  width: 100%;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--color-on-surface);
  font-family: var(--font-body);
  font-size: 14px;
}

.search-field input::placeholder {
  color: var(--color-outline);
}

.filter-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  justify-content: flex-end;
}

.filter-tabs button {
  min-height: 34px;
  padding: 0 11px;
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 12px;
  cursor: pointer;
}

.filter-tabs button:hover,
.filter-tabs button.active {
  border-color: var(--color-primary);
  color: var(--color-on-surface);
}

.question-count {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--color-outline-light);
}

.question-count strong {
  color: var(--color-on-surface);
  font-family: var(--font-label);
  font-size: 12px;
  font-weight: 400;
}

.question-rows {
  display: flex;
  flex-direction: column;
}

.question-row {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 16px;
  align-items: center;
  min-height: 64px;
  padding: 12px 16px 12px 20px;
  border: 0;
  border-bottom: 1px solid var(--color-outline-light);
  background: transparent;
  color: inherit;
  text-align: left;
  cursor: pointer;
}

.question-row:last-child {
  border-bottom: 0;
}

.question-row:hover,
.question-row.selected {
  background: var(--color-surface-container);
}

.active-line {
  position: absolute;
  left: 0;
  top: 12px;
  bottom: 12px;
  width: 2px;
  background: transparent;
}

.question-row.selected .active-line {
  background: var(--color-primary);
}

.row-main {
  min-width: 0;
}

.row-title {
  display: block;
  overflow: hidden;
  color: var(--color-on-surface);
  font-family: var(--font-body);
  font-size: 15px;
  font-weight: 600;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.row-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.row-tags span {
  min-height: 22px;
  padding-inline: 7px;
  color: var(--color-muted);
}

.row-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 12px;
  white-space: nowrap;
}

.row-meta span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.question-preview {
  position: sticky;
  top: 24px;
}

.preview-header {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  align-items: flex-start;
  padding: 20px;
  border-bottom: 1px solid var(--color-outline-light);
}

.preview-header h2 {
  margin: 8px 0 0;
  color: var(--color-on-surface);
  font-family: var(--font-heading);
  font-size: 28px;
  font-weight: 400;
  line-height: 1.25;
  text-wrap: pretty;
}

.preview-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 14px 20px;
  border-bottom: 1px solid var(--color-outline-light);
}

.preview-section {
  padding: 18px 20px;
  border-bottom: 1px solid var(--color-outline-light);
}

.preview-section h3 {
  margin: 0 0 10px;
}

.preview-section p {
  margin: 0;
  color: var(--color-on-surface-variant);
  font-family: var(--font-body);
  font-size: 15px;
  line-height: 1.65;
}

.option-list {
  display: grid;
  gap: 8px;
}

.answer-list {
  display: grid;
  gap: 8px;
}

.option-row {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr);
  gap: 10px;
  align-items: center;
  min-height: 44px;
  padding: 10px 12px;
  background: var(--color-surface-canvas);
  border: 1px solid var(--color-outline-light);
  color: var(--color-on-surface-variant);
  font-family: var(--font-body);
  font-size: 14px;
  line-height: 1.5;
}

.answer-row {
  padding: 10px 12px;
  background: var(--color-surface-container);
  border: 1px solid var(--color-outline-light);
  color: var(--color-on-surface);
  font-family: var(--font-body);
  font-size: 14px;
  line-height: 1.5;
}

.option-row.correct {
  border-color: var(--color-primary);
  background: var(--color-surface-container);
  color: var(--color-on-surface);
}

.option-label {
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  border: 1px solid var(--color-outline-light);
  color: var(--color-on-surface);
  font-family: var(--font-label);
  font-size: 12px;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.muted-text {
  color: var(--color-muted) !important;
}

.preview-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
  padding: 16px 20px;
}

.bank-stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  margin-top: 16px;
}

.stat-item {
  min-height: 84px;
  padding: 16px 18px;
  border-right: 1px solid var(--color-outline-light);
}

.stat-item:last-child {
  border-right: 0;
}

.stat-item span {
  display: block;
  margin-bottom: 8px;
}

.stat-item strong {
  color: var(--color-on-surface);
  font-family: var(--font-heading);
  font-size: 30px;
  font-weight: 400;
  line-height: 1;
}

.empty-filter {
  padding: 48px 20px;
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 14px;
  text-align: center;
  border-top: 1px solid var(--color-outline-light);
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 80px 32px;
  text-align: center;
  color: var(--color-muted);
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
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

.loading-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.skeleton-row {
  height: 72px;
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

.editor-overlay {
  position: fixed;
  inset: 0;
  background: var(--color-overlay);
  display: grid;
  place-items: center;
  z-index: 1000;
  padding: 24px;
}

.editor-dialog {
  width: 100%;
  max-width: 600px;
  max-height: 90vh;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.editor-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24px 28px 16px;
}

.editor-header h2 {
  margin: 0;
  font-family: var(--font-heading);
  font-size: 22px;
  font-weight: 400;
  color: var(--color-on-surface);
}

.close-btn {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border: none;
  background: none;
  color: var(--color-muted);
  cursor: pointer;
  border-radius: var(--radius-sm);
}

.close-btn:hover {
  background: var(--color-surface-container);
  color: var(--color-on-surface);
}

.editor-body {
  flex: 1;
  padding: 0 28px 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  overflow-y: auto;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.field-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.field label {
  font-family: var(--font-label);
  font-size: 12px;
  font-weight: 400;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: var(--color-muted);
}

.btn-inline {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  min-height: 30px;
  padding: 0 10px;
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-on-surface);
  font-family: var(--font-label);
  font-size: 12px;
  cursor: pointer;
}

.btn-inline:hover {
  background: var(--color-surface-container-high);
}

.input,
.textarea,
select.input {
  width: 100%;
  padding: 10px 14px;
  background: var(--color-surface-container);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  color: var(--color-on-surface);
  font-family: var(--font-body);
  font-size: 14px;
  outline: none;
}

.input:focus,
.textarea:focus,
select.input:focus {
  border-color: var(--color-on-surface);
}

.textarea {
  resize: vertical;
  min-height: 80px;
}

.field-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.editor-option-list,
.answer-editor-list {
  display: grid;
  gap: 10px;
}

.editor-option-row,
.answer-editor-row {
  display: grid;
  gap: 8px;
  padding: 10px;
  background: var(--color-surface-container);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
}

.editor-option-row {
  grid-template-columns: 34px 28px minmax(0, 1fr) 34px;
  align-items: center;
}

.answer-editor-row {
  grid-template-columns: 28px minmax(0, 1fr) 34px;
  align-items: start;
}

.option-explanation,
.answer-explanation {
  grid-column: 3 / -1;
}

.answer-explanation {
  grid-column: 2 / -1;
}

.answer-content {
  min-height: 60px;
}

.correct-toggle,
.icon-button {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  padding: 0;
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  background: var(--color-surface-card);
  color: var(--color-muted);
  cursor: pointer;
}

.correct-toggle.active {
  border-color: var(--color-primary);
  background: var(--color-primary);
  color: var(--color-on-primary);
}

.icon-button:hover,
.correct-toggle:hover {
  border-color: var(--color-outline);
  color: var(--color-on-surface);
}

.editor-option-label,
.answer-order {
  color: var(--color-on-surface);
  font-family: var(--font-label);
  font-size: 13px;
  text-align: center;
}

.editor-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px 28px 24px;
  border-top: 1px solid var(--color-outline-light);
}

.btn-save:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

@media (max-width: 1180px) {
  .ledger-toolbar {
    grid-template-columns: 1fr;
  }

  .filter-tabs {
    justify-content: flex-start;
  }

  .question-workspace {
    grid-template-columns: 1fr;
  }

  .question-preview {
    position: static;
  }
}

@media (max-width: 760px) {
  .bank-header,
  .header-actions,
  .preview-header,
  .editor-footer {
    flex-direction: column;
    align-items: stretch;
  }

  .question-row {
    grid-template-columns: 1fr;
  }

  .row-meta {
    flex-wrap: wrap;
  }

  .bank-stats {
    grid-template-columns: 1fr 1fr;
  }

  .stat-item:nth-child(2n) {
    border-right: 0;
  }

  .stat-item:nth-child(n + 3) {
    border-top: 1px solid var(--color-outline-light);
  }

  .field-row {
    grid-template-columns: 1fr;
  }

  .editor-option-row,
  .answer-editor-row {
    grid-template-columns: 34px minmax(0, 1fr) 34px;
  }

  .editor-option-label {
    display: none;
  }

  .option-explanation,
  .answer-explanation {
    grid-column: 1 / -1;
  }
}
</style>
