<template>
  <section class="tab-panel live-practice-page">
    <header class="panel-header">
      <div>
        <span>{{
            canManageCourse ? t('courseDetail.livePractice.kicker') : t('courseDetail.livePractice.workbookKicker')
          }}</span>
        <h2>{{
            canManageCourse ? t('courseDetail.livePractice.title') : t('courseDetail.livePractice.workbookTitle')
          }}</h2>
        <p>
          {{
            canManageCourse ? t('courseDetail.livePractice.teacherDescription') : t('courseDetail.livePractice.studentDescription')
          }}
        </p>
      </div>
      <button
          class="btn-secondary refresh-button"
          type="button"
          @click="loadData"
      >
        {{ t('courseDetail.livePractice.refresh') }}
      </button>
    </header>

    <div
        v-if="loading"
    >
      <CourseTabLoadingSkeleton :count="2" variant="practice"/>
    </div>
    <div
        v-else-if="loadFailed"
        class="state-block"
    >
      <p>{{ t('courseDetail.livePractice.loadFailed') }}</p>
      <button
          class="btn-secondary"
          type="button"
          @click="loadData"
      >
        {{ t('courseDetail.livePractice.retry') }}
      </button>
    </div>

    <div
        v-else-if="canManageCourse"
        class="practice-sessions"
    >
      <section
          v-for="sessionGroup in teacherSessionGroups"
          :key="sessionGroup.classSessionId"
          class="practice-session"
      >
        <header class="session-header">
          <div>
            <span>{{ t('courseDetail.livePractice.classSession') }}</span>
            <h3>{{ sessionTitle(sessionGroup.classSessionId) }}</h3>
            <p>{{ sessionTimeRange(sessionGroup.classSessionId) }}</p>
          </div>
          <strong>{{ t('courseDetail.livePractice.groupCount', {count: sessionGroup.groups.length}) }}</strong>
        </header>

        <div class="teacher-groups">
          <article
              v-for="group in sessionGroup.groups"
              :key="group.id"
              class="group-card"
          >
            <div class="group-header">
              <button
                  :aria-expanded="expandedGroupIds.has(group.id)"
                  :aria-label="groupToggleLabel(group.id)"
                  class="group-toggle"
                  type="button"
                  @click="toggleGroup(group.id)"
              >
                <ChevronRight
                    :class="{expanded: expandedGroupIds.has(group.id)}"
                    :size="18"
                    class="toggle-icon"
                    stroke-width="2"
                />
                <span class="group-main">
                  <span>{{
                      t('courseDetail.livePractice.groupOrder', {order: group.publishOrder})
                    }} · {{ formatDateTime(group.publishedAt) }}</span>
                  <strong>{{ group.title }}</strong>
                </span>
              </button>
              <div class="group-summary">
                <span>{{ t('courseDetail.livePractice.questionCount', {count: group.totalQuestions}) }}</span>
                <strong>{{ group.submittedStudents }}/{{ group.totalStudents }}</strong>
                <button
                    class="btn-secondary compact"
                    type="button"
                    @click="openGroupDetail(group.id)"
                >
                  {{ t('courseDetail.livePractice.viewDetail') }}
                </button>
              </div>
            </div>
            <div
                v-if="expandedGroupIds.has(group.id)"
                class="question-list"
            >
              <article
                  v-for="question in group.questions || []"
                  :key="question.id"
                  class="question-card"
              >
                <h5>{{ question.questionOrder }}. {{ question.questionTitle }}</h5>
                <p v-if="question.questionContent">
                  {{ question.questionContent }}
                </p>
                <div
                    v-if="question.analysis"
                    class="metric-row"
                >
                  <span>{{
                      t('courseDetail.livePractice.submittedCount', {count: question.analysis.submittedCount})
                    }}</span>
                  <span>{{
                      t('courseDetail.livePractice.lateSubmittedCount', {count: question.analysis.lateSubmittedCount})
                    }}</span>
                  <span>{{
                      t('courseDetail.livePractice.notSubmittedCount', {count: question.analysis.notSubmittedCount})
                    }}</span>
                  <span>{{
                      t('courseDetail.livePractice.correctCount', {count: question.analysis.correctCount})
                    }}</span>
                  <span>{{
                      t('courseDetail.livePractice.averageScore', {score: question.analysis.averageScore})
                    }}</span>
                </div>
                <div
                    v-if="question.analysis && Object.keys(question.analysis.optionCounts).length"
                    class="option-counts"
                >
                  <span
                      v-for="(count, label) in question.analysis.optionCounts"
                      :key="label"
                  >{{ label }} {{ count }}</span>
                </div>
              </article>
              <div
                  v-if="!group.questions?.length"
                  class="empty-list"
              >
                {{ t('courseDetail.livePractice.emptyQuestions') }}
              </div>
            </div>
          </article>
        </div>
      </section>
      <div
          v-if="teacherGroups.length === 0"
          class="empty-practice"
      >
        {{ t('courseDetail.livePractice.emptyTeacher') }}
      </div>
    </div>

    <div
        v-else
        class="workbook-list"
    >
      <section
          v-for="sessionGroup in workbookSessionGroups"
          :key="sessionGroup.classSessionId"
          class="practice-session"
      >
        <header class="session-header">
          <div>
            <span>{{ t('courseDetail.livePractice.classSession') }}</span>
            <h3>{{ sessionTitle(sessionGroup.classSessionId) }}</h3>
            <p>{{ sessionTimeRange(sessionGroup.classSessionId) }}</p>
          </div>
          <strong>{{ t('courseDetail.livePractice.groupCount', {count: sessionGroup.groups.length}) }}</strong>
        </header>

        <div class="workbook-items">
          <article
              v-for="group in sessionGroup.groups"
              :key="group.groupId"
              class="workbook-card"
          >
            <div class="workbook-meta">
              <button
                  :aria-expanded="expandedGroupIds.has(group.groupId)"
                  :aria-label="groupToggleLabel(group.groupId)"
                  class="group-toggle"
                  type="button"
                  @click="toggleGroup(group.groupId)"
              >
                <ChevronRight
                    :class="{expanded: expandedGroupIds.has(group.groupId)}"
                    :size="18"
                    class="toggle-icon"
                    stroke-width="2"
                />
                <span class="group-main">
                  <span>{{ t('courseDetail.livePractice.groupOrder', {order: group.publishOrder}) }}</span>
                  <strong>{{ group.groupTitle }}</strong>
                </span>
              </button>
              <div class="group-summary">
                <span>{{ t('courseDetail.livePractice.questionCount', {count: group.items.length}) }}</span>
                <span>{{
                    t('courseDetail.livePractice.deadlineAt', {time: formatDateTime(group.availableEndAt)})
                  }}</span>
                <button
                    class="btn-secondary compact"
                    type="button"
                    @click="openGroupDetail(group.groupId)"
                >
                  {{ t('courseDetail.livePractice.viewDetail') }}
                </button>
              </div>
            </div>

            <div
                v-if="expandedGroupIds.has(group.groupId)"
                class="workbook-question-list"
            >
              <article
                  v-for="item in group.items"
                  :key="item.question.id"
                  class="question-card"
              >
                <div class="workbook-meta">
                  <span>{{ item.question.questionOrder }}. {{ item.question.questionTitle }}</span>
                  <strong :class="statusClass(item.submitStatus)">{{ submitStatusLabel(item.submitStatus) }}</strong>
                </div>
                <p v-if="item.question.questionContent">
                  {{ item.question.questionContent }}
                </p>
                <div class="metric-row">
                  <span>{{ t('courseDetail.livePractice.scorePoints', {score: item.question.score}) }}</span>
                  <span v-if="item.submission">{{
                      t('courseDetail.livePractice.earnedScore', {score: item.submission.earnedScore})
                    }}</span>
                </div>
              </article>
              <div
                  v-if="!group.items.length"
                  class="empty-list"
              >
                {{ t('courseDetail.livePractice.emptyQuestions') }}
              </div>
            </div>
          </article>
        </div>
      </section>
      <div
          v-if="workbookItems.length === 0"
          class="empty-practice"
      >
        {{ t('courseDetail.livePractice.emptyStudent') }}
      </div>
    </div>
  </section>
</template>

<script lang="ts" setup>
import {ChevronRight} from 'lucide-vue-next'
import {computed, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'

import CourseTabLoadingSkeleton from '@/features/course/components/CourseTabLoadingSkeleton.vue'
import {getCourseClassSessions} from '@/features/course/api/classSession'
import type {ClassSession} from '@/features/course/types/classSession'
import {getLivePracticeWorkbook, getTeacherLivePractices} from '@/features/live-practice/api/livePractice'
import type {LivePracticeGroup, LivePracticeWorkbookItem} from '@/features/live-practice/types/livePractice'

const props = defineProps<{
  courseId: string
  canManageCourse: boolean
}>()

const router = useRouter()
const {t, locale} = useI18n()
const loading = ref(false)
const loadFailed = ref(false)
const teacherGroups = ref<LivePracticeGroup[]>([])
const workbookItems = ref<LivePracticeWorkbookItem[]>([])
const classSessions = ref<ClassSession[]>([])
const expandedGroupIds = ref<Set<string>>(new Set())

const classSessionMap = computed(() => new Map(classSessions.value.map(session => [session.id, session])))
const teacherSessionGroups = computed(() => groupTeacherPractices(teacherGroups.value))
const workbookSessionGroups = computed(() => groupWorkbookItems(workbookItems.value))

onMounted(() => {
  void loadData()
})

watch(() => [props.courseId, props.canManageCourse], () => {
  void loadData()
})

async function loadData() {
  loading.value = true
  loadFailed.value = false
  try {
    if (props.canManageCourse) {
      const [groups, sessions] = await Promise.all([
        getTeacherLivePractices(props.courseId),
        loadClassSessions(props.courseId),
      ])
      teacherGroups.value = groups
      classSessions.value = sessions
      workbookItems.value = []
    } else {
      const [items, sessions] = await Promise.all([
        getLivePracticeWorkbook(props.courseId),
        loadClassSessions(props.courseId),
      ])
      workbookItems.value = items
      classSessions.value = sessions
      teacherGroups.value = []
    }
  } catch {
    loadFailed.value = true
  } finally {
    loading.value = false
  }
}

async function loadClassSessions(courseId: string) {
  const sessions: ClassSession[] = []
  const size = 100
  let page = 1
  try {
    while (true) {
      const response = await getCourseClassSessions(courseId, page, size)
      const records = response.records || []
      sessions.push(...records)
      if (sessions.length >= response.total || records.length === 0) break
      page += 1
    }
  } catch {
    return []
  }
  return sessions
}

function groupTeacherPractices(groups: LivePracticeGroup[]) {
  const buckets = new Map<string, LivePracticeGroup[]>()
  groups.forEach(group => {
    buckets.set(group.classSessionId, [...(buckets.get(group.classSessionId) || []), group])
  })
  return Array.from(buckets, ([classSessionId, values]) => ({
    classSessionId,
    groups: values,
  }))
}

function groupWorkbookItems(items: LivePracticeWorkbookItem[]) {
  const buckets = new Map<string, Map<string, WorkbookGroup>>()
  items.forEach(item => {
    let groups = buckets.get(item.classSessionId)
    if (!groups) {
      groups = new Map()
      buckets.set(item.classSessionId, groups)
    }
    const existing = groups.get(item.groupId)
    if (existing) {
      existing.items.push(item)
      return
    }
    groups.set(item.groupId, {
      groupId: item.groupId,
      groupTitle: item.groupTitle,
      publishOrder: item.publishOrder,
      availableEndAt: item.availableEndAt,
      items: [item],
    })
  })
  return Array.from(buckets, ([classSessionId, groups]) => ({
    classSessionId,
    groups: Array.from(groups.values()),
  }))
}

interface WorkbookGroup {
  groupId: string
  groupTitle: string
  publishOrder: number
  availableEndAt: string
  items: LivePracticeWorkbookItem[]
}

function sessionTitle(classSessionId: string) {
  return classSessionMap.value.get(classSessionId)?.title || t('courseDetail.livePractice.classSession')
}

function sessionTimeRange(classSessionId: string) {
  const session = classSessionMap.value.get(classSessionId)
  if (!session) return t('courseDetail.livePractice.sessionFallback')
  return `${formatDateTime(session.scheduledStartAt)} - ${formatDateTime(session.scheduledEndAt)}`
}

function formatDateTime(value?: string | null) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '-'
  return new Intl.DateTimeFormat(String(locale.value), {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
}

function statusClass(status: number) {
  if (status === 1) return 'submitted'
  if (status === 2) return 'late'
  return 'missing'
}

function submitStatusLabel(status?: number | null) {
  if (status === 1) return t('courseDetail.livePractice.statusSubmitted')
  if (status === 2) return t('courseDetail.livePractice.statusLateSubmitted')
  return t('courseDetail.livePractice.statusNotSubmitted')
}

function toggleGroup(groupId: string) {
  const next = new Set(expandedGroupIds.value)
  if (next.has(groupId)) {
    next.delete(groupId)
  } else {
    next.add(groupId)
  }
  expandedGroupIds.value = next
}

function groupToggleLabel(groupId: string) {
  return expandedGroupIds.value.has(groupId)
      ? t('courseDetail.livePractice.collapseGroup')
      : t('courseDetail.livePractice.expandGroup')
}

function openGroupDetail(groupId: string) {
  void router.push({
    name: 'course-live-practice-detail',
    params: {
      courseId: props.courseId,
      groupId,
    },
  })
}
</script>

<style scoped>
.live-practice-page {
  display: grid;
  gap: 18px;
}

.panel-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 18px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--color-outline-light);
}

.panel-header span {
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 12px;
  font-weight: 400;
  line-height: 1;
  letter-spacing: 0.05em;
  text-transform: uppercase;
}

.panel-header h2 {
  margin: 6px 0 0;
  color: var(--color-on-surface);
  font-family: var(--font-heading);
  font-size: 28px;
  font-weight: 400;
  line-height: 1.3;
}

.panel-header p {
  max-width: 62ch;
  margin: 8px 0 0;
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 14px;
  line-height: 1.55;
}

.session-header span,
.group-header span,
.workbook-meta span {
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 12px;
  letter-spacing: 0.05em;
}

.session-header p,
.question-card p,
.workbook-card p {
  margin: 0;
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 14px;
  line-height: 1.55;
}

.practice-sessions,
.teacher-groups,
.workbook-list,
.workbook-items,
.question-list,
.workbook-question-list {
  display: grid;
  gap: 14px;
}

.practice-session {
  display: grid;
  gap: 12px;
  padding: 16px;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
}

.session-header {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--color-outline-light);
}

.group-card,
.question-card,
.workbook-card,
.state-block {
  display: grid;
  gap: 12px;
  padding: 16px;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
}

.empty-practice {
  display: grid;
  min-height: 220px;
  place-items: center;
  align-content: center;
  gap: 10px;
  padding: 42px 24px;
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 14px;
  line-height: 1.55;
  text-align: center;
}

.group-header,
.workbook-meta,
.metric-row,
.option-counts {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.group-header {
  justify-content: space-between;
}

.group-toggle {
  display: flex;
  flex: 1 1 280px;
  align-items: center;
  gap: 10px;
  min-width: 0;
  padding: 0;
  background: transparent;
  border: 0;
  color: inherit;
  cursor: pointer;
  text-align: left;
}

.group-toggle:focus-visible {
  outline: 2px solid var(--color-outline);
  outline-offset: 4px;
}

.toggle-icon {
  flex: 0 0 auto;
  color: var(--color-muted);
  transition: transform 0.18s ease;
}

.toggle-icon.expanded {
  transform: rotate(90deg);
}

.group-main {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.group-main strong {
  color: var(--color-on-surface);
  font-family: var(--font-body);
  font-size: 15px;
  font-weight: 800;
}

.group-summary {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
}

.session-header h3,
.workbook-card h3,
.question-card h5 {
  margin: 0;
  color: var(--color-on-surface);
  font-family: var(--font-body);
  font-size: 15px;
  font-weight: 800;
}

.session-header h3 {
  margin-bottom: 6px;
}

.question-card h5 {
  font-size: 15px;
}

.session-header strong,
.group-summary strong {
  color: var(--color-on-surface);
  font-family: var(--font-body);
  font-size: 14px;
  font-weight: 800;
}

.metric-row,
.option-counts {
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 13px;
}

.metric-row span,
.option-counts span,
.workbook-meta > strong,
.group-summary span {
  padding: 4px 8px;
  background: var(--color-surface-container);
  border: 1px solid var(--color-outline-light);
}

.workbook-meta {
  justify-content: space-between;
}

.workbook-meta strong.submitted {
  color: #16a34a;
}

.workbook-meta strong.late {
  color: #ca8a04;
}

.workbook-meta strong.missing {
  color: var(--color-muted);
}

.btn-secondary.compact {
  min-height: 30px;
  padding: 7px 12px;
}

.refresh-button {
  min-height: 38px;
  padding: 0 14px;
  border-color: var(--color-outline-light);
  border-radius: var(--radius-sm);
  color: var(--color-on-surface);
  font-size: 12px;
  font-weight: 800;
}

.refresh-button:hover {
  background: var(--color-surface-container-high);
  border-color: var(--color-outline);
  color: var(--color-on-surface);
}

.empty-list {
  padding: 12px;
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 13px;
  background: var(--color-surface-container);
  border: 1px solid var(--color-outline-light);
}

@media (max-width: 720px) {
  .panel-header,
  .session-header,
  .group-header,
  .workbook-meta,
  .group-summary {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
