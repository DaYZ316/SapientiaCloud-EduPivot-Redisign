import * as THREE from 'three'

import {ClassRoomSize} from '@/features/course/types/classSession'
import {getRoomSpec} from '@/features/classroom/types/classroom'

const LARGE_SEATS_PER_DESK = 4
const CLOCKWISE_RIGHT_ANGLE = -Math.PI / 2

export function getDeskPosition(roomSize: number, deskIndex: number): THREE.Vector3 {
    switch (roomSize) {
        case ClassRoomSize.SMALL:
            return gridPosition(deskIndex, 4, 1.75, 1.75, 2.4, 0.4)
        case ClassRoomSize.MEDIUM:
            return gridPosition(deskIndex, 8, 2.05, 1.9, 0, 6)
        case ClassRoomSize.LARGE:
            return gridPosition(deskIndex, 4, 6, 2.25, 0, 9)
        case ClassRoomSize.XLARGE:
            return fanPosition(deskIndex)
        default:
            return gridPosition(deskIndex, 3, 1.75, 1.75, 0, 2.5)
    }
}

export function getSeatPosition(roomSize: number, seatIndex: number): THREE.Vector3 {
    if (roomSize === ClassRoomSize.LARGE) {
        const deskIndex = Math.floor(seatIndex / LARGE_SEATS_PER_DESK)
        const seatInDesk = seatIndex % LARGE_SEATS_PER_DESK
        const position = getDeskPosition(roomSize, deskIndex)
        const offsets = [-1.35, -0.45, 0.45, 1.35]
        position.x += offsets[seatInDesk] ?? 0
        position.y += 1.3
        position.z += 0.35
        return position
    }

    const position = getDeskPosition(roomSize, seatIndex)
    position.y += roomSize === ClassRoomSize.XLARGE ? 1 : 1.05
    position.z += roomSize === ClassRoomSize.SMALL ? 0.25 : 0
    return position
}

export function getAllSeatPositions(roomSize: number): THREE.Vector3[] {
    const spec = getRoomSpec(roomSize)
    return Array.from({length: spec.seatCount}, (_, index) => getSeatPosition(roomSize, index))
}

export function getDeskYaw(roomSize: number, deskIndex: number): number {
    if (roomSize !== ClassRoomSize.XLARGE) {
        return Math.PI / 2 + CLOCKWISE_RIGHT_ANGLE
    }
    const position = getDeskPosition(roomSize, deskIndex)
    return Math.atan2(-position.x, 8 - position.z) + CLOCKWISE_RIGHT_ANGLE
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

function fanPosition(index: number): THREE.Vector3 {
    const ring = findFanRing(index)
    const firstIndex = fanRingStart(ring)
    const indexInRing = index - firstIndex
    const count = fanRingCount(ring)
    const radius = 10.5 + ring * 2.05
    const angleSpan = THREE.MathUtils.degToRad(112)
    const start = Math.PI / 2 + angleSpan / 2
    const step = count > 1 ? angleSpan / (count - 1) : 0
    const angle = start - step * indexInRing
    return new THREE.Vector3(Math.cos(angle) * radius, ring * 0.25, 8 - Math.sin(angle) * radius)
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
    return 16 + ring * 2
}
