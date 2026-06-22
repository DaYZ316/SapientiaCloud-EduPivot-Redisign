export interface QuestionBank {
    id: string
    courseId: string
    sysUserId: string
    bankName: string
    description: string | null
    bankType: number
    tags: string[] | null
    difficulty: number
    questionCount: number
    createdAt: string
    updatedAt: string
}

export interface Question {
    id: string
    questionBankId: string
    courseId: string
    sysUserId: string
    questionTitle: string
    questionContent: string | null
    questionType: number
    difficulty: number
    score: number
    estimatedTime: number | null
    tags: string[] | null
    imageUrls: string[] | null
    allowPartialCredit: number
    viewCount: number
    status: number
    options: QuestionOption[] | null
    answers: QuestionAnswer[] | null
    createdAt: string
    updatedAt: string
}

export interface QuestionOption {
    id: string
    questionId: string
    optionContent: string
    optionLabel: string
    isCorrect: number
    score: number | null
    imageUrls: string[] | null
    explanation: string | null
}

export interface QuestionAnswer {
    id: string
    questionId: string
    answerContent: string
    explanation: string | null
    score: number | null
    sortOrder: number
}

export interface CreateQuestionBankRequest {
    courseId: string
    bankName: string
    description?: string
    bankType?: number
    tags?: string[]
    difficulty?: number
}

export interface UpdateQuestionBankRequest {
    bankName?: string
    description?: string
    bankType?: number
    tags?: string[]
    difficulty?: number
}

export interface CreateQuestionRequest {
    questionBankId: string
    questionTitle: string
    questionContent?: string
    questionType: number
    difficulty?: number
    score?: number
    estimatedTime?: number
    tags?: string[]
    imageUrls?: string[]
    allowPartialCredit?: number
    options?: CreateOptionRequest[]
    answers?: CreateAnswerRequest[]
}

export type QuestionImportRequest = Omit<CreateQuestionRequest, 'questionBankId'>

export interface BatchCreateQuestionsRequest {
    questionBankId: string
    questions: QuestionImportRequest[]
}

export interface BatchCreateQuestionsResponse {
    questionIds: string[]
    importedCount: number
}

export interface UpdateQuestionRequest {
    questionTitle?: string
    questionContent?: string
    questionType?: number
    difficulty?: number
    score?: number
    estimatedTime?: number
    tags?: string[]
    imageUrls?: string[]
    allowPartialCredit?: number
    options?: CreateOptionRequest[]
    answers?: CreateAnswerRequest[]
}

export interface CreateOptionRequest {
    optionContent: string
    optionLabel: string
    isCorrect: number
    score?: number
    imageUrls?: string[]
    explanation?: string
}

export interface CreateAnswerRequest {
    answerContent: string
    explanation?: string
    score?: number
    sortOrder?: number
}

export const QuestionType: Record<number, string> = {
    0: '单选题',
    1: '多选题',
    2: '判断题',
    3: '填空题',
    4: '简答题',
}

export const QuestionDifficulty: Record<number, string> = {
    1: '简单',
    2: '中等',
    3: '困难',
}

export const QuestionStatus: Record<number, string> = {
    0: '草稿',
    1: '已发布',
}

export const BankType: Record<number, string> = {
    0: '练习',
    1: '考试',
    2: '作业',
}
