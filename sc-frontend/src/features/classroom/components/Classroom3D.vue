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
import {HDRLoader} from 'three/examples/jsm/loaders/HDRLoader.js'

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
import {computeCameraPositionsBySize, getCameraTarget, type ClassroomDimensions} from '@/features/classroom/composables/useCameraGroup'
import {ModelInstanceManager} from '@/features/classroom/composables/ModelInstanceManager'
import {SeatSpriteManager} from '@/features/classroom/composables/SeatSpriteManager'
import {createClassroomInteraction, type ClassroomInteractionControls} from '@/features/classroom/composables/useClassroomInteraction'
import {getRoomSpec, type SeatSyncMessage} from '@/features/classroom/types/classroom'
import {confirmDialog} from '@/shared/composables/useConfirmDialog'
import {buildSeatSyncSocketUrl} from '@/features/classroom/composables/seatSyncSocket'

const props = defineProps<{
  session: ClassSession
}>()

const emit = defineEmits<{
  joined: [participant: ClassParticipant]
  left: []
  exit: []
  'participants-change': [participants: ClassParticipant[]]
  'live-status-change': [message: SeatSyncMessage]
  'loading-progress': [payload: {progress: number; label: string}]
  ready: []
  loadError: [message: string]
}>()

const {t} = useI18n()
const authStore = useAuthStore()

const canvasRef = ref<HTMLCanvasElement | null>(null)
const loading = ref(true)
const loadError = ref('')
const seatActionPending = ref(false)
const participantsBySeat = ref(new Map<number, ClassParticipant>())

const sceneRef = shallowRef<THREE.Scene | null>(null)
const cameraRef = shallowRef<THREE.PerspectiveCamera | null>(null)
const rendererRef = shallowRef<THREE.WebGLRenderer | null>(null)
const controlsRef = shallowRef<OrbitControls | null>(null)
const interactionRef = shallowRef<ClassroomInteractionControls | null>(null)
const spriteManagerRef = shallowRef<SeatSpriteManager | null>(null)
const exitDoorRef = shallowRef<THREE.Group | null>(null)
const classroomDimensions = ref<ClassroomDimensions>({x: null, y: null, z: null})
const classroomCameraBounds = shallowRef<THREE.Box3 | null>(null)
const environmentTextureRef = shallowRef<THREE.Texture | null>(null)

let frameId = 0
let resizeObserver: ResizeObserver | null = null
let websocket: WebSocket | null = null
let seatSocketStarted = false
let seatSocketReconnectTimer: number | null = null
let seatSocketReconnectAttempts = 0
let destroyed = false
const modelInstanceManager = new ModelInstanceManager()
const deskInstancedMeshes: THREE.InstancedMesh[] = []
const targetBeforeClamp = new THREE.Vector3()
const targetAfterClamp = new THREE.Vector3()
const targetClampDelta = new THREE.Vector3()
const cameraAfterClamp = new THREE.Vector3()
const DOOR_NAME = '\u95e8'
const loadingLabel = (key: string) => t(`courseDetail.classSession.loadingSteps.${key}`)

const roomSpec = computed(() => getRoomSpec(props.session.roomSize))
const currentUserId = computed(() => authStore.user?.id || '')
const canSit = computed(() => authStore.user?.role === 1)
const currentParticipant = computed(() => {
  if (!currentUserId.value) {
    return null
  }
  return Array.from(participantsBySeat.value.values()).find((participant) => participant.userId === currentUserId.value) || null
})

onMounted(async () => {
  await nextTick()
  await setupScene()
  await loadInitialParticipants()
  connectSeatSocket()
})

onUnmounted(() => {
  destroyed = true
  clearSeatSocketReconnectTimer()
  websocket?.close()
  resizeObserver?.disconnect()
  interactionRef.value?.dispose()
  spriteManagerRef.value?.dispose()
  controlsRef.value?.dispose()
  environmentTextureRef.value?.dispose()
  if (frameId) {
    cancelAnimationFrame(frameId)
  }
  for (const mesh of deskInstancedMeshes) {
    sceneRef.value?.remove(mesh)
  }
  if (exitDoorRef.value) {
    sceneRef.value?.remove(exitDoorRef.value)
    disposeObject(exitDoorRef.value)
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
  emitLoadingProgress(10, loadingLabel('preparingScene'))
  try {
    const canvas = canvasRef.value
    const scene = new THREE.Scene()
    scene.fog = new THREE.Fog(0x0b1020, 22, 72)

    const camera = new THREE.PerspectiveCamera(75, 1, 0.1, 1000)

    const renderer = new THREE.WebGLRenderer({canvas, antialias: true, alpha: false, preserveDrawingBuffer: true})
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
    renderer.outputColorSpace = THREE.SRGBColorSpace
    renderer.shadowMap.enabled = true

    const controls = new OrbitControls(camera, canvas)
    controls.enableDamping = true
    controls.target.copy(getCameraTarget(classroomDimensions.value))
    controls.maxPolarAngle = Math.PI * 0.54
    controls.minDistance = 1
    controls.maxDistance = props.session.roomSize === ClassRoomSize.XLARGE ? 32 : 18

    scene.add(new THREE.HemisphereLight(0xffffff, 0x233045, 2.2))

    const environmentTexture = await loadEnvironment('/assets/Environment_mapping/cedar_bridge_sunset_1_4k.hdr')
    scene.background = environmentTexture
    scene.environment = environmentTexture
    scene.environmentIntensity = 0.3
    environmentTextureRef.value = environmentTexture

    sceneRef.value = scene
    cameraRef.value = camera
    rendererRef.value = renderer
    controlsRef.value = controls

    resizeRenderer()
    resizeObserver = new ResizeObserver(resizeRenderer)
    resizeObserver.observe(canvas.parentElement || canvas)
    emitLoadingProgress(15, loadingLabel('preparingScene'))

    await loadModels(scene, camera, controls)
    emitLoadingProgress(92, loadingLabel('seatMarkers'))
    setupSprites(scene)
    emitLoadingProgress(95, loadingLabel('interactions'))
    setupInteractions(canvas, camera, scene)
    emitLoadingProgress(98, loadingLabel('interactions'))
    animate()
    emitLoadingProgress(100, loadingLabel('ready'))
    emit('ready')
  } catch (error) {
    const message = error instanceof Error ? error.message : t('courseDetail.classSession.modelLoadFailed')
    loadError.value = message
    emit('loadError', message)
  } finally {
    loading.value = false
  }
}

async function loadModels(scene: THREE.Scene, camera: THREE.PerspectiveCamera, controls: OrbitControls) {
  const route = getClassroomModelRoute(props.session.roomSize)
  const progress = createAssetProgressReporter(15, 90, [
    {key: 'classroom', weight: 4},
    {key: 'desk', weight: 3},
  ], loadingLabel('loadingAssetsWithoutTextures'))
  const [classroom, desk] = await Promise.all([
    loadGlb(route.classroom.model, progress.track('classroom')),
    loadGlb(route.desk.model, progress.track('desk')),
  ])

  emitLoadingProgress(90, loadingLabel('applyingMaterials'))
  classroom.scene.position.set(0, 0, 0)
  classroom.scene.rotation.y = props.session.roomSize === ClassRoomSize.SMALL ? 0 : Math.PI / 2
  classroom.scene.updateMatrixWorld(true)
  const classroomBounds = measureClassroomBounds(classroom.scene)
  classroomDimensions.value = measureClassroomDimensions(classroomBounds)
  classroomCameraBounds.value = createCameraBounds(classroomBounds)
  applyCameraPreset(camera, controls)
  classroom.scene.traverse((child) => {
    if (child instanceof THREE.Mesh) {
      child.castShadow = true
      child.receiveShadow = true
    }
  })
  scene.add(classroom.scene)
  setupExitDoor(scene, classroom.scene, classroomBounds)

  emitLoadingProgress(91, loadingLabel('arrangingDesks'))
  const instancedMeshes = modelInstanceManager.createInstancedMeshes(desk.scene, roomSpec.value.deskInstanceCount, null)
  modelInstanceManager.setInstanceMatrices(instancedMeshes, props.session.roomSize, roomSpec.value.deskInstanceCount, (index) =>
      getDeskPosition(props.session.roomSize, index, classroomDimensions.value),
  )
  instancedMeshes.forEach((mesh) => {
    deskInstancedMeshes.push(mesh)
    scene.add(mesh)
  })
}

function setupSprites(scene: THREE.Scene) {
  const manager = new SeatSpriteManager(scene, getAllSeatPositions(props.session.roomSize, classroomDimensions.value), props.session.roomSize)
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
    dimensions: classroomDimensions.value,
    exitTarget: exitDoorRef.value,
    onHover: () => undefined,
    onClick: handleSeatClick,
    onContextMenu: handleSeatContextMenu,
    onExit: () => emit('exit'),
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
  if (seatActionPending.value || !canSit.value || !currentUserId.value) {
    return
  }
  const occupied = participantsBySeat.value.get(seatIndex)
  if (occupied) {
    return
  }
  seatActionPending.value = true
  try {
    const message = currentParticipant.value
        ? t('courseDetail.classSession.confirmChangeSeat')
        : t('courseDetail.classSession.confirmSitSeat')
    if (!(await confirmDialog({message}))) {
      return
    }
    if (participantsBySeat.value.get(seatIndex)) {
      return
    }
    const position = getAllSeatPositions(props.session.roomSize, classroomDimensions.value)[seatIndex]
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
  } finally {
    seatActionPending.value = false
  }
}

async function handleSeatContextMenu(seatIndex: number) {
  if (seatActionPending.value) {
    return
  }
  const participant = participantsBySeat.value.get(seatIndex)
  if (!participant || participant.userId !== currentUserId.value) {
    return
  }
  seatActionPending.value = true
  try {
    if (!(await confirmDialog({message: t('courseDetail.classSession.confirmStandSeat')}))) {
      return
    }
    const currentSeatParticipant = participantsBySeat.value.get(seatIndex)
    if (!currentSeatParticipant || currentSeatParticipant.userId !== currentUserId.value) {
      return
    }
    await leaveClassSessionSeat(props.session.id)
    removeParticipant(currentSeatParticipant.userId, currentSeatParticipant.seatIndex)
    emit('left')
    notify.success(t('courseDetail.classSession.standSuccess'))
  } catch {
    notify.error(t('courseDetail.classSession.standFailed'))
  } finally {
    seatActionPending.value = false
  }
}

async function connectSeatSocket() {
  if (destroyed || seatSocketStarted || isSeatSocketActive()) {
    return
  }
  seatSocketStarted = true
  try {
    const token = await issueClassSessionSeatSyncToken(props.session.id)
    if (destroyed) {
      return
    }
    const socket = new WebSocket(buildSeatSyncSocketUrl(props.session.id, token.token))
    websocket = socket
    socket.onopen = () => {
      seatSocketReconnectAttempts = 0
    }
    socket.onmessage = (event) => handleSeatSyncMessage(event.data)
    socket.onclose = () => {
      if (websocket === socket) {
        websocket = null
        seatSocketStarted = false
        scheduleSeatSocketReconnect()
      }
    }
    socket.onerror = () => {
      if (websocket === socket) {
        socket.close()
      }
    }
  } catch {
    // Seat sync is best effort. The initial participants request still renders the classroom.
    seatSocketStarted = false
    scheduleSeatSocketReconnect()
  }
}

function scheduleSeatSocketReconnect() {
  if (destroyed || seatSocketReconnectTimer != null) {
    return
  }
  seatSocketReconnectAttempts += 1
  const delay = Math.min(1000 * seatSocketReconnectAttempts, 8000)
  seatSocketReconnectTimer = window.setTimeout(() => {
    seatSocketReconnectTimer = null
    void connectSeatSocket()
  }, delay)
}

function clearSeatSocketReconnectTimer() {
  if (seatSocketReconnectTimer == null) {
    return
  }
  window.clearTimeout(seatSocketReconnectTimer)
  seatSocketReconnectTimer = null
}

function isSeatSocketActive() {
  return websocket?.readyState === WebSocket.OPEN || websocket?.readyState === WebSocket.CONNECTING
}

function handleSeatSyncMessage(raw: string) {
  try {
    const message = JSON.parse(raw) as SeatSyncMessage
    if (message.sessionId !== props.session.id) {
      return
    }
    if (message.type === 'seat_snapshot') {
      applySnapshot(message.participants || [])
      if (message.liveStatus != null) {
        emit('live-status-change', message)
      }
      return
    }
    if (message.type === 'seat_upsert' && message.participant) {
      upsertParticipant(message.participant)
      return
    }
    if (message.type === 'seat_remove') {
      removeParticipant(message.userId || '', message.seatIndex)
      return
    }
    if (
        message.type === 'live_started' ||
        message.type === 'live_paused' ||
        message.type === 'live_resumed' ||
        message.type === 'live_stopped'
    ) {
      emit('live-status-change', message)
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
  emitParticipantsChange()
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
  emitParticipantsChange()
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
  emitParticipantsChange()
}

function emitParticipantsChange() {
  emit('participants-change', Array.from(participantsBySeat.value.values()))
}

function animate() {
  if (destroyed || !rendererRef.value || !sceneRef.value || !cameraRef.value) {
    return
  }
  frameId = requestAnimationFrame(animate)
  const camera = cameraRef.value
  const controls = controlsRef.value
  controls?.update()
  if (controls) {
    keepCameraInsideClassroom(camera, controls)
  }
  spriteManagerRef.value?.updateCameraFacing(camera)
  rendererRef.value.render(sceneRef.value, camera)
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

function measureClassroomBounds(classroom: THREE.Object3D) {
  return new THREE.Box3().setFromObject(classroom)
}

function measureClassroomDimensions(box: THREE.Box3): ClassroomDimensions {
  const size = new THREE.Vector3()
  box.getSize(size)
  return {x: size.x, y: size.y, z: size.z}
}

function setupExitDoor(scene: THREE.Scene, classroom: THREE.Object3D, bounds: THREE.Box3) {
  const group = new THREE.Group()
  group.name = 'exit_door'

  const anchor = findDoorAnchor(classroom, bounds)
  group.position.copy(anchor.position)
  group.quaternion.copy(anchor.quaternion)

  const labelTexture = createExitLabelTexture()
  const labelMaterial = new THREE.SpriteMaterial({
    map: labelTexture,
    transparent: true,
    depthTest: false,
    depthWrite: false,
  })
  const label = new THREE.Sprite(labelMaterial)
  label.name = 'exit_door_label'
  label.position.set(0, 1.35, 0)
  label.scale.set(2.2, 0.68, 1)
  group.add(label)

  const targetGeometry = new THREE.BoxGeometry(2.6, 3.2, 0.8)
  targetGeometry.translate(0, 1.2, 0)
  const targetMaterial = new THREE.MeshBasicMaterial({
    transparent: true,
    opacity: 0,
    depthWrite: false,
    colorWrite: false,
  })
  const target = new THREE.Mesh(targetGeometry, targetMaterial)
  target.name = 'exit_door_target'
  group.add(target)

  scene.add(group)
  exitDoorRef.value = group
}

function findDoorAnchor(classroom: THREE.Object3D, bounds: THREE.Box3) {
  let doorMesh: THREE.Mesh | null = null
  classroom.traverse((child) => {
    if (child instanceof THREE.Mesh && isDoorObject(child) && !doorMesh) {
      doorMesh = child
    }
  })

  if (doorMesh) {
    const doorBounds = new THREE.Box3().setFromObject(doorMesh)
    const doorCenter = new THREE.Vector3()
    doorBounds.getCenter(doorCenter)
    return {
      position: new THREE.Vector3(doorCenter.x, Math.max(bounds.min.y, doorBounds.min.y), doorCenter.z),
      quaternion: faceRoomCenter(doorCenter),
    }
  }

  const size = new THREE.Vector3()
  bounds.getSize(size)
  const fallbackPosition = new THREE.Vector3(0, bounds.min.y, bounds.max.z - Math.max(size.z * 0.04, 0.35))
  return {
    position: fallbackPosition,
    quaternion: faceRoomCenter(fallbackPosition),
  }
}

function isDoorObject(object: THREE.Object3D) {
  return object.name.includes(DOOR_NAME) || object.name.toLowerCase().includes('door')
}

function faceRoomCenter(position: THREE.Vector3) {
  const quaternion = new THREE.Quaternion()
  const direction = new THREE.Vector3(-position.x, 0, -position.z)
  if (direction.lengthSq() < 0.001) {
    return quaternion
  }
  return quaternion.setFromUnitVectors(new THREE.Vector3(0, 0, 1), direction.normalize())
}

function createExitLabelTexture() {
  const labelText = t('courseDetail.classSession.exitClassroomLabel')
  const canvas = document.createElement('canvas')
  canvas.width = 512
  canvas.height = 160
  const context = canvas.getContext('2d')
  if (context) {
    context.clearRect(0, 0, canvas.width, canvas.height)

    const borderRadius = 36
    context.beginPath()
    context.roundRect(20, 20, canvas.width - 40, canvas.height - 40, borderRadius)
    context.fillStyle = 'rgba(15, 23, 42, 0.45)'
    context.fill()
    context.lineWidth = 2
    context.strokeStyle = 'rgba(255, 255, 255, 0.3)'
    context.stroke()

    context.lineWidth = 8
    context.strokeStyle = 'rgba(15, 23, 42, 0.82)'
    context.font = '700 58px sans-serif'
    context.textAlign = 'center'
    context.textBaseline = 'middle'
    context.strokeText(labelText, canvas.width / 2, canvas.height / 2 + 3)
    context.fillStyle = '#ffffff'
    context.fillText(labelText, canvas.width / 2, canvas.height / 2 + 3)
  }
  const texture = new THREE.CanvasTexture(canvas)
  texture.colorSpace = THREE.SRGBColorSpace
  texture.minFilter = THREE.LinearFilter
  texture.magFilter = THREE.LinearFilter
  texture.needsUpdate = true
  return texture
}

function createCameraBounds(classroomBounds: THREE.Box3) {
  const bounds = classroomBounds.clone()
  const size = new THREE.Vector3()
  classroomBounds.getSize(size)
  const horizontalInset = Math.min(Math.max(Math.min(size.x, size.z) * 0.035, 0.25), Math.max(Math.min(size.x, size.z) / 2 - 0.05, 0))
  const bottomInset = Math.min(Math.max(size.y * 0.08, 0.6), Math.max(size.y / 2 - 0.05, 0))
  const topInset = Math.min(Math.max(size.y * 0.12, 0.8), Math.max(size.y / 2 - 0.05, 0))
  bounds.min.x += horizontalInset
  bounds.max.x -= horizontalInset
  bounds.min.z += horizontalInset
  bounds.max.z -= horizontalInset
  bounds.min.y += bottomInset
  bounds.max.y -= topInset
  return bounds
}

function keepCameraInsideClassroom(camera: THREE.PerspectiveCamera, controls: OrbitControls) {
  const bounds = classroomCameraBounds.value
  if (!bounds) {
    return
  }
  targetBeforeClamp.copy(controls.target)
  bounds.clampPoint(targetBeforeClamp, targetAfterClamp)
  if (!targetBeforeClamp.equals(targetAfterClamp)) {
    targetClampDelta.subVectors(targetAfterClamp, targetBeforeClamp)
    controls.target.copy(targetAfterClamp)
    camera.position.add(targetClampDelta)
  }
  bounds.clampPoint(camera.position, cameraAfterClamp)
  if (!camera.position.equals(cameraAfterClamp)) {
    camera.position.copy(cameraAfterClamp)
  }
}

function applyCameraPreset(camera: THREE.PerspectiveCamera, controls: OrbitControls) {
  const front = computeCameraPositionsBySize(classroomDimensions.value, props.session.roomSize).front
  camera.position.set(front.position.x, front.position.y, front.position.z)
  camera.setRotationFromEuler(front.initialRotation)
  controls.target.copy(getCameraTarget(classroomDimensions.value))
  controls.update()
  keepCameraInsideClassroom(camera, controls)
}

interface AssetProgressItem {
  key: string
  weight: number
}

function emitLoadingProgress(progress: number, label: string) {
  if (!Number.isFinite(progress)) {
    return
  }
  emit('loading-progress', {
    progress: Math.min(Math.max(progress, 0), 100),
    label,
  })
}

function createAssetProgressReporter(start: number, end: number, items: AssetProgressItem[], label: string) {
  const validItems = items.filter((item) => Number.isFinite(item.weight) && item.weight > 0)
  const weights = new Map(validItems.map((item) => [item.key, item.weight]))
  const loadedByKey = new Map(validItems.map((item) => [item.key, 0]))
  const totalWeight = validItems.reduce((sum, item) => sum + item.weight, 0)

  function report() {
    if (totalWeight <= 0) {
      emitLoadingProgress(end, loadingLabel('applyingMaterials'))
      return
    }
    let weightedLoaded = 0
    for (const [key, weight] of weights.entries()) {
      weightedLoaded += (loadedByKey.get(key) ?? 0) * weight
    }
    emitLoadingProgress(start + (end - start) * (weightedLoaded / totalWeight), label)
  }

  return {
    track: (key: string) => (event?: ProgressEvent<EventTarget>) => {
      if (!event) {
        loadedByKey.set(key, 1)
        report()
        return
      }
      const total = event?.total ?? 0
      const loaded = event?.loaded ?? 0
      if (!Number.isFinite(total) || !Number.isFinite(loaded) || total <= 0) {
        report()
        return
      }
      loadedByKey.set(key, Math.min(loaded / total, 1))
      report()
    },
  }
}

function loadGlb(path: string, onProgress?: (event?: ProgressEvent<EventTarget>) => void) {
  const loader = new GLTFLoader()
  return new Promise<Awaited<ReturnType<GLTFLoader['loadAsync']>>>((resolve, reject) => {
    loader.load(
        path,
        (glb) => {
          onProgress?.()
          resolve(glb)
        },
        onProgress,
        reject,
    )
  })
}

function loadEnvironment(path: string): Promise<THREE.Texture> {
  const loader = new HDRLoader()
  return new Promise((resolve, reject) => {
    loader.load(
        path,
        (texture) => {
          texture.mapping = THREE.EquirectangularReflectionMapping
          resolve(texture)
        },
        undefined,
        reject,
    )
  })
}

function disposeObject(object: THREE.Object3D) {
  const geometries = new Set<THREE.BufferGeometry>()
  const materials = new Set<THREE.Material>()
  const textures = new Set<THREE.Texture>()

  object.traverse((child) => {
    if (child instanceof THREE.Mesh || child instanceof THREE.Sprite) {
      if (child instanceof THREE.Mesh) {
        geometries.add(child.geometry)
      }
      const material = child.material
      const materialList = Array.isArray(material) ? material : [material]
      materialList.forEach((item) => {
        materials.add(item)
        const map = item instanceof THREE.SpriteMaterial || item instanceof THREE.MeshBasicMaterial ? item.map : null
        if (map) {
          textures.add(map)
        }
      })
    }
  })

  geometries.forEach((geometry) => geometry.dispose())
  materials.forEach((material) => material.dispose())
  textures.forEach((texture) => texture.dispose())
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
