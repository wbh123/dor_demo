<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as THREE from 'three'

export type SceneBed = Record<string, unknown>

const props = withDefaults(
  defineProps<{
    beds: SceneBed[]
    selectedBedIds: number[]
    disabled?: boolean
  }>(),
  { disabled: false },
)

const emit = defineEmits<{
  select: [bed: SceneBed]
}>()

const container = ref<HTMLDivElement | null>(null)
const webglUnavailable = ref(false)
const selectedBedLabel = computed(() =>
  props.beds
    .filter((bed) => props.selectedBedIds.includes(Number(bed.id)))
    .map((bed) => `${String(bed.bed_code)}床`)
    .join('、'),
)

let scene: THREE.Scene | null = null
let camera: THREE.PerspectiveCamera | null = null
let renderer: THREE.WebGLRenderer | null = null
let bedLayer: THREE.Group | null = null
let resizeObserver: ResizeObserver | null = null
let renderFrame = 0

const raycaster = new THREE.Raycaster()
const pointer = new THREE.Vector2()
const clickableObjects: THREE.Object3D[] = []
const bedById = new Map<number, SceneBed>()

const reducedMotionQuery = '(prefers-reduced-motion: reduce)'

onMounted(() => {
  initializeScene()
})

watch(
  () => [props.beds, props.selectedBedIds, props.disabled],
  () => renderBeds(),
  { deep: true },
)

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  resizeObserver = null
  if (renderFrame) cancelAnimationFrame(renderFrame)
  if (renderer) {
    renderer.domElement.removeEventListener('pointerdown', handlePointerDown)
    renderer.domElement.removeEventListener('pointermove', handlePointerMove)
    renderer.dispose()
    renderer.forceContextLoss()
  }
  if (scene) disposeObject(scene)
  scene = null
  camera = null
  renderer = null
  bedLayer = null
})

function initializeScene() {
  if (!container.value) return

  try {
    scene = new THREE.Scene()
    scene.background = new THREE.Color('#eaf2ff')
    scene.fog = new THREE.Fog('#eaf2ff', 17, 28)

    camera = new THREE.PerspectiveCamera(39, 1, 0.1, 60)
    renderer = new THREE.WebGLRenderer({ antialias: true, alpha: false, powerPreference: 'high-performance' })
    renderer.outputColorSpace = THREE.SRGBColorSpace
    renderer.shadowMap.enabled = true
    renderer.shadowMap.type = THREE.PCFSoftShadowMap
    renderer.domElement.className = 'three-bed-scene-canvas'
    renderer.domElement.setAttribute('aria-label', '三维宿舍床位布局，可点击空床位进行选择')
    renderer.domElement.setAttribute('role', 'img')
    renderer.domElement.style.touchAction = 'manipulation'
    renderer.domElement.addEventListener('pointerdown', handlePointerDown)
    renderer.domElement.addEventListener('pointermove', handlePointerMove)
    container.value.appendChild(renderer.domElement)

    const reducedMotion = window.matchMedia(reducedMotionQuery).matches
    renderer.domElement.dataset.motion = reducedMotion ? 'reduced' : 'standard'

    addLights()
    addRoomShell()
    bedLayer = new THREE.Group()
    scene.add(bedLayer)
    renderBeds()

    resizeObserver = new ResizeObserver(() => resizeScene())
    resizeObserver.observe(container.value)
    resizeScene()
  } catch (reason) {
    webglUnavailable.value = true
    console.warn('Three.js床位场景初始化失败，将使用下拉框完成选择。', reason)
  }
}

function addLights() {
  if (!scene) return
  scene.add(new THREE.HemisphereLight('#ffffff', '#7f93ad', 2.2))

  const mainLight = new THREE.DirectionalLight('#ffffff', 3.1)
  mainLight.position.set(-5, 11, 8)
  mainLight.castShadow = true
  mainLight.shadow.mapSize.set(1024, 1024)
  mainLight.shadow.camera.left = -10
  mainLight.shadow.camera.right = 10
  mainLight.shadow.camera.top = 10
  mainLight.shadow.camera.bottom = -10
  scene.add(mainLight)

  const windowLight = new THREE.PointLight('#9ed9ff', 17, 18, 2)
  windowLight.position.set(4.6, 4.2, -2.8)
  scene.add(windowLight)
}

function addRoomShell() {
  if (!scene) return

  const floorMaterial = new THREE.MeshStandardMaterial({ color: '#f7f9fc', roughness: 0.82 })
  const floor = new THREE.Mesh(new THREE.BoxGeometry(11.5, 0.18, 8), floorMaterial)
  floor.position.y = -0.12
  floor.receiveShadow = true
  scene.add(floor)

  const grid = new THREE.GridHelper(11.2, 14, '#b8c7dc', '#d9e2ef')
  grid.position.y = 0.01
  grid.scale.z = 0.7
  scene.add(grid)

  const wallMaterial = new THREE.MeshStandardMaterial({ color: '#dfeafb', roughness: 0.9 })
  const backWall = new THREE.Mesh(new THREE.BoxGeometry(11.5, 4.8, 0.16), wallMaterial)
  backWall.position.set(0, 2.3, -3.92)
  backWall.receiveShadow = true
  scene.add(backWall)

  const sideWall = new THREE.Mesh(new THREE.BoxGeometry(0.16, 4.8, 8), wallMaterial)
  sideWall.position.set(5.66, 2.3, 0)
  sideWall.receiveShadow = true
  scene.add(sideWall)

  const windowFrame = new THREE.MeshStandardMaterial({ color: '#8ba9ca', roughness: 0.52 })
  const windowGlass = new THREE.MeshPhysicalMaterial({
    color: '#a8d9ff',
    transmission: 0.38,
    transparent: true,
    opacity: 0.78,
    roughness: 0.14,
  })
  const glass = new THREE.Mesh(new THREE.BoxGeometry(3.1, 1.45, 0.08), windowGlass)
  glass.position.set(3.05, 2.9, -3.78)
  scene.add(glass)
  addBox(scene, [3.35, 0.13, 0.18], [3.05, 3.68, -3.69], windowFrame)
  addBox(scene, [3.35, 0.13, 0.18], [3.05, 2.12, -3.69], windowFrame)
  addBox(scene, [0.13, 1.68, 0.18], [1.38, 2.9, -3.69], windowFrame)
  addBox(scene, [0.13, 1.68, 0.18], [4.72, 2.9, -3.69], windowFrame)
  addBox(scene, [0.1, 1.55, 0.16], [3.05, 2.9, -3.66], windowFrame)
  const windowLabel = createLabelSprite('窗户', '#405b80', '#e8f5ff')
  windowLabel.position.set(3.05, 1.72, -3.58)
  scene.add(windowLabel)

  const doorMaterial = new THREE.MeshStandardMaterial({ color: '#c99d70', roughness: 0.72 })
  const door = new THREE.Mesh(new THREE.BoxGeometry(0.16, 2.8, 1.7), doorMaterial)
  door.position.set(-5.58, 1.38, 2.2)
  door.castShadow = true
  scene.add(door)
  const doorLabel = createLabelSprite('入口', '#69482f', '#f4dfc5')
  doorLabel.position.set(-5.35, 3.05, 2.2)
  scene.add(doorLabel)
}

function renderBeds() {
  if (!scene || !bedLayer || !renderer || !camera) return

  while (bedLayer.children.length) {
    const child = bedLayer.children.pop()
    if (child) {
      bedLayer.remove(child)
      disposeObject(child)
    }
  }
  clickableObjects.length = 0
  bedById.clear()

  for (const bed of props.beds) {
    const bedId = Number(bed.id)
    if (!Number.isFinite(bedId)) continue
    bedById.set(bedId, bed)

    const type = String(bed.bed_type)
    const placement = bedPlacement(bed)
    const group = type === 'BUNK_UPPER' || type === 'BUNK_LOWER'
      ? createBunkBed(bed, placement)
      : createLoftBed(bed, placement)
    bedLayer.add(group)
  }

  requestRender()
}

function bedPlacement(bed: SceneBed) {
  const type = String(bed.bed_type)
  if (type === 'BUNK_UPPER') return new THREE.Vector3(3.65, 2.15, 1.55)
  if (type === 'BUNK_LOWER') return new THREE.Vector3(3.65, 0.78, 1.55)

  const position = Number(bed.position_index)
  if (position === 1) return new THREE.Vector3(-2.75, 0, -1.35)
  if (position === 2) return new THREE.Vector3(-2.75, 0, 1.55)
  if (position === 3) return new THREE.Vector3(0.2, 0, 1.55)
  return new THREE.Vector3(0.2, 0, -1.35)
}

function createLoftBed(bed: SceneBed, placement: THREE.Vector3) {
  const group = new THREE.Group()
  group.position.copy(placement)
  const appearance = bedAppearance(bed)
  const frame = new THREE.MeshStandardMaterial({ color: '#476386', roughness: 0.56, metalness: 0.08 })
  const desk = new THREE.MeshStandardMaterial({ color: '#d7a773', roughness: 0.68 })
  const mattress = new THREE.MeshStandardMaterial({
    color: appearance.color,
    emissive: appearance.emissive,
    emissiveIntensity: appearance.selected ? 0.7 : 0.08,
    roughness: 0.52,
  })

  addBox(group, [2.45, 0.28, 1.05], [0, 2.15, 0], mattress, appearance)
  addBox(group, [2.55, 0.12, 1.12], [0, 1.94, 0], frame)
  addBox(group, [0.12, 2.02, 0.12], [-1.16, 1.02, -0.46], frame)
  addBox(group, [0.12, 2.02, 0.12], [-1.16, 1.02, 0.46], frame)
  addBox(group, [0.12, 2.02, 0.12], [1.16, 1.02, -0.46], frame)
  addBox(group, [0.12, 2.02, 0.12], [1.16, 1.02, 0.46], frame)
  addBox(group, [1.78, 0.16, 0.82], [0.18, 0.94, 0], desk)
  addBox(group, [0.12, 0.86, 0.12], [-0.62, 0.46, -0.34], desk)
  addBox(group, [0.12, 0.86, 0.12], [-0.62, 0.46, 0.34], desk)
  addBox(group, [0.12, 0.86, 0.12], [0.98, 0.46, -0.34], desk)
  addBox(group, [0.12, 0.86, 0.12], [0.98, 0.46, 0.34], desk)

  addSelectionMarker(group, appearance, 2.8, 1.35, 0.08)
  addBedHitArea(group, bed, [2.75, 2.55, 1.35], [0, 1.25, 0], appearance.selectable)
  const label = createLabelSprite(`${String(bed.bed_code)}床`, appearance.labelColor, appearance.labelBackground)
  label.position.set(0, 2.85, 0)
  group.add(label)
  return group
}

function createBunkBed(bed: SceneBed, placement: THREE.Vector3) {
  const group = new THREE.Group()
  group.position.copy(placement)
  const appearance = bedAppearance(bed)
  const frame = new THREE.MeshStandardMaterial({ color: '#476386', roughness: 0.55, metalness: 0.1 })
  const mattress = new THREE.MeshStandardMaterial({
    color: appearance.color,
    emissive: appearance.emissive,
    emissiveIntensity: appearance.selected ? 0.82 : 0.08,
    roughness: 0.5,
  })

  addBox(group, [2.45, 0.28, 1.05], [0, 0, 0], mattress, appearance)
  addBox(group, [2.56, 0.1, 1.14], [0, -0.2, 0], frame)
  addBox(group, [2.5, 0.12, 0.12], [0, 0.3, -0.5], frame)
  addBox(group, [2.5, 0.12, 0.12], [0, 0.3, 0.5], frame)
  addBox(group, [0.12, 2.85, 0.12], [-1.18, 0.15, -0.48], frame)
  addBox(group, [0.12, 2.85, 0.12], [-1.18, 0.15, 0.48], frame)
  addBox(group, [0.12, 2.85, 0.12], [1.18, 0.15, -0.48], frame)
  addBox(group, [0.12, 2.85, 0.12], [1.18, 0.15, 0.48], frame)

  addSelectionMarker(group, appearance, 2.82, 1.38, -0.36)
  addBedHitArea(group, bed, [2.75, 0.92, 1.4], [0, 0.02, 0], appearance.selectable)
  const label = createLabelSprite(`${String(bed.bed_code)}床`, appearance.labelColor, appearance.labelBackground)
  label.position.set(0, 0.78, 0)
  group.add(label)
  return group
}

function addBox(
  parent: THREE.Object3D,
  size: [number, number, number],
  position: [number, number, number],
  material: THREE.Material,
  appearance?: ReturnType<typeof bedAppearance>,
) {
  const mesh = new THREE.Mesh(new THREE.BoxGeometry(...size), material)
  mesh.position.set(...position)
  mesh.castShadow = true
  mesh.receiveShadow = true
  if (appearance) mesh.userData.bedId = appearance.bedId
  parent.add(mesh)
  return mesh
}

function addBedHitArea(
  group: THREE.Group,
  bed: SceneBed,
  size: [number, number, number],
  position: [number, number, number],
  selectable: boolean,
) {
  const hitArea = new THREE.Mesh(
    new THREE.BoxGeometry(...size),
    new THREE.MeshBasicMaterial({ transparent: true, opacity: 0.001, depthWrite: false }),
  )
  hitArea.position.set(...position)
  hitArea.userData.bedId = Number(bed.id)
  hitArea.userData.selectable = selectable
  group.add(hitArea)
  clickableObjects.push(hitArea)
}

function addSelectionMarker(
  group: THREE.Group,
  appearance: ReturnType<typeof bedAppearance>,
  width: number,
  depth: number,
  y: number,
) {
  if (!appearance.selected) return
  const marker = new THREE.Mesh(
    new THREE.BoxGeometry(width, 0.09, depth),
    new THREE.MeshBasicMaterial({ color: '#00c2ff', transparent: true, opacity: 0.72 }),
  )
  marker.position.set(0, y, 0)
  group.add(marker)

  const check = new THREE.Mesh(
    new THREE.SphereGeometry(0.2, 20, 20),
    new THREE.MeshStandardMaterial({ color: '#15c98a', emissive: '#15c98a', emissiveIntensity: 1.2 }),
  )
  check.position.set(width / 2 - 0.15, y + 0.4, -depth / 2 + 0.15)
  group.add(check)
}

function bedAppearance(bed: SceneBed) {
  const bedId = Number(bed.id)
  const selected = props.selectedBedIds.includes(bedId)
  const status = String(bed.status)
  const selectable = !props.disabled && (status === 'AVAILABLE' || (selected && status === 'HELD_BY_ME'))

  if (selected) {
    return {
      bedId,
      selected,
      selectable,
      color: '#1263e6',
      emissive: '#00b7ff',
      labelColor: '#ffffff',
      labelBackground: '#0b57d0',
    }
  }
  if (status === 'AVAILABLE') {
    return {
      bedId,
      selected,
      selectable,
      color: '#b9d7ff',
      emissive: '#1d5aa5',
      labelColor: '#264b79',
      labelBackground: '#edf5ff',
    }
  }
  if (status === 'HELD_BY_ME') {
    return {
      bedId,
      selected,
      selectable,
      color: '#66d8b0',
      emissive: '#138a65',
      labelColor: '#0d684d',
      labelBackground: '#e1fff4',
    }
  }
  if (status === 'HELD') {
    return {
      bedId,
      selected,
      selectable: false,
      color: '#efbd6c',
      emissive: '#8b5d17',
      labelColor: '#75501a',
      labelBackground: '#fff3dc',
    }
  }
  return {
    bedId,
    selected,
    selectable: false,
    color: '#aeb8c8',
    emissive: '#4e5969',
    labelColor: '#5d6674',
    labelBackground: '#edf0f4',
  }
}

function createLabelSprite(text: string, color: string, background: string) {
  const canvas = document.createElement('canvas')
  canvas.width = 320
  canvas.height = 112
  const context = canvas.getContext('2d')
  if (context) {
    context.fillStyle = background
    context.fillRect(6, 10, 308, 92)
    context.strokeStyle = color
    context.lineWidth = 6
    context.strokeRect(6, 10, 308, 92)
    context.fillStyle = color
    context.font = '700 44px system-ui, sans-serif'
    context.textAlign = 'center'
    context.textBaseline = 'middle'
    context.fillText(text, 160, 57)
  }
  const texture = new THREE.CanvasTexture(canvas)
  texture.colorSpace = THREE.SRGBColorSpace
  const sprite = new THREE.Sprite(new THREE.SpriteMaterial({ map: texture, transparent: true }))
  sprite.scale.set(1.75, 0.62, 1)
  return sprite
}

function resizeScene() {
  if (!container.value || !renderer || !camera) return
  const width = Math.max(container.value.clientWidth, 280)
  const height = Math.max(container.value.clientHeight, 360)
  const mobile = width <= 640
  const ratio = Math.min(window.devicePixelRatio || 1, mobile ? 1.25 : 1.75)

  renderer.setPixelRatio(ratio)
  renderer.setSize(width, height, false)
  camera.aspect = width / height
  camera.position.set(mobile ? 9.7 : 9, mobile ? 8.8 : 7.7, mobile ? 16.8 : 13.3)
  camera.lookAt(0, 1.05, 0)
  camera.updateProjectionMatrix()
  requestRender()
}

function getIntersection(event: PointerEvent) {
  if (!renderer || !camera) return null
  const rect = renderer.domElement.getBoundingClientRect()
  pointer.x = ((event.clientX - rect.left) / rect.width) * 2 - 1
  pointer.y = -((event.clientY - rect.top) / rect.height) * 2 + 1
  raycaster.setFromCamera(pointer, camera)
  return raycaster.intersectObjects(clickableObjects, false)[0] ?? null
}

function handlePointerMove(event: PointerEvent) {
  if (!renderer) return
  const intersection = getIntersection(event)
  renderer.domElement.style.cursor = intersection?.object.userData.selectable ? 'pointer' : 'default'
}

function handlePointerDown(event: PointerEvent) {
  const intersection = getIntersection(event)
  if (!intersection?.object.userData.selectable) return
  const bedId = Number(intersection.object.userData.bedId)
  const bed = bedById.get(bedId)
  if (bed) emit('select', bed)
}

function requestRender() {
  if (!renderer || !scene || !camera) return
  if (renderFrame) cancelAnimationFrame(renderFrame)
  renderFrame = requestAnimationFrame(() => {
    renderFrame = 0
    renderer?.render(scene as THREE.Scene, camera as THREE.PerspectiveCamera)
  })
}

function disposeObject(object: THREE.Object3D) {
  object.traverse((child) => {
    const geometry = (child as THREE.Mesh).geometry
    if (geometry instanceof THREE.BufferGeometry) geometry.dispose()

    const material = (child as THREE.Mesh).material as THREE.Material | THREE.Material[] | undefined
    if (Array.isArray(material)) material.forEach(disposeMaterial)
    else if (material) disposeMaterial(material)
  })
}

function disposeMaterial(material: THREE.Material) {
  if (material instanceof THREE.SpriteMaterial && material.map) material.map.dispose()
  material.dispose()
}
</script>

<template>
  <div class="three-bed-scene" :class="{ 'three-scene-selected': selectedBedIds.length > 0 }">
    <div ref="container" class="three-bed-scene-mount" />
    <div class="three-scene-orientation" aria-hidden="true">
      <span>入口在左侧</span>
      <span>窗户在右侧</span>
      <span>窗边上下铺与B、C床同排</span>
    </div>
    <div v-if="selectedBedLabel" class="three-scene-selection-badge">
      <strong>✓ 已选中</strong>
      <span>{{ selectedBedLabel }}</span>
    </div>
    <p v-if="webglUnavailable" class="three-scene-fallback">
      当前浏览器无法显示三维场景，请使用上方床位下拉框完成选择。
    </p>
  </div>
</template>
