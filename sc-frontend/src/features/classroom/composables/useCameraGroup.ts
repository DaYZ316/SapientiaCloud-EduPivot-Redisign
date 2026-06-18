import * as THREE from 'three'

import {ClassRoomSize} from '@/features/course/types/classSession'

interface CameraVector {
    x: number
    y: number
    z: number
}

interface CameraConfig {
    position: CameraVector
    initialRotation: THREE.Euler
}

export interface ClassroomCameraPositions {
    front: CameraConfig
    rightRear: CameraConfig
    leftRear: CameraConfig
}

export interface ClassroomDimensions {
    x: number | null
    y: number | null
    z: number | null
}

const createCameraRotationToCenter = (position: CameraVector, targetY: number): THREE.Euler => {
    const dx = 0 - position.x
    const dy = targetY - position.y
    const dz = 0 - position.z

    const yaw = Math.atan2(dx, dz)
    const horizontalDist = Math.sqrt(dx * dx + dz * dz)
    const pitch = Math.atan2(dy, horizontalDist)

    return new THREE.Euler(pitch, yaw + Math.PI, 0, 'YXZ')
}

export const computeCameraPositionsBySize = (
    dimensions: ClassroomDimensions,
    roomSize: number,
): ClassroomCameraPositions => {
    const safeX = dimensions.x && dimensions.x > 0 ? dimensions.x : 18
    const safeY = dimensions.y && dimensions.y > 0 ? dimensions.y : 10
    const safeZ = dimensions.z && dimensions.z > 0 ? dimensions.z : 20

    const halfX = safeX / 2
    const halfZ = safeZ / 2
    const wallOffsetX = safeX * 0.1
    const wallOffsetZ = safeZ * 0.08
    const baseHeight = Math.max(1.5, safeY * 0.8)
    const targetY = safeY / 2

    const frontPos: CameraVector = {
        x: 0,
        y: baseHeight,
        z: halfZ - wallOffsetZ,
    }

    const rightRearPos: CameraVector = {
        x: halfX - wallOffsetX,
        y: baseHeight,
        z: -(halfZ - wallOffsetZ),
    }

    const leftRearPos: CameraVector = {
        x: -(halfX - wallOffsetX),
        y: baseHeight,
        z: -(halfZ - wallOffsetZ),
    }

    if (roomSize === ClassRoomSize.XLARGE) {
        const extraBack = Math.max(8, safeZ * 0.4)
        const extraHeight = Math.max(2, safeY * 0.2)
        frontPos.y = baseHeight + extraHeight - 4
        frontPos.z = halfZ - wallOffsetZ - extraBack + 8

        rightRearPos.x = halfX + wallOffsetX * 0.5 - 6
        rightRearPos.y = baseHeight + extraHeight - 2
        rightRearPos.z = -(halfZ - wallOffsetZ + extraBack) + 19

        leftRearPos.x = -rightRearPos.x
        leftRearPos.y = rightRearPos.y
        leftRearPos.z = rightRearPos.z
    }

    return {
        front: {
            position: frontPos,
            initialRotation: createCameraRotationToCenter(frontPos, targetY),
        },
        rightRear: {
            position: rightRearPos,
            initialRotation: createCameraRotationToCenter(rightRearPos, targetY),
        },
        leftRear: {
            position: leftRearPos,
            initialRotation: createCameraRotationToCenter(leftRearPos, targetY),
        },
    }
}

export const getCameraTarget = (dimensions: ClassroomDimensions): THREE.Vector3 => {
    const safeY = dimensions.y && dimensions.y > 0 ? dimensions.y : 10
    return new THREE.Vector3(0, safeY / 2, 0)
}
