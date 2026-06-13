<template>
  <div class="bank-list-page">
    <div class="page-header">
      <h1>{{ t('questionBank.title') }}</h1>
      <button v-if="isTeacher" class="btn-primary" @click="showEditor = true">
        <Plus :size="16"/>
        {{ t('questionBank.newBank') }}
      </button>
    </div>

    <div v-if="loading" class="loading-grid">
      <div v-for="i in 4" :key="i" class="skeleton-card shimmer"></div>
    </div>

    <div v-else-if="banks.length === 0" class="empty-state">
      <Database :size="36" stroke-width="1.4"/>
      <h3>{{ t('questionBank.noBanks') }}</h3>
      <p>{{ t('questionBank.noBanksDesc') }}</p>
    </div>

    <div v-else class="banks-grid">
      <div
        v-for="bank in banks"
        :key="bank.id"
        class="bank-card"
        @click="router.push('/question-banks/' + bank.id)"
      >
        <div class="bank-icon">
          <Database :size="24"/>
        </div>
        <h3 class="bank-name">{{ bank.bankName }}</h3>
        <p v-if="bank.description" class="bank-desc">{{ bank.description }}</p>
        <div class="bank-meta">
          <span><FileText :size="14"/> {{ bank.questionCount }} {{ t('questionBank.questionCount') }}</span>
          <span class="difficulty" :class="'diff-' + bank.difficulty">{{ difficultyName(bank.difficulty) }}</span>
        </div>
      </div>
    </div>

    <div v-if="showEditor" class="editor-overlay" @click.self="showEditor = false">
      <div class="editor-dialog">
        <div class="editor-header">
          <h2>{{ t('questionBank.newBank') }}</h2>
          <button class="close-btn" @click="showEditor = false"><X :size="18"/></button>
        </div>
        <div class="editor-body">
          <div class="field">
            <label>{{ t('questionBank.bankName') }} *</label>
            <input v-model="newBank.bankName" type="text" class="input"/>
          </div>
          <div class="field">
            <label>{{ t('questionBank.bankDescription') }}</label>
            <textarea v-model="newBank.description" class="textarea" rows="3"></textarea>
          </div>
          <div class="field-row">
            <div class="field">
              <label>{{ t('questionBank.bankType') }}</label>
              <select v-model="newBank.bankType" class="input">
                <option :value="0">练习</option>
                <option :value="1">考试</option>
                <option :value="2">作业</option>
              </select>
            </div>
            <div class="field">
              <label>{{ t('questionBank.difficulty') }}</label>
              <select v-model="newBank.difficulty" class="input">
                <option :value="1">简单</option>
                <option :value="2">中等</option>
                <option :value="3">困难</option>
              </select>
            </div>
          </div>
        </div>
        <div class="editor-footer">
          <button class="btn-cancel" @click="showEditor = false">{{ t('chapter.cancel') }}</button>
          <button class="btn-save" :disabled="!newBank.bankName.trim()" @click="handleCreateBank">{{ t('chapter.save') }}</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import {onMounted, ref, computed} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import {Plus, Database, FileText, X} from 'lucide-vue-next'
import {getCourseQuestionBanks, createQuestionBank} from '@/features/question-bank/api/questionBank'
import {QuestionDifficulty} from '@/features/question-bank/types/questionBank'
import type {QuestionBank} from '@/features/question-bank/types/questionBank'
import {useAuthStore} from '@/features/auth/stores/auth'
import {notify} from '@/shared/composables/useGlobalNotification'

const {t} = useI18n()
const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const courseId = route.params.courseId as string
const loading = ref(true)
const banks = ref<QuestionBank[]>([])
const showEditor = ref(false)

const isTeacher = computed(() => authStore.user?.role === 2 || authStore.user?.role === 0)

const newBank = ref({
  bankName: '',
  description: '',
  bankType: 0,
  difficulty: 2,
})

function difficultyName(d: number) {
  return QuestionDifficulty[d] || '未知'
}

onMounted(async () => {
  try {
    if (courseId) {
      banks.value = await getCourseQuestionBanks(courseId)
    }
  } finally {
    loading.value = false
  }
})

async function handleCreateBank() {
  try {
    const id = await createQuestionBank({
      courseId,
      bankName: newBank.value.bankName,
      description: newBank.value.description || undefined,
      bankType: newBank.value.bankType,
      difficulty: newBank.value.difficulty,
    })
    showEditor.value = false
    notify.success('题库创建成功')
    router.push('/question-banks/' + id)
  } catch {
    notify.error('创建失败')
  }
}
</script>

<style scoped>
.bank-list-page { max-width: 100%; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 32px; }
.page-header h1 { margin: 0; font-family: var(--font-heading); font-size: 48px; font-weight: 600; color: var(--color-on-surface); }
.btn-primary { display: inline-flex; align-items: center; gap: 8px; padding: 10px 20px; border: none; border-radius: var(--radius-sm); background: var(--color-primary); color: var(--color-on-primary); font-family: var(--font-body); font-size: 13px; font-weight: 600; cursor: pointer; }
.btn-primary:hover { background: var(--color-primary-soft); }
.banks-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 16px; }
.bank-card { display: flex; flex-direction: column; gap: 10px; padding: 24px; background: var(--color-surface-card); border: 1px solid var(--color-outline-light); border-radius: var(--radius-lg); cursor: pointer; transition: border-color 0.15s; }
.bank-card:hover { border-color: var(--color-on-surface); }
.bank-icon { width: 40px; height: 40px; display: grid; place-items: center; background: var(--color-surface-container-high); border-radius: 12px; color: var(--color-on-surface); }
.bank-name { margin: 0; font-family: var(--font-heading); font-size: 20px; font-weight: 600; color: var(--color-on-surface); }
.bank-desc { margin: 0; font-family: var(--font-body); font-size: 13px; color: var(--color-muted); line-height: 1.4; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.bank-meta { display: flex; align-items: center; gap: 12px; font-family: var(--font-body); font-size: 12px; color: var(--color-muted); }
.bank-meta span { display: inline-flex; align-items: center; gap: 4px; }
.difficulty.diff-1 { color: #22c55e; }
.difficulty.diff-2 { color: #eab308; }
.difficulty.diff-3 { color: #ef4444; }
.empty-state { display: flex; flex-direction: column; align-items: center; gap: 12px; padding: 80px 32px; text-align: center; color: var(--color-muted); }
.empty-state h3 { margin: 0; font-family: var(--font-heading); font-size: 22px; font-weight: 600; color: var(--color-on-surface); }
.empty-state p { margin: 0; font-family: var(--font-body); font-size: 15px; }
.loading-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 16px; }
.skeleton-card { height: 160px; border-radius: var(--radius-lg); }
.shimmer { background: linear-gradient(110deg, var(--color-surface-container-high) 8%, var(--color-surface-canvas) 18%, var(--color-surface-container-high) 33%); background-size: 200% 100%; animation: shimmer 1.4s ease-in-out infinite; }
@keyframes shimmer { to { background-position-x: -200%; } }
.editor-overlay { position: fixed; inset: 0; background: var(--color-overlay); display: grid; place-items: center; z-index: 1000; padding: 24px; }
.editor-dialog { width: 100%; max-width: 500px; background: var(--color-surface-card); border: 1px solid var(--color-outline-light); border-radius: var(--radius-lg); }
.editor-header { display: flex; align-items: center; justify-content: space-between; padding: 24px 28px 16px; }
.editor-header h2 { margin: 0; font-family: var(--font-heading); font-size: 22px; font-weight: 600; color: var(--color-on-surface); }
.close-btn { display: grid; place-items: center; width: 32px; height: 32px; border: none; background: none; color: var(--color-muted); cursor: pointer; border-radius: 8px; }
.close-btn:hover { background: var(--color-surface-container); color: var(--color-on-surface); }
.editor-body { padding: 0 28px 16px; display: flex; flex-direction: column; gap: 16px; }
.field { display: flex; flex-direction: column; gap: 6px; }
.field label { font-family: var(--font-body); font-size: 12px; font-weight: 600; letter-spacing: 0.05em; text-transform: uppercase; color: var(--color-muted); }
.input, .textarea, select.input { width: 100%; padding: 10px 14px; background: var(--color-surface-container); border: 1px solid var(--color-outline-light); border-radius: var(--radius-sm); color: var(--color-on-surface); font-family: var(--font-body); font-size: 14px; outline: none; }
.input:focus, .textarea:focus, select.input:focus { border-color: var(--color-on-surface); }
.textarea { resize: vertical; min-height: 60px; }
.field-row { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.editor-footer { display: flex; justify-content: flex-end; gap: 12px; padding: 16px 28px 24px; border-top: 1px solid var(--color-outline-light); }
.btn-cancel, .btn-save { padding: 10px 20px; border: none; border-radius: var(--radius-sm); font-family: var(--font-body); font-size: 13px; font-weight: 600; cursor: pointer; }
.btn-cancel { background: var(--color-surface-container); color: var(--color-on-surface); }
.btn-save { background: var(--color-primary); color: var(--color-on-primary); }
.btn-save:disabled { opacity: 0.5; cursor: not-allowed; }
</style>

