<template>
  <aside
    :aria-busy="Boolean(exportingFormat)"
    :class="{'is-exporting': Boolean(exportingFormat)}"
    class="ai-studio-panel"
  >
    <header
      :class="{'has-artifact-tabs': artifact && !mode}"
      class="studio-header"
    >
      <div
        v-if="artifact && !mode"
        :class="{
          'has-answer-toggle': activeArtifactTab !== 'trace',
          'has-question-stepper': activeArtifactTab === 'single' && questions.length > 0,
          'has-export-actions': showExportActions,
        }"
        class="artifact-toolbar"
      >
        <nav
          :aria-label="t('common.ai.studio.artifactTabsAria')"
          class="artifact-tabs"
        >
          <button
            :class="{active: activeArtifactTab === 'single'}"
            type="button"
            @click="activeArtifactTab = 'single'"
          >
            {{ t('common.ai.studio.singlePreview') }}
          </button>
          <button
            :class="{active: activeArtifactTab === 'overall'}"
            type="button"
            @click="activeArtifactTab = 'overall'"
          >
            {{ t('common.ai.studio.overallPreview') }}
          </button>
          <button
            :class="{active: activeArtifactTab === 'trace'}"
            type="button"
            @click="activeArtifactTab = 'trace'"
          >
            {{ t('common.ai.studio.generationTrace') }}
          </button>
        </nav>
        <div
          v-if="activeArtifactTab !== 'trace'"
          :aria-label="t('questionBank.showAnswer')"
          class="answer-toggle"
          role="group"
        >
          <button
            :aria-pressed="!showQuestionAnswer"
            :class="{active: !showQuestionAnswer}"
            type="button"
            @click="showQuestionAnswer = false"
          >
            {{ t('common.ai.studio.answerOff') }}
          </button>
          <button
            :aria-pressed="showQuestionAnswer"
            :class="{active: showQuestionAnswer}"
            type="button"
            @click="showQuestionAnswer = true"
          >
            {{ t('common.ai.studio.answerOn') }}
          </button>
        </div>
        <div
          v-if="activeArtifactTab === 'single' && questions.length > 0"
          :aria-label="t('questionBank.questionPreview')"
          class="question-stepper"
          role="group"
        >
          <button
            :aria-label="t('questionBank.prevQuestion')"
            :disabled="activeQuestionIndex === 0"
            type="button"
            @click="activeQuestionIndex -= 1"
          >
            <ChevronLeft
              :size="16"
              stroke-width="1.9"
            />
          </button>
          <span>{{ activeQuestionIndex + 1 }} / {{ questions.length }}</span>
          <button
            :aria-label="t('questionBank.nextQuestion')"
            :disabled="activeQuestionIndex >= questions.length - 1"
            type="button"
            @click="activeQuestionIndex += 1"
          >
            <ChevronRight
              :size="16"
              stroke-width="1.9"
            />
          </button>
        </div>
        <div
          v-if="showExportActions"
          :aria-label="t('common.ai.studio.exportAria')"
          class="export-actions"
          role="group"
        >
          <button
            :aria-busy="exportingFormat === 'pdf'"
            :disabled="!canExportArtifact || Boolean(exportingFormat)"
            :title="t('common.ai.studio.exportPdf')"
            type="button"
            @click="exportArtifact('pdf')"
          >
            <FileDown
              :size="15"
              stroke-width="1.9"
            />
            <span>PDF</span>
          </button>
          <button
            :aria-busy="exportingFormat === 'docx'"
            :disabled="!canExportArtifact || Boolean(exportingFormat)"
            :title="t('common.ai.studio.exportWord')"
            type="button"
            @click="exportArtifact('docx')"
          >
            <FileText
              :size="15"
              stroke-width="1.9"
            />
            <span>Word</span>
          </button>
          <button
            v-if="canImportArtifact"
            :disabled="importingQuestions"
            :title="t('common.ai.studio.importToBank')"
            class="import-to-bank-button"
            type="button"
            @click="openImportDialog"
          >
            <BookPlus
              :size="15"
              stroke-width="1.9"
            />
            <span>{{ t('common.ai.studio.importToBankShort') }}</span>
          </button>
        </div>
      </div>
      <div v-else>
        <h2>{{ panelTitle }}</h2>
      </div>
      <button
        :title="t('common.ai.studio.close')"
        class="btn-close"
        type="button"
        @click="$emit('close')"
      >
        <X
          :size="16"
          stroke-width="1.8"
        />
      </button>
    </header>

    <section
      v-if="mode"
      class="generation-form"
    >
      <p>{{ formHint }}</p>

      <div
        v-if="mode === 'PAPER'"
        class="form-grid"
      >
        <label class="form-field">
          <span>{{ t('common.ai.studio.paperName') }}</span>
          <input
            v-model.trim="generationModel.paperName"
            :placeholder="t('common.ai.studio.paperNamePlaceholder')"
            class="form-input"
            maxlength="100"
            type="text"
          >
        </label>
        <label class="form-field">
          <span>{{ t('common.ai.studio.paperType') }}</span>
          <input
            v-model.trim="generationModel.paperType"
            :placeholder="t('common.ai.studio.paperTypePlaceholder')"
            class="form-input"
            maxlength="50"
            type="text"
          >
        </label>
        <label class="form-field">
          <span>{{ t('common.ai.studio.totalScore') }}</span>
          <BaseNumberStepper
            v-model="totalScoreValue"
            :max="1000"
            :min="1"
          />
        </label>
        <label class="form-field">
          <span>{{ t('common.ai.studio.estimatedTime') }}</span>
          <BaseNumberStepper
            v-model="totalEstimatedTimeValue"
            :max="300"
            :min="1"
          />
        </label>
      </div>

      <div class="form-grid">
        <label class="form-field">
          <span>{{ t('common.ai.studio.questionCount') }}</span>
          <BaseNumberStepper
            v-model="questionCountValue"
            :max="mode === 'PAPER' ? 50 : 10"
            :min="1"
          />
        </label>
        <label class="form-field">
          <span>{{ t('common.ai.studio.questionType') }}</span>
          <BaseSelect
            v-model="questionTypeValue"
            :options="questionTypeOptions"
            class="form-select-control"
            min-width="100%"
          />
        </label>
        <label class="form-field">
          <span>{{ t('common.ai.studio.difficulty') }}</span>
          <BaseSelect
            v-model="difficultyValue"
            :options="difficultyOptions"
            class="form-select-control"
            min-width="100%"
          />
        </label>
        <label class="form-field">
          <span>{{ t('common.ai.studio.scorePerQuestion') }}</span>
          <BaseNumberStepper
            v-model="scorePerQuestionValue"
            :max="100"
            :min="0"
          />
        </label>
      </div>

      <label
        v-if="mode === 'PAPER'"
        class="wide-field"
      >
        <span>{{ t('common.ai.studio.knowledgePoints') }}</span>
        <textarea
          v-model="knowledgePointsText"
          :placeholder="t('common.ai.studio.knowledgePointsPlaceholder')"
          class="form-textarea"
          rows="2"
        />
      </label>
      <label
        v-if="mode === 'PAPER'"
        class="wide-field"
      >
        <span>{{ t('common.ai.studio.abilityGoals') }}</span>
        <textarea
          v-model="abilityGoalsText"
          :placeholder="t('common.ai.studio.abilityGoalsPlaceholder')"
          class="form-textarea"
          rows="2"
        />
      </label>
      <label class="wide-field">
        <span>{{ t('common.ai.studio.requirement') }}</span>
        <textarea
          v-model="generationModel.requirement"
          :placeholder="requirementPlaceholder"
          class="form-textarea"
          maxlength="1000"
          rows="4"
        />
      </label>

      <div class="panel-footer">
        <button
          class="btn-secondary"
          type="button"
          @click="$emit('close')"
        >
          {{ t('common.confirmDialog.cancel') }}
        </button>
        <button
          class="btn-primary"
          type="button"
          @click="$emit('generate')"
        >
          {{ generateButtonText }}
        </button>
      </div>
    </section>

    <section
      v-else-if="artifact"
      :class="{'is-single-preview': activeArtifactTab === 'single'}"
      class="artifact-body"
    >
      <template v-if="activeArtifactTab === 'single'">
        <section
          v-if="activeQuestion"
          class="generated-question-preview"
        >
          <div class="preview-header">
            <div>
              <span class="section-label">{{ t('questionBank.questionPreview') }}</span>
              <h2>{{ questionTitle(activeQuestion) }}</h2>
            </div>
          </div>

          <div class="preview-meta">
            <span>{{ questionTypeName(activeQuestion) }}</span>
            <span>{{ difficultyName(activeQuestion) }}</span>
            <span v-if="scoreText(activeQuestion)">{{ scoreText(activeQuestion) }} {{ t('questionBank.score') }}</span>
            <span v-if="estimatedTimeText(activeQuestion)">{{ t('questionBank.estimatedMinutes', {n: estimatedTimeText(activeQuestion)}) }}</span>
          </div>

          <section class="preview-section">
            <h3>{{ t('questionBank.questionStem') }}</h3>
            <AiMarkdownMessage
              :content="questionContent(activeQuestion)"
              class="preview-markdown"
            />
          </section>

          <section
            v-if="activeQuestionOptions.length"
            class="preview-section"
          >
            <h3>{{ t('questionBank.options') }}</h3>
            <div class="preview-option-list">
              <div
                v-for="(option, optionIndex) in activeQuestionOptions"
                :key="optionKey(option, optionIndex)"
                :class="{correct: showQuestionAnswer && isCorrectOption(option)}"
                class="preview-option-row"
              >
                <span class="option-label">{{ optionLabelText(option, optionIndex) }}</span>
                <div class="option-body">
                  <AiMarkdownMessage
                    :content="optionContentText(option) || t('common.ai.studio.optionFallback')"
                    class="preview-markdown option-content-markdown"
                  />
                </div>
                <span
                  v-if="showQuestionAnswer && activeQuestionIsObjective"
                  class="option-score"
                >
                  {{ optionScoreText(option) || '0' }} {{ t('questionBank.score') }}
                </span>
              </div>
            </div>
          </section>

          <section
            v-if="showQuestionAnswer"
            class="preview-section"
          >
            <h3>{{ t('questionBank.correctAnswer') }}</h3>
            <div
              v-if="activeAnswerItems.length"
              class="answer-list"
            >
              <AiMarkdownMessage
                v-for="(answer, answerIndex) in activeAnswerItems"
                :key="answerIndex"
                :content="answer"
                class="answer-row"
              />
            </div>
            <p
              v-else
              class="muted-text"
            >
              {{ t('questionBank.noAnswer') }}
            </p>
          </section>

          <section
            v-if="showQuestionAnswer"
            class="preview-section"
          >
            <h3>{{ t('questionBank.explanation') }}</h3>
            <AiMarkdownMessage
              :content="activeExplanationText || t('questionBank.noExplanation')"
              class="preview-markdown"
            />
          </section>

          <section
            v-if="questionTags(activeQuestion).length"
            class="preview-section"
          >
            <h3>{{ t('questionBank.tags') }}</h3>
            <div class="tag-list">
              <span
                v-for="tag in questionTags(activeQuestion)"
                :key="tag"
              >
                {{ tag }}
              </span>
            </div>
          </section>
        </section>

        <AiMarkdownMessage
          v-else
          :content="artifact.content"
          class="artifact-text"
        />
      </template>

      <template v-else-if="activeArtifactTab === 'overall'">
        <article class="artifact-report">
          <div class="report-kicker">
            <span>{{ artifact.messageType }}</span>
            <i />
          </div>
          <h1>{{ t('common.ai.studio.overviewTitle') }}</h1>
          <p>{{ artifactSummary }}</p>

          <div
            v-if="questions.length > 0"
            class="question-list"
          >
            <article
              v-for="(question, index) in questions"
              :key="index"
              class="question-card"
            >
              <span>Q{{ index + 1 }}</span>
              <AiMarkdownMessage
                :content="questionTitle(question)"
                class="question-title-markdown"
              />
              <AiMarkdownMessage
                v-if="questionContent(question)"
                :content="questionContent(question)"
                class="question-content-markdown"
              />
              <div
                v-if="questionOptions(question).length > 0"
                class="option-list"
              >
                <AiMarkdownMessage
                  v-for="(option, optionIndex) in questionOptions(question)"
                  :key="optionKey(option, optionIndex)"
                  :content="optionLabel(option, optionIndex)"
                  class="option-markdown"
                />
              </div>

              <div
                v-if="showQuestionAnswer"
                class="question-answer-preview"
              >
                <p>{{ t('questionBank.correctAnswer') }}</p>
                <div
                  v-if="answerItems(question).length"
                  class="question-answer-list"
                >
                  <AiMarkdownMessage
                    v-for="(answer, answerIndex) in answerItems(question)"
                    :key="answerIndex"
                    :content="answer"
                    class="answer-markdown"
                  />
                </div>
                <p
                  v-else
                  class="muted-text"
                >
                  {{ t('questionBank.noAnswer') }}
                </p>
              </div>

              <div
                v-if="showQuestionAnswer && explanationText(question)"
                class="question-answer-preview"
              >
                <p>{{ t('questionBank.explanation') }}</p>
                <AiMarkdownMessage
                  :content="explanationText(question)"
                  class="answer-markdown"
                />
              </div>
            </article>
          </div>

          <AiMarkdownMessage
            v-else
            :content="artifact.content"
            class="artifact-text"
          />
        </article>
      </template>

      <AiGenerationTracePanel
        v-else
        :message="traceMessage"
        embedded
      />
    </section>

    <section
      v-else
      class="empty-studio"
    >
      <PanelRight
        :size="30"
        stroke-width="1.5"
      />
      <h3>{{ t('common.ai.studio.emptyTitle') }}</h3>
      <p>{{ t('common.ai.studio.emptyDescription') }}</p>
    </section>

    <div
      v-if="exportingFormat"
      aria-live="polite"
      class="export-lock-overlay"
      role="status"
    >
      <div class="export-lock-panel">
        <FileDown
          :size="22"
          stroke-width="1.8"
        />
        <strong>{{ exportOverlayText }}</strong>
        <p>{{ t('common.ai.studio.exportLockedHint') }}</p>
      </div>
    </div>
  </aside>

  <Teleport to="body">
    <div
      v-if="showImportDialog"
      class="question-import-overlay"
      @click.self="closeImportDialog"
    >
      <section
        aria-modal="true"
        class="question-import-dialog"
        role="dialog"
      >
        <header class="question-import-header">
          <div>
            <span class="section-label">{{ t('common.ai.studio.importToBank') }}</span>
            <h2>{{ t('common.ai.studio.importDialogTitle') }}</h2>
          </div>
          <button
            :title="t('common.ai.studio.close')"
            class="btn-close"
            type="button"
            @click="closeImportDialog"
          >
            <X
              :size="16"
              stroke-width="1.8"
            />
          </button>
        </header>

        <div class="question-import-body">
          <div class="import-select-grid">
            <label class="import-field">
              <span>{{ t('common.ai.studio.importCourse') }}</span>
              <BaseSelect
                v-model="selectedImportCourseId"
                :disabled="importCoursesLoading"
                :options="importCourseOptions"
                :placeholder="t('common.ai.studio.importCoursePlaceholder')"
                class="import-select"
                min-width="100%"
                @change="handleImportCourseChanged"
              />
            </label>
            <label class="import-field">
              <span>{{ t('common.ai.studio.importBank') }}</span>
              <BaseSelect
                v-model="selectedImportBankId"
                :disabled="importBanksLoading || importQuestionBanks.length === 0"
                :options="importBankOptions"
                :placeholder="t('common.ai.studio.importBankPlaceholder')"
                class="import-select"
                min-width="100%"
              />
            </label>
          </div>

          <p
            v-if="importCoursesLoading || importBanksLoading"
            class="import-status-text"
          >
            {{ t('common.ai.studio.importLoadingTargets') }}
          </p>
          <p
            v-else-if="selectedImportCourseId && importQuestionBanks.length === 0"
            class="import-status-text"
          >
            {{ t('common.ai.studio.importNoBanks') }}
          </p>

          <div class="import-selection-bar">
            <label class="import-check-all">
              <input
                :checked="allImportQuestionsSelected"
                :disabled="importableQuestionItems.length === 0"
                type="checkbox"
                @change="handleToggleAllImportQuestions"
              >
              <span>{{ t('common.ai.studio.importSelectAll') }}</span>
            </label>
            <span>{{ t('common.ai.studio.importSelectedCount', {selected: selectedImportQuestionCount, total: importableQuestionItems.length}) }}</span>
          </div>

          <div class="import-question-list">
            <label
              v-for="item in importableQuestionItems"
              :key="item.key"
              class="import-question-row"
            >
              <input
                v-model="selectedImportQuestionKeys"
                :value="item.key"
                type="checkbox"
              >
              <span>Q{{ item.index + 1 }}</span>
              <strong>{{ questionTitle(item.question) }}</strong>
              <small>{{ questionTypeName(item.question) }} / {{ difficultyName(item.question) }}</small>
            </label>
          </div>
        </div>

        <footer class="question-import-footer">
          <button
            class="btn-secondary"
            type="button"
            @click="closeImportDialog"
          >
            {{ t('common.confirmDialog.cancel') }}
          </button>
          <button
            :disabled="!canSubmitImport"
            class="btn-primary"
            type="button"
            @click="handleImportQuestions"
          >
            {{ importingQuestions ? t('common.ai.studio.importing') : t('common.ai.studio.importSelected') }}
          </button>
        </footer>
      </section>
    </div>
  </Teleport>
</template>

<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import {BookPlus, ChevronLeft, ChevronRight, FileDown, FileText, PanelRight, X} from 'lucide-vue-next'

import {exportGeneratedArtifact} from '@/features/ai/api/ai'
import AiGenerationTracePanel from '@/features/ai/components/AiGenerationTracePanel.vue'
import AiMarkdownMessage from '@/features/ai/components/AiMarkdownMessage.vue'
import {useAiStore} from '@/features/ai/stores/ai'
import type {AiAgentMode, ChatMessage, GenerationRequest} from '@/features/ai/types/ai'
import {getTeacherCourses} from '@/features/course/api/course'
import type {Course} from '@/features/course/types/course'
import {batchCreateQuestions, getCourseQuestionBanks, getQuestionBank} from '@/features/question-bank/api/questionBank'
import type {QuestionBank, QuestionImportRequest} from '@/features/question-bank/types/questionBank'
import BaseNumberStepper from '@/shared/components/BaseNumberStepper.vue'
import BaseSelect from '@/shared/components/BaseSelect.vue'
import {confirmDialog} from '@/shared/composables/useConfirmDialog'
import {notify} from '@/shared/composables/useGlobalNotification'

type PayloadRecord = Record<string, unknown>
type SelectValue = string | number | undefined
type SelectOption = { label: string; value: SelectValue }
type ArtifactTab = 'single' | 'overall' | 'trace'
type ExportFormat = 'pdf' | 'docx'

const props = defineProps<{
  mode?: Exclude<AiAgentMode, 'CHAT'>
  generation: GenerationRequest
  artifactTab?: ArtifactTab
  traceMessage?: ChatMessage | null
}>()

const emit = defineEmits<{
  close: []
  generate: []
  'update:artifactTab': [value: ArtifactTab]
  'update:generation': [value: GenerationRequest]
}>()

const {t} = useI18n()
const router = useRouter()
const aiStore = useAiStore()
const artifact = computed(() => {
  const selectedMessage = props.traceMessage
  if (selectedMessage?.messageType && selectedMessage.messageType !== 'TEXT') {
    return selectedMessage
  }

  return aiStore.latestArtifact
})
const traceMessage = computed(() => props.traceMessage || artifact.value)
const payload = computed(() => (artifact.value?.payload || {}) as PayloadRecord)
const questions = computed(() => listValue(payload.value.questions))
const questionCount = computed(() => questions.value.length)
const generationModel = computed({
  get: () => props.generation,
  set: (value: GenerationRequest) => emit('update:generation', value),
})
const activeArtifactTab = computed({
  get: () => props.artifactTab || 'single',
  set: (value: ArtifactTab) => emit('update:artifactTab', value),
})
const activeQuestionIndex = ref(0)
const showQuestionAnswer = ref(true)
const exportingFormat = ref<ExportFormat | null>(null)
const knowledgePointsText = ref('')
const abilityGoalsText = ref('')
const showImportDialog = ref(false)
const importCourses = ref<Course[]>([])
const importQuestionBanks = ref<QuestionBank[]>([])
const importCoursesLoading = ref(false)
const importBanksLoading = ref(false)
const importingQuestions = ref(false)
const selectedImportCourseId = ref<SelectValue>()
const selectedImportBankId = ref<SelectValue>()
const selectedImportQuestionKeys = ref<string[]>([])

const questionTypeOptions = computed<SelectOption[]>(() => [
  {label: t('common.ai.studio.questionTypes.singleChoice'), value: 0},
  {label: t('common.ai.studio.questionTypes.multipleChoice'), value: 1},
  {label: t('common.ai.studio.questionTypes.trueFalse'), value: 2},
  {label: t('common.ai.studio.questionTypes.fillBlank'), value: 3},
  {label: t('common.ai.studio.questionTypes.shortAnswer'), value: 4},
  {label: t('common.ai.studio.questionTypes.mixed'), value: 5},
])

const difficultyOptions = computed<SelectOption[]>(() => [
  {label: t('common.ai.studio.difficulties.random'), value: 0},
  {label: t('common.ai.studio.difficulties.easy'), value: 1},
  {label: t('common.ai.studio.difficulties.medium'), value: 2},
  {label: t('common.ai.studio.difficulties.hard'), value: 3},
])

const totalScoreValue = numericField('totalScore', 100)
const totalEstimatedTimeValue = numericField('totalEstimatedTime', 60)
const questionCountValue = numericField('questionCount', 5)
const scorePerQuestionValue = numericField('scorePerQuestion', 0)
const questionTypeValue = selectField('questionType')
const difficultyValue = selectField('difficulty')

const panelTitle = computed(() => props.mode === 'PAPER'
  ? t('common.ai.studio.paperTitle')
  : props.mode === 'QUESTION' ? t('common.ai.studio.questionTitle') : t('common.ai.studio.artifactTitle'))
const generateButtonText = computed(() => {
  if (props.mode === 'QUESTION') return t('common.ai.studio.startQuestion')
  if (props.mode === 'PAPER') return t('common.ai.studio.startPaper')
  return panelTitle.value
})
const formHint = computed(() => props.mode === 'PAPER'
  ? t('common.ai.studio.paperHint')
  : t('common.ai.studio.questionHint'))
const requirementPlaceholder = computed(() => props.mode === 'PAPER'
  ? t('common.ai.studio.paperRequirementPlaceholder')
  : t('common.ai.studio.questionRequirementPlaceholder'))
const artifactSummary = computed(() => {
  if (questionCount.value > 0) return t('common.ai.studio.artifactSummary', {count: questionCount.value})
  return t('common.ai.studio.artifactSummaryFallback')
})
const activeQuestion = computed(() => questions.value[activeQuestionIndex.value] || null)
const activeQuestionOptions = computed(() => activeQuestion.value ? questionOptions(activeQuestion.value) : [])
const activeAnswerItems = computed(() => activeQuestion.value ? answerItems(activeQuestion.value) : [])
const activeExplanationText = computed(() => activeQuestion.value ? explanationText(activeQuestion.value) : '')
const activeQuestionIsObjective = computed(() => {
  if (!activeQuestion.value) return false
  const type = numberValue(activeQuestion.value.questionType ?? activeQuestion.value.type)
  return type !== null && type >= 0 && type <= 2
})
const showExportActions = computed(() => Boolean(
  artifact.value
  && isExportableMessage(artifact.value),
))
const canExportArtifact = computed(() => Boolean(
  showExportActions.value
  && artifact.value
  && !artifact.value.pending
  && !artifact.value.failed
  && !artifact.value.terminated
  && questionCount.value > 0
  && aiStore.activeConversationId,
))
const canImportArtifact = computed(() => Boolean(
  showExportActions.value
  && artifact.value
  && !artifact.value.pending
  && !artifact.value.failed
  && !artifact.value.terminated
  && questionCount.value > 0,
))
const exportOverlayText = computed(() => exportingFormat.value === 'docx'
  ? t('common.ai.studio.exportingWord')
  : t('common.ai.studio.exportingPdf'))
const importableQuestionItems = computed(() => questions.value.map((question, index) => ({
  key: importQuestionKey(question, index),
  question,
  index,
})))
const selectedImportQuestionCount = computed(() => selectedImportQuestionKeys.value.length)
const allImportQuestionsSelected = computed(() =>
  importableQuestionItems.value.length > 0
  && selectedImportQuestionKeys.value.length === importableQuestionItems.value.length,
)
const importCourseOptions = computed<SelectOption[]>(() =>
  importCourses.value.map(course => ({label: course.title, value: course.id})),
)
const importBankOptions = computed<SelectOption[]>(() =>
  importQuestionBanks.value.map(bank => ({label: bank.bankName, value: bank.id})),
)
const canSubmitImport = computed(() => Boolean(
  selectedString(selectedImportBankId.value)
  && selectedImportQuestionKeys.value.length > 0
  && !importingQuestions.value
  && !importCoursesLoading.value
  && !importBanksLoading.value,
))

watch(() => artifact.value?.id, () => {
  activeQuestionIndex.value = 0
  showQuestionAnswer.value = true
  resetImportState()
})

watch(questionCount, (count) => {
  if (count <= 0) {
    activeQuestionIndex.value = 0
  } else if (activeQuestionIndex.value >= count) {
    activeQuestionIndex.value = count - 1
  }
})

watch(knowledgePointsText, (value) => {
  generationModel.value = {
    ...generationModel.value,
    knowledgePoints: normalizeListInput(value),
  }
})

watch(abilityGoalsText, (value) => {
  generationModel.value = {
    ...generationModel.value,
    abilityGoals: normalizeListInput(value),
  }
})

function textValue(value: unknown) {
  return typeof value === 'string' ? value : ''
}

function listValue(value: unknown) {
  return Array.isArray(value) ? value as PayloadRecord[] : []
}

function optionLabel(option: PayloadRecord, index: number) {
  const label = optionLabelText(option, index)
  const content = optionContentText(option)
  const marker = isCorrectOption(option) ? t('common.ai.studio.answerMarker') : ''
  return `${label}. ${content || t('common.ai.studio.optionFallback')}${marker}`
}

function optionKey(option: PayloadRecord, index: number) {
  return textValue(option.id) || textValue(option.optionLabel) || textValue(option.label) || index
}

function importQuestionKey(question: PayloadRecord, index: number) {
  return `${textValue(question.draftId) || textValue(question.id) || index}-${index}`
}

function questionTitle(question: PayloadRecord) {
  return textValue(question.questionTitle) || textValue(question.title) || t('common.ai.studio.unnamedQuestion')
}

function questionContent(question: PayloadRecord) {
  return textValue(question.questionContent) || textValue(question.content) || questionTitle(question)
}

function questionOptions(question: PayloadRecord) {
  return listValue(question.options)
}

function questionAnswers(question: PayloadRecord) {
  return listValue(question.answers)
}

function questionTags(question: PayloadRecord) {
  const tags = question.tags
  if (!Array.isArray(tags)) return []
  return tags.map(item => displayValue(item)).filter(Boolean)
}

function questionTypeName(question: PayloadRecord) {
  const type = numberValue(question.questionType ?? question.type)
  const labels: Record<number, string> = {
    0: t('questionBank.typeSingle'),
    1: t('questionBank.typeMultiple'),
    2: t('questionBank.typeJudge'),
    3: t('questionBank.typeBlank'),
    4: t('questionBank.typeShort'),
    5: t('common.ai.studio.questionTypes.mixed'),
  }
  return type === null
    ? textValue(question.questionTypeName) || textValue(question.typeName) || t('questionBank.unknown')
    : labels[type] || t('questionBank.unknown')
}

function difficultyName(question: PayloadRecord) {
  const difficulty = numberValue(question.difficulty)
  const labels: Record<number, string> = {
    0: t('common.ai.studio.difficulties.random'),
    1: t('questionBank.difficultyEasy'),
    2: t('questionBank.difficultyMedium'),
    3: t('questionBank.difficultyHard'),
  }
  return difficulty === null
    ? textValue(question.difficultyName) || t('questionBank.unknown')
    : labels[difficulty] || t('questionBank.unknown')
}

function scoreText(question: PayloadRecord) {
  return displayValue(question.score)
}

function optionScoreText(option: PayloadRecord) {
  return displayValue(option.score)
}

function estimatedTimeText(question: PayloadRecord) {
  return displayValue(question.estimatedTime)
}

function optionLabelText(option: PayloadRecord, index: number) {
  return textValue(option.optionLabel) || textValue(option.label) || String.fromCharCode(65 + index)
}

function optionContentText(option: PayloadRecord) {
  return textValue(option.optionContent) || textValue(option.content) || textValue(option.answerContent)
}

function optionExplanationText(option: PayloadRecord) {
  return textValue(option.explanation)
}

function isCorrectOption(option: PayloadRecord) {
  return option.isCorrect === 1 || option.isCorrect === true || option.correct === 1 || option.correct === true
}

function answerItems(question: PayloadRecord) {
  const textAnswers = questionAnswers(question)
    .map(answer => textValue(answer.answerContent) || textValue(answer.content))
    .filter(Boolean)
  const optionAnswers = questionOptions(question)
    .map((option, index) => isCorrectOption(option) ? `${optionLabelText(option, index)}. ${optionContentText(option)}` : '')
    .filter(Boolean)

  return textAnswers.length ? textAnswers : optionAnswers
}

function explanationText(question: PayloadRecord) {
  const questionExplanation = textValue(question.explanation) || textValue(question.answerExplanation)
  const optionExplanations = questionOptions(question)
    .map((option, index) => {
      const explanation = optionExplanationText(option)
      return explanation ? `${optionLabelText(option, index)}. ${explanation}` : ''
    })
    .filter(Boolean)
  const answerExplanation = questionAnswers(question)
    .find(answer => textValue(answer.explanation))?.explanation

  return [questionExplanation, ...optionExplanations, textValue(answerExplanation)]
    .filter(Boolean)
    .join('\n\n')
}

function displayValue(value: unknown) {
  if (value === null || value === undefined || value === '') return ''
  return String(value)
}

function numberValue(value: unknown) {
  if (typeof value === 'number' && Number.isFinite(value)) return value
  if (typeof value === 'string' && value.trim() !== '') {
    const parsed = Number(value)
    return Number.isFinite(parsed) ? parsed : null
  }
  return null
}

function selectedString(value: SelectValue) {
  return typeof value === 'string' ? value : ''
}

function stringListValue(value: unknown) {
  if (!Array.isArray(value)) return []
  return value.map(item => displayValue(item)).filter(Boolean)
}

function decimalValue(value: unknown, fallback: number) {
  const parsed = numberValue(value)
  return parsed === null ? fallback : Math.max(0, parsed)
}

function integerValue(value: unknown, fallback: number) {
  const parsed = numberValue(value)
  return parsed === null ? fallback : Math.trunc(parsed)
}

function normalizeImportQuestionType(question: PayloadRecord) {
  const value = numberValue(question.questionType ?? question.type)
  if (value !== null && value >= 0 && value <= 4) return Math.trunc(value)
  return questionOptions(question).length > 0 ? 0 : 4
}

function normalizeImportDifficulty(question: PayloadRecord) {
  const value = numberValue(question.difficulty)
  if (value !== null && value >= 1 && value <= 3) return Math.trunc(value)
  return 2
}

function numericField(key: keyof GenerationRequest, fallback: number) {
  return computed({
    get: () => {
      const value = generationModel.value[key]
      return typeof value === 'number' ? value : fallback
    },
    set: (value: number) => {
      generationModel.value = {
        ...generationModel.value,
        [key]: value,
      }
    },
  })
}

function selectField(key: keyof GenerationRequest) {
  return computed({
    get: () => {
      const value = generationModel.value[key]
      return typeof value === 'number' || typeof value === 'string' ? value : undefined
    },
    set: (value: SelectValue) => {
      generationModel.value = {
        ...generationModel.value,
        [key]: value,
      }
    },
  })
}

function normalizeListInput(value: string) {
  const items = value
    .split(/[\n,，]/)
    .map(item => item.trim())
    .filter(Boolean)

  return items.length ? items : null
}
function resetImportState() {
  showImportDialog.value = false
  importCourses.value = []
  importQuestionBanks.value = []
  importCoursesLoading.value = false
  importBanksLoading.value = false
  importingQuestions.value = false
  selectedImportCourseId.value = undefined
  selectedImportBankId.value = undefined
  selectedImportQuestionKeys.value = []
}

async function openImportDialog() {
  if (!canImportArtifact.value) return
  showImportDialog.value = true
  importCourses.value = []
  importQuestionBanks.value = []
  selectedImportCourseId.value = undefined
  selectedImportBankId.value = undefined
  selectedImportQuestionKeys.value = []
  await loadImportTargets()
}

function closeImportDialog() {
  if (importingQuestions.value) return
  showImportDialog.value = false
}

async function loadImportTargets() {
  importCoursesLoading.value = true
  try {
    const contextBankId = aiStore.context.questionBankId || ''
    const contextBank = contextBankId ? await loadContextBank(contextBankId) : null
    const preferredCourseId = aiStore.context.courseId || contextBank?.courseId || ''
    const response = await getTeacherCourses(1, 100)
    importCourses.value = response.records || []

    const courseId = importCourses.value.some(course => course.id === preferredCourseId)
      ? preferredCourseId
      : importCourses.value[0]?.id || ''
    selectedImportCourseId.value = courseId || undefined

    if (courseId) {
      await loadImportQuestionBanks(courseId, contextBankId)
    }
  } catch {
    notify.error(t('common.ai.studio.importLoadFailed'))
  } finally {
    importCoursesLoading.value = false
  }
}

async function loadContextBank(questionBankId: string) {
  try {
    return await getQuestionBank(questionBankId)
  } catch {
    return null
  }
}

async function handleImportCourseChanged() {
  const courseId = selectedString(selectedImportCourseId.value)
  selectedImportBankId.value = undefined
  importQuestionBanks.value = []
  if (courseId) {
    await loadImportQuestionBanks(courseId)
  }
}

async function loadImportQuestionBanks(courseId: string, preferredBankId = '') {
  importBanksLoading.value = true
  try {
    const banks = await getCourseQuestionBanks(courseId)
    importQuestionBanks.value = banks
    const bankId = banks.some(bank => bank.id === preferredBankId)
      ? preferredBankId
      : banks[0]?.id || ''
    selectedImportBankId.value = bankId || undefined
  } catch {
    notify.error(t('common.ai.studio.importLoadFailed'))
  } finally {
    importBanksLoading.value = false
  }
}

function handleToggleAllImportQuestions(event: Event) {
  const checked = event.target instanceof HTMLInputElement && event.target.checked
  selectedImportQuestionKeys.value = checked ? importableQuestionItems.value.map(item => item.key) : []
}

async function handleImportQuestions() {
  const questionBankId = selectedString(selectedImportBankId.value)
  if (!questionBankId || !canSubmitImport.value) return

  const questionsToImport = importableQuestionItems.value
    .filter(item => selectedImportQuestionKeys.value.includes(item.key))
    .map(item => toImportQuestionRequest(item.question))

  if (questionsToImport.length === 0) return

  importingQuestions.value = true
  let importedCount = 0
  try {
    const response = await batchCreateQuestions({
      questionBankId,
      questions: questionsToImport,
    })
    importedCount = response.importedCount
    showImportDialog.value = false
  } catch {
    notify.error(t('common.ai.studio.importFailed'))
    return
  } finally {
    importingQuestions.value = false
  }

  const shouldViewBank = await confirmDialog({
    title: t('common.ai.studio.importCompleteTitle'),
    message: t('common.ai.studio.importCompleteMessage', {count: importedCount}),
    cancelText: t('common.ai.studio.importStayHere'),
    confirmText: t('common.ai.studio.importViewBank'),
  })
  if (shouldViewBank) {
    await router.push({name: 'question-bank-detail', params: {id: questionBankId}})
  }
}

function toImportQuestionRequest(question: PayloadRecord): QuestionImportRequest {
  const questionType = normalizeImportQuestionType(question)
  const score = decimalValue(question.score, 0)
  return {
    questionTitle: questionTitle(question),
    questionContent: questionContent(question),
    questionType,
    difficulty: normalizeImportDifficulty(question),
    score,
    estimatedTime: Math.max(1, integerValue(question.estimatedTime, 1)),
    tags: questionTags(question),
    imageUrls: stringListValue(question.imageUrls),
    allowPartialCredit: questionType === 1 ? 1 : 0,
    options: questionType <= 2 ? importOptions(question) : [],
    answers: importAnswers(question, score),
  }
}

function importOptions(question: PayloadRecord) {
  return questionOptions(question)
    .map((option, index) => ({
      optionLabel: optionLabelText(option, index),
      optionContent: optionContentText(option),
      isCorrect: isCorrectOption(option) ? 1 : 0,
      score: decimalValue(option.score, 0),
      imageUrls: stringListValue(option.imageUrls),
      explanation: textValue(option.explanation) || undefined,
    }))
    .filter(option => option.optionContent)
}

function importAnswers(question: PayloadRecord, score: number) {
  const explicitAnswers = questionAnswers(question)
    .map((answer, index) => ({
      answerContent: textValue(answer.answerContent) || textValue(answer.content),
      explanation: textValue(answer.explanation) || undefined,
      score: decimalValue(answer.score, score),
      sortOrder: Math.max(1, integerValue(answer.sortOrder, index + 1)),
    }))
    .filter(answer => answer.answerContent)

  if (explicitAnswers.length > 0) return explicitAnswers

  return questionOptions(question)
    .map((option, index) => isCorrectOption(option)
      ? {
          answerContent: `${optionLabelText(option, index)}. ${optionContentText(option)}`,
          explanation: textValue(option.explanation) || undefined,
          score,
          sortOrder: 1,
        }
      : null)
    .filter((answer): answer is NonNullable<typeof answer> => answer != null && Boolean(answer.answerContent))
}

function isExportableMessage(message: ChatMessage) {
  return message.messageType === 'QUESTION_SET' || message.messageType === 'PAPER'
}

async function exportArtifact(format: ExportFormat) {
  const conversationId = aiStore.activeConversationId
  if (!artifact.value || !conversationId || !canExportArtifact.value || exportingFormat.value) return

  exportingFormat.value = format
  try {
    const result = await exportGeneratedArtifact(conversationId, artifact.value.id, {
      format,
      includeAnswers: showQuestionAnswer.value,
    })
    downloadBlob(result.blob, result.filename)
    notify.success(t('common.ai.studio.exportSuccess'))
  } catch (error) {
    notify.error(error instanceof Error && error.message ? error.message : t('common.ai.studio.exportFailed'))
  } finally {
    exportingFormat.value = null
  }
}

function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  document.body.appendChild(anchor)
  anchor.click()
  document.body.removeChild(anchor)
  URL.revokeObjectURL(url)
}
</script>

<style scoped>
.ai-studio-panel {
  position: relative;
  display: flex;
  min-width: 0;
  min-height: 0;
  flex-direction: column;
  overflow: hidden;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  color: var(--color-on-surface);
}

.export-lock-overlay {
  position: absolute;
  z-index: 20;
  inset: 0;
  display: grid;
  place-items: center;
  padding: 24px;
  background: color-mix(in srgb, var(--color-surface-card) 74%, transparent);
  backdrop-filter: blur(6px);
}

.export-lock-panel {
  display: grid;
  width: min(300px, 100%);
  justify-items: center;
  gap: 10px;
  padding: 22px;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  color: var(--color-on-surface);
  text-align: center;
  box-shadow: 0 18px 54px rgba(0, 0, 0, 0.24);
}

.export-lock-panel strong {
  font-family: var(--font-heading);
  font-size: 22px;
  font-weight: 600;
  line-height: 1.18;
}

.export-lock-panel p {
  margin: 0;
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 13px;
  line-height: 1.5;
}

.studio-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 48px;
  padding: 0 0 0 28px;
  border-bottom: 1px solid var(--color-outline-light);
}

.studio-header.has-artifact-tabs {
  gap: 0;
  padding-left: 0;
}

.report-kicker span,
.question-card span,
.generation-form span,
.generation-form p,
.empty-studio p {
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 11px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.studio-header h2 {
  margin: 2px 0 0;
  font-family: var(--font-heading);
  font-size: 24px;
  font-weight: 600;
  line-height: 1.12;
}

.studio-header .btn-close {
  display: grid;
  width: 48px;
  height: 48px;
  align-self: stretch;
  place-items: center;
  background: transparent;
  border: 0;
  border-radius: 0;
  color: var(--color-on-surface);
  cursor: pointer;
  transition: background 0.2s ease, color 0.2s ease;
}

.studio-header .btn-close:hover {
  background: var(--color-on-surface);
  color: var(--color-surface-card);
}

.generation-form {
  display: grid;
  gap: 18px;
  min-height: 0;
  overflow-y: auto;
  padding: 28px;
}

.generation-form p {
  margin: 0;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--color-outline-light);
  font-size: 13px;
  line-height: 1.55;
  text-transform: none;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.generation-form label {
  display: grid;
  gap: var(--space-xs);
}

.generation-form label > span {
  font-size: 12px;
}

.form-input,
.form-textarea {
  width: 100%;
  background: var(--color-surface-container);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  color: var(--color-on-surface);
  font-family: var(--font-body);
  font-size: 16px;
  outline: none;
  transition: border-color 0.2s ease, background 0.2s ease;
}

.form-input {
  min-height: 40px;
  padding: 0 var(--space-sm);
}

.form-input::placeholder,
.form-textarea::placeholder {
  color: var(--color-muted);
}

.form-input:focus,
.form-textarea:focus {
  background: var(--color-surface-card);
  border-color: var(--color-on-surface);
}

.form-textarea {
  min-height: 80px;
  resize: vertical;
  padding: var(--space-sm);
  line-height: 1.5;
}

.form-field :deep(.base-select) {
  width: 100%;
  min-width: 0;
}

.form-field :deep(.base-select-trigger) {
  min-height: 40px;
  padding: 0;
  background: transparent;
  border: 0;
  border-bottom: 1px solid var(--color-outline-light);
  border-radius: 0;
  font-family: var(--font-body);
  font-size: 16px;
}

.form-field :deep(.base-select-trigger:hover),
.form-field :deep(.base-select.open .base-select-trigger) {
  background: transparent;
  border-bottom-color: var(--color-outline-variant);
}

.form-field :deep(.base-select-trigger:focus-visible),
.form-field :deep(.base-select.open .base-select-trigger) {
  box-shadow: none;
}

.form-field :deep(.base-select-menu) {
  border-radius: var(--radius-sm);
}

.form-field :deep(.base-select-option) {
  font-size: 15px;
}

.form-field :deep(.base-number-stepper) {
  min-height: 40px;
  grid-template-columns: 28px minmax(0, 1fr) 28px;
  background: transparent;
  border: 0;
  border-bottom: 1px solid var(--color-outline-light);
  border-radius: 0;
}

.form-field :deep(.base-number-stepper:focus-within) {
  border-bottom-color: var(--color-on-surface);
}

.form-field :deep(.base-number-stepper-input) {
  min-height: 38px;
  padding: 0;
  color: var(--color-on-surface);
  font-size: 16px;
}

.form-field :deep(.base-number-stepper-button) {
  height: 38px;
  border-radius: 0;
}

.wide-field {
  grid-column: 1 / -1;
}

.panel-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding-top: 14px;
  border-top: 1px solid var(--color-outline-light);
}

.panel-footer button {
  min-height: 38px;
  padding: 0 15px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  font-family: var(--font-label);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  transition: background 0.2s ease, color 0.2s ease, transform 0.2s ease;
}

.panel-footer .btn-secondary {
  background: transparent;
  border: 1px solid var(--color-outline-light);
  color: var(--color-on-surface);
}

.panel-footer .btn-primary {
  background: var(--color-primary);
  border: 1px solid var(--color-primary);
  color: var(--color-on-primary);
}

.panel-footer .btn-secondary:hover {
  background: var(--color-surface-container-high);
  border-color: var(--color-outline-variant);
}

.panel-footer .btn-primary:hover {
  background: var(--color-primary-soft);
}

.panel-footer button:active {
  transform: scale(0.98);
}

.artifact-body {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  gap: 18px;
  overflow-y: auto;
  padding: 34px clamp(24px, 6vw, 72px);
}

.artifact-body.is-single-preview {
  gap: 0;
  padding: 0;
}

.artifact-toolbar {
  display: grid;
  min-width: 0;
  width: min(100%, 480px);
  flex: 1;
  align-items: stretch;
}

.artifact-toolbar.has-answer-toggle {
  width: min(100%, 640px);
  grid-template-columns: minmax(0, 3fr) minmax(0, 1fr);
}

.artifact-toolbar.has-question-stepper {
  width: min(100%, 800px);
  grid-template-columns: minmax(0, 3fr) minmax(0, 1fr) minmax(0, 1fr);
}

.artifact-toolbar.has-export-actions {
  width: min(100%, 760px);
  grid-template-columns: minmax(0, 3fr) minmax(0, 1fr) minmax(104px, auto);
}

.artifact-toolbar.has-question-stepper.has-export-actions {
  width: min(100%, 940px);
  grid-template-columns: minmax(0, 3fr) minmax(0, 1fr) minmax(0, 1fr) minmax(104px, auto);
}

.artifact-tabs {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  width: 100%;
  min-height: 48px;
  border: 1px solid var(--color-outline-light);
  background: var(--color-surface-container-highest, #161616);
}

.artifact-toolbar.has-answer-toggle .artifact-tabs {
  border-right: 0;
}

.artifact-tabs button {
  min-width: 0;
  padding: 0 14px;
  background: transparent;
  border: 0;
  border-right: 1px solid var(--color-outline-light);
  color: var(--color-muted);
  cursor: pointer;
  font-family: var(--font-label);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0;
  transition: background 0.18s ease, color 0.18s ease;
}

.artifact-tabs button:last-child {
  border-right: 0;
}

.artifact-tabs button:hover,
.artifact-tabs button.active {
  background: var(--color-on-surface);
  color: var(--color-surface-card);
}

.answer-toggle,
.question-stepper,
.export-actions {
  display: grid;
  min-width: 0;
  min-height: 48px;
  overflow: hidden;
  background: var(--color-surface-container-highest, #161616);
  border: 1px solid var(--color-outline-light);
  border-radius: 0;
}

.answer-toggle {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.question-stepper {
  grid-template-columns: 40px minmax(0, 1fr) 40px;
  border-left: 0;
}

.export-actions {
  grid-template-columns: repeat(3, minmax(48px, 1fr));
  border-left: 0;
}

.answer-toggle button,
.question-stepper button,
.export-actions button {
  min-width: 0;
  padding: 0 8px;
  background: transparent;
  border: 0;
  color: var(--color-muted);
  cursor: pointer;
  font-family: var(--font-label);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0;
  transition: background 0.18s ease, color 0.18s ease;
}

.question-stepper span {
  display: grid;
  min-width: 0;
  place-items: center;
  background: var(--color-on-surface);
  border-right: 1px solid var(--color-outline-light);
  border-left: 1px solid var(--color-outline-light);
  color: var(--color-surface-card);
  font-family: var(--font-label);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0;
  white-space: nowrap;
}

.answer-toggle button + button {
  border-left: 1px solid var(--color-outline-light);
}

.export-actions button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
}

.export-actions button + button {
  border-left: 1px solid var(--color-outline-light);
}

.answer-toggle button:hover,
.answer-toggle button.active,
.question-stepper button:hover:not(:disabled),
.export-actions button:hover:not(:disabled) {
  background: var(--color-on-surface);
  color: var(--color-surface-card);
}

.question-stepper button:disabled,
.export-actions button:disabled {
  cursor: not-allowed;
  opacity: 0.38;
}

.answer-toggle button:focus-visible,
.question-stepper button:focus-visible,
.export-actions button:focus-visible {
  outline: 2px solid color-mix(in srgb, var(--color-primary) 28%, transparent);
  outline-offset: -2px;
}

.answer-toggle:focus-within {
  border-color: var(--color-primary);
}

.artifact-report {
  display: grid;
  gap: 22px;
}

.report-kicker {
  display: flex;
  align-items: center;
  gap: 14px;
}

.report-kicker i {
  display: block;
  height: 1px;
  flex: 1;
  background: var(--color-outline);
}

.artifact-report h1 {
  max-width: 9em;
  margin: 0;
  font-family: var(--font-heading);
  font-size: clamp(38px, 5vw, 72px);
  font-weight: 700;
  line-height: 0.96;
  text-wrap: balance;
}

.artifact-report > p {
  max-width: 62ch;
  margin: 0;
  color: var(--color-on-surface-variant);
  font-family: var(--font-body);
  font-size: 15px;
  line-height: 1.7;
}

.generated-question-preview {
  display: flex;
  min-height: 100%;
  flex: 1;
  flex-direction: column;
  overflow: hidden;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
}

.artifact-body.is-single-preview .generated-question-preview {
  border: 0;
  border-radius: 0;
  overflow: visible;
}

.preview-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  padding: 20px;
  border-bottom: 1px solid var(--color-outline-light);
}

.section-label,
.preview-section h3,
.question-answer-preview > p {
  margin: 0;
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 12px;
  font-weight: 400;
  letter-spacing: 0.08em;
  text-transform: uppercase;
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

.preview-meta,
.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.preview-meta {
  padding: 14px 20px;
  border-bottom: 1px solid var(--color-outline-light);
}

.preview-meta span,
.tag-list span {
  display: inline-flex;
  min-height: 26px;
  align-items: center;
  padding: 0 9px;
  background: var(--color-surface-container);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  color: var(--color-on-surface-variant);
  font-family: var(--font-label);
  font-size: 12px;
  line-height: 1;
}

.preview-section {
  padding: 18px 20px;
  border-bottom: 1px solid var(--color-outline-light);
}

.preview-section h3 {
  margin-bottom: 10px;
}

.preview-option-list,
.answer-list,
.question-answer-list {
  display: grid;
  gap: 8px;
}

.preview-option-row {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr) max-content;
  gap: 10px;
  align-items: center;
  min-height: 44px;
  padding: 10px 12px;
  background: var(--color-surface-canvas);
  border: 1px solid var(--color-outline-light);
  color: var(--color-on-surface-variant);
}

.option-body {
  display: grid;
  min-width: 0;
  gap: 6px;
}

.preview-option-row.correct {
  background: var(--color-surface-container);
  border-color: var(--color-primary);
  color: var(--color-on-surface);
}

.option-score {
  display: inline-flex;
  min-height: 26px;
  align-items: center;
  justify-content: center;
  padding: 0 8px;
  background: var(--color-surface-container);
  border: 1px solid var(--color-outline-light);
  color: var(--color-on-surface);
  font-family: var(--font-label);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  line-height: 1;
  white-space: nowrap;
}

.option-label {
  display: grid;
  width: 26px;
  height: 26px;
  place-items: center;
  border: 1px solid var(--color-outline-light);
  color: var(--color-on-surface);
  font-family: var(--font-label);
  font-size: 12px;
}

.preview-markdown,
.answer-row,
.answer-markdown {
  max-width: none;
  margin: 0;
  color: var(--color-on-surface-variant);
  font-family: var(--font-body);
  font-size: 15px;
  line-height: 1.65;
}

.answer-row,
.answer-markdown {
  padding: 10px 12px;
  background: var(--color-surface-container);
  border: 1px solid var(--color-outline-light);
  color: var(--color-on-surface);
  font-size: 14px;
  line-height: 1.5;
}

.preview-markdown :deep(.ai-markdown-message),
.answer-row :deep(.ai-markdown-message),
.answer-markdown :deep(.ai-markdown-message) {
  max-width: none;
  margin: 0;
  font-size: inherit;
  line-height: inherit;
}

.preview-markdown :deep(p:last-child),
.answer-row :deep(p:last-child),
.answer-markdown :deep(p:last-child) {
  margin-bottom: 0;
}

.muted-text {
  margin: 0;
  color: var(--color-muted) !important;
  font-family: var(--font-body);
  font-size: 14px;
  line-height: 1.6;
}

.btn-outline {
  display: inline-flex;
  min-height: 36px;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 0 13px;
  background: transparent;
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  color: var(--color-on-surface);
  cursor: pointer;
  font-family: var(--font-label);
  font-size: 12px;
  transition: background 0.2s ease, border-color 0.2s ease, color 0.2s ease;
}

.btn-outline:hover:not(:disabled) {
  background: var(--color-surface-container-high);
  border-color: var(--color-outline);
}

.btn-outline:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.question-list {
  display: grid;
  gap: 0;
  border-top: 1px solid var(--color-outline);
}

.question-card {
  display: grid;
  gap: 8px;
  padding: 20px 0;
  border-bottom: 1px solid var(--color-outline-light);
}

.question-title-markdown {
  max-width: none;
  margin: 0;
  font-family: var(--font-heading);
  font-size: 24px;
  font-weight: 600;
  line-height: 1.15;
}

.question-content-markdown,
.option-list,
.option-markdown {
  max-width: none;
  margin: 0;
  color: var(--color-on-surface-variant);
  font-family: var(--font-body);
  font-size: 14px;
  line-height: 1.58;
}

.question-title-markdown :deep(.ai-markdown-message),
.question-content-markdown :deep(.ai-markdown-message),
.option-markdown :deep(.ai-markdown-message),
.artifact-text :deep(.ai-markdown-message) {
  max-width: none;
  margin: 0;
  font-size: inherit;
  line-height: inherit;
}

.question-title-markdown :deep(p:last-child),
.question-content-markdown :deep(p:last-child),
.option-markdown :deep(p:last-child),
.artifact-text :deep(p:last-child) {
  margin-bottom: 0;
}

.option-list {
  display: grid;
  gap: 6px;
  padding-top: 4px;
}

.question-answer-preview {
  display: grid;
  gap: 8px;
  padding-top: 8px;
}

.artifact-text {
  max-width: none;
  margin: 0;
  color: var(--color-on-surface);
  font-family: var(--font-body);
  font-size: 14px;
  line-height: 1.7;
}

.question-import-overlay {
  position: fixed;
  inset: 0;
  z-index: 2600;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(0, 0, 0, 0.38);
}

.question-import-dialog {
  display: flex;
  width: min(860px, 100%);
  max-height: min(760px, calc(100dvh - 48px));
  flex-direction: column;
  overflow: hidden;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  color: var(--color-on-surface);
  box-shadow: 0 24px 70px rgba(0, 0, 0, 0.26);
}

.question-import-header,
.question-import-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 20px;
  border-bottom: 1px solid var(--color-outline-light);
}

.question-import-header h2 {
  margin: 6px 0 0;
  font-family: var(--font-heading);
  font-size: 28px;
  font-weight: 500;
  line-height: 1.15;
}

.question-import-header .btn-close {
  display: grid;
  width: 40px;
  height: 40px;
  place-items: center;
  background: transparent;
  border: 1px solid var(--color-outline-light);
  color: var(--color-on-surface);
  cursor: pointer;
}

.question-import-body {
  display: grid;
  gap: 18px;
  min-height: 0;
  overflow-y: auto;
  padding: 20px;
}

.import-select-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.import-field {
  display: grid;
  gap: 8px;
}

.import-field > span,
.import-selection-bar,
.import-status-text {
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 12px;
  letter-spacing: 0;
}

.import-field :deep(.import-select .base-select-trigger) {
  min-height: 44px;
  border-radius: var(--radius-sm);
}

.import-selection-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  background: var(--color-surface-container);
  border: 1px solid var(--color-outline-light);
}

.import-check-all {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--color-on-surface);
  cursor: pointer;
}

.import-check-all input[type="checkbox"],
.import-question-row input[type="checkbox"] {
  width: 16px;
  height: 16px;
  margin: 0;
  accent-color: var(--color-primary);
  cursor: pointer;
}

.import-check-all input[type="checkbox"]:focus-visible,
.import-question-row input[type="checkbox"]:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 2px;
}

.import-check-all input[type="checkbox"]:disabled,
.import-question-row input[type="checkbox"]:disabled {
  accent-color: var(--color-outline);
  cursor: not-allowed;
}

.import-status-text {
  margin: 0;
}

.import-question-list {
  display: grid;
  gap: 8px;
}

.import-question-row {
  display: grid;
  grid-template-columns: 22px 42px minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
  min-height: 50px;
  padding: 10px 12px;
  background: var(--color-surface-canvas);
  border: 1px solid var(--color-outline-light);
  cursor: pointer;
}

.import-question-row span,
.import-question-row small {
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 12px;
}

.import-question-row strong {
  min-width: 0;
  overflow: hidden;
  color: var(--color-on-surface);
  font-family: var(--font-body);
  font-size: 14px;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.question-import-footer {
  justify-content: flex-end;
  border-top: 1px solid var(--color-outline-light);
  border-bottom: 0;
}

.empty-studio {
  display: grid;
  flex: 1;
  place-items: center;
  align-content: center;
  gap: 12px;
  padding: 34px;
  color: var(--color-muted);
  text-align: center;
}

.empty-studio h3 {
  max-width: 10em;
  margin: 0;
  color: var(--color-on-surface);
  font-family: var(--font-heading);
  font-size: 34px;
  font-weight: 700;
  line-height: 1;
  text-wrap: balance;
}

.empty-studio p {
  max-width: 33ch;
  margin: 0;
  line-height: 1.55;
  text-transform: none;
}

@media (max-width: 760px) {
  .studio-header.has-artifact-tabs {
    align-items: flex-start;
    padding: 0 10px 10px 0;
  }

  .artifact-toolbar {
    width: 100%;
  }

  .artifact-toolbar.has-question-stepper {
    grid-template-columns: minmax(0, 1fr) minmax(116px, auto);
  }

  .artifact-toolbar.has-export-actions {
    grid-template-columns: minmax(0, 1fr) minmax(156px, auto);
  }

  .artifact-toolbar.has-question-stepper.has-export-actions {
    grid-template-columns: minmax(0, 1fr) minmax(116px, auto) minmax(156px, auto);
  }

  .artifact-toolbar.has-question-stepper .artifact-tabs {
    grid-column: 1 / -1;
    border-right: 1px solid var(--color-outline-light);
    border-bottom: 0;
  }

  .artifact-tabs,
  .answer-toggle,
  .question-stepper,
  .export-actions {
    min-height: 44px;
  }

  .artifact-tabs button,
  .answer-toggle button,
  .question-stepper button,
  .question-stepper span,
  .export-actions button {
    padding: 0 8px;
    font-size: 11px;
    line-height: 1.15;
  }

  .answer-toggle,
  .question-stepper,
  .export-actions {
    margin-left: 0;
  }

  .form-grid {
    grid-template-columns: 1fr;
  }

  .artifact-body,
  .generation-form {
    padding: 22px;
  }

  .question-import-overlay {
    align-items: end;
    padding: 10px;
  }

  .question-import-dialog {
    width: 100%;
    max-height: calc(100dvh - 20px);
  }

  .import-select-grid {
    grid-template-columns: 1fr;
  }

  .import-question-row {
    grid-template-columns: 22px 36px minmax(0, 1fr);
  }

  .import-question-row small {
    grid-column: 3;
  }

  .preview-option-row {
    grid-template-columns: 34px minmax(0, 1fr);
    align-items: start;
  }

  .option-score {
    grid-column: 2;
    justify-self: start;
  }

  .option-body {
    grid-column: 2;
  }
}
</style>
