<template>
  <div class="classroom-3d">
    <canvas ref="canvasRef" class="classroom-canvas"></canvas>

    <div v-if="loadError" class="classroom-error">
      <CircleAlert :size="28" stroke-width="1.7"/>
      <p>{{ loadError }}</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import {computed, nextTick, onMounted, onUnmounted, ref, shallowRef} from 'vue'
import {useI18n} from 'vue-i18n'
import {CircleAlert} from 'lucide-vue-next'
import * as THREE from 'three'
import {GLTFLoader} from 'three/examples/jsm/loaders/GLTFLoader.js'
import {OrbitControls} from 'three/examples/jsm/controls/OrbitControls.js'

import {
  issueClassSessionSeatSyncToken,
  joinClassSession,
  leaveClassSessionSeat,
  listClassSessionParticipants,
} from '@/features/course/api/classSession'
import {ClassRoomSize, type ClassParticipant, type ClassSession} from '@/features/course/types/classSession'
import {useAuthStore} from '@/features/auth/stores/auth'
import {notify} from '@/shared/composables/useGlobalNotification'
import {getClassroomModelRoute} from '@/features/classroom/composables/useModelRouter'
import {getAllSeatPositions, getDeskPosition} from '@/features/classroom/composables/useSeatLayout'
import {ModelInstanceManager} from '@/features/classroom/composables/ModelInstanceManager'
import {SeatSpriteManager} from '@/features/classroom/composables/SeatSpriteManager'
import {createClassroomInteraction, type ClassroomInteractionControls} from '@/features/classroom/composables/useClassroomInteraction'
import {getRoomSpec, type SeatSyncMessage} from '@/features/classroom/types/classroom'

const props = defineProps<{
  session: ClassSession
}>()

const emit = defineEmits<{
  joined: [participant: ClassParticipant]
  left: []
  ready: []
  loadError: [message: string]
}>()

const {t} = useI18n()
const authStore = useAuthStore()

const canvasRef = ref<HTMLCanvasElement | null>(null)
const loading = ref(true)
const loadError = ref('')
const participantsBySeat = ref(new Map<number, ClassParticipant>())

const sceneRef = shallowRef<THREE.Scene | null>(null)
const cameraRef = shallowRef<THREE.PerspectiveCamera | null>(null)
const rendererRef = shallowRef<THREE.WebGLRenderer | null>(null)
const controlsRef = shallowRef<OrbitControls | null>(null)
const interactionRef = shallowRef<ClassroomInteractionControls | null>(null)
const spriteManagerRef = shallowRef<SeatSpriteManager | null>(null)

let frameId = 0
let resizeObserver: ResizeObserver | null = null
let websocket: WebSocket | null = null
let reconnectTimer = 0
let destroyed = false
const modelInstanceManager = new ModelInstanceManager()
const deskInstancedMeshes: THREE.InstancedMesh[] = []

const roomSpec = computed(() => getRoomSpec(props.session.roomSize))
const currentUserId = computed(() => authStore.user?.id || '')
const canSit = computed(() => authStore.user?.role === 1)

onMounted(async () => {
  await nextTick()
  await setupScene()
  await loadInitialParticipants()
  connectSeatSocket()
})

onUnmounted(() => {
  destroyed = true
  window.clearTimeout(reconnectTimer)
  websocket?.close()
  resizeObserver?.disconnect()
  interactionRef.value?.dispose()
  spriteManagerRef.value?.dispose()
  controlsRef.value?.dispose()
  if (frameId) {
    cancelAnimationFrame(frameId)
  }
  for (const mesh of deskInstancedMeshes) {
    sceneRef.value?.remove(mesh)
  }
  modelInstanceManager.dispose(deskInstancedMeshes)
  rendererRef.value?.dispose()
})

async function setupScene() {
  if (!canvasRef.value) {
    return
  }
  loading.value = true
  loadError.value = ''
  try {
    const canvas = canvasRef.value
    const scene = new THREE.Scene()
    scene.background = new THREE.Color(0x0b1020)
    scene.fog = new THREE.Fog(0x0b1020, 22, 72)

    const camera = new THREE.PerspectiveCamera(52, 1, 0.1, 120)
    positionCamera(camera, props.session.roomSize)

    const renderer = new THREE.WebGLRenderer({canvas, antialias: true, alpha: false, preserveDrawingBuffer: true})
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
    renderer.outputColorSpace = THREE.SRGBColorSpace
    renderer.shadowMap.enabled = true

    const controls = new OrbitControls(camera, canvas)
    controls.enableDamping = true
    controls.target.copy(getCameraTarget(props.session.roomSize))
    controls.maxPolarAngle = Math.PI * 0.54
    controls.minDistance = 1
    controls.maxDistance = props.session.roomSize === ClassRoomSize.XLARGE ? 32 : 18

    scene.add(new THREE.HemisphereLight(0xffffff, 0x233045, 2.2))
    const keyLight = new THREE.DirectionalLight(0xffffff, 2.2)
    keyLight.position.set(5, 12, 8)
    keyLight.castShadow = true
    scene.add(keyLight)

    sceneRef.value = scene
    cameraRef.value = camera
    rendererRef.value = renderer
    controlsRef.value = controls

    resizeRenderer()
    resizeObserver = new ResizeObserver(resizeRenderer)
    resizeObserver.observe(canvas.parentElement || canvas)

    await loadModels(scene)
    setupSprites(scene)
    setupInteractions(canvas, camera, scene)
    animate()
    emit('ready')
  } catch (error) {
    const message = error instanceof Error ? error.message : t('courseDetail.classSession.modelLoadFailed')
    loadError.value = message
    emit('loadError', message)
  } finally {
    loading.value = false
  }
}

async function loadModels(scene: THREE.Scene) {
  const route = getClassroomModelRoute(props.session.roomSize)
  const [classroom, desk, classroomTexture, deskTexture] = await Promise.all([
    loadGltf(route.classroom.model),
    loadGltf(route.desk.model),
    loadTexture(route.classroom.texture),
    loadTexture(route.desk.texture),
  ])

  applyTexture(classroom.scene, classroomTexture)
  classroom.scene.traverse((child) => {
    if (child instanceof THREE.Mesh) {
      child.castShadow = true
      child.receiveShadow = true
    }
  })
  scene.add(classroom.scene)

  const instancedMeshes = modelInstanceManager.createInstancedMeshes(desk.scene, roomSpec.value.deskInstanceCount, deskTexture)
  modelInstanceManager.setInstanceMatrices(instancedMeshes, props.session.roomSize, roomSpec.value.deskInstanceCount, (index) =>
      getDeskPosition(props.session.roomSize, index),
  )
  instancedMeshes.forEach((mesh) => {
    deskInstancedMeshes.push(mesh)
    scene.add(mesh)
  })
}

function setupSprites(scene: THREE.Scene) {
  const manager = new SeatSpriteManager(scene, getAllSeatPositions(props.session.roomSize))
  spriteManagerRef.value = manager
  manager.applySnapshot(Array.from(participantsBySeat.value.values()))
}

function setupInteractions(canvas: HTMLCanvasElement, camera: THREE.PerspectiveCamera, scene: THREE.Scene) {
  interactionRef.value = createClassroomInteraction({
    canvas,
    camera,
    scene,
    instancedMeshes: deskInstancedMeshes,
    roomSize: props.session.roomSize,
    onHover: () => undefined,
    onClick: handleSeatClick,
    onContextMenu: handleSeatContextMenu,
  })
}

async function loadInitialParticipants() {
  try {
    applySnapshot(await listClassSessionParticipants(props.session.id))
  } catch {
    applySnapshot([])
  }
}

async function handleSeatClick(seatIndex: number) {
  if (!canSit.value) {
    notify.warn(t('courseDetail.classSession.studentOnlySeat'))
    return
  }
  const occupied = participantsBySeat.value.get(seatIndex)
  if (occupied && occupied.userId !== currentUserId.value) {
    notify.warn(t('courseDetail.classSession.seatOccupied'))
    return
  }
  const position = getAllSeatPositions(props.session.roomSize)[seatIndex]
  try {
    const participant = await joinClassSession(props.session.id, {
      seatIndex,
      x: round(position.x),
      y: round(position.y),
      z: round(position.z),
    })
    upsertParticipant(participant)
    emit('joined', participant)
    notify.success(t('courseDetail.classSession.sitSuccess'))
  } catch {
    notify.error(t('courseDetail.classSession.sitFailed'))
  }
}

async function handleSeatContextMenu(seatIndex: number) {
  const participant = participantsBySeat.value.get(seatIndex)
  if (!participant || participant.userId !== currentUserId.value) {
    return
  }
  try {
    await leaveClassSessionSeat(props.session.id)
    removeParticipant(participant.userId, participant.seatIndex)
    emit('left')
    notify.success(t('courseDetail.classSession.standSuccess'))
  } catch {
    notify.error(t('courseDetail.classSession.standFailed'))
  }
}

async function connectSeatSocket() {
  if (destroyed) {
    return
  }
  try {
    const token = await issueClassSessionSeatSyncToken(props.session.id)
    websocket = new WebSocket(buildSeatSocketUrl(token.token))
    websocket.onmessage = (event) => handleSeatSyncMessage(event.data)
    websocket.onclose = () => {
      if (!destroyed) {
        reconnectTimer = window.setTimeout(connectSeatSocket, 1800)
      }
    }
    websocket.onerror = () => {
      websocket?.close()
    }
  } catch {
    if (!destroyed) {
      reconnectTimer = window.setTimeout(connectSeatSocket, 3000)
    }
  }
}

function handleSeatSyncMessage(raw: string) {
  try {
    const message = JSON.parse(raw) as SeatSyncMessage
    if (message.sessionId !== props.session.id) {
      return
    }
    if (message.type === 'seat_snapshot') {
      applySnapshot(message.participants || [])
      return
    }
    if (message.type === 'seat_upsert' && message.participant) {
      upsertParticipant(message.participant)
      return
    }
    if (message.type === 'seat_remove') {
      removeParticipant(message.userId || '', message.seatIndex)
    }
  } catch {
    // Ignore malformed WebSocket payloads.
  }
}

function applySnapshot(participants: ClassParticipant[]) {
  const next = new Map<number, ClassParticipant>()
  participants.forEach((participant) => {
    if (participant.seatIndex != null) {
      next.set(participant.seatIndex, participant)
    }
  })
  participantsBySeat.value = next
  spriteManagerRef.value?.applySnapshot(Array.from(next.values()))
}

function upsertParticipant(participant: ClassParticipant) {
  if (participant.seatIndex == null) {
    return
  }
  const next = new Map(participantsBySeat.value)
  for (const [seatIndex, existing] of next.entries()) {
    if (existing.userId === participant.userId && seatIndex !== participant.seatIndex) {
      next.delete(seatIndex)
    }
  }
  next.set(participant.seatIndex, participant)
  participantsBySeat.value = next
  void spriteManagerRef.value?.upsert(participant)
}

function removeParticipant(userId: string, seatIndex?: number | null) {
  const next = new Map(participantsBySeat.value)
  if (seatIndex != null) {
    next.delete(seatIndex)
    spriteManagerRef.value?.removeBySeatIndex(seatIndex)
  }
  if (userId) {
    for (const [index, participant] of next.entries()) {
      if (participant.userId === userId) {
        next.delete(index)
      }
    }
    spriteManagerRef.value?.removeByUserId(userId)
  }
  participantsBySeat.value = next
}

function animate() {
  if (destroyed || !rendererRef.value || !sceneRef.value || !cameraRef.value) {
    return
  }
  frameId = requestAnimationFrame(animate)
  controlsRef.value?.update()
  spriteManagerRef.value?.updateCameraFacing(cameraRef.value)
  rendererRef.value.render(sceneRef.value, cameraRef.value)
}

function resizeRenderer() {
  if (!canvasRef.value || !rendererRef.value || !cameraRef.value) {
    return
  }
  const parent = canvasRef.value.parentElement || canvasRef.value
  const width = Math.max(parent.clientWidth, 1)
  const height = Math.max(parent.clientHeight, 1)
  rendererRef.value.setSize(width, height, false)
  cameraRef.value.aspect = width / height
  cameraRef.value.updateProjectionMatrix()
}

function positionCamera(camera: THREE.PerspectiveCamera, roomSize: number) {
  if (roomSize === ClassRoomSize.XLARGE) {
    camera.position.set(0, 3.2, -13.5)
    return
  }
  if (roomSize === ClassRoomSize.LARGE) {
    camera.position.set(0, 2.1, -8.5)
    return
  }
  if (roomSize === ClassRoomSize.MEDIUM) {
    camera.position.set(0, 1.65, -5.2)
    return
  }
  camera.position.set(0, 1.55, -2.4)
}

function getCameraTarget(roomSize: number) {
  if (roomSize === ClassRoomSize.XLARGE) {
    return new THREE.Vector3(0, 2.4, 4)
  }
  if (roomSize === ClassRoomSize.LARGE) {
    return new THREE.Vector3(0, 1.35, 5.2)
  }
  if (roomSize === ClassRoomSize.MEDIUM) {
    return new THREE.Vector3(0, 1.2, 2.8)
  }
  return new THREE.Vector3(0, 1.05, 1.5)
}

function loadGltf(path: string) {
  const loader = new GLTFLoader()
  return loader.loadAsync(path)
}

async function loadTexture(path?: string) {
  if (!path) {
    return null
  }
  const texture = await new THREE.TextureLoader().loadAsync(path)
  texture.colorSpace = THREE.SRGBColorSpace
  texture.flipY = false
  return texture
}

function applyTexture(root: THREE.Object3D, texture: THREE.Texture | null) {
  if (!texture) {
    return
  }
  root.traverse((child) => {
    if (child instanceof THREE.Mesh) {
      child.material = new THREE.MeshBasicMaterial({
        map: texture,
        side: THREE.DoubleSide,
      })
    }
  })
}

function buildSeatSocketUrl(token: string) {
  const baseUrl = import.meta.env.VITE_API_BASE_URL as string | undefined
  const apiUrl = baseUrl ? new URL(baseUrl, window.location.origin) : new URL(window.location.origin)
  apiUrl.protocol = apiUrl.protocol === 'https:' ? 'wss:' : 'ws:'
  apiUrl.pathname = '/api/class-sessions/seats/ws'
  apiUrl.search = new URLSearchParams({
    sessionId: props.session.id,
    token,
  }).toString()
  return apiUrl.toString()
}

function round(value: number) {
  return Math.round(value * 100) / 100
}
</script>

<style scoped>
.classroom-3d {
  position: fixed;
  inset: 0;
  width: 100vw;
  height: 100dvh;
  min-height: 0;
  overflow: hidden;
  background: #0b1020;
}

.classroom-canvas {
  display: block;
  width: 100%;
  height: 100%;
}

.classroom-error {
  position: absolute;
  z-index: 2;
  left: 50%;
  top: 50%;
  display: grid;
  min-width: min(100% - 48px, 360px);
  padding: 20px;
  place-items: center;
  align-content: center;
  gap: 12px;
  transform: translate(-50%, -50%);
  border: 1px solid rgb(255 255 255 / 18%);
  background: rgba(8, 13, 27, 0.86);
  color: #f8fafc;
  text-align: center;
}

.classroom-error p {
  margin: 0;
  font-family: var(--font-body);
}

@media (max-width: 760px) {
  .classroom-3d {
    height: 100dvh;
  }
}
</style>
