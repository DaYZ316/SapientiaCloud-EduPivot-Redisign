<template>
  <div class="notifications-page">
    <!-- Header Section -->
    <header class="page-header">
      <div class="page-header-row">
        <div>
          <h1 class="page-title">{{ t('notifications.title') }}</h1>
          <p class="page-subtitle">{{ t('notifications.subtitle') }}</p>
          <div class="header-stats">
            <span v-if="unreadCount > 0" class="stat-item stat-unread">
              <span class="stat-dot"></span>
              {{ t('notifications.statUnread', {n: unreadCount}) }}
            </span>
            <span class="stat-item">
              {{ t('notifications.statTotal', {n: totalItems}) }}
            </span>
          </div>
        </div>
        <div class="page-header-actions">
          <button
            v-if="hasNotifications && activeFilter !== 'sent'"
            class="btn-secondary btn-danger"
            type="button"
            @click="showDeleteAllConfirm = true"
          >
            <Trash2 :size="16" stroke-width="1.8"/>
            {{ t('notifications.deleteAll') }}
          </button>
          <button
            v-if="hasUnread"
            class="btn-secondary"
            type="button"
            @click="handleMarkAllRead"
          >
            <CheckCheck :size="16" stroke-width="1.8"/>
            {{ t('notifications.markAllRead') }}
          </button>
          <button
            v-if="canSendNotification"
            class="btn-primary"
            type="button"
            @click="showSendModal = true"
          >
            <Send :size="16" stroke-width="1.8"/>
            {{ t('notifications.sendNotification') }}
          </button>
        </div>
      </div>
    </header>

    <!-- Filters Section -->
    <section class="filter-section">
      <button
        v-for="tab in filterTabs"
        :key="tab.id"
        class="filter-btn"
        :class="{ active: activeFilter === tab.id }"
        @click="switchFilter(tab.id)"
      >
        {{ tab.label }}
        <span v-if="tab.count !== undefined && tab.count > 0" class="filter-count">
          {{ tab.count }}
        </span>
      </button>
    </section>

    <!-- Loading State -->
    <NotificationListSkeleton v-if="loading" :label="t('notifications.loading')"/>

    <!-- Master-Detail Body -->
    <div v-else class="notifications-body" :class="{ 'detail-open': selectedNotification !== null }">

      <!-- Left: List Panel -->
      <div class="list-panel">
        <!-- Empty State -->
        <div v-if="displayNotifications.length === 0" class="empty-state">
          <BellOff :size="48" stroke-width="1.2" class="empty-icon"/>
          <h3>{{ t('notifications.empty') }}</h3>
        </div>

        <!-- Notification List -->
        <div v-else class="notification-list">
          <div
            v-for="notification in displayNotifications"
            :key="notification.id"
            class="notification-card"
            :class="{
              unread: !notification.isRead,
              read: notification.isRead,
              active: selectedNotification?.id === notification.id,
            }"
            @click="handleNotificationClick(notification)"
          >
            <!-- Icon -->
            <div class="card-icon-wrapper">
              <component :is="getNotificationIcon(notification.type)" :size="24" stroke-width="1.8" class="card-icon"/>
            </div>

            <!-- Content -->
            <div class="card-content">
              <div class="card-header">
                <h3 class="card-title">{{ notification.title }}</h3>
                <div class="card-meta-desktop">
                  <span class="card-time">{{ notification.time }}</span>
                  <div v-if="!notification.isRead" class="unread-dot"></div>
                </div>
              </div>
              <p class="card-description">{{ notification.content }}</p>
              <span class="card-tag" :class="notification.type">
                {{ getTypeName(notification.type) }}
              </span>
              <div class="card-meta-mobile">
                <span class="card-time">{{ notification.time }}</span>
              </div>
            </div>

            <!-- Mobile unread dot -->
            <div v-if="!notification.isRead" class="unread-dot-mobile"></div>

            <!-- Recall button for own notifications or admin -->
            <button
              v-if="isAdmin || notification.senderId === authStore.user?.id"
              class="btn-card-recall"
              type="button"
              :aria-label="t('notifications.recall')"
              @click.stop="handleRecallFromCard(notification)"
            >
              <RotateCcw :size="14" stroke-width="1.8"/>
            </button>
          </div>
        </div>

        <!-- Pagination -->
        <nav v-if="displayNotifications.length > 0 && totalPages > 1" class="pagination">
          <button class="page-btn nav-btn" :disabled="currentPage === 1" @click="changePage(currentPage - 1)">
            <ChevronLeft :size="18" stroke-width="2"/>
          </button>
          <div class="page-numbers">
            <button
              v-for="page in displayedPages"
              :key="page"
              class="page-btn"
              :class="{ active: currentPage === page }"
              @click="changePage(page)"
            >
              {{ page }}
            </button>
          </div>
          <button class="page-btn nav-btn" :disabled="currentPage === totalPages" @click="changePage(currentPage + 1)">
            <ChevronRight :size="18" stroke-width="2"/>
          </button>
        </nav>
      </div>

      <!-- Right: Detail Panel -->
      <Transition name="detail-slide">
        <article
          v-if="selectedNotification"
          class="detail-panel"
          role="region"
          :aria-labelledby="`detail-title-${selectedNotification.id}`"
        >
          <div class="detail-header">
            <div class="detail-icon-wrapper" :class="selectedNotification.type">
              <component :is="getNotificationIcon(selectedNotification.type)" :size="20" stroke-width="1.8"/>
            </div>
            <div class="detail-header-text">
              <span class="card-tag" :class="selectedNotification.type">
                {{ getTypeName(selectedNotification.type) }}
              </span>
              <span class="card-time">{{ selectedNotification.time }}</span>
            </div>
            <button
              v-if="isAdmin || selectedNotification.senderId === authStore.user?.id"
              class="btn-detail-recall"
              type="button"
              :aria-label="t('notifications.recall')"
              @click="handleRecallNotification"
            >
              <RotateCcw :size="16" stroke-width="1.8"/>
            </button>
            <button
              v-else
              class="btn-detail-delete"
              type="button"
              :aria-label="t('notifications.delete')"
              @click="handleDeleteNotification"
            >
              <Trash2 :size="16" stroke-width="1.8"/>
            </button>
            <button class="btn-close" type="button" :aria-label="t('notifications.modal.cancel')" @click="closeDetail">
              <X :size="20"/>
            </button>
          </div>
          <div class="detail-divider"></div>
          <div class="detail-body">
            <h2 :id="`detail-title-${selectedNotification.id}`" class="detail-title">
              {{ selectedNotification.title }}
            </h2>
            <NotificationContentPreview :content="selectedNotification.content" variant="plain"/>
          </div>
        </article>
      </Transition>
    </div>

    <!-- Mark All Read Confirm Modal -->
    <Teleport to="body">
      <div v-if="showMarkAllReadConfirm" class="modal-overlay">
        <div class="modal confirm-modal">
          <div class="modal-header">
            <h2>{{ t('notifications.markAllReadConfirmTitle') }}</h2>
            <button class="btn-close" type="button" @click="closeMarkAllReadConfirm">
              <X :size="20"/>
            </button>
          </div>
          <div class="modal-body">
            <p class="confirm-message">{{ t('notifications.markAllReadConfirmMessage') }}</p>
            <div class="modal-actions">
              <button class="btn-secondary" type="button" @click="closeMarkAllReadConfirm">
                {{ t('notifications.modal.cancel') }}
              </button>
              <button class="btn-primary" type="button" @click="confirmMarkAllRead">
                {{ t('notifications.markAllRead') }}
              </button>
            </div>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- Delete All Confirm Modal -->
    <Teleport to="body">
      <div v-if="showDeleteAllConfirm" class="modal-overlay">
        <div class="modal confirm-modal">
          <div class="modal-header">
            <h2>{{ t('notifications.deleteAllConfirmTitle') }}</h2>
            <button class="btn-close" type="button" @click="closeDeleteAllConfirm">
              <X :size="20"/>
            </button>
          </div>
          <div class="modal-body">
            <p class="confirm-message">{{ t('notifications.deleteAllConfirmMessage') }}</p>
            <div class="modal-actions">
              <button class="btn-secondary" type="button" @click="closeDeleteAllConfirm">
                {{ t('notifications.modal.cancel') }}
              </button>
              <button class="btn-primary btn-danger" type="button" @click="confirmDeleteAll">
                {{ t('notifications.deleteAll') }}
              </button>
            </div>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- Delete Single Confirm Modal -->
    <Teleport to="body">
      <div v-if="showDeleteConfirm" class="modal-overlay">
        <div class="modal confirm-modal">
          <div class="modal-header">
            <h2>{{ t('notifications.deleteConfirmTitle') }}</h2>
            <button class="btn-close" type="button" @click="closeDeleteConfirm">
              <X :size="20"/>
            </button>
          </div>
          <div class="modal-body">
            <p class="confirm-message">{{ t('notifications.deleteConfirmMessage') }}</p>
            <div class="modal-actions">
              <button class="btn-secondary" type="button" @click="closeDeleteConfirm">
                {{ t('notifications.modal.cancel') }}
              </button>
              <button class="btn-primary btn-danger" type="button" @click="confirmDeleteNotification">
                {{ t('notifications.delete') }}
              </button>
            </div>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- Recall Confirm Modal -->
    <Teleport to="body">
      <div v-if="showRecallConfirm" class="modal-overlay">
        <div class="modal confirm-modal">
          <div class="modal-header">
            <h2>{{ t('notifications.recallConfirmTitle') }}</h2>
            <button class="btn-close" type="button" @click="closeRecallConfirm">
              <X :size="20"/>
            </button>
          </div>
          <div class="modal-body">
            <p class="confirm-message">{{ t('notifications.recallConfirmMessage') }}</p>
            <div class="modal-actions">
              <button class="btn-secondary" type="button" @click="closeRecallConfirm">
                {{ t('notifications.modal.cancel') }}
              </button>
              <button class="btn-primary btn-danger" type="button" @click="confirmRecallNotification">
                {{ t('notifications.recall') }}
              </button>
            </div>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- Send Notification Modal -->
    <Teleport to="body">
      <div v-if="showSendModal" class="modal-overlay">
        <div class="modal">
          <div class="modal-header">
            <h2>{{ t('notifications.sendNotification') }}</h2>
            <button class="btn-close" @click="closeSendModal">
              <X :size="20"/>
            </button>
          </div>
          <form class="modal-body" @submit.prevent="handleSendNotification">
            <div class="form-group">
              <label>{{ t('notifications.modal.typeLabel') }}</label>
              <div class="radio-group">
                <label class="radio-option">
                  <input v-model="sendForm.type" type="radio" :value="1"/>
                  <span>{{ t('notifications.filterSystem') }}</span>
                </label>
                <label class="radio-option">
                  <input v-model="sendForm.type" type="radio" :value="2"/>
                  <span>{{ t('notifications.filterTeaching') }}</span>
                </label>
              </div>
            </div>
            <div class="form-group">
              <label>{{ t('notifications.modal.targetTypeLabel') }}</label>
              <div class="radio-group">
                <label class="radio-option">
                  <input v-model="sendForm.targetType" type="radio" :value="0"/>
                  <span>{{ t('notifications.modal.targetTypeAll') }}</span>
                </label>
                <label class="radio-option">
                  <input v-model="sendForm.targetType" type="radio" :value="1" @change="loadUsers"/>
                  <span>{{ t('notifications.modal.targetTypeUser') }}</span>
                </label>
              </div>
            </div>

            <!-- User Selection Panel -->
            <div v-if="sendForm.targetType === 1" class="user-selection-panel">
              <div class="user-selection-header">
                <label>{{ t('notifications.modal.selectUsers') }}</label>
                <span v-if="selectedUserCount > 0" class="selected-count">
                  {{ t('notifications.modal.selectedCount', {n: selectedUserCount}) }}
                </span>
              </div>

              <!-- Quick Actions -->
              <div class="quick-actions">
                <button type="button" class="quick-btn" @click="selectAll">
                  {{ t('notifications.modal.selectAll') }}
                </button>
                <button type="button" class="quick-btn" @click="selectAllStudents">
                  {{ t('notifications.modal.selectAllStudents') }}
                </button>
                <button type="button" class="quick-btn" @click="selectAllTeachers">
                  {{ t('notifications.modal.selectAllTeachers') }}
                </button>
                <button v-if="selectedUserCount > 0" type="button" class="quick-btn clear" @click="clearSelection">
                  {{ t('notifications.modal.cancel') }}
                </button>
              </div>

              <!-- Search -->
              <div class="user-search">
                <input
                  v-model="userSearchQuery"
                  class="input-field"
                  type="text"
                  :placeholder="t('notifications.modal.searchUsers')"
                />
              </div>

              <!-- User List -->
              <div class="user-list">
                <div v-if="loadingUsers" class="user-loading">
                  <div class="loading-spinner"></div>
                </div>
                <div v-else-if="filteredUsers.length === 0" class="user-empty">
                  {{ t('notifications.modal.noUsersFound') }}
                </div>
                <label
                  v-for="user in filteredUsers"
                  :key="user.id"
                  class="user-item"
                  :class="{selected: selectedUserIds.has(user.id)}"
                >
                  <input
                    type="checkbox"
                    :checked="selectedUserIds.has(user.id)"
                    @change="toggleUser(user.id)"
                  />
                  <div class="user-info">
                    <span class="user-name">{{ user.displayName || user.email }}</span>
                    <span class="user-role" :class="`role-${user.role}`">{{ getRoleName(user.role) }}</span>
                  </div>
                </label>
              </div>
            </div>
            <div class="form-group">
              <label>{{ t('notifications.modal.titleLabel') }}</label>
              <input
                v-model="sendForm.title"
                class="input-field"
                type="text"
                :placeholder="t('notifications.modal.titlePlaceholder')"
                required
              />
            </div>
            <div class="form-group">
              <label for="notification-content">{{ t('notifications.modal.contentLabel') }}</label>
              <NotificationContentEditor v-model="sendForm.content"/>
            </div>
            <div class="modal-actions">
              <button class="btn-secondary" type="button" @click="closeSendModal">
                {{ t('notifications.modal.cancel') }}
              </button>
              <button class="btn-primary" type="submit" :disabled="sending">
                {{ sending ? '...' : t('notifications.modal.submit') }}
              </button>
            </div>
          </form>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script lang="ts" setup>
import {computed, onMounted, onUnmounted, reactive, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {
  Bell, BellOff, CheckCheck, ChevronLeft, ChevronRight,
  FileWarning, Megaphone, RotateCcw, Send, Trash2, X,
} from 'lucide-vue-next'
import {
  deleteAllNotifications as apiDeleteAllNotifications,
  deleteNotification as apiDeleteNotification,
  getNotifications,
  markAllAsRead as apiMarkAllAsRead,
  markAsRead as apiMarkAsRead,
  recallNotification as apiRecallNotification,
  sendNotification as apiSendNotification,
  subscribeNotifications,
} from '@/features/notification/api/notification'
import type {
  Notification as ApiNotification,
  NotificationSubscription,
} from '@/features/notification/api/notification'
import {pageUsers} from '@/features/user/api/user'
import type {UserProfile} from '@/features/user/types/user'
import {useAuthStore} from '@/features/auth/stores/auth'
import {useUnreadCount} from '@/shared/composables/useUnreadCount'
import {notify} from '@/shared/composables/useGlobalNotification'
import NotificationContentEditor from '@/features/notification/components/NotificationContentEditor.vue'
import NotificationContentPreview from '@/features/notification/components/NotificationContentPreview.vue'
import NotificationListSkeleton from '@/features/notification/components/NotificationListSkeleton.vue'

const {t} = useI18n()
const authStore = useAuthStore()
const {unreadCount, decrement, load, reset} = useUnreadCount()

const canSendNotification = computed(() => {
  const role = authStore.user?.role
  return role === 0 || role === 2
})

const isAdmin = computed(() => authStore.user?.role === 0)

// --- Notification item model ---

interface NotificationItem {
  id: string
  type: string
  title: string
  content: string
  time: string
  isRead: boolean
  senderId: string | null
}

const notificationIcons: Record<string, typeof Bell> = {
  teaching: FileWarning,
  system: Megaphone,
}

function getNotificationIcon(type: string) {
  return notificationIcons[type] ?? Bell
}

function getNotificationType(type: number): string {
  switch (type) {
    case 1: return 'system'
    case 2: return 'teaching'
    default: return 'system'
  }
}

function getTypeName(type: string): string {
  switch (type) {
    case 'system': return t('notifications.filterSystem')
    case 'teaching': return t('notifications.filterTeaching')
    default: return t('notifications.filterOther')
  }
}

function formatTime(dateStr: string): string {
  const date = new Date(dateStr)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  const minutes = Math.floor(diff / 60000)
  const hours = Math.floor(diff / 3600000)
  const days = Math.floor(diff / 86400000)

  if (minutes < 1) return t('notifications.justNow')
  if (minutes < 60) return t('notifications.minAgo', {n: minutes})
  if (hours < 24) return t('notifications.hoursAgo', {n: hours})
  if (days === 1) return t('notifications.yesterday')
  if (days < 7) return t('notifications.daysAgo', {n: days})
  return date.toLocaleDateString('en-US', {month: 'short', day: 'numeric'})
}

function mapNotification(n: ApiNotification): NotificationItem {
  return {
    id: n.id,
    type: getNotificationType(n.type),
    title: n.title,
    content: n.content,
    time: formatTime(n.createdAt),
    isRead: n.isRead,
    senderId: n.senderId,
  }
}

// --- List state ---

const activeFilter = ref('all')
const currentPage = ref(1)
const pageSize = ref(10)
const totalItems = ref(0)
const loading = ref(false)
const notifications = ref<NotificationItem[]>([])
const selectedNotification = ref<NotificationItem | null>(null)

const filterTabs = computed(() => [
  {id: 'all', label: t('notifications.filterAll')},
  {id: 'unread', label: t('notifications.filterUnread'), count: unreadCount.value},
  {id: 'sent', label: t('notifications.filterSent')},
  {id: 'system', label: t('notifications.filterSystem')},
  {id: 'teaching', label: t('notifications.filterTeaching')},
])

const hasUnread = computed(() => notifications.value.some(n => !n.isRead))
const hasNotifications = computed(() => totalItems.value > 0)

const displayNotifications = computed(() => {
  if (activeFilter.value === 'all') return notifications.value
  if (activeFilter.value === 'unread') return notifications.value.filter(n => !n.isRead)
  return notifications.value
})

const totalPages = computed(() => Math.max(1, Math.ceil(totalItems.value / pageSize.value)))

const displayedPages = computed(() => {
  const pages: number[] = []
  const maxDisplay = 5
  let start = Math.max(1, currentPage.value - Math.floor(maxDisplay / 2))
  let end = Math.min(totalPages.value, start + maxDisplay - 1)

  if (end - start + 1 < maxDisplay) {
    start = Math.max(1, end - maxDisplay + 1)
  }

  for (let i = start; i <= end; i++) {
    pages.push(i)
  }
  return pages
})

// --- Data loading ---

async function loadNotifications() {
  loading.value = true
  try {
    let type: number | undefined
    if (activeFilter.value === 'system') type = 1
    if (activeFilter.value === 'teaching') type = 2
    const sentByMe = activeFilter.value === 'sent'

    const response = await getNotifications(currentPage.value, pageSize.value, type, sentByMe)
    notifications.value = response.records.map(mapNotification)
    totalItems.value = response.total
  } catch {
    notifications.value = []
    totalItems.value = 0
  } finally {
    loading.value = false
  }
}

function switchFilter(filter: string) {
  activeFilter.value = filter
  currentPage.value = 1
  loadNotifications()
}

async function changePage(page: number) {
  if (page < 1 || page > totalPages.value) return
  currentPage.value = page
  await loadNotifications()
}

// --- Mark as read ---

const showMarkAllReadConfirm = ref(false)

function closeMarkAllReadConfirm() {
  showMarkAllReadConfirm.value = false
}

async function confirmMarkAllRead() {
  showMarkAllReadConfirm.value = false
  try {
    let type: number | undefined
    if (activeFilter.value === 'system') type = 1
    if (activeFilter.value === 'teaching') type = 2

    await apiMarkAllAsRead(type)
    reset()
    notify.success(t('notifications.markAllReadSuccess'))
    await loadNotifications()
  } catch {
    notify.error(t('notifications.markAllReadFailed'))
  }
}

async function handleNotificationClick(notification: NotificationItem) {
  selectedNotification.value = notification

  if (!notification.isRead) {
    notification.isRead = true
    decrement()
    try {
      await apiMarkAsRead(notification.id)
    } catch {
      notification.isRead = false
      decrement(-1)
      notify.error(t('notifications.markAllReadFailed'))
    }
  }
}

function closeDetail() {
  selectedNotification.value = null
}

async function handleMarkAllRead() {
  showMarkAllReadConfirm.value = true
}

// --- Delete ---

const showDeleteAllConfirm = ref(false)

function closeDeleteAllConfirm() {
  showDeleteAllConfirm.value = false
}

async function confirmDeleteAll() {
  showDeleteAllConfirm.value = false
  try {
    let type: number | undefined
    if (activeFilter.value === 'system') type = 1
    if (activeFilter.value === 'teaching') type = 2

    await apiDeleteAllNotifications(type)
    // 重新加载以获取准确的未读数
    await loadNotifications()
    await load()
    notify.success(t('notifications.deleteAllSuccess'))
  } catch {
    notify.error(t('notifications.deleteAllFailed'))
  }
}

// --- Delete single notification ---

const showDeleteConfirm = ref(false)

function closeDeleteConfirm() {
  showDeleteConfirm.value = false
}

function handleDeleteNotification() {
  showDeleteConfirm.value = true
}

async function confirmDeleteNotification() {
  if (!selectedNotification.value) return
  showDeleteConfirm.value = false
  const target = selectedNotification.value
  try {
    await apiDeleteNotification(target.id)
    if (!target.isRead) decrement()
    selectedNotification.value = null
    notify.success(t('notifications.deleteSuccess'))
    await loadNotifications()
  } catch {
    notify.error(t('notifications.deleteFailed'))
  }
}

// --- Recall notification (sent by me) ---

const showRecallConfirm = ref(false)
const recallTarget = ref<NotificationItem | null>(null)

function closeRecallConfirm() {
  showRecallConfirm.value = false
  recallTarget.value = null
}

function handleRecallNotification() {
  if (!selectedNotification.value) return
  recallTarget.value = selectedNotification.value
  showRecallConfirm.value = true
}

async function confirmRecallNotification() {
  const target = recallTarget.value
  if (!target) return
  showRecallConfirm.value = false
  try {
    await apiRecallNotification(target.id)
    if (selectedNotification.value?.id === target.id) {
      selectedNotification.value = null
    }
    recallTarget.value = null
    notify.success(t('notifications.recallSuccess'))
    await loadNotifications()
  } catch {
    recallTarget.value = null
    notify.error(t('notifications.recallFailed'))
  }
}

function handleRecallFromCard(notification: NotificationItem) {
  recallTarget.value = notification
  showRecallConfirm.value = true
}

// --- SSE real-time ---

let eventSource: NotificationSubscription | null = null

function handleSseNotification(apiNotification: ApiNotification) {
  const item = mapNotification(apiNotification)
  if (currentPage.value === 1 && (activeFilter.value === 'all' || activeFilter.value === item.type)) {
    notifications.value.unshift(item)
    if (notifications.value.length > pageSize.value) {
      notifications.value.pop()
    }
    totalItems.value++
  }
}

onMounted(() => {
  loadNotifications()
  eventSource = subscribeNotifications(handleSseNotification)
})

onUnmounted(() => {
  eventSource?.close()
})

// --- Send notification modal ---

const showSendModal = ref(false)
const sending = ref(false)
const sendForm = reactive({
  type: 1,
  title: '',
  content: '',
  targetType: 0,
})

// User selection state
const availableUsers = ref<UserProfile[]>([])
const selectedUserIds = ref<Set<string>>(new Set())
const userSearchQuery = ref('')
const loadingUsers = ref(false)

const filteredUsers = computed(() => {
  if (!userSearchQuery.value.trim()) return availableUsers.value
  const query = userSearchQuery.value.toLowerCase()
  return availableUsers.value.filter(user =>
    user.displayName?.toLowerCase().includes(query) ||
    user.email?.toLowerCase().includes(query)
  )
})

const selectedUserCount = computed(() => selectedUserIds.value.size)

function getRoleName(role: number | null): string {
  switch (role) {
    case 0: return t('notifications.modal.roleAdmin')
    case 1: return t('notifications.modal.roleStudent')
    case 2: return t('notifications.modal.roleTeacher')
    default: return '-'
  }
}

async function loadUsers() {
  loadingUsers.value = true
  try {
    const response = await pageUsers({page: 1, size: 100})
    availableUsers.value = response.records
  } catch {
    availableUsers.value = []
  } finally {
    loadingUsers.value = false
  }
}

function toggleUser(userId: string) {
  if (selectedUserIds.value.has(userId)) {
    selectedUserIds.value.delete(userId)
  } else {
    selectedUserIds.value.add(userId)
  }
  // Trigger reactivity
  selectedUserIds.value = new Set(selectedUserIds.value)
}

function selectAll() {
  filteredUsers.value.forEach(user => selectedUserIds.value.add(user.id))
  selectedUserIds.value = new Set(selectedUserIds.value)
}

function selectAllStudents() {
  filteredUsers.value.filter(u => u.role === 1).forEach(user => selectedUserIds.value.add(user.id))
  selectedUserIds.value = new Set(selectedUserIds.value)
}

function selectAllTeachers() {
  filteredUsers.value.filter(u => u.role === 2).forEach(user => selectedUserIds.value.add(user.id))
  selectedUserIds.value = new Set(selectedUserIds.value)
}

function clearSelection() {
  selectedUserIds.value = new Set()
}

function closeSendModal() {
  showSendModal.value = false
  sendForm.type = 1
  sendForm.title = ''
  sendForm.content = ''
  sendForm.targetType = 0
  selectedUserIds.value = new Set()
  userSearchQuery.value = ''
}

async function handleSendNotification() {
  if (!sendForm.title.trim() || !sendForm.content.trim()) return
  if (sendForm.targetType === 1 && selectedUserIds.value.size === 0) return

  sending.value = true
  try {
    await apiSendNotification({
      type: sendForm.type,
      title: sendForm.title.trim(),
      content: sendForm.content.trim(),
      targetType: sendForm.targetType,
      userIds: sendForm.targetType === 1 ? Array.from(selectedUserIds.value) : undefined,
    })
    notify.success(t('notifications.notificationSent'))
    closeSendModal()
    await loadNotifications()
  } catch {
    notify.error(t('notifications.sendFailed'))
  } finally {
    sending.value = false
  }
}
</script>

<style scoped>
/* ========================================
   Monolith Academic - Notification Center
   ======================================== */

.notifications-page {
  max-width: 100%;
}

/* ---- Page Header ---- */

.page-header {
  margin-bottom: 64px;
}

.page-header-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 24px;
  flex-wrap: wrap;
}

.page-title {
  margin: 0 0 24px;
  font-family: 'Bodoni Moda', serif;
  font-size: 48px;
  font-weight: 600;
  line-height: 1.2;
  color: var(--color-on-surface);
  letter-spacing: -0.02em;
}

.page-subtitle {
  margin: 0;
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 18px;
  font-weight: 400;
  line-height: 1.6;
  color: var(--color-secondary);
  max-width: 672px;
}

.page-header-actions {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-shrink: 0;
}

.header-stats {
  display: flex;
  align-items: center;
  gap: 20px;
  margin-top: 16px;
}

.stat-item {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 13px;
  font-weight: 500;
  color: var(--color-secondary);
}

.stat-unread {
  color: var(--color-primary);
  font-weight: 600;
}

.stat-dot {
  width: 8px;
  height: 8px;
  background: var(--color-primary);
  border-radius: 50%;
  animation: pulse 2s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

/* ---- Filter Section ---- */

.filter-section {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 32px;
}

.filter-btn {
  padding: 12px 32px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--color-primary);
  background: transparent;
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.1em;
  color: var(--color-primary);
  cursor: pointer;
  transition: all 0.2s;
}

.filter-btn:hover {
  background: var(--color-surface-container);
}

.filter-btn:active {
  transform: scale(0.95);
}

.filter-btn.active {
  background: var(--color-primary);
  color: var(--color-on-primary);
  border-color: var(--color-primary);
}

.filter-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  border-radius: 10px;
  background: var(--color-primary-container);
  color: var(--color-primary);
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 11px;
  font-weight: 700;
  margin-left: 4px;
}

.filter-btn.active .filter-count {
  background: rgba(255, 255, 255, 0.25);
  color: var(--color-on-primary);
}

/* ---- Loading State ---- */

.loading-spinner {
  width: 32px;
  height: 32px;
  border: 3px solid var(--color-outline-variant);
  border-top-color: var(--color-primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* ---- Master-Detail Layout ---- */

.notifications-body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 0fr);
  align-items: start;
  gap: 0;
  transition: grid-template-columns 0.3s ease, gap 0.3s ease;
}

.notifications-body.detail-open {
  grid-template-columns: minmax(0, 2fr) minmax(0, 3fr);
  gap: 24px;
}

.list-panel {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 20px;
  overflow-y: auto;
  max-height: calc(100vh - 252px);
}

/* ---- Notification List ---- */

.notification-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.notifications-body.detail-open .notification-list {
  gap: 12px;
}

/* ---- Notification Card ---- */

.notification-card {
  display: flex;
  align-items: flex-start;
  gap: 20px;
  padding: 24px;
  border-width: 1px;
  border-style: solid;
  border-radius: 20px;
  cursor: pointer;
  transition: all 0.3s;
  position: relative;
}

.notifications-body.detail-open .notification-card {
  gap: 16px;
  padding: 20px;
  border-radius: 18px;
}

.notification-card.unread {
  border-color: var(--color-primary);
}

.notification-card.read {
  border-color: var(--color-outline-variant);
}

.notification-card.active {
  background-color: var(--color-primary);
  color: var(--color-on-primary);
  border-color: var(--color-primary);
}

.notification-card.active .card-icon-wrapper {
  background: var(--color-on-primary);
}

.notification-card.active .card-description {
  color: var(--color-surface-container-high);
}

.notification-card.active .card-time {
  color: var(--color-surface-container-high);
}

.notification-card.active .unread-dot,
.notification-card.active .unread-dot-mobile {
  background-color: var(--color-on-primary);
}

.notification-card.active .card-tag.system {
  background-color: var(--color-on-primary);
  color: var(--color-primary);
}

.notification-card.active .card-tag.teaching {
  background-color: var(--color-on-primary);
  color: var(--color-primary);
}

/* Hover Effect - Reverse colors */
.notification-card:hover {
  background-color: var(--color-primary);
  color: var(--color-on-primary);
  border-color: var(--color-primary);
}

.notification-card:hover .card-icon {
  color: var(--color-primary);
}

.notification-card:hover .card-description {
  color: var(--color-surface-container-high);
}

.notification-card:hover .card-time {
  color: var(--color-surface-container-high);
}

.notification-card:hover .unread-dot,
.notification-card:hover .unread-dot-mobile {
  background-color: var(--color-on-primary);
}

.notification-card:hover .card-tag.system {
  background-color: var(--color-on-primary);
  color: var(--color-primary);
}

.notification-card:hover .card-tag.teaching {
  background-color: var(--color-on-primary);
  color: var(--color-primary);
}

/* ---- Card Icon ---- */

.card-icon-wrapper {
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-surface-container);
  border-radius: var(--radius-sm);
  flex-shrink: 0;
  transition: background-color 0.3s;
}

.notifications-body.detail-open .card-icon-wrapper {
  width: 44px;
  height: 44px;
}

.notification-card:hover .card-icon-wrapper {
  background: var(--color-on-primary);
}

.card-icon {
  color: var(--color-on-surface);
  transition: color 0.3s;
}

/* ---- Card Content ---- */

.card-content {
  flex: 1;
  min-width: 0;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 8px;
}

.card-title {
  margin: 0;
  font-family: 'Bodoni Moda', serif;
  font-size: 20px;
  font-weight: 700;
  line-height: 1.3;
  color: inherit;
}

.notifications-body.detail-open .card-title {
  font-size: 18px;
}

.card-meta-desktop {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-shrink: 0;
}

.card-time {
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.1em;
  color: var(--color-secondary);
  transition: color 0.3s;
}

.unread-dot {
  width: 8px;
  height: 8px;
  background: var(--color-primary);
  border-radius: var(--radius-sm);
  transition: background-color 0.3s;
}

.card-description {
  margin: 0 0 12px;
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 14px;
  font-weight: 400;
  line-height: 1.5;
  color: var(--color-secondary);
  transition: color 0.3s;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-tag {
  display: inline-block;
  padding: 6px 16px;
  border-radius: var(--radius-sm);
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.1em;
}

.card-tag.system {
  background: var(--color-primary);
  color: var(--color-on-primary);
}

.card-tag.teaching {
  background: var(--color-secondary);
  color: var(--color-on-primary);
}

.card-meta-mobile {
  display: none;
  margin-top: 16px;
}

.unread-dot-mobile {
  display: none;
  position: absolute;
  top: 32px;
  right: 32px;
  width: 8px;
  height: 8px;
  background: var(--color-primary);
  border-radius: var(--radius-sm);
  transition: background-color 0.3s;
}

/* ---- Danger Button ---- */

.btn-danger {
  padding: 14px 28px;
  background: var(--color-error);
  color: #fff;
  border: none;
}

.btn-danger:hover:not(:disabled) {
  background: var(--color-on-surface);
  color: var(--color-on-primary);
}

/* ---- Empty State ---- */

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 120px 32px;
}

.empty-icon {
  color: var(--color-outline-variant);
  margin-bottom: 24px;
  opacity: 0.6;
}

.empty-state h3 {
  margin: 0 0 8px;
  font-family: 'Bodoni Moda', serif;
  font-size: 22px;
  font-weight: 600;
  color: var(--color-on-surface);
}

.empty-state p {
  margin: 0;
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 14px;
  color: var(--color-secondary);
  max-width: 280px;
}

/* ---- Pagination ---- */

.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  margin-top: 64px;
}

.page-numbers {
  display: flex;
  gap: 8px;
}

.page-btn {
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-sm);
  border: 1px solid var(--color-outline-variant);
  background: transparent;
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.1em;
  color: var(--color-on-surface);
  cursor: pointer;
  transition: all 0.2s;
}

.page-btn:hover:not(:disabled) {
  background: var(--color-primary);
  color: var(--color-on-primary);
  border-color: var(--color-primary);
}

.page-btn.active {
  background: var(--color-primary);
  color: var(--color-on-primary);
  border-color: var(--color-primary);
}

.page-btn:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}

.nav-btn {
  border-color: var(--color-outline-variant);
}

/* ---- Detail Panel ---- */

.detail-panel {
  min-width: 0;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-variant);
  border-radius: 24px;
  overflow-x: hidden;
  overflow-y: auto;
  min-height: clamp(480px, 62vh, 620px);
  max-height: calc(100vh - 252px);
}

.detail-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 28px 28px 0;
}

.detail-icon-wrapper {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  flex-shrink: 0;
  background: var(--color-surface-container);
  color: var(--color-on-surface);
}

.detail-icon-wrapper.system {
  background: var(--color-primary-container);
  color: var(--color-primary);
}

.detail-icon-wrapper.teaching {
  background: var(--color-secondary-container, var(--color-surface-container-high));
  color: var(--color-secondary);
}

.detail-header-text {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  flex: 1;
}

.btn-detail-delete {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--color-outline-variant);
  background: transparent;
  color: var(--color-secondary);
  cursor: pointer;
  transition: all 0.2s;
  flex-shrink: 0;
}

.btn-detail-delete:hover {
  background: var(--color-error);
  border-color: var(--color-error);
  color: #fff;
}

.btn-detail-recall {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--color-outline-variant);
  background: transparent;
  color: var(--color-secondary);
  cursor: pointer;
  transition: all 0.2s;
  flex-shrink: 0;
}

.btn-detail-recall:hover {
  background: var(--color-error);
  border-color: var(--color-error);
  color: #fff;
}

/* ---- Card Recall Button (sent view) ---- */

.btn-card-recall {
  position: absolute;
  bottom: 16px;
  right: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--color-outline-variant);
  background: var(--color-surface);
  color: var(--color-secondary);
  cursor: pointer;
  opacity: 0;
  transition: all 0.2s;
  z-index: 1;
}

.notification-card:hover .btn-card-recall {
  opacity: 1;
}

.btn-card-recall:hover {
  background: var(--color-error);
  border-color: var(--color-error);
  color: #fff;
}

.detail-divider {
  height: 1px;
  background: var(--color-outline-variant);
  margin: 18px 28px 0;
}

.detail-body {
  padding: 24px 32px 36px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-width: 720px;
}

.detail-title {
  margin: 0;
  font-family: 'Bodoni Moda', serif;
  font-size: 28px;
  font-weight: 600;
  line-height: 1.3;
  color: var(--color-on-surface);
  overflow-wrap: anywhere;
}

.detail-content {
  margin: 0;
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 16px;
  line-height: 1.7;
  color: var(--color-secondary);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

/* ---- Detail Transition ---- */

.detail-slide-enter-active {
  animation: detail-in 0.25s ease-out;
}

.detail-slide-leave-active {
  animation: detail-in 0.2s ease-in reverse;
}

@keyframes detail-in {
  from {
    opacity: 0;
    transform: translateX(16px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

/* ---- Confirm Modal ---- */

.confirm-modal {
  max-width: 440px;
}

.confirm-message {
  margin: 0 0 24px;
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 16px;
  line-height: 1.6;
  color: var(--color-secondary);
}

/* ---- User Selection Panel ---- */

.user-selection-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 20px;
  background: var(--color-surface);
  border-radius: 16px;
  border: 1px solid var(--color-outline-variant);
}

.user-selection-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.user-selection-header label {
  margin: 0;
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--color-secondary);
}

.selected-count {
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 12px;
  font-weight: 600;
  color: var(--color-primary);
  letter-spacing: 0.05em;
  padding: 4px 10px;
  background: var(--color-primary-container);
  border-radius: 100px;
}

.quick-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.quick-btn {
  padding: 8px 16px;
  border: 1px solid var(--color-outline-variant);
  border-radius: 100px;
  background: var(--color-surface);
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 12px;
  font-weight: 500;
  color: var(--color-on-surface);
  cursor: pointer;
  transition: all 0.2s ease;
}

.quick-btn:hover {
  background: var(--color-primary);
  color: var(--color-on-primary);
  border-color: var(--color-primary);
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.quick-btn:active {
  transform: translateY(0);
}

.quick-btn.clear {
  border-color: var(--color-outline);
  color: var(--color-secondary);
}

.quick-btn.clear:hover {
  background: var(--color-error);
  color: var(--color-on-primary);
  border-color: var(--color-error);
}

.user-search {
  position: relative;
}

.user-search .input-field {
  width: 100%;
  padding: 12px 16px;
  border: 1px solid var(--color-outline-variant);
  border-radius: 12px;
  background: var(--color-surface-container);
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 14px;
  color: var(--color-on-surface);
  transition: all 0.2s ease;
  box-sizing: border-box;
}

.user-search .input-field:focus {
  outline: none;
  border-color: var(--color-primary);
  background: var(--color-surface);
  box-shadow: 0 0 0 3px var(--color-primary-container);
}

.user-search .input-field::placeholder {
  color: var(--color-outline);
}

.user-list {
  max-height: 280px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 4px;
  border-radius: 12px;
  background: var(--color-surface-container);
}

.user-loading,
.user-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px;
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 14px;
  color: var(--color-secondary);
}

.user-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.15s ease;
  background: var(--color-surface);
}

.user-item:hover {
  background: var(--color-surface-container-high);
}

.user-item.selected {
  background: var(--color-primary-container);
}

.user-item.selected:hover {
  background: var(--color-primary-container);
}

.user-item input[type="checkbox"] {
  appearance: none;
  width: 18px;
  height: 18px;
  border: 2px solid var(--color-outline);
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.15s ease;
  position: relative;
  flex-shrink: 0;
}

.user-item input[type="checkbox"]:checked {
  background: var(--color-primary);
  border-color: var(--color-primary);
}

.user-item input[type="checkbox"]:checked::after {
  content: '';
  position: absolute;
  left: 5px;
  top: 2px;
  width: 4px;
  height: 8px;
  border: solid var(--color-on-primary);
  border-width: 0 2px 2px 0;
  transform: rotate(45deg);
}

.user-item input[type="checkbox"]:hover {
  border-color: var(--color-primary);
}

.user-info {
  display: flex;
  flex-direction: column;
  gap: 3px;
  flex: 1;
  min-width: 0;
}

.user-name {
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 14px;
  font-weight: 500;
  color: var(--color-on-surface);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  line-height: 1.3;
}

.user-role {
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  padding: 2px 8px;
  border-radius: 100px;
  width: fit-content;
}

.user-role.role-0 {
  color: var(--color-primary);
  background: var(--color-primary-container);
}

.user-role.role-1 {
  color: var(--color-secondary);
  background: var(--color-surface-container-high);
}

.user-role.role-2 {
  color: var(--color-tertiary);
  background: var(--color-tertiary-container);
}

.modal-body .form-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.modal-body label {
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 14px;
  font-weight: 600;
  color: var(--color-on-surface);
}

.modal-body .input-field {
  padding: 12px 16px;
  border: 1px solid var(--color-outline-variant);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 14px;
  color: var(--color-on-surface);
  transition: border-color 0.2s;
}

.modal-body .input-field:focus {
  outline: none;
  border-color: var(--color-primary);
  border-width: 2px;
}

.radio-group {
  display: flex;
  gap: 24px;
}

.radio-option {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  font-weight: 400;
}

.radio-option input[type="radio"] {
  accent-color: var(--color-primary);
}

/* ---- Responsive ---- */

@media (max-width: 1100px) {
  .notifications-body.detail-open {
    display: block;
  }

  .list-panel {
    max-height: none;
  }

  .detail-panel {
    position: fixed;
    inset: 24px;
    z-index: 1500;
    min-height: auto;
    max-height: calc(100dvh - 48px);
  }
}

@media (max-width: 768px) {
  .page-header-row {
    flex-direction: column;
  }

  .page-header-actions {
    width: 100%;
  }

  .page-header-actions .btn-secondary,
  .page-header-actions .btn-primary {
    flex: 1;
  }

  .page-title {
    font-size: 32px;
  }

  .page-subtitle {
    font-size: 16px;
  }

  .header-stats {
    margin-top: 12px;
    gap: 16px;
  }

  .stat-item {
    font-size: 12px;
  }

  .notification-card {
    padding: 20px;
    gap: 14px;
    border-radius: 16px;
    flex-direction: column;
  }

  .card-meta-desktop {
    display: none;
  }

  .card-meta-mobile {
    display: block;
  }

  .unread-dot-mobile {
    display: block;
  }

  .btn-card-recall {
    opacity: 1;
  }

  .card-icon-wrapper {
    width: 36px;
    height: 36px;
  }

  .card-title {
    font-size: 16px;
  }

  .card-description {
    font-size: 13px;
    -webkit-line-clamp: 3;
  }

  .filter-section {
    gap: 8px;
  }

  .filter-btn {
    padding: 8px 14px;
    font-size: 11px;
  }

  .filter-count {
    min-width: 18px;
    height: 18px;
    font-size: 10px;
  }

  .page-btn {
    width: 40px;
    height: 40px;
  }

  /* Mobile: detail panel overlays list */
  .notifications-body {
    display: block;
    height: auto;
    min-height: auto;
  }

  .detail-panel {
    position: fixed;
    inset: 0;
    width: 100%;
    border-radius: 0;
    z-index: 1500;
    overflow-y: auto;
  }

  .detail-header {
    padding: 16px 16px 0;
  }

  .detail-icon-wrapper {
    width: 36px;
    height: 36px;
    border-radius: 10px;
  }

  .detail-divider {
    margin: 12px 16px 0;
  }

  .detail-body {
    padding: 16px 16px 20px;
  }

  .detail-title {
    font-size: 20px;
  }
}
</style>
