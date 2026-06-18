<template>
  <aside class="ai-live-summary-panel">
    <header>
      <div>
        <span>Live Summary</span>
        <h2>实时总结</h2>
      </div>
      <button
        title="关闭"
        type="button"
        @click="$emit('close')"
      >
        <X
          :size="18"
          stroke-width="1.8"
        />
      </button>
    </header>

    <section class="summary-state">
      <Sparkles
        :size="30"
        stroke-width="1.5"
      />
      <h3>直播总结管道待接入</h3>
      <p>这里将承载 ASR 文本、时间线摘要、关键知识点和一键生成随堂练习。当前一期只保留课堂伴随面板入口。</p>
    </section>

    <div class="timeline-placeholder">
      <article>
        <span>00:00</span>
        <strong>等待课堂音频转写</strong>
      </article>
      <article>
        <span>05:00</span>
        <strong>摘要卡片将在这里流式出现</strong>
      </article>
    </div>
  </aside>
</template>

<script lang="ts" setup>
import {Sparkles, X} from 'lucide-vue-next'

defineEmits<{ close: [] }>()
</script>

<style scoped>
.ai-live-summary-panel {
  position: fixed;
  top: 24px;
  right: 24px;
  bottom: 84px;
  z-index: 2150;
  display: flex;
  width: min(420px, calc(100vw - 32px));
  flex-direction: column;
  overflow: hidden;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline);
  color: var(--color-on-surface);
  box-shadow: 0 24px 64px rgba(15, 23, 42, 0.24);
}

header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 16px;
  border-bottom: 1px solid var(--color-outline-light);
}

header span,
.timeline-placeholder span {
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 11px;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

header h2 {
  margin: 5px 0 0;
  font-family: var(--font-heading);
  font-size: 26px;
  font-weight: 400;
}

header button {
  display: grid;
  width: 36px;
  height: 36px;
  place-items: center;
  background: transparent;
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  color: var(--color-on-surface);
  cursor: pointer;
}

.summary-state {
  display: grid;
  gap: 10px;
  padding: 32px 18px;
  border-bottom: 1px solid var(--color-outline-light);
  color: var(--color-muted);
  text-align: center;
}

.summary-state h3 {
  margin: 0;
  color: var(--color-on-surface);
  font-family: var(--font-heading);
  font-size: 24px;
  font-weight: 400;
}

.summary-state p {
  margin: 0;
  font-family: var(--font-body);
  font-size: 14px;
  line-height: 1.55;
}

.timeline-placeholder {
  display: grid;
  gap: 10px;
  padding: 16px;
  overflow-y: auto;
}

.timeline-placeholder article {
  display: grid;
  gap: 6px;
  padding: 12px;
  background: var(--color-surface-container);
  border: 1px solid var(--color-outline-light);
}

.timeline-placeholder strong {
  font-family: var(--font-body);
  font-size: 14px;
  font-weight: 600;
}

@media (max-width: 640px) {
  .ai-live-summary-panel {
    inset: 12px;
    width: auto;
  }
}
</style>
