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

    <div class="agent-evidence__timeline">
      <section
        v-for="record in records"
        :key="record.searchId || `${record.domain}-${record.query}`"
        class="agent-evidence__record"
        :class="`agent-evidence__record--${statusTone(record)}`"
      >
        <header class="agent-evidence__record-header">
          <span class="agent-evidence__status">{{ statusLabel(record) }}</span>
          <div class="agent-evidence__record-title">
            <strong>{{ record.label || fallbackLabel(record.phase) }}</strong>
            <small>{{ recordMeta(record) }}</small>
          </div>
        </header>

        <p
          v-if="record.reason"
          class="agent-evidence__reason"
        >
          {{ record.reason }}
        </p>

        <div
          v-if="record.items?.length"
          class="agent-evidence__items"
        >
          <article
            v-for="(item, index) in record.items"
            :key="`${item.sourceType || 'source'}-${item.sourceId || item.title || index}`"
            class="agent-evidence__item"
          >
            <RouterLink
              v-if="navigationFor(item)"
              :to="navigationFor(item)!.to"
              :title="sourceTitle(item)"
              class="agent-evidence__source-row"
            >
              <span
                aria-hidden="true"
                class="agent-evidence__source-icon agent-evidence__source-icon--fallback"
              >
                {{ sourceFallback(item) }}
              </span>
              <strong>{{ sourcePrimary(item) }}</strong>
              <span>{{ sourceTitle(item) }}</span>
            </RouterLink>

            <a
              v-else-if="webUrl(item)"
              :href="webUrl(item)"
              :title="sourceTitle(item)"
              class="agent-evidence__source-row"
              rel="noopener noreferrer"
              target="_blank"
            >
              <img
                v-if="faviconUrl(item)"
                :src="faviconUrl(item)"
                alt=""
                class="agent-evidence__source-icon"
                loading="lazy"
              >
              <span
                v-else
                aria-hidden="true"
                class="agent-evidence__source-icon agent-evidence__source-icon--fallback"
              >
                {{ sourceFallback(item) }}
              </span>
              <strong>{{ sourcePrimary(item) }}</strong>
              <span>{{ sourceTitle(item) }}</span>
            </a>

            <div
              v-else
              :title="sourceTitle(item)"
              class="agent-evidence__source-row is-static"
            >
              <span
                aria-hidden="true"
                class="agent-evidence__source-icon agent-evidence__source-icon--fallback"
              >
                {{ sourceFallback(item) }}
              </span>
              <strong>{{ sourcePrimary(item) }}</strong>
              <span>{{ sourceTitle(item) }}</span>
            </div>

            <details
              v-if="canViewIndexInfo && hasIndexInfo(item)"
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
          {{ emptyLabel(record) }}
        </p>
      </section>
    </div>
  </details>
</template>

<script lang="ts" setup>
import {computed} from 'vue'
import {RouterLink} from 'vue-router'
import {ChevronDown} from 'lucide-vue-next'

import type {AgentSearchItem, AgentSearchPayload, AgentSearchRecord} from '@/features/ai/types/ai'
import {useAuthStore} from '@/features/auth/stores/auth'
import {resolveAgentSearchNavigation} from '@/features/ai/utils/agentSearchNavigation'

const props = defineProps<{
  payload?: AgentSearchPayload | null
}>()

const authStore = useAuthStore()
const records = computed(() => normalizeRecords(props.payload))
const canViewIndexInfo = computed(() => authStore.user?.role === 0)
const totalItems = computed(() => records.value.reduce((total, record) => total + (record.items?.length || 0), 0))
const terminalRecords = computed(() => records.value.filter(record => record.phase !== 'started'))
const summaryLabel = computed(() => {
  if (totalItems.value > 0) return `已参考 ${totalItems.value} 条参考资料`
  const recordCount = terminalRecords.value.length || records.value.length
  return recordCount > 0 ? `检索 ${recordCount} 次，未获得可引用结果` : ''
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
      status: event.status,
      reason: event.reason,
      provider: event.provider,
      durationMs: event.durationMs,
      retryable: event.retryable,
      items: event.items?.length ? event.items : previous?.items || [],
    })
  }
  return [...recordsById.values()]
}

function navigationFor(item: AgentSearchItem) {
  if (item.sourceType === 'WEB_SEARCH' || item.sourceType === 'SYSTEM_TIME') return null
  return resolveAgentSearchNavigation(item)
}

function webUrl(item: AgentSearchItem) {
  if (item.sourceType !== 'WEB_SEARCH') return ''
  return textValue(item.metadata?.url) || textValue(item.indexInfo?.url) || textValue(item.sourceId)
}

function sourcePrimary(item: AgentSearchItem) {
  return webDomain(item) || item.sourceLabel || item.contextLabel || item.sourceType || '来源'
}

function sourceTitle(item: AgentSearchItem) {
  return item.title || item.sourceType || '未命名资料'
}

function sourceFallback(item: AgentSearchItem) {
  return sourcePrimary(item).trim().slice(0, 1).toUpperCase()
}

function faviconUrl(item: AgentSearchItem) {
  return textValue(item.metadata?.favicon)
}

function webDomain(item: AgentSearchItem) {
  const url = webUrl(item)
  if (!url) return ''
  try {
    return new URL(url).hostname.replace(/^www\./, '')
  } catch {
    return ''
  }
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

function recordMeta(record: AgentSearchRecord) {
  return [
    record.query,
    record.provider ? `来源 ${record.provider}` : '',
    typeof record.durationMs === 'number' ? `${record.durationMs}ms` : '',
  ].filter(Boolean).join(' · ')
}

function statusLabel(record: AgentSearchRecord) {
  const status = record.status || phaseStatus(record.phase)
  if (status === 'OK') return '完成'
  if (status === 'EMPTY') return '无结果'
  if (status === 'DISABLED') return '未启用'
  if (status === 'MISCONFIGURED') return '未配置'
  if (status === 'FAILED') return '失败'
  return '检索中'
}

function statusTone(record: AgentSearchRecord) {
  const status = record.status || phaseStatus(record.phase)
  if (status === 'OK') return 'ok'
  if (status === 'EMPTY') return 'empty'
  if (status === 'DISABLED' || status === 'MISCONFIGURED' || status === 'FAILED') return 'error'
  return 'pending'
}

function phaseStatus(phase?: string) {
  if (phase === 'results') return 'OK'
  if (phase === 'empty') return 'EMPTY'
  if (phase === 'error') return 'FAILED'
  return 'PENDING'
}

function emptyLabel(record: AgentSearchRecord) {
  if (statusTone(record) === 'error') return record.reason || '检索失败，未获得可引用结果'
  return '没有匹配资料'
}

function fallbackLabel(phase?: string) {
  if (phase === 'empty') return '未找到匹配资料'
  if (phase === 'error') return '检索失败'
  return '检索资料'
}

function textValue(value: unknown) {
  return typeof value === 'string' && value.trim() ? value.trim() : ''
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

.agent-evidence__timeline {
  display: grid;
  gap: 12px;
  padding: 4px 0 8px;
}

.agent-evidence__record {
  display: grid;
  gap: 8px;
}

.agent-evidence__record + .agent-evidence__record {
  padding-top: 10px;
  border-top: 1px solid var(--color-outline-light);
}

.agent-evidence__record-header {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: start;
  gap: 10px;
}

.agent-evidence__status {
  grid-column: 2;
  display: inline-flex;
  min-width: 48px;
  justify-content: center;
  padding: 2px 7px;
  background: var(--color-surface-container);
  border: 1px solid var(--color-outline-light);
  border-radius: 999px;
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 11px;
  line-height: 1.4;
}

.agent-evidence__record--ok .agent-evidence__status {
  color: var(--color-primary);
}

.agent-evidence__record--error .agent-evidence__status {
  color: var(--color-error);
}

.agent-evidence__record-title {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.agent-evidence__record-title strong {
  color: var(--color-on-surface);
  font-family: var(--font-label);
  font-size: 13px;
  line-height: 1.4;
}

.agent-evidence__record-title small {
  overflow: hidden;
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 12px;
  line-height: 1.45;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.agent-evidence__items {
  display: grid;
  gap: 4px;
}

.agent-evidence__item {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.agent-evidence__source-row {
  display: grid;
  min-width: 0;
  min-height: 36px;
  grid-template-columns: 20px max-content minmax(0, 1fr);
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  border-radius: 7px;
  color: var(--color-on-surface);
  font-family: var(--font-body);
  font-size: 14px;
  line-height: 1.35;
  text-decoration: none;
  transition: background 0.18s ease, color 0.18s ease;
}

.agent-evidence__source-row:not(.is-static):hover {
  background: var(--color-surface-container-high);
}

.agent-evidence__source-row:focus-visible {
  outline: 2px solid color-mix(in srgb, var(--color-primary) 54%, transparent);
  outline-offset: 2px;
}

.agent-evidence__source-icon {
  width: 18px;
  height: 18px;
  border-radius: 5px;
  object-fit: cover;
}

.agent-evidence__source-icon--fallback {
  display: grid;
  place-items: center;
  background: var(--color-surface-container-high);
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 11px;
  font-weight: 650;
}

.agent-evidence__source-row strong,
.agent-evidence__source-row span:last-child {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.agent-evidence__source-row strong {
  font-weight: 650;
}

.agent-evidence__source-row span:last-child {
  color: var(--color-on-surface-variant);
}

.agent-evidence__empty,
.agent-evidence__reason {
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 13px;
  line-height: 1.5;
}

.agent-evidence__empty,
.agent-evidence__reason {
  margin: 0;
}

.agent-evidence__empty,
.agent-evidence__reason {
  padding: 10px 12px;
  background: var(--color-surface-container);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
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

@media (max-width: 640px) {
  .agent-evidence__record-header {
    grid-template-columns: minmax(0, 1fr);
  }

  .agent-evidence__status {
    grid-column: 1;
    width: fit-content;
  }

  .agent-evidence__source-row {
    grid-template-columns: 20px minmax(0, 1fr);
  }

  .agent-evidence__source-row span:last-child {
    grid-column: 2;
  }
}
</style>
