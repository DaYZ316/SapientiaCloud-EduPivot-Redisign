import {computed, onUnmounted, ref, shallowRef, unref, watch, type MaybeRef} from 'vue'
import {createLocalTracks, Room, RoomEvent, Track, type ConnectionQuality, type Participant} from 'livekit-client'

import {i18n} from '@/app/i18n'
import {issueClassLiveToken} from '@/features/course/api/classSession'
import {ClassLiveStatus, type ClassParticipant, type ClassSession} from '@/features/course/types/classSession'
import {
    EMPTY_LIVE_NETWORK_STATS,
    mergeOnlineParticipants,
    normalizeTrackStats,
    type LiveNetworkStats,
    type LiveOnlineParticipant,
} from '@/features/classroom/composables/livePresence'

const CAMERA_OVERLAY_TOPIC = 'classroom-camera-overlay-position'
const CAMERA_OVERLAY_MESSAGE_TYPE = 'camera_overlay_position'
const CAMERA_OVERLAY_POSITIONS = ['top-left', 'top-right', 'bottom-left', 'bottom-right'] as const
const NETWORK_STATS_INTERVAL_MS = 2000
const SCREEN_SHARE_CAPTURE_OPTIONS = {
    selfBrowserSurface: 'exclude',
    surfaceSwitching: 'include',
    preferCurrentTab: false,
    contentHint: 'detail',
} as const
const liveStateBySessionId = new Map<string, ClassroomLiveState>()
const liveStateConsumerCount = new Map<string, number>()

export type CameraOverlayPosition = (typeof CAMERA_OVERLAY_POSITIONS)[number]
type ClassroomLiveState = ReturnType<typeof createClassroomLiveState>

export function useClassroomLive(session: MaybeRef<ClassSession>, isTeacher: MaybeRef<boolean>) {
    const sessionId = unref(session).id
    let liveState = liveStateBySessionId.get(sessionId)
    if (!liveState) {
        liveState = createClassroomLiveState(unref(session), Boolean(unref(isTeacher)))
        liveStateBySessionId.set(sessionId, liveState)
    }
    liveStateConsumerCount.set(sessionId, (liveStateConsumerCount.get(sessionId) ?? 0) + 1)

    const stopSessionWatch = watch(() => unref(session), (nextSession) => {
        liveState.updateSession(nextSession)
    }, {immediate: true})
    const stopTeacherWatch = watch(() => unref(isTeacher), (nextIsTeacher) => {
        liveState.updateIsTeacher(Boolean(nextIsTeacher))
    }, {immediate: true})

    onUnmounted(() => {
        stopSessionWatch()
        stopTeacherWatch()
        const remainingConsumers = (liveStateConsumerCount.get(sessionId) ?? 1) - 1
        if (remainingConsumers > 0) {
            liveStateConsumerCount.set(sessionId, remainingConsumers)
            return
        }

        liveStateConsumerCount.delete(sessionId)
        liveState.releaseMediaElements()
        if (liveState.shouldRetainOnUnmount()) {
            return
        }
        liveStateBySessionId.delete(sessionId)
        void liveState.disconnect()
    })

    return liveState
}

function createClassroomLiveState(initialSession: ClassSession, initialIsTeacher: boolean) {
    const currentSession = shallowRef(initialSession)
    const currentIsTeacher = ref(initialIsTeacher)
    const room = shallowRef<Room | null>(null)
    const localVideoEl = shallowRef<HTMLVideoElement | null>(null)
    const localCameraVideoEl = shallowRef<HTMLVideoElement | null>(null)
    const remoteVideoEl = shallowRef<HTMLVideoElement | null>(null)
    const remoteCameraVideoEl = shallowRef<HTMLVideoElement | null>(null)
    const remoteAudioEl = shallowRef<HTMLAudioElement | null>(null)
    const connected = ref(false)
    const connecting = ref(false)
    const cameraEnabled = ref(false)
    const microphoneEnabled = ref(false)
    const screenShareEnabled = ref(false)
    const localVideoVisible = ref(false)
    const remoteVideoVisible = ref(false)
    const remoteCameraVisible = ref(false)
    const remoteScreenShareVisible = ref(false)
    const cameraOverlayPosition = ref<CameraOverlayPosition>('bottom-right')
    const sessionParticipants = ref<ClassParticipant[]>([])
    const connectionQualityByIdentity = ref<Record<string, string>>({})
    const onlineParticipants = ref<LiveOnlineParticipant[]>([])
    const networkStats = ref<LiveNetworkStats>({...EMPTY_LIVE_NETWORK_STATS})
    const errorMessage = ref('')
    const textDecoder = new TextDecoder()
    const textEncoder = new TextEncoder()
    let networkStatsTimer: number | null = null
    const onlineCount = computed(() => onlineParticipants.value.length)
    const hasVideoTrack = computed(() => currentIsTeacher.value ? localVideoVisible.value : remoteVideoVisible.value)

    function updateSession(nextSession: ClassSession) {
        currentSession.value = nextSession
    }

    function updateIsTeacher(nextIsTeacher: boolean) {
        currentIsTeacher.value = nextIsTeacher
    }

    async function connect() {
        if (connecting.value || connected.value) {
            return
        }
        connecting.value = true
        errorMessage.value = ''
        try {
            const token = await issueClassLiveToken(currentSession.value.id)
            const nextRoom = new Room({disconnectOnPageLeave: false})
            room.value = nextRoom
            nextRoom.on(RoomEvent.TrackSubscribed, (track) => {
                if (track.kind === Track.Kind.Video) {
                    attachRemoteTracks()
                }
                if (track.kind === Track.Kind.Audio && remoteAudioEl.value) {
                    track.attach(remoteAudioEl.value)
                }
            })
            nextRoom.on(RoomEvent.TrackUnsubscribed, (track) => {
                if (track.kind === Track.Kind.Video) {
                    detachVideoTrack(track, remoteVideoEl.value)
                    detachVideoTrack(track, remoteCameraVideoEl.value)
                    attachRemoteTracks()
                }
                if (track.kind === Track.Kind.Audio && remoteAudioEl.value) {
                    track.detach(remoteAudioEl.value)
                    attachRemoteAudioTracks()
                }
            })
            nextRoom.on(RoomEvent.LocalTrackPublished, (publication) => {
                if (publication.source === Track.Source.Camera) {
                    cameraEnabled.value = true
                }
                if (publication.source === Track.Source.ScreenShare) {
                    screenShareEnabled.value = true
                }
                attachLocalTracks()
            })
            nextRoom.on(RoomEvent.LocalTrackUnpublished, (publication) => {
                if (publication.source === Track.Source.Camera) {
                    cameraEnabled.value = false
                }
                if (publication.source === Track.Source.ScreenShare) {
                    screenShareEnabled.value = false
                }
                attachLocalTracks()
            })
            nextRoom.on(RoomEvent.ParticipantConnected, () => {
                refreshPresence()
                if (currentIsTeacher.value) {
                    void publishCameraOverlayPosition().catch(() => undefined)
                }
            })
            nextRoom.on(RoomEvent.ParticipantDisconnected, (participant) => {
                delete connectionQualityByIdentity.value[participant.identity]
                connectionQualityByIdentity.value = {...connectionQualityByIdentity.value}
                refreshPresence()
            })
            nextRoom.on(RoomEvent.ConnectionQualityChanged, (quality, participant) => {
                updateConnectionQuality(participant, quality)
            })
            nextRoom.on(RoomEvent.Reconnected, () => {
                refreshConnectionQualities()
                refreshPresence()
                void sampleNetworkStats()
            })
            nextRoom.on(RoomEvent.DataReceived, (payload, _participant, _kind, topic) => {
                handleCameraOverlayMessage(payload, topic)
            })
            nextRoom.on(RoomEvent.Disconnected, () => {
                stopNetworkStatsSampler()
                resetLiveState()
            })
            await nextRoom.connect(token.url, token.token)
            connected.value = true
            refreshConnectionQualities()
            refreshPresence()
            attachRemoteTracks()
            attachRemoteAudioTracks()
            attachLocalTracks()
            startNetworkStatsSampler()
        } catch (error) {
            errorMessage.value = error instanceof Error ? error.message : i18n.global.t('courseDetail.live.connectionFailed')
            await disconnect()
        } finally {
            connecting.value = false
        }
    }

    async function enableCamera() {
        if (!currentIsTeacher.value || !room.value || cameraEnabled.value) {
            return
        }
        const [track] = await createLocalTracks({audio: false, video: true})
        await room.value.localParticipant.publishTrack(track)
        attachLocalTracks()
        cameraEnabled.value = true
    }

    function attachLocalTracks() {
        const currentRoom = room.value
        if (!currentRoom) {
            localVideoVisible.value = false
            clearVideoTrack(localVideoEl.value)
            clearVideoTrack(localCameraVideoEl.value)
            return
        }
        const tracks = pickVideoTracks(currentRoom.localParticipant.videoTrackPublications.values())
        const mainTrack = tracks.screenShareTrack || tracks.cameraTrack || tracks.fallbackTrack
        const overlayTrack = tracks.screenShareTrack && tracks.cameraTrack ? tracks.cameraTrack : null
        localVideoVisible.value = Boolean(mainTrack)
        attachVideoTrack(localVideoEl.value, mainTrack)
        attachVideoTrack(localCameraVideoEl.value, overlayTrack)
    }

    function attachRemoteTracks() {
        const currentRoom = room.value
        if (!currentRoom) {
            remoteVideoVisible.value = false
            remoteCameraVisible.value = false
            remoteScreenShareVisible.value = false
            clearVideoTrack(remoteVideoEl.value)
            clearVideoTrack(remoteCameraVideoEl.value)
            return
        }
        const tracks = pickRemoteVideoTracks()
        const mainTrack = tracks.screenShareTrack || tracks.cameraTrack || tracks.fallbackTrack
        const overlayTrack = tracks.screenShareTrack && tracks.cameraTrack ? tracks.cameraTrack : null
        remoteVideoVisible.value = Boolean(mainTrack)
        remoteCameraVisible.value = Boolean(tracks.cameraTrack)
        remoteScreenShareVisible.value = Boolean(tracks.screenShareTrack)
        attachVideoTrack(remoteVideoEl.value, mainTrack)
        attachVideoTrack(remoteCameraVideoEl.value, overlayTrack)
        void sampleNetworkStats()
    }

    function attachRemoteAudioTracks() {
        const element = remoteAudioEl.value
        const currentRoom = room.value
        if (!element || !currentRoom) {
            return
        }
        for (const participant of currentRoom.remoteParticipants.values()) {
            for (const publication of participant.audioTrackPublications.values()) {
                publication.track?.attach(element)
                return
            }
        }
    }

    async function disableCamera() {
        if (!room.value) {
            return
        }
        for (const publication of room.value.localParticipant.videoTrackPublications.values()) {
            const track = publication.track
            if (track?.source === Track.Source.Camera) {
                await room.value.localParticipant.unpublishTrack(track)
                track.stop()
            }
        }
        cameraEnabled.value = false
    }

    async function enableMicrophone() {
        if (!currentIsTeacher.value || !room.value || microphoneEnabled.value) {
            return
        }
        await room.value.localParticipant.setMicrophoneEnabled(true)
        microphoneEnabled.value = true
    }

    async function disableMicrophone() {
        if (!room.value) {
            return
        }
        await room.value.localParticipant.setMicrophoneEnabled(false)
        microphoneEnabled.value = false
    }

    async function toggleCamera() {
        if (cameraEnabled.value) {
            await disableCamera()
            return
        }
        await enableCamera()
    }

    async function toggleMicrophone() {
        if (microphoneEnabled.value) {
            await disableMicrophone()
            return
        }
        await enableMicrophone()
    }

    async function toggleScreenShare() {
        if (!currentIsTeacher.value || !room.value) {
            return
        }
        const nextEnabled = !screenShareEnabled.value
        await room.value.localParticipant.setScreenShareEnabled(
            nextEnabled,
            nextEnabled ? SCREEN_SHARE_CAPTURE_OPTIONS : undefined,
        )
        screenShareEnabled.value = nextEnabled
        attachLocalTracks()
    }

    async function publishDefaults() {
        await disableCamera()
        await disableMicrophone()
        if (screenShareEnabled.value) {
            await toggleScreenShare()
        }
    }

    async function pausePublishing() {
        await disableCamera()
        await disableMicrophone()
        if (screenShareEnabled.value) {
            await toggleScreenShare()
        }
    }

    async function disconnect() {
        const currentRoom = room.value
        room.value = null
        stopNetworkStatsSampler()
        if (currentRoom) {
            currentRoom.disconnect()
        }
        resetLiveState()
    }

    function setSessionParticipants(participants: ClassParticipant[]) {
        sessionParticipants.value = participants
        refreshPresence()
    }

    async function setCameraOverlayPosition(position: CameraOverlayPosition) {
        if (!isCameraOverlayPosition(position)) {
            return
        }
        cameraOverlayPosition.value = position
        if (!currentIsTeacher.value || !room.value || !connected.value) {
            return
        }
        await publishCameraOverlayPosition()
    }

    async function publishCameraOverlayPosition() {
        if (!room.value || !connected.value) {
            return
        }
        const payload = textEncoder.encode(JSON.stringify({
            type: CAMERA_OVERLAY_MESSAGE_TYPE,
            position: cameraOverlayPosition.value,
        }))
        await room.value.localParticipant.publishData(payload, {
            reliable: true,
            topic: CAMERA_OVERLAY_TOPIC,
        })
    }

    function handleCameraOverlayMessage(payload: Uint8Array, topic?: string) {
        if (topic !== CAMERA_OVERLAY_TOPIC) {
            return
        }
        try {
            const message = JSON.parse(textDecoder.decode(payload)) as {type?: unknown; position?: unknown}
            if (message.type === CAMERA_OVERLAY_MESSAGE_TYPE && isCameraOverlayPosition(message.position)) {
                cameraOverlayPosition.value = message.position
            }
        } catch {
            // Ignore unrelated malformed data packets from the room.
        }
    }

    function isCameraOverlayPosition(value: unknown): value is CameraOverlayPosition {
        return CAMERA_OVERLAY_POSITIONS.includes(value as CameraOverlayPosition)
    }

    function pickRemoteVideoTracks() {
        const currentRoom = room.value
        let cameraTrack: Track | null = null
        let fallbackTrack: Track | null = null
        if (!currentRoom) {
            return {screenShareTrack: null, cameraTrack, fallbackTrack}
        }
        for (const participant of currentRoom.remoteParticipants.values()) {
            const tracks = pickVideoTracks(participant.videoTrackPublications.values())
            if (tracks.screenShareTrack) {
                return tracks
            }
            cameraTrack ||= tracks.cameraTrack
            fallbackTrack ||= tracks.fallbackTrack
        }
        return {screenShareTrack: null, cameraTrack, fallbackTrack}
    }

    function pickPrimaryStatsTrack() {
        const currentRoom = room.value
        if (!currentRoom) {
            return null
        }
        const localTracks = pickVideoTracks(currentRoom.localParticipant.videoTrackPublications.values())
        const localTrack = localTracks.screenShareTrack || localTracks.cameraTrack || localTracks.fallbackTrack
        if (localTrack) {
            return localTrack
        }
        const remoteTracks = pickRemoteVideoTracks()
        return remoteTracks.screenShareTrack || remoteTracks.cameraTrack || remoteTracks.fallbackTrack
    }

    function pickVideoTracks(publications: Iterable<{source: Track.Source; track?: Track}>) {
        let screenShareTrack: Track | null = null
        let cameraTrack: Track | null = null
        let fallbackTrack: Track | null = null
        for (const publication of publications) {
            const track = publication.track
            if (!track || track.kind !== Track.Kind.Video) {
                continue
            }
            if (publication.source === Track.Source.ScreenShare) {
                screenShareTrack = track
            } else if (publication.source === Track.Source.Camera) {
                cameraTrack = track
            } else {
                fallbackTrack = track
            }
        }
        return {screenShareTrack, cameraTrack, fallbackTrack}
    }

    function attachVideoTrack(element: HTMLVideoElement | null, track: Track | null) {
        if (!element) {
            return
        }
        if (track) {
            track.attach(element)
            return
        }
        clearVideoTrack(element)
    }

    function detachVideoTrack(track: Track, element: HTMLVideoElement | null) {
        if (element) {
            track.detach(element)
        }
    }

    function clearVideoTrack(element: HTMLMediaElement | null) {
        if (element) {
            element.srcObject = null
        }
    }

    function clearVideoElements() {
        clearVideoTrack(localVideoEl.value)
        clearVideoTrack(localCameraVideoEl.value)
        clearVideoTrack(remoteVideoEl.value)
        clearVideoTrack(remoteCameraVideoEl.value)
    }

    function releaseMediaElements() {
        clearVideoElements()
        clearVideoTrack(remoteAudioEl.value)
        localVideoEl.value = null
        localCameraVideoEl.value = null
        remoteVideoEl.value = null
        remoteCameraVideoEl.value = null
        remoteAudioEl.value = null
    }

    function updateConnectionQuality(participant: Participant, quality: ConnectionQuality) {
        connectionQualityByIdentity.value = {
            ...connectionQualityByIdentity.value,
            [participant.identity]: quality,
        }
        refreshPresence()
    }

    function refreshConnectionQualities() {
        const currentRoom = room.value
        if (!currentRoom) {
            connectionQualityByIdentity.value = {}
            return
        }
        const nextQualities: Record<string, string> = {
            [currentRoom.localParticipant.identity]: currentRoom.localParticipant.connectionQuality,
        }
        for (const participant of currentRoom.remoteParticipants.values()) {
            nextQualities[participant.identity] = participant.connectionQuality
        }
        connectionQualityByIdentity.value = nextQualities
    }

    function refreshPresence() {
        const currentRoom = room.value
        if (!currentRoom || !connected.value) {
            onlineParticipants.value = []
            return
        }
        onlineParticipants.value = mergeOnlineParticipants(
            currentRoom.localParticipant,
            currentRoom.remoteParticipants.values(),
            sessionParticipants.value,
            connectionQualityByIdentity.value,
        )
    }

    function startNetworkStatsSampler() {
        stopNetworkStatsSampler()
        void sampleNetworkStats()
        networkStatsTimer = window.setInterval(() => {
            void sampleNetworkStats()
        }, NETWORK_STATS_INTERVAL_MS)
    }

    function stopNetworkStatsSampler() {
        if (networkStatsTimer == null) {
            return
        }
        window.clearInterval(networkStatsTimer)
        networkStatsTimer = null
    }

    async function sampleNetworkStats() {
        const statsTrack = pickPrimaryStatsTrack() as (Track & {
            getSenderStats?: () => Promise<unknown>
            getReceiverStats?: () => Promise<unknown>
            getRTCStatsReport?: () => Promise<unknown>
        }) | null
        const currentRoom = room.value
        if (!statsTrack && !currentRoom) {
            networkStats.value = {...EMPTY_LIVE_NETWORK_STATS}
            return
        }
        try {
            const trackStats = statsTrack && typeof statsTrack.getRTCStatsReport === 'function'
                ? await statsTrack.getRTCStatsReport()
                : statsTrack && typeof statsTrack.getSenderStats === 'function'
                    ? await statsTrack.getSenderStats()
                    : await statsTrack?.getReceiverStats?.()
            const roomStats = await getRoomPeerConnectionStats(currentRoom)
            networkStats.value = normalizeTrackStats([trackStats, roomStats])
        } catch {
            networkStats.value = {...EMPTY_LIVE_NETWORK_STATS}
        }
    }

    async function getRoomPeerConnectionStats(currentRoom: Room | null) {
        const pcManager = currentRoom?.engine?.pcManager
        const subscriberStats = await pcManager?.subscriber?.getStats?.()
        if (subscriberStats) {
            return subscriberStats
        }
        return pcManager?.publisher?.getStats?.()
    }

    function resetLiveState() {
        connected.value = false
        cameraEnabled.value = false
        microphoneEnabled.value = false
        screenShareEnabled.value = false
        localVideoVisible.value = false
        remoteVideoVisible.value = false
        remoteCameraVisible.value = false
        remoteScreenShareVisible.value = false
        connectionQualityByIdentity.value = {}
        onlineParticipants.value = []
        networkStats.value = {...EMPTY_LIVE_NETWORK_STATS}
        clearVideoElements()
    }

    function shouldRetainOnUnmount() {
        return currentIsTeacher.value
            && connected.value
            && (currentSession.value.liveStatus === ClassLiveStatus.LIVE
                || currentSession.value.liveStatus === ClassLiveStatus.PAUSED)
    }

    return {
        localVideoEl,
        localCameraVideoEl,
        remoteVideoEl,
        remoteCameraVideoEl,
        remoteAudioEl,
        connected,
        connecting,
        cameraEnabled,
        microphoneEnabled,
        screenShareEnabled,
        remoteCameraVisible,
        remoteScreenShareVisible,
        cameraOverlayPosition,
        onlineParticipants,
        onlineCount,
        hasVideoTrack,
        connectionQualityByIdentity,
        networkStats,
        errorMessage,
        connect,
        disconnect,
        pausePublishing,
        toggleCamera,
        toggleMicrophone,
        toggleScreenShare,
        publishDefaults,
        setCameraOverlayPosition,
        setSessionParticipants,
        attachLocalTracks,
        attachRemoteTracks,
        attachRemoteAudioTracks,
        updateSession,
        updateIsTeacher,
        releaseMediaElements,
        shouldRetainOnUnmount,
    }
}
