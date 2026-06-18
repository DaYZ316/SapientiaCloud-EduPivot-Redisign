<template>
  <section class="ai-question-tools">
    <div>
      <span>Celestial Hub</span>
      <h2>AI 题库工具</h2>
      <p>AI 生成题目会先进入草稿预览，由教师确认后再写入题库。当前一期仅保留入口与交互位置。</p>
    </div>
    <div class="tool-actions">
      <button
        disabled
        type="button"
      >
        生成题目
      </button>
      <button
        disabled
        type="button"
      >
        补全解析
      </button>
      <button
        disabled
        type="button"
      >
        难度校准
      </button>
    </div>
  </section>
</template>

<style scoped>
.ai-question-tools {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 18px;
  padding: 16px;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  color: var(--color-on-surface);
}

.ai-question-tools span {
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 11px;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.ai-question-tools h2 {
  margin: 5px 0 0;
  font-family: var(--font-heading);
  font-size: 24px;
  font-weight: 400;
}

.ai-question-tools p {
  max-width: 72ch;
  margin: 8px 0 0;
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 14px;
  line-height: 1.55;
}

.tool-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.tool-actions button {
  min-height: 36px;
  padding: 0 12px;
  background: transparent;
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  color: var(--color-muted);
  cursor: not-allowed;
  font-family: var(--font-label);
  font-size: 12px;
}

@media (max-width: 760px) {
  .ai-question-tools {
    align-items: stretch;
    flex-direction: column;
  }

  .tool-actions {
    justify-content: flex-start;
  }
}
</style>
