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
        <UserAvatarLink
          class="reply-author-avatar"
          :user-id="reply.sysUserId"
          :display-name="userMap[reply.sysUserId]?.displayName"
          :avatar-url="userMap[reply.sysUserId]?.avatarUrl"
          :role="userMap[reply.sysUserId]?.role"
          size="medium"
          :show-name="false"
        />

        <div class="reply-content">
          <div class="reply-header">
            <span class="reply-author">{{ userMap[reply.sysUserId]?.displayName || '' }}</span>
            <span v-if="reply.isAccepted" class="accepted-badge">✓ {{ t('forum.accepted') }}</span>
            <span class="reply-time">{{ formatTime(reply.createdAt) }}</span>
          </div>

          <div v-if="editingReplyId === reply.id" class="edit-reply-composer">
            <textarea v-model="editingContent" class="edit-reply-textarea" rows="3"></textarea>
            <div class="edit-reply-actions">
              <button class="btn-cancel-edit" type="button" @click="cancelEdit">
                <X :size="14"/>
              </button>
              <button class="btn-save-edit" :disabled="savingEdit || !editingContent.trim()" @click="saveEdit(reply)">
                {{ t('forum.saveEdit') }}
              </button>
            </div>
          </div>

          <ForumContentPreview
            v-else
            class="reply-text"
            :content="reply.content"
            :image-urls="reply.imageUrls"
            compact
          />

          <div class="reply-actions">
            <button v-if="showLike" class="action-btn" @click="$emit('like', reply)">
              <Heart :size="14"/>
              {{ reply.likeCount }}
            </button>
            <button v-if="canReply" class="action-btn" @click="$emit('reply', reply)">
              <MessageCircle :size="14"/>
              {{ t('forum.reply') }}
            </button>
            <button v-if="canEditReply(reply)" class="action-btn" @click="startEdit(reply)">
              <Pencil :size="13"/>
              {{ t('forum.editReply') }}
            </button>
            <button v-if="canEditReply(reply)" class="action-btn danger" @click="handleDelete(reply)">
              <Trash2 :size="13"/>
              {{ t('forum.deleteReply') }}
            </button>
          </div>

          <div v-if="reply.children && reply.children.length" class="nested-replies">
            <div
              v-for="child in reply.children"
              :key="child.id"
              class="reply-item nested"
            >
              <UserAvatarLink
                class="reply-author-avatar"
                :user-id="child.sysUserId"
                :display-name="userMap[child.sysUserId]?.displayName"
                :avatar-url="userMap[child.sysUserId]?.avatarUrl"
                :role="userMap[child.sysUserId]?.role"
                size="medium"
                :show-name="false"
              />
              <div class="reply-content">
                <div class="reply-header">
                  <span class="reply-author">{{ userMap[child.sysUserId]?.displayName || '' }}</span>
                  <span class="reply-time">{{ formatTime(child.createdAt) }}</span>
                </div>
                <ForumContentPreview
                  class="reply-text"
                  :content="child.content"
                  :image-urls="child.imageUrls"
                  compact
                />
                <div class="reply-actions">
                  <button v-if="showLike" class="action-btn" @click="$emit('like', child)">
                    <Heart :size="14"/>
                    {{ child.likeCount }}
                  </button>
                  <button v-if="canEditReply(child)" class="action-btn" @click="startEdit(child)">
                    <Pencil :size="13"/>
                  </button>
                  <button v-if="canEditReply(child)" class="action-btn danger" @click="handleDelete(child)">
                    <Trash2 :size="13"/>
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
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {Heart, MessageCircle, Pencil, Trash2, X} from 'lucide-vue-next'
import {updateReply, deleteReply} from '@/features/forum/api/forum'
import type {ForumReply} from '@/features/forum/types/forum'
import type {UserBasicInfo} from '@/features/user/types/user'
import {confirmDialog} from '@/shared/composables/useConfirmDialog'
import {notify} from '@/shared/composables/useGlobalNotification'
import ForumContentPreview from '@/features/forum/components/ForumContentPreview.vue'
import UserAvatarLink from '@/shared/components/UserAvatarLink.vue'

const props = withDefaults(defineProps<{
  replies: ForumReply[]
  showLike?: boolean
  canReply?: boolean
  currentUserId?: string
  canManage?: boolean
  userMap?: Record<string, UserBasicInfo>
}>(), {
  showLike: true,
  canReply: true,
  userMap: () => ({}),
})

const emit = defineEmits<{
  like: [reply: ForumReply]
  reply: [reply: ForumReply]
  deleted: []
}>()

const {t} = useI18n()

const editingReplyId = ref<string | null>(null)
const editingContent = ref('')
const savingEdit = ref(false)

function canEditReply(reply: ForumReply): boolean {
  return Boolean(props.currentUserId && (reply.sysUserId === props.currentUserId || props.canManage))
}

function startEdit(reply: ForumReply) {
  editingReplyId.value = reply.id
  editingContent.value = reply.content
}

function cancelEdit() {
  editingReplyId.value = null
  editingContent.value = ''
}

async function saveEdit(reply: ForumReply) {
  if (!editingContent.value.trim()) return
  savingEdit.value = true
  try {
    await updateReply(reply.id, {content: editingContent.value})
    reply.content = editingContent.value
    cancelEdit()
    notify.success(t('forum.replyUpdated'))
  } catch {
    notify.error(t('forum.replyFailed'))
  } finally {
    savingEdit.value = false
  }
}

async function handleDelete(reply: ForumReply) {
  if (!(await confirmDialog({message: t('forum.confirmDeleteReply'), confirmVariant: 'danger'}))) return
  try {
    await deleteReply(reply.id)
    notify.success(t('forum.replyDeleted'))
    emit('deleted')
  } catch {
    notify.error(t('forum.replyFailed'))
  }
}

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
  gap: 0;
}

.reply-item {
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr);
  gap: 12px;
  padding: 16px 0;
  background: transparent;
  border-bottom: 1px solid var(--color-outline-light);
  border-radius: 0;
}

.reply-item:last-child {
  border-bottom: none;
}

.reply-item.accepted {
  border: 1px solid #22c55e;
  border-radius: 0;
  padding: 16px;
  margin: 4px 0;
  background: rgba(34, 197, 94, 0.04);
}

.reply-author-avatar {
  align-self: start;
  width: 44px;
  margin-top: 2px;
}

.reply-content {
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: 1;
  min-width: 0;
}

.reply-header {
  display: flex;
  align-items: baseline;
  gap: 8px;
  flex-wrap: wrap;
  min-width: 0;
  font-family: var(--font-body);
  font-size: 12px;
  color: var(--color-muted);
  line-height: 1.4;
}

.reply-author {
  font-size: 13px;
  font-weight: 700;
  color: var(--color-on-surface);
}

.accepted-badge {
  font-family: var(--font-body);
  font-size: 11px;
  font-weight: 400;
  color: #22c55e;
  background: rgba(34, 197, 94, 0.12);
  padding: 1px 6px;
  border-radius: 4px;
}

.reply-time {
  font-size: 12px;
  color: var(--color-muted);
}

.reply-text {
  max-width: 72ch;
  margin: 0;
}

.reply-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
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
  min-height: 28px;
  padding: 0;
  transition: color 0.2s ease, transform 0.2s ease;
}

.action-btn:hover {
  color: var(--color-on-surface);
}

.action-btn:active {
  transform: translateY(1px);
}

.action-btn.danger:hover {
  color: #ef4444;
}

.edit-reply-composer {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin: 6px 0;
}

.edit-reply-textarea {
  width: 100%;
  padding: 10px;
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  background: var(--color-surface-container);
  color: var(--color-on-surface);
  font-family: var(--font-body);
  font-size: 13px;
  line-height: 1.5;
  resize: vertical;
}

.edit-reply-textarea:focus {
  outline: none;
  border-color: var(--color-primary);
}

.edit-reply-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}

.btn-cancel-edit {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  padding: 0;
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-muted);
  cursor: pointer;
}

.btn-cancel-edit:hover {
  background: var(--color-surface-container);
  border-color: var(--color-outline);
}

.btn-save-edit {
  padding: 4px 12px;
  border: none;
  border-radius: var(--radius-sm);
  background: var(--color-primary);
  color: var(--color-on-primary);
  font-family: var(--font-body);
  font-size: 12px;
  font-weight: 400;
  cursor: pointer;
}

.btn-save-edit:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.nested-replies {
  margin-top: 8px;
  display: flex;
  flex-direction: column;
  border-top: 1px solid var(--color-outline-light);
}

.reply-item.nested {
  padding: 16px 0;
  border-bottom: 1px solid var(--color-outline-light);
}

.reply-item.nested:last-child {
  border-bottom: none;
}

@media (max-width: 640px) {
  .reply-item {
    grid-template-columns: 38px minmax(0, 1fr);
    gap: 10px;
  }

  .reply-author-avatar {
    width: 38px;
  }

  .reply-author-avatar :deep(.user-avatar-link__avatar) {
    width: 38px;
    height: 38px;
  }
}
</style>
