<template>
  <div class="tree-node" :style="{'paddingLeft': depth * 24 + 'px'}">
    <div
      class="node-row"
      :class="{active: chapter.id === activeChapterId, published: chapter.status === 1}"
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

      <span class="chapter-name">{{ chapter.chapterName }}</span>

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
}

.node-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s;
  border: 1px solid transparent;
}

.node-row:hover {
  background: var(--color-surface-container);
}

.node-row.active {
  background: var(--color-surface-container-high);
  border-color: var(--color-outline-light);
}

.toggle-btn {
  display: grid;
  place-items: center;
  width: 20px;
  height: 20px;
  border: none;
  background: none;
  color: var(--color-muted);
  cursor: pointer;
  padding: 0;
}

.toggle-btn svg {
  transition: transform 0.2s;
}

.toggle-btn svg.rotated {
  transform: rotate(90deg);
}

.toggle-spacer {
  width: 20px;
  height: 20px;
  flex-shrink: 0;
}

.chapter-name {
  flex: 1;
  font-family: var(--font-body);
  font-size: 14px;
  font-weight: 500;
  color: var(--color-on-surface);
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.status-badge {
  font-family: var(--font-body);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.05em;
  padding: 2px 8px;
  border-radius: 4px;
  text-transform: uppercase;
  flex-shrink: 0;
}

.status-badge.published {
  color: #22c55e;
  background: rgba(34, 197, 94, 0.12);
}

.status-badge.draft {
  color: var(--color-muted);
  background: var(--color-surface-container-high);
}

.node-actions {
  display: flex;
  gap: 2px;
  opacity: 0;
  transition: opacity 0.15s;
}

.node-row:hover .node-actions {
  opacity: 1;
}

.action-btn {
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  border: none;
  background: none;
  color: var(--color-muted);
  cursor: pointer;
  border-radius: 4px;
  padding: 0;
  transition: background 0.15s, color 0.15s;
}

.action-btn:hover {
  background: var(--color-surface-container-high);
  color: var(--color-on-surface);
}

.action-btn.danger:hover {
  color: var(--color-error);
}

.children {
  display: flex;
  flex-direction: column;
}
</style>