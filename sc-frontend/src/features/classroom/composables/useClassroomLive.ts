import {onUnmounted, ref, shallowRef} from 'vue'
import {createLocalTracks, Room, RoomEvent, Track} from 'livekit-client'

import {issueClassLiveToken} from '@/features/course/api/classSession'
import type {ClassSession} from '@/features/course/types/classSession'

export function useClassroomLive(session: ClassSession, isTeacher: boolean) {
    const room = shallowRef<Room | null>(null)
    const localVideoEl = shallowRef<HTMLVideoElement | null>(null)
    const remoteVideoEl = shallowRef<HTMLVideoElement | null>(null)
    const remoteAudioEl = shallowRef<HTMLAudioElement | null>(null)
    const connected = ref(false)
    const connecting = ref(false)
    const cameraEnabled = ref(false)
    const microphoneEnabled = ref(false)
    const screenShareEnabled = ref(false)
    const errorMessage = ref('')

    async function connect() {
        if (connecting.value || connected.value) {
            return
        }
        connecting.value = true
        errorMessage.value = ''
        try {
            const token = await issueClassLiveToken(session.id)
            const nextRoom = new Room()
            room.value = nextRoom
            nextRoom.on(RoomEvent.TrackSubscribed, (track) => {
                if (track.kind === Track.Kind.Video && remoteVideoEl.value) {
                    track.attach(remoteVideoEl.value)
                }
                if (track.kind === Track.Kind.Audio && remoteAudioEl.value) {
                    track.attach(remoteAudioEl.value)
                }
            })
            nextRoom.on(RoomEvent.Disconnected, () => {
                connected.value = false
                cameraEnabled.value = false
                microphoneEnabled.value = false
                screenShareEnabled.value = false
            })
            await nextRoom.connect(token.url, token.token)
            connected.value = true
            attachRemoteTracks()
            attachRemoteAudioTracks()
            attachLocalTracks()
        } catch (error) {
            errorMessage.value = error instanceof Error ? error.message : '直播连接失败'
            await disconnect()
        } finally {
            connecting.value = false
        }
    }

    async function enableCamera() {
        if (!isTeacher || !room.value || cameraEnabled.value) {
            return
        }
        const [track] = await createLocalTracks({audio: false, video: true})
        await room.value.localParticipant.publishTrack(track)
        if (localVideoEl.value && track.kind === Track.Kind.Video) {
            track.attach(localVideoEl.value)
        }
        cameraEnabled.value = true
    }

    function attachLocalTracks() {
        const element = localVideoEl.value
        const currentRoom = room.value
        if (!element || !currentRoom) {
            return
        }
        for (const publication of currentRoom.localParticipant.videoTrackPublications.values()) {
            publication.track?.attach(element)
        }
    }

    function attachRemoteTracks() {
        const element = remoteVideoEl.value
        const currentRoom = room.value
        if (!element || !currentRoom) {
            return
        }
        for (const participant of currentRoom.remoteParticipants.values()) {
            for (const publication of participant.videoTrackPublications.values()) {
                publication.track?.attach(element)
                return
            }
        }
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
        if (!isTeacher || !room.value || microphoneEnabled.value) {
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
        if (!isTeacher || !room.value) {
            return
        }
        const nextEnabled = !screenShareEnabled.value
        await room.value.localParticipant.setScreenShareEnabled(nextEnabled)
        screenShareEnabled.value = nextEnabled
    }

    async function publishDefaults() {
        await enableCamera()
        await enableMicrophone()
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
        if (currentRoom) {
            currentRoom.disconnect()
        }
        connected.value = false
        cameraEnabled.value = false
        microphoneEnabled.value = false
        screenShareEnabled.value = false
    }

    onUnmounted(() => {
        void disconnect()
    })

    return {
        localVideoEl,
        remoteVideoEl,
        remoteAudioEl,
        connected,
        connecting,
        cameraEnabled,
        microphoneEnabled,
        screenShareEnabled,
        errorMessage,
        connect,
        disconnect,
        pausePublishing,
        toggleCamera,
        toggleMicrophone,
        toggleScreenShare,
        publishDefaults,
        attachLocalTracks,
        attachRemoteTracks,
        attachRemoteAudioTracks,
    }
}
