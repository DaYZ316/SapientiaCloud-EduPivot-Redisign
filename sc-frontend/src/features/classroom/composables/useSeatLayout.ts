import * as THREE from 'three'

import {ClassRoomSize} from '@/features/course/types/classSession'
import {getRoomSpec} from '@/features/classroom/types/classroom'

const LARGE_SEATS_PER_DESK = 4
const DEFAULT_ROOM_DIMENSIONS = {x: 18, z: 20}
const MEDIUM_COLUMNS = 8
const LARGE_DESK_COLUMNS = 4
const LARGE_DESK_ROWS = 10
const LARGE_ROW_SPACING = 1.82
const LARGE_BACK_MARGIN = 0.8
const LARGE_LEFT_SHIFT_SEGMENTS = 4
const LARGE_REAR_SHIFT = 5
const LARGE_DOWN_SHIFT = 0.6
const X_LARGE_FIRST_RING_SEATS = 16
const X_LARGE_FORWARD_SHIFT = 0

export interface RoomPlanDimensions {
    x: number | null
    z: number | null
}

export function getDeskPosition(
    roomSize: number,
    deskIndex: number,
    dimensions: RoomPlanDimensions = DEFAULT_ROOM_DIMENSIONS,
): THREE.Vector3 {
    switch (roomSize) {
        case ClassRoomSize.SMALL:
            return gridPosition(deskIndex, 4, 1.75, 1.75, 2.4, 0.4)
        case ClassRoomSize.MEDIUM:
            return middleDeskPosition(deskIndex, dimensions)
        case ClassRoomSize.LARGE:
            return largeDeskPosition(deskIndex, dimensions)
        case ClassRoomSize.XLARGE:
            return extraLargeDeskPosition(deskIndex)
        default:
            return gridPosition(deskIndex, 3, 1.75, 1.75, 0, 2.5)
    }
}

export function getSeatPosition(
    roomSize: number,
    seatIndex: number,
    dimensions: RoomPlanDimensions = DEFAULT_ROOM_DIMENSIONS,
): THREE.Vector3 {
    if (roomSize === ClassRoomSize.LARGE) {
        const deskIndex = Math.floor(seatIndex / LARGE_SEATS_PER_DESK)
        const seatInDesk = seatIndex % LARGE_SEATS_PER_DESK
        const position = getDeskPosition(roomSize, deskIndex, dimensions)
        const modelWidth = 4
        const seatSpacing = modelWidth / (LARGE_SEATS_PER_DESK + 1)
        const leftOffset = -modelWidth / 2
        position.x += leftOffset + seatSpacing * (seatInDesk + 1) - 0.1
        position.y += 3.4
        position.z += 0.4
        return position
    }

    const position = getDeskPosition(roomSize, seatIndex, dimensions)
    if (roomSize === ClassRoomSize.MEDIUM) {
        position.x += 0.4
        position.y += 1.6
        position.z -= 0.2
        return position
    }
    if (roomSize === ClassRoomSize.XLARGE) {
        position.y += 1.4
        position.z += 0.2
        return position
    }
    position.y += 1.05
    position.z += roomSize === ClassRoomSize.SMALL ? 0.25 : 0
    return position
}

export function getAllSeatPositions(
    roomSize: number,
    dimensions: RoomPlanDimensions = DEFAULT_ROOM_DIMENSIONS,
): THREE.Vector3[] {
    const spec = getRoomSpec(roomSize)
    return Array.from({length: spec.seatCount}, (_, index) => getSeatPosition(roomSize, index, dimensions))
}

export function getDeskYaw(
    roomSize: number,
    deskIndex: number,
    dimensions: RoomPlanDimensions = DEFAULT_ROOM_DIMENSIONS,
): number {
    if (roomSize === ClassRoomSize.SMALL) {
        return 0
    }
    if (roomSize !== ClassRoomSize.XLARGE) {
        return Math.PI / 2
    }
    const position = getDeskPosition(roomSize, deskIndex, dimensions)
    return Math.atan2(-position.x, 10 - position.z) + Math.PI * 1.5
}

function gridPosition(
    index: number,
    columns: number,
    xSpacing: number,
    zSpacing: number,
    centerX: number,
    frontZ: number,
): THREE.Vector3 {
    const row = Math.floor(index / columns)
    const column = index % columns
    const x = centerX + (column - (columns - 1) / 2) * xSpacing
    const z = frontZ - row * zSpacing
    const y = row * 0.04
    return new THREE.Vector3(x, y, z)
}

function middleDeskPosition(index: number, dimensions: RoomPlanDimensions): THREE.Vector3 {
    const safeX = (dimensions.x && dimensions.x > 0 ? dimensions.x : 20) - 2
    const safeZ = dimensions.z && dimensions.z > 0 ? dimensions.z : 15
    const columnIndex = index % MEDIUM_COLUMNS
    const rowIndex = Math.floor(index / MEDIUM_COLUMNS)

    return new THREE.Vector3(
        safeX / 2 - safeX / MEDIUM_COLUMNS * (columnIndex),
        0,
        safeZ / 2 - 1.8 * rowIndex - 5,
    )
}

function largeDeskPosition(index: number, dimensions: RoomPlanDimensions): THREE.Vector3 {
    const width = dimensions.x && dimensions.x > 0 ? dimensions.x : 20
    const depth = dimensions.z && dimensions.z > 0 ? dimensions.z : 30
    const rowIndex = Math.floor(index / LARGE_DESK_COLUMNS)
    const columnIndex = index % LARGE_DESK_COLUMNS
    const segmentCount = 10
    const segmentWidth = width / segmentCount
    const deskCenterSegments = [0, 3, 5, 8]
    const centerSegment = deskCenterSegments[columnIndex]
    const halfWidth = width / 2
    const halfDepth = depth / 2
    const startZ = halfDepth - 10.4
    const backZ = -halfDepth + LARGE_BACK_MARGIN
    const compactRowSpacing = (startZ - backZ) / (LARGE_DESK_ROWS - 1)
    const rowSpacing = Math.min(LARGE_ROW_SPACING, Math.max(compactRowSpacing, 0))
    const z = startZ - rowIndex * rowSpacing - LARGE_REAR_SHIFT

    return new THREE.Vector3(
        -halfWidth + centerSegment * segmentWidth + segmentWidth - segmentWidth * LARGE_LEFT_SHIFT_SEGMENTS,
        0.55 + rowIndex * 0.18 - LARGE_DOWN_SHIFT,
        z,
    )
}

function extraLargeDeskPosition(index: number): THREE.Vector3 {
    const ring = findFanRing(index)
    const firstIndex = fanRingStart(ring)
    let indexInRing = index - firstIndex + 1
    const count = fanRingCount(ring)
    const radius = 10.05 + (ring / 9) * (27.8 - 10.05)
    const angleSpanDeg = 120
    const angleStart = THREE.MathUtils.degToRad(180 - (180 - angleSpanDeg) / 2)
    const angleStep = THREE.MathUtils.degToRad(angleSpanDeg) / (count + 1)
    const angleMedium = angleStart - (THREE.MathUtils.degToRad(angleSpanDeg) / 13) * 7
    let angle: number

    indexInRing -= 0.5
    if (indexInRing <= count / 2) {
        angle = angleStart - angleStep * indexInRing
    } else {
        indexInRing -= count / 2
        angle = angleMedium - angleStep * indexInRing
    }

    return new THREE.Vector3(
        Math.cos(angle) * radius,
        0.3 + ring * 0.6,
        -Math.sin(angle) * radius + 10 + X_LARGE_FORWARD_SHIFT,
    )
}

function findFanRing(index: number): number {
    let ring = 0
    let remaining = index
    while (remaining >= fanRingCount(ring)) {
        remaining -= fanRingCount(ring)
        ring += 1
    }
    return ring
}

function fanRingStart(ring: number): number {
    let start = 0
    for (let index = 0; index < ring; index += 1) {
        start += fanRingCount(index)
    }
    return start
}

function fanRingCount(ring: number): number {
    return X_LARGE_FIRST_RING_SEATS + ring * 2
}
