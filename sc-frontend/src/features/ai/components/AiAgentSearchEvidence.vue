<template>
  <details
    v-if="records.length > 0"
    class="agent-evidence"
  >
    <summary class="agent-evidence__summary">
      <span>{{ summaryLabel }}</span>
      <ChevronDown
        :size="15"
        class="agent-evidence__chevron"
        stroke-width="1.8"
      />
    </summary>

    <div class="agent-evidence__body">
      <section
        v-for="record in records"
        :key="record.searchId || `${record.domain}-${record.query}`"
        class="agent-evidence__record"
      >
        <header class="agent-evidence__record-header">
          <span>{{ record.label || fallbackLabel(record.phase) }}</span>
          <small v-if="record.query">{{ record.query }}</small>
        </header>

        <div
          v-if="record.items?.length"
          class="agent-evidence__items"
        >
          <article
            v-for="(item, index) in record.items"
            :key="`${item.sourceType || 'source'}-${item.sourceId || item.title || index}`"
            class="agent-evidence__item"
          >
            <div class="agent-evidence__item-main">
              <span class="agent-evidence__source">{{ item.sourceLabel || item.sourceType || '来源' }}</span>
              <strong>{{ item.title || item.sourceType || '未命名资料' }}</strong>
              <small>{{ itemMeta(item) }}</small>
              <p v-if="item.snippet">{{ item.snippet }}</p>
            </div>

            <RouterLink
              v-if="navigationFor(item)"
              :to="navigationFor(item)!.to"
              class="agent-evidence__link"
            >
              <ExternalLink
                :size="13"
                stroke-width="1.9"
              />
              <span>{{ navigationFor(item)!.label }}</span>
            </RouterLink>

            <details
              v-if="hasIndexInfo(item)"
              class="agent-evidence__index"
            >
              <summary>索引信息</summary>
              <pre>{{ formatIndexInfo(item) }}</pre>
            </details>
          </article>
        </div>

        <p
          v-else
          class="agent-evidence__empty"
        >
          {{ record.phase === 'error' ? '检索失败' : '没有匹配资料' }}
        </p>
      </section>
    </div>
  </details>
</template>

<script lang="ts" setup>
import {computed} from 'vue'
import {RouterLink} from 'vue-router'
import {ChevronDown, ExternalLink} from 'lucide-vue-next'

import type {AgentSearchItem, AgentSearchPayload, AgentSearchRecord} from '@/features/ai/types/ai'
import {resolveAgentSearchNavigation} from '@/features/ai/utils/agentSearchNavigation'

const props = defineProps<{
  payload?: AgentSearchPayload | null
}>()

const records = computed(() => normalizeRecords(props.payload))
const totalItems = computed(() => records.value.reduce((total, record) => total + (record.items?.length || 0), 0))
const summaryLabel = computed(() => {
  const recordCount = records.value.length
  if (totalItems.value > 0) return `已参考 ${totalItems.value} 条平台内容`
  return recordCount > 0 ? `检索 ${recordCount} 次` : ''
})

function normalizeRecords(payload?: AgentSearchPayload | null): AgentSearchRecord[] {
  if (!payload) return []
  if (payload.searches?.length) {
    return payload.searches.map((record) => ({
      ...record,
      items: record.items || [],
    }))
  }

  const recordsById = new Map<string, AgentSearchRecord>()
  for (const event of payload.events || []) {
    if (event.phase === 'completed') continue
    const key = event.searchId || `${event.domain}:${event.query || ''}`
    const previous = recordsById.get(key)
    recordsById.set(key, {
      ...previous,
      searchId: event.searchId,
      domain: event.domain,
      query: event.query,
      label: event.label,
      phase: event.phase,
      total: event.total,
      occurredAt: event.occurredAt,
      items: event.items?.length ? event.items : previous?.items || [],
    })
  }
  return [...recordsById.values()]
}

function navigationFor(item: AgentSearchItem) {
  return resolveAgentSearchNavigation(item)
}

function itemMeta(item: AgentSearchItem) {
  return [item.contextLabel, item.relationLabel].filter(Boolean).join(' / ')
}

function hasIndexInfo(item: AgentSearchItem) {
  return Object.keys(item.indexInfo || {}).length > 0 || Object.keys(item.metadata || {}).length > 0
}

function formatIndexInfo(item: AgentSearchItem) {
  return JSON.stringify({
    sourceType: item.sourceType,
    sourceId: item.sourceId,
    courseId: item.courseId,
    metadata: item.metadata || {},
    indexInfo: item.indexInfo || {},
  }, null, 2)
}

function fallbackLabel(phase?: string) {
  if (phase === 'empty') return '未找到匹配资料'
  if (phase === 'error') return '检索失败'
  return '检索资料'
}
</script>

<style scoped>
.agent-evidence {
  width: min(100%, 760px);
  margin: 10px 0 4px;
  border-top: 1px solid var(--color-outline-light);
}

.agent-evidence__summary {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 34px;
  color: var(--color-muted);
  cursor: pointer;
  font-family: var(--font-label);
  font-size: 13px;
  list-style: none;
}

.agent-evidence__summary::-webkit-details-marker {
  display: none;
}

.agent-evidence__summary:hover {
  color: var(--color-on-surface);
}

.agent-evidence__chevron {
  transition: transform 0.2s ease;
}

.agent-evidence[open] .agent-evidence__chevron {
  transform: rotate(180deg);
}

.agent-evidence__body {
  display: grid;
  gap: 12px;
  padding: 4px 0 8px;
}

.agent-evidence__record {
  display: grid;
  gap: 8px;
}

.agent-evidence__record-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  color: var(--color-on-surface);
  font-family: var(--font-label);
  font-size: 13px;
}

.agent-evidence__record-header small {
  min-width: 0;
  overflow: hidden;
  color: var(--color-muted);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.agent-evidence__items {
  display: grid;
  gap: 8px;
}

.agent-evidence__item {
  display: grid;
  gap: 8px;
  padding: 10px 12px;
  background: var(--color-surface-container);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
}

.agent-evidence__item-main {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.agent-evidence__source {
  color: var(--color-primary);
  font-family: var(--font-label);
  font-size: 11px;
  font-weight: 600;
}

.agent-evidence__item strong {
  color: var(--color-on-surface);
  font-family: var(--font-body);
  font-size: 14px;
  line-height: 1.35;
}

.agent-evidence__item small,
.agent-evidence__item p,
.agent-evidence__empty {
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 13px;
  line-height: 1.5;
}

.agent-evidence__item p,
.agent-evidence__empty {
  margin: 0;
}

.agent-evidence__link {
  display: inline-flex;
  width: fit-content;
  align-items: center;
  gap: 5px;
  color: var(--color-on-surface);
  font-family: var(--font-label);
  font-size: 12px;
  text-decoration: none;
}

.agent-evidence__link:hover {
  color: var(--color-primary);
}

.agent-evidence__index {
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 12px;
}

.agent-evidence__index summary {
  width: fit-content;
  cursor: pointer;
}

.agent-evidence__index pre {
  max-height: 180px;
  margin: 8px 0 0;
  overflow: auto;
  padding: 10px;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  color: var(--color-on-surface-variant);
  font-family: 'Cascadia Code', 'Fira Code', 'JetBrains Mono', Consolas, monospace;
  font-size: 12px;
  line-height: 1.45;
  white-space: pre-wrap;
}
</style>
