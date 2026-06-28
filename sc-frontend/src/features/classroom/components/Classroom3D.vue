<template>
  <div class="classroom-3d">
    <canvas ref="canvasRef" class="classroom-canvas"></canvas>

    <div v-if="loadError" class="classroom-error">
      <CircleAlert :size="44" stroke-width="1.7"/>
      <p>{{ loadError }}</p>
    </div>
  </div>
</template>

<script lang="ts" setup>
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
import {type ClassParticipant, ClassRoomSize, type ClassSession} from '@/features/course/types/classSession'
import {useAuthStore} from '@/features/auth/stores/auth'
import {notify} from '@/shared/composables/useGlobalNotification'
import {getClassroomModelRoute} from '@/features/classroom/composables/useModelRouter'
import {
  getAllSeatPositions,
  getDeskPosition,
  seatIndexToPositionIndex
} from '@/features/classroom/composables/useSeatLayout'
import {
  type ClassroomDimensions,
  computeCameraPositionsBySize,
  getCameraTarget
} from '@/features/classroom/composables/useCameraGroup'
import {ModelInstanceManager} from '@/features/classroom/composables/ModelInstanceManager'
import {SeatSpriteManager} from '@/features/classroom/composables/SeatSpriteManager'
import {BlackboardCanvasManager} from '@/features/classroom/composables/BlackboardCanvasManager'
import {
  type ClassroomInteractionControls,
  createClassroomInteraction
} from '@/features/classroom/composables/useClassroomInteraction'
import {getRoomSpec, type SeatSyncMessage} from '@/features/classroom/types/classroom'
import {confirmDialog} from '@/shared/composables/useConfirmDialog'
import {buildSeatSyncSocketUrl} from '@/features/classroom/composables/seatSyncSocket'

const props = defineProps<{
  session: ClassSession
  openingTeacherName?: string
}>()

const emit = defineEmits<{
  joined: [participant: ClassParticipant]
  left: []
  exit: []
  'participants-change': [participants: ClassParticipant[]]
  'live-status-change': [message: SeatSyncMessage]
  'loading-progress': [payload: { progress: number; label: string }]
  ready: []
  loadError: [message: string]
}>()

const {t, locale} = useI18n()
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
const exitDoorRef = shallowRef<THREE.Group[]>([])
const classroomDimensions = ref<ClassroomDimensions>({x: null, y: null, z: null})
const classroomCameraBounds = shallowRef<THREE.Box3 | null>(null)
const environmentTextureRef = shallowRef<THREE.Texture | null>(null)

let frameId = 0
let resizeObserver: ResizeObserver | null = null
let mousemoveHandler: ((event: MouseEvent) => void) | null = null
let websocket: WebSocket | null = null
let seatSocketStarted = false
let seatSocketReconnectTimer: number | null = null
let seatSocketReconnectAttempts = 0
let destroyed = false
const modelInstanceManager = new ModelInstanceManager()
const blackboardCanvasManager = new BlackboardCanvasManager()
const deskInstancedMeshes: THREE.InstancedMesh[] = []
const targetBeforeClamp = new THREE.Vector3()
const targetAfterClamp = new THREE.Vector3()
const targetClampDelta = new THREE.Vector3()
const cameraAfterClamp = new THREE.Vector3()
const exitLabelMaterials: THREE.MeshBasicMaterial[] = []
const exitLabelTextures: { normal: THREE.Texture; highlight: THREE.Texture }[] = []
const hoveredExitDoorIndex = ref(-1)
const exitRaycaster = new THREE.Raycaster()
const exitPointer = new THREE.Vector2()
const loadingLabel = (key: string) => t(`courseDetail.classSession.loadingSteps.${key}`)
const BLACKBOARD_SEATED_STUDENT_LIMIT = 20
const BLACKBOARD_MULTI_BOARD_SEATED_STUDENT_COLUMNS = 3
const BLACKBOARD_SINGLE_BOARD_SEATED_STUDENT_COLUMNS = 4
const BLACKBOARD_SEATED_STUDENTS_PER_COLUMN = 4
const BLACKBOARD_LARGE_TOP_COLUMNS = 3
const BLACKBOARD_LARGE_TOP_ROWS = 3
const BLACKBOARD_LARGE_BOTTOM_COLUMNS = 3
const BLACKBOARD_LARGE_BOTTOM_ROWS = 4
const BLACKBOARD_LARGE_ROW_HEIGHT_MAX = 88
const BLACKBOARD_XLARGE_LEFT_COLUMNS = 3
const BLACKBOARD_XLARGE_LEFT_ROWS = 4
const BLACKBOARD_XLARGE_RIGHT_COLUMNS = 3
const BLACKBOARD_XLARGE_RIGHT_ROWS = 5
const BLACKBOARD_MULTI_BOARD_SEATED_STUDENTS_PER_BOARD = BLACKBOARD_MULTI_BOARD_SEATED_STUDENT_COLUMNS * BLACKBOARD_SEATED_STUDENTS_PER_COLUMN
const BLACKBOARD_SINGLE_BOARD_SEATED_STUDENTS_PER_BOARD = BLACKBOARD_SINGLE_BOARD_SEATED_STUDENT_COLUMNS * BLACKBOARD_SEATED_STUDENTS_PER_COLUMN
const BLACKBOARD_LATIN_FONT_FAMILY = '"EduPivot Blackboard Latin"'
const BLACKBOARD_CJK_FONT_FAMILY = '"EduPivot Blackboard CJK"'
const BLACKBOARD_FONT_FAMILY = `${BLACKBOARD_LATIN_FONT_FAMILY}, ${BLACKBOARD_CJK_FONT_FAMILY}, cursive, sans-serif`
const BLACKBOARD_CHALK_COLOR = 'rgba(250, 250, 238, 0.9)'

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
  if (mousemoveHandler && canvasRef.value) {
    canvasRef.value.removeEventListener('mousemove', mousemoveHandler)
  }
  mousemoveHandler = null
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
  for (const doorGroup of exitDoorRef.value) {
    sceneRef.value?.remove(doorGroup)
    disposeObject(doorGroup)
  }
  exitDoorRef.value = []
  for (const material of exitLabelMaterials) {
    material.dispose()
  }
  exitLabelMaterials.length = 0
  for (const textures of exitLabelTextures) {
    textures.normal.dispose()
    textures.highlight.dispose()
  }
  exitLabelTextures.length = 0
  hoveredExitDoorIndex.value = -1
  blackboardCanvasManager.dispose()
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
    scene.fog = new THREE.Fog(themeColor('--color-surface-canvas', '#0e0e0e'), 22, 72)

    const camera = new THREE.PerspectiveCamera(75, 1, 0.1, 1000)

    const renderer = new THREE.WebGLRenderer({canvas, antialias: true, alpha: false, preserveDrawingBuffer: true})
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
    renderer.outputColorSpace = THREE.SRGBColorSpace
    renderer.setClearColor(themeColor('--color-surface-canvas', '#0e0e0e'), 1)
    renderer.shadowMap.enabled = true

    const controls = new OrbitControls(camera, canvas)
    controls.enableDamping = true
    controls.target.copy(getCameraTarget(classroomDimensions.value))
    controls.maxPolarAngle = Math.PI * 0.54
    controls.minDistance = 1
    controls.maxDistance = props.session.roomSize === ClassRoomSize.XLARGE ? 32 : 18

    scene.add(new THREE.HemisphereLight(
        themeColor('--color-on-surface', '#f5f5f5'),
        themeColor('--color-surface-container-highest', '#292929'),
        2.2,
    ))

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
    mousemoveHandler = (event: MouseEvent) => {
      const rect = canvas.getBoundingClientRect()
      exitPointer.x = ((event.clientX - rect.left) / rect.width) * 2 - 1
      exitPointer.y = -((event.clientY - rect.top) / rect.height) * 2 + 1
    }
    canvas.addEventListener('mousemove', mousemoveHandler)
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
  classroomCameraBounds.value = createCameraBounds(classroomBounds, props.session.roomSize)
  applyCameraPreset(camera, controls)
  classroom.scene.traverse((child) => {
    if (child instanceof THREE.Mesh) {
      child.castShadow = true
      child.receiveShadow = true
    }
  })
  scene.add(classroom.scene)
  setupExitDoor(scene, classroom.scene, classroomBounds, props.session.roomSize)
  blackboardCanvasManager.mount(scene, props.session.roomSize)
  void waitForBlackboardFonts().then(renderSeatedStudentsOnBlackboard).catch(() => undefined)
  renderSeatedStudentsOnBlackboard()

  emitLoadingProgress(91, loadingLabel('arrangingDesks'))
  const deskVariantNames = route.desk.variantNames
  if (props.session.roomSize === ClassRoomSize.LARGE && deskVariantNames?.firstRow && deskVariantNames.otherRows) {
    const firstRowCount = 4
    const {firstRowMeshes, otherRowsMeshes} = modelInstanceManager.createMixedInstancedMeshes(
        desk.scene,
        deskVariantNames.firstRow,
        deskVariantNames.otherRows,
        firstRowCount,
        roomSpec.value.deskInstanceCount - firstRowCount,
    )
    modelInstanceManager.setMixedInstanceMatrices(
        firstRowMeshes,
        otherRowsMeshes,
        props.session.roomSize,
        firstRowCount,
        0,
        firstRowCount,
        (index) => getDeskPosition(props.session.roomSize, index, classroomDimensions.value),
    )
    for (const mesh of [...firstRowMeshes, ...otherRowsMeshes]) {
      deskInstancedMeshes.push(mesh)
      scene.add(mesh)
    }
  } else {
    const instancedMeshes = modelInstanceManager.createInstancedMeshes(desk.scene, roomSpec.value.deskInstanceCount, null)
    modelInstanceManager.setInstanceMatrices(instancedMeshes, props.session.roomSize, roomSpec.value.deskInstanceCount, (index) =>
        getDeskPosition(props.session.roomSize, index, classroomDimensions.value),
    )
    instancedMeshes.forEach((mesh) => {
      deskInstancedMeshes.push(mesh)
      scene.add(mesh)
    })
  }
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
    deskInstanceCount: roomSpec.value.deskInstanceCount,
    exitTarget: exitDoorRef.value.length > 0 ? exitDoorRef.value : null,
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
    const position = getAllSeatPositions(props.session.roomSize, classroomDimensions.value)[seatIndexToPositionIndex(seatIndex)]
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
  renderSeatedStudentsOnBlackboard()
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
  renderSeatedStudentsOnBlackboard()
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
  renderSeatedStudentsOnBlackboard()
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
  updateExitDoorHover(camera)
  rendererRef.value.render(sceneRef.value, camera)
}

function updateExitDoorHover(camera: THREE.PerspectiveCamera) {
  if (!canvasRef.value || exitDoorRef.value.length === 0 || exitLabelMaterials.length === 0) {
    return
  }

  exitRaycaster.setFromCamera(exitPointer, camera)

  let hoveredIndex = -1
  for (let i = 0; i < exitDoorRef.value.length; i += 1) {
    const group = exitDoorRef.value[i]
    const target = group.getObjectByName(`exit_door_target_${i}`)
    if (target && exitRaycaster.intersectObject(target, false).length > 0) {
      hoveredIndex = i
      break
    }
  }

  for (let i = 0; i < exitLabelMaterials.length; i += 1) {
    const material = exitLabelMaterials[i]
    const textures = exitLabelTextures[i]
    const isHovered = i === hoveredIndex
    const targetOpacity = isHovered ? 1.0 : 0.3
    const targetTexture = isHovered ? textures.highlight : textures.normal

    if (material.opacity !== targetOpacity) {
      material.opacity = targetOpacity
    }
    if (material.map !== targetTexture) {
      material.map = targetTexture
    }
    material.needsUpdate = true
  }

  hoveredExitDoorIndex.value = hoveredIndex
}

function renderSeatedStudentsOnBlackboard() {
  const canvasCount = blackboardCanvasManager.getCanvases().length
  if (canvasCount === 0) {
    return
  }
  const seatedStudents = Array.from(participantsBySeat.value.values())
      .filter(participant => participant.role === 1 && participant.seatIndex != null)
      .sort((a, b) => joinedAtTime(b) - joinedAtTime(a))
  const isXLargeRoom = props.session.roomSize === ClassRoomSize.XLARGE && canvasCount >= 4
  const studentBoardIndexes = isXLargeRoom ? [0, 3] : Array.from({length: canvasCount}, (_, index) => index)
  const isLargeRoom = props.session.roomSize === ClassRoomSize.LARGE && canvasCount >= 2
  const isSingleBoardGridRoom = canvasCount === 1
      && (props.session.roomSize === ClassRoomSize.SMALL || props.session.roomSize === ClassRoomSize.MEDIUM)
  const fixedColumnCount = studentBoardIndexes.length > 1
      ? BLACKBOARD_MULTI_BOARD_SEATED_STUDENT_COLUMNS
      : (isSingleBoardGridRoom ? BLACKBOARD_SINGLE_BOARD_SEATED_STUDENT_COLUMNS : 0)
  const studentsPerBoard = studentBoardIndexes.length > 1
      ? BLACKBOARD_MULTI_BOARD_SEATED_STUDENTS_PER_BOARD
      : (isSingleBoardGridRoom ? BLACKBOARD_SINGLE_BOARD_SEATED_STUDENTS_PER_BOARD : BLACKBOARD_SEATED_STUDENT_LIMIT)
  let largeRoomStartIndex = 0
  blackboardCanvasManager.render((context, canvas, index) => {
    const boardPageIndex = studentBoardIndexes.indexOf(index)
    if (boardPageIndex < 0) {
      if (isXLargeRoom) {
        drawCourseInfoOnBlackboard(
            context,
            canvas,
            index,
            props.openingTeacherName || props.session.teacherId,
        )
      }
      return
    }
    const boardGrid = blackboardStudentGrid(index)
    const boardCapacity = boardGrid ? boardGrid.columns * boardGrid.rows : studentsPerBoard
    const startIndex = boardGrid ? largeRoomStartIndex : boardPageIndex * studentsPerBoard
    const boardParticipants = seatedStudents.slice(startIndex, startIndex + boardCapacity)
    if (boardGrid) {
      largeRoomStartIndex += boardCapacity
    }
    if (startIndex > 0 && boardParticipants.length === 0) {
      return
    }
    const overflowCount = boardPageIndex === studentBoardIndexes.length - 1
        ? (boardGrid ? 0 : Math.max(seatedStudents.length - startIndex - boardParticipants.length, 0))
        : 0
    drawSeatedStudents(
        context,
        canvas,
        boardParticipants,
        seatedStudents.length,
        roomSpec.value.seatCount,
        fixedColumnCount > 0 ? canvas.width : canvas.width / 2,
        boardPageIndex === 0,
        startIndex,
        overflowCount,
        boardGrid?.columns ?? fixedColumnCount,
        boardGrid?.rows ?? BLACKBOARD_SEATED_STUDENTS_PER_COLUMN,
        isSingleBoardGridRoom || (isLargeRoom && index === 0) ? props.session.title : '',
        props.openingTeacherName || props.session.teacherId,
        Boolean(boardGrid),
    )
  })
}

function largeRoomBoardGrid(index: number) {
  if (props.session.roomSize !== ClassRoomSize.LARGE) {
    return null
  }
  if (index === 0) {
    return {columns: BLACKBOARD_LARGE_TOP_COLUMNS, rows: BLACKBOARD_LARGE_TOP_ROWS}
  }
  if (index === 1) {
    return {columns: BLACKBOARD_LARGE_BOTTOM_COLUMNS, rows: BLACKBOARD_LARGE_BOTTOM_ROWS}
  }
  return null
}

function xLargeRoomBoardGrid(index: number) {
  if (props.session.roomSize !== ClassRoomSize.XLARGE) {
    return null
  }
  if (index === 0) {
    return {columns: BLACKBOARD_XLARGE_LEFT_COLUMNS, rows: BLACKBOARD_XLARGE_LEFT_ROWS}
  }
  if (index === 3) {
    return {columns: BLACKBOARD_XLARGE_RIGHT_COLUMNS, rows: BLACKBOARD_XLARGE_RIGHT_ROWS}
  }
  return null
}

function blackboardStudentGrid(index: number) {
  return largeRoomBoardGrid(index) ?? xLargeRoomBoardGrid(index)
}

function drawCourseInfoOnBlackboard(
    context: CanvasRenderingContext2D,
    canvas: HTMLCanvasElement,
    index: number,
    teacherName: string,
) {
  const paddingX = 52
  const textWidth = canvas.width - paddingX * 2
  context.save()
  context.fillStyle = BLACKBOARD_CHALK_COLOR
  context.textBaseline = 'top'

  if (index === 1) {
    drawCenteredBlackboardText(context, props.session.title, canvas.width, 64, textWidth, 34)
    if (props.session.description) {
      drawCenteredBlackboardText(context, props.session.description, canvas.width, 150, textWidth, 24)
    }
  } else {
    context.font = `400 30px ${BLACKBOARD_FONT_FAMILY}`
    drawBlackboardText(context, `\u5f00\u8bfe\u6559\u5e08\uff1a${teacherName}`, paddingX, 72, textWidth)
    context.font = `400 26px ${BLACKBOARD_FONT_FAMILY}`
    drawBlackboardText(context, `\u4e0a\u8bfe\u65f6\u95f4\uff1a${formatBlackboardDateTime(props.session.scheduledStartAt)}`, paddingX, 154, textWidth)
  }

  context.restore()
}

function drawCenteredBlackboardText(
    context: CanvasRenderingContext2D,
    text: string,
    canvasWidth: number,
    y: number,
    maxWidth: number,
    fontSize: number,
) {
  context.font = `400 ${fontSize}px ${BLACKBOARD_FONT_FAMILY}`
  const fittedText = fitCanvasText(context, text, maxWidth)
  const textWidth = context.measureText(fittedText).width
  drawBlackboardText(context, fittedText, (canvasWidth - textWidth) / 2, y, textWidth + 1)
}

function drawSeatedStudents(
    context: CanvasRenderingContext2D,
    canvas: HTMLCanvasElement,
    participants: ClassParticipant[],
    totalParticipants: number,
    seatCount: number,
    targetWidth: number,
    showTitle = true,
    startIndex = 0,
    overflowCount = 0,
    fixedColumnCount = 0,
    fixedRowsPerColumn = BLACKBOARD_SEATED_STUDENTS_PER_COLUMN,
    boardTitle = '',
    teacherName = '',
    isLargeBoard = false,
) {
  const paddingX = isLargeBoard ? 44 : 52
  const textWidth = Math.max(targetWidth - paddingX * 2, 120)
  context.save()
  context.fillStyle = BLACKBOARD_CHALK_COLOR
  context.textBaseline = 'top'
  if (boardTitle) {
    context.font = `400 ${isLargeBoard ? 30 : 34}px ${BLACKBOARD_FONT_FAMILY}`
    const fittedBoardTitle = fitCanvasText(context, boardTitle, textWidth)
    const boardTitleWidth = context.measureText(fittedBoardTitle).width
    drawBlackboardText(context, fittedBoardTitle, (canvas.width - boardTitleWidth) / 2, isLargeBoard ? 16 : 20, boardTitleWidth + 1)
  }
  if (showTitle) {
    const title = `${t('courseDetail.classSession.seatedStudents')} ${totalParticipants}/${seatCount}`
    context.font = `400 ${isLargeBoard ? 36 : 42}px ${BLACKBOARD_FONT_FAMILY}`
    const titleY = boardTitle ? (isLargeBoard ? 56 : 72) : 44
    drawBlackboardText(context, title, paddingX, titleY, textWidth)
    if (teacherName) {
      const teacherText = `\u5f00\u8bfe\u6559\u5e08\uff1a${teacherName}`
      const teacherTextWidth = Math.min(context.measureText(teacherText).width, textWidth * 0.46)
      drawBlackboardText(context, teacherText, canvas.width - paddingX - teacherTextWidth, titleY, teacherTextWidth)
    }
  }

  if (totalParticipants === 0) {
    context.font = `400 30px ${BLACKBOARD_FONT_FAMILY}`
    drawBlackboardText(context, t('courseDetail.classSession.noSeatedStudents'), paddingX, 118, textWidth)
    context.restore()
    return
  }

  const visibleParticipants = participants.slice(0, BLACKBOARD_SEATED_STUDENT_LIMIT)
  const columnCount = fixedColumnCount > 0 ? fixedColumnCount : (visibleParticipants.length > 10 ? 2 : 1)
  const columnGap = columnCount > 1 ? 36 : 0
  const columnWidth = (textWidth - columnGap * (columnCount - 1)) / columnCount
  const rowsPerColumn = fixedColumnCount > 0
      ? fixedRowsPerColumn
      : Math.ceil(visibleParticipants.length / columnCount)
  const startY = showTitle ? (boardTitle ? (isLargeBoard ? 104 : 134) : 106) : (isLargeBoard ? 52 : 44)
  const rowHeightMax = isLargeBoard ? BLACKBOARD_LARGE_ROW_HEIGHT_MAX : 56
  const rowHeight = Math.max(34, Math.min(rowHeightMax, Math.floor((canvas.height - startY - 32) / Math.max(rowsPerColumn, 1))))
  const nameFontSize = isLargeBoard ? (rowHeight <= 58 ? 24 : 28) : (rowHeight <= 40 ? 22 : 30)
  const metaFontSize = isLargeBoard ? (rowHeight <= 58 ? 15 : 17) : (rowHeight <= 40 ? 13 : 20)
  const metaOffsetY = isLargeBoard ? (rowHeight <= 58 ? 24 : 30) : (rowHeight <= 40 ? 23 : 33)
  visibleParticipants.forEach((participant, index) => {
    const columnIndex = Math.floor(index / rowsPerColumn)
    const rowIndex = index % rowsPerColumn
    const x = paddingX + columnIndex * (columnWidth + columnGap)
    const y = startY + rowIndex * rowHeight
    context.font = `400 ${nameFontSize}px ${BLACKBOARD_FONT_FAMILY}`
    drawBlackboardText(context, `${startIndex + index + 1}. ${participant.displayName || participant.userId}`, x, y, columnWidth)
    context.font = `400 ${metaFontSize}px ${BLACKBOARD_FONT_FAMILY}`
    drawBlackboardText(
        context,
        `${t('courseDetail.classSession.seatNumber', {number: participant.seatIndex ?? 0})} / ${formatBlackboardDateTime(participant.joinedAt)}`,
        x + 28,
        y + metaOffsetY,
        columnWidth - 28,
    )
  })
  if (overflowCount > 0 || participants.length > visibleParticipants.length) {
    context.font = `400 22px ${BLACKBOARD_FONT_FAMILY}`
    drawBlackboardText(
        context,
        `+${overflowCount || participants.length - visibleParticipants.length}`,
        paddingX,
        canvas.height - 48,
        textWidth,
    )
  }
  context.restore()
}

async function waitForBlackboardFonts() {
  if (!document.fonts) {
    return
  }
  await Promise.all([
    document.fonts.load(`400 42px ${BLACKBOARD_CJK_FONT_FAMILY}`, '\u5df2\u843d\u5ea7'),
    document.fonts.load(`400 30px ${BLACKBOARD_LATIN_FONT_FAMILY}`, 'Wei Qing'),
    document.fonts.ready,
  ])
}

function joinedAtTime(participant: ClassParticipant) {
  const time = new Date(participant.joinedAt).getTime()
  return Number.isNaN(time) ? 0 : time
}

function drawBlackboardText(context: CanvasRenderingContext2D, text: string, x: number, y: number, maxWidth: number) {
  const fittedText = fitCanvasText(context, text, maxWidth)
  const textWidth = context.measureText(fittedText).width
  const fontSize = getCanvasFontSize(context.font)
  context.save()
  context.shadowColor = 'rgba(255, 255, 255, 0.16)'
  context.shadowBlur = 0.8
  context.fillText(fittedText, x, y)
  context.shadowBlur = 0
  context.globalAlpha = 0.32
  context.fillText(fittedText, x + 0.7, y + 0.35)
  context.globalAlpha = 0.2
  context.fillText(fittedText, x - 0.5, y + 0.7)
  context.globalAlpha = 1
  dustChalkText(context, fittedText, x, y, textWidth, fontSize)
  context.restore()
}

function dustChalkText(
    context: CanvasRenderingContext2D,
    text: string,
    x: number,
    y: number,
    width: number,
    fontSize: number,
) {
  const random = seededRandom(`${text}:${context.font}:${Math.round(x)}:${Math.round(y)}`)
  const dotCount = Math.max(8, Math.floor((width * fontSize) / 260))
  context.globalCompositeOperation = 'destination-out'
  context.fillStyle = 'rgba(0, 0, 0, 0.12)'
  for (let i = 0; i < dotCount; i += 1) {
    const dotX = x + random() * width
    const dotY = y + random() * fontSize * 1.18
    const radius = 0.35 + random() * 0.9
    context.globalAlpha = 0.16 + random() * 0.18
    context.beginPath()
    context.arc(dotX, dotY, radius, 0, Math.PI * 2)
    context.fill()
  }
}

function seededRandom(seedText: string) {
  let seed = 2166136261
  for (let i = 0; i < seedText.length; i += 1) {
    seed ^= seedText.charCodeAt(i)
    seed = Math.imul(seed, 16777619)
  }
  return () => {
    seed += 0x6D2B79F5
    let value = seed
    value = Math.imul(value ^ (value >>> 15), value | 1)
    value ^= value + Math.imul(value ^ (value >>> 7), value | 61)
    return ((value ^ (value >>> 14)) >>> 0) / 4294967296
  }
}

function getCanvasFontSize(font: string) {
  const match = /(\d+(?:\.\d+)?)px/.exec(font)
  return match ? Number(match[1]) : 24
}

function fitCanvasText(context: CanvasRenderingContext2D, text: string, maxWidth: number) {
  if (context.measureText(text).width <= maxWidth) {
    return text
  }
  const ellipsis = '...'
  let end = text.length
  while (end > 0 && context.measureText(`${text.slice(0, end)}${ellipsis}`).width > maxWidth) {
    end -= 1
  }
  return `${text.slice(0, end)}${ellipsis}`
}

function formatBlackboardDateTime(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return '-'
  }
  return new Intl.DateTimeFormat(locale.value, {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
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

function getExitDoorSize(roomSize: number) {
  switch (roomSize) {
    case ClassRoomSize.LARGE:
      return {
        labelWidth: 2.8,
        labelHeight: 0.68,
        labelY: 1.35,
        targetWidth: 3,
        targetHeight: 1,
        targetDepth: 0.8,
        targetY: 1.2,
      }
    case ClassRoomSize.XLARGE:
      return {
        labelWidth: 1.5,
        labelHeight: 0.68,
        labelY: 1.35,
        targetWidth: 1.7,
        targetHeight: 1,
        targetDepth: 0.8,
        targetY: 1.2,
      }
    default:
      return {
        labelWidth: 2.2,
        labelHeight: 0.68,
        labelY: 1.35,
        targetWidth: 2.6,
        targetHeight: 1,
        targetDepth: 0.8,
        targetY: 1.2,
      }
  }
}

function setupExitDoor(scene: THREE.Scene, classroom: THREE.Object3D, bounds: THREE.Box3, roomSize: number) {
  const anchors = findDoorAnchors(classroom, bounds, roomSize)
  const exitDoorSize = getExitDoorSize(roomSize)

  for (let i = 0; i < anchors.length; i += 1) {
    const group = new THREE.Group()
    group.name = `exit_door_${i}`

    const anchor = anchors[i]
    group.position.copy(anchor.position)
    group.quaternion.copy(anchor.quaternion)

    const textures = createExitLabelTextures()
    exitLabelTextures.push(textures)

    const labelGeometry = new THREE.PlaneGeometry(exitDoorSize.labelWidth, exitDoorSize.labelHeight)
    const labelMaterial = new THREE.MeshBasicMaterial({
      map: textures.normal,
      transparent: true,
      opacity: 0.3,
      depthTest: true,
      depthWrite: false,
      side: THREE.DoubleSide,
    })
    exitLabelMaterials.push(labelMaterial)
    const label = new THREE.Mesh(labelGeometry, labelMaterial)
    label.name = `exit_door_label_${i}`
    label.position.set(0, exitDoorSize.labelY, 0)
    group.add(label)

    const targetGeometry = new THREE.BoxGeometry(exitDoorSize.targetWidth, exitDoorSize.targetHeight, exitDoorSize.targetDepth)
    targetGeometry.translate(0, exitDoorSize.targetY, 0)
    const targetMaterial = new THREE.MeshBasicMaterial({
      transparent: true,
      opacity: 0,
      depthWrite: false,
      colorWrite: false,
    })
    const target = new THREE.Mesh(targetGeometry, targetMaterial)
    target.name = `exit_door_target_${i}`
    group.add(target)

    scene.add(group)
    exitDoorRef.value.push(group)
  }
}

function findDoorAnchors(_classroom: THREE.Object3D, bounds: THREE.Box3, roomSize: number) {
  const y = bounds.min.y + 2

  switch (roomSize) {
    case ClassRoomSize.SMALL:
      return [
        {
          position: new THREE.Vector3(-3.4, 1, 3.2),
          quaternion: new THREE.Quaternion().setFromAxisAngle(new THREE.Vector3(0, 1, 0), Math.PI)
        },
      ]

    case ClassRoomSize.MEDIUM:
      return [
        {
          position: new THREE.Vector3(-7.3, y, 7.2),
          quaternion: new THREE.Quaternion().setFromAxisAngle(new THREE.Vector3(0, 1, 0), Math.PI / 2)
        },
        {
          position: new THREE.Vector3(-7.3, y, -7.3),
          quaternion: new THREE.Quaternion().setFromAxisAngle(new THREE.Vector3(0, 1, 0), Math.PI / 2)
        },
      ]

    case ClassRoomSize.LARGE:
      return [
        {
          position: new THREE.Vector3(-7.5, y, 14.9),
          quaternion: new THREE.Quaternion().setFromAxisAngle(new THREE.Vector3(0, 1, 0), -Math.PI)
        },
        {
          position: new THREE.Vector3(7.5, y, 14.9),
          quaternion: new THREE.Quaternion().setFromAxisAngle(new THREE.Vector3(0, 1, 0), -Math.PI)
        },
      ]

    case ClassRoomSize.XLARGE:
      return [
        {
          position: new THREE.Vector3(-9.3, 1, 8),
          quaternion: new THREE.Quaternion().setFromAxisAngle(new THREE.Vector3(0, 1, 0), Math.PI / 2)
        },
        {
          position: new THREE.Vector3(9.3, 1, 8),
          quaternion: new THREE.Quaternion().setFromAxisAngle(new THREE.Vector3(0, 1, 0), -Math.PI / 2)
        },
      ]

    default:
      return [
        {position: new THREE.Vector3(0, y, 5), quaternion: faceRoomCenter(new THREE.Vector3(0, y, 5))},
      ]
  }
}

function faceRoomCenter(position: THREE.Vector3) {
  const quaternion = new THREE.Quaternion()
  const direction = new THREE.Vector3(-position.x, 0, -position.z)
  if (direction.lengthSq() < 0.001) {
    return quaternion
  }
  return quaternion.setFromUnitVectors(new THREE.Vector3(0, 0, 1), direction.normalize())
}

function createExitLabelTextures() {
  const labelText = t('courseDetail.classSession.exitClassroomLabel')

  const normalTexture = createLabelTexture(labelText, false, 'center')
  const highlightTexture = createLabelTexture(labelText, true, 'center')

  return {normal: normalTexture, highlight: highlightTexture}
}

function createLabelTexture(labelText: string | string[], isHighlight: boolean, textAlign: 'left' | 'center' = 'left') {
  const canvas = document.createElement('canvas')
  const lines = Array.isArray(labelText) ? labelText : [labelText]
  const lineCount = lines.length
  canvas.width = 512
  canvas.height = lineCount === 1 ? 160 : 220
  const context = canvas.getContext('2d')
  if (context) {
    const textColor = themeValue('--color-on-surface', '#f5f5f5')
    const mutedColor = themeValue('--color-on-surface-variant', '#d4d4d4')
    const labelFont = themeValue('--font-label', 'sans-serif')
    context.clearRect(0, 0, canvas.width, canvas.height)

    context.lineWidth = isHighlight ? 8 : 4
    context.strokeStyle = isHighlight ? textColor : mutedColor
    context.textAlign = textAlign

    const paddingX = textAlign === 'left' ? 10 : 0
    const lineHeight = lineCount === 1 ? 0 : 75
    const startY = lineCount === 1 ? canvas.height / 2 : 50

    lines.forEach((line, index) => {
      const fontSize = lineCount === 1 ? 58 : (index === 0 ? 48 : 40)
      context.font = `500 ${fontSize}px ${labelFont}`
      context.textBaseline = 'middle'
      const x = textAlign === 'left' ? paddingX : canvas.width / 2
      const y = startY + index * lineHeight

      if (isHighlight) {
        context.shadowColor = textColor
        context.shadowBlur = 15
        context.shadowOffsetX = 0
        context.shadowOffsetY = 0
      }

      context.strokeText(line, x, y + 3)
      context.fillStyle = isHighlight ? textColor : mutedColor
      context.fillText(line, x, y + 3)

      context.shadowBlur = 0
    })
  }
  const texture = new THREE.CanvasTexture(canvas)
  texture.colorSpace = THREE.SRGBColorSpace
  texture.minFilter = THREE.LinearFilter
  texture.magFilter = THREE.LinearFilter
  texture.needsUpdate = true
  return texture
}

function createCameraBounds(classroomBounds: THREE.Box3, roomSize: number) {
  const size = new THREE.Vector3()
  classroomBounds.getSize(size)
  const center = new THREE.Vector3()
  classroomBounds.getCenter(center)

  // XLARGE 教室使用模型 60% 大小的边界框
  if (roomSize === ClassRoomSize.XLARGE) {
    const bounds = new THREE.Box3()
    bounds.min.set(
        center.x - size.x * 0.3,
        center.y - size.y * 0.3,
        center.z - size.z * 0.3 - 4,
    )
    bounds.max.set(
        center.x + size.x * 0.3,
        center.y + size.y * 0.1,
        center.z + size.z * 0.3 - 4,
    )
    return bounds
  }

  // 其他规模保持原有逻辑
  const bounds = classroomBounds.clone()
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

function themeValue(name: string, fallback: string) {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim() || fallback
}

function themeColor(name: string, fallback: string) {
  return new THREE.Color(themeValue(name, fallback))
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
  background: var(--color-surface-canvas);
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
  place-items: center;
  align-content: center;
  gap: 16px;
  transform: translate(-50%, -50%);
  color: var(--color-on-surface);
  text-align: center;
}

.classroom-error p {
  margin: 0;
  font-family: var(--font-body);
  font-size: 18px;
  font-weight: 600;
}

@media (max-width: 760px) {
  .classroom-3d {
    height: 100dvh;
  }
}
</style>
