<template>
  <ClassroomLiveExperience
    :can-participate="canParticipate"
    :is-teacher="isTeacher"
    mode="popup"
    :session="session"
    @close="$emit('close')"
    @session-change="$emit('session-change', $event)"
  />
</template>

<script setup lang="ts">
import ClassroomLiveExperience from '@/features/classroom/components/ClassroomLiveExperience.vue'
import type {ClassSession} from '@/features/course/types/classSession'

defineProps<{
  session: ClassSession
  isTeacher: boolean
  canParticipate: boolean
}>()

defineEmits<{
  close: []
  'session-change': [session: ClassSession]
}>()
</script>
