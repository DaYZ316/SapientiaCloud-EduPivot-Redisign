<template>
  <div class="tree-node">
    <div
      class="node-row"
      :class="{active: chapter.id === activeChapterId, published: chapter.status === 1}"
      :style="{'--node-indent': depth * 18 + 'px'}"
      @click="$emit('select', chapter)"
    >
      <button
        v-if="chapter.children && chapter.children.length"
        class="toggle-btn"
        @click.stop="expanded = !expanded"
      >
        <ChevronRight :size="16" :class="{rotated: expanded}"/>
      </button>
      <span v-else class="toggle-spacer"></span>

      <span class="chapter-copy">
        <span class="chapter-name">{{ chapter.chapterName }}</span>
        <span class="chapter-index">{{ t(depth === 0 ? 'chapter.chapterUnit' : 'chapter.lessonUnit') }}</span>
      </span>

      <span v-if="chapter.status === 1" class="status-badge published">{{ t('chapter.published') }}</span>
      <span v-else class="status-badge draft">{{ t('chapter.draft') }}</span>

      <div v-if="isEditable" class="node-actions" @click.stop>
        <button class="action-btn" :title="t('chapter.moveUp')" @click="$emit('move-up', chapter)">
          <ChevronUp :size="14"/>
        </button>
        <button class="action-btn" :title="t('chapter.moveDown')" @click="$emit('move-down', chapter)">
          <ChevronDown :size="14"/>
        </button>
        <button class="action-btn" :title="t('chapter.addSubChapter')" @click="$emit('add-child', chapter)">
          <Plus :size="14"/>
        </button>
        <button class="action-btn" :title="t('chapter.editChapter')" @click="$emit('edit', chapter)">
          <Pencil :size="14"/>
        </button>
        <button class="action-btn danger" :title="t('chapter.deleteChapter')" @click="$emit('delete', chapter)">
          <Trash2 :size="14"/>
        </button>
      </div>
    </div>

    <div v-if="expanded && chapter.children" class="children">
      <ChapterTreeNode
        v-for="child in chapter.children"
        :key="child.id"
        :chapter="child"
        :active-chapter-id="activeChapterId"
        :is-editable="isEditable"
        :depth="depth + 1"
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
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {ChevronRight, ChevronUp, ChevronDown, Plus, Pencil, Trash2} from 'lucide-vue-next'
import type {Chapter} from '@/features/course/types/chapter'

defineProps<{
  chapter: Chapter
  activeChapterId?: string | null
  isEditable?: boolean
  depth: number
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
const expanded = ref(true)
</script>

<style scoped>
.tree-node {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.node-row {
  position: relative;
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 48px;
  padding: 8px 10px 8px calc(12px + var(--node-indent, 0px));
  border-bottom: 1px solid var(--color-outline-light);
  border-radius: 0;
  cursor: pointer;
  transition: background 0.2s ease, color 0.2s ease;
}

.node-row:hover {
  background: var(--color-surface-container);
}

.node-row.active {
  background: var(--color-surface-container);
}

.node-row.active::before {
  position: absolute;
  top: 0;
  bottom: 0;
  left: 0;
  width: 2px;
  background: var(--color-primary);
  content: '';
}

.toggle-btn {
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  border: 1px solid transparent;
  border-radius: 0;
  background: none;
  color: var(--color-muted);
  cursor: pointer;
  padding: 0;
  flex: 0 0 auto;
  transition: background 0.2s ease, border-color 0.2s ease, color 0.2s ease;
}

.toggle-btn:hover,
.toggle-btn:focus-visible {
  background: var(--color-surface-container-high);
  border-color: var(--color-outline-light);
  color: var(--color-on-surface);
  outline: none;
}

.toggle-btn svg {
  transition: transform 0.2s;
}

.toggle-btn svg.rotated {
  transform: rotate(90deg);
}

.toggle-spacer {
  width: 24px;
  height: 24px;
  flex-shrink: 0;
}

.chapter-copy {
  flex: 1;
  min-width: 0;
}

.chapter-name {
  display: block;
  font-family: var(--font-body);
  font-size: 13px;
  font-weight: 500;
  color: var(--color-on-surface);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chapter-index {
  display: block;
  margin-top: 4px;
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 10px;
  font-weight: 400;
  letter-spacing: 0.08em;
  line-height: 1;
}

.status-badge {
  font-family: var(--font-label);
  font-size: 10px;
  font-weight: 400;
  letter-spacing: 0.05em;
  padding: 3px 6px;
  border-radius: 0;
  text-transform: uppercase;
  flex-shrink: 0;
}

.status-badge.published {
  color: var(--color-on-surface);
  background: transparent;
  border: 1px solid var(--color-outline-light);
}

.status-badge.draft {
  color: var(--color-muted);
  background: var(--color-surface-container-high);
  border: 1px solid var(--color-outline-light);
}

.node-actions {
  display: flex;
  gap: 2px;
  opacity: 0;
  transition: opacity 0.2s ease;
}

.node-row:hover .node-actions,
.node-row:focus-within .node-actions {
  opacity: 1;
}

.action-btn {
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  border: 1px solid var(--color-outline-light);
  background: var(--color-surface-card);
  color: var(--color-muted);
  cursor: pointer;
  border-radius: 0;
  padding: 0;
  transition: background 0.2s ease, color 0.2s ease, transform 0.2s ease;
}

.action-btn:hover {
  background: var(--color-surface-container-high);
  color: var(--color-on-surface);
}

.action-btn.danger:hover {
  color: var(--color-error);
}

.action-btn:active {
  transform: translateY(1px);
}

.children {
  display: flex;
  flex-direction: column;
}
</style>
