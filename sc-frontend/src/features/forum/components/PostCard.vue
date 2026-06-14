<template>
  <div class="post-card" :class="{pinned: post.isTop}" @click="('click')">
    <div v-if="post.isTop" class="pinned-bar"></div>

    <div class="post-body">
      <div class="post-header">
        <span v-if="post.isTop" class="badge pinned"><Pin :size="12"/> {{ t('forum.pinned') }}</span>
        <span v-if="post.isEssence" class="badge essence"><Star :size="12"/> {{ t('forum.essence') }}</span>
        <span v-if="post.isLocked" class="badge locked"><Lock :size="12"/> {{ t('forum.locked') }}</span>
      </div>

      <h3 class="post-title">{{ post.title }}</h3>

      <p class="post-excerpt">{{ excerpt }}</p>

      <div class="post-footer">
        <div class="post-author">
          <div class="author-avatar">
            <User :size="14"/>
          </div>
          <span></span>
        </div>

        <div class="post-stats">
          <span><Eye :size="14"/> {{ post.viewCount }}</span>
          <span><Heart :size="14"/> {{ post.likeCount }}</span>
          <span><MessageCircle :size="14"/> {{ post.replyCount }}</span>
        </div>

        <span class="post-time">{{ formatTime(post.createdAt) }}</span>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import {Pin, Star, Lock, User, Eye, Heart, MessageCircle} from 'lucide-vue-next'
import type {ForumPost} from '@/features/forum/types/forum'

const props = defineProps<{
  post: ForumPost
}>()

defineEmits<{
  click: []
}>()

const {t} = useI18n()

const excerpt = computed(() => {
  const text = props.post.content.replace(/<[^>]*>/g, '')
  return text.length > 120 ? text.slice(0, 120) + '...' : text
})

function formatTime(dateStr: string) {
  const date = new Date(dateStr)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  const minutes = Math.floor(diff / 60000)
  if (minutes < 1) return '刚刚'
  if (minutes < 60) return minutes + '分钟前'
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return hours + '小时前'
  const days = Math.floor(hours / 24)
  if (days < 30) return days + '天前'
  return date.toLocaleDateString()
}
</script>

<style scoped>
.post-card {
  display: flex;
  gap: 16px;
  padding: 20px 24px;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: 16px;
  cursor: pointer;
  transition: border-color 0.15s;
  position: relative;
}

.post-card:hover {
  border-color: var(--color-on-surface);
}

.post-card.pinned {
  border-color: var(--color-outline-variant);
}

.pinned-bar {
  position: absolute;
  left: 0;
  top: 12px;
  bottom: 12px;
  width: 3px;
  background: var(--color-primary);
  border-radius: 0 2px 2px 0;
}

.post-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
}

.post-header {
  display: flex;
  gap: 6px;
}

.badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-family: var(--font-body);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.03em;
  padding: 2px 8px;
  border-radius: 4px;
}

.badge.pinned {
  color: var(--color-primary);
  background: var(--color-surface-container-high);
}

.badge.essence {
  color: #eab308;
  background: rgba(234, 179, 8, 0.12);
}

.badge.locked {
  color: var(--color-muted);
  background: var(--color-surface-container-high);
}

.post-title {
  margin: 0;
  font-family: var(--font-heading);
  font-size: 18px;
  font-weight: 600;
  color: var(--color-on-surface);
  line-height: 1.3;
}

.post-excerpt {
  margin: 0;
  font-family: var(--font-body);
  font-size: 14px;
  color: var(--color-muted);
  line-height: 1.5;
}

.post-footer {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-top: 4px;
}

.post-author {
  display: flex;
  align-items: center;
  gap: 8px;
  font-family: var(--font-body);
  font-size: 13px;
  color: var(--color-muted);
}

.author-avatar {
  width: 24px;
  height: 24px;
  display: grid;
  place-items: center;
  background: var(--color-surface-container-high);
  border-radius: 50%;
  color: var(--color-muted);
}

.post-stats {
  display: flex;
  gap: 12px;
  font-family: var(--font-body);
  font-size: 12px;
  color: var(--color-muted);
}

.post-stats span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.post-time {
  margin-left: auto;
  font-family: var(--font-body);
  font-size: 12px;
  color: var(--color-muted);
}
</style>
