<template>
  <div class="course-comments">
    <form v-if="canComment" class="comment-composer" @submit.prevent="submitComment">
      <BaseTextEditor
        v-model="commentContent"
        :rows="4"
        :placeholder="t('forum.commentPlaceholder')"
      />
      <div v-if="commentImagePreviews.length > 0" class="image-preview-list">
        <figure v-for="image in commentImagePreviews" :key="image.id" class="image-preview-item">
          <img :src="image.url" :alt="image.fileName"/>
          <button type="button" @click="removeCommentImage(image.id)">x</button>
        </figure>
      </div>
      <div class="composer-footer">
        <div class="composer-actions">
          <BaseFileUploader
            usage="FORUM_IMAGE"
            scope-type="COURSE"
            :scope-id="courseId"
            accept="image/jpeg,image/png,image/webp"
            :button-label="t('forum.uploadImage')"
            :disabled="submitting"
            :prepare-file="validateImageFile"
            @uploaded="handleCommentImageUploaded"
            @error="handleUploadError"
          />
          <button class="btn-submit" :disabled="submitting || !commentContent.trim()">
            {{ t('forum.submitComment') }}
          </button>
        </div>
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
            <strong>{{ t('forum.courseMember') }}</strong>
            <span>{{ formatTime(comment.createdAt) }}</span>
            <span v-if="comment.replyCount">{{ comment.replyCount }} {{ t('forum.replies') }}</span>
          </div>

          <!-- 编辑模式 -->
          <div v-if="editingCommentId === comment.id" class="edit-composer">
            <BaseTextEditor v-model="editingContent" :rows="4"/>
            <div v-if="editingImages.length > 0" class="image-preview-list compact">
              <figure v-for="image in editingImages" :key="image.id" class="image-preview-item">
                <img :src="image.url || undefined" :alt="image.fileName"/>
                <button type="button" @click="removeEditCommentImage(image.id)">x</button>
              </figure>
            </div>
            <div class="composer-footer">
              <div class="composer-actions">
                <BaseFileUploader
                  usage="FORUM_IMAGE"
                  scope-type="COURSE"
                  :scope-id="courseId"
                  accept="image/jpeg,image/png,image/webp"
                  :button-label="t('forum.uploadImage')"
                  :disabled="savingEdit"
                  :prepare-file="validateImageFile"
                  @uploaded="handleEditCommentImageUploaded"
                  @error="handleUploadError"
                />
                <button class="btn-cancel" type="button" @click="cancelEditComment">{{ t('forum.cancelEdit') }}</button>
                <button class="btn-submit" :disabled="savingEdit || !editingContent.trim()" @click="saveEditComment(comment)">
                  {{ t('forum.saveEdit') }}
                </button>
              </div>
            </div>
          </div>

          <!-- 展示模式 -->
          <template v-else>
            <ForumContentPreview
              class="comment-content"
              :content="comment.content"
              :image-urls="comment.imageUrls"
            />
            <div class="comment-actions">
              <button class="action-btn" type="button" @click="toggleReplies(comment.id)">
                <MessageCircle :size="14"/>
                {{ repliesOpen[comment.id] ? t('forum.hideReplies') : t('forum.showReplies') }}
              </button>
              <button v-if="canComment" class="action-btn" type="button" @click="startReply(comment.id, null)">
                {{ t('forum.reply') }}
              </button>
              <button v-if="canEditComment(comment)" class="action-btn" type="button" @click="startEditComment(comment)">
                <Pencil :size="13"/>
                {{ t('forum.editComment') }}
              </button>
              <button v-if="canEditComment(comment)" class="action-btn danger" type="button" @click="handleDeleteComment(comment)">
                <Trash2 :size="13"/>
                {{ t('forum.deleteComment') }}
              </button>
            </div>
          </template>

          <div v-if="repliesOpen[comment.id]" class="replies-panel">
            <div v-if="replyLoading[comment.id]" class="reply-loading">{{ t('forum.loadingReplies') }}</div>
            <ReplyTree
              v-else
              :replies="repliesByComment[comment.id] || []"
              :show-like="false"
              :can-reply="canComment"
              :current-user-id="currentUserId"
              :can-manage="canManageCourse"
              @reply="startReply(comment.id, $event)"
              @deleted="handleReplyDeleted(comment.id)"
            />

            <form
              v-if="canComment && replyingCommentId === comment.id"
              class="reply-composer"
              @submit.prevent="submitReply(comment.id)"
            >
              <div v-if="replyingTo" class="reply-target">
                {{ t('forum.replyTo') }} {{ t('forum.courseMember') }}
                <button type="button" @click="cancelReply">x</button>
              </div>
              <BaseTextEditor
                v-model="replyContent"
                :rows="3"
                :placeholder="t('forum.replyPlaceholder')"
              />
              <div v-if="replyImagePreviews.length > 0" class="image-preview-list compact">
                <figure v-for="image in replyImagePreviews" :key="image.id" class="image-preview-item">
                  <img :src="image.url" :alt="image.fileName"/>
                  <button type="button" @click="removeReplyImage(image.id)">x</button>
                </figure>
              </div>
              <div class="composer-footer">
                <div class="composer-actions">
                  <BaseFileUploader
                    usage="FORUM_IMAGE"
                    scope-type="COURSE"
                    :scope-id="courseId"
                    accept="image/jpeg,image/png,image/webp"
                    :button-label="t('forum.uploadImage')"
                    :disabled="submittingReply"
                    :prepare-file="validateImageFile"
                    @uploaded="handleReplyImageUploaded"
                    @error="handleUploadError"
                  />
                  <button class="btn-submit" :disabled="submittingReply || !replyContent.trim()">
                    {{ t('forum.submitReply') }}
                  </button>
                </div>
              </div>
            </form>
          </div>
        </div>
      </article>
    </div>
  </div>
</template>

<script lang="ts" setup>
import {computed, onMounted, reactive, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {MessageCircle, Pencil, Trash2, User} from 'lucide-vue-next'
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
import {notify} from '@/shared/composables/useGlobalNotification'
import BaseFileUploader from '@/shared/components/BaseFileUploader.vue'
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

const loading = ref(true)
const submitting = ref(false)
const submittingReply = ref(false)
const comments = ref<ForumPost[]>([])
const commentContent = ref('')
const replyContent = ref('')
const commentImages = ref<FileAsset[]>([])
const replyImages = ref<FileAsset[]>([])
const replyingCommentId = ref<string | null>(null)
const replyingTo = ref<ForumReply | null>(null)
const repliesOpen = reactive<Record<string, boolean>>({})
const replyLoading = reactive<Record<string, boolean>>({})
const repliesByComment = reactive<Record<string, ForumReply[]>>({})

// 编辑评论状态
const editingCommentId = ref<string | null>(null)
const editingContent = ref('')
const editingImages = ref<FileAsset[]>([])
const savingEdit = ref(false)

const commentImagePreviews = computed(() => filePreviews(commentImages.value))
const replyImagePreviews = computed(() => filePreviews(replyImages.value))

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
      imageUrls: commentImages.value.map(image => image.id),
    })
    commentContent.value = ''
    commentImages.value = []
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

function handleCommentImageUploaded(asset: FileAsset) {
  commentImages.value = [...commentImages.value, asset]
}

function handleReplyImageUploaded(asset: FileAsset) {
  replyImages.value = [...replyImages.value, asset]
}

function removeCommentImage(imageId: string) {
  commentImages.value = commentImages.value.filter(image => image.id !== imageId)
}

function removeReplyImage(imageId: string) {
  replyImages.value = replyImages.value.filter(image => image.id !== imageId)
}

function filePreviews(files: FileAsset[]) {
  return files
    .map(file => ({
      id: file.id,
      fileName: file.fileName,
      url: file.url || '',
    }))
    .filter(file => file.url)
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
    const imageUrls = editingImages.value.length > 0
      ? editingImages.value.map(image => image.id)
      : comment.imageUrls
    await updatePost(comment.id, {content: editingContent.value, imageUrls})
    comment.content = editingContent.value
    if (imageUrls) comment.imageUrls = imageUrls
    cancelEditComment()
    notify.success(t('forum.commentUpdated'))
  } catch {
    notify.error(t('forum.commentFailed'))
  } finally {
    savingEdit.value = false
  }
}

async function handleDeleteComment(comment: ForumPost) {
  if (!confirm(t('forum.confirmDeleteComment'))) return
  try {
    await deletePost(comment.id)
    comments.value = comments.value.filter(item => item.id !== comment.id)
    notify.success(t('forum.commentDeleted'))
  } catch {
    notify.error(t('forum.commentFailed'))
  }
}

function handleEditCommentImageUploaded(asset: FileAsset) {
  editingImages.value = [...editingImages.value, asset]
}

function removeEditCommentImage(imageId: string) {
  editingImages.value = editingImages.value.filter(image => image.id !== imageId)
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
  align-items: flex-start;
  justify-content: flex-end;
  gap: 10px;
  flex-wrap: wrap;
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

.action-btn.danger:hover {
  color: #ef4444;
}

.edit-composer {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin: 8px 0;
}

.btn-cancel {
  padding: 8px 16px;
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}

.btn-cancel:hover {
  background: var(--color-surface-container);
  border-color: var(--color-outline);
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

.image-preview-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(96px, 136px));
  gap: 10px;
}

.image-preview-list.compact {
  grid-template-columns: repeat(auto-fill, minmax(82px, 112px));
}

.image-preview-item {
  position: relative;
  overflow: hidden;
  margin: 0;
  background: var(--color-surface-container);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
}

.image-preview-item img {
  width: 100%;
  aspect-ratio: 4 / 3;
  display: block;
  object-fit: cover;
}

.image-preview-item button {
  position: absolute;
  top: 6px;
  right: 6px;
  width: 22px;
  height: 22px;
  display: grid;
  place-items: center;
  padding: 0;
  background: color-mix(in srgb, var(--color-on-surface) 72%, transparent);
  border: 0;
  border-radius: 50%;
  color: var(--color-on-primary);
  cursor: pointer;
  font-family: var(--font-body);
  font-size: 12px;
  line-height: 1;
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
