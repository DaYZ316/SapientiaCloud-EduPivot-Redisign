<template>
  <div :class="variant" aria-hidden="true" class="course-tab-loading-skeleton">
    <template v-if="variant === 'practice'">
      <section v-for="panel in count" :key="panel" class="practice-panel">
        <div class="practice-panel-header">
          <div class="skeleton-copy">
            <div class="skeleton-line label"></div>
            <div class="skeleton-line title"></div>
            <div class="skeleton-line meta"></div>
          </div>
          <div class="skeleton-pill"></div>
        </div>
        <div class="practice-card-list">
          <article v-for="card in 2" :key="card" class="practice-card">
            <div class="skeleton-copy">
              <div class="skeleton-line label"></div>
              <div class="skeleton-line title wide"></div>
            </div>
            <div class="skeleton-metrics">
              <div class="skeleton-pill"></div>
              <div class="skeleton-pill"></div>
              <div class="skeleton-pill action"></div>
            </div>
          </article>
        </div>
      </section>
    </template>

    <template v-else>
      <div v-for="row in count" :key="row" class="skeleton-row">
        <div v-if="avatar" class="skeleton-avatar"></div>
        <div class="skeleton-copy">
          <div class="skeleton-line title"></div>
          <div class="skeleton-line meta"></div>
        </div>
        <div v-if="actions > 0" class="skeleton-actions">
          <div v-for="action in actions" :key="action" class="skeleton-action"></div>
        </div>
      </div>
    </template>
  </div>
</template>

<script lang="ts" setup>
withDefaults(defineProps<{
  variant?: 'list' | 'practice'
  count?: number
  avatar?: boolean
  actions?: number
}>(), {
  variant: 'list',
  count: 4,
  avatar: false,
  actions: 1,
})
</script>

<style scoped>
.course-tab-loading-skeleton {
  display: grid;
  gap: 10px;
}

.skeleton-row,
.practice-panel,
.practice-card {
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
}

.skeleton-row {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 62px;
  padding: 10px 12px;
}

.skeleton-avatar {
  width: 42px;
  height: 42px;
  flex: 0 0 auto;
  border-radius: 50%;
}

.skeleton-copy {
  display: grid;
  flex: 1;
  gap: 8px;
  min-width: 0;
}

.skeleton-line,
.skeleton-pill,
.skeleton-action,
.skeleton-avatar {
  background: linear-gradient(
      110deg,
      var(--color-surface-container-high) 8%,
      var(--color-surface-canvas) 18%,
      var(--color-surface-container-high) 33%
  );
  background-size: 200% 100%;
  animation: tab-skeleton-shimmer 1.4s ease-in-out infinite;
}

.skeleton-line {
  height: 12px;
  border-radius: var(--radius-sm);
}

.skeleton-line.label {
  width: 96px;
  height: 10px;
}

.skeleton-line.title {
  width: min(280px, 72%);
  height: 16px;
}

.skeleton-line.title.wide {
  width: min(420px, 82%);
}

.skeleton-line.meta {
  width: min(220px, 54%);
}

.skeleton-actions,
.skeleton-metrics {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  flex: 0 0 auto;
}

.skeleton-action {
  width: 32px;
  height: 32px;
  border-radius: var(--radius-sm);
}

.skeleton-pill {
  width: 74px;
  height: 28px;
  border-radius: var(--radius-sm);
}

.skeleton-pill.action {
  width: 92px;
}

.practice {
  gap: 14px;
}

.practice-panel {
  display: grid;
  gap: 12px;
  padding: 16px;
}

.practice-panel-header {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--color-outline-light);
}

.practice-card-list {
  display: grid;
  gap: 14px;
}

.practice-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 16px;
}

@keyframes tab-skeleton-shimmer {
  0% {
    background-position: 200% 0;
  }
  100% {
    background-position: -200% 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .skeleton-line,
  .skeleton-pill,
  .skeleton-action,
  .skeleton-avatar {
    animation: none;
  }
}

@media (max-width: 720px) {
  .skeleton-row,
  .practice-panel-header,
  .practice-card,
  .skeleton-metrics {
    align-items: flex-start;
    flex-direction: column;
  }

  .skeleton-actions,
  .skeleton-metrics {
    width: 100%;
    justify-content: flex-start;
  }
}
</style>
