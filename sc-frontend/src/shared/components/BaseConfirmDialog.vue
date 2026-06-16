<template>
  <Teleport to="body">
    <div v-if="visible" class="modal-overlay base-confirm-overlay" @click.self="handleBackdropClick">
      <section class="modal base-confirm-dialog" role="dialog" aria-modal="true" :aria-labelledby="titleId">
        <div class="modal-header">
          <h2 :id="titleId">{{ title }}</h2>
          <button v-if="showClose" class="btn-close" type="button" :aria-label="closeLabel" @click="emit('cancel')">
            <X :size="20"/>
          </button>
        </div>
        <div class="modal-body">
          <p v-if="message" class="base-confirm-message">{{ message }}</p>
          <slot/>
          <div class="modal-actions">
            <button class="btn-secondary" type="button" @click="emit('cancel')">
              {{ cancelText }}
            </button>
            <button class="btn-primary" :class="{'btn-danger': confirmVariant === 'danger'}" type="button" @click="emit('confirm')">
              {{ confirmText }}
            </button>
          </div>
        </div>
      </section>
    </div>
  </Teleport>
</template>

<script lang="ts" setup>
import {useId} from 'vue'
import {X} from 'lucide-vue-next'

const props = withDefaults(defineProps<{
  visible: boolean
  title: string
  message?: string
  confirmText: string
  cancelText: string
  confirmVariant?: 'primary' | 'danger'
  closeLabel?: string
  closeOnBackdrop?: boolean
  showClose?: boolean
}>(), {
  message: '',
  confirmVariant: 'primary',
  closeLabel: 'Close',
  closeOnBackdrop: true,
  showClose: true,
})

const emit = defineEmits<{
  confirm: []
  cancel: []
}>()

const titleId = useId()

function handleBackdropClick() {
  if (props.closeOnBackdrop) {
    emit('cancel')
  }
}
</script>

<style scoped>
.base-confirm-overlay {
  z-index: 2300;
}

.base-confirm-dialog {
  max-width: 440px;
}

.base-confirm-message {
  margin: 0;
  color: var(--color-secondary);
  font-family: var(--font-body);
  font-size: 16px;
  line-height: 1.6;
  overflow-wrap: anywhere;
}

.btn-danger {
  background: var(--color-error);
  border: 1px solid var(--color-error);
  color: #ffffff;
}

.btn-danger:hover:not(:disabled) {
  background: #ffffff;
  color: var(--color-error);
}
</style>
