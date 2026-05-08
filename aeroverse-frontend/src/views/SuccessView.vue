<template>
  <main class="success-page">
    <header class="success-header">
      <div>
        <p class="eyebrow">{{ t('success.eyebrow') }}</p>
        <h1>{{ t('success.title') }}</h1>
        <p class="subtitle">
          {{ t('success.subtitle') }}
        </p>
      </div>
      <NButton secondary @click="handleLogout">
        <template #icon>
          <LogOut :size="18" />
        </template>
        {{ t('success.logout') }}
      </NButton>
    </header>

    <section class="success-grid">
      <NCard :bordered="false" class="profile-card">
        <div class="avatar">
          <img v-if="authStore.user?.avatarUrl" :src="authStore.user.avatarUrl" alt="" />
          <UserRound v-else :size="34" />
        </div>
        <h2>{{ authStore.user?.displayName ?? '-' }}</h2>
        <p>{{ authStore.user?.email ?? '-' }}</p>
        <NTag type="success" round>{{ authStore.user?.status ?? '-' }}</NTag>
      </NCard>

      <NCard :bordered="false" class="status-card">
        <template #header>
          <span>{{ t('success.auditTitle') }}</span>
        </template>
        <div class="status-list">
          <div>
            <CheckCircle2 :size="20" />
            <span>{{ t('success.createdProvider') }}: {{ authStore.user?.createdProvider ?? '-' }}</span>
          </div>
          <div>
            <CheckCircle2 :size="20" />
            <span>{{ t('success.createdIp') }}: {{ authStore.user?.createdIp ?? '-' }}</span>
          </div>
          <div>
            <CheckCircle2 :size="20" />
            <span>{{ t('success.lastLoginProvider') }}: {{ authStore.user?.lastLoginProvider ?? '-' }}</span>
          </div>
          <div>
            <CheckCircle2 :size="20" />
            <span>{{ t('success.lastLoginIp') }}: {{ authStore.user?.lastLoginIp ?? '-' }}</span>
          </div>
          <div>
            <CheckCircle2 :size="20" />
            <span>{{ t('success.loginCount') }}: {{ authStore.user?.loginCount ?? 0 }}</span>
          </div>
        </div>
      </NCard>
    </section>
  </main>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { CheckCircle2, LogOut, UserRound } from 'lucide-vue-next'
import { NButton, NCard, NTag } from 'naive-ui'

import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const { t } = useI18n()

async function handleLogout() {
  authStore.logout()
  await router.push('/login')
}
</script>
