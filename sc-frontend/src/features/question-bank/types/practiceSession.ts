export interface PracticeSession {
  id: string
  questionBankId: string
  courseId: string
  sysUserId: string
  sessionType: number
  totalQuestions: number
  answeredCount: number
  correctCount: number
  totalScore: number
  earnedScore: number
  startedAt: string
  completedAt: string | null
  status: number
  answers: PracticeAnswer[] | null
}

export interface PracticeAnswer {
  id: string
  sessionId: string
  questionId: string
  selectedOptionIds: string[] | null
  textAnswer: string | null
  isCorrect: number
  earnedScore: number
  answeredAt: string
}

export interface CreatePracticeSessionRequest {
  questionBankId: string
  courseId: string
  sessionType: number
}

export interface SubmitAnswerRequest {
  questionId: string
  selectedOptionIds?: string[]
  textAnswer?: string
}

export const SessionStatus: Record<number, string> = {
  0: '进行中',
  1: '已完成',
  2: '已放弃',
}

export const SessionType: Record<number, string> = {
  0: '顺序练习',
  1: '随机练习',
}
