<template>
  <aside class="knowledge-panel">
    <header>
      <div>
        <span>Knowledge</span>
        <h2>知识库</h2>
      </div>
      <button
        title="刷新"
        type="button"
        @click="loadDocs"
      >
        <RefreshCw
          :size="15"
          stroke-width="1.8"
        />
      </button>
    </header>

    <form
      class="ingest-form"
      @submit.prevent="submit"
    >
      <label>
        <span>Storage Object ID</span>
        <input
          v-model.trim="storageObjectId"
          placeholder="粘贴已上传文件 ID"
          type="text"
        >
      </label>
      <button
        :disabled="!storageObjectId"
        type="submit"
      >
        入库
      </button>
    </form>

    <div
      v-if="aiStore.loadingKnowledgeDocs"
      class="panel-state"
    >
      加载文档中...
    </div>
    <div
      v-else-if="aiStore.knowledgeDocs.length === 0"
      class="panel-state"
    >
      暂无知识库文档
    </div>
    <div
      v-else
      class="doc-list"
    >
      <article
        v-for="doc in aiStore.knowledgeDocs"
        :key="doc.id"
        class="doc-row"
      >
        <FileText
          :size="16"
          stroke-width="1.7"
        />
        <div>
          <strong>{{ doc.filename }}</strong>
          <span>{{ doc.status }} / {{ doc.chunkCount }} chunks</span>
        </div>
      </article>
    </div>

    <section class="capability-block">
      <h3>二期能力</h3>
      <button
        disabled
        type="button"
      >
        AI 出题
      </button>
      <button
        disabled
        type="button"
      >
        AI 出卷
      </button>
      <button
        disabled
        type="button"
      >
        AI 批卷
      </button>
    </section>
  </aside>
</template>

<script lang="ts" setup>
import {onMounted, ref} from 'vue'
import {FileText, RefreshCw} from 'lucide-vue-next'

import {useAiStore} from '@/features/ai/stores/ai'
import {notify} from '@/shared/composables/useGlobalNotification'

const aiStore = useAiStore()
const storageObjectId = ref('')

onMounted(loadDocs)

async function loadDocs() {
  try {
    await aiStore.loadKnowledgeDocs()
  } catch {
    notify.error('知识库文档加载失败')
  }
}

async function submit() {
  if (!storageObjectId.value) return
  try {
    await aiStore.ingestDoc(storageObjectId.value)
    storageObjectId.value = ''
  } catch {
    notify.error('文档入库失败')
  }
}
</script>

<style scoped>
.knowledge-panel {
  display: flex;
  min-height: 0;
  flex-direction: column;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  color: var(--color-on-surface);
}

header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 16px;
  border-bottom: 1px solid var(--color-outline-light);
}

header span,
.ingest-form span {
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 11px;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

header h2 {
  margin: 5px 0 0;
  font-family: var(--font-heading);
  font-size: 24px;
  font-weight: 400;
}

header button,
.ingest-form button,
.capability-block button {
  min-height: 34px;
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-on-surface);
  cursor: pointer;
}

header button {
  display: grid;
  width: 34px;
  place-items: center;
}

.ingest-form {
  display: grid;
  gap: 10px;
  padding: 16px;
  border-bottom: 1px solid var(--color-outline-light);
}

.ingest-form label {
  display: grid;
  gap: 7px;
}

.ingest-form input {
  min-height: 38px;
  padding: 0 10px;
  background: var(--color-surface-canvas);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  color: var(--color-on-surface);
  outline: none;
}

.ingest-form button {
  background: var(--color-primary);
  color: var(--color-on-primary);
}

.ingest-form button:disabled,
.capability-block button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.doc-list {
  display: grid;
  gap: 8px;
  min-height: 0;
  overflow-y: auto;
  padding: 16px;
}

.doc-row {
  display: grid;
  grid-template-columns: 18px minmax(0, 1fr);
  gap: 10px;
  padding: 11px;
  background: var(--color-surface-container);
  border: 1px solid var(--color-outline-light);
}

.doc-row strong {
  display: block;
  overflow: hidden;
  font-family: var(--font-body);
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.doc-row span,
.panel-state {
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 12px;
}

.panel-state {
  padding: 20px 16px;
}

.capability-block {
  display: grid;
  gap: 8px;
  margin-top: auto;
  padding: 16px;
  border-top: 1px solid var(--color-outline-light);
}

.capability-block h3 {
  margin: 0 0 2px;
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 11px;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}
</style>
