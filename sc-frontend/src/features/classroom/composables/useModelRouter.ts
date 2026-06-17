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
            model: `${CLASSROOM_BASE}/classroomMiddle.gltf`,
            texture: `${CLASSROOM_BASE}/texture/classroomMiddle_baked.jpg`,
        },
        desk: {
            model: `${DESK_BASE}/deskChairMiddle.gltf`,
            texture: `${DESK_BASE}/texture/deskChairMiddle_baked.jpg`,
        },
    },
    [ClassRoomSize.LARGE]: {
        classroom: {
            model: `${CLASSROOM_BASE}/classroomPro.gltf`,
            texture: `${CLASSROOM_BASE}/texture/classroomPro_baked.jpg`,
        },
        desk: {
            model: `${DESK_BASE}/deskChairPro.gltf`,
            texture: `${DESK_BASE}/texture/deskChairPro_baked.jpg`,
        },
    },
    [ClassRoomSize.XLARGE]: {
        classroom: {
            model: `${CLASSROOM_BASE}/classroomUltra.gltf`,
            texture: `${CLASSROOM_BASE}/texture/classroomUltra_baked.jpg`,
        },
        desk: {
            model: `${DESK_BASE}/deskChairUltra.gltf`,
            texture: `${DESK_BASE}/texture/deskChairUltra_baked.jpg`,
        },
    },
}

export function getClassroomModelRoute(roomSize: number): ClassroomModelRoute {
    return MODEL_ROUTES[roomSize] ?? MODEL_ROUTES[ClassRoomSize.SMALL]
}
