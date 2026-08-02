<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { api } from '../../api/client'
import type { DataObject, ObjectSuccessResponse } from '../../api/types'

interface LayoutBed {
  id: number
  bed_code: string
  bed_type: string
  position_index: number
  bed_frame_id: number | null
  operational_status: string
  occupied: boolean
  layout_x: number
  layout_z: number
  rotation_degrees: number
  custom_layout: boolean
}

interface LayoutUnit {
  key: string
  label: string
  bedIds: number[]
  x: number
  z: number
  rotation: number
  bunk: boolean
}

const props = defineProps<{
  roomId: number
  roomLabel: string
}>()

const emit = defineEmits<{
  close: []
  saved: []
}>()

const MIN_X = -5.2
const MAX_X = 5.2
const MIN_Z = -3.5
const MAX_Z = 3.5
const SNAP = 0.25
const BED_TYPE_OPTIONS = [
  { value: 'LOFT_BED_DESK', label: '上床下桌' },
  { value: 'BUNK_UPPER', label: '上下铺上铺' },
  { value: 'BUNK_LOWER', label: '上下铺下铺' },
]

const stage = ref<HTMLDivElement | null>(null)
const reasonInput = ref<HTMLTextAreaElement | null>(null)
const beds = ref<LayoutBed[]>([])
const roomVersion = ref(0)
const currentRoomType = ref('OTHER')
const reason = ref('')
const loading = ref(true)
const saving = ref(false)
const error = ref('')
const message = ref('')
const dragKey = ref<string | null>(null)

const synchronizedRoomType = computed(() => roomTypeForBedCount(beds.value.length))
const layoutUnits = computed<LayoutUnit[]>(() => {
  const groups = new Map<string, LayoutBed[]>()
  for (const bed of beds.value) {
    const bunk = bed.bed_type === 'BUNK_UPPER' || bed.bed_type === 'BUNK_LOWER'
    const key = bunk && bed.bed_frame_id ? `frame-${bed.bed_frame_id}` : `bed-${bed.id}`
    const items = groups.get(key) ?? []
    items.push(bed)
    groups.set(key, items)
  }
  return [...groups.entries()].map(([key, items]) => {
    const first = items[0]
    return {
      key,
      label: items.map((item) => item.bed_code).join(' / '),
      bedIds: items.map((item) => item.id),
      x: first.layout_x,
      z: first.layout_z,
      rotation: first.rotation_degrees,
      bunk: items.length > 1 || first.bed_type.startsWith('BUNK_'),
    }
  })
})

onMounted(load)
onBeforeUnmount(stopDrag)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const response = await api.get<ObjectSuccessResponse>(
      `/api/v1/admin/rooms/${props.roomId}/bed-layout`,
    )
    const data = (response.data.data ?? {}) as DataObject
    const room = (data.room ?? {}) as DataObject
    roomVersion.value = Number(room.room_version)
    currentRoomType.value = String(room.room_type ?? 'OTHER')
    beds.value = ((data.beds ?? []) as DataObject[]).map(parseBed)
  } catch (reasonValue) {
    error.value = reasonValue instanceof Error ? reasonValue.message : '房间布局加载失败'
  } finally {
    loading.value = false
  }
}

function parseBed(value: DataObject): LayoutBed {
  return {
    id: Number(value.id),
    bed_code: String(value.bed_code),
    bed_type: String(value.bed_type),
    position_index: Number(value.position_index),
    bed_frame_id: value.bed_frame_id == null ? null : Number(value.bed_frame_id),
    operational_status: String(value.operational_status),
    occupied: Number(value.occupied ?? 0) === 1,
    layout_x: Number(value.layout_x),
    layout_z: Number(value.layout_z),
    rotation_degrees: Number(value.rotation_degrees),
    custom_layout: Boolean(value.custom_layout),
  }
}

function startDrag(unit: LayoutUnit, event: PointerEvent) {
  if (saving.value) return
  error.value = ''
  message.value = ''
  dragKey.value = unit.key
  updateFromPointer(unit.key, event)
  window.addEventListener('pointermove', onPointerMove)
  window.addEventListener('pointerup', stopDrag, { once: true })
}

function onPointerMove(event: PointerEvent) {
  if (dragKey.value) updateFromPointer(dragKey.value, event)
}

function stopDrag() {
  dragKey.value = null
  window.removeEventListener('pointermove', onPointerMove)
  window.removeEventListener('pointerup', stopDrag)
}

function updateFromPointer(key: string, event: PointerEvent) {
  if (!stage.value) return
  const rect = stage.value.getBoundingClientRect()
  const x = MIN_X + ((event.clientX - rect.left) / rect.width) * (MAX_X - MIN_X)
  const z = MIN_Z + ((event.clientY - rect.top) / rect.height) * (MAX_Z - MIN_Z)
  updateUnit(key, snapCoordinate(x, MIN_X, MAX_X), snapCoordinate(z, MIN_Z, MAX_Z))
}

function snapCoordinate(value: number, minimum: number, maximum: number) {
  const snapped = Math.round(value / SNAP) * SNAP
  return Math.min(maximum, Math.max(minimum, snapped))
}

function updateUnit(key: string, x: number, z: number, rotation?: number) {
  const unit = layoutUnits.value.find((item) => item.key === key)
  if (!unit) return
  const ids = new Set(unit.bedIds)
  beds.value = beds.value.map((bed) => ids.has(bed.id)
    ? {
        ...bed,
        layout_x: x,
        layout_z: z,
        rotation_degrees: rotation ?? unit.rotation,
      }
    : bed)
}

function cycleRotation(unit: LayoutUnit) {
  const rotation = (unit.rotation + 90) % 360
  updateUnit(unit.key, unit.x, unit.z, rotation)
}

function setUnitCoordinate(key: string, axis: 'x' | 'z', value: number) {
  const unit = layoutUnits.value.find((item) => item.key === key)
  if (!unit || !Number.isFinite(value)) return
  const x = axis === 'x' ? snapCoordinate(value, MIN_X, MAX_X) : unit.x
  const z = axis === 'z' ? snapCoordinate(value, MIN_Z, MAX_Z) : unit.z
  updateUnit(key, x, z)
}

function setUnitRotation(unit: LayoutUnit, value: number) {
  if (![0, 90, 180, 270].includes(value)) return
  updateUnit(unit.key, unit.x, unit.z, value)
}

function setBedType(bedId: number, bedType: string) {
  beds.value = beds.value.map((bed) => {
    if (bed.id !== bedId || bed.occupied) return bed
    return { ...bed, bed_type: bedType }
  })
  error.value = ''
  message.value = '床位类型已更新预览，保存后会同步房型。'
}

function restoreDefaultLayout() {
  beds.value = beds.value.map((bed) => {
    const placement = defaultPlacement(bed)
    return {
      ...bed,
      layout_x: placement.x,
      layout_z: placement.z,
      rotation_degrees: placement.rotation,
    }
  })
  error.value = ''
  message.value = '已恢复默认布局预览，点击保存后生效。'
}

function defaultPlacement(bed: LayoutBed) {
  if (bed.bed_type === 'BUNK_UPPER' || bed.bed_type === 'BUNK_LOWER') {
    return { x: 2.35, z: 1.65, rotation: 0 }
  }
  if (bed.position_index === 1) return { x: -2.35, z: -1.65, rotation: 0 }
  if (bed.position_index === 2) return { x: 2.35, z: -1.65, rotation: 0 }
  if (bed.position_index === 3) return { x: -2.35, z: 1.65, rotation: 0 }
  return { x: 2.35, z: 1.65, rotation: 0 }
}

function unitStyle(unit: LayoutUnit) {
  return {
    left: `${((unit.x - MIN_X) / (MAX_X - MIN_X)) * 100}%`,
    top: `${((unit.z - MIN_Z) / (MAX_Z - MIN_Z)) * 100}%`,
    transform: `translate(-50%, -50%) rotate(${unit.rotation}deg)`,
  }
}

function bedTypeText(value: string) {
  return BED_TYPE_OPTIONS.find((option) => option.value === value)?.label ?? value
}

function roomTypeForBedCount(count: number) {
  return count === 4
    ? 'FOUR_PERSON'
    : count === 5
      ? 'FIVE_PERSON'
      : count === 6
        ? 'SIX_PERSON'
        : 'OTHER'
}

function roomTypeText(value: string) {
  return {
    FOUR_PERSON: '四人间',
    FIVE_PERSON: '五人间',
    SIX_PERSON: '六人间',
    OTHER: '其他房型',
  }[value] ?? value
}

async function save() {
  if (saving.value) return
  if (!reason.value.trim()) {
    error.value = '请填写布局修改原因后再保存。'
    message.value = ''
    reasonInput.value?.focus()
    return
  }

  saving.value = true
  error.value = ''
  message.value = ''
  try {
    await api.put(`/api/v1/admin/rooms/${props.roomId}/bed-layout`, {
      expectedRoomVersion: roomVersion.value,
      reason: reason.value.trim(),
      beds: beds.value.map((bed) => ({
        bedId: bed.id,
        bedType: bed.bed_type,
        layoutX: bed.layout_x,
        layoutZ: bed.layout_z,
        rotationDegrees: bed.rotation_degrees,
      })),
    })
    message.value = '床位类型和布局已保存，房型已同步。'
    reason.value = ''
    await load()
    emit('saved')
  } catch (reasonValue) {
    error.value = reasonValue instanceof Error ? reasonValue.message : '床位布局保存失败'
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="modal-overlay room-layout-overlay" @click.self="emit('close')">
    <section class="modal-card room-layout-dialog" role="dialog" aria-modal="true" aria-labelledby="layout-title">
      <div class="section-head room-layout-head">
        <div>
          <span class="eyebrow">ROOM LAYOUT</span>
          <h3 id="layout-title">{{ roomLabel }}床位布局</h3>
          <p>拖动床位调整位置；空床位可以切换类型，已有学生的床位不可修改类型。</p>
        </div>
        <button class="button ghost" type="button" @click="emit('close')">关闭</button>
      </div>

      <p v-if="error" class="alert error">{{ error }}</p>
      <p v-if="message" class="alert success">{{ message }}</p>
      <p v-if="loading" class="empty-state">正在加载布局…</p>

      <template v-else>
        <div class="layout-room-type-summary">
          <div>
            <span>当前房型</span>
            <strong>{{ roomTypeText(currentRoomType) }}</strong>
          </div>
          <span class="layout-room-type-arrow">→</span>
          <div>
            <span>同步房型</span>
            <strong>{{ roomTypeText(synchronizedRoomType) }}</strong>
          </div>
          <small>保存时按物理床位数量自动同步房型和容量。</small>
        </div>

        <div class="layout-bed-type-grid" aria-label="床位类型设置">
          <article v-for="bed in beds" :key="`type-${bed.id}`" class="layout-bed-type-card" :class="{ occupied: bed.occupied }">
            <div>
              <strong>{{ bed.bed_code }}床</strong>
              <small>{{ bed.occupied ? '非空床位不可修改类型' : bedTypeText(bed.bed_type) }}</small>
            </div>
            <select
              class="input"
              :value="bed.bed_type"
              :disabled="saving || bed.occupied"
              @change="setBedType(bed.id, String(($event.target as HTMLSelectElement).value))"
            >
              <option v-for="option in BED_TYPE_OPTIONS" :key="option.value" :value="option.value">
                {{ option.label }}
              </option>
            </select>
          </article>
        </div>

        <div ref="stage" class="room-layout-stage" aria-label="房间床位俯视布局编辑区">
          <span class="layout-window">窗户</span>
          <span class="layout-door">入口</span>
          <button
            v-for="unit in layoutUnits"
            :key="unit.key"
            class="layout-bed-unit"
            :class="{ bunk: unit.bunk, dragging: dragKey === unit.key }"
            :style="unitStyle(unit)"
            type="button"
            @pointerdown.prevent="startDrag(unit, $event)"
          >
            <strong>{{ unit.label }}</strong>
            <small>{{ unit.bunk ? '上下铺床架' : '上床下桌' }}</small>
          </button>
        </div>

        <div class="layout-number-grid">
          <article v-for="unit in layoutUnits" :key="`form-${unit.key}`" class="layout-number-card">
            <strong>{{ unit.label }}</strong>
            <label>
              <span>横向位置X</span>
              <input
                class="input"
                type="number"
                min="-5.2"
                max="5.2"
                step="0.25"
                :value="unit.x"
                @change="setUnitCoordinate(unit.key, 'x', Number(($event.target as HTMLInputElement).value))"
              />
            </label>
            <label>
              <span>纵向位置Z</span>
              <input
                class="input"
                type="number"
                min="-3.5"
                max="3.5"
                step="0.25"
                :value="unit.z"
                @change="setUnitCoordinate(unit.key, 'z', Number(($event.target as HTMLInputElement).value))"
              />
            </label>
            <label>
              <span>朝向</span>
              <select class="input" :value="unit.rotation" @change="setUnitRotation(unit, Number(($event.target as HTMLSelectElement).value))">
                <option :value="0">0°</option>
                <option :value="90">90°</option>
                <option :value="180">180°</option>
                <option :value="270">270°</option>
              </select>
            </label>
            <button class="button ghost" type="button" @click="cycleRotation(unit)">旋转90°</button>
          </article>
        </div>

        <label class="layout-reason-field">
          <span>修改原因</span>
          <textarea
            ref="reasonInput"
            v-model.trim="reason"
            class="input"
            rows="2"
            maxlength="500"
            required
            placeholder="例如：按该房间实际床具类型和摆放位置调整"
          />
          <small>保存床位类型和布局必须填写原因，用于操作审计。</small>
        </label>

        <div class="button-row room-layout-actions">
          <button class="button ghost" type="button" @click="restoreDefaultLayout">恢复默认布局</button>
          <button class="button ghost" type="button" @click="emit('close')">取消</button>
          <button class="button primary" type="button" :disabled="saving" @click="save">
            {{ saving ? '正在保存…' : '保存类型与布局' }}
          </button>
        </div>
      </template>
    </section>
  </div>
</template>
