<template>
  <div class="question-card" :class="{published: question.status === 1}" @click="('click')">
    <div class="card-header">
      <span class="type-badge" :class="typeClass">{{ typeName }}</span>
      <span class="difficulty-badge" :class="difficultyClass">{{ difficultyName }}</span>
      <span v-if="question.status === 1" class="status-badge published">{{ t('questionBank.published') }}</span>
      <span v-else class="status-badge draft">{{ t('questionBank.draft') }}</span>
    </div>

    <h3 class="question-title">{{ question.questionTitle }}</h3>

    <div class="card-footer">
      <span class="score">{{ question.score }} {{ t('questionBank.score') }}</span>
      <span v-if="question.estimatedTime" class="time"><Clock :size="14"/> {{ question.estimatedTime }}min</span>
      <span class="views"><Eye :size="14"/> {{ question.viewCount }}</span>
    </div>
  </div>
</template>

<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import {Clock, Eye} from 'lucide-vue-next'
import type {Question} from '@/features/question-bank/types/questionBank'
import {QuestionType, QuestionDifficulty} from '@/features/question-bank/types/questionBank'

const props = defineProps<{
  question: Question
}>()

defineEmits<{click: []}>()

const {t} = useI18n()

const typeName = computed(() => QuestionType[props.question.questionType] || '未知')
const difficultyName = computed(() => QuestionDifficulty[props.question.difficulty] || '未知')

const typeClass = computed(() => 'type-' + props.question.questionType)
const difficultyClass = computed(() => 'diff-' + props.question.difficulty)
</script>

<style scoped>
.question-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 16px 20px;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: 16px;
  cursor: pointer;
  transition: border-color 0.15s;
}

.question-card:hover {
  border-color: var(--color-on-surface);
}

.card-header {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.type-badge,
.difficulty-badge,
.status-badge {
  font-family: var(--font-body);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.03em;
  padding: 2px 8px;
  border-radius: 4px;
}

.type-badge {
  background: var(--color-surface-container-high);
  color: var(--color-on-surface);
}

.difficulty-badge.diff-1 { color: #22c55e; background: rgba(34, 197, 94, 0.12); }
.difficulty-badge.diff-2 { color: #eab308; background: rgba(234, 179, 8, 0.12); }
.difficulty-badge.diff-3 { color: #ef4444; background: rgba(239, 68, 68, 0.12); }

.status-badge.published { color: #22c55e; background: rgba(34, 197, 94, 0.12); }
.status-badge.draft { color: var(--color-muted); background: var(--color-surface-container-high); }

.question-title {
  margin: 0;
  font-family: var(--font-body);
  font-size: 15px;
  font-weight: 500;
  color: var(--color-on-surface);
  line-height: 1.4;
}

.card-footer {
  display: flex;
  gap: 16px;
  font-family: var(--font-body);
  font-size: 12px;
  color: var(--color-muted);
}

.card-footer span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.score {
  font-weight: 600;
  color: var(--color-on-surface);
}
</style>
