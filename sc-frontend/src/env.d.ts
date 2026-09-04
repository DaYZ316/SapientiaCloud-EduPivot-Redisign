/// <reference types="vite/client" />

interface ImportMetaEnv {
    readonly VITE_API_BASE_URL?: string
    readonly VITE_API_PROXY_TARGET?: string
    readonly VITE_GITHUB_CLIENT_ID?: string
    readonly VITE_GITHUB_REDIRECT_URI?: string
    readonly VITE_GITHUB_SCOPE?: string
    readonly VITE_GOOGLE_CLIENT_ID?: string
    readonly VITE_GOOGLE_REDIRECT_URI?: string
    readonly VITE_DESKTOP_GITHUB_REDIRECT_URI?: string
    readonly VITE_MOBILE_GITHUB_REDIRECT_URI?: string
}

interface ImportMeta {
    readonly env: ImportMetaEnv
}

interface Window {
    edupivotDesktop?: import('@/shared/platform/desktop').DesktopBridge
}

declare module 'katex/contrib/auto-render' {
    import type {KatexOptions} from 'katex'

    interface AutoRenderDelimiter {
        left: string
        right: string
        display: boolean
    }

    interface AutoRenderOptions extends KatexOptions {
        delimiters?: AutoRenderDelimiter[]
        ignoredTags?: string[]
        ignoredClasses?: string[]
    }

    export default function renderMathInElement(element: HTMLElement, options?: AutoRenderOptions): void
}

declare module '@vue-office/pdf/lib/v3/vue-office-pdf.mjs' {
    import type {DefineComponent} from 'vue'

    const component: DefineComponent<{
        src: string | ArrayBuffer | Blob
        rerender?: () => unknown
        staticFileUrl?: string
        requestOptions?: unknown
        options?: unknown
        getScale?: () => number
        setScale?: (value: number) => void
    }>

    export default component
}

declare module '@vue-office/docx/lib/v3/vue-office-docx.mjs' {
    import type {DefineComponent} from 'vue'

    const component: DefineComponent<{
        src: string | ArrayBuffer | Blob
    }>

    export default component
}

declare module '@vue-office/excel/lib/v3/vue-office-excel.mjs' {
    import type {DefineComponent} from 'vue'

    const component: DefineComponent<{
        src: string | ArrayBuffer | Blob
    }>

    export default component
}

declare module '@vue-office/pptx/lib/v3/vue-office-pptx.mjs' {
    import type {DefineComponent} from 'vue'

    const component: DefineComponent<{
        src: string | ArrayBuffer | Blob
        requestOptions?: unknown
        options?: {
            width?: number
            height?: number
        }
    }>

    export default component
}
