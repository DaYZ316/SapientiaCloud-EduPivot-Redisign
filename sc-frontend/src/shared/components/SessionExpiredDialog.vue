<template>
  <Teleport to="body">
    <div v-if="visible" class="modal-overlay session-expired-overlay">
      <section class="modal session-expired-modal" role="dialog" aria-modal="true" aria-labelledby="session-expired-title">
        <div class="modal-header">
          <h2 id="session-expired-title">{{ t('common.sessionExpired.title') }}</h2>
        </div>
        <div class="modal-body">
          <p class="session-expired-message">{{ t('common.sessionExpired.message') }}</p>
          <div class="modal-actions">
            <button class="btn-primary" type="button" @click="confirm">
              {{ t('common.sessionExpired.confirm') }}
            </button>
          </div>
        </div>
      </section>
    </div>
  </Teleport>
</template>

<script lang="ts" setup>
import {useI18n} from 'vue-i18n'

import {router} from '@/app/router'
import {useAuthStore} from '@/features/auth/stores/auth'
import {closeSessionExpiredDialog, useSessionExpiredDialog} from '@/shared/composables/useSessionExpiredDialog'

const {t} = useI18n()
const authStore = useAuthStore()
const {visible} = useSessionExpiredDialog()

async function confirm() {
  closeSessionExpiredDialog()
  authStore.clearSession()
  await router.replace({
    name: 'login',
    query: router.currentRoute.value.name === 'login'
      ? undefined
      : {redirect: router.currentRoute.value.fullPath},
  })
}
</script>

<style scoped>
.session-expired-overlay {
  z-index: 2600;
}

.session-expired-modal {
  max-width: 440px;
}

.session-expired-message {
  margin: 0;
  color: var(--color-secondary);
  font-family: var(--font-body);
  font-size: 16px;
  line-height: 1.6;
}
</style>
