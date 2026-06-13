<template>
  <div class="forum-detail-page">
    <button class="back-link" @click="router.push('/community')">
      <ArrowLeft :size="16"/>
      返回论坛
    </button>

    <div v-if="forum" class="forum-header">
      <h1>{{ forum.forumName }}</h1>
      <p v-if="forum.description">{{ forum.description }}</p>
    </div>

    <div class="posts-header">
      <button class="btn-primary" @click="showEditor = true">
        <Plus :size="16"/>
        {{ t('forum.newPost') }}
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

    <PostEditor
      v-if="forum"
      :visible="showEditor"
      :forum-id="forum.id"
      :course-id="forum.courseId"
      @close="showEditor = false"
      @save="handleCreatePost"
    />
  </div>
</template>

<script lang="ts" setup>
import {onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import {ArrowLeft, Plus, MessageCircle} from 'lucide-vue-next'
import {getForum, getPosts, createPost} from '@/features/forum/api/forum'
import type {Forum, ForumPost} from '@/features/forum/types/forum'
import {notify} from '@/shared/composables/useGlobalNotification'
import PostCard from '@/features/forum/components/PostCard.vue'
import PostEditor from '@/features/forum/components/PostEditor.vue'

const {t} = useI18n()
const route = useRoute()
const router = useRouter()

const forumId = route.params.id as string

const loading = ref(true)
const forum = ref<Forum | null>(null)
const posts = ref<ForumPost[]>([])
const showEditor = ref(false)

onMounted(async () => {
  try {
    const [forumData, postsData] = await Promise.all([
      getForum(forumId),
      getPosts({forumId, page: 1, size: 50}),
    ])
    forum.value = forumData
    posts.value = postsData.records || []
  } finally {
    loading.value = false
  }
})

async function handleCreatePost(data: any) {
  try {
    await createPost(data)
    showEditor.value = false
    notify.success('发帖成功')
    const data2 = await getPosts({forumId, page: 1, size: 50})
    posts.value = data2.records || []
  } catch {
    notify.error('发帖失败')
  }
}
</script>

<style scoped>
.forum-detail-page { max-width: 100%; }
.back-link { display: inline-flex; align-items: center; gap: 6px; padding: 0; border: none; background: none; color: var(--color-muted); font-family: var(--font-body); font-size: 13px; cursor: pointer; margin-bottom: 24px; }
.back-link:hover { color: var(--color-on-surface); }
.forum-header { margin-bottom: 24px; }
.forum-header h1 { margin: 0 0 8px; font-family: var(--font-heading); font-size: 36px; font-weight: 600; color: var(--color-on-surface); }
.forum-header p { margin: 0; font-family: var(--font-body); font-size: 15px; color: var(--color-muted); }
.posts-header { display: flex; justify-content: flex-end; margin-bottom: 16px; }
.btn-primary { display: inline-flex; align-items: center; gap: 8px; padding: 10px 20px; border: none; border-radius: var(--radius-sm); background: var(--color-primary); color: var(--color-on-primary); font-family: var(--font-body); font-size: 13px; font-weight: 600; cursor: pointer; }
.btn-primary:hover { background: var(--color-primary-soft); }
.posts-list { display: flex; flex-direction: column; gap: 12px; }
.empty-state { display: flex; flex-direction: column; align-items: center; gap: 12px; padding: 80px 32px; text-align: center; color: var(--color-muted); }
.empty-state h3 { margin: 0; font-family: var(--font-heading); font-size: 22px; font-weight: 600; color: var(--color-on-surface); }
.empty-state p { margin: 0; font-family: var(--font-body); font-size: 15px; }
.loading-list { display: flex; flex-direction: column; gap: 12px; }
.skeleton-card { height: 120px; border-radius: 16px; }
.shimmer { background: linear-gradient(110deg, var(--color-surface-container-high) 8%, var(--color-surface-canvas) 18%, var(--color-surface-container-high) 33%); background-size: 200% 100%; animation: shimmer 1.4s ease-in-out infinite; }
@keyframes shimmer { to { background-position-x: -200%; } }
</style>

