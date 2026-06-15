<template>
  <div class="chapter-tree">
    <div v-if="chapters.length === 0" class="empty-chapters">
      <BookOpen :size="32" stroke-width="1.4"/>
      <p>{{ t('chapter.noChapters') }}</p>
      <p class="empty-desc">{{ t('chapter.noChaptersDesc') }}</p>
    </div>
    <div v-else class="chapter-list">
      <ChapterTreeNode
        v-for="chapter in chapters"
        :key="chapter.id"
        :chapter="chapter"
        :active-chapter-id="activeChapterId"
        :is-editable="isEditable"
        :depth="0"
        @select="$emit('select', $event)"
        @edit="$emit('edit', $event)"
        @delete="$emit('delete', $event)"
        @add-child="$emit('add-child', $event)"
        @move-up="$emit('move-up', $event)"
        @move-down="$emit('move-down', $event)"
      />
    </div>
  </div>
</template>

<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {BookOpen} from 'lucide-vue-next'
import type {Chapter} from '@/features/course/types/chapter'
import ChapterTreeNode from './ChapterTreeNode.vue'

defineProps<{
  chapters: Chapter[]
  activeChapterId?: string | null
  isEditable?: boolean
}>()

defineEmits<{
  select: [chapter: Chapter]
  edit: [chapter: Chapter]
  delete: [chapter: Chapter]
  'add-child': [chapter: Chapter]
  'move-up': [chapter: Chapter]
  'move-down': [chapter: Chapter]
}>()

const {t} = useI18n()
</script>

<style scoped>
.chapter-tree {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.empty-chapters {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 48px 24px;
  color: var(--color-muted);
  text-align: center;
}

.empty-chapters p {
  margin: 0;
  font-family: var(--font-body);
  font-size: 16px;
}

.empty-desc {
  font-size: 14px !important;
  color: var(--color-muted);
}

.chapter-list {
  display: flex;
  flex-direction: column;
  border-top: 1px solid var(--color-outline-light);
}
</style>
