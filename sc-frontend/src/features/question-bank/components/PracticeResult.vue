<template>
  <div class="practice-result">
    <div class="result-icon">
      <CheckCircle v-if="accuracy >= 60" :size="48" stroke-width="1.5"/>
      <XCircle v-else :size="48" stroke-width="1.5"/>
    </div>

    <h2 class="result-title">{{ t('questionBank.practiceResult') }}</h2>

    <div class="result-stats">
      <div class="stat-item">
        <span class="stat-value">{{ session.correctCount }}/{{ session.totalQuestions }}</span>
        <span class="stat-label">{{ t('questionBank.correctCount') }}</span>
      </div>
      <div class="stat-item">
        <span class="stat-value">{{ accuracy }}%</span>
        <span class="stat-label">{{ t('questionBank.accuracy') }}</span>
      </div>
      <div class="stat-item">
        <span class="stat-value">{{ session.earnedScore }}/{{ session.totalScore }}</span>
        <span class="stat-label">{{ t('questionBank.earnedScore') }}</span>
      </div>
    </div>

    <div class="result-actions">
      <button class="btn-secondary" @click="('back')">
        {{ t('questionBank.backToBank') }}
      </button>
      <button class="btn-primary" @click="('retry')">
        {{ t('questionBank.startPractice') }}
      </button>
    </div>
  </div>
</template>

<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import {CheckCircle, XCircle} from 'lucide-vue-next'
import type {PracticeSession} from '@/features/question-bank/types/practiceSession'

const props = defineProps<{
  session: PracticeSession
}>()

defineEmits<{
  back: []
  retry: []
}>()

const {t} = useI18n()

const accuracy = computed(() => {
  if (props.session.totalQuestions === 0) return 0
  return Math.round((props.session.correctCount / props.session.totalQuestions) * 100)
})
</script>

<style scoped>
.practice-result {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 24px;
  padding: 48px 32px;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-lg);
  text-align: center;
}

.result-icon {
  color: var(--color-on-surface);
}

.result-title {
  margin: 0;
  font-family: var(--font-heading);
  font-size: 28px;
  font-weight: 400;
  color: var(--color-on-surface);
}

.result-stats {
  display: flex;
  gap: 48px;
}

.stat-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.stat-value {
  font-family: var(--font-heading);
  font-size: 32px;
  font-weight: 700;
  color: var(--color-on-surface);
}

.stat-label {
  font-family: var(--font-body);
  font-size: 13px;
  color: var(--color-muted);
}

.result-actions {
  display: flex;
  gap: 12px;
  margin-top: 8px;
}

.btn-primary,
.btn-secondary {
  padding: 10px 24px;
  border: none;
  border-radius: var(--radius-sm);
  font-family: var(--font-body);
  font-size: 13px;
  font-weight: 400;
  cursor: pointer;
  transition: background 0.15s;
}

.btn-primary {
  background: var(--color-primary);
  color: var(--color-on-primary);
}

.btn-primary:hover {
  background: var(--color-primary-soft);
}

.btn-secondary {
  background: var(--color-surface-container);
  color: var(--color-on-surface);
}

.btn-secondary:hover {
  background: var(--color-surface-container-high);
}
</style>
