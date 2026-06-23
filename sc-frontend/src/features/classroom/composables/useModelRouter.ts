import {ClassRoomSize} from '@/features/course/types/classSession'
import type {ClassroomModelRoute} from '@/features/classroom/types/classroom'

const CLASSROOM_BASE = '/assets/3d/classroom'
const DESK_BASE = '/assets/3d/desk-chair'

const MODEL_ROUTES: Record<number, ClassroomModelRoute> = {
    [ClassRoomSize.SMALL]: {
        classroom: {
            model: `${CLASSROOM_BASE}/classroomMini.glb`,
        },
        desk: {
            model: `${DESK_BASE}/deskChairMini.glb`,
        },
    },
    [ClassRoomSize.MEDIUM]: {
        classroom: {
            model: `${CLASSROOM_BASE}/classroomMiddle.glb`,
        },
        desk: {
            model: `${DESK_BASE}/deskChairMiddle.glb`,
        },
    },
    [ClassRoomSize.LARGE]: {
        classroom: {
            model: `${CLASSROOM_BASE}/classroomPro.glb`,
        },
        desk: {
            model: `${DESK_BASE}/deskChairPro.glb`,
        },
    },
    [ClassRoomSize.XLARGE]: {
        classroom: {
            model: `${CLASSROOM_BASE}/classroomUltra.glb`,
        },
        desk: {
            model: `${DESK_BASE}/deskChairUltra.glb`,
        },
    },
}

export function getClassroomModelRoute(roomSize: number): ClassroomModelRoute {
    return MODEL_ROUTES[roomSize] ?? MODEL_ROUTES[ClassRoomSize.SMALL]
}
