<template>
  <section class="tab-panel">
    <div class="panel-header">
      <div>
        <span>{{ t('courseDetail.discussionTab') }}</span>
        <h2>{{ t('forum.commentsTitle') }}</h2>
        <p>{{ t('courseDetail.discussionDescription') }}</p>
      </div>
    </div>
    <Suspense>
      <CourseComments
          :can-comment="canComment"
          :can-manage-course="canManageCourse"
          :course-id="courseId"
          :current-user-id="currentUserId"
      />
      <template #fallback>
        <div class="comments-loading">
          {{ t('forum.loadingComments') }}
        </div>
      </template>
    </Suspense>
  </section>
</template>

<script lang="ts" setup>
import {defineAsyncComponent} from 'vue'
import {useI18n} from 'vue-i18n'

defineProps<{
  course?: { isPublic?: number } | null
  courseId: string
  canComment: boolean
  canManageCourse?: boolean
  currentUserId?: string
}>()

const {t} = useI18n()
const CourseComments = defineAsyncComponent(() => import('@/features/forum/components/CourseComments.vue'))
</script>

<style scoped>
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

.comments-loading {
  padding: 42px 24px;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 14px;
  text-align: center;
}

@media (max-width: 760px) {
  .panel-header {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
