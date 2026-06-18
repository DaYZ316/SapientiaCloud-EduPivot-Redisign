<template>
  <div class="ai-workspace-page">
    <header class="workspace-header">
      <div>
        <p class="eyebrow">
          Celestial Hub
        </p>
        <h1>AI 教学工作台</h1>
        <p>集中管理会话、课程知识库和后续智能体工作流。当前一期接入 RAG 问答与知识库文档入库。</p>
      </div>
      <div
        v-if="isTeacher"
        class="teacher-actions"
      >
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
      </div>
    </header>

    <section class="workspace-grid">
      <AiConversationList />
      <div class="chat-shell">
        <AiChatSurface title="AI 教学助手" />
      </div>
      <AiKnowledgePanel />
    </section>
  </div>
</template>

<script lang="ts" setup>
import {computed, onMounted} from 'vue'
import {useRoute} from 'vue-router'

import AiChatSurface from '@/features/ai/components/AiChatSurface.vue'
import AiConversationList from '@/features/ai/components/AiConversationList.vue'
import AiKnowledgePanel from '@/features/ai/components/AiKnowledgePanel.vue'
import {useAiStore} from '@/features/ai/stores/ai'
import {useAuthStore} from '@/features/auth/stores/auth'

const route = useRoute()
const aiStore = useAiStore()
const authStore = useAuthStore()
const isTeacher = computed(() => authStore.user?.role === 0 || authStore.user?.role === 2)

onMounted(() => {
  aiStore.setContext({sourceRoute: route.fullPath})
})
</script>

<style scoped>
.ai-workspace-page {
  display: grid;
  gap: 24px;
}

.workspace-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  padding-bottom: 22px;
  border-bottom: 1px solid var(--color-outline-light);
}

.workspace-header h1 {
  margin: 0;
  color: var(--color-on-surface);
  font-family: var(--font-heading);
  font-size: clamp(34px, 5vw, 56px);
  font-weight: 400;
  line-height: 1.05;
}

.workspace-header p:not(.eyebrow) {
  max-width: 68ch;
  margin: 12px 0 0;
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 15px;
  line-height: 1.6;
}

.teacher-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.teacher-actions button {
  min-height: 38px;
  padding: 0 13px;
  background: transparent;
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  color: var(--color-muted);
  cursor: not-allowed;
  font-family: var(--font-label);
  font-size: 12px;
}

.workspace-grid {
  display: grid;
  grid-template-columns: minmax(220px, 280px) minmax(0, 1fr) minmax(240px, 300px);
  gap: 16px;
  min-height: min(720px, calc(100dvh - 220px));
}

.chat-shell {
  min-width: 0;
  min-height: 0;
  border: 1px solid var(--color-outline-light);
}

@media (max-width: 1180px) {
  .workspace-grid {
    grid-template-columns: minmax(220px, 300px) minmax(0, 1fr);
  }

  .workspace-grid :deep(.knowledge-panel) {
    grid-column: 1 / -1;
    min-height: 320px;
  }
}

@media (max-width: 760px) {
  .workspace-header {
    align-items: stretch;
    flex-direction: column;
  }

  .teacher-actions {
    justify-content: flex-start;
  }

  .workspace-grid {
    grid-template-columns: 1fr;
    min-height: auto;
  }

  .chat-shell {
    min-height: 620px;
  }
}
</style>
