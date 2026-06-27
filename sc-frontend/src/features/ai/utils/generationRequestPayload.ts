import type {AiAgentMode, GenerationRequest} from '@/features/ai/types/ai'

type GenerationMode = Exclude<AiAgentMode, 'CHAT'>

export type GenerationRequestFieldKey =
    | 'questionCount'
    | 'questionType'
    | 'difficulty'
    | 'paperName'
    | 'paperType'
    | 'totalScore'
    | 'totalEstimatedTime'
    | 'scorePerQuestion'
    | 'questionBankId'
    | 'chapterIds'
    | 'knowledgePoints'
    | 'abilityGoals'

export type GenerationRequestFieldKind =
    | 'text'
    | 'number'
    | 'minutes'
    | 'list'
    | 'questionType'
    | 'difficulty'
    | 'questionBank'
    | 'chapterCount'

export interface GenerationRequestDisplayField {
    key: GenerationRequestFieldKey
    kind: GenerationRequestFieldKind
    value: number | string | string[]
}

export interface GenerationRequestDisplay {
    mode: GenerationMode
    fields: GenerationRequestDisplayField[]
    requirement: string
}

export function createGenerationRequestPayload(mode?: AiAgentMode, request?: GenerationRequest) {
    if ((mode !== 'QUESTION' && mode !== 'PAPER') || !request) return null

    return {
        generationMode: mode,
        generationRequest: request,
    }
}

export function buildGenerationRequestDisplay(
    payload: Record<string, unknown> | null | undefined,
): GenerationRequestDisplay | null {
    const request = generationRequestFromPayload(payload)
    const mode = generationModeFromPayload(payload)
    if (!request || !mode) return null

    const fields: GenerationRequestDisplayField[] = []
    pushField(fields, 'questionCount', request.questionCount, 'number')
    pushField(fields, 'questionType', request.questionType, 'questionType')
    pushField(fields, 'difficulty', request.difficulty, 'difficulty')

    if (mode === 'PAPER') {
        pushField(fields, 'paperName', request.paperName, 'text')
        pushField(fields, 'paperType', request.paperType, 'text')
        pushField(fields, 'totalScore', request.totalScore, 'number')
        pushField(fields, 'totalEstimatedTime', request.totalEstimatedTime, 'minutes')
    }

    pushField(fields, 'scorePerQuestion', request.scorePerQuestion, 'number')
    pushField(fields, 'questionBankId', request.questionBankId, 'questionBank')
    pushField(fields, 'chapterIds', request.chapterIds?.length, 'chapterCount')
    pushField(fields, 'knowledgePoints', request.knowledgePoints, 'list')
    pushField(fields, 'abilityGoals', request.abilityGoals, 'list')

    return {
        mode,
        fields,
        requirement: textValue(request.requirement),
    }
}

export function hasGenerationRequestPayload(message: { payload?: Record<string, unknown> | null }) {
    return Boolean(buildGenerationRequestDisplay(message.payload))
}

function generationRequestFromPayload(payload: Record<string, unknown> | null | undefined) {
    const value = payload?.generationRequest
    if (!value || typeof value !== 'object' || Array.isArray(value)) return null

    return value as GenerationRequest
}

function generationModeFromPayload(payload: Record<string, unknown> | null | undefined): GenerationMode | null {
    const mode = payload?.generationMode
    return mode === 'QUESTION' || mode === 'PAPER' ? mode : null
}

function pushField(
    fields: GenerationRequestDisplayField[],
    key: GenerationRequestFieldKey,
    value: unknown,
    kind: GenerationRequestFieldKind,
) {
    const normalizedValue = normalizeFieldValue(value)
    if (normalizedValue === null) return

    fields.push({key, value: normalizedValue, kind})
}

function normalizeFieldValue(value: unknown): GenerationRequestDisplayField['value'] | null {
    if (typeof value === 'number' && Number.isFinite(value)) return value
    if (typeof value === 'string') return value.trim() || null
    if (Array.isArray(value)) {
        const values = value
            .map(item => typeof item === 'string' ? item.trim() : '')
            .filter(Boolean)
        return values.length ? values : null
    }
    return null
}

function textValue(value: string | null | undefined) {
    return value?.trim() || ''
}
