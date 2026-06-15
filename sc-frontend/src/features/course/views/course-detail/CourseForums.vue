<template>
  <section class="tab-panel">
    <div class="panel-header">
      <div>
        <span>{{ t('courseDetail.discussionTab') }}</span>
        <h2>{{ t('forum.commentsTitle') }}</h2>
      </div>
    </div>
    <CourseComments
      :course-id="courseId"
      :can-comment="canComment"
      :can-manage-course="canManageCourse"
      :current-user-id="currentUserId"
    />
  </section>
</template>

<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import CourseComments from '@/features/forum/components/CourseComments.vue'

defineProps<{
  course?: { isPublic?: number } | null
  courseId: string
  canComment: boolean
  canManageCourse?: boolean
  currentUserId?: string
}>()

const {t} = useI18n()
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

@media (max-width: 760px) {
  .panel-header {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
