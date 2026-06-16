<template>
  <div class="user-management-page">
    <!-- Page Header -->
    <header class="page-header">
      <h1>{{ t('admin.userManagement.title') }}</h1>
      <p class="page-subtitle">{{ t('admin.userManagement.subtitle') }}</p>
    </header>

    <!-- Filter Bar -->
    <section class="filter-section">
      <div class="search-input">
        <Search :size="20" stroke-width="1.8"/>
        <input v-model="searchKeyword" :placeholder="t('admin.userManagement.searchPlaceholder')" @keyup.enter="submitSearch"/>
      </div>
      <BaseSelect
        v-model="filterStatus"
        class="filter-select-control"
        :options="statusFilterOptions"
        min-width="150px"
        @change="handleFilterChange"
      />
      <BaseSelect
        v-model="filterRole"
        class="filter-select-control"
        :options="roleFilterOptions"
        min-width="150px"
        @change="handleFilterChange"
      />
      <button class="btn-reset-filter" type="button" @click="resetFilters">
        <X :size="16" stroke-width="1.8"/>
        {{ t('admin.userManagement.reset') }}
      </button>
    </section>

    <!-- Loading Skeleton -->
    <BaseSkeleton v-if="loading" variant="table" :count="8" :columns="7"/>

    <!-- Users Table -->
    <div v-else class="table-container">
      <table class="data-table">
        <thead>
          <tr>
            <th>{{ t('admin.userManagement.table.name') }}</th>
            <th>{{ t('admin.userManagement.table.email') }}</th>
            <th>{{ t('admin.userManagement.table.role') }}</th>
            <th>{{ t('admin.userManagement.table.status') }}</th>
            <th>{{ t('admin.userManagement.table.provider') }}</th>
            <th>{{ t('admin.userManagement.table.lastLogin') }}</th>
            <th>{{ t('admin.userManagement.table.actions') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="user in users" :key="user.id">
            <td>
              <div class="user-cell">
                <UserAvatarLink
                  :user-id="user.id"
                  :display-name="user.displayName || '-'"
                  :avatar-url="user.avatarUrl"
                  :role="user.role"
                  size="medium"
                  :show-name="true"
                />
              </div>
            </td>
            <td>{{ user.email || '-' }}</td>
            <td>
              <span class="role-badge" :class="getRoleClass(user.role)">
                {{ getRoleName(user.role) }}
              </span>
            </td>
            <td>
              <span class="status-badge" :class="user.status?.toLowerCase()">
                {{ user.status }}
              </span>
            </td>
            <td>{{ user.createdProvider || '-' }}</td>
            <td>{{ user.lastLoginProvider || '-' }}</td>
            <td>
              <div class="action-buttons">
                <button class="btn-icon" @click="editUser(user)">
                  <Pencil :size="16" stroke-width="1.8"/>
                </button>
                <button
                  v-if="authStore.user?.role === 0 && authStore.user?.id !== user.id"
                  class="btn-icon btn-reset"
                  :title="t('admin.userManagement.resetPassword')"
                  @click="resetUserPassword(user)"
                >
                  <KeyRound :size="16" stroke-width="1.8"/>
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Empty State -->
    <div v-if="users.length === 0 && !loading" class="empty-state">
      <Users :size="48" stroke-width="1.2" class="empty-icon"/>
      <h3>{{ t('admin.userManagement.noUsers') }}</h3>
      <p>{{ t('admin.userManagement.noUsersDesc') }}</p>
    </div>

    <!-- Pagination -->
    <nav v-if="totalPages > 1" class="pagination">
      <button class="page-btn" :disabled="currentPage === 1" @click="changePage(currentPage - 1)">
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
      <button class="page-btn" :disabled="currentPage === totalPages" @click="changePage(currentPage + 1)">
        <ChevronRight :size="18" stroke-width="2"/>
      </button>
    </nav>

    <!-- Edit Modal -->
    <Teleport to="body">
      <div v-if="showEditModal" class="modal-overlay">
        <div class="modal">
          <div class="modal-header">
            <h2>{{ t('admin.userManagement.editModal.title') }}</h2>
            <button class="btn-close" @click="showEditModal = false">
              <X :size="20" stroke-width="1.8"/>
            </button>
          </div>
          <form class="modal-body" @submit.prevent="saveUser">
            <div class="form-group">
              <label>{{ t('admin.userManagement.editModal.displayName') }}</label>
              <input v-model="editForm.displayName" class="form-input" type="text"/>
            </div>
            <div class="form-group">
              <label>{{ t('admin.userManagement.editModal.email') }}</label>
              <input v-model="editForm.email" class="form-input" type="email" disabled/>
            </div>
            <div class="form-group">
              <label>{{ t('admin.userManagement.editModal.status') }}</label>
              <BaseSelect
                v-model="editForm.status"
                class="form-select-control"
                :options="editStatusOptions"
                min-width="100%"
              />
            </div>
            <div class="modal-footer">
              <button type="button" class="btn-secondary" @click="showEditModal = false">{{ t('admin.userManagement.editModal.cancel') }}</button>
              <button type="submit" class="btn-primary">{{ t('admin.userManagement.editModal.save') }}</button>
            </div>
          </form>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script lang="ts" setup>
import {computed, onMounted, reactive, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {ChevronLeft, ChevronRight, KeyRound, Pencil, Search, Users, X} from 'lucide-vue-next'
import BaseSelect from '@/shared/components/BaseSelect.vue'
import BaseSkeleton from '@/shared/components/BaseSkeleton.vue'
import UserAvatarLink from '@/shared/components/UserAvatarLink.vue'
import {confirmDialog} from '@/shared/composables/useConfirmDialog'
import {notify} from '@/shared/composables/useGlobalNotification'
import {pageUsers, resetPassword, updateUser} from '@/features/user/api/user'
import {useAuthStore} from '@/features/auth/stores/auth'
import type {UserProfile, UserStatus} from '@/features/user/types/user'

const {t} = useI18n()
const authStore = useAuthStore()

const users = ref<UserProfile[]>([])
const currentPage = ref(1)
const pageSize = ref(10)
const totalItems = ref(0)
const loading = ref(false)
const searchKeyword = ref('')
const submittedSearchKeyword = ref('')
const filterStatus = ref('')
const filterRole = ref<number | undefined>(undefined)

const showEditModal = ref(false)
const editingUser = ref<UserProfile | null>(null)
const editForm = reactive({
  displayName: '',
  email: '',
  status: 'ACTIVE' as string,
})

const totalPages = computed(() => Math.max(1, Math.ceil(totalItems.value / pageSize.value)))

const statusFilterOptions = computed(() => [
  {label: t('admin.userManagement.allStatus'), value: ''},
  {label: t('admin.userManagement.active'), value: 'ACTIVE'},
  {label: t('admin.userManagement.disabled'), value: 'DISABLED'},
  {label: t('admin.userManagement.deleted'), value: 'DELETED'},
])

const roleFilterOptions = computed(() => [
  {label: t('admin.userManagement.allRoles'), value: undefined},
  {label: t('admin.userManagement.admin'), value: 0},
  {label: t('admin.userManagement.student'), value: 1},
  {label: t('admin.userManagement.teacher'), value: 2},
])

const editStatusOptions = computed(() => [
  {label: t('admin.userManagement.active'), value: 'ACTIVE'},
  {label: t('admin.userManagement.disabled'), value: 'DISABLED'},
])

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

function getRoleName(role?: number | null): string {
  switch (role) {
    case 0: return t('admin.userManagement.admin')
    case 1: return t('admin.userManagement.student')
    case 2: return t('admin.userManagement.teacher')
    default: return '-'
  }
}

function getRoleClass(role?: number | null): string {
  switch (role) {
    case 0: return 'admin'
    case 1: return 'student'
    case 2: return 'teacher'
    default: return ''
  }
}

async function loadUsers() {
  loading.value = true
  try {
    const response = await pageUsers({
      page: currentPage.value,
      size: pageSize.value,
      keyword: submittedSearchKeyword.value || undefined,
      status: (filterStatus.value || undefined) as UserStatus | undefined,
      role: filterRole.value,
    })
    users.value = response.records
    totalItems.value = response.total
  } catch (error) {
    console.error('Failed to load users:', error)
    notify.error('Failed to load users')
    users.value = []
  } finally {
    loading.value = false
  }
}

async function submitSearch() {
  submittedSearchKeyword.value = searchKeyword.value.trim()
  currentPage.value = 1
  await loadUsers()
}

async function handleFilterChange() {
  currentPage.value = 1
  await loadUsers()
}

async function resetFilters() {
  searchKeyword.value = ''
  submittedSearchKeyword.value = ''
  filterStatus.value = ''
  filterRole.value = undefined
  currentPage.value = 1
  await loadUsers()
}

async function changePage(page: number) {
  if (page < 1 || page > totalPages.value) return
  currentPage.value = page
  await loadUsers()
}

function editUser(user: UserProfile) {
  editingUser.value = user
  editForm.displayName = user.displayName || ''
  editForm.email = user.email || ''
  editForm.status = user.status || 'ACTIVE'
  showEditModal.value = true
}

async function saveUser() {
  if (!editingUser.value) return
  try {
    await updateUser(editingUser.value.id, {
      displayName: editForm.displayName,
      status: editForm.status as UserStatus,
    })
    showEditModal.value = false
    notify.success(t('admin.userManagement.alert.updateSuccess'))
    await loadUsers()
  } catch {
    notify.error(t('admin.userManagement.alert.updateFailed'))
  }
}

async function resetUserPassword(user: UserProfile) {
  if (!(await confirmDialog({message: t('admin.userManagement.resetPasswordConfirm')}))) return
  try {
    await resetPassword(user.id)
    notify.success(t('admin.userManagement.resetPasswordSuccess'))
  } catch {
    notify.error(t('admin.userManagement.resetPasswordFailed'))
  }
}

onMounted(() => {
  loadUsers()
})
</script>

<style scoped>
.user-management-page {
  max-width: 100%;
}

/* Page Header */
.page-header {
  margin-bottom: 48px;
}

.page-header h1 {
  margin: 0 0 16px;
  font-family: var(--font-heading);
  font-size: 48px;
  font-weight: 400;
  line-height: 1.3;
  color: var(--color-on-surface);
  letter-spacing: 0;
}

.page-subtitle {
  margin: 0;
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 16px;
  color: var(--color-muted);
  line-height: 1.6;
}

/* Filter Section */
.filter-section {
  display: flex;
  gap: 12px;
  margin-bottom: 32px;
  flex-wrap: wrap;
}

.search-input {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 250px;
  padding: 12px 16px;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: 16px;
}

.search-input svg {
  color: var(--color-muted);
  flex-shrink: 0;
}

.search-input input {
  flex: 1;
  border: none;
  outline: none;
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 14px;
  color: var(--color-on-surface);
  background: transparent;
}

.search-input input::placeholder {
  color: var(--color-muted);
}

.filter-select-control {
  flex: 0 0 auto;
}

.btn-reset-filter {
  min-height: 40px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 0 14px;
  background: var(--color-primary);
  border: 1px solid var(--color-primary);
  border-radius: 8px;
  color: var(--color-on-primary);
  font-family: var(--font-label);
  font-size: 13px;
  font-weight: 800;
  cursor: pointer;
  transition: background 0.2s, border-color 0.2s, color 0.2s, opacity 0.2s;
}

.btn-reset-filter svg {
  flex-shrink: 0;
}

.btn-reset-filter:hover {
  background: var(--color-primary-soft);
  border-color: var(--color-primary-soft);
}

/* Table */
.table-container {
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: 32px;
  overflow: hidden;
  margin-bottom: 32px;
}

.data-table {
  width: 100%;
  border-collapse: collapse;
}

.data-table th {
  padding: 16px 20px;
  text-align: left;
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 12px;
  font-weight: 400;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--color-muted);
  background: var(--color-surface-canvas);
  border-bottom: 1px solid var(--color-outline-light);
}

.data-table td {
  padding: 16px 20px;
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 14px;
  color: var(--color-on-surface);
  border-bottom: 1px solid var(--color-surface-canvas);
}

.data-table tr:last-child td {
  border-bottom: none;
}

.data-table tr:hover {
  background: var(--color-surface-canvas);
}

.user-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-avatar {
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: var(--color-surface-canvas);
  overflow: hidden;
  flex-shrink: 0;
}

.user-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.user-avatar svg {
  color: var(--color-muted);
}

.user-name {
  font-weight: 400;
}

/* Badges */
.role-badge {
  display: inline-block;
  padding: 4px 12px;
  border-radius: var(--radius-sm);
  font-size: 12px;
  font-weight: 400;
  letter-spacing: 0.05em;
}

.role-badge.admin {
  background: var(--color-primary);
  color: var(--color-on-primary);
}

.role-badge.student {
  background: var(--color-surface-canvas);
  color: var(--color-on-surface);
  border: 1px solid var(--color-outline-light);
}

.role-badge.teacher {
  background: var(--color-secondary);
  color: var(--color-on-primary);
}

.status-badge {
  display: inline-block;
  padding: 4px 12px;
  border-radius: var(--radius-sm);
  font-size: 12px;
  font-weight: 400;
}

.status-badge.active {
  background: var(--color-surface-container);
  color: var(--color-on-surface);
}

.status-badge.disabled {
  background: var(--color-surface-container);
  color: var(--color-muted);
}

.status-badge.deleted {
  background: var(--color-surface-container-high);
  color: var(--color-error);
}

/* Button */
.action-buttons {
  display: flex;
  gap: 4px;
}

.btn-icon {
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

.btn-icon:hover {
  background: var(--color-surface-canvas);
  color: var(--color-on-surface);
}

.btn-reset:hover {
  color: var(--color-error);
}

.btn-primary {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 12px 24px;
  background: var(--color-primary);
  color: var(--color-on-primary);
  border: 1px solid var(--color-primary);
  border-radius: var(--radius-sm);
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 14px;
  font-weight: 400;
  letter-spacing: 0.05em;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-primary:hover {
  background: var(--color-primary-soft);
  border-color: var(--color-primary-soft);
}

.btn-secondary {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 12px 24px;
  background: transparent;
  border: 1px solid var(--color-on-surface);
  border-radius: var(--radius-sm);
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 14px;
  font-weight: 400;
  color: var(--color-on-surface);
  cursor: pointer;
  transition: all 0.2s;
}

.btn-secondary:hover {
  background: var(--color-on-surface);
  color: var(--color-on-primary);
}

/* Empty State */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 120px 32px;
  margin-bottom: 32px;
}

.empty-icon {
  color: var(--color-muted);
  margin-bottom: 16px;
}

.empty-state h3 {
  margin: 0 0 8px;
  font-family: var(--font-heading);
  font-size: 24px;
  font-weight: 400;
  color: var(--color-on-surface);
}

.empty-state p {
  margin: 0;
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 16px;
  color: var(--color-muted);
}

/* Pagination */
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

.page-btn {
  width: 40px;
  height: 40px;
  display: grid;
  place-items: center;
  border-radius: var(--radius-sm);
  border: 1px solid var(--color-outline-light);
  background: transparent;
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 14px;
  font-weight: 400;
  color: var(--color-on-surface);
  cursor: pointer;
  transition: all 0.2s;
}

.page-btn:hover:not(:disabled) {
  background: var(--color-surface-canvas);
  border-color: var(--color-on-surface);
}

.page-btn.active {
  background: var(--color-primary);
  border-color: var(--color-primary);
  color: var(--color-on-primary);
}

.page-btn:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}

/* Modal */
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: var(--color-overlay);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal {
  background: var(--color-surface-card);
  border-radius: 32px;
  width: 100%;
  max-width: 480px;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 32px 32px 0;
}

.modal-header h2 {
  margin: 0;
  font-family: var(--font-heading);
  font-size: 28px;
  font-weight: 400;
  color: var(--color-on-surface);
}

.btn-close {
  width: 40px;
  height: 40px;
  display: grid;
  place-items: center;
  background: none;
  border: none;
  border-radius: 12px;
  color: var(--color-muted);
  cursor: pointer;
  transition: all 0.2s;
}

.btn-close:hover {
  background: var(--color-surface-canvas);
  color: var(--color-on-surface);
}

.modal-body {
  padding: 32px;
}

.form-group {
  margin-bottom: 20px;
}

.form-group label {
  display: block;
  margin-bottom: 8px;
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 14px;
  font-weight: 400;
  color: var(--color-on-surface);
}

.form-input {
  width: 100%;
  padding: 14px 16px;
  background: var(--color-surface-canvas);
  border: 1px solid transparent;
  border-radius: 16px;
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 16px;
  color: var(--color-on-surface);
  transition: all 0.2s;
  box-sizing: border-box;
}

.form-select-control {
  width: 100%;
}

.form-input:focus {
  outline: none;
  background: var(--color-surface-card);
  border-color: var(--color-primary);
  border-width: 2px;
}

.form-input:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 0 32px 32px;
}

/* Responsive */
@media (max-width: 768px) {
  .page-header h1 {
    font-size: 32px;
  }

  .filter-section {
    flex-direction: column;
  }

  .filter-select-control {
    width: 100%;
  }

  .table-container {
    overflow-x: auto;
  }

  .data-table {
    min-width: 700px;
  }
}
</style>
