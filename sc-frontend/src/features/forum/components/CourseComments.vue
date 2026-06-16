<template>
  <div class="course-comments">
    <form v-if="canComment" class="comment-composer" @submit.prevent="submitComment">
      <div class="composer-head">
        <div>
          <span class="composer-kicker">{{ t('forum.composerKicker') }}</span>
          <strong>{{ t('forum.composerTitle') }}</strong>
        </div>
        <span>{{ t('forum.composerHint') }}</span>
      </div>
      <BaseTextEditor
          v-model="commentContent"
          v-model:image-assets="commentImages"
          :image-upload-options="imageUploadOptions"
          :min-rows="3"
          :placeholder="t('forum.commentPlaceholder')"
          :prepare-image-file="validateImageFile"
          :rows="3"
          @image-upload-error="handleUploadError"
      />
      <div class="composer-footer">
        <div class="composer-actions">
          <button :disabled="submitting || !commentContent.trim()" class="btn-submit" type="submit">
            <Send :size="14" stroke-width="1.8"/>
            {{ t('forum.submitComment') }}
          </button>
        </div>
      </div>
    </form>

    <div v-else class="readonly-hint">
      {{ t('forum.commentsReadonly') }}
    </div>

    <div v-if="!loading" class="comment-summary">
      <strong>{{ t('forum.commentCount', {count: total}) }}</strong>
      <span>{{ canComment ? t('forum.commentsParticipationOpen') : t('forum.commentsReadonlyShort') }}</span>
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
        <UserAvatarLink
            :avatar-url="userMap[comment.sysUserId]?.avatarUrl"
            :display-name="userMap[comment.sysUserId]?.displayName"
            :linkable="false"
            :role="userMap[comment.sysUserId]?.role"
            :show-name="false"
            :user-id="comment.sysUserId"
            class="comment-author-avatar"
            size="medium"
        />
        <div class="comment-body">
          <div class="comment-meta">
            <div class="comment-identity">
              <strong>{{ userMap[comment.sysUserId]?.displayName || t('forum.courseMember') }}</strong>
              <span>{{ formatTime(comment.createdAt) }}</span>
            </div>
            <span v-if="comment.replyCount" class="reply-count">{{ comment.replyCount }} {{ t('forum.replies') }}</span>
          </div>

          <!-- 编辑模式 -->
          <div v-if="editingCommentId === comment.id" class="edit-composer">
            <BaseTextEditor
                v-model="editingContent"
                v-model:image-assets="editingImages"
                :image-upload-options="imageUploadOptions"
                :min-rows="3"
                :prepare-image-file="validateImageFile"
                :rows="3"
                @image-upload-error="handleUploadError"
            />
            <div class="composer-footer">
              <div class="composer-actions">
                <button class="btn-cancel" type="button" @click="cancelEditComment">{{ t('forum.cancelEdit') }}</button>
                <button :disabled="savingEdit || !editingContent.trim()" class="btn-submit"
                        @click="saveEditComment(comment)">
                  {{ t('forum.saveEdit') }}
                </button>
              </div>
            </div>
          </div>

          <!-- 展示模式 -->
          <template v-else>
            <ForumContentPreview
                :content="comment.content"
                :image-urls="comment.imageUrls"
                class="comment-content"
            />
            <div class="comment-actions">
              <button class="action-btn" type="button" @click="toggleReplies(comment.id)">
                <MessageCircle :size="14"/>
                {{ repliesOpen[comment.id] ? t('forum.hideReplies') : t('forum.showReplies') }}
              </button>
              <button v-if="canComment" class="action-btn" type="button" @click="startReply(comment.id, null)">
                <MessageCircle :size="14"/>
                {{ t('forum.reply') }}
              </button>
              <button v-if="canEditComment(comment)" class="action-btn" type="button"
                      @click="startEditComment(comment)">
                <Pencil :size="13"/>
                {{ t('forum.editComment') }}
              </button>
              <button v-if="canEditComment(comment)" class="action-btn danger" type="button"
                      @click="handleDeleteComment(comment)">
                <Trash2 :size="13"/>
                {{ t('forum.deleteComment') }}
              </button>
            </div>
          </template>

          <div v-if="repliesOpen[comment.id]" class="replies-panel">
            <div v-if="replyLoading[comment.id]" class="reply-loading">{{ t('forum.loadingReplies') }}</div>
            <ReplyTree
                v-else
                :can-manage="canManageCourse"
                :can-reply="canComment"
                :current-user-id="currentUserId"
                :replies="repliesByComment[comment.id] || []"
                :show-like="false"
                :user-map="userMap"
                @deleted="handleReplyDeleted(comment.id)"
                @reply="startReply(comment.id, $event)"
            />

            <form
                v-if="canComment && replyingCommentId === comment.id"
                class="reply-composer"
                @submit.prevent="submitReply(comment.id)"
            >
              <div v-if="replyingTo" class="reply-target">
                {{ t('forum.replyTo') }} {{ replyTargetName }}
                <button type="button" @click="cancelReply">x</button>
              </div>
              <BaseTextEditor
                  v-model="replyContent"
                  v-model:image-assets="replyImages"
                  :image-upload-options="imageUploadOptions"
                  :min-rows="2"
                  :placeholder="replyPlaceholder"
                  :prepare-image-file="validateImageFile"
                  :rows="2"
                  @image-upload-error="handleUploadError"
              />
              <div class="composer-footer">
                <div class="composer-actions">
                  <button :disabled="submittingReply || !replyContent.trim()" class="btn-submit" type="submit">
                    <Send :size="14" stroke-width="1.8"/>
                    {{ t('forum.submitReply') }}
                  </button>
                </div>
              </div>
            </form>
          </div>
        </div>
      </article>
    </div>
    <BasePagination
        v-if="total > 0"
        :aria-label="t('courseDetail.pagination')"
        :disabled="loading"
        :next-title="t('courseDetail.nextPage')"
        :page="currentPage"
        :previous-title="t('courseDetail.previousPage')"
        :size="COMMENT_PAGE_SIZE"
        :total="total"
        @change="loadComments"
    />
  </div>
</template>

<script lang="ts" setup>
import {computed, onMounted, reactive, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {MessageCircle, Pencil, Send, Trash2} from 'lucide-vue-next'
import {getUsersBasicInfo} from '@/features/user/api/user'
import type {UserBasicInfo} from '@/features/user/types/user'
import BasePagination from '@/shared/components/BasePagination.vue'
import UserAvatarLink from '@/shared/components/UserAvatarLink.vue'
import {
  createCommentReply,
  createCourseComment,
  deletePost,
  getCommentReplies,
  getCourseComments,
  updatePost,
} from '@/features/forum/api/forum'
import type {ForumPost, ForumReply} from '@/features/forum/types/forum'
import type {FileAsset} from '@/features/storage/types/storage'
import {confirmDialog} from '@/shared/composables/useConfirmDialog'
import {notify} from '@/shared/composables/useGlobalNotification'
import BaseTextEditor from '@/shared/components/BaseTextEditor.vue'
import ForumContentPreview from '@/features/forum/components/ForumContentPreview.vue'
import ReplyTree from '@/features/forum/components/ReplyTree.vue'

const props = defineProps<{
  courseId: string
  canComment: boolean
  canManageCourse?: boolean
  currentUserId?: string
}>()

const {t} = useI18n()
const COMMENT_PAGE_SIZE = 10

const loading = ref(true)
const submitting = ref(false)
const submittingReply = ref(false)
const comments = ref<ForumPost[]>([])
const currentPage = ref(1)
const total = ref(0)
const commentContent = ref('')
const replyContent = ref('')
const commentImages = ref<FileAsset[]>([])
const replyImages = ref<FileAsset[]>([])
const replyingCommentId = ref<string | null>(null)
const replyingTo = ref<ForumReply | null>(null)
const repliesOpen = reactive<Record<string, boolean>>({})
const replyLoading = reactive<Record<string, boolean>>({})
const repliesByComment = reactive<Record<string, ForumReply[]>>({})

// 用户信息缓存
const userMap = reactive<Record<string, UserBasicInfo>>({})

// 编辑评论状�?
const editingCommentId = ref<string | null>(null)
const editingContent = ref('')
const editingImages = ref<FileAsset[]>([])
const savingEdit = ref(false)

const imageUploadOptions = computed(() => ({
  usage: 'FORUM_IMAGE' as const,
  scopeType: 'COURSE' as const,
  scopeId: props.courseId,
  accept: 'image/jpeg,image/png,image/webp',
  buttonLabel: t('forum.uploadImage'),
}))

const replyTargetName = computed(() => {
  const userId = replyingTo.value?.sysUserId
  if (!userId) return t('forum.courseMember')
  return userMap[userId]?.displayName || t('forum.courseMember')
})

const replyPlaceholder = computed(() => {
  if (!replyingTo.value) return t('forum.replyPlaceholder')
  return `${t('forum.replyTo')} ${replyTargetName.value}...`
})

onMounted(loadComments)

async function loadComments(page = currentPage.value) {
  loading.value = true
  try {
    let nextPage = page
    let data = await getCourseComments(props.courseId, {page: nextPage, size: COMMENT_PAGE_SIZE})
    while ((data.records || []).length === 0 && data.total > 0 && nextPage > 1) {
      nextPage -= 1
      data = await getCourseComments(props.courseId, {page: nextPage, size: COMMENT_PAGE_SIZE})
    }
    comments.value = data.records || []
    currentPage.value = data.page
    total.value = data.total
    cacheUsersFromItems(comments.value)
    await fetchUsersForItems(comments.value.map(c => c.sysUserId))
  } finally {
    loading.value = false
  }
}

function cacheUserInfo(userInfo?: UserBasicInfo | null) {
  if (!userInfo?.id) return
  userMap[userInfo.id] = userInfo
}

function cacheUsersFromItems(items: Array<{ userInfo?: UserBasicInfo | null }>) {
  items.forEach(item => cacheUserInfo(item.userInfo))
}

function flattenReplies(replies: ForumReply[]): ForumReply[] {
  return replies.flatMap(reply => [reply, ...flattenReplies(reply.children || [])])
}

async function fetchUsersForItems(userIds: string[]) {
  const newIds = userIds.filter(id => id && !userMap[id])
  if (newIds.length === 0) return
  try {
    const users = await getUsersBasicInfo(newIds)
    for (const u of users) {
      userMap[u.id] = u
    }
  } catch {
    // 静默失败，头像显示默认�?
  }
}

async function submitComment() {
  if (!commentContent.value.trim()) return
  submitting.value = true
  try {
    await createCourseComment(props.courseId, {
      content: commentContent.value,
      imageUrls: commentImages.value.map(image => image.id),
    })
    commentContent.value = ''
    commentImages.value = []
    notify.success(t('forum.commentSuccess'))
    await loadComments(1)
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
    const replies = repliesByComment[commentId] || []
    const flatReplies = flattenReplies(replies)
    cacheUsersFromItems(flatReplies)
    await fetchUsersForItems(flatReplies.map(reply => reply.sysUserId))
  } finally {
    replyLoading[commentId] = false
  }
}

async function startReply(commentId: string, reply: ForumReply | null) {
  repliesOpen[commentId] = true
  replyingCommentId.value = commentId
  replyingTo.value = reply
  if (!repliesByComment[commentId]) {
    await loadReplies(commentId)
  }
}

function cancelReply() {
  replyingCommentId.value = null
  replyingTo.value = null
  replyContent.value = ''
  replyImages.value = []
}

async function submitReply(commentId: string) {
  if (!replyContent.value.trim()) return
  submittingReply.value = true
  try {
    await createCommentReply(commentId, {
      content: replyContent.value,
      parentReplyId: replyingTo.value?.id || null,
      replyToUserId: replyingTo.value?.sysUserId || null,
      imageUrls: replyImages.value.map(image => image.id),
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

function validateImageFile(file: File) {
  if (!file.type.startsWith('image/')) {
    notify.error(t('forum.imageTypeError'))
    return null
  }
  if (file.size > 5 * 1024 * 1024) {
    notify.error(t('forum.imageSizeError'))
    return null
  }
  return file
}

function handleUploadError() {
  notify.error(t('forum.imageUploadFailed'))
}

function canEditComment(comment: ForumPost): boolean {
  return Boolean(props.currentUserId && (comment.sysUserId === props.currentUserId || props.canManageCourse))
}

function startEditComment(comment: ForumPost) {
  editingCommentId.value = comment.id
  editingContent.value = comment.content
  editingImages.value = []
}

function cancelEditComment() {
  editingCommentId.value = null
  editingContent.value = ''
  editingImages.value = []
}

async function saveEditComment(comment: ForumPost) {
  if (!editingContent.value.trim()) return
  savingEdit.value = true
  try {
    const payload = editingImages.value.length > 0
        ? {content: editingContent.value, imageUrls: editingImages.value.map(image => image.id)}
        : {content: editingContent.value}
    await updatePost(comment.id, payload)
    comment.content = editingContent.value
    if (editingImages.value.length > 0) {
      comment.imageUrls = editingImages.value
          .map(image => image.url)
          .filter((url): url is string => Boolean(url))
    }
    cancelEditComment()
    notify.success(t('forum.commentUpdated'))
  } catch {
    notify.error(t('forum.commentFailed'))
  } finally {
    savingEdit.value = false
  }
}

async function handleDeleteComment(comment: ForumPost) {
  if (!(await confirmDialog({message: t('forum.confirmDeleteComment'), confirmVariant: 'danger'}))) return
  try {
    await deletePost(comment.id)
    await loadComments()
    notify.success(t('forum.commentDeleted'))
  } catch {
    notify.error(t('forum.commentFailed'))
  }
}

function handleReplyDeleted(commentId: string) {
  const comment = comments.value.find(item => item.id === commentId)
  if (comment && comment.replyCount > 0) comment.replyCount -= 1
  void loadReplies(commentId)
}
</script>

<style scoped>
.course-comments {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.comment-composer,
.reply-composer {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: 0;
}

.comment-composer :deep(.base-text-editor),
.comment-composer :deep(.base-text-editor-input figure.chapter-image img),
.comment-composer :deep(.base-text-editor-image),
.reply-composer :deep(.base-text-editor),
.reply-composer :deep(.base-text-editor-input figure.chapter-image img),
.reply-composer :deep(.base-text-editor-image),
.edit-composer :deep(.base-text-editor),
.edit-composer :deep(.base-text-editor-input figure.chapter-image img),
.edit-composer :deep(.base-text-editor-image) {
  border-radius: 0;
}

.composer-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.composer-head div {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.composer-kicker {
  color: var(--color-muted);
  font-family: var(--font-label);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.06em;
  line-height: 1;
  text-transform: uppercase;
}

.composer-head strong {
  color: var(--color-on-surface);
  font-family: var(--font-label);
  font-size: 14px;
  font-weight: 700;
  line-height: 1.3;
}

.composer-head > span {
  max-width: 260px;
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 12px;
  line-height: 1.45;
  text-align: right;
}

.composer-footer,
.comment-actions,
.comment-meta {
  display: flex;
  align-items: center;
  gap: 12px;
}

.composer-footer {
  justify-content: flex-end;
  flex-wrap: wrap;
}

.composer-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  flex-wrap: wrap;
}

.btn-submit {
  min-height: 36px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  padding: 0 14px;
  border: none;
  border-radius: var(--radius-sm);
  background: var(--color-primary);
  color: var(--color-on-primary);
  font-family: var(--font-body);
  font-size: 13px;
  font-weight: 400;
  cursor: pointer;
  transition: background 0.2s ease, opacity 0.2s ease, transform 0.2s ease;
}

.btn-submit:hover:not(:disabled) {
  background: var(--color-primary-soft);
}

.btn-submit:active:not(:disabled),
.btn-cancel:active,
.action-btn:active {
  transform: translateY(1px);
}

.btn-submit:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.readonly-hint {
  padding: 12px 14px;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: 0;
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 13px;
}

.comment-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 2px 2px 12px;
  border-bottom: 1px solid var(--color-outline-light);
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 13px;
  line-height: 1.4;
}

.comment-summary strong {
  color: var(--color-on-surface);
  font-family: var(--font-label);
  font-size: 13px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.comment-list,
.loading-list {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.comment-card {
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr);
  gap: 12px;
  padding: 16px 0;
  background: transparent;
  border-bottom: 1px solid var(--color-outline-light);
  border-radius: 0;
}

.comment-author-avatar {
  align-self: start;
  width: 44px;
  margin-top: 2px;
}

.comment-avatar {
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  flex-shrink: 0;
  background: var(--color-surface-container-high);
  border: 1px solid var(--color-outline-light);
  border-radius: 50%;
  color: var(--color-muted);
}

.comment-body {
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: 1;
  min-width: 0;
}

.comment-meta {
  justify-content: space-between;
  flex-wrap: wrap;
  font-family: var(--font-body);
  font-size: 12px;
  color: var(--color-muted);
  line-height: 1.4;
}

.comment-identity {
  display: flex;
  align-items: baseline;
  gap: 8px;
  flex-wrap: wrap;
  min-width: 0;
}

.comment-meta strong {
  color: var(--color-on-surface);
  font-size: 13px;
}

.reply-count {
  flex-shrink: 0;
  padding: 2px 8px;
  background: var(--color-surface-container);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  font-variant-numeric: tabular-nums;
}

.comment-content {
  max-width: 72ch;
  margin: 0;
}

.action-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  min-height: 28px;
  padding: 0;
  border: none;
  background: none;
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 12px;
  cursor: pointer;
  transition: color 0.2s ease, transform 0.2s ease;
}

.action-btn:hover {
  color: var(--color-on-surface);
}

.action-btn.danger:hover {
  color: #ef4444;
}

.edit-composer {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin: 2px 0 0;
  padding: 12px;
  background: var(--color-surface-container);
  border: 1px solid var(--color-outline-light);
  border-radius: 0;
}

.btn-cancel {
  min-height: 36px;
  padding: 0 14px;
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 13px;
  font-weight: 400;
  cursor: pointer;
  transition: background 0.2s ease, border-color 0.2s ease, transform 0.2s ease;
}

.btn-cancel:hover {
  background: var(--color-surface-container);
  border-color: var(--color-outline);
}

.replies-panel {
  margin-top: 6px;
  padding-left: 16px;
  border-left: 1px solid var(--color-outline);
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
  display: grid;
  place-items: center;
  padding: 0;
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
  padding: 42px 24px;
  border: 1px dashed var(--color-outline-light);
  border-radius: 0;
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
  font-weight: 400;
}

.empty-state p {
  font-family: var(--font-body);
  font-size: 14px;
}

.skeleton-comment {
  height: 108px;
  border-radius: 0;
}

.shimmer {
  background: linear-gradient(110deg, var(--color-surface-container-high) 8%, var(--color-surface-canvas) 18%, var(--color-surface-container-high) 33%);
  background-size: 200% 100%;
  animation: shimmer 1.4s ease-in-out infinite;
}

@keyframes shimmer {
  to {
    background-position-x: -200%;
  }
}

@media (max-width: 640px) {
  .composer-head,
  .comment-summary {
    align-items: stretch;
    flex-direction: column;
    gap: 8px;
  }

  .composer-head > span {
    max-width: none;
    text-align: left;
  }

  .comment-card {
    grid-template-columns: 38px minmax(0, 1fr);
    gap: 10px;
  }

  .comment-author-avatar {
    width: 38px;
  }

  .comment-author-avatar :deep(.user-avatar-link__avatar) {
    width: 38px;
    height: 38px;
  }

  .comment-avatar {
    width: 32px;
    height: 32px;
  }

  .comment-meta {
    align-items: flex-start;
    flex-direction: column;
    gap: 6px;
  }
}
</style>
