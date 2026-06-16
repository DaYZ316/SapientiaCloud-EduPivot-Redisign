<template>
  <div :aria-label="label" aria-live="polite" class="notification-loading-layout" role="status">
    <div aria-hidden="true" class="notification-loading-list">
      <div v-for="item in itemCount" :key="item" class="notification-loading-card">
        <div class="skeleton-block notification-loading-icon"></div>
        <div class="notification-loading-content">
          <div class="notification-loading-header">
            <div class="skeleton-block notification-loading-title"></div>
            <div class="skeleton-block notification-loading-time"></div>
          </div>
          <div class="skeleton-block notification-loading-line"></div>
          <div class="skeleton-block notification-loading-line short"></div>
          <div class="skeleton-block notification-loading-tag"></div>
        </div>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
withDefaults(defineProps<{
  itemCount?: number
  label?: string
}>(), {
  itemCount: 6,
  label: 'Loading notifications',
})
</script>

<style scoped>
.notification-loading-layout {
  width: 100%;
}

.notification-loading-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.notification-loading-card {
  display: flex;
  align-items: flex-start;
  gap: 20px;
  min-height: 136px;
  padding: 24px;
  border: 1px solid var(--color-outline-variant);
  border-radius: 20px;
  background: var(--color-surface-card);
}

.notification-loading-icon {
  width: 48px;
  height: 48px;
  border-radius: var(--radius-sm);
  flex-shrink: 0;
}

.notification-loading-content {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding-top: 2px;
}

.notification-loading-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
}

.notification-loading-title {
  width: min(360px, 62%);
  height: 24px;
}

.notification-loading-time {
  width: 92px;
  height: 14px;
  flex-shrink: 0;
}

.notification-loading-line {
  width: min(560px, 88%);
  height: 14px;
}

.notification-loading-line.short {
  width: min(420px, 68%);
}

.notification-loading-tag {
  width: 88px;
  height: 26px;
  margin-top: 2px;
  border-radius: var(--radius-sm);
}

.skeleton-block {
  position: relative;
  overflow: hidden;
  background: var(--color-surface-container);
  border-radius: 999px;
}

.skeleton-block::after {
  content: '';
  position: absolute;
  inset: 0;
  transform: translateX(-100%);
  background: linear-gradient(
      90deg,
      transparent,
      color-mix(in srgb, var(--color-on-surface) 8%, transparent),
      transparent
  );
  animation: skeleton-shimmer 1.4s ease-in-out infinite;
}

@keyframes skeleton-shimmer {
  100% {
    transform: translateX(100%);
  }
}

@media (max-width: 768px) {
  .notification-loading-card {
    flex-direction: column;
    gap: 14px;
    min-height: 190px;
    padding: 20px;
    border-radius: 16px;
  }

  .notification-loading-icon {
    width: 36px;
    height: 36px;
  }

  .notification-loading-header {
    align-items: flex-start;
    flex-direction: column;
    gap: 10px;
  }

  .notification-loading-title {
    width: 82%;
    height: 20px;
  }

  .notification-loading-time {
    width: 76px;
  }

  .notification-loading-line,
  .notification-loading-line.short {
    width: 100%;
  }
}
</style>
