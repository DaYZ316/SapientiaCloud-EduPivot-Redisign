import {describe, expect, it} from 'vitest'

import {ClassRoomSize} from '@/features/course/types/classSession'
import {getDeskPosition, getSeatPosition} from '@/features/classroom/composables/useSeatLayout'

describe('useSeatLayout', () => {
    it('keeps all large classroom desk rows distinct when depth is constrained', () => {
        const rowZPositions = Array.from({length: 10}, (_, rowIndex) =>
            getDeskPosition(ClassRoomSize.LARGE, rowIndex * 4, {x: 18, z: 20}).z.toFixed(4),
        )

        expect(new Set(rowZPositions).size).toBe(10)
    })

    it('moves extra large classroom seats slightly forward', () => {
        expect(getDeskPosition(ClassRoomSize.XLARGE, 0).x).toBeCloseTo(-9.111519342876624)
        expect(getDeskPosition(ClassRoomSize.XLARGE, 0).y).toBeCloseTo(1.2)
        expect(getDeskPosition(ClassRoomSize.XLARGE, 0).z).toBeCloseTo(3.6208485693373815)
        expect(getSeatPosition(ClassRoomSize.XLARGE, 0).x).toBeCloseTo(-9.111519342876624)
        expect(getSeatPosition(ClassRoomSize.XLARGE, 0).y).toBeCloseTo(2.6)
        expect(getSeatPosition(ClassRoomSize.XLARGE, 0).z).toBeCloseTo(3.8208485693373815)
    })
})
