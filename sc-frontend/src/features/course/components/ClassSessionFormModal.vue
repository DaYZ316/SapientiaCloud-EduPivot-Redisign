<template>
  <Teleport to="body">
    <div v-if="visible" class="modal-overlay" @click.self="emit('close')">
      <div class="modal class-session-modal">
        <div class="modal-header">
          <h2>{{ session ? t('courseDetail.classSession.editTitle') : t('courseDetail.classSession.createTitle') }}</h2>
          <button class="btn-close" type="button" @click="emit('close')">
            <X :size="20" stroke-width="1.8"/>
          </button>
        </div>

        <form class="modal-body class-session-form" @submit.prevent="handleSubmit">
          <label class="form-group">
            <span>{{ t('courseDetail.classSession.titleLabel') }}</span>
            <input
                v-model="form.title"
                :placeholder="t('courseDetail.classSession.titlePlaceholder')"
                class="input-field"
                maxlength="200"
                type="text"
            />
          </label>

          <label class="form-group">
            <span>{{ t('courseDetail.classSession.descriptionLabel') }}</span>
            <textarea
                v-model="form.description"
                :placeholder="t('courseDetail.classSession.descriptionPlaceholder')"
                class="input-field"
                maxlength="5000"
                rows="4"
            ></textarea>
          </label>

          <div class="form-grid">
            <label class="form-group">
              <span>{{ t('courseDetail.classSession.startAtLabel') }}</span>
              <BaseDatePicker
                  v-model="form.scheduledStartAt"
                  :placeholder="t('courseDetail.classSession.startAtLabel')"
                  class="modal-date-control"
                  default-time="09:00"
                  show-time
              />
            </label>
            <label class="form-group">
              <span>{{ t('courseDetail.classSession.endAtLabel') }}</span>
              <BaseDatePicker
                  v-model="form.scheduledEndAt"
                  :placeholder="t('courseDetail.classSession.endAtLabel')"
                  class="modal-date-control"
                  default-time="10:00"
                  show-time
              />
            </label>
          </div>
          <p class="duration-note">{{ t('courseDetail.classSession.durationTooLong') }}</p>

          <label class="form-group">
            <span>{{ t('courseDetail.classSession.roomSizeLabel') }}</span>
            <BaseSelect
                v-model="form.roomSize"
                :options="roomSizeOptions"
                class="modal-select-control"
                min-width="100%"
            />
          </label>

          <p v-if="errorMessage" class="form-error">{{ errorMessage }}</p>

          <div class="modal-footer class-session-footer">
            <button class="btn-secondary" type="button" @click="emit('close')">
              {{ t('courseDetail.cancel') }}
            </button>
            <button :disabled="submitting" class="btn-primary" type="submit">
              {{ submitting ? t('courseDetail.saving') : t('courseDetail.save') }}
            </button>
          </div>
        </form>
      </div>
    </div>
  </Teleport>
</template>

<script lang="ts" setup>
import {computed, reactive, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {X} from 'lucide-vue-next'

import BaseDatePicker from '@/shared/components/BaseDatePicker.vue'
import BaseSelect from '@/shared/components/BaseSelect.vue'
import {notify} from '@/shared/composables/useGlobalNotification'
import type {ClassSession, ClassSessionFormPayload} from '@/features/course/types/classSession'
import {ClassRoomSize, ClassRoomSizeLabel} from '@/features/course/types/classSession'

const props = withDefaults(
    defineProps<{
      visible: boolean
      session?: ClassSession | null
      submitting?: boolean
    }>(),
    {
      session: null,
      submitting: false,
    },
)

const emit = defineEmits<{
  close: []
  submit: [payload: ClassSessionFormPayload]
}>()

const {t} = useI18n()
const errorMessage = ref('')
const MAX_SESSION_DURATION_MS = 2 * 60 * 60 * 1000

const form = reactive({
  title: '',
  description: '',
  scheduledStartAt: '',
  scheduledEndAt: '',
  roomSize: ClassRoomSize.MEDIUM as number,
})

const roomSizeOptions = computed(() => [
  {label: t('courseDetail.classSession.roomSmall'), value: ClassRoomSize.SMALL},
  {label: t('courseDetail.classSession.roomMedium'), value: ClassRoomSize.MEDIUM},
  {label: t('courseDetail.classSession.roomLarge'), value: ClassRoomSize.LARGE},
  {label: t('courseDetail.classSession.roomXLarge'), value: ClassRoomSize.XLARGE},
])

watch(
    () => [props.visible, props.session] as const,
    () => resetForm(),
    {immediate: true},
)

function resetForm() {
  errorMessage.value = ''
  if (!props.visible) return

  form.title = props.session?.title || ''
  form.description = props.session?.description || ''
  form.scheduledStartAt = toDatetimeLocalValue(props.session?.scheduledStartAt)
  form.scheduledEndAt = toDatetimeLocalValue(props.session?.scheduledEndAt)
  form.roomSize = props.session?.roomSize ?? ClassRoomSize.MEDIUM
}

function handleSubmit() {
  const title = form.title.trim()
  if (!title) {
    errorMessage.value = t('courseDetail.classSession.titleRequired')
    return
  }
  if (!form.scheduledStartAt || !form.scheduledEndAt) {
    errorMessage.value = t('courseDetail.classSession.timeRequired')
    return
  }

  const startDate = new Date(form.scheduledStartAt)
  const endDate = new Date(form.scheduledEndAt)
  if (Number.isNaN(startDate.getTime()) || Number.isNaN(endDate.getTime())) {
    errorMessage.value = t('courseDetail.classSession.timeRequired')
    return
  }
  if (endDate <= startDate) {
    errorMessage.value = t('courseDetail.classSession.endAfterStart')
    return
  }
  if (endDate.getTime() - startDate.getTime() > MAX_SESSION_DURATION_MS) {
    notify.warn(t('courseDetail.classSession.durationTooLong'))
    return
  }

  errorMessage.value = ''
  emit('submit', {
    title,
    description: form.description.trim() || undefined,
    scheduledStartAt: startDate.toISOString(),
    scheduledEndAt: endDate.toISOString(),
    roomSize: normalizedRoomSize(form.roomSize),
  })
}

function toDatetimeLocalValue(value?: string | null) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  const localDate = new Date(date.getTime() - date.getTimezoneOffset() * 60000)
  return localDate.toISOString().slice(0, 16)
}

function normalizedRoomSize(value: number) {
  return ClassRoomSizeLabel[value] == null ? ClassRoomSize.MEDIUM : value
}
</script>

<style scoped>
.class-session-modal {
  width: min(100%, 640px);
}

.btn-close {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  padding: 0;
  background: transparent;
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  color: var(--color-muted);
  cursor: pointer;
  transition: background 0.2s ease,
  color 0.2s ease,
  border-color 0.2s ease;
}

.btn-close:hover {
  background: var(--color-surface-container);
  border-color: var(--color-outline);
  color: var(--color-on-surface);
}

.class-session-form {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 13px;
}

.form-group span {
  color: var(--color-on-surface);
  font-weight: 700;
}

.form-group :deep(.modal-select-control) {
  width: 100%;
  min-width: 0;
}

.form-group :deep(.modal-select-control .base-select-trigger) {
  min-height: 49px;
  border-radius: var(--radius-sm);
  font-family: var(--font-body);
}

.form-group :deep(.modal-select-control .base-select-menu) {
  min-width: 100%;
}

.form-group :deep(.modal-date-control) {
  width: 100%;
  min-width: 0;
}

.form-group :deep(.modal-date-control .base-date-picker-trigger) {
  min-height: 49px;
  padding: 0 12px 0 14px;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  color: var(--color-on-surface);
  font-family: var(--font-label);
  font-size: 14px;
  font-weight: 700;
  line-height: 1;
  transition: border-color 0.2s,
  background 0.2s,
  box-shadow 0.2s;
}

.form-group :deep(.modal-date-control .base-date-picker-trigger:hover) {
  background: var(--color-surface-container);
  border-color: var(--color-outline-variant);
}

.form-group :deep(.modal-date-control .base-date-picker-trigger:focus) {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px rgba(26, 28, 28, 0.08);
}

.form-group :deep(.modal-date-control .base-date-picker-trigger.placeholder span) {
  color: var(--color-muted);
}

.form-group :deep(.modal-date-control .base-date-picker-trigger svg) {
  width: 16px;
  height: 16px;
  color: var(--color-muted);
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.duration-note {
  margin: -8px 0 0;
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 12px;
  line-height: 1.5;
}

.form-error {
  margin: 0;
  color: var(--color-error);
  font-family: var(--font-body);
  font-size: 13px;
  line-height: 1.5;
}

.class-session-footer {
  padding: 8px 0 0;
}

textarea.input-field {
  resize: vertical;
}

@media (max-width: 640px) {
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
