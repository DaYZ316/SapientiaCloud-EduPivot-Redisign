export interface LivePracticeOptionSnapshot {
    id: string
    optionContent: string
    optionLabel: string
    isCorrect: number
    score: number | null
    imageUrls: string[] | null
    explanation: string | null
}

export interface LivePracticeAnswerSnapshot {
    id: string
    answerContent: string
    explanation: string | null
    score: number | null
    sortOrder: number | null
}

export interface LivePracticeSubmission {
    id: string
    groupId: string
    questionSnapshotId: string
    courseId: string
    classSessionId: string
    studentId: string
    studentName: string | null
    studentAvatarUrl: string | null
    selectedOptionIds: string[] | null
    textAnswer: string | null
    submitStatus: number
    submitStatusText: string
    isCorrect: number | null
    earnedScore: number
    aiGradingStatus: string
    aiGradingFeedback: string | null
    aiGradingError: string | null
    aiGradedAt: string | null
    submittedAt: string
}

export interface LivePracticeStudent {
    id: string
    displayName: string | null
    avatarUrl: string | null
}

export interface LivePracticeAnalysis {
    submittedCount: number
    lateSubmittedCount: number
    notSubmittedCount: number
    correctCount: number
    averageScore: number
    optionCounts: Record<string, number>
    notSubmittedStudents: LivePracticeStudent[]
    submissions: LivePracticeSubmission[]
}

export interface LivePracticeQuestion {
    id: string
    groupId: string
    sourceQuestionId: string | null
    questionOrder: number
    questionTitle: string
    questionContent: string | null
    questionType: number
    difficulty: number
    score: number
    estimatedTime: number | null
    tags: string[] | null
    imageUrls: string[] | null
    allowPartialCredit: number
    options: LivePracticeOptionSnapshot[] | null
    answers: LivePracticeAnswerSnapshot[] | null
    aiGradingEnabled: number
    mySubmission: LivePracticeSubmission | null
    analysis: LivePracticeAnalysis | null
    createdAt: string
}

export interface LivePracticeGroup {
    id: string
    courseId: string
    classSessionId: string
    teacherId: string
    title: string
    availableStartAt: string
    availableEndAt: string
    allowLateSubmission: number
    aiGradingEnabled: number
    aiGradingRequirement: string | null
    publishOrder: number
    publishedAt: string
    totalQuestions: number
    submittedStudents: number
    totalStudents: number
    questions: LivePracticeQuestion[] | null
}

export interface LivePracticeWorkbookItem {
    groupId: string
    groupTitle: string
    classSessionId: string
    publishOrder: number
    availableStartAt: string
    availableEndAt: string
    allowLateSubmission: number
    question: LivePracticeQuestion
    submission: LivePracticeSubmission | null
    submitStatus: number
    submitStatusText: string
}

export interface LivePracticeEvent {
    groupId: string
    courseId: string
    classSessionId: string
    title: string
    totalQuestions: number
    availableStartAt: string
    availableEndAt: string
    allowLateSubmission: number
    publishedAt: string
}

export interface CreateLivePracticeRequest {
    title: string
    availableStartAt: string
    availableEndAt: string
    allowLateSubmission: number
    aiGradingEnabled?: number
    aiGradingRequirement?: string
    selectedQuestionIds?: string[]
    createdQuestions?: CreateLivePracticeQuestionRequest[]
}

export interface CreateLivePracticeQuestionRequest {
    questionTitle: string
    questionContent?: string
    questionType: number
    difficulty: number
    score: number
    estimatedTime?: number
    tags?: string[]
    imageUrls?: string[]
    allowPartialCredit: number
    aiGradingEnabled: number
    options?: CreateLivePracticeOptionRequest[]
    answers?: CreateLivePracticeAnswerRequest[]
}

export interface CreateLivePracticeOptionRequest {
    optionContent: string
    optionLabel: string
    isCorrect: number
    score?: number
    imageUrls?: string[]
    explanation?: string
}

export interface CreateLivePracticeAnswerRequest {
    answerContent: string
    explanation?: string
    score?: number
    sortOrder?: number
}

export interface SubmitLivePracticeAnswerRequest {
    selectedOptionIds?: string[]
    textAnswer?: string
}

export interface LivePracticeSubscription {
    close: () => void
}

export const LivePracticeSubmitStatus = {
    NOT_SUBMITTED: 0,
    SUBMITTED: 1,
    LATE_SUBMITTED: 2,
} as const
