<template>
  <aside class="ai-studio-panel">
    <header class="studio-header">
      <div>
        <span>{{ panelTag }}</span>
        <h2>{{ panelTitle }}</h2>
      </div>
      <button
        class="btn-close"
        title="关闭"
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
          <span>试卷名称</span>
          <input
            v-model.trim="generationModel.paperName"
            class="form-input"
            maxlength="100"
            placeholder="期末复习卷"
            type="text"
          >
        </label>
        <label class="form-field">
          <span>试卷类型</span>
          <input
            v-model.trim="generationModel.paperType"
            class="form-input"
            maxlength="50"
            placeholder="阶段测验"
            type="text"
          >
        </label>
        <label class="form-field">
          <span>总分</span>
          <BaseNumberStepper
            v-model="totalScoreValue"
            :max="1000"
            :min="1"
          />
        </label>
        <label class="form-field">
          <span>预计用时</span>
          <BaseNumberStepper
            v-model="totalEstimatedTimeValue"
            :max="300"
            :min="1"
          />
        </label>
      </div>

      <div class="form-grid">
        <label class="form-field">
          <span>题目数量</span>
          <BaseNumberStepper
            v-model="questionCountValue"
            :max="mode === 'PAPER' ? 50 : 10"
            :min="1"
          />
        </label>
        <label class="form-field">
          <span>题型</span>
          <BaseSelect
            v-model="questionTypeValue"
            :options="questionTypeOptions"
            class="form-select-control"
            min-width="100%"
          />
        </label>
        <label class="form-field">
          <span>难度</span>
          <BaseSelect
            v-model="difficultyValue"
            :options="difficultyOptions"
            class="form-select-control"
            min-width="100%"
          />
        </label>
        <label class="form-field">
          <span>每题分值</span>
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
        <span>知识点</span>
        <textarea
          v-model="knowledgePointsText"
          class="form-textarea"
          placeholder="多个知识点用逗号或换行分隔"
          rows="2"
        />
      </label>
      <label
        v-if="mode === 'PAPER'"
        class="wide-field"
      >
        <span>能力目标</span>
        <textarea
          v-model="abilityGoalsText"
          class="form-textarea"
          placeholder="多个能力目标用逗号或换行分隔"
          rows="2"
        />
      </label>
      <label class="wide-field">
        <span>生成要求</span>
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
          Cancel
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
            <h3>{{ textValue(question.questionTitle) || textValue(question.title) || '未命名题目' }}</h3>
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
        Copy artifact
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
      <h3>Generated work appears here</h3>
      <p>Choose question set or paper mode from the composer to open a structured request panel.</p>
    </section>
  </aside>
</template>

<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
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

const questionTypeOptions: SelectOption[] = [
  {label: '单选题', value: 0},
  {label: '多选题', value: 1},
  {label: '判断题', value: 2},
  {label: '填空题', value: 3},
  {label: '简答题', value: 4},
  {label: '混合题型', value: 5},
]

const difficultyOptions: SelectOption[] = [
  {label: '随机', value: 0},
  {label: '简单', value: 1},
  {label: '中等', value: 2},
  {label: '困难', value: 3},
]

const totalScoreValue = numericField('totalScore', 100)
const totalEstimatedTimeValue = numericField('totalEstimatedTime', 60)
const questionCountValue = numericField('questionCount', 5)
const scorePerQuestionValue = numericField('scorePerQuestion', 1)
const questionTypeValue = selectField('questionType')
const difficultyValue = selectField('difficulty')

const panelTitle = computed(() => props.mode === 'PAPER' ? '智能出卷' : props.mode === 'QUESTION' ? '智能出题' : '生成产物')
const panelTag = computed(() => props.mode === 'PAPER' ? 'Paper request' : props.mode === 'QUESTION' ? 'Question request' : 'Preview')
const formHint = computed(() => props.mode === 'PAPER'
  ? '设置试卷结构、知识点和生成要求，提交后 AI 会在当前会话中生成试卷草稿。'
  : '设置题目数量、题型、难度和生成要求，提交后 AI 会在当前会话中生成题目草稿。')
const requirementPlaceholder = computed(() => props.mode === 'PAPER'
  ? '例如：覆盖本章核心概念，包含基础题和综合应用题'
  : '例如：围绕本章知识点生成可直接加入题库的选择题')
const artifactTitle = computed(() =>
  textValue(payload.value.title) ||
  (artifact.value?.messageType === 'PAPER' ? 'AI 试卷草稿' : 'AI 题目草稿'),
)
const artifactSummary = computed(() => {
  if (questionCount.value > 0) return `${questionCount.value} 个条目已生成，可复制后进入题库或试卷编辑流程。`
  return 'The generated response is preserved as a structured research artifact.'
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
    notify.success('产物已复制')
  } catch {
    notify.error('复制失败')
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
  const marker = option.correct === true || option.isCorrect === 1 ? ' / 答案' : ''
  return `${label}. ${content || '选项'}${marker}`
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

.studio-header span,
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

.form-input,
.form-textarea {
  width: 100%;
  background: var(--color-surface-container);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  color: var(--color-on-surface);
  font-family: var(--font-body);
  font-size: 14px;
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
  background: var(--color-surface-container);
  border-radius: var(--radius-sm);
  font-family: var(--font-body);
  font-size: 14px;
}

.form-field :deep(.base-select-trigger:hover),
.form-field :deep(.base-select.open .base-select-trigger) {
  background: var(--color-surface-container-high);
  border-color: var(--color-outline-variant);
}

.form-field :deep(.base-select-trigger:focus-visible),
.form-field :deep(.base-select.open .base-select-trigger) {
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--color-on-surface) 10%, transparent);
}

.form-field :deep(.base-select-menu) {
  border-radius: var(--radius-sm);
}

.form-field :deep(.base-number-stepper) {
  min-height: 40px;
  grid-template-columns: 28px minmax(0, 1fr) 28px;
  background: var(--color-surface-container);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
}

.form-field :deep(.base-number-stepper:focus-within) {
  border-color: var(--color-on-surface);
}

.form-field :deep(.base-number-stepper-input) {
  min-height: 38px;
  padding: 0;
  color: var(--color-on-surface);
  font-size: 14px;
}

.form-field :deep(.base-number-stepper-button) {
  height: 38px;
  border-radius: var(--radius-sm);
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
  font-size: 11px;
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
