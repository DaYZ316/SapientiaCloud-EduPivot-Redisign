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
      返回练习列表
    </button>

    <div
      v-if="loading"
      class="state-block"
    >
      加载中...
    </div>
    <div
      v-else-if="loadFailed"
      class="state-block"
    >
      <CircleAlert
        :size="28"
        stroke-width="1.5"
      />
      <h3>练习详情加载失败</h3>
      <button
        class="text-button"
        type="button"
        @click="loadData"
      >
        重试
      </button>
    </div>

    <template v-else-if="group">
      <header class="detail-header">
        <div>
          <span>Live Practice</span>
          <h2>{{ group.title }}</h2>
          <p>
            {{ sessionTitle }} · 第 {{ group.publishOrder }} 组 · {{ group.totalQuestions }} 题 ·
            {{ formatDateTime(group.availableEndAt) }} 截止
          </p>
        </div>
        <div class="header-count">
          <strong>{{ group.submittedStudents }}/{{ group.totalStudents }}</strong>
          <span>提交学生</span>
        </div>
      </header>

      <div class="summary-strip">
        <div class="summary-item">
          <ClipboardList
            :size="16"
            stroke-width="1.8"
          />
          <span>{{ group.totalQuestions }} 题</span>
        </div>
        <div class="summary-item">
          <Users
            :size="16"
            stroke-width="1.8"
          />
          <span>{{ group.totalStudents }} 名学生</span>
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
          <span>{{ group.allowLateSubmission === 1 ? '允许补交' : '不允许补交' }}</span>
        </div>
      </div>

      <div class="detail-workspace">
        <section class="question-ledger">
          <div class="ledger-header">
            <span>题目列表</span>
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
                <span>{{ question.score }} 分</span>
                <span v-if="canManageCourse && question.analysis">
                  未提交 {{ question.analysis.notSubmittedCount }}
                </span>
                <span
                  v-else
                  :class="submitStatusClass(question.mySubmission?.submitStatus)"
                  class="submit-badge"
                >
                  {{ question.mySubmission?.submitStatusText || '未提交' }}
                </span>
              </span>
            </span>
          </button>

          <div
            v-if="questions.length === 0"
            class="empty-list"
          >
            暂无题目
          </div>
        </section>

        <aside class="question-preview">
          <template v-if="selectedQuestion">
            <div class="preview-header">
              <div>
                <span class="section-label">题目浏览</span>
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
                {{ selectedQuestion.score }} 分
              </span>
              <span v-if="selectedQuestion.estimatedTime">
                <Clock
                  :size="14"
                  stroke-width="1.8"
                />
                {{ selectedQuestion.estimatedTime }} 分钟
              </span>
            </div>

            <section class="preview-section">
              <h4>题干</h4>
              <p>{{ selectedQuestion.questionContent || selectedQuestion.questionTitle }}</p>
            </section>

            <section
              v-if="selectedQuestion.options?.length"
              class="preview-section"
            >
              <h4>选项</h4>
              <div class="option-list">
                <div
                  v-for="option in selectedQuestion.options"
                  :key="option.id"
                  :class="{
                    correct: canManageCourse && option.isCorrect === 1,
                    selected: isSelectedOption(option.id),
                  }"
                  class="option-row"
                >
                  <span class="option-label">{{ option.optionLabel }}</span>
                  <span>{{ option.optionContent }}</span>
                  <strong v-if="canManageCourse && option.isCorrect === 1">正确</strong>
                  <strong v-else-if="isSelectedOption(option.id)">已选</strong>
                </div>
              </div>
            </section>

            <section
              v-if="selectedQuestion.tags?.length"
              class="preview-section"
            >
              <h4>标签</h4>
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
              <h4>教师分析</h4>
              <div
                v-if="selectedQuestion.analysis"
                class="analysis-grid"
              >
                <div>
                  <span>提交</span>
                  <strong>{{ selectedQuestion.analysis.submittedCount }}</strong>
                </div>
                <div>
                  <span>未提交</span>
                  <strong>{{ selectedQuestion.analysis.notSubmittedCount }}</strong>
                </div>
                <div>
                  <span>正确</span>
                  <strong>{{ selectedQuestion.analysis.correctCount }}</strong>
                </div>
                <div>
                  <span>均分</span>
                  <strong>{{ selectedQuestion.analysis.averageScore }}</strong>
                </div>
              </div>

              <div
                v-if="selectedQuestion.analysis && Object.keys(selectedQuestion.analysis.optionCounts || {}).length"
                class="option-counts"
              >
                <span
                  v-for="(count, label) in selectedQuestion.analysis.optionCounts"
                  :key="label"
                >
                  {{ label }} {{ count }}
                </span>
              </div>

              <div
                v-if="selectedQuestion.analysis?.notSubmittedStudents.length"
                class="not-submitted"
              >
                <span>未提交学生</span>
                <div>
                  <span
                    v-for="student in selectedQuestion.analysis.notSubmittedStudents"
                    :key="student.id"
                  >
                    {{ student.displayName || student.id }}
                  </span>
                </div>
              </div>
            </section>

            <section
              v-if="canManageCourse && answerItems.length"
              class="preview-section"
            >
              <h4>参考答案</h4>
              <div class="answer-list">
                <div
                  v-for="answer in answerItems"
                  :key="answer"
                  class="answer-row"
                >
                  {{ answer }}
                </div>
              </div>
            </section>

            <section
              v-if="!canManageCourse"
              class="preview-section"
            >
              <h4>我的提交</h4>
              <div
                v-if="selectedQuestion.mySubmission"
                class="submission-card"
              >
                <div class="submission-meta">
                  <span :class="submitStatusClass(selectedQuestion.mySubmission.submitStatus)">
                    {{ selectedQuestion.mySubmission.submitStatusText }}
                  </span>
                  <strong>得分 {{ selectedQuestion.mySubmission.earnedScore }}</strong>
                  <span>{{ formatDateTime(selectedQuestion.mySubmission.submittedAt) }}</span>
                </div>
                <div
                  v-if="selectedOptionItems.length"
                  class="answer-list"
                >
                  <div
                    v-for="option in selectedOptionItems"
                    :key="option"
                    class="answer-row"
                  >
                    {{ option }}
                  </div>
                </div>
                <p
                  v-if="selectedQuestion.mySubmission.textAnswer"
                  class="text-answer"
                >
                  {{ selectedQuestion.mySubmission.textAnswer }}
                </p>
              </div>
              <p
                v-else
                class="muted-text"
              >
                尚未提交本题。
              </p>
            </section>
          </template>
        </aside>
      </div>
    </template>
  </section>
</template>

<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import {
  ArrowLeft,
  Check,
  CircleAlert,
  ClipboardList,
  Clock,
  FileText,
  Hash,
  Tag,
  Users,
} from 'lucide-vue-next'

import {getClassSession} from '@/features/course/api/classSession'
import {getCourse} from '@/features/course/api/course'
import {getLivePractice} from '@/features/live-practice/api/livePractice'
import {useAuthStore} from '@/features/auth/stores/auth'
import type {ClassSession} from '@/features/course/types/classSession'
import type {CourseDetail} from '@/features/course/types/course'
import type {LivePracticeGroup} from '@/features/live-practice/types/livePractice'

const {locale} = useI18n()
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
const selectedOptionItems = computed(() => {
  const question = selectedQuestion.value
  const selectedIds = new Set(question?.mySubmission?.selectedOptionIds || [])
  return (question?.options || [])
      .filter(option => selectedIds.has(option.id))
      .map(option => `${option.optionLabel}. ${option.optionContent}`)
})
const answerItems = computed(() => {
  return (selectedQuestion.value?.answers || [])
      .map(answer => answer.answerContent)
      .filter(Boolean)
})
const sessionTitle = computed(() => classSession.value?.title || '所属开课记录')
const sessionTimeRange = computed(() => {
  if (!classSession.value) return `${formatDateTime(group.value?.publishedAt)} 发布`
  return `${formatDateTime(classSession.value.scheduledStartAt)} - ${formatDateTime(classSession.value.scheduledEndAt)}`
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
    const nextQuestions = groupData.questions || []
    selectedQuestionId.value = nextQuestions.some(question => question.id === selectedQuestionId.value)
        ? selectedQuestionId.value
        : nextQuestions[0]?.id || null
  } catch {
    if (targetCourseId === courseId.value && targetGroupId === groupId.value) loadFailed.value = true
  } finally {
    if (targetCourseId === courseId.value && targetGroupId === groupId.value) loading.value = false
  }
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

function questionTypeLabel(type: number) {
  const labels: Record<number, string> = {
    0: '单选题',
    1: '多选题',
    2: '判断题',
    3: '填空题',
    4: '简答题',
  }
  return labels[type] || '未知题型'
}

function difficultyLabel(difficulty: number) {
  const labels: Record<number, string> = {
    1: '简单',
    2: '中等',
    3: '困难',
  }
  return labels[difficulty] || '未知难度'
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

function isSelectedOption(optionId: string) {
  return Boolean(selectedQuestion.value?.mySubmission?.selectedOptionIds?.includes(optionId))
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
  border-radius: var(--radius-sm);
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

.question-ledger,
.question-preview,
.state-block {
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
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

.option-list,
.answer-list {
  display: grid;
  gap: 8px;
}

.option-row {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr) auto;
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

.option-row.correct,
.option-row.selected {
  border-color: var(--color-primary);
  background: var(--color-surface-container);
  color: var(--color-on-surface);
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

.submission-card {
  display: grid;
  gap: 12px;
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
  .detail-workspace {
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
