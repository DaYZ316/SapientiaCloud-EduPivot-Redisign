<template>
  <section
      v-if="display"
      class="generation-request-card"
  >
    <header>
      <h3>{{ requestTitle }}</h3>
    </header>

    <div
        v-if="display.fields.length"
        class="request-field-grid"
    >
      <div
          v-for="field in display.fields"
          :key="field.key"
          class="request-field"
      >
        <span class="request-field-label">{{ fieldLabel(field.key) }}</span>
        <span class="request-field-value">{{ fieldValue(field) }}</span>
      </div>
    </div>

    <div
        v-if="requirementLines.length"
        class="request-requirement"
    >
      <span class="request-section-label">{{ t('common.ai.studio.requirement') }}</span>
      <div class="request-lines">
        <p
            v-for="(line, index) in requirementLines"
            :key="index"
            class="request-requirement-line"
        >
          {{ line }}
        </p>
      </div>
    </div>
  </section>
</template>

<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'

import type {ChatMessage} from '@/features/ai/types/ai'
import {
  buildGenerationRequestDisplay,
  type GenerationRequestDisplayField,
  type GenerationRequestFieldKey,
} from '@/features/ai/utils/generationRequestPayload'

const props = defineProps<{
  message: ChatMessage
}>()

const {t, locale} = useI18n()
const display = computed(() => buildGenerationRequestDisplay(props.message.payload))
const requestTitle = computed(() => {
  if (!display.value) return ''

  return display.value.mode === 'PAPER'
      ? t('common.ai.studio.paperRequestTitle')
      : t('common.ai.studio.questionRequestTitle')
})
const requirementLines = computed(() => display.value?.requirement
    .split(/\r?\n/)
    .map(line => line.trim())
    .filter(Boolean) ?? [])

const fieldLabels: Record<GenerationRequestFieldKey, string> = {
  questionCount: 'common.ai.studio.questionCount',
  questionType: 'common.ai.studio.questionType',
  difficulty: 'common.ai.studio.difficulty',
  paperName: 'common.ai.studio.paperName',
  paperType: 'common.ai.studio.paperType',
  totalScore: 'common.ai.studio.totalScore',
  totalEstimatedTime: 'common.ai.studio.estimatedTime',
  scorePerQuestion: 'common.ai.studio.scorePerQuestion',
  questionBankId: 'questionBank.title',
  chapterIds: 'chapter.title',
  knowledgePoints: 'common.ai.studio.knowledgePoints',
  abilityGoals: 'common.ai.studio.abilityGoals',
}

const questionTypeLabels: Record<number, string> = {
  0: 'questionBank.typeSingle',
  1: 'questionBank.typeMultiple',
  2: 'questionBank.typeJudge',
  3: 'questionBank.typeBlank',
  4: 'questionBank.typeShort',
  5: 'common.ai.studio.questionTypes.mixed',
}

const difficultyLabels: Record<number, string> = {
  0: 'common.ai.studio.difficulties.random',
  1: 'common.ai.studio.difficulties.easy',
  2: 'common.ai.studio.difficulties.medium',
  3: 'common.ai.studio.difficulties.hard',
}

function fieldLabel(key: GenerationRequestFieldKey) {
  return t(fieldLabels[key])
}

function fieldValue(field: GenerationRequestDisplayField) {
  if (field.kind === 'questionType' && typeof field.value === 'number') {
    return t(questionTypeLabels[field.value] || 'questionBank.questionType')
  }
  if (field.kind === 'difficulty' && typeof field.value === 'number') {
    return t(difficultyLabels[field.value] || 'questionBank.difficulty')
  }
  if (field.kind === 'questionBank') {
    return t('common.ai.studio.currentQuestionBank')
  }
  if (field.kind === 'chapterCount') {
    return t('common.ai.studio.selectedChapterCount', {count: field.value})
  }
  if (field.kind === 'minutes') {
    return t('courseDetail.livePractice.estimatedMinutes', {minutes: field.value})
  }
  if (Array.isArray(field.value)) {
    return new Intl.ListFormat(String(locale.value), {
      style: 'long',
      type: 'conjunction',
    }).format(field.value)
  }
  return String(field.value)
}
</script>

<style scoped>
.generation-request-card {
  display: grid;
  width: 100%;
  gap: 14px;
  color: inherit;
}

.generation-request-card header {
  display: grid;
}

.request-section-label {
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 13px;
  font-weight: 600;
  line-height: 1.45;
  white-space: nowrap;
}

.generation-request-card h3 {
  margin: 0;
  color: var(--color-on-surface);
  font-family: var(--font-heading);
  font-size: 22px;
  font-weight: 600;
  line-height: 1.2;
}

.request-field-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px 18px;
}

.request-field {
  display: grid;
  grid-template-columns: max-content minmax(0, 1fr);
  align-items: end;
  column-gap: 8px;
  min-width: 0;
  font-size: 13px;
  line-height: 1.3;
}

.request-field-label {
  color: var(--color-muted);
  font-family: var(--font-label);
  font-weight: 600;
  white-space: nowrap;
  padding-bottom: 3px;
}

.request-field-value,
.request-requirement-line {
  display: block;
  min-width: 0;
  min-height: 22px;
  padding: 0 0 3px;
  border-bottom: 1px solid var(--color-outline-light);
  overflow-wrap: anywhere;
  color: var(--color-on-surface);
  font-family: var(--font-body);
  font-size: 13px;
  line-height: 1.3;
}

.request-field-value {
  text-align: center;
}

.request-lines {
  display: grid;
  gap: 8px;
}

.request-requirement {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: start;
  gap: 10px;
}

.request-requirement .request-lines {
  min-width: 0;
}

.request-requirement-line {
  margin: 0;
  width: fit-content;
  max-width: 100%;
  text-align: left;
}

@media (max-width: 720px) {
  .generation-request-card {
    width: 100%;
  }

  .request-field-grid {
    grid-template-columns: 1fr;
  }

  .request-field {
    align-items: flex-start;
    grid-template-columns: 1fr;
    gap: 4px;
  }

  .request-field-label {
    padding-bottom: 0;
  }
}
</style>
