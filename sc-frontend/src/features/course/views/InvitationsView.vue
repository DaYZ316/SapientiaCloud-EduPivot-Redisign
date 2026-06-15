<template>
  <div class="invitations-page">
    <div class="page-header">
      <h1>{{ t('invitations.title') }}</h1>
    </div>

    <div class="tabs">
      <button
        class="tab-btn"
        :class="{active: activeTab === 'received'}"
        @click="activeTab = 'received'; currentPage = 1; loadData()"
      >
        {{ t('invitations.tabs.received') }}
      </button>
      <button
        class="tab-btn"
        :class="{active: activeTab === 'sent'}"
        @click="activeTab = 'sent'; currentPage = 1; loadData()"
      >
        {{ t('invitations.tabs.sent') }}
      </button>
    </div>

    <div v-if="invitations.length > 0" class="invitations-list">
      <div v-for="inv in invitations" :key="inv.id" class="invitation-card">
        <div class="invitation-cover">
          <img v-if="inv.courseCoverUrl" :src="inv.courseCoverUrl" alt="Cover"/>
          <BookOpen v-else :size="24" stroke-width="1.5"/>
        </div>
        <div class="invitation-info">
          <h3>{{ inv.courseTitle || '--' }}</h3>
          <div class="invitation-meta">
            <span class="invitation-status-badge" :class="getStatusClass(inv.status)">
              {{ InvitationStatus[inv.status] }}
            </span>
            <span v-if="activeTab === 'received'" class="invitation-person">
              {{ t('invitations.inviter') }}: {{ inv.inviterName || '--' }}
            </span>
            <span v-else class="invitation-person">
              {{ t('invitations.invitee') }}: {{ inv.inviteeName || '--' }}
            </span>
            <span class="invitation-date">{{ formatDate(inv.createdAt) }}</span>
          </div>
          <p v-if="inv.message" class="invitation-message">{{ inv.message }}</p>
        </div>
        <div class="invitation-actions">
          <template v-if="activeTab === 'received' && inv.status === 0">
            <button class="btn-primary btn-sm" @click="handleAccept(inv)">
              {{ t('invitations.actions.accept') }}
            </button>
            <button class="btn-secondary btn-sm" @click="handleDecline(inv)">
              {{ t('invitations.actions.decline') }}
            </button>
          </template>
          <template v-if="activeTab === 'sent' && inv.status === 0">
            <button class="btn-danger btn-sm" @click="confirmWithdraw(inv)">
              {{ t('invitations.actions.withdraw') }}
            </button>
          </template>
        </div>
      </div>
    </div>

    <div v-else-if="loading" class="invitations-list">
      <div v-for="n in 3" :key="n" class="skeleton-invitation">
        <div class="skeleton-cover shimmer"></div>
        <div class="skeleton-body">
          <div class="skeleton-title shimmer"></div>
          <div class="skeleton-meta">
            <div class="skeleton-tag shimmer"></div>
            <div class="skeleton-tag wide shimmer"></div>
          </div>
        </div>
      </div>
    </div>

    <div v-else class="empty-state">
      <Mail :size="48" stroke-width="1.2"/>
      <h3>{{ activeTab === 'received' ? t('invitations.empty.received') : t('invitations.empty.sent') }}</h3>
    </div>

    <div v-if="totalPages > 1" class="pagination">
      <button class="btn-page" :disabled="currentPage === 1" @click="changePage(currentPage - 1)">
        {{ t('courses.previous') }}
      </button>
      <div class="page-numbers">
        <button
          v-for="page in displayedPages"
          :key="page"
          class="btn-page"
          :class="{active: currentPage === page}"
          @click="changePage(page)"
        >
          {{ page }}
        </button>
      </div>
      <button class="btn-page" :disabled="currentPage === totalPages" @click="changePage(currentPage + 1)">
        {{ t('courses.next') }}
      </button>
    </div>

    <Teleport to="body">
      <div v-if="showWithdrawModal" class="modal-overlay">
        <div class="modal modal-sm">
          <div class="modal-header">
            <h2>{{ t('invitations.confirm.withdrawTitle') }}</h2>
            <button class="btn-close" @click="showWithdrawModal = false">
              <X :size="20"/>
            </button>
          </div>
          <div class="modal-body">
            <p>{{ t('invitations.confirm.withdrawMessage') }}</p>
          </div>
          <div class="modal-footer">
            <button class="btn-secondary" @click="showWithdrawModal = false">{{ t('courses.deleteModal.cancel') }}</button>
            <button class="btn-danger" @click="handleWithdraw">{{ t('invitations.actions.withdraw') }}</button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script lang="ts" setup>
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {BookOpen, Mail, X} from 'lucide-vue-next'

import {
  getReceivedInvitations,
  getSentInvitations,
  acceptInvitation,
  declineInvitation,
  withdrawInvitation,
} from '@/features/course/api/invitation'
import {InvitationStatus} from '@/features/course/types/invitation'
import type {CourseInvitation} from '@/features/course/types/invitation'
import {notify} from '@/shared/composables/useGlobalNotification'

const {t} = useI18n()

const activeTab = ref<'received' | 'sent'>('received')
const currentPage = ref(1)
const totalPages = ref(1)
const pageSize = ref(12)
const loading = ref(false)
const invitations = ref<CourseInvitation[]>([])
const showWithdrawModal = ref(false)
const withdrawTarget = ref<CourseInvitation | null>(null)

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

function getStatusClass(status: number): string {
  const map: Record<number, string> = {0: 'pending', 1: 'accepted', 2: 'declined', 3: 'withdrawn'}
  return map[status] ?? ''
}

function formatDate(dateStr: string): string {
  return new Date(dateStr).toLocaleDateString('en-US', {month: 'short', day: 'numeric', year: 'numeric'})
}

async function loadData() {
  loading.value = true
  try {
    const query = {page: currentPage.value, size: pageSize.value}
    const response = activeTab.value === 'received'
      ? await getReceivedInvitations(query)
      : await getSentInvitations(query)
    invitations.value = response.records
    totalPages.value = Math.ceil(response.total / pageSize.value)
  } catch (error) {
    console.error('Failed to load invitations:', error)
    notify.error('Failed to load invitations')
    invitations.value = []
  } finally {
    loading.value = false
  }
}

function changePage(page: number) {
  currentPage.value = page
  loadData()
}

async function handleAccept(inv: CourseInvitation) {
  try {
    await acceptInvitation(inv.id)
    notify.success(t('invitations.alert.acceptSuccess'))
    await loadData()
  } catch (error) {
    console.error('Failed to accept invitation:', error)
    notify.error(t('invitations.alert.acceptFailed'))
  }
}

async function handleDecline(inv: CourseInvitation) {
  try {
    await declineInvitation(inv.id)
    notify.success(t('invitations.alert.declineSuccess'))
    await loadData()
  } catch (error) {
    console.error('Failed to decline invitation:', error)
    notify.error(t('invitations.alert.declineFailed'))
  }
}

function confirmWithdraw(inv: CourseInvitation) {
  withdrawTarget.value = inv
  showWithdrawModal.value = true
}

async function handleWithdraw() {
  if (!withdrawTarget.value) return
  try {
    await withdrawInvitation(withdrawTarget.value.id)
    showWithdrawModal.value = false
    withdrawTarget.value = null
    notify.success(t('invitations.alert.withdrawSuccess'))
    await loadData()
  } catch (error) {
    console.error('Failed to withdraw invitation:', error)
    notify.error(t('invitations.alert.withdrawFailed'))
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.invitations-page {
  max-width: 100%;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 32px;
}

.page-header h1 {
  margin: 0;
  font-family: var(--font-heading);
  font-size: 48px;
  font-weight: 400;
  line-height: 1.3;
  color: var(--color-on-surface);
}

.tabs {
  display: flex;
  gap: 4px;
  margin-bottom: 24px;
  padding: 4px;
  background: var(--color-surface-canvas);
  border-radius: 12px;
  width: fit-content;
}

.tab-btn {
  padding: 10px 24px;
  background: transparent;
  border: none;
  border-radius: 8px;
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 14px;
  font-weight: 400;
  color: var(--color-muted);
  cursor: pointer;
  transition: all 0.2s;
}

.tab-btn:hover {
  color: var(--color-on-surface);
}

.tab-btn.active {
  background: var(--color-surface-card);
  color: var(--color-on-surface);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
}

.invitations-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 32px;
}

.invitation-card,
.skeleton-invitation {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 20px 24px;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: 16px;
}

.invitation-card {
  transition: all 0.2s;
}

.invitation-card:hover {
  border-color: var(--color-on-surface);
}

.invitation-cover,
.skeleton-cover {
  width: 80px;
  height: 60px;
  border-radius: 8px;
  flex-shrink: 0;
}

.invitation-cover {
  display: grid;
  place-items: center;
  background: var(--color-surface-canvas);
  color: var(--color-on-surface);
  overflow: hidden;
}

.invitation-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.invitation-info,
.skeleton-body {
  flex: 1;
  min-width: 0;
}

.invitation-info h3 {
  margin: 0 0 8px;
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 16px;
  font-weight: 400;
  color: var(--color-on-surface);
}

.invitation-meta,
.skeleton-meta {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}

.skeleton-meta {
  margin-top: 16px;
}

.invitation-status-badge {
  padding: 4px 10px;
  border-radius: var(--radius-sm);
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 11px;
  font-weight: 400;
}

.invitation-status-badge.pending {
  background: var(--color-primary);
  color: var(--color-on-primary);
}

.invitation-status-badge.accepted {
  background: var(--color-surface-container);
  color: var(--color-on-surface);
}

.invitation-status-badge.declined {
  background: var(--color-surface-container-high);
  color: var(--color-error);
}

.invitation-status-badge.withdrawn {
  background: var(--color-surface-canvas);
  color: var(--color-muted);
}

.invitation-person,
.invitation-date {
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 12px;
  color: var(--color-muted);
}

.invitation-message {
  margin: 8px 0 0;
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 13px;
  color: var(--color-muted);
  font-style: italic;
}

.invitation-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.btn-sm {
  padding: 8px 16px;
  font-size: 13px;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 120px 32px;
  color: var(--color-muted);
}

.empty-state h3 {
  margin: 16px 0 0;
  font-family: var(--font-heading);
  font-size: 24px;
  font-weight: 400;
  color: var(--color-on-surface);
}

.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 8px;
}

.page-numbers {
  display: flex;
  gap: 4px;
}

.btn-page {
  padding: 10px 16px;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: 8px;
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 14px;
  font-weight: 400;
  color: var(--color-on-surface);
  cursor: pointer;
  transition: all 0.2s;
}

.btn-page:hover:not(:disabled) {
  background: var(--color-surface-container);
}

.btn-page.active,
.btn-page.active:hover:not(:disabled) {
  background: var(--color-primary);
  border-color: var(--color-primary);
  color: var(--color-on-primary);
}

.btn-page:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.modal-overlay {
  position: fixed;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: var(--color-overlay);
  z-index: 1000;
}

.modal {
  width: 100%;
  max-width: 400px;
  max-height: calc(100dvh - 48px);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: 24px;
  box-shadow: 0 24px 80px rgba(0, 0, 0, 0.16);
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24px 28px 0;
}

.modal-header h2 {
  margin: 0;
  font-family: var(--font-heading);
  font-size: 24px;
  font-weight: 400;
  color: var(--color-on-surface);
}

.btn-close {
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  background: none;
  border: none;
  border-radius: 8px;
  color: var(--color-muted);
  cursor: pointer;
  transition: all 0.2s;
}

.btn-close:hover {
  background: var(--color-surface-canvas);
  color: var(--color-on-surface);
}

.modal-body {
  padding: 24px 28px;
  overflow-y: auto;
}

.modal-body p {
  margin: 0;
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 14px;
  color: var(--color-on-surface);
  line-height: 1.6;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 0 28px 24px;
  flex-shrink: 0;
}

.btn-primary,
.btn-secondary,
.btn-danger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 14px 28px;
  border-radius: var(--radius-sm);
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 14px;
  font-weight: 400;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-primary {
  background: var(--color-primary);
  border: 1px solid var(--color-primary);
  color: var(--color-on-primary);
}

.btn-primary:hover {
  background: var(--color-primary-soft);
  border-color: var(--color-primary-soft);
}

.btn-secondary {
  background: transparent;
  border: 1px solid var(--color-on-surface);
  color: var(--color-on-surface);
}

.btn-secondary:hover {
  background: var(--color-on-surface);
  color: var(--color-on-primary);
}

.btn-danger {
  background: var(--color-error);
  color: #fff;
  border: none;
}

.btn-danger:hover {
  background: var(--color-on-surface);
  color: var(--color-on-primary);
}

.skeleton-title {
  height: 20px;
  width: 50%;
  border-radius: 6px;
  background: var(--color-surface-canvas);
  margin-bottom: 12px;
}

.skeleton-tag {
  height: 22px;
  width: 56px;
  border-radius: var(--radius-sm);
  background: var(--color-surface-canvas);
}

.skeleton-tag.wide {
  width: 80px;
}

.skeleton-cover {
  background: var(--color-surface-canvas);
}

.shimmer {
  position: relative;
  overflow: hidden;
}

.shimmer::after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(
    90deg,
    transparent 0%,
    var(--color-surface-card) 40%,
    var(--color-surface-card) 60%,
    transparent 100%
  );
  animation: shimmer 1.4s ease-in-out infinite;
}

@keyframes shimmer {
  0% { transform: translateX(-100%); }
  100% { transform: translateX(100%); }
}

@media (max-width: 768px) {
  .page-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 16px;
  }

  .page-header h1 {
    font-size: 32px;
  }

  .invitation-card,
  .skeleton-invitation {
    align-items: flex-start;
    flex-direction: column;
  }

  .invitation-actions {
    width: 100%;
    justify-content: flex-end;
  }

  .pagination {
    flex-wrap: wrap;
  }
}
</style>
