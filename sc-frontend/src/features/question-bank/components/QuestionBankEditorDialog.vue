<template>
  <Teleport to="body">
    <div v-if="visible" class="modal-overlay" @click.self="emit('close')">
      <div class="modal-content">
        <h3>{{ bank ? t('courseDetail.editBank') : t('courseDetail.newBank') }}</h3>
        <form @submit.prevent="handleSubmit">
          <label>
            {{ t('questionBank.bankName') }} *
            <input v-model="form.bankName" maxlength="200" required type="text"/>
          </label>
          <label>
            {{ t('questionBank.bankDescription') }}
            <textarea v-model="form.description" maxlength="2000" rows="3"></textarea>
          </label>
          <label>
            {{ t('questionBank.bankType') }}
            <BaseSelect v-model="form.bankType" :options="bankTypeOptions" min-width="100%"/>
          </label>
          <label>
            {{ t('questionBank.difficulty') }}
            <BaseSelect v-model="form.difficulty" :options="difficultyOptions" min-width="100%"/>
          </label>
          <div class="modal-actions">
            <button class="btn-cancel" type="button" @click="emit('close')">{{ t('courseDetail.cancel') }}</button>
            <button :disabled="submitting || !form.bankName.trim()" class="btn-submit" type="submit">
              {{ t('courseDetail.save') }}
            </button>
          </div>
        </form>
      </div>
    </div>
  </Teleport>
</template>

<script lang="ts">
export interface QuestionBankEditorPayload {
  bankName: string
  description?: string
  bankType: number
  difficulty: number
}
</script>

<script lang="ts" setup>
import {computed, reactive, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import BaseSelect from '@/shared/components/BaseSelect.vue'
import type {QuestionBank} from '@/features/question-bank/types/questionBank'

const props = withDefaults(defineProps<{
  visible: boolean
  bank?: QuestionBank | null
  submitting?: boolean
}>(), {
  bank: null,
  submitting: false,
})

const emit = defineEmits<{
  close: []
  submit: [payload: QuestionBankEditorPayload]
}>()

const {t} = useI18n()

const form = reactive({
  bankName: '',
  description: '',
  bankType: 0,
  difficulty: 2,
})

const bankTypeOptions = computed(() => [
  {label: t('questionBank.bankTypePractice'), value: 0},
  {label: t('questionBank.bankTypeExam'), value: 1},
  {label: t('questionBank.bankTypeHomework'), value: 2},
])

const difficultyOptions = computed(() => [
  {label: t('questionBank.difficultyEasy'), value: 1},
  {label: t('questionBank.difficultyMedium'), value: 2},
  {label: t('questionBank.difficultyHard'), value: 3},
])

watch(
    () => [props.visible, props.bank] as const,
    () => syncForm(),
    {immediate: true},
)

function syncForm() {
  if (!props.visible) return
  if (!props.bank) {
    resetForm()
    return
  }

  form.bankName = props.bank.bankName
  form.description = props.bank.description || ''
  form.bankType = props.bank.bankType
  form.difficulty = props.bank.difficulty
}

function resetForm() {
  form.bankName = ''
  form.description = ''
  form.bankType = 0
  form.difficulty = 2
}

function handleSubmit() {
  if (!form.bankName.trim() || props.submitting) return
  emit('submit', {
    bankName: form.bankName,
    description: form.description || undefined,
    bankType: form.bankType,
    difficulty: form.difficulty,
  })
}
</script>

<style scoped>
.modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: grid;
  place-items: center;
  background: rgba(0, 0, 0, 0.4);
}

.modal-content {
  width: min(480px, 90vw);
  padding: 24px;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-md);
}

.modal-content h3 {
  margin: 0 0 20px;
  color: var(--color-on-surface);
  font-family: var(--font-heading);
  font-size: 20px;
  font-weight: 400;
}

.modal-content form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.modal-content label {
  display: flex;
  flex-direction: column;
  gap: 6px;
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 13px;
  font-weight: 400;
}

.modal-content input,
.modal-content textarea {
  padding: 10px 12px;
  background: var(--color-surface-container);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  color: var(--color-on-surface);
  font-family: var(--font-body);
  font-size: 14px;
}

.modal-content input:focus,
.modal-content textarea:focus {
  border-color: var(--color-primary);
  outline: none;
}

.modal-actions {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
  margin-top: 4px;
}

.btn-cancel {
  padding: 10px 20px;
  background: transparent;
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  color: var(--color-muted);
  cursor: pointer;
  font-family: var(--font-label);
  font-size: 13px;
  font-weight: 400;
}

.btn-cancel:hover {
  background: var(--color-surface-container);
  border-color: var(--color-outline);
}

.btn-submit {
  padding: 10px 20px;
  background: var(--color-primary);
  border: none;
  border-radius: var(--radius-sm);
  color: var(--color-on-primary);
  cursor: pointer;
  font-family: var(--font-label);
  font-size: 13px;
  font-weight: 400;
}

.btn-submit:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}
</style>
