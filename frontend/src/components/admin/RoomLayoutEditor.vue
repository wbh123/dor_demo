<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { api } from '../../api/client'
import type { DataObject, ObjectSuccessResponse } from '../../api/types'
import { useFeatureAccess } from '../../composables/useFeatureAccess'
import { useI18n } from '../../i18n'

type UnitType = 'LOFT_BED_DESK' | 'BUNK' | 'SINGLE_BED'
interface Bed { id:number; bed_code:string; bed_type:string; position_index:number; bed_frame_id:number|null; occupied:boolean; layout_x:number; layout_z:number; rotation_degrees:number }
interface Unit { key:string; label:string; representativeBedId:number; bedIds:number[]; originalType:UnitType; unitType:UnitType; occupied:boolean; x:number; z:number; rotation:number }

const props = defineProps<{ roomId:number; roomLabel:string }>()
const emit = defineEmits<{ close:[]; saved:[] }>()
const { subtitle, translateError } = useI18n()
const { hasFeature } = useFeatureAccess()
const stage = ref<HTMLDivElement|null>(null)
const beds = ref<Bed[]>([])
const selectedTypes = ref<Record<string,UnitType>>({})
const roomVersion = ref(0)
const reason = ref('')
const loading = ref(true)
const saving = ref(false)
const error = ref('')
const message = ref('')
const dragKey = ref('')
const MAX_CAPACITY = 8
const MIN_X=-5.2, MAX_X=5.2, MIN_Z=-3.5, MAX_Z=3.5
const canEdit = computed(() => hasFeature('P2_ROOM_LAYOUT_UPDATE'))

const units = computed<Unit[]>(() => {
  const groups = new Map<string,Bed[]>()
  for (const bed of beds.value) {
    const bunk = bed.bed_type === 'BUNK_UPPER' || bed.bed_type === 'BUNK_LOWER'
    const key = bunk && bed.bed_frame_id ? `frame-${bed.bed_frame_id}` : `bed-${bed.id}`
    groups.set(key,[...(groups.get(key)??[]),bed])
  }
  return [...groups.entries()].map(([key,items]) => {
    const sorted=[...items].sort((a,b)=>a.position_index-b.position_index)
    const representative=sorted.find(item=>item.bed_type==='BUNK_UPPER')??sorted[0]
    const originalType:UnitType = sorted.some(item=>item.bed_type.startsWith('BUNK_'))
      ? 'BUNK' : sorted.some(item=>item.bed_type==='SINGLE_BED') ? 'SINGLE_BED' : 'LOFT_BED_DESK'
    return { key,label:sorted.map(item=>item.bed_code).join(' / '),representativeBedId:representative.id,
      bedIds:sorted.map(item=>item.id),originalType,unitType:selectedTypes.value[key]??originalType,
      occupied:sorted.some(item=>item.occupied),x:representative.layout_x,z:representative.layout_z,rotation:representative.rotation_degrees }
  })
})
const projectedCapacity = computed(() => beds.value.length + units.value.filter(unit => unit.originalType!=='BUNK' && unit.unitType==='BUNK').length)

onMounted(load)
onBeforeUnmount(stopDrag)
async function load(){loading.value=true;error.value='';try{const response=await api.get<ObjectSuccessResponse>(`/api/v1/admin/rooms/${props.roomId}/bed-layout`);const data=(response.data.data??{}) as DataObject;const room=(data.room??{}) as DataObject;roomVersion.value=Number(room.room_version??0);beds.value=((data.beds??[]) as DataObject[]).map(item=>({id:Number(item.id),bed_code:String(item.bed_code),bed_type:String(item.bed_type),position_index:Number(item.position_index),bed_frame_id:item.bed_frame_id==null?null:Number(item.bed_frame_id),occupied:Number(item.occupied??0)===1,layout_x:Number(item.layout_x),layout_z:Number(item.layout_z),rotation_degrees:Number(item.rotation_degrees)}));selectedTypes.value={}}catch(cause){error.value=translateError(cause)}finally{loading.value=false}}
function setType(unit:Unit,type:UnitType){if(!canEdit.value)return;if(unit.occupied&&type!==unit.originalType){error.value='非空床位不可修改类型。';return}if(unit.originalType==='BUNK'&&type!=='BUNK'){error.value='上下铺不能直接合并为其他床型。';return}const currentAdds=units.value.filter(item=>item.key!==unit.key&&item.originalType!=='BUNK'&&item.unitType==='BUNK').length;if(unit.originalType!=='BUNK'&&type==='BUNK'&&beds.value.length+currentAdds>=MAX_CAPACITY){error.value='房间最多只能配置8个床位。';return}selectedTypes.value={...selectedTypes.value,[unit.key]:type};error.value=''}
function rotate(unit:Unit){if(canEdit.value)updateUnit(unit.key,unit.x,unit.z,(unit.rotation+90)%360)}
function isQuarterTurn(rotation:number){return Math.abs(rotation%180)===90}
function updateUnit(key:string,x:number,z:number,rotation?:number){const target=units.value.find(item=>item.key===key);if(!target)return;const ids=new Set(target.bedIds);beds.value=beds.value.map(bed=>ids.has(bed.id)?{...bed,layout_x:x,layout_z:z,rotation_degrees:rotation??target.rotation}:bed)}
function startDrag(unit:Unit,event:PointerEvent){if(!canEdit.value||(event.target as HTMLElement).closest('button,select,input'))return;dragKey.value=unit.key;move(event);window.addEventListener('pointermove',move);window.addEventListener('pointerup',stopDrag,{once:true})}
function move(event:PointerEvent){if(!stage.value||!dragKey.value)return;const rect=stage.value.getBoundingClientRect();const x=MIN_X+Math.min(1,Math.max(0,(event.clientX-rect.left)/rect.width))*(MAX_X-MIN_X);const z=MIN_Z+Math.min(1,Math.max(0,(event.clientY-rect.top)/rect.height))*(MAX_Z-MIN_Z);const unit=units.value.find(item=>item.key===dragKey.value);if(unit)updateUnit(unit.key,Math.round(x*4)/4,Math.round(z*4)/4)}
function stopDrag(){dragKey.value='';window.removeEventListener('pointermove',move);window.removeEventListener('pointerup',stopDrag)}
function unitStyle(unit:Unit){return{left:`${((unit.x-MIN_X)/(MAX_X-MIN_X))*100}%`,top:`${((unit.z-MIN_Z)/(MAX_Z-MIN_Z))*100}%`,transform:'translate(-50%,-50%)'}}
function typeLabel(type:UnitType){return{LOFT_BED_DESK:'上床下桌',BUNK:'上下铺',SINGLE_BED:'单人床'}[type]}
function typeActionDisabled(unit:Unit,type:UnitType){if(!canEdit.value)return true;if(unit.occupied&&type!==unit.originalType)return true;return unit.originalType==='BUNK'&&type!=='BUNK'}
async function save(){if(!canEdit.value)return;if(!reason.value.trim()){error.value='请填写布局修改原因。';return}if(projectedCapacity.value>MAX_CAPACITY){error.value='房间最多只能配置8个床位。';return}saving.value=true;try{await api.put(`/api/v1/admin/rooms/${props.roomId}/bed-layout`,{expectedRoomVersion:roomVersion.value,reason:reason.value.trim(),beds:units.value.map(unit=>({bedId:unit.representativeBedId,bedType:unit.unitType,layoutX:unit.x,layoutZ:unit.z,rotationDegrees:unit.rotation}))});message.value='床位类型与布局已保存。';reason.value='';await load();emit('saved')}catch(cause){error.value=translateError(cause)}finally{saving.value=false}}
</script>

<template>
  <div class="modal-overlay room-layout-overlay" @click.self="emit('close')"><section class="modal-card room-layout-dialog">
    <header class="section-head split-title"><div><span class="eyebrow">{{ subtitle('床位布局','ROOM LAYOUT') }}</span><h3>{{ roomLabel }}床位布局</h3><p>以左侧房门和右侧窗户为相对位置参照；有可视化编辑权限时可拖动、旋转并调整床型。</p></div><button class="button ghost" @click="emit('close')">关闭</button></header>
    <p v-if="error" class="alert error">{{ error }}</p><p v-if="message" class="alert success">{{ message }}</p><p v-if="loading" class="empty-state">正在加载布局…</p>
    <template v-else>
      <p v-if="!canEdit" class="alert info">当前账号仅可查看床位相对位置；可视化编辑按钮已按权限隐藏。</p>
      <div class="capacity-summary"><strong>预计容量 {{ projectedCapacity }} / 8 人</strong><span>单人床和上床下桌各占1个床位，上下铺占2个床位。</span></div>
      <div ref="stage" class="layout-stage" :class="{readonly:!canEdit}">
        <span class="layout-boundary-label door">房门侧</span><span class="layout-boundary-label window">窗户侧</span>
        <article v-for="unit in units" :key="unit.key" class="layout-unit layout-bed-unit" :class="[unit.unitType.toLowerCase(),{occupied:unit.occupied,vertical:isQuarterTurn(unit.rotation)}]" :style="unitStyle(unit)" @pointerdown="startDrag(unit,$event)">
          <div class="layout-bed-surface" :class="{vertical:isQuarterTurn(unit.rotation)}" :style="{transform:`rotate(${unit.rotation}deg)`}" />
          <div class="layout-bed-content"><strong>{{ unit.label }}</strong><span>{{ typeLabel(unit.unitType) }}</span><div v-if="canEdit" class="layout-bed-type-actions"><button type="button" @pointerdown.stop @click.stop="rotate(unit)">旋转90°</button><button type="button" :class="{active:unit.unitType==='BUNK'}" :disabled="typeActionDisabled(unit,'BUNK')" @pointerdown.stop @click.stop="setType(unit,'BUNK')">上下铺</button><button type="button" :class="{active:unit.unitType==='LOFT_BED_DESK'}" :disabled="typeActionDisabled(unit,'LOFT_BED_DESK')" @pointerdown.stop @click.stop="setType(unit,'LOFT_BED_DESK')">上床下桌</button><button type="button" :class="{active:unit.unitType==='SINGLE_BED'}" :disabled="typeActionDisabled(unit,'SINGLE_BED')" @pointerdown.stop @click.stop="setType(unit,'SINGLE_BED')">单人床</button></div></div>
        </article>
      </div>
      <template v-if="canEdit"><label class="form-stack"><span>修改原因</span><textarea v-model="reason" class="input" rows="3" maxlength="500" placeholder="说明调整床位类型或布局的原因"/></label><div class="button-row dialog-actions"><button class="button ghost" @click="emit('close')">取消</button><button class="button primary" :disabled="saving" @click="save">{{ saving?'保存中…':'保存类型与布局' }}</button></div></template>
    </template>
  </section></div>
</template>

<style scoped>
.room-layout-overlay{z-index:1320;padding:18px}.room-layout-dialog{width:min(1120px,100%);max-height:calc(100vh - 36px);overflow:auto;padding:24px;border-radius:24px}.capacity-summary{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:12px 14px;border-radius:14px;background:var(--soft)}.capacity-summary span{color:var(--muted)}.layout-stage{position:relative;height:460px;margin:16px 0;border:1px solid var(--line);border-radius:20px;background:linear-gradient(90deg,rgba(80,120,180,.06) 1px,transparent 1px),linear-gradient(rgba(80,120,180,.06) 1px,transparent 1px);background-size:40px 40px;overflow:hidden}.layout-stage.readonly .layout-unit{cursor:default}.layout-boundary-label{position:absolute;z-index:8;top:50%;padding:13px 6px;border-radius:999px;writing-mode:vertical-rl;transform:translateY(-50%);font-size:12px;font-weight:800;box-shadow:0 5px 14px rgba(15,23,42,.12)}.layout-boundary-label.door{left:8px;color:#72431d;background:#f8dfc4}.layout-boundary-label.window{right:8px;color:#245982;background:#d9efff}.layout-unit{position:absolute;width:190px;aspect-ratio:19/9;min-height:0;cursor:grab;user-select:none;touch-action:none}.layout-bed-surface{position:absolute;inset:0;border:2px solid #5684c9;border-radius:18px;background:#eef5ff;transform-origin:center}.layout-unit.bunk .layout-bed-surface{border-color:#7a5fb5;background:#f4efff}.layout-unit.single_bed .layout-bed-surface{border-color:#3f8b69;background:#eefaf5}.layout-unit.occupied{opacity:.78}.layout-bed-content{position:relative;z-index:2;display:grid;grid-template-rows:auto auto auto;align-content:center;gap:3px;height:100%;padding:7px 9px;text-align:center}.layout-bed-content>strong{font-size:13px;line-height:1.1}.layout-bed-content>span{font-size:11px;color:var(--muted)}.layout-bed-type-actions{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:3px;width:100%}.layout-bed-content button{min-width:0;border:1px solid rgba(86,132,201,.28);border-radius:7px;padding:2px 3px;background:rgba(255,255,255,.94);cursor:pointer;font-size:9.5px;line-height:1.25;white-space:nowrap}.layout-bed-content button.active{border-color:#5684c9;background:#dfeeff;color:#24599c;font-weight:800}.layout-bed-content button:disabled{cursor:not-allowed;opacity:.48}.dialog-actions{justify-content:flex-end;margin-top:16px}@media(max-width:720px){.room-layout-overlay{padding:6px}.room-layout-dialog{max-height:calc(100vh - 12px);padding:16px;border-radius:20px}.capacity-summary{align-items:flex-start;flex-direction:column}.layout-stage{height:380px}.layout-unit{width:152px}.layout-bed-content{padding:6px}.layout-bed-content button{font-size:8.5px}.layout-boundary-label{font-size:10px}}
</style>
