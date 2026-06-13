<template>
  <div class="forum-list-page">
    <div class="page-header">
      <h1>{{ t('forum.title') }}</h1>
      <button class="btn-primary" @click="showEditor = true">
        <Plus :size="16"/>
        {{ t('forum.newPost') }}
      </button>
    </div>

    <div class="forum-grid">
      <div class="main-content">
        <div class="sort-tabs">
          <button
            v-for="tab in sortTabs"
            :key="tab.value"
            class="sort-tab"
            :class="{active: currentSort === tab.value}"
            @click="currentSort = tab.value"
          >
            {{ tab.label }}
          </button>
        </div>

        <div v-if="loading" class="loading-list">
          <div v-for="i in 3" :key="i" class="skeleton-card shimmer"></div>
        </div>

        <div v-else-if="posts.length === 0" class="empty-state">
          <MessageCircle :size="36" stroke-width="1.4"/>
          <h3>{{ t('forum.noPosts') }}</h3>
          <p>{{ t('forum.noPostsDesc') }}</p>
        </div>

        <div v-else class="posts-list">
          <PostCard
            v-for="post in posts"
            :key="post.id"
            :post="post"
            @click="router.push('/community/posts/' + post.id)"
          />
        </div>
      </div>

      <aside class="sidebar">
        <div class="sidebar-card">
          <h3>{{ t('forum.hotPosts') }}</h3>
          <div v-if="hotPosts.length" class="hot-list">
            <div
              v-for="(post, index) in hotPosts"
              :key="post.id"
              class="hot-item"
              @click="router.push('/community/posts/' + post.id)"
            >
              <span class="hot-rank">{{ index + 1 }}</span>
              <span class="hot-title">{{ post.title }}</span>
            </div>
          </div>
          <p v-else class="empty-text">{{ t('forum.noPosts') }}</p>
        </div>
      </aside>
    </div>

    <PostEditor
      :visible="showEditor"
      :forum-id="defaultForumId"
      :course-id="defaultCourseId"
      @close="showEditor = false"
      @save="handleCreatePost"
    />
  </div>
</template>

<script lang="ts" setup>
import {computed, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import {Plus, MessageCircle} from 'lucide-vue-next'
import {getPosts, getHotPosts, createPost} from '@/features/forum/api/forum'
import type {ForumPost} from '@/features/forum/types/forum'
import {notify} from '@/shared/composables/useGlobalNotification'
import PostCard from '@/features/forum/components/PostCard.vue'
import PostEditor from '@/features/forum/components/PostEditor.vue'

const {t} = useI18n()
const router = useRouter()

const loading = ref(true)
const posts = ref<ForumPost[]>([])
const hotPosts = ref<ForumPost[]>([])
const currentSort = ref('latest')
const showEditor = ref(false)
const defaultForumId = ref('')
const defaultCourseId = ref('')

const sortTabs = computed(() => [
  {value: 'latest', label: t('forum.sortByLatest')},
  {value: 'hot', label: t('forum.sortByHot')},
  {value: 'essence', label: t('forum.sortByEssence')},
])

onMounted(async () => {
  try {
    const [postsData, hotData] = await Promise.all([
      getPosts({sort: 'latest', page: 1, size: 20}),
      getHotPosts(),
    ])
    posts.value = postsData.records || []
    hotPosts.value = (hotData || []).slice(0, 5)
  } finally {
    loading.value = false
  }
})

watch(currentSort, async (sort) => {
  loading.value = true
  try {
    const data = await getPosts({sort, page: 1, size: 20})
    posts.value = data.records || []
  } finally {
    loading.value = false
  }
})

async function handleCreatePost(data: any) {
  try {
    await createPost(data)
    showEditor.value = false
    notify.success('发帖成功')
    const data2 = await getPosts({sort: currentSort.value, page: 1, size: 20})
    posts.value = data2.records || []
  } catch {
    notify.error('发帖失败')
  }
}
</script>

<style scoped>
.forum-list-page {
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
  font-weight: 600;
  color: var(--color-on-surface);
}

.btn-primary {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  border: none;
  border-radius: var(--radius-sm);
  background: var(--color-primary);
  color: var(--color-on-primary);
  font-family: var(--font-body);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s;
}

.btn-primary:hover {
  background: var(--color-primary-soft);
}

.forum-grid {
  display: grid;
  grid-template-columns: 1fr 280px;
  gap: 32px;
}

.sort-tabs {
  display: flex;
  gap: 4px;
  margin-bottom: 16px;
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-md);
  padding: 4px;
}

.sort-tab {
  flex: 1;
  padding: 8px 16px;
  border: none;
  background: none;
  color: var(--color-muted);
  font-family: var(--font-body);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  border-radius: var(--radius-sm);
  transition: background 0.15s, color 0.15s;
}

.sort-tab.active {
  background: var(--color-surface-container-high);
  color: var(--color-on-surface);
}

.posts-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.sidebar {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.sidebar-card {
  background: var(--color-surface-card);
  border: 1px solid var(--color-outline-light);
  border-radius: var(--radius-lg);
  padding: 24px;
}

.sidebar-card h3 {
  margin: 0 0 16px;
  font-family: var(--font-body);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--color-muted);
}

.hot-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.hot-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 8px 0;
  cursor: pointer;
  border-bottom: 1px solid var(--color-outline-light);
}

.hot-item:last-child {
  border-bottom: none;
}

.hot-item:hover .hot-title {
  color: var(--color-on-surface);
}

.hot-rank {
  font-family: var(--font-heading);
  font-size: 18px;
  font-weight: 700;
  color: var(--color-muted);
  min-width: 20px;
}

.hot-title {
  font-family: var(--font-body);
  font-size: 13px;
  color: var(--color-on-surface-variant);
  line-height: 1.4;
  transition: color 0.15s;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 80px 32px;
  text-align: center;
  color: var(--color-muted);
}

.empty-state h3 {
  margin: 0;
  font-family: var(--font-heading);
  font-size: 22px;
  font-weight: 600;
  color: var(--color-on-surface);
}

.empty-state p {
  margin: 0;
  font-family: var(--font-body);
  font-size: 15px;
}

.empty-text {
  margin: 0;
  font-family: var(--font-body);
  font-size: 14px;
  color: var(--color-muted);
}

.loading-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.skeleton-card {
  height: 120px;
  border-radius: 16px;
}

.shimmer {
  background: linear-gradient(110deg, var(--color-surface-container-high) 8%, var(--color-surface-canvas) 18%, var(--color-surface-container-high) 33%);
  background-size: 200% 100%;
  animation: shimmer 1.4s ease-in-out infinite;
}

@keyframes shimmer {
  to { background-position-x: -200%; }
}

@media (max-width: 1024px) {
  .forum-grid {
    grid-template-columns: 1fr;
  }
}
</style>

