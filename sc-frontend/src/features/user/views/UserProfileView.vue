<template>
  <div class="profile-page">
    <button class="back-btn" type="button" @click="goBack">
      <ArrowLeft :size="18" stroke-width="1.8"/>
      {{ t('profile.userProfile.back') }}
    </button>

    <!-- Loading state -->
    <section v-if="loading" class="profile-hero">
      <div class="profile-avatar skeleton-block"></div>
      <div class="profile-intro">
        <div class="skeleton-line skeleton-line--short"></div>
        <div class="skeleton-line skeleton-line--long"></div>
        <div class="skeleton-line skeleton-line--medium"></div>
      </div>
    </section>

    <!-- Error state -->
    <section v-else-if="error" class="error-state">
      <AlertCircle :size="48" stroke-width="1.2"/>
      <h2>{{ t('profile.userProfile.notFound') }}</h2>
      <p>{{ error }}</p>
      <router-link class="btn-primary" to="/dashboard">{{ t('profile.userProfile.backToDashboard') }}</router-link>
    </section>

    <!-- Profile content -->
    <template v-else-if="user">
      <section class="profile-hero">
        <div :class="{ 'profile-avatar--image': Boolean(user.avatarUrl) }" class="profile-avatar">
          <img v-if="user.avatarUrl" :alt="`${displayName} avatar`" :src="user.avatarUrl"/>
          <span v-else>{{ initials }}</span>
        </div>

        <div class="profile-intro">
          <div class="profile-title-row">
            <h1>{{ displayName }}</h1>
            <span class="role-pill">{{ roleLabel }}</span>
          </div>
          <p class="profile-bio">{{ profileBio }}</p>
          <div class="profile-contact">
            <span>
              <CalendarDays :size="15" stroke-width="1.8"/>
              {{ t('profile.userProfile.joined') }} {{ formatMonth(user.createdAt) }}
            </span>
          </div>
        </div>
      </section>

      <section class="profile-shell">
        <div class="profile-main-column">
          <!-- Role-specific identity panel -->
          <section v-if="profileKind !== 'user'" class="profile-panel role-identity">
            <div class="panel-heading">
              <div class="panel-icon">
                <component :is="roleConfig.icon" :size="20" stroke-width="1.8"/>
              </div>
              <div>
                <p class="panel-label">{{ roleConfig.identityLabel }}</p>
                <h2>{{ roleConfig.identityTitle }}</h2>
              </div>
            </div>

            <div class="identity-grid">
              <div
                  v-for="item in identityDetails"
                  :key="item.label"
                  :class="{ 'identity-item--wide': item.wide }"
                  class="identity-item"
              >
                <span>{{ item.label }}</span>
                <strong>{{ item.value }}</strong>
              </div>
            </div>
          </section>

          <!-- Bio panel for generic users -->
          <section v-else class="profile-panel">
            <div class="panel-heading">
              <div class="panel-icon">
                <UserRound :size="20" stroke-width="1.8"/>
              </div>
              <div>
                <p class="panel-label">{{ t('profile.userProfile.aboutLabel') }}</p>
                <h2>{{ t('profile.userProfile.aboutTitle') }}</h2>
              </div>
            </div>
            <p class="about-text">{{ user.bio || t('profile.userProfile.noBio') }}</p>
          </section>
        </div>

        <aside class="profile-side-column">
          <section class="profile-panel details-panel">
            <div class="section-header section-header--compact">
              <div>
                <p class="panel-label">{{ t('profile.userProfile.infoLabel') }}</p>
                <h2>{{ t('profile.userProfile.infoTitle') }}</h2>
              </div>
              <Info :size="20" stroke-width="1.8"/>
            </div>

            <div class="detail-list">
              <div v-for="item in publicDetails" :key="item.label" class="detail-row">
                <span>{{ item.label }}</span>
                <strong>{{ item.value }}</strong>
              </div>
            </div>
          </section>
        </aside>
      </section>
    </template>
  </div>
</template>

<script lang="ts" setup>
import {computed, onMounted, ref, watch} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import {useI18n} from 'vue-i18n'
import {AlertCircle, ArrowLeft, BriefcaseBusiness, CalendarDays, GraduationCap, Info, UserRound,} from 'lucide-vue-next'

import {getUserById} from '@/features/user/api/user'
import type {UserProfile} from '@/features/user/types/user'

type ProfileKind = 'user' | 'student' | 'teacher'

interface DetailItem {
  label: string
  value: string
  wide?: boolean
}

const route = useRoute()
const router = useRouter()
const {t} = useI18n()

function goBack() {
  if (window.history.length > 1) {
    router.back()
  } else {
    router.push({name: 'dashboard'})
  }
}

const user = ref<UserProfile | null>(null)
const loading = ref(true)
const error = ref('')

const profileKind = computed<ProfileKind>(() => {
  if (user.value?.role === 1 || user.value?.studentInfo) return 'student'
  if (user.value?.role === 2 || user.value?.teacherInfo) return 'teacher'
  return 'user'
})

const roleConfig = computed(() => {
  if (profileKind.value === 'student') {
    return {
      icon: GraduationCap,
      identityLabel: t('profile.userProfile.identity.studentLabel'),
      identityTitle: t('profile.userProfile.identity.studentTitle'),
    }
  }
  if (profileKind.value === 'teacher') {
    return {
      icon: BriefcaseBusiness,
      identityLabel: t('profile.userProfile.identity.teacherLabel'),
      identityTitle: t('profile.userProfile.identity.teacherTitle'),
    }
  }
  return {
    icon: UserRound,
    identityLabel: t('profile.userProfile.identity.userLabel'),
    identityTitle: t('profile.userProfile.identity.userTitle'),
  }
})

const displayName = computed(() => user.value?.displayName || 'User')

const roleLabel = computed(() => {
  switch (user.value?.role) {
    case 0:
      return 'ADMIN'
    case 1:
      return 'STUDENT'
    case 2:
      return 'TEACHER'
    default:
      return 'USER'
  }
})

const initials = computed(() => {
  const source = displayName.value || user.value?.email || 'User'
  const words = source.trim().split(/\s+/).filter(Boolean)
  if (words.length >= 2) return `${words[0][0]}${words[1][0]}`.toUpperCase()
  return source.slice(0, 2).toUpperCase()
})

const profileBio = computed(() => {
  if (user.value?.bio) return user.value.bio
  if (profileKind.value === 'student') return t('profile.userProfile.bio.student')
  if (profileKind.value === 'teacher') return t('profile.userProfile.bio.teacher')
  return t('profile.userProfile.bio.user')
})

const identityDetails = computed<DetailItem[]>(() => {
  if (profileKind.value === 'student') {
    const info = user.value?.studentInfo
    return [
      {label: t('profile.userProfile.identityFields.studentNo'), value: valueOrDash(info?.studentNo)},
      {label: t('profile.userProfile.identityFields.grade'), value: valueOrDash(info?.grade)},
      {label: t('profile.userProfile.identityFields.major'), value: valueOrDash(info?.major)},
      {label: t('profile.userProfile.identityFields.school'), value: valueOrDash(info?.school), wide: true},
    ]
  }
  if (profileKind.value === 'teacher') {
    const info = user.value?.teacherInfo
    return [
      {label: t('profile.userProfile.identityFields.employeeNo'), value: valueOrDash(info?.employeeNo)},
      {label: t('profile.userProfile.identityFields.department'), value: valueOrDash(info?.department)},
      {label: t('profile.userProfile.identityFields.title'), value: valueOrDash(info?.title)},
      {label: t('profile.userProfile.identityFields.school'), value: valueOrDash(info?.school), wide: true},
    ]
  }
  return []
})

const publicDetails = computed<DetailItem[]>(() => [
  {label: t('profile.userProfile.publicFields.role'), value: roleLabel.value},
  {label: t('profile.userProfile.publicFields.joined'), value: formatMonth(user.value?.createdAt)},
])

async function loadUser() {
  const userId = route.params.userId as string
  if (!userId) {
    error.value = t('profile.userProfile.noUserId')
    loading.value = false
    return
  }

  loading.value = true
  error.value = ''
  try {
    user.value = await getUserById(userId)
  } catch {
    user.value = null
    error.value = t('profile.userProfile.fetchError')
  } finally {
    loading.value = false
  }
}

function valueOrDash(value?: string | number | null): string {
  if (value === undefined || value === null || value === '') return '-'
  return String(value)
}

function formatMonth(dateStr?: string | null): string {
  if (!dateStr) return '-'
  return new Intl.DateTimeFormat('en-US', {month: 'short', year: 'numeric'}).format(new Date(dateStr))
}

onMounted(loadUser)

watch(() => route.params.userId, (newId) => {
  if (newId) loadUser()
})
</script>

<style scoped>
.profile-page {
  display: flex;
  flex-direction: column;
  gap: 24px;
  color: var(--color-on-surface);
}

.back-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  align-self: flex-start;
  min-height: 36px;
  padding: 6px 14px;
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  background: var(--color-surface-card);
  color: var(--color-on-surface);
  font-family: var(--font-body);
  font-size: 14px;
  font-weight: 400;
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease;
}

.back-btn:hover {
  background: var(--color-surface-container);
  border-color: var(--color-outline);
}

.profile-hero,
.profile-panel {
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-xl);
}

.profile-hero {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 28px;
  align-items: center;
  padding: 32px;
}

.profile-avatar {
  width: 104px;
  height: 104px;
  display: grid;
  place-items: center;
  overflow: hidden;
  border: 1px solid var(--color-primary);
  border-radius: 32px;
  background: var(--color-primary);
  color: var(--color-on-primary);
  font-family: var(--font-heading);
  font-size: 36px;
  font-weight: 700;
  letter-spacing: -0.02em;
  font-variant-numeric: tabular-nums;
}

.profile-avatar--image {
  background: var(--color-surface-canvas);
}

.profile-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.profile-intro {
  min-width: 0;
}

.panel-label {
  margin: 0 0 8px;
  font-family: var(--font-label);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.1em;
  color: var(--color-muted);
  text-transform: uppercase;
}

.profile-title-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.profile-title-row h1 {
  margin: 0;
  font-family: var(--font-heading);
  font-size: clamp(32px, 5vw, 48px);
  font-weight: 400;
  line-height: 1.3;
  letter-spacing: 0;
  text-wrap: balance;
}

.role-pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 28px;
  padding: 5px 12px;
  border-radius: var(--radius-pill);
  background: var(--color-primary);
  color: var(--color-on-primary);
  font-family: var(--font-label);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  white-space: nowrap;
}

.profile-bio {
  max-width: 720px;
  margin: 0 0 18px;
  color: var(--color-on-surface-variant);
  font-family: var(--font-body);
  font-size: 16px;
  line-height: 1.65;
  text-wrap: pretty;
}

.profile-contact {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.profile-contact span {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  min-height: 34px;
  padding: 7px 12px;
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-pill);
  color: var(--color-muted);
  font-size: 14px;
}

.profile-shell {
  display: grid;
  grid-template-columns: minmax(0, 1.48fr) minmax(320px, 0.72fr);
  gap: 24px;
  align-items: start;
}

.profile-main-column,
.profile-side-column {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.profile-panel {
  padding: 28px;
}

.panel-heading {
  display: flex;
  justify-content: flex-start;
  align-items: center;
  gap: 18px;
  margin-bottom: 24px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  align-items: flex-start;
  margin-bottom: 24px;
}

.section-header--compact {
  align-items: center;
  margin-bottom: 18px;
}

.panel-heading h2,
.section-header h2 {
  margin: 0;
  font-family: var(--font-heading);
  font-size: 28px;
  font-weight: 400;
  line-height: 1.1;
  letter-spacing: 0;
}

.panel-icon {
  width: 44px;
  height: 44px;
  display: grid;
  place-items: center;
  border: 1px solid var(--color-primary);
  border-radius: 16px;
  background: var(--color-primary);
  color: var(--color-on-primary);
  flex: 0 0 auto;
}

.identity-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.identity-item,
.detail-row {
  min-width: 0;
  border: 1px solid var(--color-outline-light);
  background: var(--color-surface-canvas);
}

.identity-item {
  padding: 18px;
  border-radius: var(--radius-md);
}

.identity-item--wide {
  grid-column: 1 / -1;
}

.identity-item span,
.detail-row span {
  display: block;
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 12px;
  font-weight: 400;
  margin-bottom: 7px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.identity-item strong,
.detail-row strong {
  display: block;
  overflow-wrap: anywhere;
  color: var(--color-on-surface);
  font-family: var(--font-body);
  font-size: 15px;
  font-weight: 700;
}

.detail-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.detail-row {
  display: grid;
  grid-template-columns: minmax(96px, 0.65fr) minmax(0, 1fr);
  gap: 12px;
  align-items: center;
  padding: 13px 14px;
  border-radius: var(--radius-sm);
}

.detail-row span {
  margin-bottom: 0;
}

.detail-row strong {
  text-align: right;
}

.about-text {
  margin: 0;
  color: var(--color-on-surface-variant);
  font-family: var(--font-body);
  font-size: 16px;
  line-height: 1.65;
}

/* Error state */
.error-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  padding: 80px 24px;
  text-align: center;
  color: var(--color-muted);
}

.error-state h2 {
  margin: 0;
  font-family: var(--font-heading);
  font-size: 28px;
  font-weight: 400;
  color: var(--color-on-surface);
}

.error-state p {
  margin: 0;
  font-family: var(--font-body);
  font-size: 15px;
}

.error-state .btn-primary {
  margin-top: 8px;
  text-decoration: none;
}

/* Skeleton */
.skeleton-block {
  background: linear-gradient(90deg, var(--color-surface-container) 0%, var(--color-surface-container-high) 45%, var(--color-surface-container) 100%);
  background-size: 220% 100%;
  animation: shimmer 1.4s ease-in-out infinite;
}

.skeleton-line {
  height: 18px;
  border-radius: var(--radius-sm);
  margin-bottom: 12px;
  background: linear-gradient(90deg, var(--color-surface-container) 0%, var(--color-surface-container-high) 45%, var(--color-surface-container) 100%);
  background-size: 220% 100%;
  animation: shimmer 1.4s ease-in-out infinite;
}

.skeleton-line--short {
  width: 120px;
}

.skeleton-line--medium {
  width: 240px;
}

.skeleton-line--long {
  width: 360px;
}

@keyframes shimmer {
  0% {
    background-position: 120% 0;
  }
  100% {
    background-position: -120% 0;
  }
}

@media (max-width: 1100px) {
  .profile-hero,
  .profile-shell {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .profile-hero,
  .profile-panel {
    border-radius: var(--radius-lg);
    padding: 22px;
  }

  .profile-avatar {
    width: 88px;
    height: 88px;
    border-radius: 24px;
    font-size: 30px;
  }

  .profile-title-row h1 {
    font-size: 32px;
  }

  .identity-grid {
    grid-template-columns: 1fr;
  }

  .detail-row {
    grid-template-columns: 1fr;
  }

  .detail-row strong {
    text-align: left;
  }
}
</style>
