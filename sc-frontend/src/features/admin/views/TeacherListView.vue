<template>
  <div class="teacher-list-page">
    <!-- Page Header -->
    <header class="page-header">
      <h1>{{ t('admin.teacherList.title') }}</h1>
      <p class="page-subtitle">{{ t('admin.teacherList.subtitle') }}</p>
    </header>

    <!-- Filter Section -->
    <section class="filter-section">
      <div class="search-input">
        <Search :size="20" stroke-width="1.8"/>
        <input v-model="searchKeyword" :placeholder="t('admin.teacherList.searchPlaceholder')" @keyup.enter="submitSearch"/>
      </div>
    </section>

    <!-- Loading Skeleton -->
    <BaseSkeleton v-if="loading" variant="card" :count="6"/>

    <!-- Teacher Grid -->
    <div v-else-if="teachers.length > 0" class="teacher-grid">
      <div v-for="teacher in teachers" :key="teacher.id" class="teacher-card">
        <div class="card-header">
          <div class="teacher-avatar">
            <img v-if="teacher.avatarUrl" :src="teacher.avatarUrl" alt=""/>
            <img v-else src="/assets/avatar-teacher-default.png" alt=""/>
          </div>
          <span class="teacher-badge">{{ t('admin.teacherList.teacherBadge') }}</span>
        </div>
        <div class="card-content">
          <h3 class="teacher-name">{{ teacher.displayName || '-' }}</h3>
          <p class="teacher-email">{{ teacher.email }}</p>
          <div class="teacher-details">
            <div class="detail-item">
              <span class="detail-label">{{ t('admin.teacherList.status') }}</span>
              <span class="detail-value">{{ teacher.status }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">{{ t('admin.teacherList.provider') }}</span>
              <span class="detail-value">{{ teacher.createdProvider || '-' }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Empty State -->
    <div v-else class="empty-state">
      <Monitor :size="48" stroke-width="1.2" class="empty-icon"/>
      <h3>{{ t('admin.teacherList.noTeachers') }}</h3>
      <p>{{ t('admin.teacherList.noTeachersDesc') }}</p>
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
  </div>
</template>

<script lang="ts" setup>
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {ChevronLeft, ChevronRight, Monitor, Search} from 'lucide-vue-next'
import BaseSkeleton from '@/shared/components/BaseSkeleton.vue'
import {pageUsers} from '@/features/user/api/user'
import type {UserProfile} from '@/features/user/types/user'

const {t} = useI18n()

const teachers = ref<UserProfile[]>([])
const currentPage = ref(1)
const pageSize = ref(12)
const totalItems = ref(0)
const loading = ref(false)
const searchKeyword = ref('')
const submittedSearchKeyword = ref('')

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

async function loadTeachers() {
  loading.value = true
  try {
    const response = await pageUsers({
      page: currentPage.value,
      size: pageSize.value,
      keyword: submittedSearchKeyword.value || undefined,
      role: 2,
    })
    teachers.value = response.records
    totalItems.value = response.total
  } catch (error) {
    console.error('Failed to load teachers:', error)
    teachers.value = []
    totalItems.value = 0
  } finally {
    loading.value = false
  }
}

async function submitSearch() {
  submittedSearchKeyword.value = searchKeyword.value.trim()
  currentPage.value = 1
  await loadTeachers()
}

async function changePage(page: number) {
  if (page < 1 || page > totalPages.value) return
  currentPage.value = page
  await loadTeachers()
}

onMounted(() => {
  loadTeachers()
})
</script>

<style scoped>
.teacher-list-page {
  max-width: 100%;
}

/* Page Header */
.page-header {
  margin-bottom: 48px;
}

.page-header h1 {
  margin: 0 0 16px;
  font-family: 'Bodoni Moda', serif;
  font-size: 48px;
  font-weight: 600;
  color: var(--color-on-surface);
  letter-spacing: -0.02em;
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

.filter-select {
  padding: 12px 16px;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: 16px;
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 14px;
  color: var(--color-on-surface);
  cursor: pointer;
  min-width: 180px;
}

.filter-select:focus {
  outline: none;
  border-color: var(--color-primary);
}

/* Teacher Grid */
.teacher-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 24px;
  margin-bottom: 32px;
}

.teacher-card {
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: 24px;
  overflow: hidden;
  transition: all 0.2s;
}

.teacher-card:hover {
  border-color: var(--color-on-surface);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 24px 24px 0;
}

.teacher-avatar {
  width: 56px;
  height: 56px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: var(--color-surface-canvas);
  overflow: hidden;
}

.teacher-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.teacher-avatar svg {
  color: var(--color-muted);
}

.teacher-badge {
  padding: 4px 12px;
  background: var(--color-secondary);
  color: var(--color-on-primary);
  border-radius: var(--radius-sm);
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.08em;
}

.card-content {
  padding: 16px 24px 24px;
}

.teacher-name {
  margin: 0 0 4px;
  font-family: 'Bodoni Moda', serif;
  font-size: 20px;
  font-weight: 600;
  color: var(--color-on-surface);
}

.teacher-email {
  margin: 0 0 16px;
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 14px;
  color: var(--color-muted);
}

.teacher-details {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.detail-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.detail-full {
  grid-column: 1 / -1;
}

.detail-label {
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--color-muted);
}

.detail-value {
  font-family: 'Hanken Grotesk', sans-serif;
  font-size: 14px;
  font-weight: 500;
  color: var(--color-on-surface);
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
  font-family: 'Bodoni Moda', serif;
  font-size: 24px;
  font-weight: 600;
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
  font-weight: 600;
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

/* Responsive */
@media (max-width: 768px) {
  .page-header h1 {
    font-size: 32px;
  }

  .filter-section {
    flex-direction: column;
  }

  .teacher-grid {
    grid-template-columns: 1fr;
  }
}
</style>
