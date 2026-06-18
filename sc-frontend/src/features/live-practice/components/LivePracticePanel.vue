<template>
  <aside class="live-practice-panel">
    <header class="panel-header">
      <div>
        <span>Live Practice</span>
        <h2>{{ isTeacher ? '随堂练习发布' : '随堂练习' }}</h2>
      </div>
      <button class="icon-button" type="button" @click="$emit('close')">
        <X :size="18" stroke-width="1.8"/>
      </button>
    </header>

    <div v-if="loading" class="panel-state">加载中...</div>
    <div v-else-if="loadFailed" class="panel-state">
      <p>随堂练习加载失败</p>
      <button class="text-button" type="button" @click="loadGroups">重试</button>
    </div>

    <template v-else>
      <section v-if="isTeacher" class="publish-form">
        <div class="limit-line">已发布 {{ groups.length }}/5 组</div>
        <label>
          <span>练习标题</span>
          <input v-model="form.title" maxlength="200" type="text"/>
        </label>
        <div class="time-grid">
          <label>
            <span>开始时间</span>
            <input v-model="form.availableStartAt" type="datetime-local"/>
          </label>
          <label>
            <span>截止时间</span>
            <input v-model="form.availableEndAt" type="datetime-local"/>
          </label>
        </div>
        <label class="check-row">
          <input v-model="allowLateSubmission" type="checkbox"/>
          <span>允许补交</span>
        </label>

        <section class="question-picker">
          <h3>从课程题库选择</h3>
          <input v-model="questionKeyword" placeholder="搜索题目" type="search" @keydown.enter.prevent="loadQuestions"/>
          <button class="text-button" type="button" @click="loadQuestions">搜索</button>
          <div class="question-list">
            <label v-for="question in questions" :key="question.id" class="question-row">
              <input v-model="selectedQuestionIds" :value="question.id" type="checkbox"/>
              <span>{{ question.questionTitle }}</span>
            </label>
          </div>
        </section>

        <section class="quick-question">
          <h3>快速出题</h3>
          <label>
            <span>题干</span>
            <input v-model="quickQuestion.questionTitle" type="text"/>
          </label>
          <label>
            <span>题型</span>
            <select v-model.number="quickQuestion.questionType">
              <option :value="0">单选题</option>
              <option :value="1">多选题</option>
              <option :value="2">判断题</option>
              <option :value="3">填空题</option>
              <option :value="4">简答题</option>
            </select>
          </label>
          <label class="check-row disabled">
            <input disabled type="checkbox"/>
            <span>AI 判分（待开发）</span>
          </label>
          <div v-if="quickQuestion.questionType <= 2" class="option-grid">
            <label v-for="option in quickQuestion.options" :key="option.optionLabel">
              <span>{{ option.optionLabel }}</span>
              <input v-model="option.optionContent" type="text"/>
              <input v-model="option.isCorrect" type="checkbox"/>
            </label>
          </div>
          <button class="text-button" type="button" @click="addQuickQuestion">加入本次发布</button>
          <div v-if="createdQuestions.length > 0" class="created-list">
            <span v-for="(question, index) in createdQuestions" :key="index">{{ question.questionTitle }}</span>
          </div>
        </section>

        <button
            :disabled="publishing || groups.length >= 5"
            class="primary-button"
            type="button"
            @click="publish"
        >
          {{ publishing ? '发布中...' : '发布练习' }}
        </button>
      </section>

      <section class="group-list">
        <article v-for="group in groups" :key="group.id" class="practice-group">
          <button class="group-title" type="button" @click="selectGroup(group.id)">
            <span>第 {{ group.publishOrder }} 组</span>
            <strong>{{ group.title }}</strong>
          </button>
          <div v-if="activeGroup?.id === group.id" class="question-stack">
            <article v-for="question in activeGroup.questions || []" :key="question.id" class="answer-card">
              <h3>{{ question.questionOrder }}. {{ question.questionTitle }}</h3>
              <p v-if="question.questionContent">{{ question.questionContent }}</p>
              <div v-if="question.options?.length" class="answer-options">
                <label v-for="option in question.options" :key="option.id">
                  <input
                      :checked="answerSelected(question.id, option.id)"
                      :disabled="Boolean(question.mySubmission) || submittingQuestionId === question.id"
                      :type="question.questionType === 1 ? 'checkbox' : 'radio'"
                      :name="question.id"
                      @change="toggleOption(question.id, option.id, question.questionType)"
                  />
                  <span>{{ option.optionLabel }}. {{ option.optionContent }}</span>
                </label>
              </div>
              <textarea
                  v-else
                  v-model="textAnswers[question.id]"
                  :disabled="Boolean(question.mySubmission)"
                  placeholder="输入答案"
              ></textarea>
              <div class="answer-footer">
                <span>{{ question.mySubmission?.submitStatusText || '未提交' }}</span>
                <button
                    v-if="!isTeacher && !question.mySubmission"
                    :disabled="submittingQuestionId === question.id"
                    class="text-button"
                    type="button"
                    @click="submitAnswer(question)"
                >
                  提交
                </button>
              </div>
              <div v-if="isTeacher && question.analysis" class="analysis-line">
                <span>提交 {{ question.analysis.submittedCount }}</span>
                <span>正确 {{ question.analysis.correctCount }}</span>
                <span>未交 {{ question.analysis.notSubmittedCount }}</span>
              </div>
            </article>
          </div>
        </article>
      </section>
    </template>
  </aside>
</template>

<script lang="ts" setup>
import {computed, onMounted, reactive, ref, watch} from 'vue'
import {X} from 'lucide-vue-next'

import {getQuestions} from '@/features/question-bank/api/questionBank'
import type {Question} from '@/features/question-bank/types/questionBank'
import {
  createLivePractice,
  getLivePractice,
  listClassSessionLivePractices,
  submitLivePracticeAnswer,
} from '@/features/live-practice/api/livePractice'
import type {
  CreateLivePracticeQuestionRequest,
  LivePracticeGroup,
  LivePracticeQuestion,
} from '@/features/live-practice/types/livePractice'
import type {ClassSession} from '@/features/course/types/classSession'
import {notify} from '@/shared/composables/useGlobalNotification'

const props = defineProps<{
  session: ClassSession
  isTeacher: boolean
  initialGroupId?: string | null
}>()

defineEmits<{ close: [] }>()

const groups = ref<LivePracticeGroup[]>([])
const activeGroup = ref<LivePracticeGroup | null>(null)
const questions = ref<Question[]>([])
const selectedQuestionIds = ref<string[]>([])
const createdQuestions = ref<CreateLivePracticeQuestionRequest[]>([])
const selectedAnswers = reactive<Record<string, string[]>>({})
const textAnswers = reactive<Record<string, string>>({})
const loading = ref(false)
const loadFailed = ref(false)
const publishing = ref(false)
const submittingQuestionId = ref<string | null>(null)
const questionKeyword = ref('')
const allowLateSubmission = ref(true)

const form = reactive({
  title: '随堂练习',
  availableStartAt: toDatetimeLocalValue(new Date().toISOString()),
  availableEndAt: toDatetimeLocalValue(new Date(Date.now() + 15 * 60 * 1000).toISOString()),
})

const quickQuestion = reactive({
  questionTitle: '',
  questionType: 0,
  options: [
    {optionLabel: 'A', optionContent: '', isCorrect: false},
    {optionLabel: 'B', optionContent: '', isCorrect: false},
    {optionLabel: 'C', optionContent: '', isCorrect: false},
    {optionLabel: 'D', optionContent: '', isCorrect: false},
  ],
})

const activeGroupId = computed(() => props.initialGroupId || activeGroup.value?.id || groups.value[0]?.id || null)

onMounted(async () => {
  await Promise.all([loadGroups(), props.isTeacher ? loadQuestions() : Promise.resolve()])
})

watch(activeGroupId, (groupId) => {
  if (groupId) void selectGroup(groupId)
})

async function loadGroups() {
  loading.value = true
  loadFailed.value = false
  try {
    groups.value = await listClassSessionLivePractices(props.session.id)
    const targetGroupId = props.initialGroupId || groups.value[0]?.id
    if (targetGroupId) await selectGroup(targetGroupId)
  } catch {
    loadFailed.value = true
  } finally {
    loading.value = false
  }
}

async function loadQuestions() {
  const response = await getQuestions({
    courseId: props.session.courseId,
    keyword: questionKeyword.value,
    status: 1,
    page: 1,
    size: 20,
  })
  questions.value = response.records || []
}

async function selectGroup(groupId: string) {
  activeGroup.value = await getLivePractice(groupId)
}

function addQuickQuestion() {
  if (!quickQuestion.questionTitle.trim()) return
  const objective = quickQuestion.questionType <= 2
  createdQuestions.value.push({
    questionTitle: quickQuestion.questionTitle.trim(),
    questionType: quickQuestion.questionType,
    difficulty: 2,
    score: 1,
    allowPartialCredit: 0,
    aiGradingEnabled: 0,
    options: objective
        ? quickQuestion.options
            .filter(option => option.optionContent.trim())
            .map(option => ({
              optionLabel: option.optionLabel,
              optionContent: option.optionContent.trim(),
              isCorrect: option.isCorrect ? 1 : 0,
            }))
        : undefined,
  })
  quickQuestion.questionTitle = ''
  quickQuestion.options.forEach(option => {
    option.optionContent = ''
    option.isCorrect = false
  })
}

async function publish() {
  const selected = selectedQuestionIds.value
  if (selected.length === 0 && createdQuestions.value.length === 0) {
    notify.warn('请选择或创建至少一道题')
    return
  }
  publishing.value = true
  try {
    const groupId = await createLivePractice(props.session.id, {
      title: form.title || '随堂练习',
      availableStartAt: toIso(form.availableStartAt),
      availableEndAt: toIso(form.availableEndAt),
      allowLateSubmission: allowLateSubmission.value ? 1 : 0,
      selectedQuestionIds: selected,
      createdQuestions: createdQuestions.value,
    })
    notify.success('随堂练习已发布')
    selectedQuestionIds.value = []
    createdQuestions.value = []
    await loadGroups()
    await selectGroup(groupId)
  } catch {
    notify.error('发布随堂练习失败')
  } finally {
    publishing.value = false
  }
}

function answerSelected(questionId: string, optionId: string) {
  return selectedAnswers[questionId]?.includes(optionId) || false
}

function toggleOption(questionId: string, optionId: string, questionType: number) {
  if (questionType === 1) {
    const current = selectedAnswers[questionId] || []
    selectedAnswers[questionId] = current.includes(optionId)
        ? current.filter(id => id !== optionId)
        : [...current, optionId]
    return
  }
  selectedAnswers[questionId] = [optionId]
}

async function submitAnswer(question: LivePracticeQuestion) {
  if (!activeGroup.value) return
  submittingQuestionId.value = question.id
  try {
    await submitLivePracticeAnswer(activeGroup.value.id, question.id, {
      selectedOptionIds: selectedAnswers[question.id],
      textAnswer: textAnswers[question.id],
    })
    notify.success('答案已提交')
    await selectGroup(activeGroup.value.id)
  } catch {
    notify.error('提交失败')
  } finally {
    submittingQuestionId.value = null
  }
}

function toDatetimeLocalValue(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  const localDate = new Date(date.getTime() - date.getTimezoneOffset() * 60000)
  return localDate.toISOString().slice(0, 16)
}

function toIso(value: string) {
  return new Date(value).toISOString()
}
</script>

<style scoped>
.live-practice-panel {
  position: fixed;
  top: 24px;
  right: 24px;
  bottom: 24px;
  z-index: 2200;
  display: flex;
  width: min(480px, calc(100vw - 32px));
  flex-direction: column;
  overflow: hidden;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline);
  color: var(--color-on-surface);
  box-shadow: 0 24px 64px rgba(15, 23, 42, 0.24);
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px;
  border-bottom: 1px solid var(--color-outline-light);
}

.panel-header span,
.limit-line {
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 12px;
}

.panel-header h2 {
  margin: 4px 0 0;
  font-family: var(--font-heading);
  font-size: 28px;
  font-weight: 400;
}

.icon-button,
.text-button,
.primary-button {
  cursor: pointer;
}

.icon-button {
  display: grid;
  width: 36px;
  height: 36px;
  place-items: center;
  background: transparent;
  border: 1px solid var(--color-outline-light);
  color: var(--color-on-surface);
}

.publish-form,
.group-list,
.question-stack {
  display: grid;
  gap: 12px;
}

.publish-form,
.group-list {
  padding: 16px;
  overflow-y: auto;
}

label {
  display: grid;
  gap: 6px;
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 13px;
}

input,
select,
textarea {
  min-height: 40px;
  padding: 8px 10px;
  background: var(--color-surface-canvas);
  border: 1px solid var(--color-outline-light);
  color: var(--color-on-surface);
  font: inherit;
}

textarea {
  min-height: 96px;
  resize: vertical;
}

.time-grid,
.option-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.check-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.check-row input {
  min-height: auto;
}

.disabled {
  opacity: 0.55;
}

.question-picker,
.quick-question,
.practice-group,
.answer-card {
  display: grid;
  gap: 10px;
  padding: 12px;
  background: var(--color-surface-container);
  border: 1px solid var(--color-outline-light);
}

.question-picker h3,
.quick-question h3,
.answer-card h3 {
  margin: 0;
  font-family: var(--font-body);
  font-size: 15px;
}

.question-list,
.created-list,
.answer-options {
  display: grid;
  gap: 8px;
}

.question-row,
.answer-options label {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.question-row input,
.answer-options input {
  margin-top: 3px;
}

.created-list span {
  padding: 6px 8px;
  background: var(--color-surface-canvas);
  border: 1px solid var(--color-outline-light);
  font-size: 13px;
}

.primary-button {
  min-height: 42px;
  background: var(--color-primary);
  border: 1px solid var(--color-primary);
  color: var(--color-on-primary);
}

.primary-button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.text-button {
  min-height: 32px;
  padding: 0 10px;
  background: transparent;
  border: 1px solid var(--color-outline-light);
  color: var(--color-on-surface);
}

.group-title {
  display: grid;
  gap: 4px;
  padding: 0;
  background: transparent;
  border: 0;
  color: var(--color-on-surface);
  cursor: pointer;
  text-align: left;
}

.group-title span {
  color: var(--color-muted);
  font-size: 12px;
}

.answer-card p,
.panel-state {
  margin: 0;
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 14px;
  line-height: 1.5;
}

.answer-footer,
.analysis-line {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  color: var(--color-muted);
  font-size: 13px;
}

.analysis-line {
  justify-content: flex-start;
}

@media (max-width: 640px) {
  .live-practice-panel {
    inset: 12px;
    width: auto;
  }

  .time-grid,
  .option-grid {
    grid-template-columns: 1fr;
  }
}
</style>
