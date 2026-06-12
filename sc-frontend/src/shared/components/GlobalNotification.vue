<template>
  <Teleport to="body">
    <div class="global-notification-region" role="region" aria-live="polite" aria-atomic="false">
      <TransitionGroup name="global-notification" tag="div" class="global-notification-stack">
        <article
          v-for="item in notifications"
          :key="item.id"
          class="global-notification-item"
          :class="item.type"
          :style="notificationStyle(item)"
          role="status"
        >
          <div class="global-notification-mark" aria-hidden="true">
            <component :is="iconMap[item.type]" :size="18" stroke-width="2"/>
          </div>

          <span class="global-notification-message">{{ item.title || item.message || typeLabel(item.type) }}</span>
        </article>
      </TransitionGroup>
    </div>
  </Teleport>
</template>

<script lang="ts" setup>
import {AlertTriangle, CheckCircle2, Info, XCircle} from 'lucide-vue-next'
import {useI18n} from 'vue-i18n'

import type {GlobalNotificationItem, GlobalNotificationType} from '@/shared/composables/useGlobalNotification'
import {useGlobalNotification} from '@/shared/composables/useGlobalNotification'

const {t} = useI18n()
const {notifications} = useGlobalNotification()

const iconMap: Record<GlobalNotificationType, typeof CheckCircle2> = {
  success: CheckCircle2,
  warn: AlertTriangle,
  info: Info,
  error: XCircle,
}

function typeLabel(type: GlobalNotificationType) {
  return t(`common.feedback.${type}`)
}

function notificationStyle(item: GlobalNotificationItem) {
  return {
    '--notification-duration': `${item.duration}ms`,
  }
}
</script>

<style scoped>
.global-notification-region {
  position: fixed;
  top: 16px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 2400;
  width: auto;
  max-width: calc(100vw - 32px);
  pointer-events: none;
}

.global-notification-stack {
  display: grid;
  gap: 8px;
  justify-items: center;
}

.global-notification-item {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 10px;
  max-width: min(420px, calc(100vw - 32px));
  padding: 10px 22px;
  overflow: visible;
  pointer-events: auto;
  background: var(--color-primary);
  border: 1px solid var(--color-on-primary);
  border-radius: 9999px;
  color: var(--color-on-primary);
  box-shadow: 0 8px 18px rgba(0, 0, 0, 0.18);
}

.global-notification-mark {
  width: 18px;
  height: 18px;
  display: grid;
  place-items: center;
  flex: 0 0 auto;
}

.global-notification-item.error .global-notification-mark {
  color: var(--color-error);
}

.global-notification-message {
  min-width: 0;
  overflow: hidden;
  color: inherit;
  font-family: var(--font-label);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  line-height: 1;
  text-overflow: ellipsis;
  text-transform: uppercase;
  white-space: nowrap;
}

.global-notification-enter-active,
.global-notification-leave-active {
  transition: opacity 0.24s ease, transform 0.24s ease;
}

.global-notification-enter-from,
.global-notification-leave-to {
  opacity: 0;
  transform: translateY(-12px);
}

:global(:root[data-theme='light']) .global-notification-item {
  background: var(--color-primary);
  border-color: var(--color-on-primary);
  color: var(--color-on-primary);
}

@media (max-width: 640px) {
  .global-notification-region {
    top: 16px;
    left: 16px;
    transform: none;
    width: auto;
  }
}
</style>
