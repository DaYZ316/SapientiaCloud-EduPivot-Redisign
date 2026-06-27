import {inject, type InjectionKey} from 'vue'
import {type RouteLocationRaw, useRouter} from 'vue-router'

export type EnterAiMode = (target?: RouteLocationRaw) => Promise<unknown>

export const aiModeNavigationKey: InjectionKey<EnterAiMode> = Symbol('aiModeNavigation')

export function useAiModeNavigation() {
    const enterAiMode = inject(aiModeNavigationKey, null)
    const router = useRouter()

    return (target: RouteLocationRaw = {name: 'ai-workspace'}) => (
        enterAiMode ? enterAiMode(target) : router.push(target)
    )
}
