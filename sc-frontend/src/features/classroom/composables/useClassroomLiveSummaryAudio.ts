import {i18n} from '@/app/i18n'
import {buildLiveSummaryAudioSocketUrl, issueLiveSummaryAudioToken} from '@/features/ai/api/ai'
import {notify} from '@/shared/composables/useGlobalNotification'

const LIVE_SUMMARY_AUDIO_WORKLET_URL = '/audio/live-summary-pcm-processor.js'
const LIVE_SUMMARY_AUDIO_WORKLET_NAME = 'live-summary-pcm-processor'

type LiveSummaryAudioUploadState = {
    socket: WebSocket | null
    mediaStream: MediaStream | null
    audioContext: AudioContext | null
    destinationNode: MediaStreamAudioDestinationNode | null
    sourceNode: MediaStreamAudioSourceNode | null
    inputSourceNodes: MediaStreamAudioSourceNode[]
    workletNode: AudioWorkletNode | null
    silenceNode: GainNode | null
    scriptProcessorNode: ScriptProcessorNode | null
    additionalAudioTracks: (() => MediaStreamTrack[]) | null
    additionalAudioTrackIds: Set<string>
    additionalAudioTrackTimer: number | null
    runId: number
}

type LiveSummaryAudioUploadOptions = {
    additionalAudioTracks?: () => MediaStreamTrack[]
}

const uploadsBySessionId = new Map<string, LiveSummaryAudioUploadState>()

export async function startLiveSummaryAudioUpload(
    classSessionId: string,
    options: LiveSummaryAudioUploadOptions = {},
) {
    stopLiveSummaryAudioUpload(classSessionId)
    const state = createLiveSummaryAudioUploadState()
    state.additionalAudioTracks = options.additionalAudioTracks ?? null
    uploadsBySessionId.set(classSessionId, state)
    const runId = ++state.runId
    try {
        const token = await issueLiveSummaryAudioToken(classSessionId)
        if (!isCurrentLiveSummaryAudioUpload(classSessionId, state, runId)) return
        const socket = new WebSocket(buildLiveSummaryAudioSocketUrl(classSessionId, token.token))
        socket.binaryType = 'arraybuffer'
        state.socket = socket
        await waitForLiveSummaryAudioSocketOpen(socket)
        if (!isCurrentLiveSummaryAudioUpload(classSessionId, state, runId, socket)) return
        monitorLiveSummaryAudioSocket(classSessionId, state, socket)
        const stream = await navigator.mediaDevices.getUserMedia({
            audio: {
                channelCount: 1,
                echoCancellation: true,
                noiseSuppression: true,
                autoGainControl: true,
            },
            video: false,
        })
        if (!isCurrentLiveSummaryAudioUpload(classSessionId, state, runId, socket)) {
            stream.getTracks().forEach(track => track.stop())
            return
        }
        state.mediaStream = stream
        const context = new AudioContext()
        state.audioContext = context
        const source = createLiveSummaryAudioSource(state, context, stream)
        state.sourceNode = source
        if (!(await connectLiveSummaryAudioWorklet(classSessionId, state, context, source, runId))) {
            if (!isCurrentLiveSummaryAudioUpload(classSessionId, state, runId, socket, context)) return
            connectLiveSummaryScriptProcessor(classSessionId, state, context, source)
        }
        if (!isCurrentLiveSummaryAudioUpload(classSessionId, state, runId, socket, context)) return
        if (context.state === 'suspended') {
            await context.resume()
        }
    } catch (error) {
        if (isCurrentLiveSummaryAudioUpload(classSessionId, state, runId)) {
            stopLiveSummaryAudioUpload(classSessionId)
            throw error
        }
    }
}

export function stopLiveSummaryAudioUpload(classSessionId: string) {
    const state = uploadsBySessionId.get(classSessionId)
    if (!state) {
        return
    }
    uploadsBySessionId.delete(classSessionId)
    state.runId += 1
    if (state.additionalAudioTrackTimer != null) {
        window.clearInterval(state.additionalAudioTrackTimer)
    }
    state.workletNode?.port.postMessage({type: 'flush'})
    state.workletNode?.disconnect()
    state.silenceNode?.disconnect()
    state.scriptProcessorNode?.disconnect()
    state.sourceNode?.disconnect()
    state.inputSourceNodes.forEach(source => source.disconnect())
    state.destinationNode?.disconnect()
    state.additionalAudioTrackTimer = null
    state.workletNode = null
    state.silenceNode = null
    state.scriptProcessorNode = null
    state.sourceNode = null
    state.inputSourceNodes = []
    state.destinationNode = null
    state.additionalAudioTracks = null
    state.additionalAudioTrackIds.clear()
    void state.audioContext?.close()
    state.audioContext = null
    state.mediaStream?.getTracks().forEach(track => track.stop())
    state.mediaStream = null
    if (state.socket && state.socket.readyState <= WebSocket.OPEN) {
        state.socket.close()
    }
    state.socket = null
}

export function isLiveSummaryAudioUploading(classSessionId: string) {
    return uploadsBySessionId.has(classSessionId)
}

function createLiveSummaryAudioUploadState(): LiveSummaryAudioUploadState {
    return {
        socket: null,
        mediaStream: null,
        audioContext: null,
        destinationNode: null,
        sourceNode: null,
        inputSourceNodes: [],
        workletNode: null,
        silenceNode: null,
        scriptProcessorNode: null,
        additionalAudioTracks: null,
        additionalAudioTrackIds: new Set<string>(),
        additionalAudioTrackTimer: null,
        runId: 0,
    }
}

function createLiveSummaryAudioSource(
    state: LiveSummaryAudioUploadState,
    context: AudioContext,
    microphoneStream: MediaStream,
) {
    const destination = context.createMediaStreamDestination()
    const microphoneSource = context.createMediaStreamSource(microphoneStream)
    state.destinationNode = destination
    state.inputSourceNodes.push(microphoneSource)
    microphoneSource.connect(destination)
    connectAdditionalLiveSummaryAudioTracks(state, context)
    startAdditionalLiveSummaryAudioTrackRefresh(state, context)
    return context.createMediaStreamSource(destination.stream)
}

function startAdditionalLiveSummaryAudioTrackRefresh(
    state: LiveSummaryAudioUploadState,
    context: AudioContext,
) {
    if (!state.additionalAudioTracks) {
        return
    }
    state.additionalAudioTrackTimer = window.setInterval(() => {
        if (state.audioContext === context) {
            connectAdditionalLiveSummaryAudioTracks(state, context)
        }
    }, 1000)
}

function connectAdditionalLiveSummaryAudioTracks(
    state: LiveSummaryAudioUploadState,
    context: AudioContext,
) {
    const destination = state.destinationNode
    if (!destination || !state.additionalAudioTracks) {
        return
    }
    for (const trackId of state.additionalAudioTrackIds) {
        if (!state.additionalAudioTracks().some(track => track.id === trackId && track.readyState === 'live')) {
            state.additionalAudioTrackIds.delete(trackId)
        }
    }
    for (const track of state.additionalAudioTracks()) {
        if (track.kind !== 'audio'
            || track.readyState !== 'live'
            || state.additionalAudioTrackIds.has(track.id)) {
            continue
        }
        const source = context.createMediaStreamSource(new MediaStream([track]))
        source.connect(destination)
        state.inputSourceNodes.push(source)
        state.additionalAudioTrackIds.add(track.id)
    }
}

async function connectLiveSummaryAudioWorklet(
    classSessionId: string,
    state: LiveSummaryAudioUploadState,
    context: AudioContext,
    source: MediaStreamAudioSourceNode,
    runId: number,
) {
    if (!context.audioWorklet || typeof AudioWorkletNode === 'undefined') {
        return false
    }
    try {
        await context.audioWorklet.addModule(LIVE_SUMMARY_AUDIO_WORKLET_URL)
        if (!isCurrentLiveSummaryAudioUpload(classSessionId, state, runId, undefined, context)) {
            return true
        }
        state.workletNode = new AudioWorkletNode(context, LIVE_SUMMARY_AUDIO_WORKLET_NAME)
        state.silenceNode = context.createGain()
        state.silenceNode.gain.value = 0
        state.workletNode.port.onmessage = event => {
            if (event.data instanceof ArrayBuffer) {
                sendLiveSummaryAudioChunk(state, event.data)
            }
        }
        source.connect(state.workletNode)
        state.workletNode.connect(state.silenceNode)
        state.silenceNode.connect(context.destination)
        return true
    } catch {
        if (!isCurrentLiveSummaryAudioUpload(classSessionId, state, runId, undefined, context)) {
            return true
        }
        state.workletNode?.disconnect()
        state.silenceNode?.disconnect()
        state.workletNode = null
        state.silenceNode = null
        return false
    }
}

function connectLiveSummaryScriptProcessor(
    classSessionId: string,
    state: LiveSummaryAudioUploadState,
    context: AudioContext,
    source: MediaStreamAudioSourceNode,
) {
    if (typeof context.createScriptProcessor !== 'function') {
        throw new Error(t('courseDetail.classSession.liveSummary.errors.audioUnsupported'))
    }
    state.scriptProcessorNode = context.createScriptProcessor(4096, 1, 1)
    state.scriptProcessorNode.onaudioprocess = event => {
        const input = event.inputBuffer.getChannelData(0)
        sendLiveSummaryAudioChunk(state, floatTo16kPcm(input, context.sampleRate))
    }
    source.connect(state.scriptProcessorNode)
    state.scriptProcessorNode.connect(context.destination)

    if (!uploadsBySessionId.has(classSessionId)) {
        stopLiveSummaryAudioUpload(classSessionId)
    }
}

function sendLiveSummaryAudioChunk(state: LiveSummaryAudioUploadState, chunk: ArrayBuffer) {
    if (state.socket?.readyState !== WebSocket.OPEN) {
        return
    }
    state.socket.send(chunk)
}

function isCurrentLiveSummaryAudioUpload(
    classSessionId: string,
    state: LiveSummaryAudioUploadState,
    runId: number,
    socket?: WebSocket,
    context?: AudioContext,
) {
    return uploadsBySessionId.get(classSessionId) === state
        && state.runId === runId
        && (!socket || state.socket === socket)
        && (!context || state.audioContext === context)
}

function waitForLiveSummaryAudioSocketOpen(socket: WebSocket) {
    return new Promise<void>((resolve, reject) => {
        const cleanup = () => {
            socket.removeEventListener('open', handleOpen)
            socket.removeEventListener('error', handleError)
            socket.removeEventListener('close', handleClose)
        }
        const handleOpen = () => {
            cleanup()
            resolve()
        }
        const handleError = () => {
            cleanup()
            reject(new Error(t('courseDetail.classSession.liveSummary.errors.audioConnectFailed')))
        }
        const handleClose = (event: CloseEvent) => {
            cleanup()
            reject(new Error(event.reason || t('courseDetail.classSession.liveSummary.errors.audioClosed')))
        }
        socket.addEventListener('open', handleOpen)
        socket.addEventListener('error', handleError)
        socket.addEventListener('close', handleClose)
    })
}

function monitorLiveSummaryAudioSocket(classSessionId: string, state: LiveSummaryAudioUploadState, socket: WebSocket) {
    socket.addEventListener('error', () => {
        handleLiveSummaryAudioSocketFailure(
            classSessionId,
            state,
            socket,
            t('courseDetail.classSession.liveSummary.errors.audioChannelFailed'),
        )
    })
    socket.addEventListener('close', event => {
        handleLiveSummaryAudioSocketFailure(
            classSessionId,
            state,
            socket,
            event.reason || t('courseDetail.classSession.liveSummary.errors.audioClosedRetry'),
        )
    })
}

function handleLiveSummaryAudioSocketFailure(
    classSessionId: string,
    state: LiveSummaryAudioUploadState,
    socket: WebSocket,
    message: string,
) {
    if (uploadsBySessionId.get(classSessionId) !== state || state.socket !== socket) {
        return
    }
    notify.warn(message)
    stopLiveSummaryAudioUpload(classSessionId)
}

function floatTo16kPcm(input: Float32Array, inputSampleRate: number) {
    const targetRate = 16000
    const ratio = inputSampleRate / targetRate
    const outputLength = Math.max(1, Math.floor(input.length / ratio))
    const buffer = new ArrayBuffer(outputLength * 2)
    const view = new DataView(buffer)
    for (let i = 0; i < outputLength; i += 1) {
        const sample = input[Math.floor(i * ratio)] || 0
        const clamped = Math.max(-1, Math.min(1, sample))
        view.setInt16(i * 2, clamped < 0 ? clamped * 0x8000 : clamped * 0x7fff, true)
    }
    return buffer
}

function t(key: string) {
    return i18n.global.t(key)
}
