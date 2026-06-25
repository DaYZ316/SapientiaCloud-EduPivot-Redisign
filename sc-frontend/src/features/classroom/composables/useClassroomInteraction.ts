import * as THREE from 'three'

import {ClassRoomSize} from '@/features/course/types/classSession'
import {getDeskPosition, getDeskYaw, getSeatPosition, type RoomPlanDimensions} from '@/features/classroom/composables/useSeatLayout'

const LARGE_SEATS_PER_DESK = 4

export interface ClassroomInteractionOptions {
    canvas: HTMLCanvasElement
    camera: THREE.Camera
    scene: THREE.Scene
    instancedMeshes: THREE.InstancedMesh[]
    exitTarget?: THREE.Object3D | null
    roomSize: number
    dimensions?: RoomPlanDimensions
    onHover: (seatIndex: number | null, event: MouseEvent) => void
    onClick: (seatIndex: number) => void
    onContextMenu: (seatIndex: number) => void
    onExit?: () => void
}

export interface ClassroomInteractionControls {
    dispose: () => void
}

interface SeatHit {
    seatIndex: number
    deskIndex: number
}

const DRAG_CLICK_THRESHOLD_PX = 5
const DRAG_CLICK_THRESHOLD_SQUARED = DRAG_CLICK_THRESHOLD_PX * DRAG_CLICK_THRESHOLD_PX

export function createClassroomInteraction(options: ClassroomInteractionOptions): ClassroomInteractionControls {
    const raycaster = new THREE.Raycaster()
    const pointer = new THREE.Vector2()
    const isLargeRoom = options.roomSize === ClassRoomSize.LARGE
    const metrics = getDeskInteractionMetrics(options.instancedMeshes, isLargeRoom)
    const highlight = createDeskHighlight(metrics, isLargeRoom)
    const targets = createInteractionTargets(options.roomSize, options.instancedMeshes[0]?.count ?? 0, metrics, options.dimensions)
    let hoveredSeat: number | null = null
    let pointerDownX = 0
    let pointerDownY = 0
    let leftPointerDown = false
    let suppressNextClick = false

    options.scene.add(highlight)
    options.scene.add(targets)

    function updateRaycaster(event: MouseEvent) {
        const rect = options.canvas.getBoundingClientRect()
        pointer.x = ((event.clientX - rect.left) / rect.width) * 2 - 1
        pointer.y = -((event.clientY - rect.top) / rect.height) * 2 + 1
        raycaster.setFromCamera(pointer, options.camera)
    }

    function detectSeat(event: MouseEvent): SeatHit | null {
        updateRaycaster(event)

        const hit = raycaster.intersectObjects(targets.children, false)[0]
        if (!hit) {
            return null
        }
        const deskIndex = Number(hit.object.userData.deskIndex)
        if (!Number.isFinite(deskIndex)) {
            return null
        }
        const seatIndex = options.roomSize === ClassRoomSize.LARGE
            ? deskIndex * 4 + largeSeatOffset(hit.point, hit.object, metrics)
            : deskIndex
        return {seatIndex, deskIndex}
    }

    function detectExit(event: MouseEvent) {
        if (!options.exitTarget || !options.onExit) {
            return false
        }
        updateRaycaster(event)
        return raycaster.intersectObject(options.exitTarget, true).length > 0
    }

    function updateHighlight(hit: SeatHit | null) {
        if (!hit) {
            highlight.visible = false
            return
        }
        if (options.roomSize === ClassRoomSize.LARGE) {
            const position = getSeatPosition(options.roomSize, hit.seatIndex, options.dimensions)
            const quaternion = new THREE.Quaternion().setFromEuler(new THREE.Euler(0, getDeskYaw(options.roomSize, hit.deskIndex, options.dimensions), 0))
            highlight.position.copy(position)
            highlight.quaternion.copy(quaternion)
            highlight.visible = true
        } else {
            const position = getDeskPosition(options.roomSize, hit.deskIndex, options.dimensions)
            const quaternion = new THREE.Quaternion().setFromEuler(new THREE.Euler(0, getDeskYaw(options.roomSize, hit.deskIndex, options.dimensions), 0))
            highlight.position.copy(position)
            highlight.quaternion.copy(quaternion)
            highlight.visible = true
        }
    }

    function handleMove(event: MouseEvent) {
        const hit = detectSeat(event)
        const seatIndex = hit?.seatIndex ?? null
        const exitHit = seatIndex == null && detectExit(event)
        options.canvas.style.cursor = seatIndex == null && !exitHit ? 'default' : 'pointer'
        if (seatIndex !== hoveredSeat) {
            hoveredSeat = seatIndex
            updateHighlight(hit)
        }
        options.onHover(seatIndex, event)
    }

    function handlePointerDown(event: PointerEvent) {
        if (event.button !== 0) {
            return
        }
        leftPointerDown = true
        suppressNextClick = false
        pointerDownX = event.clientX
        pointerDownY = event.clientY
    }

    function handlePointerMove(event: PointerEvent) {
        if (!leftPointerDown || suppressNextClick) {
            return
        }
        const deltaX = event.clientX - pointerDownX
        const deltaY = event.clientY - pointerDownY
        if (deltaX * deltaX + deltaY * deltaY > DRAG_CLICK_THRESHOLD_SQUARED) {
            suppressNextClick = true
        }
    }

    function handlePointerUp() {
        leftPointerDown = false
    }

    function handleClick(event: MouseEvent) {
        if (suppressNextClick) {
            event.preventDefault()
            event.stopPropagation()
            suppressNextClick = false
            return
        }
        if (detectExit(event)) {
            event.preventDefault()
            options.onExit?.()
            return
        }
        const hit = detectSeat(event)
        if (hit) {
            options.onClick(hit.seatIndex)
        }
    }

    function handleContextMenu(event: MouseEvent) {
        event.preventDefault()
        const hit = detectSeat(event)
        if (hit) {
            options.onContextMenu(hit.seatIndex)
        }
    }

    function handleLeave(event: MouseEvent) {
        hoveredSeat = null
        highlight.visible = false
        options.canvas.style.cursor = 'default'
        options.onHover(null, event)
    }

    options.canvas.addEventListener('pointerdown', handlePointerDown)
    options.canvas.addEventListener('mousemove', handleMove)
    options.canvas.addEventListener('click', handleClick)
    options.canvas.addEventListener('contextmenu', handleContextMenu)
    options.canvas.addEventListener('mouseleave', handleLeave)
    window.addEventListener('pointermove', handlePointerMove)
    window.addEventListener('pointerup', handlePointerUp)
    window.addEventListener('pointercancel', handlePointerUp)

    return {
        dispose: () => {
            options.canvas.removeEventListener('pointerdown', handlePointerDown)
            options.canvas.removeEventListener('mousemove', handleMove)
            options.canvas.removeEventListener('click', handleClick)
            options.canvas.removeEventListener('contextmenu', handleContextMenu)
            options.canvas.removeEventListener('mouseleave', handleLeave)
            window.removeEventListener('pointermove', handlePointerMove)
            window.removeEventListener('pointerup', handlePointerUp)
            window.removeEventListener('pointercancel', handlePointerUp)
            options.canvas.style.cursor = 'default'
            options.scene.remove(highlight)
            options.scene.remove(targets)
            disposeObject(highlight)
            disposeObject(targets)
        },
    }
}

interface DeskInteractionMetrics {
    highlightCenter: THREE.Vector3
    highlightSize: THREE.Vector2
    proxyCenter: THREE.Vector3
    proxySize: THREE.Vector3
    seatMinX: number
    seatWidth: number
    seatProxySize?: THREE.Vector3
}

function getDeskInteractionMetrics(instancedMeshes: THREE.InstancedMesh[], isLargeRoom: boolean): DeskInteractionMetrics {
    const bounds = computeDeskLocalBounds(instancedMeshes)
    const topBounds = computeDeskTopLocalBounds(instancedMeshes, bounds.max.y)
    const proxyCenter = new THREE.Vector3()
    const proxySize = new THREE.Vector3()
    const highlightCenter = new THREE.Vector3()
    const highlightSize3 = new THREE.Vector3()

    bounds.getCenter(proxyCenter)
    bounds.getSize(proxySize)
    topBounds.getCenter(highlightCenter)
    topBounds.getSize(highlightSize3)
    highlightCenter.y = bounds.max.y + 0.025

    let seatProxySize: THREE.Vector3 | undefined
    if (isLargeRoom) {
        seatProxySize = new THREE.Vector3(
            Math.max(proxySize.x / LARGE_SEATS_PER_DESK, 0.4),
            Math.max(proxySize.y, 0.4),
            Math.max(proxySize.z, 0.4),
        )
    }

    return {
        highlightCenter,
        highlightSize: new THREE.Vector2(Math.max(highlightSize3.x, 0.2), Math.max(highlightSize3.z, 0.2)),
        proxyCenter,
        proxySize: new THREE.Vector3(
            Math.max(proxySize.x, 0.4),
            Math.max(proxySize.y, 0.4),
            Math.max(proxySize.z, 0.4),
        ),
        seatMinX: bounds.min.z,
        seatWidth: Math.max(proxySize.z, 0.4),
        seatProxySize,
    }
}

function createInteractionTargets(
    roomSize: number,
    count: number,
    metrics: DeskInteractionMetrics,
    dimensions?: RoomPlanDimensions,
) {
    const group = new THREE.Group()
    const geometry = new THREE.BoxGeometry(metrics.proxySize.x + 0.12, metrics.proxySize.y + 0.12, metrics.proxySize.z + 0.12)
    geometry.translate(metrics.proxyCenter.x, metrics.proxyCenter.y, metrics.proxyCenter.z)
    const material = new THREE.MeshBasicMaterial({
        transparent: true,
        opacity: 0,
        depthWrite: false,
        colorWrite: false,
    })

    for (let index = 0; index < count; index += 1) {
        const target = new THREE.Mesh(geometry, material)
        target.position.copy(getDeskPosition(roomSize, index, dimensions))
        target.quaternion.setFromEuler(new THREE.Euler(0, getDeskYaw(roomSize, index, dimensions), 0))
        target.userData.deskIndex = index
        group.add(target)
    }

    return group
}

function createDeskHighlight(metrics: DeskInteractionMetrics, useSeatSize: boolean = false) {
    const group = new THREE.Group()

    const baseSize = useSeatSize && metrics.seatProxySize ? metrics.seatProxySize : metrics.proxySize
    const boxSize = baseSize.clone().addScalar(0.08)
    const boxGeometry = new THREE.BoxGeometry(boxSize.x, boxSize.y, boxSize.z)
    boxGeometry.translate(metrics.proxyCenter.x, metrics.proxyCenter.y, metrics.proxyCenter.z)

    const fillMaterial = new THREE.MeshBasicMaterial({
        color: 0x60a5fa,
        transparent: true,
        opacity: 0.24,
        depthWrite: false,
    })
    const fill = new THREE.Mesh(boxGeometry, fillMaterial)

    const edgeGeometry = new THREE.EdgesGeometry(boxGeometry)
    const edgeMaterial = new THREE.LineBasicMaterial({
        color: 0x93c5fd,
        transparent: true,
        opacity: 0.95,
    })
    const edge = new THREE.LineSegments(edgeGeometry, edgeMaterial)

    group.add(fill)
    group.add(edge)
    group.visible = false
    return group
}

function computeDeskLocalBounds(instancedMeshes: THREE.InstancedMesh[]) {
    const bounds = new THREE.Box3()
    const meshBounds = new THREE.Box3()
    for (const mesh of instancedMeshes) {
        if (!mesh.geometry.boundingBox) {
            mesh.geometry.computeBoundingBox()
        }
        if (!mesh.geometry.boundingBox) {
            continue
        }
        meshBounds.copy(mesh.geometry.boundingBox).applyMatrix4(getBaseMatrix(mesh))
        bounds.union(meshBounds)
    }
    if (bounds.isEmpty()) {
        bounds.set(new THREE.Vector3(-0.7, 0, -0.5), new THREE.Vector3(0.7, 1, 0.5))
    }
    return bounds
}

function computeDeskTopLocalBounds(instancedMeshes: THREE.InstancedMesh[], topY: number) {
    const bounds = new THREE.Box3()
    const position = new THREE.Vector3()
    const topBand = 0.12

    for (const mesh of instancedMeshes) {
        const positions = mesh.geometry.getAttribute('position')
        const matrix = getBaseMatrix(mesh)
        for (let index = 0; index < positions.count; index += 1) {
            position.fromBufferAttribute(positions, index).applyMatrix4(matrix)
            if (position.y >= topY - topBand) {
                bounds.expandByPoint(position)
            }
        }
    }

    if (bounds.isEmpty()) {
        const fallback = computeDeskLocalBounds(instancedMeshes)
        bounds.set(
            new THREE.Vector3(fallback.min.x, fallback.max.y, fallback.min.z),
            new THREE.Vector3(fallback.max.x, fallback.max.y, fallback.max.z),
        )
    }
    return bounds
}

function getBaseMatrix(mesh: THREE.InstancedMesh) {
    return new THREE.Matrix4().compose(
        mesh.userData.basePosition as THREE.Vector3,
        mesh.userData.baseQuaternion as THREE.Quaternion,
        mesh.userData.baseScale as THREE.Vector3,
    )
}

function largeSeatOffset(point: THREE.Vector3, object: THREE.Object3D, metrics: DeskInteractionMetrics) {
    const localPoint = object.worldToLocal(point.clone())
    const normalized = THREE.MathUtils.clamp((localPoint.z - metrics.seatMinX) / metrics.seatWidth, 0, 0.999)
    return Math.floor(normalized * 4)
}

function disposeObject(object: THREE.Object3D) {
    const geometries = new Set<THREE.BufferGeometry>()
    const materials = new Set<THREE.Material>()

    object.traverse((child) => {
        if (child instanceof THREE.Mesh || child instanceof THREE.LineSegments) {
            geometries.add(child.geometry)
            const material = child.material
            if (Array.isArray(material)) {
                material.forEach((item) => materials.add(item))
            } else {
                materials.add(material)
            }
        }
    })

    geometries.forEach((geometry) => geometry.dispose())
    materials.forEach((material) => material.dispose())
}
