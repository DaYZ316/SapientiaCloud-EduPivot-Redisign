<template>
  <section class="practice-detail-page">
    <button
        class="back-link"
        type="button"
        @click="backToList"
    >
      <ArrowLeft
          :size="16"
          stroke-width="1.8"
      />
      {{ t('courseDetail.livePractice.backToList') }}
    </button>

    <div
        v-if="loading"
        class="state-block"
    >
      {{ t('courseDetail.livePractice.loading') }}
    </div>
    <div
        v-else-if="loadFailed"
        class="state-block"
    >
      <CircleAlert
          :size="28"
          stroke-width="1.5"
      />
      <h3>{{ t('courseDetail.livePractice.detailLoadFailed') }}</h3>
      <button
          class="text-button"
          type="button"
          @click="loadData"
      >
        {{ t('courseDetail.livePractice.retry') }}
      </button>
    </div>

    <template v-else-if="group">
      <header class="detail-header">
        <div>
          <span>{{ t('courseDetail.livePractice.kicker') }}</span>
          <h2>{{ group.title }}</h2>
          <p>
            {{ sessionTitle }} · {{ t('courseDetail.livePractice.groupOrder', {order: group.publishOrder}) }} ·
            {{ t('courseDetail.livePractice.questionCount', {count: group.totalQuestions}) }} ·
            {{ t('courseDetail.livePractice.deadlineAt', {time: formatDateTime(group.availableEndAt)}) }}
          </p>
        </div>
        <div class="header-count">
          <strong>{{ group.submittedStudents }}/{{ group.totalStudents }}</strong>
          <span>{{ t('courseDetail.livePractice.submittedStudents') }}</span>
        </div>
      </header>

      <div class="summary-strip">
        <div class="summary-item">
          <ClipboardList
              :size="16"
              stroke-width="1.8"
          />
          <span>{{ t('courseDetail.livePractice.questionCount', {count: group.totalQuestions}) }}</span>
        </div>
        <div class="summary-item">
          <Users
              :size="16"
              stroke-width="1.8"
          />
          <span>{{ t('courseDetail.livePractice.studentCount', {count: group.totalStudents}) }}</span>
        </div>
        <div class="summary-item">
          <Clock
              :size="16"
              stroke-width="1.8"
          />
          <span>{{ sessionTimeRange }}</span>
        </div>
        <div class="summary-item">
          <Check
              :size="16"
              stroke-width="1.8"
          />
          <span>{{
              group.allowLateSubmission === 1 ? t('courseDetail.livePractice.allowLate') : t('courseDetail.livePractice.disallowLate')
            }}</span>
        </div>
      </div>

      <div
          :class="{'teacher-layout': canManageCourse}"
          class="detail-workspace"
      >
        <section class="question-ledger">
          <div class="ledger-header">
            <span>{{ t('courseDetail.livePractice.questionList') }}</span>
            <strong>{{ questions.length }}</strong>
          </div>

          <button
              v-for="question in questions"
              :key="question.id"
              :class="{selected: selectedQuestion?.id === question.id}"
              class="question-row"
              type="button"
              @click="selectedQuestionId = question.id"
          >
            <span class="question-order">{{ question.questionOrder }}</span>
            <span class="question-main">
              <span class="question-title">{{ question.questionTitle }}</span>
              <span class="question-tags">
                <span>{{ questionTypeLabel(question.questionType) }}</span>
                <span>{{ t('courseDetail.livePractice.scorePoints', {score: question.score}) }}</span>
                <span v-if="canManageCourse && question.analysis">
                  {{ t('courseDetail.livePractice.notSubmittedCount', {count: question.analysis.notSubmittedCount}) }}
                </span>
                <span
                    v-else
                    :class="submitStatusClass(question.mySubmission?.submitStatus)"
                    class="submit-badge"
                >
                  {{ submitStatusLabel(question.mySubmission?.submitStatus) }}
                </span>
              </span>
            </span>
          </button>

          <div
              v-if="questions.length === 0"
              class="empty-list"
          >
            {{ t('courseDetail.livePractice.emptyQuestions') }}
          </div>
        </section>

        <section
            v-if="canManageCourse"
            class="teacher-overview"
        >
          <div class="overview-header">
            <span>{{ t('courseDetail.livePractice.classOverview') }}</span>
            <strong>{{ t('courseDetail.livePractice.questionCount', {count: questionMetrics.length}) }}</strong>
          </div>

          <div class="overview-stats">
            <div>
              <span>{{ t('courseDetail.livePractice.lowAccuracyQuestions') }}</span>
              <strong>{{ lowAccuracyQuestionCount }}</strong>
            </div>
            <div>
              <span>{{ t('courseDetail.livePractice.followUpStudents') }}</span>
              <strong>{{ followUpStudentCount }}</strong>
            </div>
          </div>

          <div class="overview-chart-panel">
            <div class="chart-title-row">
              <span>{{ t('courseDetail.livePractice.submissionProgressChart') }}</span>
            </div>
            <DashboardChart
                :aria-label="t('courseDetail.livePractice.submissionProgressAria')"
                :empty-text="t('courseDetail.livePractice.analysisChartEmpty')"
                :has-data="submissionBreakdownHasData"
                :option="submissionBreakdownOption"
                height="220px"
            />
          </div>

          <div class="overview-chart-panel">
            <div class="chart-title-row">
              <span>{{ t('courseDetail.livePractice.questionCorrectRateChart') }}</span>
            </div>
            <DashboardChart
                :aria-label="t('courseDetail.livePractice.questionCorrectRateAria')"
                :empty-text="t('courseDetail.livePractice.analysisChartEmpty')"
                :has-data="questionMetrics.length > 0"
                :option="questionCorrectRateOption"
                height="220px"
            />
          </div>

          <div class="overview-chart-panel">
            <div class="chart-title-row">
              <span>{{ t('courseDetail.livePractice.averageScoreRateChart') }}</span>
            </div>
            <DashboardChart
                :aria-label="t('courseDetail.livePractice.averageScoreRateAria')"
                :empty-text="t('courseDetail.livePractice.analysisChartEmpty')"
                :has-data="questionMetrics.length > 0"
                :option="questionAverageScoreOption"
                height="220px"
            />
          </div>
        </section>

        <aside class="question-preview">
          <template v-if="selectedQuestion">
            <div class="preview-header">
              <div>
                <span class="section-label">{{ t('courseDetail.livePractice.questionPreview') }}</span>
                <h3>{{ selectedQuestion.questionTitle }}</h3>
              </div>
              <span
                  :class="difficultyClass(selectedQuestion.difficulty)"
                  class="difficulty-badge"
              >
                {{ difficultyLabel(selectedQuestion.difficulty) }}
              </span>
            </div>

            <div class="preview-meta">
              <span>
                <FileText
                    :size="14"
                    stroke-width="1.8"
                />
                {{ questionTypeLabel(selectedQuestion.questionType) }}
              </span>
              <span>
                <Hash
                    :size="14"
                    stroke-width="1.8"
                />
                {{ t('courseDetail.livePractice.scorePoints', {score: selectedQuestion.score}) }}
              </span>
              <span v-if="selectedQuestion.estimatedTime">
                <Clock
                    :size="14"
                    stroke-width="1.8"
                />
                {{ t('courseDetail.livePractice.estimatedMinutes', {minutes: selectedQuestion.estimatedTime}) }}
              </span>
            </div>

            <section class="preview-section">
              <h4>{{ t('courseDetail.livePractice.stem') }}</h4>
              <RichMathContent
                  :content="selectedQuestion.questionContent || selectedQuestion.questionTitle"
                  class="question-content rich-content"
              />
            </section>

            <section
                v-if="selectedQuestion.options?.length"
                class="preview-section"
            >
              <h4>{{ t('courseDetail.livePractice.options') }}</h4>
              <div class="option-list">
                <div
                    v-for="option in selectedQuestion.options"
                    :key="option.id"
                    :class="{
                    correct: shouldShowAnswerKey(selectedQuestion) && option.isCorrect === 1,
                    selected: isSelectedOption(option.id) || isDraftOptionSelected(selectedQuestion.id, option.id),
                    answerable: canAnswerQuestion(selectedQuestion),
                  }"
                    class="option-row"
                    @click="toggleDraftOption(selectedQuestion, option.id)"
                >
                  <span class="option-choice">
                    <input
                        v-if="canAnswerQuestion(selectedQuestion)"
                        :checked="isDraftOptionSelected(selectedQuestion.id, option.id)"
                        :disabled="submittingQuestionId === selectedQuestion.id"
                        :name="selectedQuestion.id"
                        :type="selectedQuestion.questionType === 1 ? 'checkbox' : 'radio'"
                        @change="toggleDraftOption(selectedQuestion, option.id)"
                        @click.stop
                    >
                    <span class="option-label">{{ option.optionLabel }}</span>
                  </span>
                  <RichMathContent
                      :content="option.optionContent"
                      class="option-content rich-content"
                  />
                  <strong v-if="shouldShowAnswerKey(selectedQuestion) && option.isCorrect === 1">{{
                      t('courseDetail.livePractice.correct')
                    }}</strong>
                  <strong v-else-if="isSelectedOption(option.id)">{{ t('courseDetail.livePractice.selected') }}</strong>
                </div>
              </div>
            </section>

            <section
                v-if="selectedQuestion.tags?.length"
                class="preview-section"
            >
              <h4>{{ t('courseDetail.livePractice.tags') }}</h4>
              <div class="tag-list">
                <span
                    v-for="tag in selectedQuestion.tags"
                    :key="tag"
                >
                  <Tag
                      :size="13"
                      stroke-width="1.8"
                  />
                  {{ tag }}
                </span>
              </div>
            </section>

            <section
                v-if="canManageCourse"
                class="preview-section"
            >
              <h4>{{ t('courseDetail.livePractice.teacherAnalysis') }}</h4>
              <div
                  v-if="selectedQuestion.analysis"
                  class="analysis-grid"
              >
                <div>
                  <span>{{ t('courseDetail.livePractice.submittedLabel') }}</span>
                  <strong>{{ selectedQuestion.analysis.submittedCount }}</strong>
                </div>
                <div>
                  <span>{{ t('courseDetail.livePractice.notSubmittedLabel') }}</span>
                  <strong>{{ selectedQuestion.analysis.notSubmittedCount }}</strong>
                </div>
                <div>
                  <span>{{ t('courseDetail.livePractice.correctLabel') }}</span>
                  <strong>{{ selectedQuestion.analysis.correctCount }}</strong>
                </div>
                <div>
                  <span>{{ t('courseDetail.livePractice.averageScoreLabel') }}</span>
                  <strong>{{ selectedQuestion.analysis.averageScore }}</strong>
                </div>
              </div>

              <div
                  v-if="selectedQuestion.analysis && isObjectiveQuestion(selectedQuestion.questionType)"
                  class="analysis-chart-panel"
              >
                <div class="chart-title-row">
                  <span>{{ t('courseDetail.livePractice.optionDistributionChart') }}</span>
                </div>
                <DashboardChart
                    :aria-label="t('courseDetail.livePractice.optionDistributionAria')"
                    :empty-text="t('courseDetail.livePractice.analysisChartEmpty')"
                    :has-data="selectedOptionDistributionHasData"
                    :option="selectedOptionDistributionOption"
                    height="220px"
                />
              </div>

              <div
                  v-else-if="selectedQuestion.analysis"
                  class="analysis-chart-panel"
              >
                <div class="chart-title-row">
                  <span>{{ t('courseDetail.livePractice.scoreDistributionChart') }}</span>
                </div>
                <DashboardChart
                    :aria-label="t('courseDetail.livePractice.scoreDistributionAria')"
                    :empty-text="t('courseDetail.livePractice.analysisChartEmpty')"
                    :has-data="selectedScoreDistributionHasData"
                    :option="selectedScoreDistributionOption"
                    height="220px"
                />
              </div>

              <div
                  v-if="selectedQuestion.analysis?.notSubmittedStudents.length"
                  class="not-submitted"
              >
                <span>{{ t('courseDetail.livePractice.notSubmittedStudents') }}</span>
                <div>
                  <span
                      v-for="student in selectedQuestion.analysis.notSubmittedStudents"
                      :key="student.id"
                  >
                    {{ student.displayName || student.id }}
                  </span>
                </div>
              </div>
              <div
                  v-if="selectedQuestion.analysis?.submissions?.length"
                  class="ai-submission-list"
              >
                <span>学生提交</span>
                <article
                    v-for="submission in selectedQuestion.analysis.submissions"
                    :key="submission.id"
                    class="ai-submission-row"
                >
                  <div>
                    <strong>{{ submission.studentName || submission.studentId }}</strong>
                    <span :class="aiStatusClass(submission.aiGradingStatus)">
                      {{ aiStatusText(submission.aiGradingStatus) }}
                    </span>
                  </div>
                  <RichMathContent
                      v-if="submission.textAnswer"
                      :content="submission.textAnswer"
                      class="text-answer"
                  />
                  <p>
                    {{ submission.earnedScore }} / {{ selectedQuestion.score }} 分
                    <template v-if="submission.aiGradingFeedback"> · {{ submission.aiGradingFeedback }}</template>
                    <template v-else-if="submission.aiGradingError"> · {{ submission.aiGradingError }}</template>
                  </p>
                </article>
              </div>
            </section>

            <section
                v-if="shouldShowAnswerKey(selectedQuestion) && answerItems.length"
                class="preview-section"
            >
              <h4>{{ t('courseDetail.livePractice.referenceAnswers') }}</h4>
              <div class="answer-list">
                <RichMathContent
                    v-for="answer in answerItems"
                    :key="answer"
                    :content="answer"
                    class="answer-row"
                />
              </div>
            </section>

            <section
                v-if="!canManageCourse"
                class="preview-section"
            >
              <h4>{{ t('courseDetail.livePractice.mySubmission') }}</h4>
              <div
                  v-if="selectedQuestion.mySubmission"
                  class="submission-card"
              >
                <div class="submission-meta">
                  <span :class="submitStatusClass(selectedQuestion.mySubmission.submitStatus)">
                    {{ submitStatusLabel(selectedQuestion.mySubmission.submitStatus) }}
                  </span>
                  <strong>{{
                      t('courseDetail.livePractice.earnedScore', {score: selectedQuestion.mySubmission.earnedScore})
                    }}</strong>
                  <span>{{ formatDateTime(selectedQuestion.mySubmission.submittedAt) }}</span>
                </div>
                <div
                    v-if="selectedOptionItems.length"
                    class="answer-list"
                >
                  <RichMathContent
                      v-for="option in selectedOptionItems"
                      :key="option"
                      :content="option"
                      class="answer-row"
                  />
                </div>
                <RichMathContent
                    v-if="selectedQuestion.mySubmission.textAnswer"
                    :content="selectedQuestion.mySubmission.textAnswer"
                    class="text-answer"
                />
                <div
                    v-if="selectedQuestion.aiGradingEnabled === 1"
                    class="ai-result-card"
                >
                  <span :class="aiStatusClass(selectedQuestion.mySubmission.aiGradingStatus)">
                    {{ aiStatusText(selectedQuestion.mySubmission.aiGradingStatus) }}
                  </span>
                  <p v-if="selectedQuestion.mySubmission.aiGradingFeedback">
                    {{ selectedQuestion.mySubmission.aiGradingFeedback }}</p>
                  <p v-else-if="selectedQuestion.mySubmission.aiGradingError">
                    {{ selectedQuestion.mySubmission.aiGradingError }}</p>
                </div>
                <div
                    v-if="isObjectiveQuestion(selectedQuestion.questionType) && correctOptionItems.length"
                    class="correct-answer-block"
                >
                  <span>{{ t('courseDetail.livePractice.correctAnswers') }}</span>
                  <div class="answer-list">
                    <RichMathContent
                        v-for="option in correctOptionItems"
                        :key="option"
                        :content="option"
                        class="answer-row correct-answer-row"
                    />
                  </div>
                </div>
              </div>
              <div
                  v-else
                  class="answer-form"
              >
                <p class="muted-text">{{ t('courseDetail.livePractice.notSubmittedQuestion') }}</p>
                <label
                    v-if="!isObjectiveQuestion(selectedQuestion.questionType)"
                    class="text-answer-input"
                >
                  <span>{{ t('courseDetail.livePractice.answerLabel') }}</span>
                  <textarea
                      v-model="textAnswers[selectedQuestion.id]"
                      :disabled="submittingQuestionId === selectedQuestion.id"
                      :placeholder="t('courseDetail.livePractice.answerPlaceholder')"
                  />
                </label>
                <button
                    :disabled="!canSubmitAnswer || submittingQuestionId === selectedQuestion.id"
                    class="submit-answer-button"
                    type="button"
                    @click="submitSelectedAnswer"
                >
                  {{
                    submittingQuestionId === selectedQuestion.id
                        ? t('courseDetail.livePractice.submittingAnswer')
                        : t('courseDetail.livePractice.submitAnswer')
                  }}
                </button>
              </div>
            </section>
          </template>
        </aside>
      </div>
    </template>
  </section>
</template>

<script lang="ts" setup>
import type {EChartsCoreOption} from 'echarts/core'
import {computed, onBeforeUnmount, reactive, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import {ArrowLeft, Check, CircleAlert, ClipboardList, Clock, FileText, Hash, Tag, Users,} from 'lucide-vue-next'

import {getClassSession} from '@/features/course/api/classSession'
import {getCourse} from '@/features/course/api/course'
import {getLivePractice, submitLivePracticeAnswer} from '@/features/live-practice/api/livePractice'
import {useAuthStore} from '@/features/auth/stores/auth'
import type {ClassSession} from '@/features/course/types/classSession'
import type {CourseDetail} from '@/features/course/types/course'
import type {LivePracticeGroup, LivePracticeQuestion} from '@/features/live-practice/types/livePractice'
import {notify} from '@/shared/composables/useGlobalNotification'
import DashboardChart from '@/features/dashboard/components/DashboardChart.vue'
import RichMathContent from '@/shared/components/RichMathContent.vue'

interface QuestionMetric {
  id: string
  label: string
  submittedCount: number
  correctRate: number
  averageScoreRate: number
  notSubmittedCount: number
}

interface SubmissionBreakdown {
  submitted: number
  late: number
  missing: number
}

interface ScoreBucket {
  label: string
  value: number
}

const {t, locale} = useI18n()
const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const courseId = computed(() => String(route.params.courseId || ''))
const groupId = computed(() => String(route.params.groupId || ''))
const loading = ref(false)
const loadFailed = ref(false)
const course = ref<CourseDetail | null>(null)
const group = ref<LivePracticeGroup | null>(null)
const classSession = ref<ClassSession | null>(null)
const selectedQuestionId = ref<string | null>(null)
const selectedOptionAnswers = reactive<Record<string, string[]>>({})
const textAnswers = reactive<Record<string, string>>({})
const submittingQuestionId = ref<string | null>(null)
let aiRefreshTimer: number | null = null
let aiRefreshCount = 0

const isAdmin = computed(() => authStore.user?.role === 0)
const canManageCourse = computed(() => {
  const userId = authStore.user?.id
  if (!userId || !course.value) return isAdmin.value
  return isAdmin.value || course.value.teacherId === userId || Boolean(course.value.teacherIds?.includes(userId))
})
const questions = computed(() => group.value?.questions || [])
const selectedQuestion = computed(() => {
  return questions.value.find(question => question.id === selectedQuestionId.value) || questions.value[0] || null
})
const canSubmitAnswer = computed(() => {
  const question = selectedQuestion.value
  if (!question || !canAnswerQuestion(question)) return false
  if (isObjectiveQuestion(question.questionType)) {
    return (selectedOptionAnswers[question.id] || []).length > 0
  }
  return Boolean((textAnswers[question.id] || '').trim())
})
const selectedOptionItems = computed(() => {
  const question = selectedQuestion.value
  const selectedIds = new Set(question?.mySubmission?.selectedOptionIds || [])
  return (question?.options || [])
      .filter(option => selectedIds.has(option.id))
      .map(option => `${option.optionLabel}. ${option.optionContent}`)
})
const correctOptionItems = computed(() => {
  return (selectedQuestion.value?.options || [])
      .filter(option => option.isCorrect === 1)
      .map(option => `${option.optionLabel}. ${option.optionContent}`)
})
const answerItems = computed(() => {
  return (selectedQuestion.value?.answers || [])
      .map(answer => answer.answerContent)
      .filter(Boolean)
})
const sessionTitle = computed(() => classSession.value?.title || t('courseDetail.livePractice.classSessionFallback'))
const sessionTimeRange = computed(() => {
  if (!classSession.value) return t('courseDetail.livePractice.publishedAt', {time: formatDateTime(group.value?.publishedAt)})
  return `${formatDateTime(classSession.value.scheduledStartAt)} - ${formatDateTime(classSession.value.scheduledEndAt)}`
})
const questionMetrics = computed<QuestionMetric[]>(() => {
  return questions.value
      .filter(question => Boolean(question.analysis))
      .map(question => {
        const analysis = question.analysis!
        const submittedCount = Math.max(Number(analysis.submittedCount || 0), 0)
        const totalScore = Math.max(Number(question.score || 0), 0)
        return {
          id: question.id,
          label: t('courseDetail.livePractice.questionShortLabel', {order: question.questionOrder}),
          submittedCount,
          correctRate: submittedCount > 0 ? toPercent(Number(analysis.correctCount || 0) / submittedCount) : 0,
          averageScoreRate: totalScore > 0 ? toPercent(Number(analysis.averageScore || 0) / totalScore) : 0,
          notSubmittedCount: Math.max(Number(analysis.notSubmittedCount || 0), 0),
        }
      })
})
const submissionBreakdown = computed<SubmissionBreakdown>(() => {
  return questionMetrics.value.reduce((breakdown, metric) => {
    const question = questions.value.find(item => item.id === metric.id)
    const late = Math.max(Number(question?.analysis?.lateSubmittedCount || 0), 0)
    breakdown.late += late
    breakdown.submitted += Math.max(metric.submittedCount - late, 0)
    breakdown.missing += metric.notSubmittedCount
    return breakdown
  }, {submitted: 0, late: 0, missing: 0})
})
const submissionBreakdownHasData = computed(() => {
  const breakdown = submissionBreakdown.value
  return breakdown.submitted + breakdown.late + breakdown.missing > 0
})
const lowAccuracyQuestionCount = computed(() => {
  return questionMetrics.value.filter(metric => metric.submittedCount > 0 && metric.correctRate < 60).length
})
const followUpStudentCount = computed(() => {
  const studentIds = new Set<string>()
  questions.value.forEach(question => {
    question.analysis?.notSubmittedStudents.forEach(student => studentIds.add(student.id))
  })
  return studentIds.size
})
const submissionBreakdownOption = computed<EChartsCoreOption>(() => {
  const breakdown = submissionBreakdown.value
  return donutChartOption([
    {name: t('courseDetail.livePractice.submittedOnTime'), value: breakdown.submitted},
    {name: t('courseDetail.livePractice.lateSubmittedLabel'), value: breakdown.late},
    {name: t('courseDetail.livePractice.notSubmittedLabel'), value: breakdown.missing},
  ], t('courseDetail.livePractice.submissionProgressChart'))
})
const questionCorrectRateOption = computed<EChartsCoreOption>(() => {
  return percentBarChartOption(
      questionMetrics.value.map(metric => metric.label),
      questionMetrics.value.map(metric => metric.correctRate),
      t('courseDetail.livePractice.correctRateSeries'),
      true,
  )
})
const questionAverageScoreOption = computed<EChartsCoreOption>(() => {
  return percentBarChartOption(
      questionMetrics.value.map(metric => metric.label),
      questionMetrics.value.map(metric => metric.averageScoreRate),
      t('courseDetail.livePractice.averageScoreRateSeries'),
  )
})
const selectedOptionDistribution = computed(() => {
  const counts = selectedQuestion.value?.analysis?.optionCounts || {}
  return Object.entries(counts).map(([label, count]) => ({
    label,
    value: Math.max(Number(count || 0), 0),
  }))
})
const selectedOptionDistributionHasData = computed(() => {
  return selectedOptionDistribution.value.some(item => item.value > 0)
})
const selectedOptionDistributionOption = computed<EChartsCoreOption>(() => {
  return countBarChartOption(
      selectedOptionDistribution.value.map(item => item.label),
      selectedOptionDistribution.value.map(item => item.value),
      t('courseDetail.livePractice.optionDistributionChart'),
  )
})
const selectedScoreDistribution = computed<ScoreBucket[]>(() => {
  const question = selectedQuestion.value
  const submissions = question?.analysis?.submissions || []
  if (!question || submissions.length === 0) return []
  const totalScore = Math.max(Number(question.score || 0), 0)
  const buckets: ScoreBucket[] = [
    {label: '0%', value: 0},
    {label: '1-59%', value: 0},
    {label: '60-79%', value: 0},
    {label: '80-99%', value: 0},
    {label: '100%', value: 0},
  ]
  submissions.forEach(submission => {
    const earnedScore = Math.max(Number(submission.earnedScore || 0), 0)
    const percent = totalScore > 0 ? toPercent(earnedScore / totalScore) : 0
    if (percent <= 0) buckets[0].value += 1
    else if (percent < 60) buckets[1].value += 1
    else if (percent < 80) buckets[2].value += 1
    else if (percent < 100) buckets[3].value += 1
    else buckets[4].value += 1
  })
  return buckets
})
const selectedScoreDistributionHasData = computed(() => {
  return selectedScoreDistribution.value.some(item => item.value > 0)
})
const selectedScoreDistributionOption = computed<EChartsCoreOption>(() => {
  return countBarChartOption(
      selectedScoreDistribution.value.map(item => item.label),
      selectedScoreDistribution.value.map(item => item.value),
      t('courseDetail.livePractice.scoreDistributionChart'),
  )
})

watch(() => [courseId.value, groupId.value], () => {
  void loadData()
}, {immediate: true})

async function loadData() {
  const targetCourseId = courseId.value
  const targetGroupId = groupId.value
  if (!targetCourseId || !targetGroupId) return
  loading.value = true
  loadFailed.value = false
  try {
    const [courseData, groupData] = await Promise.all([
      getCourse(targetCourseId),
      getLivePractice(targetGroupId),
    ])
    if (targetCourseId !== courseId.value || targetGroupId !== groupId.value) return
    const sessionData = await getClassSession(groupData.classSessionId).catch(() => null)
    if (targetCourseId !== courseId.value || targetGroupId !== groupId.value) return
    course.value = courseData
    group.value = groupData
    classSession.value = sessionData
    syncSelectedQuestion(groupData.questions || [])
    maybeStartAiRefresh()
  } catch {
    if (targetCourseId === courseId.value && targetGroupId === groupId.value) loadFailed.value = true
  } finally {
    if (targetCourseId === courseId.value && targetGroupId === groupId.value) loading.value = false
  }
}

async function reloadGroup() {
  const targetGroupId = groupId.value
  if (!targetGroupId) return
  const groupData = await getLivePractice(targetGroupId)
  if (targetGroupId !== groupId.value) return
  group.value = groupData
  syncSelectedQuestion(groupData.questions || [])
  maybeStartAiRefresh()
}

onBeforeUnmount(() => {
  stopAiRefresh()
})

function syncSelectedQuestion(nextQuestions: LivePracticeQuestion[]) {
  selectedQuestionId.value = nextQuestions.some(question => question.id === selectedQuestionId.value)
      ? selectedQuestionId.value
      : nextQuestions[0]?.id || null
}

function backToList() {
  void router.push({
    name: 'course-live-practices',
    params: {id: courseId.value},
  })
}

function formatDateTime(value?: string | null) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '-'
  return new Intl.DateTimeFormat(String(locale.value), {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
}

function toPercent(value: number) {
  if (!Number.isFinite(value)) return 0
  return Math.round(Math.max(0, Math.min(value, 1)) * 1000) / 10
}

function donutChartOption(data: Array<{ name: string; value: number }>, name: string): EChartsCoreOption {
  return {
    legend: {
      bottom: 0,
      left: 'center',
    },
    series: [
      {
        name,
        type: 'pie',
        radius: ['54%', '72%'],
        center: ['50%', '42%'],
        avoidLabelOverlap: true,
        label: {
          formatter: '{b}\n{c}',
        },
        labelLine: {
          length: 8,
          length2: 6,
        },
        data: data.filter(item => item.value > 0),
      },
    ],
  }
}

function percentBarChartOption(labels: string[], values: number[], name: string, markLow = false): EChartsCoreOption {
  return {
    tooltip: {
      trigger: 'axis',
      valueFormatter: (value: unknown) => `${value}%`,
    },
    xAxis: {
      type: 'category',
      data: labels,
      axisLabel: {
        interval: 0,
      },
    },
    yAxis: {
      type: 'value',
      max: 100,
      axisLabel: {
        formatter: '{value}%',
      },
    },
    series: [
      {
        name,
        type: 'bar',
        barMaxWidth: 22,
        barMinHeight: 3,
        data: values.map(value => markLow && value < 60
            ? {value, itemStyle: {color: '#b65f5f'}}
            : value),
        itemStyle: {
          borderRadius: [3, 3, 0, 0],
        },
      },
    ],
  }
}

function countBarChartOption(labels: string[], values: number[], name: string): EChartsCoreOption {
  return {
    tooltip: {
      trigger: 'axis',
    },
    xAxis: {
      type: 'category',
      data: labels,
      axisLabel: {
        interval: 0,
      },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
    },
    series: [
      {
        name,
        type: 'bar',
        barMaxWidth: 24,
        barMinHeight: 3,
        data: values,
        itemStyle: {
          borderRadius: [3, 3, 0, 0],
        },
      },
    ],
  }
}

function questionTypeLabel(type: number) {
  const labels: Record<number, string> = {
    0: t('courseDetail.livePractice.singleChoice'),
    1: t('courseDetail.livePractice.multipleChoice'),
    2: t('courseDetail.livePractice.trueFalse'),
    3: t('courseDetail.livePractice.fillBlank'),
    4: t('courseDetail.livePractice.shortAnswer'),
  }
  return labels[type] || t('courseDetail.livePractice.unknownQuestionType')
}

function difficultyLabel(difficulty: number) {
  const labels: Record<number, string> = {
    1: t('courseDetail.livePractice.easy'),
    2: t('courseDetail.livePractice.medium'),
    3: t('courseDetail.livePractice.hard'),
  }
  return labels[difficulty] || t('courseDetail.livePractice.unknownDifficulty')
}

function difficultyClass(difficulty: number) {
  if (difficulty === 1) return 'easy'
  if (difficulty === 3) return 'hard'
  return 'medium'
}

function submitStatusClass(status?: number | null) {
  if (status === 1) return 'submitted'
  if (status === 2) return 'late'
  return 'missing'
}

function submitStatusLabel(status?: number | null) {
  if (status === 1) return t('courseDetail.livePractice.statusSubmitted')
  if (status === 2) return t('courseDetail.livePractice.statusLateSubmitted')
  return t('courseDetail.livePractice.statusNotSubmitted')
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

function isSelectedOption(optionId: string) {
  return Boolean(selectedQuestion.value?.mySubmission?.selectedOptionIds?.includes(optionId))
}

function isObjectiveQuestion(type: number) {
  return type <= 2
}

function canAnswerQuestion(question: LivePracticeQuestion) {
  return !canManageCourse.value && !question.mySubmission
}

function shouldShowAnswerKey(question: LivePracticeQuestion) {
  return canManageCourse.value || Boolean(question.mySubmission)
}

function isDraftOptionSelected(questionId: string, optionId: string) {
  return selectedOptionAnswers[questionId]?.includes(optionId) || false
}

function toggleDraftOption(question: LivePracticeQuestion, optionId: string) {
  if (!canAnswerQuestion(question) || submittingQuestionId.value === question.id) return
  if (question.questionType === 1) {
    const current = selectedOptionAnswers[question.id] || []
    selectedOptionAnswers[question.id] = current.includes(optionId)
        ? current.filter(id => id !== optionId)
        : [...current, optionId]
    return
  }
  selectedOptionAnswers[question.id] = [optionId]
}

async function submitSelectedAnswer() {
  const question = selectedQuestion.value
  const targetGroup = group.value
  if (!question || !targetGroup || !canSubmitAnswer.value) return
  submittingQuestionId.value = question.id
  try {
    await submitLivePracticeAnswer(targetGroup.id, question.id, {
      selectedOptionIds: isObjectiveQuestion(question.questionType) ? selectedOptionAnswers[question.id] : undefined,
      textAnswer: isObjectiveQuestion(question.questionType) ? undefined : textAnswers[question.id]?.trim(),
    })
    delete selectedOptionAnswers[question.id]
    delete textAnswers[question.id]
    notify.success(t('courseDetail.livePractice.submitSuccess'))
    await reloadGroup()
    maybeStartAiRefresh()
  } catch {
    notify.error(t('courseDetail.livePractice.submitFailed'))
  } finally {
    submittingQuestionId.value = null
  }
}

function maybeStartAiRefresh() {
  if (!hasPendingAiGrading()) {
    stopAiRefresh()
    return
  }
  if (aiRefreshTimer) return
  aiRefreshCount = 0
  aiRefreshTimer = window.setInterval(() => {
    aiRefreshCount += 1
    if (aiRefreshCount > 10 || !hasPendingAiGrading()) {
      stopAiRefresh()
      return
    }
    void reloadGroup().catch(() => stopAiRefresh())
  }, 3000)
}

function stopAiRefresh() {
  if (!aiRefreshTimer) return
  window.clearInterval(aiRefreshTimer)
  aiRefreshTimer = null
}

function hasPendingAiGrading() {
  return questions.value.some(question => {
    if (question.mySubmission?.aiGradingStatus === 'PENDING') return true
    return Boolean(question.analysis?.submissions?.some(submission => submission.aiGradingStatus === 'PENDING'))
  })
}
</script>

<style scoped>
.practice-detail-page {
  display: grid;
  gap: 18px;
}

.back-link,
.text-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  min-height: 36px;
  width: fit-content;
  padding: 0 12px;
  background: transparent;
  border: 1px solid var(--color-outline-light);
  border-radius: 0;
  color: var(--color-on-surface);
  cursor: pointer;
  font-family: var(--font-label);
  font-size: 13px;
  text-decoration: none;
}

.back-link:hover,
.text-button:hover {
  background: var(--color-surface-container-high);
  border-color: var(--color-outline);
}

.detail-header {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 18px;
  padding-bottom: 18px;
  border-bottom: 1px solid var(--color-outline-light);
}

.detail-header span,
.header-count span,
.ledger-header span,
.section-label,
.preview-section h4,
.analysis-grid span,
.not-submitted > span {
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 12px;
  font-weight: 400;
  line-height: 1;
  letter-spacing: 0.05em;
  text-transform: uppercase;
}

.detail-header h2 {
  margin: 8px 0;
  color: var(--color-on-surface);
  font-family: var(--font-heading);
  font-size: clamp(32px, 4vw, 44px);
  font-weight: 400;
  line-height: 1.22;
}

.detail-header p {
  margin: 0;
  color: var(--color-on-surface-variant);
  font-family: var(--font-body);
  font-size: 15px;
  line-height: 1.55;
}

.header-count {
  display: grid;
  min-width: 120px;
  gap: 6px;
  text-align: right;
}

.header-count strong {
  color: var(--color-on-surface);
  font-family: var(--font-heading);
  font-size: 34px;
  font-weight: 400;
  line-height: 1;
}

.summary-strip {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
}

.summary-item {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  padding: 14px;
  border-left: 1px solid var(--color-outline-light);
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 13px;
}

.summary-item:first-child {
  border-left: 0;
}

.summary-item svg {
  flex: 0 0 auto;
}

.detail-workspace {
  display: grid;
  grid-template-columns: minmax(0, 4fr) minmax(420px, 6fr);
  gap: 18px;
  align-items: start;
}

.detail-workspace.teacher-layout {
  grid-template-columns: minmax(260px, 3fr) minmax(320px, 4fr) minmax(420px, 5fr);
}

.question-ledger,
.teacher-overview,
.question-preview,
.state-block {
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: 0;
}

.ledger-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--color-outline-light);
}

.ledger-header strong {
  color: var(--color-on-surface);
  font-family: var(--font-label);
  font-size: 12px;
  font-weight: 400;
}

.teacher-overview {
  display: grid;
  gap: 0;
}

.overview-header,
.chart-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.overview-header {
  padding: 14px 16px;
  border-bottom: 1px solid var(--color-outline-light);
}

.overview-header span,
.overview-stats span,
.chart-title-row span {
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 12px;
  font-weight: 400;
  line-height: 1;
  letter-spacing: 0.05em;
  text-transform: uppercase;
}

.overview-header strong {
  color: var(--color-on-surface);
  font-family: var(--font-label);
  font-size: 12px;
  font-weight: 400;
}

.overview-stats {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  border-bottom: 1px solid var(--color-outline-light);
}

.overview-stats div {
  display: grid;
  gap: 8px;
  min-width: 0;
  padding: 14px 16px;
  border-left: 1px solid var(--color-outline-light);
}

.overview-stats div:first-child {
  border-left: 0;
}

.overview-stats strong {
  color: var(--color-on-surface);
  font-family: var(--font-heading);
  font-size: 26px;
  font-weight: 400;
  line-height: 1;
}

.overview-chart-panel,
.analysis-chart-panel {
  display: grid;
  gap: 12px;
  min-width: 0;
}

.overview-chart-panel {
  padding: 16px;
  border-bottom: 1px solid var(--color-outline-light);
}

.overview-chart-panel:last-child {
  border-bottom: 0;
}

.analysis-chart-panel {
  padding: 12px;
  border: 1px solid var(--color-outline-light);
}

.question-row {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr);
  gap: 12px;
  align-items: start;
  width: 100%;
  min-height: 74px;
  padding: 14px 16px;
  background: transparent;
  border: 0;
  border-bottom: 1px solid var(--color-outline-light);
  color: inherit;
  cursor: pointer;
  text-align: left;
}

.question-row:last-child {
  border-bottom: 0;
}

.question-row:hover,
.question-row.selected {
  background: var(--color-surface-container);
}

.question-order {
  display: grid;
  width: 30px;
  height: 30px;
  place-items: center;
  background: var(--color-surface-container);
  border: 1px solid var(--color-outline-light);
  color: var(--color-on-surface);
  font-family: var(--font-label);
  font-size: 12px;
}

.question-main {
  min-width: 0;
}

.question-title {
  display: block;
  overflow: hidden;
  color: var(--color-on-surface);
  font-family: var(--font-body);
  font-size: 15px;
  font-weight: 700;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.question-tags,
.preview-meta,
.tag-list,
.option-counts,
.submission-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.question-tags {
  margin-top: 8px;
}

.question-tags span,
.preview-meta span,
.tag-list span,
.option-counts span,
.submission-meta span,
.submission-meta strong,
.difficulty-badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  min-height: 24px;
  padding: 0 8px;
  background: var(--color-surface-container);
  border: 1px solid var(--color-outline-light);
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 12px;
  font-weight: 400;
  line-height: 1;
}

.submitted {
  color: #16a34a !important;
}

.late {
  color: #ca8a04 !important;
}

.missing {
  color: var(--color-muted) !important;
}

.question-preview {
  position: sticky;
  top: 18px;
}

.preview-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  padding: 18px;
  border-bottom: 1px solid var(--color-outline-light);
}

.preview-header h3 {
  margin: 8px 0 0;
  color: var(--color-on-surface);
  font-family: var(--font-heading);
  font-size: 30px;
  font-weight: 400;
  line-height: 1.22;
  text-wrap: pretty;
}

.difficulty-badge.easy {
  color: #16a34a;
}

.difficulty-badge.hard {
  color: var(--color-error);
}

.preview-meta {
  padding: 14px 18px;
  border-bottom: 1px solid var(--color-outline-light);
}

.preview-section {
  display: grid;
  gap: 12px;
  padding: 18px;
  border-bottom: 1px solid var(--color-outline-light);
}

.preview-section:last-child {
  border-bottom: 0;
}

.preview-section h4 {
  margin: 0;
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

.question-content.rich-content {
  font-size: 15px;
  line-height: 1.65;
}

.rich-content :deep(p),
.answer-row :deep(p),
.text-answer :deep(p) {
  margin: 0;
}

.option-list,
.answer-list {
  display: grid;
  gap: 8px;
}

.option-row {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
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

.option-row.answerable {
  cursor: pointer;
}

.option-row.answerable:hover {
  border-color: var(--color-primary);
  background: var(--color-surface-container);
}

.option-choice {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.option-choice input {
  margin: 0;
  accent-color: var(--color-primary);
  cursor: pointer;
}

.option-content.rich-content {
  font-size: 14px;
  line-height: 1.5;
}

.option-row.selected {
  border-color: var(--color-primary);
  background: var(--color-surface-container);
  color: var(--color-on-surface);
}

.option-row.correct {
  border-color: #16a34a;
  background: rgba(22, 163, 74, 0.08);
  color: var(--color-on-surface);
}

.option-row.correct .option-label {
  border-color: #16a34a;
  color: #16a34a;
}

.option-row strong {
  color: var(--color-on-surface);
  font-family: var(--font-label);
  font-size: 12px;
  font-weight: 400;
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

.analysis-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  border: 1px solid var(--color-outline-light);
}

.analysis-grid div {
  display: grid;
  gap: 8px;
  padding: 12px;
  border-left: 1px solid var(--color-outline-light);
}

.analysis-grid div:first-child {
  border-left: 0;
}

.analysis-grid strong {
  color: var(--color-on-surface);
  font-family: var(--font-heading);
  font-size: 26px;
  font-weight: 400;
  line-height: 1;
}

.not-submitted {
  display: grid;
  gap: 10px;
}

.ai-submission-list,
.ai-submission-row,
.ai-result-card {
  display: grid;
  gap: 10px;
}

.ai-submission-list > span {
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 12px;
}

.ai-submission-row,
.ai-result-card {
  padding: 12px;
  background: var(--color-surface-container);
  border: 1px solid var(--color-outline-light);
}

.ai-submission-row > div {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.ai-submission-row strong {
  color: var(--color-on-surface);
  font-family: var(--font-body);
  font-size: 14px;
}

.ai-submission-row p,
.ai-result-card p {
  margin: 0;
  color: var(--color-on-surface-variant);
  font-family: var(--font-body);
  font-size: 13px;
  line-height: 1.5;
}

.ai-submission-row span,
.ai-result-card span {
  width: fit-content;
  padding: 4px 8px;
  border: 1px solid var(--color-outline-light);
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 12px;
}

.ai-submission-row span.completed,
.ai-result-card span.completed {
  border-color: #16a34a;
  color: #16a34a;
}

.ai-submission-row span.failed,
.ai-result-card span.failed {
  border-color: #b65f5f;
  color: #b65f5f;
}

.ai-submission-row span.pending,
.ai-result-card span.pending {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.not-submitted div {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.not-submitted div span,
.answer-row {
  padding: 8px 10px;
  background: var(--color-surface-container);
  border: 1px solid var(--color-outline-light);
  color: var(--color-on-surface-variant);
  font-family: var(--font-body);
  font-size: 13px;
  line-height: 1.4;
}

.answer-row {
  max-width: none;
  margin: 0;
}

.answer-row.rich-math-content,
.text-answer.rich-math-content {
  font-size: 13px;
  line-height: 1.4;
}

.submission-card {
  display: grid;
  gap: 12px;
}

.correct-answer-block {
  display: grid;
  gap: 8px;
}

.correct-answer-block > span {
  color: #16a34a;
  font-family: var(--font-label);
  font-size: 12px;
}

.correct-answer-row {
  border-color: #16a34a;
  background: rgba(22, 163, 74, 0.08);
}

.answer-form,
.text-answer-input {
  display: grid;
  gap: 12px;
}

.answer-form p {
  margin: 0;
}

.text-answer-input span {
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 12px;
}

.text-answer-input textarea {
  min-height: 120px;
  padding: 12px;
  background: var(--color-surface-canvas);
  border: 1px solid var(--color-outline-light);
  color: var(--color-on-surface);
  font-family: var(--font-body);
  font-size: 14px;
  line-height: 1.6;
  resize: vertical;
}

.text-answer-input textarea:focus {
  border-color: var(--color-outline);
  outline: none;
}

.submit-answer-button {
  justify-self: start;
  min-height: 36px;
  padding: 0 14px;
  background: var(--color-primary);
  border: 1px solid var(--color-primary);
  color: var(--color-on-primary);
  cursor: pointer;
  font-family: var(--font-label);
  font-size: 13px;
}

.submit-answer-button:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.text-answer {
  padding: 12px;
  background: var(--color-surface-container);
  border: 1px solid var(--color-outline-light);
}

.muted-text,
.empty-list {
  color: var(--color-muted) !important;
}

.empty-list,
.state-block {
  padding: 42px 24px;
  text-align: center;
}

.state-block {
  display: grid;
  min-height: 220px;
  place-items: center;
  align-content: center;
  gap: 12px;
  color: var(--color-muted);
}

.state-block h3 {
  margin: 0;
  color: var(--color-on-surface);
  font-family: var(--font-heading);
  font-size: 24px;
  font-weight: 400;
}

@media (max-width: 1180px) {
  .detail-workspace,
  .detail-workspace.teacher-layout {
    grid-template-columns: 1fr;
  }

  .question-preview {
    position: static;
  }
}

@media (max-width: 760px) {
  .detail-header,
  .preview-header {
    flex-direction: column;
    align-items: stretch;
  }

  .header-count {
    text-align: left;
  }

  .summary-strip,
  .analysis-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .summary-item:nth-child(odd),
  .analysis-grid div:nth-child(odd) {
    border-left: 0;
  }

  .summary-item:nth-child(n + 3),
  .analysis-grid div:nth-child(n + 3) {
    border-top: 1px solid var(--color-outline-light);
  }

  .option-row {
    grid-template-columns: 34px minmax(0, 1fr);
  }

  .option-row strong {
    grid-column: 2;
  }
}
</style>
