<template>
  <ClassroomLiveMiniWindow
    v-if="compact"
    :can-participate="canParticipate"
    :is-teacher="isTeacher"
    :session="session"
    @close="$emit('close', $event)"
    @expand="$emit('expand')"
    @session-change="$emit('session-change', $event)"
  />
  <ClassroomLiveExperience
    v-else
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
import ClassroomLiveMiniWindow from '@/features/classroom/components/ClassroomLiveMiniWindow.vue'
import type {ClassSession} from '@/features/course/types/classSession'

defineProps<{
  session: ClassSession
  isTeacher: boolean
  canParticipate: boolean
  compact?: boolean
}>()

defineEmits<{
  close: [forceClose?: boolean]
  expand: []
  'session-change': [session: ClassSession]
}>()
</script>
