import * as THREE from 'three'

import {ClassRoomSize} from '@/features/course/types/classSession'

export type BlackboardCanvasRenderer = (
    context: CanvasRenderingContext2D,
    canvas: HTMLCanvasElement,
    index: number,
) => void

interface BlackboardCanvasSlot {
    offsetX: number
    offsetY: number
    width: number
    height: number
}

interface BlackboardCanvasLayout {
    position: THREE.Vector3
    quaternion: THREE.Quaternion
    slots: BlackboardCanvasSlot[]
}

interface BlackboardCanvasHandle {
    canvas: HTMLCanvasElement
    texture: THREE.CanvasTexture
    material: THREE.MeshBasicMaterial
    mesh: THREE.Mesh
}

const DEFAULT_CANVAS_WIDTH = 1024
const DEFAULT_CANVAS_HEIGHT = 512

export const BLACKBOARD_CANVAS_LAYOUTS: Record<number, BlackboardCanvasLayout> = {
    [ClassRoomSize.SMALL]: {
        position: new THREE.Vector3(-4.6, 2, -0.33),
        quaternion: new THREE.Quaternion().setFromAxisAngle(new THREE.Vector3(0, 1, 0), Math.PI / 2),
        slots: [
            {offsetX: 0, offsetY: 0, width: 2.8, height: 1.4},
        ],
    },
    [ClassRoomSize.MEDIUM]: {
        position: new THREE.Vector3(0, 2.8, 9.5),
        quaternion: new THREE.Quaternion().setFromAxisAngle(new THREE.Vector3(0, 1, 0), Math.PI),
        slots: [
            {offsetX: 0, offsetY: 0, width: 7, height: 2.2},
        ],
    },
    [ClassRoomSize.LARGE]: {
        position: new THREE.Vector3(0, 3, 14.9),
        quaternion: new THREE.Quaternion().setFromAxisAngle(new THREE.Vector3(0, 1, 0), Math.PI),
        slots: [
            {offsetX: 0, offsetY: 2, width: 9.8, height: 2.2},
            {offsetX: 0, offsetY: 0, width: 9.8, height: 2.2},
        ],
    },
    [ClassRoomSize.XLARGE]: {
        position: new THREE.Vector3(0, 5, 9.8),
        quaternion: new THREE.Quaternion().setFromAxisAngle(new THREE.Vector3(0, 1, 0), Math.PI),
        slots: [
            {offsetX: -5, offsetY: -1.2, width: 4.2, height: 2.5},
            {offsetX: 0, offsetY: 0, width: 4.2, height: 2.5},
            {offsetX: 0, offsetY: -3, width: 4.2, height: 2.5},
            {offsetX: 5, offsetY: -1.8, width: 4.2, height: 2.5},
        ],
    },
}

export class BlackboardCanvasManager {
    private scene: THREE.Scene | null = null
    private group: THREE.Group | null = null
    private handles: BlackboardCanvasHandle[] = []

    mount(scene: THREE.Scene, roomSize: number, renderer?: BlackboardCanvasRenderer) {
        this.dispose()
        this.scene = scene

        const layout = BLACKBOARD_CANVAS_LAYOUTS[roomSize] ?? BLACKBOARD_CANVAS_LAYOUTS[ClassRoomSize.SMALL]
        const group = new THREE.Group()
        group.name = 'blackboard_canvas_layer'
        group.position.copy(layout.position)
        group.quaternion.copy(layout.quaternion)

        layout.slots.forEach((slot, index) => {
            const handle = this.createCanvasHandle(slot, index, renderer)
            group.add(handle.mesh)
            this.handles.push(handle)
        })

        scene.add(group)
        this.group = group
    }

    render(renderer?: BlackboardCanvasRenderer) {
        this.handles.forEach((handle, index) => {
            const context = handle.canvas.getContext('2d')
            if (!context) {
                return
            }
            context.clearRect(0, 0, handle.canvas.width, handle.canvas.height)
            renderer?.(context, handle.canvas, index)
            handle.texture.needsUpdate = true
        })
    }

    clear() {
        this.render()
    }

    getCanvases() {
        return this.handles.map((handle) => handle.canvas)
    }

    dispose() {
        if (this.group && this.scene) {
            this.scene.remove(this.group)
        }
        this.group = null
        this.scene = null

        this.handles.forEach((handle) => {
            handle.mesh.geometry.dispose()
            handle.material.dispose()
            handle.texture.dispose()
        })
        this.handles = []
    }

    private createCanvasHandle(slot: BlackboardCanvasSlot, index: number, renderer?: BlackboardCanvasRenderer): BlackboardCanvasHandle {
        const canvas = document.createElement('canvas')
        canvas.width = DEFAULT_CANVAS_WIDTH
        canvas.height = DEFAULT_CANVAS_HEIGHT

        const texture = new THREE.CanvasTexture(canvas)
        texture.colorSpace = THREE.SRGBColorSpace
        texture.minFilter = THREE.LinearFilter
        texture.magFilter = THREE.LinearFilter

        const material = new THREE.MeshBasicMaterial({
            map: texture,
            transparent: true,
            opacity: 1,
            depthTest: true,
            depthWrite: false,
            side: THREE.DoubleSide,
        })

        const geometry = new THREE.PlaneGeometry(slot.width, slot.height)
        const mesh = new THREE.Mesh(geometry, material)
        mesh.name = `blackboard_canvas_${index}`
        mesh.position.set(slot.offsetX, slot.offsetY, 0.02)

        const handle = {canvas, texture, material, mesh}
        this.clearHandle(handle, index, renderer)
        return handle
    }

    private clearHandle(handle: BlackboardCanvasHandle, index: number, renderer?: BlackboardCanvasRenderer) {
        const context = handle.canvas.getContext('2d')
        if (!context) {
            return
        }
        context.clearRect(0, 0, handle.canvas.width, handle.canvas.height)
        renderer?.(context, handle.canvas, index)
        handle.texture.needsUpdate = true
    }
}
