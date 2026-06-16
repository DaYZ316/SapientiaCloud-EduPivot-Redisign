<template>
  <div :class="[variant]" class="skeleton-wrapper">
    <!-- Card Skeleton -->
    <template v-if="variant === 'card'">
      <div v-for="n in count" :key="n" class="skeleton-card">
        <div class="skeleton-card-header">
          <div class="skeleton-avatar"></div>
          <div class="skeleton-badge"></div>
        </div>
        <div class="skeleton-card-content">
          <div class="skeleton-line skeleton-title"></div>
          <div class="skeleton-line skeleton-subtitle"></div>
          <div class="skeleton-details">
            <div class="skeleton-detail-item">
              <div class="skeleton-line skeleton-label"></div>
              <div class="skeleton-line skeleton-value"></div>
            </div>
            <div class="skeleton-detail-item">
              <div class="skeleton-line skeleton-label"></div>
              <div class="skeleton-line skeleton-value"></div>
            </div>
          </div>
        </div>
      </div>
    </template>

    <!-- Table Skeleton -->
    <template v-else-if="variant === 'table'">
      <div :style="{'--cols': String(columns)}" class="skeleton-table">
        <div class="skeleton-table-header">
          <div v-for="col in columns" :key="col" class="skeleton-table-cell skeleton-header-cell"></div>
        </div>
        <div v-for="n in count" :key="n" class="skeleton-table-row">
          <div v-for="col in columns" :key="col" class="skeleton-table-cell">
            <div class="skeleton-line"></div>
          </div>
        </div>
      </div>
    </template>

    <!-- Spinner (inline) -->
    <template v-else-if="variant === 'spinner'">
      <div class="skeleton-spinner">
        <div class="spinner"></div>
        <span v-if="label" class="spinner-label">{{ label }}</span>
      </div>
    </template>
  </div>
</template>

<script lang="ts" setup>
interface Props {
  variant?: 'card' | 'table' | 'spinner'
  count?: number
  columns?: number
  label?: string
}

withDefaults(defineProps<Props>(), {
  variant: 'card',
  count: 6,
  columns: 7,
  label: '',
})
</script>

<style scoped>
.skeleton-wrapper {
  width: 100%;
}

/* Card variant */
.skeleton-wrapper.card {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 24px;
  margin-bottom: 32px;
}

.skeleton-card {
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: 24px;
  overflow: hidden;
}

.skeleton-card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 24px 24px 0;
}

.skeleton-avatar {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: var(--color-surface-canvas);
  animation: pulse 1.5s ease-in-out infinite;
}

.skeleton-badge {
  width: 60px;
  height: 24px;
  border-radius: var(--radius-sm);
  background: var(--color-surface-canvas);
  animation: pulse 1.5s ease-in-out infinite;
}

.skeleton-card-content {
  padding: 16px 24px 24px;
}

.skeleton-line {
  height: 14px;
  background: var(--color-surface-canvas);
  border-radius: 8px;
  animation: pulse 1.5s ease-in-out infinite;
}

.skeleton-title {
  width: 60%;
  height: 20px;
  margin-bottom: 8px;
}

.skeleton-subtitle {
  width: 80%;
  margin-bottom: 20px;
}

.skeleton-details {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.skeleton-detail-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.skeleton-label {
  width: 50%;
  height: 11px;
}

.skeleton-value {
  width: 70%;
}

/* Table variant */
.skeleton-table {
  --cols: 7;

  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: 32px;
  overflow: hidden;
  margin-bottom: 32px;
}

.skeleton-table-header {
  display: grid;
  grid-template-columns: repeat(var(--cols, 7), 1fr);
  gap: 20px;
  padding: 16px 20px;
  background: var(--color-surface-canvas);
  border-bottom: 1px solid var(--color-outline-light);
}

.skeleton-table-row {
  display: grid;
  grid-template-columns: repeat(var(--cols, 7), 1fr);
  gap: 20px;
  padding: 16px 20px;
  border-bottom: 1px solid var(--color-surface-canvas);
}

.skeleton-table-row:last-child {
  border-bottom: none;
}

.skeleton-header-cell {
  height: 14px;
  background: var(--color-surface-card);
  border-radius: 8px;
  animation: pulse 1.5s ease-in-out infinite;
}

.skeleton-table-cell .skeleton-line {
  height: 14px;
  animation: pulse 1.5s ease-in-out infinite;
}

/* Spinner variant */
.skeleton-spinner {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 32px;
}

.spinner {
  width: 40px;
  height: 40px;
  border: 3px solid var(--color-outline-light);
  border-top-color: var(--color-primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

.spinner-label {
  margin-top: 16px;
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 14px;
  color: var(--color-muted);
}

/* Animations */
@keyframes pulse {
  0%, 100% {
    opacity: 1;
  }
  50% {
    opacity: 0.4;
  }
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
