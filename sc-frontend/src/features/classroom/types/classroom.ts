import type {ClassParticipant} from '@/features/course/types/classSession'
import {ClassRoomSize} from '@/features/course/types/classSession'

export interface ClassroomRoomSpec {
    label: string
    seatCount: number
    deskInstanceCount: number
    seatsPerDesk: number
}

export const CLASSROOM_ROOM_SPECS: Record<number, ClassroomRoomSpec> = {
    [ClassRoomSize.SMALL]: {
        label: 'Small',
        seatCount: 12,
        deskInstanceCount: 12,
        seatsPerDesk: 1,
    },
    [ClassRoomSize.MEDIUM]: {
        label: 'Medium',
        seatCount: 64,
        deskInstanceCount: 64,
        seatsPerDesk: 1,
    },
    [ClassRoomSize.LARGE]: {
        label: 'Large',
        seatCount: 160,
        deskInstanceCount: 40,
        seatsPerDesk: 4,
    },
    [ClassRoomSize.XLARGE]: {
        label: 'Extra Large',
        seatCount: 250,
        deskInstanceCount: 250,
        seatsPerDesk: 1,
    },
}

export interface ClassroomAssetRoute {
    model: string
    texture?: string
}

export interface ClassroomModelRoute {
    classroom: ClassroomAssetRoute
    desk: ClassroomAssetRoute
}

export interface SeatSyncMessage {
    type: 'seat_snapshot' | 'seat_upsert' | 'seat_remove'
    sessionId: string
    participants?: ClassParticipant[]
    participant?: ClassParticipant
    userId?: string
    seatIndex?: number | null
}

export function getRoomSpec(roomSize: number): ClassroomRoomSpec {
    return CLASSROOM_ROOM_SPECS[roomSize] ?? CLASSROOM_ROOM_SPECS[ClassRoomSize.SMALL]
}
