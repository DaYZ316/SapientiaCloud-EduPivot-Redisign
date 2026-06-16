<template>
  <div class="classroom-page">
    <button class="back-link" type="button" @click="backToCourse">
      <ArrowLeft :size="16" stroke-width="1.8"/>
      {{ t('courseDetail.classSession.backToCourse') }}
    </button>

    <section v-if="loading" class="classroom-state">
      <LoaderCircle :size="28" stroke-width="1.7"/>
      <p>{{ t('courseDetail.classSession.roomLoading') }}</p>
    </section>

    <section v-else-if="!session" class="classroom-state">
      <CircleAlert :size="32" stroke-width="1.5"/>
      <h1>{{ t('courseDetail.classSession.roomNotFound') }}</h1>
      <button class="btn-secondary" type="button" @click="router.push('/courses')">
        {{ t('courseDetail.browseCourses') }}
      </button>
    </section>

    <template v-else>
      <section class="classroom-hero">
        <div>
          <span class="hero-kicker">{{ courseTitle || t('courseDetail.classSession.roomKicker') }}</span>
          <h1>{{ session.title }}</h1>
          <p>{{ session.description || t('courseDetail.classSession.noDescription') }}</p>
        </div>
        <span class="status-badge">{{ session.statusText || fallbackStatusText(session.status) }}</span>
      </section>

      <section class="classroom-grid">
        <div class="classroom-info">
          <div>
            <span>{{ t('courseDetail.classSession.timeRange') }}</span>
            <strong>{{ formatDateTime(session.scheduledStartAt) }} - {{ formatDateTime(session.scheduledEndAt) }}</strong>
          </div>
          <div>
            <span>{{ t('courseDetail.classSession.roomSizeLabel') }}</span>
            <strong>{{ roomSizeLabel(session.roomSize) }}</strong>
          </div>
          <div>
            <span>{{ t('courseDetail.classSession.joinStatus') }}</span>
            <strong>{{ joined ? t('courseDetail.classSession.joined') : t('courseDetail.classSession.notJoined') }}</strong>
          </div>
        </div>

        <div class="classroom-stage">
          <div class="stage-copy">
            <Presentation :size="36" stroke-width="1.4"/>
            <h2>{{ joined ? t('courseDetail.classSession.placeholderTitle') : t('courseDetail.classSession.beforeJoinTitle') }}</h2>
            <p>{{ joined ? t('courseDetail.classSession.placeholderDesc') : t('courseDetail.classSession.beforeJoinDesc') }}</p>
          </div>
          <button
            v-if="!joined"
            class="btn-primary"
            type="button"
            :disabled="joining || !session.publishedAt"
            @click="handleJoin"
          >
            {{ joining ? t('courseDetail.classSession.joining') : t('courseDetail.classSession.enterAction') }}
          </button>
        </div>
      </section>
    </template>
  </div>
</template>

<script lang="ts" setup>
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import {ArrowLeft, CircleAlert, LoaderCircle, Presentation} from 'lucide-vue-next'

import {getClassSession, joinClassSession} from '@/features/course/api/classSession'
import {getCourse} from '@/features/course/api/course'
import {notify} from '@/shared/composables/useGlobalNotification'
import {ClassRoomSize, ClassSessionStatus, type ClassSession} from '@/features/course/types/classSession'

const {t, locale} = useI18n()
const route = useRoute()
const router = useRouter()

const sessionId = computed(() => route.params.sessionId as string)
const loading = ref(true)
const joining = ref(false)
const session = ref<ClassSession | null>(null)
const courseTitle = ref('')
const joined = ref(false)

onMounted(loadSession)

async function loadSession() {
  loading.value = true
  try {
    const sessionData = await getClassSession(sessionId.value)
    session.value = sessionData
    joined.value = sessionData.joined
    await loadCourseTitle(sessionData.courseId)
  } catch {
    session.value = null
  } finally {
    loading.value = false
  }
}

async function loadCourseTitle(courseId: string) {
  try {
    const course = await getCourse(courseId)
    courseTitle.value = course.title
  } catch {
    courseTitle.value = ''
  }
}

async function handleJoin() {
  if (!session.value || joining.value || !session.value.publishedAt) return
  joining.value = true
  try {
    await joinClassSession(session.value.id, {x: 0, y: 0, z: 0})
    joined.value = true
    session.value.joined = true
    notify.success(t('courseDetail.classSession.joinSuccess'))
  } catch {
    notify.error(t('courseDetail.classSession.joinFailed'))
  } finally {
    joining.value = false
  }
}

function backToCourse() {
  if (session.value?.courseId) {
    router.push({name: 'course-class-sessions', params: {id: session.value.courseId}})
    return
  }
  router.push('/courses')
}

function fallbackStatusText(status: number) {
  const labels: Record<number, string> = {
    [ClassSessionStatus.PREPARING]: t('courseDetail.classSession.statusPreparing'),
    [ClassSessionStatus.UPCOMING]: t('courseDetail.classSession.statusUpcoming'),
    [ClassSessionStatus.LIVE]: t('courseDetail.classSession.statusLive'),
    [ClassSessionStatus.FINISHED]: t('courseDetail.classSession.statusFinished'),
  }
  return labels[status] || t('courseDetail.statusUnknown')
}

function roomSizeLabel(roomSize: number) {
  const labels: Record<number, string> = {
    [ClassRoomSize.SMALL]: t('courseDetail.classSession.roomSmall'),
    [ClassRoomSize.MEDIUM]: t('courseDetail.classSession.roomMedium'),
    [ClassRoomSize.LARGE]: t('courseDetail.classSession.roomLarge'),
    [ClassRoomSize.XLARGE]: t('courseDetail.classSession.roomXLarge'),
  }
  return labels[roomSize] || t('courseDetail.unknown')
}

function formatDateTime(value?: string | null) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '-'
  return new Intl.DateTimeFormat(String(locale.value), {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
}
</script>

<style scoped>
.classroom-page {
  width: 100%;
  max-width: 1120px;
  margin: 0 auto;
  padding: 18px 0 48px;
  color: var(--color-on-surface);
}

.back-link {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 32px;
  padding: 0;
  margin-bottom: 24px;
  background: none;
  border: 0;
  color: var(--color-muted);
  cursor: pointer;
  font-family: var(--font-label);
  font-size: 13px;
}

.back-link:hover {
  color: var(--color-on-surface);
}

.classroom-state,
.classroom-hero,
.classroom-info,
.classroom-stage {
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
}

.classroom-state {
  display: grid;
  min-height: 340px;
  place-items: center;
  align-content: center;
  gap: 12px;
  padding: 40px;
  color: var(--color-muted);
  text-align: center;
}

.classroom-state h1 {
  margin: 0;
  color: var(--color-on-surface);
  font-family: var(--font-heading);
  font-size: 32px;
  font-weight: 400;
}

.classroom-state p {
  margin: 0;
}

.classroom-hero {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  padding: 28px;
}

.hero-kicker {
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.classroom-hero h1 {
  margin: 8px 0 10px;
  color: var(--color-on-surface);
  font-family: var(--font-heading);
  font-size: clamp(32px, 5vw, 56px);
  font-weight: 400;
  line-height: 1.05;
}

.classroom-hero p {
  max-width: 68ch;
  margin: 0;
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 15px;
  line-height: 1.6;
}

.status-badge {
  align-self: flex-start;
  flex-shrink: 0;
  padding: 6px 10px;
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  color: var(--color-on-surface);
  font-family: var(--font-label);
  font-size: 12px;
  font-weight: 800;
}

.classroom-grid {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  gap: 18px;
  margin-top: 18px;
}

.classroom-info {
  display: grid;
  gap: 14px;
  align-content: start;
  padding: 20px;
}

.classroom-info div {
  display: grid;
  gap: 6px;
}

.classroom-info span {
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 12px;
}

.classroom-info strong {
  color: var(--color-on-surface);
  font-family: var(--font-body);
  font-size: 15px;
}

.classroom-stage {
  display: grid;
  min-height: 360px;
  place-items: center;
  align-content: center;
  gap: 22px;
  padding: 32px;
  text-align: center;
}

.stage-copy {
  display: grid;
  justify-items: center;
  gap: 10px;
  max-width: 520px;
}

.stage-copy h2 {
  margin: 0;
  color: var(--color-on-surface);
  font-family: var(--font-heading);
  font-size: 32px;
  font-weight: 400;
}

.stage-copy p {
  margin: 0;
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 14px;
  line-height: 1.6;
}

@media (max-width: 820px) {
  .classroom-hero,
  .classroom-grid {
    grid-template-columns: 1fr;
  }

  .classroom-hero {
    flex-direction: column;
  }
}
</style>
