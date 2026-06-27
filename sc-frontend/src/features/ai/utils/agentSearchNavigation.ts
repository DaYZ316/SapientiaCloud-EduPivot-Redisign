import type {RouteLocationRaw} from 'vue-router'

import type {AgentSearchItem} from '@/features/ai/types/ai'

export interface AgentSearchNavigation {
    label: string
    to: RouteLocationRaw
}

export function resolveAgentSearchNavigation(item: AgentSearchItem): AgentSearchNavigation | null {
    const sourceType = item.sourceType || ''
    const sourceId = textValue(item.sourceId)
    const courseId = textValue(item.courseId)
    const metadata = item.metadata || {}
    const indexInfo = item.indexInfo || {}
    const sourceIdFromIndex = textValue(indexInfo.sourceId) || sourceId
    const courseIdFromIndex = textValue(indexInfo.courseId) || courseId

    if (sourceType === 'COURSE') {
        const id = sourceIdFromIndex || courseIdFromIndex
        return id ? route('打开课程', {name: 'course-overview', params: {id}}) : null
    }

    if (sourceType === 'CHAPTER') {
        return courseIdFromIndex
            ? route('打开章节', {
                name: 'chapter-detail',
                params: {courseId: courseIdFromIndex},
                query: sourceIdFromIndex ? {chapterId: sourceIdFromIndex} : undefined,
            })
            : null
    }

    if (sourceType === 'QUESTION_BANK') {
        const bankId = sourceIdFromIndex
        return bankId ? route('打开题库', {name: 'question-bank-detail', params: {id: bankId}}) : null
    }

    if (sourceType === 'QUESTION') {
        const questionBankId = textValue(metadata.questionBankId) || textValue(indexInfo.questionBankId)
        return questionBankId
            ? route('打开题目', {
                name: 'question-bank-detail',
                params: {id: questionBankId},
                query: sourceIdFromIndex ? {questionId: sourceIdFromIndex} : undefined,
            })
            : null
    }

    if (sourceType === 'COURSE_FILE') {
        const fileId = textValue(metadata.storageObjectId) || textValue(indexInfo.storageObjectId) || sourceIdFromIndex
        return fileId
            ? route('预览资料', {
                name: 'file-preview',
                query: {
                    fileId,
                    ...(item.title ? {fileName: item.title} : {}),
                },
            })
            : null
    }

    if (sourceType === 'LIVE_PRACTICE') {
        const groupId = textValue(metadata.groupId) || textValue(indexInfo.groupId) || sourceIdFromIndex
        return courseIdFromIndex && groupId
            ? route('打开课堂练习', {
                name: 'course-live-practice-detail',
                params: {courseId: courseIdFromIndex, groupId},
            })
            : null
    }

    if (sourceType === 'CHAT_MEMORY') {
        const conversationId = textValue(metadata.conversationId) || textValue(indexInfo.conversationId)
        return conversationId
            ? route('打开会话', {name: 'ai-workspace', query: {conversationId}})
            : null
    }

    return null
}

function route(label: string, to: RouteLocationRaw): AgentSearchNavigation {
    return {label, to}
}

function textValue(value: unknown) {
    return typeof value === 'string' && value.trim() ? value.trim() : ''
}
