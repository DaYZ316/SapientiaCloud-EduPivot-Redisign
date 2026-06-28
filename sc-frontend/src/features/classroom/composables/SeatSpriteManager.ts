import * as THREE from 'three'

import {ClassRoomSize, type ClassParticipant} from '@/features/course/types/classSession'
import {seatIndexToPositionIndex} from '@/features/classroom/composables/useSeatLayout'
import {getAvatarInitials} from '@/shared/utils/avatar'

const TEXTURE_SIZE = 192
const SPRITE_POSITION_OFFSETS: Record<number, THREE.Vector3> = {
    [ClassRoomSize.SMALL]: new THREE.Vector3(-1.5, 0.3, 1.75),
    [ClassRoomSize.MEDIUM]: new THREE.Vector3(-1.5, 0.3, 0.4),
    [ClassRoomSize.LARGE]: new THREE.Vector3(0, -2.82, -1.0),
    [ClassRoomSize.XLARGE]: new THREE.Vector3(0, 0.72, 0),
}

export function mapSeatPositionToSpritePosition(
    seatPosition: THREE.Vector3,
    roomSize: number,
): THREE.Vector3 {
    const spritePosition = seatPosition.clone()
    spritePosition.add(SPRITE_POSITION_OFFSETS[roomSize] ?? SPRITE_POSITION_OFFSETS[ClassRoomSize.MEDIUM])
    return spritePosition
}

export class SeatSpriteManager {
    private readonly scene: THREE.Scene
    private readonly sprites: THREE.Sprite[] = []
    private readonly materials: THREE.SpriteMaterial[] = []
    private readonly userSeatMap = new Map<string, number>()
    private readonly textures = new Map<number, THREE.Texture>()
    private readonly defaultTexture: THREE.Texture

    constructor(scene: THREE.Scene, positions: THREE.Vector3[], roomSize: number) {
        this.scene = scene
        this.defaultTexture = createAvatarTexture('', '#64748b')
        positions.forEach((position, index) => {
            const material = new THREE.SpriteMaterial({
                map: this.defaultTexture,
                transparent: true,
                opacity: 0,
                depthWrite: false,
                depthTest: false,
            })
            const sprite = new THREE.Sprite(material)
            sprite.name = `seat_avatar_${index}`
            sprite.position.copy(mapSeatPositionToSpritePosition(position, roomSize))
            sprite.scale.set(0.78, 0.78, 1)
            sprite.visible = false
            this.scene.add(sprite)
            this.sprites[index] = sprite
            this.materials[index] = material
        })
    }

    async upsert(participant: ClassParticipant) {
        if (participant.seatIndex == null) {
            return
        }
        const seatIndex = participant.seatIndex
        const positionIndex = seatIndexToPositionIndex(seatIndex)
        if (positionIndex < 0 || !this.sprites[positionIndex]) {
            return
        }
        this.removeByUserId(participant.userId)
        const texture = await createParticipantTexture(participant)
        this.disposeSeatTexture(seatIndex)
        this.textures.set(seatIndex, texture)
        this.userSeatMap.set(participant.userId, seatIndex)
        const material = this.materials[positionIndex]
        material.map = texture
        material.opacity = 1
        material.needsUpdate = true
        this.sprites[positionIndex].visible = true
    }

    applySnapshot(participants: ClassParticipant[]) {
        this.clearOccupants()
        participants.forEach((participant) => {
            void this.upsert(participant)
        })
    }

    removeByUserId(userId: string) {
        const seatIndex = this.userSeatMap.get(userId)
        if (seatIndex == null) {
            return
        }
        this.hideSeat(seatIndex)
        this.userSeatMap.delete(userId)
    }

    removeBySeatIndex(seatIndex: number | null | undefined) {
        if (seatIndex == null) {
            return
        }
        for (const [userId, userSeatIndex] of this.userSeatMap.entries()) {
            if (userSeatIndex === seatIndex) {
                this.userSeatMap.delete(userId)
                break
            }
        }
        this.hideSeat(seatIndex)
    }

    updateCameraFacing(camera: THREE.Camera) {
        for (const sprite of this.sprites) {
            if (sprite.visible) {
                sprite.lookAt(camera.position)
            }
        }
    }

    dispose() {
        for (const sprite of this.sprites) {
            this.scene.remove(sprite)
        }
        for (const material of this.materials) {
            material.dispose()
        }
        for (const texture of this.textures.values()) {
            texture.dispose()
        }
        this.defaultTexture.dispose()
        this.textures.clear()
        this.userSeatMap.clear()
    }

    private clearOccupants() {
        for (let index = 0; index < this.sprites.length; index += 1) {
            this.hideSeat(index + 1)
        }
        this.userSeatMap.clear()
    }

    private hideSeat(seatIndex: number) {
        const positionIndex = seatIndexToPositionIndex(seatIndex)
        const sprite = this.sprites[positionIndex]
        const material = this.materials[positionIndex]
        if (!sprite || !material) {
            return
        }
        this.disposeSeatTexture(seatIndex)
        material.map = this.defaultTexture
        material.opacity = 0
        material.needsUpdate = true
        sprite.visible = false
    }

    private disposeSeatTexture(seatIndex: number) {
        const texture = this.textures.get(seatIndex)
        if (texture) {
            texture.dispose()
            this.textures.delete(seatIndex)
        }
    }
}

async function createParticipantTexture(participant: ClassParticipant) {
    if (participant.avatarUrl) {
        const loadedTexture = await loadImageTexture(participant.avatarUrl)
        if (loadedTexture) {
            return loadedTexture
        }
    }
    return createAvatarTexture(getAvatarInitials(participant.displayName || participant.userId))
}

function loadImageTexture(url: string): Promise<THREE.Texture | null> {
    return new Promise((resolve) => {
        const image = new Image()
        image.crossOrigin = 'anonymous'
        image.onload = () => resolve(createCircularImageTexture(image))
        image.onerror = () => resolve(null)
        image.src = url
    })
}

function createCircularImageTexture(image: HTMLImageElement) {
    const canvas = document.createElement('canvas')
    canvas.width = TEXTURE_SIZE
    canvas.height = TEXTURE_SIZE
    const context = canvas.getContext('2d')
    if (!context) {
        return new THREE.CanvasTexture(canvas)
    }
    context.clearRect(0, 0, TEXTURE_SIZE, TEXTURE_SIZE)
    context.save()
    context.beginPath()
    context.arc(TEXTURE_SIZE / 2, TEXTURE_SIZE / 2, TEXTURE_SIZE / 2 - 4, 0, Math.PI * 2)
    context.clip()
    const scale = Math.max(TEXTURE_SIZE / image.width, TEXTURE_SIZE / image.height)
    const width = image.width * scale
    const height = image.height * scale
    context.drawImage(image, (TEXTURE_SIZE - width) / 2, (TEXTURE_SIZE - height) / 2, width, height)
    context.restore()
    drawAvatarBorder(context)
    return canvasTexture(canvas)
}

function createAvatarTexture(label: string, color = themeValue('--color-primary', '#f5f5f5')) {
    const canvas = document.createElement('canvas')
    canvas.width = TEXTURE_SIZE
    canvas.height = TEXTURE_SIZE
    const context = canvas.getContext('2d')
    if (!context) {
        return new THREE.CanvasTexture(canvas)
    }
    context.clearRect(0, 0, TEXTURE_SIZE, TEXTURE_SIZE)
    context.beginPath()
    context.arc(TEXTURE_SIZE / 2, TEXTURE_SIZE / 2, TEXTURE_SIZE / 2 - 4, 0, Math.PI * 2)
    context.fillStyle = color
    context.fill()
    drawAvatarBorder(context)
    context.fillStyle = themeValue('--color-on-primary', '#0e0e0e')
    context.font = `700 64px ${themeValue('--font-heading', 'serif')}`
    context.textAlign = 'center'
    context.textBaseline = 'middle'
    context.fillText(label.slice(0, 2).toUpperCase(), TEXTURE_SIZE / 2, TEXTURE_SIZE / 2 + 2)
    return canvasTexture(canvas)
}

function canvasTexture(canvas: HTMLCanvasElement) {
    const texture = new THREE.CanvasTexture(canvas)
    texture.colorSpace = THREE.SRGBColorSpace
    texture.minFilter = THREE.LinearFilter
    texture.magFilter = THREE.LinearFilter
    texture.needsUpdate = true
    return texture
}

function drawAvatarBorder(context: CanvasRenderingContext2D) {
    context.beginPath()
    context.arc(TEXTURE_SIZE / 2, TEXTURE_SIZE / 2, TEXTURE_SIZE / 2 - 7, 0, Math.PI * 2)
    context.lineWidth = 10
    context.strokeStyle = themeValue('--color-outline-light', 'rgba(255, 255, 255, 0.92)')
    context.stroke()
}

function themeValue(name: string, fallback: string) {
    return getComputedStyle(document.documentElement).getPropertyValue(name).trim() || fallback
}
