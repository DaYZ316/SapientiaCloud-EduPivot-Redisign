import {request} from '@/shared/api/request'
import type {
  PracticeSession,
  PracticeAnswer,
  CreatePracticeSessionRequest,
  SubmitAnswerRequest,
} from '@/features/question-bank/types/practiceSession'

export function createPracticeSession(data: CreatePracticeSessionRequest) {
  return request<string>({method: 'POST', url: '/api/practice-sessions', data, silent: true})
}

export function getPracticeSession(id: string) {
  return request<PracticeSession>({method: 'GET', url: "/api/practice-sessions/" + id})
}

export function submitAnswer(sessionId: string, data: SubmitAnswerRequest) {
  return request<PracticeAnswer>({method: 'POST', url: "/api/practice-sessions/" + sessionId + "/answers", data, silent: true})
}

export function completePracticeSession(id: string) {
  return request<void>({method: 'PUT', url: "/api/practice-sessions/" + id + "/complete", silent: true})
}

export function getMyPracticeHistory() {
  return request<PracticeSession[]>({method: 'GET', url: '/api/practice-sessions/my'})
}

export function getBankPracticeStats(bankId: string) {
  return request<PracticeSession>({method: 'GET', url: "/api/practice-sessions/bank/" + bankId + "/stats"})
}