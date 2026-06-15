<template>
    <Teleport to="body">
        <div v-if="modelValue" class="modal-overlay" @click.self="close">
            <div class="modal-content">
                <h3>{{ isAdmin ? t('courseDetail.addAssistant') : t('courseDetail.inviteAssistant') }}</h3>

                <div class="teacher-search">
                    <Search :size="14" stroke-width="1.8"/>
                    <input
                        v-model="searchKeyword"
                        type="text"
                        :placeholder="t('courseDetail.searchTeacherPlaceholder')"
                        @input="handleSearch"
                    />
                </div>

                <div
                    ref="teacherListRef"
                    class="teacher-list"
                    @scroll="handleScroll"
                    @wheel="handleWheel"
                >
                    <div v-if="loading && teachers.length === 0" class="teacher-status">
                        {{ t('courseDetail.loadingTeachers') }}
                    </div>

                    <div v-else-if="teachers.length === 0" class="teacher-status">
                        {{ t('courseDetail.noTeachersFound') }}
                    </div>

                    <template v-else>
                        <div
                            v-for="teacher in teachers"
                            :key="teacher.id"
                            class="teacher-card"
                            :class="{inviting: invitingTeacherId === teacher.id}"
                        >
                            <div class="teacher-card-main">
                                <UserAvatarLink
                                    :user-id="teacher.id"
                                    :display-name="teacher.displayName"
                                    :avatar-url="teacher.avatarUrl"
                                    :role="2"
                                    size="small"
                                    :show-name="false"
                                />
                                <div class="teacher-info">
                                    <strong>{{ teacher.displayName || teacher.id }}</strong>
                                    <span v-if="teacher.email">{{ teacher.email }}</span>
                                </div>
                                <div class="teacher-actions">
                                    <!-- 已有助教 -->
                                    <span v-if="assistantIdSet.has(teacher.id)" class="status-badge enrolled">
                                        {{ t('courseDetail.alreadyAssistant') }}
                                    </span>
                                    <!-- 待处理邀请 -->
                                    <span v-else-if="pendingInviteeIds.has(teacher.id)" class="status-badge pending">
                                        {{ t('courseDetail.alreadyInvited') }}
                                    </span>
                                    <!-- Admin: 直接添加 -->
                                    <button
                                        v-else-if="isAdmin"
                                        class="btn-teacher-action add"
                                        type="button"
                                        :disabled="actionLoadingId === teacher.id"
                                        @click="handleDirectAdd(teacher)"
                                    >
                                        {{ actionLoadingId === teacher.id ? '...' : t('courseDetail.add') }}
                                    </button>
                                    <!-- Teacher: 邀请 -->
                                    <button
                                        v-else-if="invitingTeacherId !== teacher.id"
                                        class="btn-teacher-action invite"
                                        type="button"
                                        :disabled="invitingTeacherId !== null"
                                        @click="startInvite(teacher)"
                                    >
                                        {{ t('courseDetail.invite') }}
                                    </button>
                                </div>
                            </div>
                            <!-- Teacher: 展开的留言区域 -->
                            <div v-if="invitingTeacherId === teacher.id" class="invite-expand">
                                <textarea
                                    v-model="inviteMessage"
                                    rows="2"
                                    maxlength="500"
                                    :placeholder="t('courseDetail.inviteMessagePlaceholder')"
                                ></textarea>
                                <div class="invite-expand-actions">
                                    <button type="button" class="btn-cancel-sm" @click="cancelInvite">
                                        {{ t('courseDetail.cancel') }}
                                    </button>
                                    <button
                                        type="button"
                                        class="btn-send"
                                        :disabled="actionLoadingId !== null"
                                        @click="handleSendInvite"
                                    >
                                        {{ actionLoadingId === teacher.id ? '...' : t('courseDetail.sendInvite') }}
                                    </button>
                                </div>
                            </div>
                        </div>

                        <div v-if="loading" class="teacher-status">
                            {{ t('courseDetail.loadingTeachers') }}
                        </div>
                        <div v-else-if="!hasMore && teachers.length > 0" class="teacher-status done">
                            {{ t('courseDetail.allTeachersLoaded') }}
                        </div>
                    </template>
                </div>

                <div class="modal-actions">
                    <button type="button" class="btn-cancel" @click="close">
                        {{ t('courseDetail.close') }}
                    </button>
                </div>
            </div>
        </div>
    </Teleport>
</template>

<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {Search} from 'lucide-vue-next'
import {sendInvitation, getSentInvitations, withdrawInvitation} from '@/features/course/api/invitation'
import {getCourse} from '@/features/course/api/course'
import {listTeachers} from '@/features/user/api/user'
import {notify} from '@/shared/composables/useGlobalNotification'
import UserAvatarLink from '@/shared/components/UserAvatarLink.vue'
import type {CourseInvitation} from '@/features/course/types/invitation'
import type {UserProfile} from '@/features/user/types/user'

type TeacherInfo = {id: string; displayName?: string | null; avatarUrl?: string | null}

const props = defineProps<{
    modelValue: boolean
    courseId: string
    courseTeacherId: string
    assistants: TeacherInfo[]
    isAdmin?: boolean
}>()

const emit = defineEmits<{
    'update:modelValue': [value: boolean]
    invited: []
}>()

const {t} = useI18n()

const PAGE_SIZE = 20
const teachers = ref<UserProfile[]>([])
const searchKeyword = ref('')
const page = ref(1)
const hasMore = ref(true)
const loading = ref(false)
const teacherListRef = ref<HTMLElement | null>(null)
let loadMoreArmed = true

const actionLoadingId = ref<string | null>(null)
const invitingTeacherId = ref<string | null>(null)
const inviteMessage = ref('')

const pendingInvitations = ref<CourseInvitation[]>([])
const fetchedAssistants = ref<TeacherInfo[]>([])

// 优先用 fetch 到的完整数据，回退到 props
const assistantIdSet = computed(() => {
    const source = fetchedAssistants.value.length > 0 ? fetchedAssistants.value : props.assistants
    return new Set(source.map(a => a.id))
})
const pendingInviteeIds = computed(() => new Set(pendingInvitations.value.map(inv => inv.inviteeId)))

// 打开时加载数据
watch(() => props.modelValue, (open) => {
    if (open) {
        void loadCourseAssistants()
        void loadPendingInvitations()
        if (teachers.value.length === 0) {
            void loadTeachers(true)
        }
    }
})

async function loadCourseAssistants() {
    try {
        const detail = await getCourse(props.courseId)
        const teacherId = detail.teacherId
        fetchedAssistants.value = (detail.teacherInfos || []).filter(info => info.id !== teacherId)
    } catch {
        fetchedAssistants.value = []
    }
}

async function loadPendingInvitations() {
    if (props.isAdmin) return
    try {
        const resp = await getSentInvitations({status: 0, size: 100})
        pendingInvitations.value = (resp.records || []).filter(inv => inv.courseId === props.courseId)
    } catch {
        pendingInvitations.value = []
    }
}

async function loadTeachers(reset = false) {
    if (loading.value) return
    if (!reset && !hasMore.value) return

    loading.value = true
    try {
        if (reset) {
            page.value = 1
            hasMore.value = true
            loadMoreArmed = true
        }

        const resp = await listTeachers({
            page: page.value,
            size: PAGE_SIZE,
            keyword: searchKeyword.value.trim() || undefined,
        })

        const filtered = (resp.records || []).filter(u => u.id !== props.courseTeacherId)

        if (reset) {
            teachers.value = filtered
        } else {
            teachers.value = [...teachers.value, ...filtered]
        }

        hasMore.value = resp.page * resp.size < resp.total
        page.value++
    } catch {
        if (reset) teachers.value = []
    } finally {
        loading.value = false
    }
}

// 搜索防抖
let searchTimer: ReturnType<typeof setTimeout> | null = null

function handleSearch() {
    if (searchTimer) clearTimeout(searchTimer)
    searchTimer = setTimeout(() => {
        void loadTeachers(true)
    }, 300)
}

// 滚动加载
function handleScroll() {
    const el = teacherListRef.value
    if (!el || loading.value || !hasMore.value) return

    const {scrollTop, scrollHeight, clientHeight} = el
    const isNearBottom = scrollHeight - scrollTop - clientHeight < 80
    if (!isNearBottom) {
        loadMoreArmed = true
        return
    }

    if (!loadMoreArmed) return
    loadMoreArmed = false
    void loadTeachers(false)
}

function handleWheel(e: WheelEvent) {
    const el = teacherListRef.value
    if (!el) return

    const {scrollTop, scrollHeight, clientHeight} = el
    const atTop = scrollTop <= 0 && e.deltaY < 0
    const atBottom = scrollHeight - scrollTop - clientHeight <= 1 && e.deltaY > 0

    if (atTop || atBottom) {
        e.preventDefault()
    }
}

// Admin: 直接添加
async function handleDirectAdd(teacher: UserProfile) {
    if (!confirm(t('courseDetail.confirmAddAssistant'))) return
    actionLoadingId.value = teacher.id
    try {
        await sendInvitation({courseId: props.courseId, inviteeId: teacher.id})
        notify.success(t('courseDetail.assistantAdded'))
        teachers.value = teachers.value.filter(u => u.id !== teacher.id)
        emit('invited')
    } catch {
        notify.error(t('courseDetail.saveChapterFailed'))
    } finally {
        actionLoadingId.value = null
    }
}

function startInvite(teacher: UserProfile) {
    invitingTeacherId.value = teacher.id
    inviteMessage.value = ''
}

function cancelInvite() {
    invitingTeacherId.value = null
    inviteMessage.value = ''
}

async function handleSendInvite() {
    if (!invitingTeacherId.value) return
    actionLoadingId.value = invitingTeacherId.value
    try {
        await sendInvitation({
            courseId: props.courseId,
            inviteeId: invitingTeacherId.value,
            message: inviteMessage.value || undefined,
        })
        notify.success(t('courseDetail.invitationSent'))
        teachers.value = teachers.value.filter(u => u.id !== invitingTeacherId.value)
        invitingTeacherId.value = null
        inviteMessage.value = ''
        await loadPendingInvitations()
        emit('invited')
    } catch {
        notify.error(t('courseDetail.saveChapterFailed'))
    } finally {
        actionLoadingId.value = null
    }
}

function close() {
    emit('update:modelValue', false)
    searchKeyword.value = ''
    teachers.value = []
    fetchedAssistants.value = []
    page.value = 1
    hasMore.value = true
    loadMoreArmed = true
    invitingTeacherId.value = null
    inviteMessage.value = ''
}
</script>

<style scoped>
.modal-overlay {
    position: fixed;
    inset: 0;
    display: grid;
    place-items: center;
    background: rgba(0, 0, 0, 0.4);
    z-index: 1000;
}

.modal-content {
    display: flex;
    flex-direction: column;
    width: min(520px, 90vw);
    max-height: calc(100dvh - 48px);
    padding: 24px;
    background: var(--color-surface-card);
    border: 1px solid var(--color-outline-light);
    border-radius: var(--radius-md);
}

.modal-content h3 {
    margin: 0 0 16px;
    color: var(--color-on-surface);
    font-family: var(--font-heading);
    font-size: 20px;
    font-weight: 400;
}

/* 搜索框 */
.teacher-search {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 8px 12px;
    margin-bottom: 12px;
    background: var(--color-surface-container);
    border: 1px solid var(--color-outline-light);
    border-radius: var(--radius-sm);
    color: var(--color-muted);
}

.teacher-search input {
    flex: 1;
    min-width: 0;
    padding: 0;
    background: transparent;
    border: 0;
    outline: none;
    color: var(--color-on-surface);
    font-family: var(--font-body);
    font-size: 13px;
}

.teacher-search input::placeholder {
    color: var(--color-muted);
}

/* 教师列表 */
.teacher-list {
    display: flex;
    flex-direction: column;
    gap: 6px;
    max-height: 400px;
    overflow-y: auto;
    padding-right: 4px;
    margin-bottom: 16px;
}

.teacher-list::-webkit-scrollbar {
    width: 4px;
}

.teacher-list::-webkit-scrollbar-track {
    background: transparent;
}

.teacher-list::-webkit-scrollbar-thumb {
    background: var(--color-outline-light);
    border-radius: 999px;
}

.teacher-card {
    border: 1px solid var(--color-outline-light);
    border-radius: var(--radius-sm);
    background: var(--color-surface-card);
    transition: background 0.15s ease;
}

.teacher-card:hover {
    background: var(--color-surface-container);
}

.teacher-card.inviting {
    border-color: var(--color-outline);
    background: var(--color-surface-container);
}

.teacher-card-main {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 8px 10px;
}

.teacher-info {
    min-width: 0;
    flex: 1;
}

.teacher-info strong {
    display: block;
    overflow: hidden;
    color: var(--color-on-surface);
    font-family: var(--font-body);
    font-size: 13px;
    font-weight: 400;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.teacher-info span {
    display: block;
    overflow: hidden;
    color: var(--color-muted);
    font-family: var(--font-body);
    font-size: 12px;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.teacher-actions {
    flex: 0 0 auto;
}

.btn-teacher-action {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-height: 28px;
    padding: 0 10px;
    border: 1px solid var(--color-outline-light);
    border-radius: var(--radius-sm);
    background: transparent;
    color: var(--color-on-surface);
    cursor: pointer;
    font-family: var(--font-label);
    font-size: 11px;
    font-weight: 700;
    transition: background 0.15s ease, border-color 0.15s ease, color 0.15s ease;
}

.btn-teacher-action:hover:not(:disabled) {
    background: var(--color-surface-container-high);
    border-color: var(--color-outline);
}

.btn-teacher-action.add:hover:not(:disabled),
.btn-teacher-action.invite:hover:not(:disabled) {
    border-color: var(--color-primary);
    color: var(--color-primary);
}

.btn-teacher-action:disabled {
    opacity: 0.4;
    cursor: not-allowed;
}

/* 状态标签 */
.status-badge {
    display: inline-flex;
    align-items: center;
    height: 28px;
    padding: 0 10px;
    border-radius: var(--radius-sm);
    font-family: var(--font-label);
    font-size: 11px;
    font-weight: 700;
    white-space: nowrap;
}

.status-badge.enrolled {
    background: var(--color-surface-container-high);
    border: 1px solid var(--color-outline-light);
    color: var(--color-muted);
}

.status-badge.pending {
    background: var(--color-surface-container);
    border: 1px solid var(--color-outline-light);
    color: var(--color-muted);
}

/* 邀请展开区域 */
.invite-expand {
    display: flex;
    flex-direction: column;
    gap: 8px;
    padding: 0 10px 10px;
}

.invite-expand textarea {
    padding: 8px 10px;
    border: 1px solid var(--color-outline-light);
    border-radius: var(--radius-sm);
    background: var(--color-surface-card);
    color: var(--color-on-surface);
    font-family: var(--font-body);
    font-size: 13px;
    resize: vertical;
}

.invite-expand textarea:focus {
    outline: none;
    border-color: var(--color-primary);
}

.invite-expand textarea::placeholder {
    color: var(--color-muted);
}

.invite-expand-actions {
    display: flex;
    gap: 8px;
    justify-content: flex-end;
}

.btn-cancel-sm {
    padding: 5px 12px;
    border: 1px solid var(--color-outline-light);
    border-radius: var(--radius-sm);
    background: transparent;
    color: var(--color-muted);
    cursor: pointer;
    font-family: var(--font-label);
    font-size: 11px;
    font-weight: 400;
}

.btn-cancel-sm:hover {
    background: var(--color-surface-container-high);
    border-color: var(--color-outline);
}

.btn-send {
    padding: 5px 14px;
    border: none;
    border-radius: var(--radius-sm);
    background: var(--color-primary);
    color: var(--color-on-primary);
    cursor: pointer;
    font-family: var(--font-label);
    font-size: 11px;
    font-weight: 700;
}

.btn-send:disabled {
    opacity: 0.5;
    cursor: not-allowed;
}

.teacher-status {
    padding: 18px;
    text-align: center;
    color: var(--color-muted);
    font-family: var(--font-body);
    font-size: 13px;
}

.teacher-status.done {
    font-size: 11px;
    opacity: 0.7;
    padding: 10px;
}

/* 底部按钮 */
.modal-actions {
    display: flex;
    gap: 10px;
    justify-content: flex-end;
}

.btn-cancel {
    padding: 10px 20px;
    border: 1px solid var(--color-outline-light);
    border-radius: var(--radius-sm);
    background: transparent;
    color: var(--color-muted);
    font-family: var(--font-label);
    font-size: 13px;
    font-weight: 400;
    cursor: pointer;
}

.btn-cancel:hover {
    background: var(--color-surface-container);
    border-color: var(--color-outline);
}
</style>
