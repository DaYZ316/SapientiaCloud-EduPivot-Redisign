import {flushPromises, mount} from '@vue/test-utils'
import {computed, ref, shallowRef} from 'vue'
import {createI18n} from 'vue-i18n'
import {beforeEach, describe, expect, it, vi} from 'vitest'

import ClassroomLiveExperience from '@/features/classroom/components/ClassroomLiveExperience.vue'
import enCourseDetail from '@/features/course/i18n/en-US/courseDetail'
import {
    listClassBarrages,
    sendClassBarrage,
    startClassLive,
    stopClassLive,
    subscribeClassBarrages,
} from '@/features/course/api/classSession'
import {
    ClassLiveStatus,
    ClassSessionStatus,
    type ClassBarrage,
    type ClassParticipant,
    type ClassSession,
} from '@/features/course/types/classSession'

const participants: ClassParticipant[] = [{
    id: 'teacher-participant',
    sessionId: 'session-id',
    userId: 'teacher-id',
    role: 0,
    seatIndex: null,
    x: 0,
    y: 0,
    z: 0,
    displayName: 'Teacher Li',
    avatarUrl: null,
    joinedAt: '2026-06-24T10:00:00Z',
}, {
    id: 'student-participant',
    sessionId: 'session-id',
    userId: 'student-id',
    role: 1,
    seatIndex: 1,
    x: 1,
    y: 1,
    z: 0,
    displayName: 'Student Chen',
    avatarUrl: null,
    joinedAt: '2026-06-24T10:00:00Z',
}]

const liveMock = {
    localVideoEl: shallowRef<HTMLVideoElement | null>(null),
    localCameraVideoEl: shallowRef<HTMLVideoElement | null>(null),
    remoteVideoEl: shallowRef<HTMLVideoElement | null>(null),
    remoteCameraVideoEl: shallowRef<HTMLVideoElement | null>(null),
    remoteAudioEl: shallowRef<HTMLAudioElement | null>(null),
    connected: ref(true),
    connecting: ref(false),
    cameraEnabled: ref(false),
    microphoneEnabled: ref(false),
    screenShareEnabled: ref(false),
    remoteCameraVisible: ref(false),
    remoteScreenShareVisible: ref(false),
    cameraOverlayPosition: ref('bottom-right'),
    onlineParticipants: computed(() => [{
        identity: 'teacher-id',
        userId: 'teacher-id',
        displayName: 'Teacher Li',
        avatarUrl: null,
        role: 0,
        isLocal: true,
        connectionQuality: 'excellent',
    }, {
        identity: 'student-id',
        userId: 'student-id',
        displayName: 'Student Chen',
        avatarUrl: null,
        role: 1,
        isLocal: false,
        connectionQuality: 'poor',
    }]),
    onlineCount: computed(() => 2),
    connectionQualityByIdentity: ref({}),
    networkStats: ref({rttMs: 24, jitterMs: null, packetLossPercent: null}),
    errorMessage: ref(''),
    connect: vi.fn(),
    disconnect: vi.fn(),
    pausePublishing: vi.fn(),
    toggleCamera: vi.fn(),
    toggleMicrophone: vi.fn(),
    toggleScreenShare: vi.fn(),
    publishDefaults: vi.fn(),
    setCameraOverlayPosition: vi.fn(),
    setSessionParticipants: vi.fn(),
    attachLocalTracks: vi.fn(),
    attachRemoteTracks: vi.fn(),
    attachRemoteAudioTracks: vi.fn(),
}

vi.mock('@/features/classroom/composables/useClassroomLive', () => ({
    useClassroomLive: () => liveMock,
}))

vi.mock('@/features/course/api/classSession', () => ({
    listClassBarrages: vi.fn(async () => ({records: []})),
    listClassSessionParticipants: vi.fn(async () => participants),
    pauseClassLive: vi.fn(),
    resumeClassLive: vi.fn(),
    sendClassBarrage: vi.fn(),
    startClassLive: vi.fn(),
    stopClassLive: vi.fn(),
    subscribeClassBarrages: vi.fn(() => ({close: vi.fn()})),
}))

vi.mock('@/shared/composables/useGlobalNotification', () => ({
    notify: {
        error: vi.fn(),
    },
}))

const listClassBarragesMock = vi.mocked(listClassBarrages)
const sendClassBarrageMock = vi.mocked(sendClassBarrage)
const startClassLiveMock = vi.mocked(startClassLive)
const stopClassLiveMock = vi.mocked(stopClassLive)
const subscribeClassBarragesMock = vi.mocked(subscribeClassBarrages)

describe('ClassroomLiveExperience live presence', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        Object.defineProperty(document, 'fullscreenElement', {
            configurable: true,
            value: null,
        })
        liveMock.connected.value = true
        liveMock.connecting.value = false
        liveMock.cameraEnabled.value = false
        liveMock.microphoneEnabled.value = false
        liveMock.screenShareEnabled.value = false
        liveMock.remoteCameraVisible.value = false
        liveMock.remoteScreenShareVisible.value = false
        liveMock.cameraOverlayPosition.value = 'bottom-right'
        liveMock.networkStats.value = {rttMs: 24, jitterMs: null, packetLossPercent: null}
        liveMock.errorMessage.value = ''
        listClassBarragesMock.mockResolvedValue({records: [], total: 0, page: 1, size: 30})
        sendClassBarrageMock.mockResolvedValue(barrage())
        startClassLiveMock.mockResolvedValue(session({liveStatus: ClassLiveStatus.LIVE}))
        stopClassLiveMock.mockResolvedValue(session({liveStatus: ClassLiveStatus.ENDED}))
        subscribeClassBarragesMock.mockReturnValue({close: vi.fn()})
    })

    it('renders live online count and RTT in the header', () => {
        const wrapper = mountExperience()

        expect(wrapper.text()).toContain('Online: 2')
        expect(wrapper.text()).toContain('RTT: 24ms')
    })

    it('shows the live stage loading layout while the stream is connecting', () => {
        liveMock.connecting.value = true

        const wrapper = mountExperience()

        expect(wrapper.find('.live-stage-loading').exists()).toBe(true)
        expect(wrapper.find('.live-stage').classes()).toContain('loading')
    })

    it('shows chat loading rows during the initial barrage load', async () => {
        let resolveBarrages!: (value: {records: ClassBarrage[]; total: number; page: number; size: number}) => void
        listClassBarragesMock.mockReturnValue(new Promise((resolve) => {
            resolveBarrages = resolve
        }))

        const wrapper = mountExperience({mode: 'popup'})
        await wrapper.vm.$nextTick()

        expect(wrapper.find('.chat-loading-list').exists()).toBe(true)

        resolveBarrages({records: [], total: 0, page: 1, size: 30})
        await flushPromises()

        expect(wrapper.find('.chat-loading-list').exists()).toBe(false)
    })

    it('renders the fullscreen online tab with participant quality', async () => {
        const wrapper = mountExperience()
        await wrapper.get('.side-tab:nth-child(3)').trigger('click')

        expect(wrapper.find('.online-panel').exists()).toBe(true)
        expect(wrapper.text()).toContain('Teacher Li')
        expect(wrapper.text()).toContain('Teacher / You')
        expect(wrapper.text()).toContain('Student Chen')
        expect(wrapper.text()).toContain('Poor')
    })

    it('hides the live operation bar when viewer is not the opening teacher', () => {
        const wrapper = mountExperience()

        expect(wrapper.find('.live-bottom-dock').exists()).toBe(false)
        expect(wrapper.find('.dock-collapse-button').exists()).toBe(false)
    })

    it('keeps loaded barrage history out of the video danmaku layer', async () => {
        listClassBarragesMock.mockResolvedValue({
            records: [barrage({id: 'barrage-history', senderDisplayName: 'Ada', content: 'Nice explanation'})],
            total: 1,
            page: 1,
            size: 30,
        })

        const wrapper = mountExperience()
        await flushPromises()

        expect(wrapper.find('.danmaku-layer').text()).not.toContain('Ada: Nice explanation')
        expect(wrapper.find('.sidebar-chat-list').text()).toContain('Nice explanation')
    })

    it('renders incoming SSE barrage messages over the video stage and in chat', async () => {
        let onMessage: (message: ClassBarrage) => void = () => undefined
        subscribeClassBarragesMock.mockImplementation((_id, callback) => {
            onMessage = callback
            return {close: vi.fn()}
        })

        const wrapper = mountExperience()
        await flushPromises()
        onMessage(barrage({id: 'barrage-live', senderDisplayName: 'Grace', content: 'I can see it'}))
        await wrapper.vm.$nextTick()

        expect(wrapper.find('.danmaku-layer').text()).toContain('Grace: I can see it')
        expect(wrapper.find('.sidebar-chat-list').text()).toContain('I can see it')
    })

    it('sends barrage messages and clears the draft after success', async () => {
        const wrapper = mountExperience()
        await flushPromises()

        const input = wrapper.get('.sidebar-chat-form input')
        await input.setValue('Question from seat 4')
        await wrapper.get('.sidebar-chat-form').trigger('submit')
        await flushPromises()

        expect(sendClassBarrageMock).toHaveBeenCalledWith('session-id', 'Question from seat 4')
        expect((input.element as HTMLInputElement).value).toBe('')
    })

    it('sends barrage messages from the player fullscreen composer', async () => {
        const wrapper = mountExperience({mode: 'popup'})
        await flushPromises()

        const input = wrapper.get('.player-danmaku-form input')
        await input.setValue('Fullscreen barrage')
        await wrapper.get('.player-danmaku-form').trigger('submit')
        await flushPromises()

        expect(sendClassBarrageMock).toHaveBeenCalledWith('session-id', 'Fullscreen barrage')
        expect((input.element as HTMLInputElement).value).toBe('')
    })

    it('renders the player fullscreen composer inside the video stage', () => {
        const wrapper = mountExperience({mode: 'popup'})

        expect(wrapper.find('.live-stage > .player-danmaku-form').exists()).toBe(true)
    })

    it('disables barrage input when the viewer cannot participate', () => {
        const wrapper = mountExperience({canParticipate: false})

        expect(wrapper.get('.sidebar-chat-form input').attributes('disabled')).toBeDefined()
        expect(wrapper.get('.sidebar-chat-form button').attributes('disabled')).toBeDefined()
        expect(wrapper.get('.player-danmaku-form input').attributes('disabled')).toBeDefined()
        expect(wrapper.get('.player-danmaku-form button').attributes('disabled')).toBeDefined()
    })

    it('keeps realtime danmaku visible after opening the expanded live page from the header', async () => {
        let onMessage: (message: ClassBarrage) => void = () => undefined
        subscribeClassBarragesMock.mockImplementation((_id, callback) => {
            onMessage = callback
            return {close: vi.fn()}
        })
        const requestFullscreen = vi.fn(() => Promise.resolve())
        Object.defineProperty(HTMLElement.prototype, 'requestFullscreen', {
            configurable: true,
            value: requestFullscreen,
        })
        const wrapper = mountExperience({mode: 'popup'})
        await flushPromises()
        onMessage(barrage({id: 'barrage-popup', senderDisplayName: 'Lin', content: 'Full screen please'}))
        await wrapper.vm.$nextTick()

        await wrapper.get('.header-actions .header-icon-button').trigger('click')
        await flushPromises()

        expect(requestFullscreen).not.toHaveBeenCalled()
        expect(wrapper.classes()).toContain('mode-fullscreen')
        expect(wrapper.find('.danmaku-layer').text()).toContain('Lin: Full screen please')
    })

    it('requests browser fullscreen from the video hover action', async () => {
        const requestFullscreen = vi.fn(() => Promise.resolve())
        Object.defineProperty(HTMLElement.prototype, 'requestFullscreen', {
            configurable: true,
            value: requestFullscreen,
        })
        const wrapper = mountExperience({mode: 'popup'})

        await wrapper.get('.stage-expand-button').trigger('click')
        await flushPromises()

        expect(requestFullscreen).toHaveBeenCalled()
        expect(wrapper.classes()).toContain('mode-popup')
    })

    it('keeps popup layout focused on video, chat, and the fixed input form', () => {
        const wrapper = mountExperience({mode: 'popup'})

        expect(wrapper.find('.live-stage').exists()).toBe(true)
        expect(wrapper.find('.chat-panel').exists()).toBe(true)
        expect(wrapper.find('.chat-form').exists()).toBe(true)
        expect(wrapper.find('.ai-summary-card').exists()).toBe(false)
    })

    it('renders teacher popup controls as an overlay without hiding the message form', () => {
        const wrapper = mountExperience({mode: 'popup', isTeacher: true})

        expect(wrapper.find('.controls-header-toggle').exists()).toBe(true)
        expect(wrapper.find('.dock-collapse-button').exists()).toBe(false)
        expect(wrapper.find('.live-bottom-dock').exists()).toBe(true)
        expect(wrapper.find('.chat-form').exists()).toBe(true)
    })

    it('starts or restarts live from the green call button', async () => {
        liveMock.cameraEnabled.value = true
        liveMock.microphoneEnabled.value = true
        liveMock.screenShareEnabled.value = true
        liveMock.publishDefaults.mockImplementation(async () => {
            liveMock.cameraEnabled.value = false
            liveMock.microphoneEnabled.value = false
            liveMock.screenShareEnabled.value = false
        })
        const wrapper = mountExperience({
            isTeacher: true,
            session: session({liveStatus: ClassLiveStatus.NOT_STARTED}),
        })
        await flushPromises()

        await wrapper.get('.call-button').trigger('click')
        await flushPromises()

        expect(startClassLiveMock).toHaveBeenCalledWith('session-id')
        expect(liveMock.publishDefaults).toHaveBeenCalled()
        expect(liveMock.microphoneEnabled.value).toBe(false)
        expect(liveMock.cameraEnabled.value).toBe(false)
        expect(liveMock.screenShareEnabled.value).toBe(false)
    })

    it('ends the current live stream from the hangup button without closing the window', async () => {
        const wrapper = mountExperience({isTeacher: true})
        await flushPromises()

        await wrapper.get('.stop-live-button').trigger('click')
        await flushPromises()

        expect(stopClassLiveMock).toHaveBeenCalledWith('session-id')
        expect(liveMock.disconnect).toHaveBeenCalled()
        expect(wrapper.emitted('close')).toBeUndefined()
    })
})

function mountExperience(props?: Partial<{
    session: ClassSession
    isTeacher: boolean
    canParticipate: boolean
    mode: 'popup' | 'fullscreen'
}>) {
    return mount(ClassroomLiveExperience, {
        props: {
            session: session(),
            isTeacher: false,
            canParticipate: true,
            mode: 'fullscreen',
            ...props,
        },
        global: {
            plugins: [createTestI18n()],
            stubs: {
                UserAvatarLink: {
                    props: ['displayName'],
                    template: '<span class="avatar-stub">{{ displayName }}</span>',
                },
            },
        },
    })
}

function createTestI18n() {
    return createI18n({
        legacy: false,
        locale: 'en-US',
        messages: {
            'en-US': {
                courseDetail: enCourseDetail,
            },
        },
    })
}

function barrage(overrides: Partial<ClassBarrage> = {}): ClassBarrage {
    return {
        id: 'barrage-id',
        sessionId: 'session-id',
        senderId: 'student-id',
        senderDisplayName: 'Student Chen',
        senderAvatarUrl: null,
        senderRoleLabel: 'Student',
        content: 'Hello live class',
        sentAt: '2026-06-24T10:05:00Z',
        ...overrides,
    }
}

function session(overrides: Partial<ClassSession> = {}): ClassSession {
    return {
        id: 'session-id',
        courseId: 'course-id',
        teacherId: 'teacher-id',
        title: 'Live Session',
        description: null,
        scheduledStartAt: '2026-06-24T10:00:00Z',
        scheduledEndAt: '2026-06-24T11:00:00Z',
        publishedAt: '2026-06-24T09:50:00Z',
        roomSize: 1,
        liveRoomName: 'class-session-session-id',
        liveStatus: ClassLiveStatus.LIVE,
        liveStatusText: 'live',
        liveStartedAt: '2026-06-24T10:00:00Z',
        livePausedAt: null,
        liveEndedAt: null,
        status: ClassSessionStatus.LIVE,
        statusText: 'live',
        joined: true,
        createdAt: '2026-06-24T09:00:00Z',
        updatedAt: '2026-06-24T10:00:00Z',
        ...overrides,
    }
}
