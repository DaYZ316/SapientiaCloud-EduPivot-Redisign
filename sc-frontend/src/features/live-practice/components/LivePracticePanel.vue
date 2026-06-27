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

    <div v-if="loading" aria-label="Loading live practice" aria-live="polite" class="panel-loading-layout">
      <div v-if="isTeacher" aria-hidden="true" class="practice-loading-teacher">
        <div class="practice-loading-tabs">
          <span class="practice-skeleton skeleton-tab"></span>
          <span class="practice-skeleton skeleton-tab muted"></span>
        </div>
        <section class="practice-loading-section">
          <span class="practice-skeleton skeleton-kicker"></span>
          <span class="practice-skeleton skeleton-input"></span>
          <div class="practice-loading-grid">
            <span class="practice-skeleton skeleton-input"></span>
            <span class="practice-skeleton skeleton-input"></span>
          </div>
        </section>
        <section class="practice-loading-section">
          <div class="practice-loading-heading">
            <span class="practice-skeleton skeleton-title"></span>
            <span class="practice-skeleton skeleton-pill"></span>
          </div>
          <span class="practice-skeleton skeleton-search"></span>
          <div class="practice-loading-list">
            <span v-for="item in 4" :key="item" class="practice-skeleton skeleton-row"></span>
          </div>
        </section>
        <span class="practice-skeleton skeleton-action"></span>
      </div>
      <div v-else aria-hidden="true" class="practice-loading-student">
        <article v-for="item in 3" :key="item" class="practice-loading-card">
          <span class="practice-skeleton skeleton-kicker"></span>
          <span class="practice-skeleton skeleton-title wide"></span>
          <div class="practice-loading-list">
            <span class="practice-skeleton skeleton-option"></span>
            <span class="practice-skeleton skeleton-option short"></span>
          </div>
        </article>
      </div>
    </div>
    <div v-else-if="loadFailed" class="panel-state">
      <p>随堂练习加载失败</p>
      <button class="text-button" type="button" @click="retryLoadGroups">重试</button>
    </div>

    <template v-else>
      <nav v-if="isTeacher" aria-label="随堂练习面板" class="panel-tabs">
        <button
            :class="{active: activeTeacherPanel === 'publish'}"
            type="button"
            @click="activeTeacherPanel = 'publish'"
        >
          发布操作
        </button>
        <button
            :class="{active: activeTeacherPanel === 'published'}"
            type="button"
            @click="activeTeacherPanel = 'published'"
        >
          已发布
        </button>
      </nav>

      <section v-if="isTeacher && activeTeacherPanel === 'publish'" class="publish-form">
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

        <section class="ai-grading-box">
          <label :class="{disabled: !hasShortAnswerForPublish}" class="check-row">
            <input v-model="aiGradingEnabled" :disabled="!hasShortAnswerForPublish" type="checkbox"/>
            <span>AI 判题</span>
          </label>
          <label v-if="aiGradingEnabled">
            <span>判题要求</span>
            <textarea
                v-model="aiGradingRequirement"
                maxlength="2000"
                placeholder="说明评分标准、给分点、可接受的同义表达等"
            ></textarea>
          </label>
          <p v-if="!hasShortAnswerForPublish" class="ai-grading-hint">添加或选择简答题后可开启 AI 判题。</p>
        </section>

        <section class="question-picker">
          <div class="question-picker-header">
            <h3>从课程题库选择</h3>
            <span>已选择 {{ selectedQuestionIds.length }} 道</span>
          </div>
          <div class="question-search-row">
            <input v-model="questionKeyword" placeholder="搜索题目" type="search"
                   @keydown.enter.prevent="searchQuestions"/>
            <button class="text-button" type="button" @click="searchQuestions">搜索</button>
          </div>
          <div v-if="questionsLoading" class="question-state">题目加载中...</div>
          <div v-else-if="questionsLoadFailed" class="question-state">
            <p>题目加载失败</p>
            <button class="text-button" type="button" @click="loadQuestions">重试</button>
          </div>
          <div v-else-if="questions.length > 0" class="question-list">
            <div v-for="question in questions" :key="question.id" class="question-row">
              <label class="question-check">
                <input v-model="selectedQuestionIds" :value="question.id" type="checkbox"/>
                <span class="question-row-main">
                  <span>{{ question.questionTitle }}</span>
                  <span class="question-row-meta">
                    {{ questionTypeName(question.questionType) }} · {{
                      difficultyName(question.difficulty)
                    }} · {{ question.score }} 分
                  </span>
                </span>
              </label>
              <button class="preview-button" type="button" @click="openQuestionPreview(question)">
                <Eye :size="14" stroke-width="1.8"/>
                预览
              </button>
            </div>
          </div>
          <div v-else class="question-state">暂无可选题目</div>
          <div v-if="questionTotal > 0" class="question-pagination">
            <span>{{ questionRangeText }}</span>
            <div>
              <button
                  :disabled="questionPage <= 1 || questionsLoading"
                  class="text-button compact"
                  type="button"
                  @click="loadQuestionPage(questionPage - 1)"
              >
                <ChevronLeft :size="14" stroke-width="1.8"/>
                上一页
              </button>
              <strong>{{ questionPage }} / {{ questionTotalPages }}</strong>
              <button
                  :disabled="questionPage >= questionTotalPages || questionsLoading"
                  class="text-button compact"
                  type="button"
                  @click="loadQuestionPage(questionPage + 1)"
              >
                下一页
                <ChevronRight :size="14" stroke-width="1.8"/>
              </button>
            </div>
          </div>
        </section>

        <section class="quick-question">
          <div class="quick-question-header">
            <h3>快速出题</h3>
            <span>{{ createdQuestions.length }} 道已加入</span>
          </div>
          <div class="quick-question-grid">
            <label class="quick-title-field">
              <span>题干</span>
              <input v-model="quickQuestion.questionTitle" placeholder="输入题目内容" type="text"/>
            </label>
            <label>
              <span>题型</span>
              <BaseSelect
                  v-model="quickQuestion.questionType"
                  :options="questionTypeOptions"
                  min-width="100%"
              />
            </label>
          </div>
          <label v-if="quickQuestion.questionType === 4" class="quick-answer-field">
            <span>参考答案 / 评分要点</span>
            <textarea v-model="quickQuestion.answerContent" placeholder="输入参考答案或关键给分点"></textarea>
          </label>
          <div v-if="quickQuestion.questionType <= 2" class="option-grid quick-option-grid">
            <label v-for="option in quickQuestion.options" :key="option.optionLabel">
              <span>{{ option.optionLabel }}</span>
              <input v-model="option.optionContent" placeholder="选项内容" type="text"/>
              <input v-model="option.isCorrect" type="checkbox"/>
            </label>
          </div>
          <div class="quick-question-actions">
            <button class="text-button" type="button" @click="addQuickQuestion">加入本次发布</button>
          </div>
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

      <section v-if="!isTeacher || activeTeacherPanel === 'published'" class="group-list">
        <div v-if="groups.length === 0" class="panel-empty">
          {{ isTeacher ? '暂无已发布练习' : '暂无随堂练习' }}
        </div>
        <article v-for="group in groups" :key="group.id" class="practice-group">
          <button class="group-title" type="button" @click="toggleGroup(group.id)">
            <span>
              <span>第 {{ group.publishOrder }} 组</span>
              <ChevronRight
                  :class="{expanded: activeGroup?.id === group.id}"
                  :size="15"
                  stroke-width="1.8"
              />
            </span>
            <strong>{{ group.title }}</strong>
          </button>
          <div v-if="activeGroup?.id === group.id" class="question-stack">
            <article v-for="question in activeGroup.questions || []" :key="question.id" class="answer-card">
              <h3>{{ question.questionOrder }}. {{ question.questionTitle }}</h3>
              <p v-if="question.questionContent">{{ question.questionContent }}</p>
              <div v-if="isTeacher && question.options?.length" class="answer-options readonly">
                <div
                    v-for="option in question.options"
                    :key="option.id"
                    :class="{correct: option.isCorrect === 1}"
                >
                  <span>{{ option.optionLabel }}. {{ option.optionContent }}</span>
                </div>
              </div>
              <div v-else-if="question.options?.length" class="answer-options">
                <label v-for="option in question.options" :key="option.id">
                  <input
                      :checked="answerSelected(question.id, option.id)"
                      :disabled="Boolean(question.mySubmission) || submittingQuestionId === question.id"
                      :name="question.id"
                      :type="question.questionType === 1 ? 'checkbox' : 'radio'"
                      @change="toggleOption(question.id, option.id, question.questionType)"
                  />
                  <span>{{ option.optionLabel }}. {{ option.optionContent }}</span>
                </label>
              </div>
              <textarea
                  v-else-if="!isTeacher"
                  v-model="textAnswers[question.id]"
                  :disabled="Boolean(question.mySubmission)"
                  placeholder="输入答案"
              ></textarea>
              <div v-if="!isTeacher" class="answer-footer">
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
              <div v-if="question.mySubmission && question.aiGradingEnabled === 1" class="ai-status-block">
                <span :class="aiStatusClass(question.mySubmission.aiGradingStatus)">
                  {{ aiStatusText(question.mySubmission.aiGradingStatus) }}
                </span>
                <strong v-if="question.mySubmission.aiGradingStatus === 'COMPLETED'">
                  {{ question.mySubmission.earnedScore }} / {{ question.score }} 分
                </strong>
                <p v-if="question.mySubmission.aiGradingFeedback">{{ question.mySubmission.aiGradingFeedback }}</p>
                <p v-else-if="question.mySubmission.aiGradingError">{{ question.mySubmission.aiGradingError }}</p>
              </div>
              <div v-if="isTeacher && teacherAnswerItems(question).length" class="teacher-answer-list">
                <span>正确答案</span>
                <strong v-for="answer in teacherAnswerItems(question)" :key="answer">{{ answer }}</strong>
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

  <div v-if="previewQuestion" class="preview-overlay" @click.self="closeQuestionPreview">
    <article aria-modal="true" class="preview-dialog question-preview" role="dialog">
      <template v-if="previewQuestion">
        <div class="preview-header">
          <div>
            <span class="section-label">题目预览</span>
            <h2>{{ previewQuestion.questionTitle }}</h2>
          </div>
          <div class="preview-header-actions">
            <label class="answer-switch">
              <input v-model="showPreviewAnswer" type="checkbox"/>
              <span>显示答案</span>
            </label>
            <button aria-label="关闭预览" class="preview-close" type="button" @click="closeQuestionPreview">
              <X :size="18" stroke-width="1.8"/>
            </button>
          </div>
        </div>

        <div class="preview-dialog-body">
          <div class="preview-meta">
            <span>{{ questionTypeName(previewQuestion.questionType) }}</span>
            <span>{{ difficultyName(previewQuestion.difficulty) }}</span>
            <span>{{ previewQuestion.score }} 分</span>
            <span v-if="previewQuestion.estimatedTime">{{ previewQuestion.estimatedTime }} 分钟</span>
            <span>浏览 {{ previewQuestion.viewCount }} 次</span>
          </div>

          <section class="preview-section">
            <h3>题干</h3>
            <RichMathContent
                :content="previewQuestion.questionContent || previewQuestion.questionTitle"
                class="question-content rich-content"
            />
          </section>

          <section v-if="previewQuestionHasOptions" class="preview-section">
            <h3>选项</h3>
            <p v-if="previewLoading" class="muted-text">正在加载题目详情...</p>
            <div v-else-if="previewQuestion.options?.length" class="option-list">
              <div
                  v-for="option in previewQuestion.options"
                  :key="option.id"
                  :class="{correct: showPreviewAnswer && option.isCorrect === 1}"
                  class="option-row"
              >
                <span class="option-label">{{ option.optionLabel }}</span>
                <RichMathContent
                    :content="option.optionContent"
                    class="option-content rich-content"
                />
              </div>
            </div>
            <p v-else class="muted-text">暂无选项</p>
          </section>

          <section v-if="showPreviewAnswer" class="preview-section">
            <h3>正确答案</h3>
            <p v-if="previewLoading" class="muted-text">正在加载题目详情...</p>
            <div v-else-if="previewAnswerItems.length" class="answer-list">
              <RichMathContent
                  v-for="answer in previewAnswerItems"
                  :key="answer"
                  :content="answer"
                  class="answer-row rich-content"
              />
            </div>
            <p v-else class="muted-text">暂无答案</p>
          </section>

          <section v-if="showPreviewAnswer" class="preview-section">
            <h3>解析</h3>
            <RichMathContent
                :content="previewExplanationText"
                class="explanation-content rich-content"
            />
          </section>

          <section v-if="previewQuestion.tags?.length" class="preview-section">
            <h3>标签</h3>
            <div class="tag-list">
              <span v-for="tag in previewQuestion.tags" :key="tag">{{ tag }}</span>
            </div>
          </section>
        </div>
      </template>
    </article>
  </div>
</template>

<script lang="ts" setup>
import {computed, onBeforeUnmount, onMounted, reactive, ref} from 'vue'
import {ChevronLeft, ChevronRight, Eye, X} from 'lucide-vue-next'

import {getQuestion, getQuestions} from '@/features/question-bank/api/questionBank'
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
import BaseSelect from '@/shared/components/BaseSelect.vue'
import RichMathContent from '@/shared/components/RichMathContent.vue'
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
const questionCache = reactive<Record<string, Question>>({})
const selectedQuestionIds = ref<string[]>([])
const createdQuestions = ref<CreateLivePracticeQuestionRequest[]>([])
const selectedAnswers = reactive<Record<string, string[]>>({})
const textAnswers = reactive<Record<string, string>>({})
const loading = ref(false)
const loadFailed = ref(false)
const publishing = ref(false)
const questionsLoading = ref(false)
const questionsLoadFailed = ref(false)
const submittingQuestionId = ref<string | null>(null)
const questionKeyword = ref('')
const questionPage = ref(1)
const questionPageSize = 8
const questionTotal = ref(0)
const previewQuestion = ref<Question | null>(null)
const previewLoading = ref(false)
const showPreviewAnswer = ref(false)
const activeTeacherPanel = ref<'publish' | 'published'>('publish')
const allowLateSubmission = ref(true)
const aiGradingEnabled = ref(false)
const aiGradingRequirement = ref('')
let aiRefreshTimer: number | null = null
let aiRefreshCount = 0
const questionTypeOptions = [
  {label: '单选题', value: 0},
  {label: '多选题', value: 1},
  {label: '判断题', value: 2},
  {label: '填空题', value: 3},
  {label: '简答题', value: 4},
]

const form = reactive({
  title: '随堂练习',
  availableStartAt: toDatetimeLocalValue(new Date().toISOString()),
  availableEndAt: toDatetimeLocalValue(new Date(Date.now() + 15 * 60 * 1000).toISOString()),
})

const quickQuestion = reactive({
  questionTitle: '',
  questionType: 0,
  answerContent: '',
  options: [
    {optionLabel: 'A', optionContent: '', isCorrect: false},
    {optionLabel: 'B', optionContent: '', isCorrect: false},
    {optionLabel: 'C', optionContent: '', isCorrect: false},
    {optionLabel: 'D', optionContent: '', isCorrect: false},
  ],
})

const questionTotalPages = computed(() => Math.max(1, Math.ceil(questionTotal.value / questionPageSize)))
const questionRangeText = computed(() => {
  if (questionTotal.value === 0) return '0 道题'
  const start = (questionPage.value - 1) * questionPageSize + 1
  const end = Math.min(questionPage.value * questionPageSize, questionTotal.value)
  return `${start}-${end} / ${questionTotal.value} 道题`
})
const previewQuestionHasOptions = computed(() => {
  const question = previewQuestion.value
  return question ? [0, 1, 2].includes(question.questionType) : false
})
const previewAnswerItems = computed(() => {
  const question = previewQuestion.value
  if (!question) return []
  const textAnswers = (question.answers || [])
      .map((answer) => answer.answerContent.trim())
      .filter((answer) => answer.length > 0)
  const optionAnswers = (question.options || [])
      .filter((option) => option.isCorrect === 1)
      .map((option) => option.optionLabel ? option.optionLabel + '. ' + option.optionContent : option.optionContent)
  return textAnswers.length ? textAnswers : optionAnswers
})
const previewExplanationText = computed(() => {
  const question = previewQuestion.value
  if (!question) return ''
  const optionExplanation = question.options?.find((option) => option.isCorrect === 1 && option.explanation)?.explanation
  const answerExplanation = question.answers?.find((answer) => answer.explanation)?.explanation
  return optionExplanation || answerExplanation || '暂无解析'
})

const selectedQuestionsForPublish = computed(() => {
  return selectedQuestionIds.value
      .map(id => questionCache[id])
      .filter((question): question is Question => Boolean(question))
})
const hasShortAnswerForPublish = computed(() => {
  return createdQuestions.value.some(question => question.questionType === 4)
      || selectedQuestionsForPublish.value.some(question => question.questionType === 4)
})

onMounted(async () => {
  await Promise.all([loadGroups(), props.isTeacher ? loadQuestions() : Promise.resolve()])
})

onBeforeUnmount(() => {
  stopAiRefresh()
})

async function loadGroups(preferredGroupId?: string) {
  loading.value = true
  loadFailed.value = false
  try {
    groups.value = await listClassSessionLivePractices(props.session.id)
    const targetGroupId = preferredGroupId || props.initialGroupId || groups.value[0]?.id
    if (targetGroupId) await selectGroup(targetGroupId)
    else maybeStartAiRefresh()
  } catch {
    loadFailed.value = true
  } finally {
    loading.value = false
  }
}

async function retryLoadGroups() {
  await loadGroups()
}

async function loadQuestions() {
  questionsLoading.value = true
  questionsLoadFailed.value = false
  const targetPage = questionPage.value
  try {
    const response = await getQuestions({
      courseId: props.session.courseId,
      keyword: questionKeyword.value,
      status: 1,
      page: targetPage,
      size: questionPageSize,
    })
    questions.value = response.records || []
    for (const question of questions.value) {
      questionCache[question.id] = question
    }
    questionTotal.value = response.total || 0
    questionPage.value = response.page || targetPage
  } catch {
    questionsLoadFailed.value = true
  } finally {
    questionsLoading.value = false
  }
}

async function searchQuestions() {
  questionPage.value = 1
  await loadQuestions()
}

async function loadQuestionPage(page: number) {
  if (page < 1 || page > questionTotalPages.value) return
  questionPage.value = page
  await loadQuestions()
}

async function openQuestionPreview(question: Question) {
  previewQuestion.value = question
  previewLoading.value = false
  showPreviewAnswer.value = false
  if (question.options != null && question.answers != null) return
  previewLoading.value = true
  try {
    const detail = await getQuestion(question.id)
    if (previewQuestion.value?.id === question.id) {
      previewQuestion.value = {...question, ...detail}
    }
    questionCache[question.id] = {...question, ...detail}
    const index = questions.value.findIndex((item) => item.id === question.id)
    if (index !== -1) {
      questions.value.splice(index, 1, {...questions.value[index], ...detail})
    }
  } catch {
    notify.error('题目详情加载失败')
  } finally {
    previewLoading.value = false
  }
}

function closeQuestionPreview() {
  previewQuestion.value = null
  previewLoading.value = false
}

function difficultyName(difficulty: number) {
  const map: Record<number, string> = {
    1: '简单',
    2: '中等',
    3: '困难',
  }
  return map[difficulty] || '未知'
}

function questionTypeName(type: number) {
  const map: Record<number, string> = {
    0: '单选题',
    1: '多选题',
    2: '判断题',
    3: '填空题',
    4: '简答题',
  }
  return map[type] || '未知'
}

function teacherAnswerItems(question: LivePracticeQuestion) {
  if (![3, 4].includes(question.questionType)) return []
  const textAnswers = (question.answers || [])
      .map((answer) => answer.answerContent.trim())
      .filter((answer) => answer.length > 0)
  return textAnswers
}

async function selectGroup(groupId: string) {
  activeGroup.value = await getLivePractice(groupId)
  syncSubmittedAnswers(activeGroup.value)
  maybeStartAiRefresh()
}

function syncSubmittedAnswers(group: LivePracticeGroup | null) {
  for (const question of group?.questions || []) {
    const submission = question.mySubmission
    if (!submission) continue
    if (submission.selectedOptionIds) {
      selectedAnswers[question.id] = [...submission.selectedOptionIds]
    }
    if (submission.textAnswer != null) {
      textAnswers[question.id] = submission.textAnswer
    }
  }
}

async function toggleGroup(groupId: string) {
  if (activeGroup.value?.id === groupId) {
    activeGroup.value = null
    return
  }
  await selectGroup(groupId)
}

function addQuickQuestion() {
  if (!quickQuestion.questionTitle.trim()) return
  const objective = quickQuestion.questionType <= 2
  const answerContent = quickQuestion.answerContent.trim()
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
    answers: !objective && answerContent
        ? [{answerContent, sortOrder: 1}]
        : undefined,
  })
  quickQuestion.questionTitle = ''
  quickQuestion.answerContent = ''
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
  if (aiGradingEnabled.value && !hasShortAnswerForPublish.value) {
    notify.warn('AI 判题需要至少一道简答题')
    return
  }
  if (aiGradingEnabled.value && !aiGradingRequirement.value.trim()) {
    notify.warn('请输入 AI 判题要求')
    return
  }
  publishing.value = true
  try {
    const groupId = await createLivePractice(props.session.id, {
      title: form.title || '随堂练习',
      availableStartAt: toIso(form.availableStartAt),
      availableEndAt: toIso(form.availableEndAt),
      allowLateSubmission: allowLateSubmission.value ? 1 : 0,
      aiGradingEnabled: aiGradingEnabled.value ? 1 : 0,
      aiGradingRequirement: aiGradingEnabled.value ? aiGradingRequirement.value.trim() : undefined,
      selectedQuestionIds: selected,
      createdQuestions: createdQuestions.value,
    })
    notify.success('随堂练习已发布')
    selectedQuestionIds.value = []
    createdQuestions.value = []
    aiGradingEnabled.value = false
    aiGradingRequirement.value = ''
    activeTeacherPanel.value = 'published'
    await loadGroups(groupId)
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

function aiStatusText(status?: string | null) {
  if (status === 'PENDING') return 'AI 批改中'
  if (status === 'COMPLETED') return 'AI 已批改'
  if (status === 'FAILED') return 'AI 批改失败'
  return '无需 AI 判题'
}

function aiStatusClass(status?: string | null) {
  if (status === 'COMPLETED') return 'completed'
  if (status === 'FAILED') return 'failed'
  if (status === 'PENDING') return 'pending'
  return 'idle'
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
    maybeStartAiRefresh()
  } catch {
    notify.error('提交失败')
  } finally {
    submittingQuestionId.value = null
  }
}

function maybeStartAiRefresh() {
  if (!hasPendingAiGrading()) {
    stopAiRefresh()
    return
  }
  if (aiRefreshTimer || !activeGroup.value) return
  aiRefreshCount = 0
  aiRefreshTimer = window.setInterval(() => {
    aiRefreshCount += 1
    if (aiRefreshCount > 10 || !hasPendingAiGrading() || !activeGroup.value) {
      stopAiRefresh()
      return
    }
    void selectGroup(activeGroup.value.id).catch(() => stopAiRefresh())
  }, 3000)
}

function stopAiRefresh() {
  if (!aiRefreshTimer) return
  window.clearInterval(aiRefreshTimer)
  aiRefreshTimer = null
}

function hasPendingAiGrading() {
  return Boolean(activeGroup.value?.questions?.some(question => question.mySubmission?.aiGradingStatus === 'PENDING'))
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
  border-radius: var(--radius-md);
  color: var(--color-on-surface);
  box-shadow: var(--shadow-card);
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

.panel-tabs {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  padding: 12px 16px 0;
}

.panel-tabs button {
  min-height: 36px;
  background: var(--color-surface-container);
  border: 1px solid var(--color-outline-light);
  color: var(--color-muted);
  cursor: pointer;
  font-family: var(--font-label);
  font-size: 13px;
}

.panel-tabs button.active {
  border-color: var(--color-primary);
  color: var(--color-on-surface);
}

.panel-loading-layout {
  display: flex;
  min-height: 0;
  flex: 1 1 auto;
  flex-direction: column;
  overflow: hidden;
  padding: 16px;
}

.practice-loading-teacher,
.practice-loading-student,
.practice-loading-section,
.practice-loading-card,
.practice-loading-list {
  display: grid;
  gap: 12px;
}

.practice-loading-tabs {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.practice-loading-section,
.practice-loading-card {
  padding: 12px;
  background: var(--color-surface-container);
  border: 1px solid var(--color-outline-light);
}

.practice-loading-heading,
.practice-loading-grid {
  display: grid;
  gap: 10px;
}

.practice-loading-heading {
  grid-template-columns: minmax(0, 1fr) 72px;
  align-items: center;
}

.practice-loading-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.practice-skeleton {
  position: relative;
  overflow: hidden;
  display: block;
  background: color-mix(in srgb, var(--color-outline-light) 58%, transparent);
}

.practice-skeleton::after {
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, transparent, color-mix(in srgb, var(--color-surface-card) 70%, transparent), transparent);
  content: '';
  transform: translateX(-100%);
  animation: practiceSkeletonShimmer 1.35s ease-in-out infinite;
}

.skeleton-tab,
.skeleton-input,
.skeleton-search,
.skeleton-action {
  height: 38px;
}

.skeleton-tab.muted {
  opacity: 0.56;
}

.skeleton-kicker {
  width: 38%;
  height: 12px;
}

.skeleton-title {
  width: 68%;
  height: 18px;
}

.skeleton-title.wide {
  width: 86%;
}

.skeleton-pill {
  height: 24px;
}

.skeleton-row,
.skeleton-option {
  height: 48px;
}

.skeleton-option.short {
  width: 74%;
}

.skeleton-action {
  margin-top: 2px;
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

.panel-empty {
  padding: 18px 12px;
  background: var(--color-surface-container);
  border: 1px solid var(--color-outline-light);
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 14px;
  text-align: center;
}

label {
  display: grid;
  gap: 6px;
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 13px;
}

input,
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

.live-practice-panel input[type='checkbox'],
.live-practice-panel input[type='radio'] {
  width: 16px;
  height: 16px;
  min-height: 0;
  margin: 0;
  padding: 0;
  accent-color: var(--color-primary);
  cursor: pointer;
}

.live-practice-panel input[type='checkbox']:focus-visible,
.live-practice-panel input[type='radio']:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 2px;
}

.live-practice-panel input[type='checkbox']:disabled,
.live-practice-panel input[type='radio']:disabled {
  accent-color: var(--color-outline);
  cursor: not-allowed;
}

.disabled {
  opacity: 0.55;
}

.question-picker,
.ai-grading-box,
.quick-question,
.practice-group,
.answer-card {
  display: grid;
  gap: 10px;
  padding: 12px;
  background: var(--color-surface-container);
  border: 1px solid var(--color-outline-light);
}

.ai-grading-box {
  gap: 12px;
}

.quick-question {
  gap: 12px;
}

.quick-question-header,
.quick-question-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.quick-question-header span {
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 12px;
}

.quick-question-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(132px, 0.34fr);
  gap: 10px;
  align-items: end;
}

.quick-title-field,
.quick-answer-field {
  min-width: 0;
}

.quick-answer-field textarea {
  min-height: 82px;
}

.quick-option-grid {
  padding: 10px;
  background: var(--color-surface-canvas);
  border: 1px solid var(--color-outline-light);
}

.quick-option-grid label {
  grid-template-columns: 20px minmax(0, 1fr) 16px;
  align-items: center;
  gap: 8px;
}

.quick-option-grid label > span {
  color: var(--color-on-surface);
  font-family: var(--font-label);
  font-size: 12px;
  text-align: center;
}

.quick-option-grid input[type='text'] {
  min-width: 0;
}

.quick-question-actions {
  padding-top: 2px;
}

.ai-grading-hint,
.ai-status-block p {
  margin: 0;
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 13px;
  line-height: 1.5;
}

.ai-status-block {
  display: grid;
  gap: 6px;
  padding: 10px;
  background: var(--color-surface-canvas);
  border: 1px solid var(--color-outline-light);
}

.ai-status-block span {
  width: fit-content;
  padding: 4px 8px;
  border: 1px solid var(--color-outline-light);
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 12px;
}

.ai-status-block span.completed {
  border-color: #3b8f61;
  color: #3b8f61;
}

.ai-status-block span.failed {
  border-color: #b65f5f;
  color: #b65f5f;
}

.ai-status-block span.pending {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.ai-status-block strong {
  color: var(--color-on-surface);
  font-family: var(--font-body);
  font-size: 14px;
}

.question-picker-header,
.question-search-row,
.question-pagination,
.preview-header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.question-picker-header,
.question-pagination {
  justify-content: space-between;
}

.question-picker-header span,
.question-row-meta,
.question-pagination,
.question-state,
.muted-text {
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 12px;
}

.question-search-row input {
  min-width: 0;
  flex: 1;
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

.question-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
  align-items: center;
  min-height: 48px;
  padding: 8px;
  background: var(--color-surface-canvas);
  border: 1px solid var(--color-outline-light);
}

.question-check,
.answer-options label {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.answer-options.readonly div {
  display: block;
  padding: 8px 10px;
  background: var(--color-surface-canvas);
  border: 1px solid var(--color-outline-light);
  color: var(--color-on-surface-variant);
}

.answer-options.readonly div.correct,
.teacher-answer-list strong {
  border-color: #ffffff;
  box-shadow: inset 3px 0 0 #ffffff;
  color: var(--color-on-surface);
}

.teacher-answer-list {
  display: grid;
  gap: 6px;
}

.teacher-answer-list span {
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 12px;
}

.teacher-answer-list strong {
  padding: 8px 10px;
  border: 1px solid #ffffff;
  font-family: var(--font-body);
  font-size: 14px;
  font-weight: 500;
  line-height: 1.5;
}

.question-check {
  min-width: 0;
}

.question-row-main {
  display: grid;
  min-width: 0;
  gap: 4px;
  color: var(--color-on-surface);
}

.question-row-main > span:first-child {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.question-row-meta {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.question-row input,
.answer-options input {
  margin-top: 3px;
}

.preview-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  min-height: 32px;
  padding: 0 8px;
  background: transparent;
  border: 1px solid var(--color-outline-light);
  color: var(--color-on-surface);
  cursor: pointer;
  font-family: var(--font-label);
  font-size: 12px;
  white-space: nowrap;
}

.question-pagination > div {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.question-pagination strong {
  min-width: 44px;
  text-align: center;
  color: var(--color-on-surface);
  font-size: 12px;
}

.text-button.compact {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  min-width: 74px;
  padding: 0 8px;
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

.text-button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
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
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--color-muted);
  font-size: 12px;
}

.group-title svg {
  transition: transform 160ms ease;
}

.group-title svg.expanded {
  transform: rotate(90deg);
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

.preview-overlay {
  position: fixed;
  inset: 0;
  z-index: 2300;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(9, 12, 18, 0.48);
}

.preview-dialog {
  display: flex;
  flex-direction: column;
  width: min(760px, calc(100vw - 32px));
  max-height: min(760px, calc(100vh - 48px));
  overflow: hidden;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline);
  border-radius: var(--radius-md);
  color: var(--color-on-surface);
  box-shadow: var(--shadow-card);
}

.preview-dialog-body {
  min-height: 0;
  overflow-y: auto;
}

.preview-header {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  align-items: flex-start;
  padding: 20px;
  border-bottom: 1px solid var(--color-outline-light);
}

.section-label {
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 12px;
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

.answer-switch {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 32px;
  padding: 0 10px;
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 12px;
  white-space: nowrap;
  cursor: pointer;
}

.answer-switch input {
  min-height: 0;
  padding: 0;
  accent-color: var(--color-primary);
}

.preview-close {
  display: grid;
  width: 32px;
  height: 32px;
  place-items: center;
  background: transparent;
  border: 1px solid var(--color-outline-light);
  color: var(--color-on-surface);
  cursor: pointer;
}

.preview-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 14px 20px;
  border-bottom: 1px solid var(--color-outline-light);
}

.preview-meta span {
  display: inline-flex;
  align-items: center;
  min-height: 26px;
  padding: 0 8px;
  background: var(--color-surface-container);
  border: 1px solid var(--color-outline-light);
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 12px;
}

.preview-section {
  padding: 18px 20px;
  border-bottom: 1px solid var(--color-outline-light);
}

.preview-section:last-child {
  border-bottom: 0;
}

.preview-section h3 {
  margin: 0 0 10px;
  color: var(--color-on-surface);
  font-family: var(--font-body);
  font-size: 15px;
}

.preview-section p {
  margin: 0;
  color: var(--color-on-surface-variant);
  font-family: var(--font-body);
  font-size: 15px;
  line-height: 1.65;
}

.rich-content {
  max-width: none;
  margin: 0;
  color: var(--color-on-surface-variant);
  font-size: 15px;
  line-height: 1.65;
}

.rich-content :deep(p) {
  margin: 0;
}

.option-list,
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

.answer-row {
  padding: 10px 12px;
  background: var(--color-surface-container);
  border: 1px solid var(--color-outline-light);
  color: var(--color-on-surface);
  font-family: var(--font-body);
  font-size: 14px;
  line-height: 1.5;
}

.option-content.rich-content,
.answer-row.rich-content {
  font-size: 14px;
  line-height: 1.5;
}

.answer-row.rich-content {
  color: var(--color-on-surface);
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.tag-list span {
  min-height: 24px;
  padding: 3px 8px;
  background: var(--color-surface-container);
  border: 1px solid var(--color-outline-light);
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 12px;
}

@media (max-width: 640px) {
  .live-practice-panel {
    inset: 12px;
    width: auto;
  }

  .time-grid,
  .option-grid,
  .quick-question-grid,
  .practice-loading-grid {
    grid-template-columns: 1fr;
  }

  .question-pagination,
  .preview-header {
    align-items: stretch;
    flex-direction: column;
  }

  .question-pagination > div,
  .preview-header-actions {
    justify-content: space-between;
  }

  .preview-overlay {
    padding: 12px;
  }

  .preview-dialog {
    width: calc(100vw - 24px);
    max-height: calc(100vh - 24px);
  }

  .preview-header h2 {
    font-size: 23px;
  }
}

@keyframes practiceSkeletonShimmer {
  to {
    transform: translateX(100%);
  }
}
</style>
