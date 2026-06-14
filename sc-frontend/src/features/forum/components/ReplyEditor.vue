<template>
  <div class="reply-editor">
    <div v-if="replyTo" class="reply-to-hint">
      {{ t('forum.replyTo') }}
      <button class="cancel-reply" @click="('cancel-reply')">
        <X :size="14"/>
      </button>
    </div>
    <div class="editor-row">
      <textarea
        v-model="content"
        class="reply-textarea"
        rows="3"
        :placeholder="t('forum.replyPlaceholder')"
      ></textarea>
    </div>
    <div class="editor-footer">
      <button
        class="btn-submit"
        :disabled="!content.trim()"
        @click="handleSubmit"
      >
        {{ t('forum.submitReply') }}
      </button>
    </div>
  </div>
</template>

<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {X} from 'lucide-vue-next'
import type {ForumReply} from '@/features/forum/types/forum'

const props = defineProps<{
  replyTo?: ForumReply | null
}>()

const emit = defineEmits<{
  submit: [content: string, replyToUserId?: string | null]
  'cancel-reply': []
}>()

const {t} = useI18n()
const content = ref('')

function handleSubmit() {
  if (!content.value.trim()) return
  emit('submit', content.value, props.replyTo?.sysUserId || null)
  content.value = ''
}
</script>

<style scoped>
.reply-editor {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
  background: var(--color-surface-container);
  border-radius: var(--radius-md);
}

.reply-to-hint {
  display: flex;
  align-items: center;
  gap: 8px;
  font-family: var(--font-body);
  font-size: 13px;
  color: var(--color-muted);
}

.cancel-reply {
  display: grid;
  place-items: center;
  width: 20px;
  height: 20px;
  border: none;
  background: none;
  color: var(--color-muted);
  cursor: pointer;
  border-radius: 4px;
}

.cancel-reply:hover {
  background: var(--color-surface-container-high);
  color: var(--color-on-surface);
}

.reply-textarea {
  width: 100%;
  padding: 12px 14px;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  color: var(--color-on-surface);
  font-family: var(--font-body);
  font-size: 14px;
  outline: none;
  resize: vertical;
  min-height: 80px;
  transition: border-color 0.15s;
}

.reply-textarea:focus {
  border-color: var(--color-on-surface);
}

.editor-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
}

.btn-submit {
  padding: 8px 16px;
  border: none;
  border-radius: var(--radius-sm);
  background: var(--color-primary);
  color: var(--color-on-primary);
  font-family: var(--font-body);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s;
}

.btn-submit:hover:not(:disabled) {
  background: var(--color-primary-soft);
}

.btn-submit:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
