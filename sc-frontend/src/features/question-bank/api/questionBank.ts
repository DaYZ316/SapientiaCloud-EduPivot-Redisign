import {request} from '@/shared/api/request'
import type {PageResponse} from '@/shared/types/common'
import type {
    CreateQuestionBankRequest,
    CreateQuestionRequest,
    Question,
    QuestionBank,
    UpdateQuestionBankRequest,
    UpdateQuestionRequest,
} from '@/features/question-bank/types/questionBank'

export function getQuestionBanks(params: { courseId?: string; keyword?: string; page?: number; size?: number } = {}) {
    const searchParams = new URLSearchParams()
    if (params.courseId) searchParams.append('courseId', params.courseId)
    if (params.keyword) searchParams.append('keyword', params.keyword)
    if (params.page) searchParams.append('page', params.page.toString())
    if (params.size) searchParams.append('size', params.size.toString())
    return request<PageResponse<QuestionBank>>({method: 'GET', url: "/api/question-banks?" + searchParams.toString()})
}

export function getQuestionBank(id: string) {
    return request<QuestionBank>({method: 'GET', url: "/api/question-banks/" + id})
}

export function getCourseQuestionBanks(courseId: string) {
    return request<QuestionBank[]>({method: 'GET', url: "/api/question-banks/course/" + courseId})
}

export function createQuestionBank(data: CreateQuestionBankRequest) {
    return request<string>({method: 'POST', url: '/api/question-banks', data, silent: true})
}

export function updateQuestionBank(id: string, data: UpdateQuestionBankRequest) {
    return request<void>({method: 'PUT', url: "/api/question-banks/" + id, data, silent: true})
}

export function deleteQuestionBank(id: string) {
    return request<void>({method: 'DELETE', url: "/api/question-banks/" + id, silent: true})
}

export function getQuestions(params: {
    questionBankId?: string;
    courseId?: string;
    page?: number;
    size?: number;
    questionType?: number;
    difficulty?: number;
    status?: number;
    keyword?: string
}) {
    const searchParams = new URLSearchParams()
    if (params.questionBankId) searchParams.append('questionBankId', params.questionBankId)
    if (params.courseId) searchParams.append('courseId', params.courseId)
    if (params.page) searchParams.append('page', params.page.toString())
    if (params.size) searchParams.append('size', params.size.toString())
    if (params.questionType != null) searchParams.append('questionType', params.questionType.toString())
    if (params.difficulty != null) searchParams.append('difficulty', params.difficulty.toString())
    if (params.status != null) searchParams.append('status', params.status.toString())
    if (params.keyword) searchParams.append('keyword', params.keyword)
    return request<PageResponse<Question>>({
        method: 'GET',
        url: "/api/question-banks/questions?" + searchParams.toString()
    })
}

export function getQuestion(id: string) {
    return request<Question>({method: 'GET', url: "/api/question-banks/questions/" + id})
}

export function createQuestion(data: CreateQuestionRequest) {
    return request<string>({method: 'POST', url: '/api/question-banks/questions', data, silent: true})
}

export function updateQuestion(id: string, data: UpdateQuestionRequest) {
    return request<void>({method: 'PUT', url: "/api/question-banks/questions/" + id, data, silent: true})
}

export function deleteQuestion(id: string) {
    return request<void>({method: 'DELETE', url: "/api/question-banks/questions/" + id, silent: true})
}

export function publishQuestion(id: string) {
    return request<void>({method: 'PUT', url: "/api/question-banks/questions/" + id + "/publish", silent: true})
}

export function viewQuestion(id: string) {
    return request<void>({method: 'POST', url: "/api/question-banks/questions/" + id + "/view", silent: true})
}