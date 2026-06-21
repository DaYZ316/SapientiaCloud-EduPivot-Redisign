<template>
  <aside class="ai-studio-panel">
    <header class="studio-header">
      <div>
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
            :min="1"
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
          {{ panelTitle }}
        </button>
      </div>
    </section>

    <section
      v-else-if="artifact"
      class="artifact-body"
    >
      <article class="artifact-report">
        <div class="report-kicker">
          <span>{{ artifact.messageType }}</span>
          <i />
        </div>
        <h1>{{ artifactTitle }}</h1>
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
            <h3>{{ textValue(question.questionTitle) || textValue(question.title) || t('common.ai.studio.unnamedQuestion') }}</h3>
            <p v-if="textValue(question.questionContent)">
              {{ textValue(question.questionContent) }}
            </p>
            <div
              v-if="listValue(question.options).length > 0"
              class="option-list"
            >
              <div
                v-for="(option, optionIndex) in listValue(question.options)"
                :key="optionIndex"
              >
                {{ optionLabel(option, optionIndex) }}
              </div>
            </div>
          </article>
        </div>

        <pre
          v-else
          class="artifact-text"
        >{{ artifact.content }}</pre>
      </article>

      <button
        class="copy-action"
        type="button"
        @click="copyArtifact"
      >
        {{ t('common.ai.studio.copyArtifact') }}
      </button>
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
  </aside>
</template>

<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {PanelRight, X} from 'lucide-vue-next'

import {useAiStore} from '@/features/ai/stores/ai'
import type {AiAgentMode, GenerationRequest} from '@/features/ai/types/ai'
import BaseNumberStepper from '@/shared/components/BaseNumberStepper.vue'
import BaseSelect from '@/shared/components/BaseSelect.vue'
import {notify} from '@/shared/composables/useGlobalNotification'

type PayloadRecord = Record<string, unknown>
type SelectValue = string | number | undefined
type SelectOption = { label: string; value: SelectValue }

const props = defineProps<{
  mode?: Exclude<AiAgentMode, 'CHAT'>
  generation: GenerationRequest
}>()

const emit = defineEmits<{
  close: []
  generate: []
  'update:generation': [value: GenerationRequest]
}>()

const {t} = useI18n()
const aiStore = useAiStore()
const artifact = computed(() => aiStore.latestArtifact)
const payload = computed(() => (artifact.value?.payload || {}) as PayloadRecord)
const questions = computed(() => listValue(payload.value.questions))
const questionCount = computed(() => questions.value.length)
const generationModel = computed({
  get: () => props.generation,
  set: (value: GenerationRequest) => emit('update:generation', value),
})
const knowledgePointsText = ref('')
const abilityGoalsText = ref('')

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
const scorePerQuestionValue = numericField('scorePerQuestion', 1)
const questionTypeValue = selectField('questionType')
const difficultyValue = selectField('difficulty')

const panelTitle = computed(() => props.mode === 'PAPER'
  ? t('common.ai.studio.paperTitle')
  : props.mode === 'QUESTION' ? t('common.ai.studio.questionTitle') : t('common.ai.studio.artifactTitle'))
const formHint = computed(() => props.mode === 'PAPER'
  ? t('common.ai.studio.paperHint')
  : t('common.ai.studio.questionHint'))
const requirementPlaceholder = computed(() => props.mode === 'PAPER'
  ? t('common.ai.studio.paperRequirementPlaceholder')
  : t('common.ai.studio.questionRequirementPlaceholder'))
const artifactTitle = computed(() =>
  textValue(payload.value.title) ||
  (artifact.value?.messageType === 'PAPER'
    ? t('common.ai.studio.paperDraftTitle')
    : t('common.ai.studio.questionDraftTitle')),
)
const artifactSummary = computed(() => {
  if (questionCount.value > 0) return t('common.ai.studio.artifactSummary', {count: questionCount.value})
  return t('common.ai.studio.artifactSummaryFallback')
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

async function copyArtifact() {
  if (!artifact.value) return

  const text = Object.keys(payload.value).length > 0
    ? JSON.stringify(payload.value, null, 2)
    : artifact.value.content
  try {
    await navigator.clipboard.writeText(text)
    notify.success(t('common.ai.studio.copied'))
  } catch {
    notify.error(t('common.ai.studio.copyFailed'))
  }
}

function textValue(value: unknown) {
  return typeof value === 'string' ? value : ''
}

function listValue(value: unknown) {
  return Array.isArray(value) ? value as PayloadRecord[] : []
}

function optionLabel(option: PayloadRecord, index: number) {
  const label = textValue(option.label) || String.fromCharCode(65 + index)
  const content = textValue(option.content) || textValue(option.answerContent)
  const marker = option.correct === true || option.isCorrect === 1 ? t('common.ai.studio.answerMarker') : ''
  return `${label}. ${content || t('common.ai.studio.optionFallback')}${marker}`
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
</script>

<style scoped>
.ai-studio-panel {
  display: flex;
  min-width: 0;
  min-height: 0;
  flex-direction: column;
  overflow: hidden;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  color: var(--color-on-surface);
}

.studio-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 48px;
  padding: 0 18px 0 28px;
  border-bottom: 1px solid var(--color-outline-light);
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

.studio-header button {
  display: grid;
  width: 34px;
  height: 34px;
  place-items: center;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 999px;
  color: var(--color-on-surface);
  cursor: pointer;
  transition: background 0.2s ease, color 0.2s ease;
}

.studio-header button:hover {
  background: var(--color-primary);
  color: var(--color-on-primary);
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

.panel-footer button,
.copy-action {
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

.panel-footer .btn-secondary,
.copy-action {
  background: transparent;
  border: 1px solid var(--color-outline-light);
  color: var(--color-on-surface);
}

.panel-footer .btn-primary {
  background: var(--color-primary);
  border: 1px solid var(--color-primary);
  color: var(--color-on-primary);
}

.panel-footer .btn-secondary:hover,
.copy-action:hover {
  background: var(--color-surface-container-high);
  border-color: var(--color-outline-variant);
}

.panel-footer .btn-primary:hover {
  background: var(--color-primary-soft);
}

.panel-footer button:active,
.copy-action:active {
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

.question-card h3 {
  margin: 0;
  font-family: var(--font-heading);
  font-size: 24px;
  font-weight: 600;
  line-height: 1.15;
}

.question-card p,
.option-list {
  margin: 0;
  color: var(--color-on-surface-variant);
  font-family: var(--font-body);
  font-size: 14px;
  line-height: 1.58;
}

.option-list {
  display: grid;
  gap: 6px;
  padding-top: 4px;
}

.artifact-text {
  margin: 0;
  white-space: pre-wrap;
  color: var(--color-on-surface);
  font-family: var(--font-body);
  font-size: 14px;
  line-height: 1.7;
}

.copy-action {
  width: fit-content;
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
  .form-grid {
    grid-template-columns: 1fr;
  }

  .artifact-body,
  .generation-form {
    padding: 22px;
  }
}
</style>
