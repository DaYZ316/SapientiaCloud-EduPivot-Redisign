import {computed, ref} from 'vue'
import {defineStore} from 'pinia'

import {deleteLiveSummaryHistoryRecord, listLiveSummaryRecords} from '@/features/ai/api/ai'
import type {LiveSummaryRecord} from '@/features/ai/types/ai'

const PAGE_SIZE = 20

export const useLiveSummaryLibraryStore = defineStore('liveSummaryLibrary', () => {
    const records = ref<LiveSummaryRecord[]>([])
    const activeRecordId = ref<string | null>(null)
    const loading = ref(false)
    const loadingMore = ref(false)
    const page = ref(1)
    const total = ref(0)
    const loaded = ref(false)
    let loadPromise: Promise<void> | null = null

    const activeRecord = computed(() =>
        records.value.find(record => record.id === activeRecordId.value) ?? records.value[0] ?? null,
    )
    const hasMore = computed(() => records.value.length < total.value)

    async function loadRecords(options: { page?: number; append?: boolean; silent?: boolean } = {}) {
        const nextPage = options.page ?? 1
        if (!options.silent) {
            loading.value = true
        }
        try {
            const response = await listLiveSummaryRecords(nextPage, PAGE_SIZE)
            records.value = options.append
                ? appendRecords(records.value, response.records)
                : response.records
            page.value = response.page
            total.value = response.total
            loaded.value = true
            if (!activeRecordId.value && records.value.length > 0) {
                activeRecordId.value = records.value[0].id
            }
            if (activeRecordId.value && !records.value.some(record => record.id === activeRecordId.value)) {
                activeRecordId.value = records.value[0]?.id ?? null
            }
        } finally {
            if (!options.silent) {
                loading.value = false
            }
        }
    }

    async function ensureLoaded() {
        if (loaded.value) return
        loadPromise ??= loadRecords().finally(() => {
            loadPromise = null
        })
        await loadPromise
    }

    async function loadMoreRecords() {
        if (loadingMore.value || !hasMore.value) return
        loadingMore.value = true
        try {
            await loadRecords({append: true, page: page.value + 1, silent: true})
        } finally {
            loadingMore.value = false
        }
    }

    function selectRecord(recordId: string) {
        activeRecordId.value = recordId
    }

    async function deleteRecord(record: LiveSummaryRecord) {
        await deleteLiveSummaryHistoryRecord(record.classSessionId, record.id)
        records.value = records.value.filter(item => item.id !== record.id)
        total.value = Math.max(0, total.value - 1)
        if (activeRecordId.value === record.id) {
            activeRecordId.value = records.value[0]?.id ?? null
        }
    }

    function appendRecords(current: LiveSummaryRecord[], next: LiveSummaryRecord[]) {
        const currentIds = new Set(current.map(record => record.id))
        return current.concat(next.filter(record => !currentIds.has(record.id)))
    }

    return {
        records,
        activeRecordId,
        activeRecord,
        loading,
        loadingMore,
        loaded,
        hasMore,
        ensureLoaded,
        loadRecords,
        loadMoreRecords,
        selectRecord,
        deleteRecord,
    }
})
