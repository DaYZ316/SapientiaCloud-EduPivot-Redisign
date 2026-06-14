<template>
  <div class="post-detail-page">
    <button class="back-link" @click="router.back()">
      <ArrowLeft :size="16"/>
      返回
    </button>

    <div v-if="loading" class="loading-state">
      <div class="skeleton-title shimmer"></div>
      <div class="skeleton-block shimmer"></div>
    </div>

    <div v-else-if="post" class="post-layout">
      <article class="post-article">
        <div class="post-header">
          <div class="post-badges">
            <span v-if="post.isTop" class="badge pinned"><Pin :size="12"/> {{ t('forum.pinned') }}</span>
            <span v-if="post.isEssence" class="badge essence"><Star :size="12"/> {{ t('forum.essence') }}</span>
            <span v-if="post.isLocked" class="badge locked"><Lock :size="12"/> {{ t('forum.locked') }}</span>
          </div>
          <h1 class="post-title">{{ post.title }}</h1>
          <div class="post-meta">
            <span class="author"></span>
            <span class="time">{{ formatTime(post.createdAt) }}</span>
            <span><Eye :size="14"/> {{ post.viewCount }} {{ t('forum.views') }}</span>
            <span><Heart :size="14"/> {{ post.likeCount }} {{ t('forum.likes') }}</span>
          </div>
        </div>

        <div class="post-content">{{ post.content }}</div>

        <div class="post-actions">
          <button class="action-btn" @click="handleLike">
            <Heart :size="16" :class="{filled: liked}"/>
            {{ post.likeCount }} {{ t('forum.likes') }}
          </button>
          <span class="action-btn"><MessageCircle :size="16"/> {{ post.replyCount }} {{ t('forum.replies') }}</span>
        </div>
      </article>

      <section class="replies-section">
        <h2>{{ t('forum.replies') }} ({{ replyTree.length }})</h2>

        <ReplyTree
          :replies="replyTree"
          @like="handleReplyLike"
          @reply="handleReplyTo"
        />

        <ReplyEditor
          v-if="!post.isLocked"
          :reply-to="replyingTo"
          @cancel-reply="replyingTo = null"
          @submit="handleCreateReply"
        />
      </section>
    </div>
  </div>
</template>

<script lang="ts" setup>
import {onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import {ArrowLeft, Pin, Star, Lock, Eye, Heart, MessageCircle} from 'lucide-vue-next'
import {getPost, getReplyTree, togglePostLike, viewPost, createReply, toggleReplyLike} from '@/features/forum/api/forum'
import type {ForumPost, ForumReply} from '@/features/forum/types/forum'
import {notify} from '@/shared/composables/useGlobalNotification'
import ReplyTree from '@/features/forum/components/ReplyTree.vue'
import ReplyEditor from '@/features/forum/components/ReplyEditor.vue'

const {t} = useI18n()
const route = useRoute()
const router = useRouter()

const postId = route.params.id as string

const loading = ref(true)
const post = ref<ForumPost | null>(null)
const replyTree = ref<ForumReply[]>([])
const liked = ref(false)
const replyingTo = ref<ForumReply | null>(null)

onMounted(async () => {
  try {
    const [postData, repliesData] = await Promise.all([
      getPost(postId),
      getReplyTree(postId),
    ])
    post.value = postData
    replyTree.value = repliesData || []
    await viewPost(postId)
  } finally {
    loading.value = false
  }
})

function formatTime(dateStr: string) {
  return new Date(dateStr).toLocaleString()
}

async function handleLike() {
  if (!post.value) return
  try {
    await togglePostLike(postId)
    liked.value = !liked.value
    post.value.likeCount += liked.value ? 1 : -1
  } catch {
    notify.error('操作失败')
  }
}

async function handleReplyLike(reply: ForumReply) {
  try {
    await toggleReplyLike(reply.id)
    reply.likeCount += 1
  } catch {
    notify.error('操作失败')
  }
}

function handleReplyTo(reply: ForumReply) {
  replyingTo.value = reply
}

async function handleCreateReply(content: string, replyToUserId?: string | null) {
  if (!post.value) return
  try {
    await createReply({
      postId: post.value.id,
      forumId: post.value.forumId,
      courseId: post.value.courseId,
      content,
      parentReplyId: replyingTo.value?.id || null,
      replyToUserId: replyToUserId || replyingTo.value?.sysUserId || null,
    })
    replyingTo.value = null
    notify.success('回复成功')
    const data = await getReplyTree(postId)
    replyTree.value = data || []
    post.value.replyCount += 1
  } catch {
    notify.error('回复失败')
  }
}
</script>

<style scoped>
.post-detail-page {
  max-width: 100%;
}

.back-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0;
  border: none;
  background: none;
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 13px;
  cursor: pointer;
  margin-bottom: 32px;
}

.back-link:hover {
  color: var(--color-on-surface);
}

.post-layout {
  display: flex;
  flex-direction: column;
  gap: 32px;
}

.post-article {
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-lg);
  padding: 32px;
}

.post-header {
  margin-bottom: 24px;
}

.post-badges {
  display: flex;
  gap: 6px;
  margin-bottom: 12px;
}

.badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-family: var(--font-body);
  font-size: 11px;
  font-weight: 600;
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
  margin: 0 0 12px;
  font-family: var(--font-heading);
  font-size: 32px;
  font-weight: 600;
  color: var(--color-on-surface);
}

.post-meta {
  display: flex;
  align-items: center;
  gap: 16px;
  font-family: var(--font-body);
  font-size: 13px;
  color: var(--color-muted);
}

.post-meta span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.post-content {
  font-family: var(--font-body);
  font-size: 15px;
  color: var(--color-on-surface);
  line-height: 1.8;
  white-space: pre-wrap;
}

.post-actions {
  display: flex;
  gap: 16px;
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px solid var(--color-outline-light);
}

.action-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: none;
  background: none;
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 14px;
  cursor: pointer;
  padding: 8px 12px;
  border-radius: var(--radius-sm);
  transition: background 0.15s;
}

.action-btn:hover {
  background: var(--color-surface-container);
  color: var(--color-on-surface);
}

.action-btn svg.filled {
  color: var(--color-error);
}

.replies-section {
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-lg);
  padding: 24px 32px;
}

.replies-section h2 {
  margin: 0 0 16px;
  font-family: var(--font-heading);
  font-size: 22px;
  font-weight: 600;
  color: var(--color-on-surface);
}

.loading-state {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.skeleton-title {
  height: 36px;
  width: 400px;
  border-radius: var(--radius-sm);
}

.skeleton-block {
  height: 200px;
  width: 100%;
  border-radius: var(--radius-lg);
}

.shimmer {
  background: linear-gradient(110deg, var(--color-surface-container-high) 8%, var(--color-surface-canvas) 18%, var(--color-surface-container-high) 33%);
  background-size: 200% 100%;
  animation: shimmer 1.4s ease-in-out infinite;
}

@keyframes shimmer {
  to { background-position-x: -200%; }
}
</style>
