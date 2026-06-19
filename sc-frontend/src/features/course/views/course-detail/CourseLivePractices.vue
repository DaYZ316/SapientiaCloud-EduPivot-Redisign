<template>
  <Suspense>
    <CourseLivePracticesContent
      :can-manage-course="canManageCourse"
      :course-id="courseId"
    />
    <template #fallback>
      <section class="tab-panel live-practice-page">
        <CourseTabLoadingSkeleton :count="2" variant="practice"/>
      </section>
    </template>
  </Suspense>
</template>

<script lang="ts" setup>
import {defineAsyncComponent} from 'vue'
import CourseTabLoadingSkeleton from '@/features/course/components/CourseTabLoadingSkeleton.vue'

defineProps<{
  courseId: string
  canManageCourse: boolean
}>()

const CourseLivePracticesContent = defineAsyncComponent(() => import('./CourseLivePracticesContent.vue'))
</script>

<style scoped>
.live-practice-page {
  display: grid;
  gap: 18px;
}
</style>
