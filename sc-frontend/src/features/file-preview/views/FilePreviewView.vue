<template>
    <div class="file-preview-page">
        <header class="page-header">
            <div class="page-header-row">
                <div>
                    <h1 class="page-title">{{ t('filePreview.title') }}</h1>
                    <p class="page-subtitle">{{ fileName }} · {{ fileTypeLabel }}</p>
                </div>
                <div class="page-header-actions">
                    <button class="btn-back" @click="goBack" :title="t('filePreview.back')">
                        <ArrowLeft :size="18" :stroke-width="1.5" />
                        <span>{{ t('filePreview.back') }}</span>
                    </button>
                    <a v-if="fileUrl" :href="fileUrl" download class="btn-download">
                        <Download :size="18" :stroke-width="1.5" />
                        <span>{{ t('filePreview.download') }}</span>
                    </a>
                </div>
            </div>
        </header>

        <main class="preview-content">
            <!-- Loading -->
            <div v-if="loading" class="preview-status">
                <div class="spinner"></div>
                <p>{{ t('filePreview.loading') }}</p>
            </div>

            <!-- Error -->
            <div v-else-if="error" class="preview-status">
                <AlertCircle :size="48" :stroke-width="1" />
                <p>{{ error }}</p>
                <a v-if="fileUrl" :href="fileUrl" download class="btn-primary">
                    <Download :size="16" :stroke-width="1.5" />
                    {{ t('filePreview.download') }}
                </a>
            </div>

            <!-- PDF -->
            <VuePdf
                v-else-if="fileCategory === 'pdf'"
                :src="fileUrl"
                @on-error="handleLoadError"
            />

            <!-- Word -->
            <VueDocx
                v-else-if="fileCategory === 'word'"
                :src="fileUrl"
                @on-error="handleLoadError"
            />

            <!-- Excel -->
            <VueExcel
                v-else-if="fileCategory === 'excel'"
                :src="fileUrl"
                @on-error="handleLoadError"
            />

            <!-- PPTX -->
            <VuePptx
                v-else-if="fileCategory === 'ppt'"
                :src="fileUrl"
                @on-error="handleLoadError"
            />

            <!-- Image -->
            <div v-else-if="fileCategory === 'image'" class="image-container">
                <img :src="fileUrl" :alt="fileName" @error="handleLoadError" />
            </div>

            <!-- Video -->
            <div v-else-if="fileCategory === 'video'" class="media-container">
                <video controls :src="fileUrl" @error="handleLoadError">
                    {{ t('filePreview.unsupported') }}
                </video>
            </div>

            <!-- Audio -->
            <div v-else-if="fileCategory === 'audio'" class="media-container">
                <div class="audio-wrapper">
                    <Music2 :size="64" :stroke-width="0.8" />
                    <audio controls :src="fileUrl" @error="handleLoadError">
                        {{ t('filePreview.unsupported') }}
                    </audio>
                </div>
            </div>

            <!-- Text / Code -->
            <div v-else-if="fileCategory === 'text' || fileCategory === 'code'" class="text-container">
                <pre class="text-content"><code>{{ textContent }}</code></pre>
            </div>

            <!-- Markdown -->
            <div v-else-if="fileCategory === 'markdown'" class="markdown-container">
                <article class="markdown-body" v-html="markdownHtml"></article>
            </div>

            <!-- Unsupported -->
            <div v-else class="preview-status">
                <FileQuestion :size="48" :stroke-width="1" />
                <p>{{ t('filePreview.unsupported') }}</p>
                <a v-if="fileUrl" :href="fileUrl" download class="btn-primary">
                    <Download :size="16" :stroke-width="1.5" />
                    {{ t('filePreview.download') }}
                </a>
            </div>
        </main>
    </div>
</template>

<script setup lang="ts">
import {ref, computed, onMounted} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import {useI18n} from 'vue-i18n'
import {ArrowLeft, Download, AlertCircle, FileQuestion, Music2} from 'lucide-vue-next'
import VuePdf from '@vue-office/pdf'
import VueDocx from '@vue-office/docx'
import '@vue-office/docx/lib/index.css'
import VueExcel from '@vue-office/excel'
import '@vue-office/excel/lib/index.css'
import VuePptx from '@vue-office/pptx'
import {getDownloadUrl} from '@/features/storage/api/storage'
import {marked} from 'marked'

const route = useRoute()
const router = useRouter()
const {t} = useI18n()

const fileId = computed(() => (route.query.fileId as string) || '')
const directUrl = computed(() => (route.query.url as string) || '')
const fileName = computed(() => (route.query.fileName as string) || 'file')

const fileUrl = ref('')
const loading = ref(true)
const error = ref('')
const textContent = ref('')
const markdownHtml = ref('')

// File extension to category mapping
const EXTENSION_CATEGORY: Record<string, FileCategory> = {
    '.pdf': 'pdf',
    '.docx': 'word',
    '.xlsx': 'excel',
    '.xls': 'excel',
    '.xlsb': 'excel',
    '.csv': 'excel',
    '.png': 'image',
    '.jpg': 'image',
    '.jpeg': 'image',
    '.gif': 'image',
    '.svg': 'image',
    '.webp': 'image',
    '.bmp': 'image',
    '.mp4': 'video',
    '.webm': 'video',
    '.ogg': 'video',
    '.mp3': 'audio',
    '.wav': 'audio',
    '.aac': 'audio',
    '.flac': 'audio',
    '.txt': 'text',
    '.json': 'text',
    '.xml': 'text',
    '.md': 'markdown',
    '.markdown': 'markdown',
    '.yaml': 'text',
    '.yml': 'text',
    '.toml': 'text',
    '.ini': 'text',
    '.log': 'text',
    '.py': 'code',
    '.java': 'code',
    '.js': 'code',
    '.ts': 'code',
    '.html': 'code',
    '.css': 'code',
    '.sql': 'code',
    '.sh': 'code',
    '.vue': 'code',
    '.jsx': 'code',
    '.tsx': 'code',
    '.c': 'code',
    '.cpp': 'code',
    '.go': 'code',
    '.rs': 'code',
    '.rb': 'code',
    '.php': 'code',
    '.swift': 'code',
    '.kt': 'code',
    '.pptx': 'ppt',
}

type FileCategory = 'pdf' | 'word' | 'excel' | 'image' | 'video' | 'audio' | 'text' | 'code' | 'ppt' | 'markdown' | 'unknown'

function getExtension(name: string): string {
    const idx = name.lastIndexOf('.')
    return idx >= 0 ? name.slice(idx).toLowerCase() : ''
}

const fileCategory = computed<FileCategory>(() => {
    const ext = getExtension(fileName.value)
    return EXTENSION_CATEGORY[ext] ?? 'unknown'
})

const fileTypeLabel = computed(() => {
    const key = `filePreview.fileType.${fileCategory.value}`
    return t(key)
})

function handleLoadError() {
    loading.value = false
    error.value = t('filePreview.previewFailed')
}


function goBack() {
    if (window.history.length > 1) {
        router.back()
    } else {
        router.push({name: 'dashboard'})
    }
}

async function resolveFileUrl(): Promise<string> {
    // Priority: fileId (fresh pre-signed URL) > direct url (backward compat)
    if (fileId.value) {
        const resp = await getDownloadUrl(fileId.value)
        return resp.url
    }
    return directUrl.value
}

async function loadTextFile() {
    if (fileCategory.value !== 'text' && fileCategory.value !== 'code') return
    try {
        const resp = await fetch(fileUrl.value)
        if (!resp.ok) throw new Error(resp.statusText)
        textContent.value = await resp.text()
        loading.value = false
    } catch {
        error.value = t('filePreview.loadError')
        loading.value = false
    }
}

async function loadMarkdownFile() {
    try {
        const resp = await fetch(fileUrl.value)
        if (!resp.ok) throw new Error(resp.statusText)
        const raw = await resp.text()
        markdownHtml.value = await marked.parse(raw)
        loading.value = false
    } catch {
        error.value = t('filePreview.loadError')
        loading.value = false
    }
}

async function initPreview() {
    loading.value = true
    error.value = ''
    textContent.value = ''
    markdownHtml.value = ''

    try {
        fileUrl.value = await resolveFileUrl()
    } catch {
        error.value = t('filePreview.fileNotFound')
        loading.value = false
        return
    }

    if (!fileUrl.value) {
        error.value = t('filePreview.fileNotFound')
        loading.value = false
        return
    }

    // For text/code files, fetch content manually
    if (fileCategory.value === 'text' || fileCategory.value === 'code') {
        loadTextFile()
        return
    }

    // For markdown files, fetch and render to HTML
    if (fileCategory.value === 'markdown') {
        loadMarkdownFile()
        return
    }

    // For unsupported formats, don't show loading
    if (fileCategory.value === 'unknown') {
        loading.value = false
        return
    }

    // vue-office components don't reliably fire @rendered, use timeout fallback
    setTimeout(() => {
        loading.value = false
    }, 2000)
}

onMounted(() => {
    initPreview()
})
</script>

<style scoped>
.file-preview-page {
    display: flex;
    flex-direction: column;
    height: calc(100vh - 60px);
    background: var(--color-surface-canvas);
}

.page-header {
    margin-bottom: 32px;
}

.page-header-row {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    gap: 24px;
    flex-wrap: wrap;
}

.page-title {
    margin: 0 0 8px;
    font-family: var(--font-heading);
    font-size: 48px;
    font-weight: 400;
    line-height: 1.3;
    color: var(--color-on-surface);
    letter-spacing: 0;
}

.page-subtitle {
    margin: 0;
    font-family: var(--font-body);
    font-size: 16px;
    font-weight: 400;
    line-height: 1.6;
    color: var(--color-muted);
    max-width: 672px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.page-header-actions {
    display: flex;
    gap: 12px;
    align-items: center;
    flex-shrink: 0;
}

.btn-back {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 6px 14px;
    border: 1px solid var(--color-outline-light);
    border-radius: var(--radius-pill);
    background: transparent;
    color: var(--color-on-surface);
    font-family: var(--font-label);
    font-size: 13px;
    cursor: pointer;
    transition: background 0.15s;
}

.btn-back:hover {
    background: var(--color-surface-container);
}

.btn-download {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 6px 14px;
    background: var(--color-primary);
    color: var(--color-on-primary);
    border-radius: var(--radius-pill);
    font-family: var(--font-label);
    font-size: 13px;
    text-decoration: none;
    transition: background 0.15s;
}

.btn-download:hover {
    background: var(--color-primary-soft);
}

.btn-primary {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 8px 20px;
    background: var(--color-primary);
    color: var(--color-on-primary);
    border-radius: var(--radius-pill);
    font-family: var(--font-label);
    font-size: 14px;
    text-decoration: none;
    transition: background 0.15s;
    margin-top: 16px;
}

.btn-primary:hover {
    background: var(--color-primary-soft);
}

.preview-content {
    flex: 1;
    overflow: auto;
    position: relative;
}

/* Loading & Error states */
.preview-status {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 16px;
    height: 100%;
    color: var(--color-muted);
    font-family: var(--font-body);
    font-size: 15px;
}

.spinner {
    width: 36px;
    height: 36px;
    border: 3px solid var(--color-outline-light);
    border-top-color: var(--color-primary);
    border-radius: 50%;
    animation: spin 0.8s linear infinite;
}

@keyframes spin {
    to {
        transform: rotate(360deg);
    }
}

/* Image preview */
.image-container {
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 24px;
    height: 100%;
}

.image-container img {
    max-width: 100%;
    max-height: 100%;
    object-fit: contain;
    border-radius: var(--radius-sm);
}

/* Video preview */
.media-container {
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 24px;
    height: 100%;
}

.media-container video {
    max-width: 100%;
    max-height: 100%;
    border-radius: var(--radius-sm);
    background: #000;
}

/* Audio preview */
.audio-wrapper {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 24px;
    padding: 48px;
    background: var(--color-surface-card);
    border: 1px solid var(--color-outline-light);
    border-radius: var(--radius-md);
    min-width: 360px;
}

.audio-wrapper audio {
    width: 100%;
}

/* Text / Code preview */
.text-container {
    height: 100%;
    overflow: auto;
    padding: 24px;
}

.text-content {
    margin: 0;
    padding: 24px;
    background: var(--color-surface-card);
    border: 1px solid var(--color-outline-light);
    border-radius: var(--radius-sm);
    font-family: 'Cascadia Code', 'Fira Code', 'JetBrains Mono', 'Consolas', monospace;
    font-size: 13px;
    line-height: 1.7;
    color: var(--color-on-surface);
    white-space: pre-wrap;
    word-break: break-word;
    tab-size: 4;
    overflow-x: auto;
}

/* PDF container override */
.preview-content :deep(.vue-pdf-container) {
    height: 100%;
}

/* ========== Markdown Whitey Theme (System Tokens) ========== */
.markdown-container {
    height: 100%;
    overflow: auto;
    padding: var(--space-lg) var(--space-md);
}

.markdown-body {
    max-width: 860px;
    margin: 0 auto;
    padding: 0 0 var(--space-xl);
    font-family: var(--font-body);
    font-size: 16px;
    line-height: 1.8;
    color: var(--color-on-surface);
    word-wrap: break-word;
}

/* Headings — Whitey: clean sans-serif, bottom rule */
.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3),
.markdown-body :deep(h4),
.markdown-body :deep(h5),
.markdown-body :deep(h6) {
    font-family: var(--font-heading);
    font-weight: 400;
    color: var(--color-on-surface);
    margin-top: 1.4em;
    margin-bottom: 0.6em;
    line-height: 1.3;
}

.markdown-body :deep(h1) {
    font-size: 2em;
    padding-bottom: 0.3em;
    border-bottom: 2px solid var(--color-outline-light);
}

.markdown-body :deep(h2) {
    font-size: 1.6em;
    padding-bottom: 0.25em;
    border-bottom: 1px solid var(--color-outline-light);
}

.markdown-body :deep(h3) { font-size: 1.3em; }
.markdown-body :deep(h4) { font-size: 1.15em; }
.markdown-body :deep(h5) { font-size: 1em; }
.markdown-body :deep(h6) { font-size: 0.9em; color: var(--color-muted); }

/* First heading no top margin */
.markdown-body :deep(h1:first-child) { margin-top: 0; }

/* Paragraphs */
.markdown-body :deep(p) {
    margin: 0 0 1em;
}

/* Links */
.markdown-body :deep(a) {
    color: var(--color-primary);
    text-decoration: none;
    border-bottom: 1px solid transparent;
    transition: border-color 0.2s;
}

.markdown-body :deep(a:hover) {
    border-bottom-color: var(--color-primary);
}

/* Strong / Em */
.markdown-body :deep(strong) { font-weight: 700; color: var(--color-on-surface); }
.markdown-body :deep(em) { font-style: italic; }

/* Blockquote — Whitey: left bar, muted text */
.markdown-body :deep(blockquote) {
    margin: 1em 0;
    padding: 0.5em 1.2em;
    border-left: 4px solid var(--color-outline);
    color: var(--color-muted);
    background: transparent;
}

.markdown-body :deep(blockquote p:last-child) {
    margin-bottom: 0;
}

/* Horizontal rule */
.markdown-body :deep(hr) {
    border: none;
    border-top: 1px solid var(--color-outline-light);
    margin: 2em 0;
}

/* Lists */
.markdown-body :deep(ul),
.markdown-body :deep(ol) {
    margin: 0 0 1em;
    padding-left: 2em;
}

.markdown-body :deep(li) {
    margin-bottom: 0.3em;
}

.markdown-body :deep(li > ul),
.markdown-body :deep(li > ol) {
    margin-top: 0.3em;
    margin-bottom: 0;
}

/* Inline code */
.markdown-body :deep(code) {
    font-family: 'Cascadia Code', 'Fira Code', 'JetBrains Mono', Consolas, monospace;
    font-size: 0.88em;
    padding: 0.15em 0.4em;
    background: var(--color-surface-container);
    border-radius: var(--radius-sm);
    color: var(--color-on-surface);
}

/* Code blocks — Whitey: clean light background */
.markdown-body :deep(pre) {
    margin: 1em 0;
    padding: 1em 1.2em;
    background: var(--color-surface-container);
    border: 1px solid var(--color-outline-light);
    border-radius: var(--radius-sm);
    overflow-x: auto;
    line-height: 1.6;
}

.markdown-body :deep(pre code) {
    font-size: 0.88em;
    padding: 0;
    background: transparent;
    border-radius: 0;
    color: var(--color-on-surface);
}

/* Tables — Whitey: clean borders, minimal */
.markdown-body :deep(table) {
    width: 100%;
    margin: 1em 0;
    border-collapse: collapse;
    border-spacing: 0;
}

.markdown-body :deep(th),
.markdown-body :deep(td) {
    padding: 0.5em 0.8em;
    border: 1px solid var(--color-outline-light);
    text-align: left;
}

.markdown-body :deep(th) {
    font-weight: 600;
    background: var(--color-surface-container);
    font-family: var(--font-label);
    font-size: 0.92em;
}

.markdown-body :deep(tr:nth-child(even)) {
    background: var(--color-surface-container);
}

/* Images */
.markdown-body :deep(img) {
    max-width: 100%;
    height: auto;
    margin: 0.5em 0;
    border-radius: var(--radius-sm);
}

/* Task lists */
.markdown-body :deep(input[type='checkbox']) {
    margin-right: 0.4em;
}

.markdown-body :deep(li.task-list-item) {
    list-style: none;
    margin-left: -1.5em;
}

/* Responsive */
@media (max-width: 768px) {
    .page-header {
        margin-bottom: 24px;
    }

    .page-header-row {
        flex-direction: column;
    }

    .page-title {
        font-size: 32px;
    }

    .page-subtitle {
        font-size: 14px;
    }

    .page-header-actions {
        width: 100%;
        justify-content: flex-start;
    }

    .btn-back span,
    .btn-download span {
        display: none;
    }

    .audio-wrapper {
        min-width: auto;
        width: 100%;
        padding: 24px;
    }

    .markdown-container {
        padding: var(--space-sm) var(--space-xs);
    }

    .markdown-body {
        font-size: 15px;
    }
}
</style>
