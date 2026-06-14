<template>
  <section class="tab-panel">
    <div class="panel-header">
      <div>
        <span>{{ t('courseDetail.chaptersTab') }}</span>
        <h2>{{ t('chapter.title') }}</h2>
      </div>
      <button v-if="canManageCourse" class="btn-add" type="button" @click="$emit('openChapterEditor')">
        <Plus :size="14" stroke-width="2"/>
        {{ t('chapter.addChapter') }}
      </button>
    </div>
    <div v-if="flatChapters.length > 0" class="chapter-summary">
      <div>
        <span>{{ t('courseDetail.totalChapters') }}</span>
        <strong>{{ flatChapters.length }}</strong>
      </div>
      <div>
        <span>{{ t('courseDetail.publishedChapters') }}</span>
        <strong>{{ publishedChapterCount }}</strong>
      </div>
      <button v-if="firstChapter && canAccessCourseContent" class="text-action" type="button" @click="$emit('selectChapter', firstChapter)">
        {{ t('courseDetail.startFirstChapter') }}
        <ArrowRight :size="14" stroke-width="1.8"/>
      </button>
    </div>
    <ChapterTree
      :chapters="chapterTree"
      :is-editable="canManageCourse"
      @select="(ch) => $emit('selectChapter', ch)"
      @edit="(ch) => $emit('editChapter', ch)"
      @delete="(ch) => $emit('deleteChapter', ch)"
      @add-child="(ch) => $emit('addChildChapter', ch)"
    />
  </section>
</template>

<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import {ArrowRight, Plus} from 'lucide-vue-next'
import ChapterTree from '@/features/course/components/ChapterTree.vue'
import type {Chapter} from '@/features/course/types/chapter'

const props = defineProps<{
  chapterTree: Chapter[]
  canManageCourse: boolean
  canAccessCourseContent: boolean
}>()

defineEmits<{
  openChapterEditor: []
  selectChapter: [chapter: Chapter]
  editChapter: [chapter: Chapter]
  deleteChapter: [chapter: Chapter]
  addChildChapter: [chapter: Chapter]
}>()

const {t} = useI18n()

function flattenChapters(chapters: Chapter[]): Chapter[] {
  return chapters.flatMap(chapter => [chapter, ...flattenChapters(chapter.children || [])])
}

const flatChapters = computed(() => flattenChapters(props.chapterTree))
const firstChapter = computed(() => flatChapters.value.find(chapter => chapter.status === 1) || flatChapters.value[0] || null)
const publishedChapterCount = computed(() => flatChapters.value.filter(chapter => chapter.status === 1).length)
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
  font-weight: 600;
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

.chapter-summary {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 160px)) minmax(180px, auto);
  gap: 12px;
  align-items: center;
  padding: 16px;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
}

.chapter-summary span {
  display: block;
  margin-bottom: 4px;
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 11px;
  font-weight: 800;
  text-transform: uppercase;
}

.chapter-summary strong {
  color: var(--color-on-surface);
  font-family: var(--font-body);
  font-size: 22px;
  font-variant-numeric: tabular-nums;
  font-weight: 800;
}

.btn-add,
.text-action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  min-height: 38px;
  padding: 0 14px;
  background: transparent;
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  color: var(--color-on-surface);
  cursor: pointer;
  font-family: var(--font-label);
  font-size: 12px;
  font-weight: 800;
  text-decoration: none;
  transition: background 0.2s ease, border-color 0.2s ease, color 0.2s ease, transform 0.2s ease;
}

.btn-add:hover,
.text-action:hover {
  background: var(--color-surface-container-high);
  border-color: var(--color-outline);
}

.text-action {
  justify-self: end;
}

@media (max-width: 760px) {
  .chapter-summary {
    grid-template-columns: 1fr;
  }

  .panel-header {
    flex-direction: column;
    align-items: stretch;
  }

  .text-action {
    justify-self: stretch;
  }
}
</style>
