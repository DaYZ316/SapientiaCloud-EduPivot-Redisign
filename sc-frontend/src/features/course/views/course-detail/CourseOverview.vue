<template>
  <section class="tab-panel">
    <section class="instructor-biography">
      <span class="section-kicker">{{ t('courseDetail.overviewTab') }}</span>
      <h2>{{ t('courseDetail.instructorBiography') }}</h2>
      <p class="section-description">{{ t('courseDetail.overviewDescription') }}</p>
      <div class="instructor-row">
        <UserAvatarLink
            :avatar-url="course.teacherAvatar"
            :display-name="course.teacherName || t('courseDetail.unknownTeacher')"
            :role="2"
            :show-name="false"
            :user-id="course.teacherId"
            size="xl"
        />
        <div class="biography-copy">
          <h3>{{ course.teacherName || t('courseDetail.unknownTeacher') }}</h3>
          <p class="biography-role">{{ t('courseDetail.primaryInstructor') }}</p>
          <p>{{ actionDescription }}</p>
        </div>
      </div>
    </section>
  </section>
</template>

<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import type {CourseDetail} from '@/features/course/types/course'
import UserAvatarLink from '@/shared/components/UserAvatarLink.vue'

const props = defineProps<{
  course: CourseDetail
  canManageCourse: boolean
}>()

const {t} = useI18n()

const actionDescription = computed(() => {
  if (props.canManageCourse) return t('courseDetail.teacherActionDescription')
  if (props.course.enrolled) return t('courseDetail.studentActionDescription')
  return t('courseDetail.guestActionDescription')
})
</script>

<style scoped>
.instructor-biography {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.instructor-biography h2 {
  margin: 6px 0 0;
  color: var(--color-on-surface);
  font-family: var(--font-heading);
  font-size: 22px;
  font-weight: 400;
  line-height: 1.3;
}

.section-kicker {
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 12px;
  font-weight: 400;
  line-height: 1;
  letter-spacing: 0.05em;
  text-transform: uppercase;
}

.section-description {
  max-width: 62ch;
  margin: 0;
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 14px;
  line-height: 1.55;
}

.instructor-row {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}

.biography-avatar {
  width: 64px;
  height: 64px;
  flex: 0 0 auto;
  overflow: hidden;
  background: var(--color-surface-container-high);
  border: 1px solid var(--color-outline-light);
  border-radius: 50%;
}

.biography-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.biography-copy {
  min-width: 0;
}

.biography-copy h3 {
  margin: 0;
  color: var(--color-on-surface);
  font-family: var(--font-label);
  font-size: 18px;
  font-weight: 400;
  line-height: 1.2;
}

.biography-role {
  margin: 4px 0 8px;
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 14px;
  line-height: 1.4;
}

.biography-copy p:last-child {
  max-width: 62ch;
  margin: 0;
  color: var(--color-on-surface-variant);
  font-family: var(--font-body);
  font-size: 16px;
  line-height: 1.5;
}

@media (max-width: 760px) {
  .instructor-row {
    flex-direction: column;
  }
}
</style>
