<template>
  <main class="monolith-ai-page">
    <AiWorkspaceShell
      ref="workspaceShellRef"
      layout="page"
    />
  </main>
</template>

<script lang="ts" setup>
import {onMounted, ref, watch} from 'vue'
import {useRoute} from 'vue-router'

import AiWorkspaceShell from '@/features/ai/components/AiWorkspaceShell.vue'
import {useAiStore} from '@/features/ai/stores/ai'
import type {AiAgentMode} from '@/features/ai/types/ai'

type AiWorkspaceShellInstance = InstanceType<typeof AiWorkspaceShell>

const route = useRoute()
const aiStore = useAiStore()
const workspaceShellRef = ref<AiWorkspaceShellInstance | null>(null)
let loadingRouteConversationId = ''

onMounted(() => {
  aiStore.setContext({sourceRoute: route.fullPath})
  syncModeFromRoute()
  void loadConversationFromRoute()
})

watch(
  () => route.fullPath,
  () => aiStore.setContext({sourceRoute: route.fullPath}),
)

watch(
  () => route.query.mode,
  () => syncModeFromRoute(),
)

watch(
  () => route.query.conversationId,
  () => {
    void loadConversationFromRoute()
  },
)

function syncModeFromRoute() {
  const mode = normalizeRouteMode(route.query.mode)
  if (mode) {
    workspaceShellRef.value?.setChatMode(mode)
  }
}

async function loadConversationFromRoute() {
  const conversationId = normalizeRouteText(route.query.conversationId)
  if (!conversationId || conversationId === aiStore.activeConversationId || conversationId === loadingRouteConversationId) {
    return
  }

  loadingRouteConversationId = conversationId
  try {
    await aiStore.loadMessages(conversationId)
  } catch {
    // Request handling already shows the load failure notification.
  } finally {
    if (loadingRouteConversationId === conversationId) {
      loadingRouteConversationId = ''
    }
  }
}

function normalizeRouteMode(value: unknown): Exclude<AiAgentMode, 'CHAT'> | null {
  const mode = normalizeRouteText(value)
  return mode === 'QUESTION' || mode === 'PAPER' ? mode : null
}

function normalizeRouteText(value: unknown) {
  const text = Array.isArray(value) ? value[0] : value
  return typeof text === 'string' ? text.trim() : ''
}
</script>

<style scoped>
.monolith-ai-page {
  min-height: 100dvh;
}
</style>
