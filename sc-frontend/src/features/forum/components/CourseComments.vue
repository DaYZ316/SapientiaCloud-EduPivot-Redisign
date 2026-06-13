<template>
  <div class="course-comments">
    <form v-if="canComment" class="comment-composer" @submit.prevent="submitComment">
      <textarea
        v-model="commentContent"
        class="comment-input"
        rows="4"
        :placeholder="t('forum.commentPlaceholder')"
      ></textarea>
      <div class="composer-footer">
        <label class="checkbox-label">
          <input v-model="commentAnonymous" type="checkbox"/>
          {{ t('forum.anonymous') }}
        </label>
        <button class="btn-submit" :disabled="submitting || !commentContent.trim()">
          {{ t('forum.submitComment') }}
        </button>
      </div>
    </form>

    <div v-else class="readonly-hint">
      {{ t('forum.commentsReadonly') }}
    </div>

    <div v-if="loading" class="loading-list">
      <div v-for="i in 3" :key="i" class="skeleton-comment shimmer"></div>
    </div>

    <div v-else-if="comments.length === 0" class="empty-state">
      <MessageCircle :size="32" stroke-width="1.4"/>
      <h3>{{ t('forum.noComments') }}</h3>
      <p>{{ t('forum.noCommentsDesc') }}</p>
    </div>

    <div v-else class="comment-list">
      <article v-for="comment in comments" :key="comment.id" class="comment-card">
        <div class="comment-avatar">
          <User :size="18"/>
        </div>
        <div class="comment-body">
          <div class="comment-meta">
            <strong>{{ comment.isAnonymous ? t('forum.anonymousUser') : t('forum.courseMember') }}</strong>
            <span>{{ formatTime(comment.createdAt) }}</span>
            <span v-if="comment.replyCount">{{ comment.replyCount }} {{ t('forum.replies') }}</span>
          </div>
          <p class="comment-content">{{ comment.content }}</p>
          <div class="comment-actions">
            <button class="action-btn" type="button" @click="toggleReplies(comment.id)">
              <MessageCircle :size="14"/>
              {{ repliesOpen[comment.id] ? t('forum.hideReplies') : t('forum.showReplies') }}
            </button>
            <button v-if="canComment" class="action-btn" type="button" @click="startReply(comment.id, null)">
              {{ t('forum.reply') }}
            </button>
          </div>

          <div v-if="repliesOpen[comment.id]" class="replies-panel">
            <div v-if="replyLoading[comment.id]" class="reply-loading">{{ t('forum.loadingReplies') }}</div>
            <ReplyTree
              v-else
              :replies="repliesByComment[comment.id] || []"
              :show-like="false"
              :can-reply="canComment"
              @reply="startReply(comment.id, $event)"
            />

            <form
              v-if="canComment && replyingCommentId === comment.id"
              class="reply-composer"
              @submit.prevent="submitReply(comment.id)"
            >
              <div v-if="replyingTo" class="reply-target">
                {{ t('forum.replyTo') }} {{ replyingTo.isAnonymous ? t('forum.anonymousUser') : t('forum.courseMember') }}
                <button type="button" @click="cancelReply">x</button>
              </div>
              <textarea
                v-model="replyContent"
                class="comment-input"
                rows="3"
                :placeholder="t('forum.replyPlaceholder')"
              ></textarea>
              <div class="composer-footer">
                <label class="checkbox-label">
                  <input v-model="replyAnonymous" type="checkbox"/>
                  {{ t('forum.anonymous') }}
                </label>
                <button class="btn-submit" :disabled="submittingReply || !replyContent.trim()">
                  {{ t('forum.submitReply') }}
                </button>
              </div>
            </form>
          </div>
        </div>
      </article>
    </div>
  </div>
</template>

<script lang="ts" setup>
import {onMounted, reactive, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {MessageCircle, User} from 'lucide-vue-next'
import {
  createCommentReply,
  createCourseComment,
  getCommentReplies,
  getCourseComments,
} from '@/features/forum/api/forum'
import type {ForumPost, ForumReply} from '@/features/forum/types/forum'
import {notify} from '@/shared/composables/useGlobalNotification'
import ReplyTree from '@/features/forum/components/ReplyTree.vue'

const props = defineProps<{
  courseId: string
  canComment: boolean
}>()

const {t} = useI18n()

const loading = ref(true)
const submitting = ref(false)
const submittingReply = ref(false)
const comments = ref<ForumPost[]>([])
const commentContent = ref('')
const commentAnonymous = ref(false)
const replyContent = ref('')
const replyAnonymous = ref(false)
const replyingCommentId = ref<string | null>(null)
const replyingTo = ref<ForumReply | null>(null)
const repliesOpen = reactive<Record<string, boolean>>({})
const replyLoading = reactive<Record<string, boolean>>({})
const repliesByComment = reactive<Record<string, ForumReply[]>>({})

onMounted(loadComments)

async function loadComments() {
  loading.value = true
  try {
    const data = await getCourseComments(props.courseId, {page: 1, size: 50})
    comments.value = data.records || []
  } finally {
    loading.value = false
  }
}

async function submitComment() {
  if (!commentContent.value.trim()) return
  submitting.value = true
  try {
    await createCourseComment(props.courseId, {
      content: commentContent.value,
      isAnonymous: commentAnonymous.value ? 1 : 0,
    })
    commentContent.value = ''
    commentAnonymous.value = false
    notify.success(t('forum.commentSuccess'))
    await loadComments()
  } catch {
    notify.error(t('forum.commentFailed'))
  } finally {
    submitting.value = false
  }
}

async function toggleReplies(commentId: string) {
  repliesOpen[commentId] = !repliesOpen[commentId]
  if (repliesOpen[commentId] && !repliesByComment[commentId]) {
    await loadReplies(commentId)
  }
}

async function loadReplies(commentId: string) {
  replyLoading[commentId] = true
  try {
    repliesByComment[commentId] = await getCommentReplies(commentId)
  } finally {
    replyLoading[commentId] = false
  }
}

function startReply(commentId: string, reply: ForumReply | null) {
  repliesOpen[commentId] = true
  replyingCommentId.value = commentId
  replyingTo.value = reply
}

function cancelReply() {
  replyingCommentId.value = null
  replyingTo.value = null
  replyContent.value = ''
  replyAnonymous.value = false
}

async function submitReply(commentId: string) {
  if (!replyContent.value.trim()) return
  submittingReply.value = true
  try {
    await createCommentReply(commentId, {
      content: replyContent.value,
      parentReplyId: replyingTo.value?.id || null,
      replyToUserId: replyingTo.value?.sysUserId || null,
      isAnonymous: replyAnonymous.value ? 1 : 0,
    })
    cancelReply()
    await loadReplies(commentId)
    const comment = comments.value.find(item => item.id === commentId)
    if (comment) comment.replyCount += 1
    notify.success(t('forum.replySuccess'))
  } catch {
    notify.error(t('forum.replyFailed'))
  } finally {
    submittingReply.value = false
  }
}

function formatTime(dateStr: string) {
  return new Date(dateStr).toLocaleString()
}
</script>

<style scoped>
.course-comments {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.comment-composer,
.reply-composer {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-md);
}

.comment-input {
  width: 100%;
  padding: 12px 14px;
  background: var(--color-surface-container);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  color: var(--color-on-surface);
  font-family: var(--font-body);
  font-size: 14px;
  line-height: 1.6;
  resize: vertical;
  outline: none;
}

.comment-input:focus {
  border-color: var(--color-on-surface);
}

.composer-footer,
.comment-actions,
.comment-meta {
  display: flex;
  align-items: center;
  gap: 12px;
}

.composer-footer {
  justify-content: space-between;
}

.checkbox-label {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-family: var(--font-body);
  font-size: 13px;
  color: var(--color-muted);
}

.btn-submit {
  padding: 8px 16px;
  border: none;
  border-radius: var(--radius-sm);
  background: var(--color-primary);
  color: var(--color-on-primary);
  font-family: var(--font-body);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}

.btn-submit:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.readonly-hint {
  padding: 14px 16px;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 13px;
}

.comment-list,
.loading-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.comment-card {
  display: flex;
  gap: 12px;
  padding: 18px;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-md);
}

.comment-avatar {
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  flex-shrink: 0;
  background: var(--color-surface-container-high);
  border-radius: 50%;
  color: var(--color-muted);
}

.comment-body {
  flex: 1;
  min-width: 0;
}

.comment-meta {
  flex-wrap: wrap;
  font-family: var(--font-body);
  font-size: 12px;
  color: var(--color-muted);
}

.comment-meta strong {
  color: var(--color-on-surface);
  font-size: 13px;
}

.comment-content {
  margin: 10px 0 12px;
  color: var(--color-on-surface);
  font-family: var(--font-body);
  font-size: 14px;
  line-height: 1.7;
  white-space: pre-wrap;
}

.action-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 0;
  border: none;
  background: none;
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 12px;
  cursor: pointer;
}

.action-btn:hover {
  color: var(--color-on-surface);
}

.replies-panel {
  margin-top: 12px;
  padding-left: 16px;
  border-left: 2px solid var(--color-outline-light);
}

.reply-loading {
  padding: 12px 0;
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 13px;
}

.reply-composer {
  margin-top: 12px;
  background: var(--color-surface-container);
}

.reply-target {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 13px;
}

.reply-target button {
  width: 22px;
  height: 22px;
  border: none;
  background: none;
  color: var(--color-muted);
  cursor: pointer;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 48px 24px;
  color: var(--color-muted);
  text-align: center;
}

.empty-state h3,
.empty-state p {
  margin: 0;
}

.empty-state h3 {
  color: var(--color-on-surface);
  font-family: var(--font-heading);
  font-size: 20px;
  font-weight: 600;
}

.empty-state p {
  font-family: var(--font-body);
  font-size: 14px;
}

.skeleton-comment {
  height: 128px;
  border-radius: var(--radius-md);
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
