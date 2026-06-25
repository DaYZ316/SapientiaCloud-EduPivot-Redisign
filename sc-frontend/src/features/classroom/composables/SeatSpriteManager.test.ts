import * as THREE from 'three'
import {describe, expect, it} from 'vitest'

import {mapSeatPositionToSpritePosition} from '@/features/classroom/composables/SeatSpriteManager'
import {ClassRoomSize} from '@/features/course/types/classSession'

describe('SeatSpriteManager', () => {
    it('places large classroom avatars directly above their seat position', () => {
        const seatPosition = new THREE.Vector3(-6.1, 3.55, -8.4)
        const spritePosition = mapSeatPositionToSpritePosition(seatPosition, ClassRoomSize.LARGE)

        expect(spritePosition.x).toBe(seatPosition.x)
        expect(spritePosition.z).toBe(seatPosition.z)
        expect(spritePosition.y).toBeGreaterThan(seatPosition.y)
    })
})
