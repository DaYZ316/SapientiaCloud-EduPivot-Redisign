<template>
  <div class="reply-tree">
    <div v-if="replies.length === 0" class="empty-replies">
      <MessageCircle :size="28" stroke-width="1.4"/>
      <p>{{ t('forum.noReplies') }}</p>
      <p class="empty-desc">{{ t('forum.noRepliesDesc') }}</p>
    </div>
    <div v-else class="reply-list">
      <div
        v-for="reply in replies"
        :key="reply.id"
        class="reply-item"
        :class="{accepted: reply.isAccepted}"
      >
        <div class="reply-avatar">
          <User :size="16"/>
        </div>

        <div class="reply-content">
          <div class="reply-header">
            <span class="reply-author">{{ reply.isAnonymous ? t('forum.anonymousUser') : '' }}</span>
            <span v-if="reply.isAccepted" class="accepted-badge">✓ {{ t('forum.accepted') }}</span>
            <span class="reply-time">{{ formatTime(reply.createdAt) }}</span>
          </div>

          <p class="reply-text">{{ reply.content }}</p>

          <div class="reply-actions">
            <button v-if="showLike" class="action-btn" @click="$emit('like', reply)">
              <Heart :size="14"/>
              {{ reply.likeCount }}
            </button>
            <button v-if="canReply" class="action-btn" @click="$emit('reply', reply)">
              <MessageCircle :size="14"/>
              {{ t('forum.reply') }}
            </button>
          </div>

          <div v-if="reply.children && reply.children.length" class="nested-replies">
            <div
              v-for="child in reply.children"
              :key="child.id"
              class="reply-item nested"
            >
              <div class="reply-avatar small">
                <User :size="12"/>
              </div>
              <div class="reply-content">
                <div class="reply-header">
                  <span class="reply-author">{{ child.isAnonymous ? t('forum.anonymousUser') : '' }}</span>
                  <span class="reply-time">{{ formatTime(child.createdAt) }}</span>
                </div>
                <p class="reply-text">{{ child.content }}</p>
                <div class="reply-actions">
                  <button v-if="showLike" class="action-btn" @click="$emit('like', child)">
                    <Heart :size="14"/>
                    {{ child.likeCount }}
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {User, Heart, MessageCircle} from 'lucide-vue-next'
import type {ForumReply} from '@/features/forum/types/forum'

withDefaults(defineProps<{
  replies: ForumReply[]
  showLike?: boolean
  canReply?: boolean
}>(), {
  showLike: true,
  canReply: true,
})

defineEmits<{
  like: [reply: ForumReply]
  reply: [reply: ForumReply]
}>()

const {t} = useI18n()

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
.reply-tree {
  display: flex;
  flex-direction: column;
}

.empty-replies {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 48px 24px;
  color: var(--color-muted);
  text-align: center;
}

.empty-replies p {
  margin: 0;
  font-family: var(--font-body);
  font-size: 15px;
}

.empty-desc {
  font-size: 13px !important;
}

.reply-list {
  display: flex;
  flex-direction: column;
}

.reply-item {
  display: flex;
  gap: 12px;
  padding: 16px 0;
  border-bottom: 1px solid var(--color-outline-light);
}

.reply-item:last-child {
  border-bottom: none;
}

.reply-item.accepted {
  border: 1px solid #22c55e;
  border-radius: var(--radius-md);
  padding: 16px;
  margin: 4px 0;
  background: rgba(34, 197, 94, 0.04);
}

.reply-avatar {
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  background: var(--color-surface-container-high);
  border-radius: 50%;
  color: var(--color-muted);
  flex-shrink: 0;
}

.reply-avatar.small {
  width: 24px;
  height: 24px;
}

.reply-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.reply-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.reply-author {
  font-family: var(--font-body);
  font-size: 13px;
  font-weight: 600;
  color: var(--color-on-surface);
}

.accepted-badge {
  font-family: var(--font-body);
  font-size: 11px;
  font-weight: 600;
  color: #22c55e;
  background: rgba(34, 197, 94, 0.12);
  padding: 1px 6px;
  border-radius: 4px;
}

.reply-time {
  font-family: var(--font-body);
  font-size: 12px;
  color: var(--color-muted);
}

.reply-text {
  margin: 0;
  font-family: var(--font-body);
  font-size: 14px;
  color: var(--color-on-surface);
  line-height: 1.6;
}

.reply-actions {
  display: flex;
  gap: 12px;
}

.action-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: none;
  background: none;
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 12px;
  cursor: pointer;
  padding: 4px 0;
}

.action-btn:hover {
  color: var(--color-on-surface);
}

.nested-replies {
  margin-top: 8px;
  padding-left: 12px;
  border-left: 2px solid var(--color-outline-light);
  display: flex;
  flex-direction: column;
}

.reply-item.nested {
  padding: 8px 0;
}
</style>
