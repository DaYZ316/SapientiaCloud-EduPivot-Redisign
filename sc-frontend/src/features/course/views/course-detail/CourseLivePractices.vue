<template>
  <Suspense>
    <CourseLivePracticesContent
      :can-manage-course="canManageCourse"
      :course-id="courseId"
    />
    <template #fallback>
      <section class="tab-panel live-practice-page">
        <div class="state-block">
          {{ t('courseDetail.livePractice.loading') }}
        </div>
      </section>
    </template>
  </Suspense>
</template>

<script lang="ts" setup>
import {defineAsyncComponent} from 'vue'
import {useI18n} from 'vue-i18n'

defineProps<{
  courseId: string
  canManageCourse: boolean
}>()

const {t} = useI18n()
const CourseLivePracticesContent = defineAsyncComponent(() => import('./CourseLivePracticesContent.vue'))
</script>

<style scoped>
.live-practice-page {
  display: grid;
  gap: 18px;
}

.state-block {
  display: grid;
  gap: 12px;
  padding: 42px 24px;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  color: var(--color-muted);
  text-align: center;
}
</style>
