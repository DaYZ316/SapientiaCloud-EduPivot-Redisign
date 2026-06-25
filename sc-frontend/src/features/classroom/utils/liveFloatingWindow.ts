type DocumentPictureInPictureController = {
    requestWindow: (options?: {width?: number; height?: number}) => Promise<Window>
}

type WindowWithDocumentPictureInPicture = Window & {
    documentPictureInPicture?: DocumentPictureInPictureController
}

type ClassroomLiveFloatingWindowOptions = {
    title: string
    status: string
    restoreLabel: string
    onRestore: () => void
    onClose?: () => void
}

let activeFloatingWindow: Window | null = null

export async function openClassroomLiveFloatingWindow(options: ClassroomLiveFloatingWindowOptions) {
    if (typeof window === 'undefined') {
        return false
    }
    const documentPictureInPicture = (window as WindowWithDocumentPictureInPicture).documentPictureInPicture
    if (!documentPictureInPicture?.requestWindow) {
        return false
    }
    try {
        closeClassroomLiveFloatingWindow()
        const floatingWindow = await documentPictureInPicture.requestWindow({width: 340, height: 132})
        let restoring = false
        activeFloatingWindow = floatingWindow
        renderFloatingWindow(floatingWindow, {
            ...options,
            onRestore: () => {
                restoring = true
                options.onRestore()
                closeClassroomLiveFloatingWindow()
            },
        })
        floatingWindow.addEventListener('pagehide', () => {
            if (activeFloatingWindow === floatingWindow) {
                activeFloatingWindow = null
            }
            if (!restoring) {
                options.onClose?.()
            }
        }, {once: true})
        return true
    } catch {
        return false
    }
}

export function closeClassroomLiveFloatingWindow() {
    activeFloatingWindow?.close()
    activeFloatingWindow = null
}

function renderFloatingWindow(floatingWindow: Window, options: ClassroomLiveFloatingWindowOptions) {
    const document = floatingWindow.document
    document.title = options.title
    document.body.replaceChildren()

    const style = document.createElement('style')
    style.textContent = `
        :root {
            color-scheme: light;
            font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
        }
        body {
            margin: 0;
            background: #fff;
            color: #171717;
        }
        button {
            display: grid;
            width: 100vw;
            height: 100vh;
            grid-template-columns: auto minmax(0, 1fr) auto;
            gap: 12px;
            align-items: center;
            padding: 16px;
            border: 0;
            background: #fff;
            color: inherit;
            cursor: pointer;
            text-align: left;
        }
        .dot {
            width: 10px;
            height: 10px;
            border-radius: 50%;
            background: #16a34a;
            animation: pulse 1.4s ease-in-out infinite;
        }
        .copy {
            display: grid;
            min-width: 0;
            gap: 4px;
        }
        strong,
        span {
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
        }
        strong {
            font-size: 14px;
        }
        span {
            color: #6b7280;
            font-size: 12px;
        }
        .restore {
            color: #2563eb;
            font-size: 12px;
            font-weight: 700;
        }
        @keyframes pulse {
            50% {
                opacity: 0.44;
                transform: scale(1.35);
            }
        }
    `
    document.head.replaceChildren(style)

    const button = document.createElement('button')
    button.type = 'button'
    button.addEventListener('click', options.onRestore)

    const dot = document.createElement('span')
    dot.className = 'dot'

    const copy = document.createElement('span')
    copy.className = 'copy'
    const title = document.createElement('strong')
    title.textContent = options.title
    const status = document.createElement('span')
    status.textContent = options.status
    copy.append(title, status)

    const restore = document.createElement('span')
    restore.className = 'restore'
    restore.textContent = options.restoreLabel

    button.append(dot, copy, restore)
    document.body.append(button)
}
