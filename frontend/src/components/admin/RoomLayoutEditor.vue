<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { api } from '../../api/client'
import type { DataObject, ObjectSuccessResponse } from '../../api/types'
import { useI18n } from '../../i18n'

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
  representativeBedId: number
  bedIds: number[]
  x: number
  z: number
  rotation: number
  originalType: 'LOFT_BED_DESK' | 'BUNK'
  unitType: 'LOFT_BED_DESK' | 'BUNK'
  occupied: boolean
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
const MAX_CAPACITY = 8

const { t, subtitle, translateError } = useI18n()
const stage = ref<HTMLDivElement | null>(null)
const reasonInput = ref<HTMLTextAreaElement | null>(null)
const beds = ref<LayoutBed[]>([])
const unitTypes = ref<Record<string, 'LOFT_BED_DESK' | 'BUNK'>>({})
const roomVersion = ref(0)
const currentRoomType = ref('OTHER')
const reason = ref('')
const loading = ref(true)
const saving = ref(false)
const error = ref('')
const message = ref('')
const dragKey = ref<string | null>(null)

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
    const sorted = [...items].sort((left, right) => left.position_index - right.position_index)
    const representative = sorted.find((item) => item.bed_type === 'BUNK_UPPER') ?? sorted[0]
    const originalType = sorted.some((item) => item.bed_type.startsWith('BUNK_'))
      ? 'BUNK'
      : 'LOFT_BED_DESK'
    return {
      key,
      label: sorted.map((item) => item.bed_code).join(' / '),
      representativeBedId: representative.id,
      bedIds: sorted.map((item) => item.id),
      x: representative.layout_x,
      z: representative.layout_z,
      rotation: representative.rotation_degrees,
      originalType,
      unitType: unitTypes.value[key] ?? originalType,
      occupied: sorted.some((item) => item.occupied),
    }
  })
})

const projectedCapacity = computed(() => {
  const additions = layoutUnits.value.filter((unit) =>
    unit.originalType === 'LOFT_BED_DESK' && unit.unitType === 'BUNK').length
  return beds.value.length + additions
})
const synchronizedRoomType = computed(() => roomTypeForBedCount(projectedCapacity.value))
const capacityLimitReached = computed(() => projectedCapacity.value >= MAX_CAPACITY)

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
    unitTypes.value = {}
  } catch (reasonValue) {
    error.value = translateError(reasonValue)
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
  const target = event.target as HTMLElement
  if (target.closest('button')) return
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
  updateUnit(unit.key, unit.x, unit.z, (unit.rotation + 90) % 360)
  message.value = `${unit.label} 已顺时针旋转90°，保存后生效。`
}

function setUnitType(unit: LayoutUnit, value: 'LOFT_BED_DESK' | 'BUNK') {
  if (unit.occupied && value !== unit.originalType) {
    error.value = '非空床位不可修改类型。'
    return
  }
  if (unit.originalType === 'BUNK' && value === 'LOFT_BED_DESK') {
    error.value = '已有上下铺不能直接合并为上床下桌。'
    return
  }
  const currentAdditional = layoutUnits.value.filter((item) =>
    item.key !== unit.key
    && item.originalType === 'LOFT_BED_DESK'
    && item.unitType === 'BUNK').length
  if (unit.originalType === 'LOFT_BED_DESK'
    && value === 'BUNK'
    && beds.value.length + currentAdditional >= MAX_CAPACITY) {
    error.value = '房间最多只能配置8个床位。'
    return
  }
  unitTypes.value = { ...unitTypes.value, [unit.key]: value }
  error.value = ''
  message.value = value === 'BUNK' && unit.originalType === 'LOFT_BED_DESK'
    ? '保存后将新增一个独立下铺床位，并同步增加1人居住容量。'
    : '床具类型已更新预览，点击保存后统一校验并生效。'
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
  if (projectedCapacity.value > MAX_CAPACITY) {
    error.value = '房间最多只能配置8个床位。'
    return
  }

  saving.value = true
  error.value = ''
  message.value = ''
  try {
    await api.put(`/api/v1/admin/rooms/${props.roomId}/bed-layout`, {
      expectedRoomVersion: roomVersion.value,
      reason: reason.value.trim(),
      beds: layoutUnits.value.map((unit) => ({
        bedId: unit.representativeBedId,
        bedType: unit.unitType,
        layoutX: unit.x,
        layoutZ: unit.z,
        rotationDegrees: unit.rotation,
      })),
    })
    message.value = '床具类型和布局已保存，房型与容量已同步。'
    reason.value = ''
    await load()
    emit('saved')
  } catch (reasonValue) {
    error.value = translateError(reasonValue)
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
          <span class="eyebrow">{{ subtitle('床具布局', 'ROOM LAYOUT') }}</span>
          <h3 id="layout-title">{{ roomLabel }}床位布局</h3>
          <p>拖动床具调整位置，在床具卡片内切换类型或顺时针旋转。所有更改在点击保存后统一校验。</p>
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
          <div>
            <span>同步容量</span>
            <strong>{{ projectedCapacity }} / 8 人</strong>
          </div>
          <small>{{ t('layout.maximum') }}。保存时按独立可选床位数量自动同步房型和容量。</small>
        </div>

        <p class="layout-bunk-explanation">
          空的上床下桌可切换为上下铺并新增一个独立床位；已有学生的床具不可改型。
          {{ capacityLimitReached ? '当前预览已达到最多8人。' : '' }}
        </p>

        <div ref="stage" class="room-layout-stage" aria-label="房间床具俯视布局编辑区">
          <span class="layout-window">窗户</span>
          <span class="layout-door">入口</span>
          <article
            v-for="unit in layoutUnits"
            :key="unit.key"
            class="layout-bed-unit"
            :class="{
              bunk: unit.unitType === 'BUNK',
              dragging: dragKey === unit.key,
              occupied: unit.occupied,
            }"
            :style="unitStyle(unit)"
            @pointerdown.prevent="startDrag(unit, $event)"
          >
            <div class="layout-bed-drag-handle">
              <strong>{{ unit.label }}</strong>
              <small>{{ unit.occupied ? '非空床位·类型锁定' : '拖动调整位置' }}</small>
            </div>
            <div class="layout-bed-type-actions" role="group" :aria-label="`${unit.label}床具类型`">
              <button
                type="button"
                :class="{ active: unit.unitType === 'LOFT_BED_DESK' }"
                :disabled="saving || unit.occupied || unit.originalType === 'BUNK'"
                @pointerdown.stop
                @click.stop="setUnitType(unit, 'LOFT_BED_DESK')"
              >上床下桌</button>
              <button
                type="button"
                :class="{ active: unit.unitType === 'BUNK' }"
                :disabled="saving || unit.occupied"
                @pointerdown.stop
                @click.stop="setUnitType(unit, 'BUNK')"
              >上下铺</button>
            </div>
            <button
              class="layout-bed-rotate-button"
              type="button"
              :disabled="saving"
              @pointerdown.stop
              @click.stop="cycleRotation(unit)"
            >顺时针旋转90°</button>
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
          <small>保存床具类型和布局必须填写原因，用于操作审计。</small>
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
