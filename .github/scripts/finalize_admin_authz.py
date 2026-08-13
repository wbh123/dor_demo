#!/usr/bin/env python3
from pathlib import Path

ROOT = Path.cwd()
VIEW = ROOT / 'frontend/src/views/admin/AdminDormitoryView.vue'
CARD = ROOT / 'frontend/src/components/admin/BuildingResourceCard.vue'
TEST = ROOT / 'scripts/frontend/test_building_lifecycle_campus_selectors.py'


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected exactly one match, got {count}')
    return text.replace(old, new, 1)


def patch_view() -> None:
    text = VIEW.read_text(encoding='utf-8')
    if '状态与危险操作' in text:
        return
    text = replace_once(text,
        'interface BuildingEditForm extends BuildingCreateForm { enabled: boolean }',
        'type BuildingEditForm = BuildingCreateForm', 'edit type')
    text = replace_once(text,
        "floorCount: 1, enabled: true, reason: '' })",
        "floorCount: 1, reason: '' })", 'edit initial state')
    text = replace_once(text,
        "floorCount: Number(building.floor_count ?? 1), enabled: Boolean(building.enabled), reason: '',",
        "floorCount: Number(building.floor_count ?? 1), reason: '',", 'editor open state')
    text = replace_once(text,
        '<BuildingResourceCard v-for="building in pagedBuildings" :key="String(building.id)" :building="building" @edit="openBuildingEditor" @toggle-enabled="requestBuildingEnabled" @delete="requestBuildingDelete" />',
        '<BuildingResourceCard v-for="building in pagedBuildings" :key="String(building.id)" :building="building" @edit="openBuildingEditor" />',
        'card lifecycle wiring')
    text = replace_once(text,
        "    await api.put(`/api/v1/admin/buildings/${Number(selectedBuilding.value.id)}`, { ...buildingEditForm, reason: buildingEditForm.reason.trim() })",
        "    const { reason, ...buildingFields } = buildingEditForm\n    await api.put(`/api/v1/admin/buildings/${Number(selectedBuilding.value.id)}`, {\n      ...buildingFields,\n      enabled: Boolean(selectedBuilding.value.enabled),\n      reason: reason.trim(),\n    })",
        'ordinary save lifecycle preservation')

    old_lifecycle = '''async function performBuildingLifecycle(payload: ConfirmDialogPayload) {
  const building = buildingLifecycleTarget.value
  const action = buildingLifecycleAction.value
  if (!building || !action) return
  const id = Number(building.id)
  if (action === 'delete') {
    await api.delete(`/api/v1/admin/buildings/${id}`, { data: { reason: payload.reason } })
    message.value = `${String(building.building_name)}已删除。`
  } else {
    const enabled = action === 'enable'
    await api.patch(`/api/v1/admin/buildings/${id}/enabled`, { enabled, reason: payload.reason })
    message.value = `${String(building.building_name)}已${enabled ? '启用' : '停用'}。`
  }
  closeBuildingLifecycle()
  await Promise.all([loadBuildings(), loadRooms()])
}'''
    new_lifecycle = '''async function performBuildingLifecycle(payload: ConfirmDialogPayload) {
  const building = buildingLifecycleTarget.value
  const action = buildingLifecycleAction.value
  if (!building || !action) return
  const id = Number(building.id)
  if (action === 'delete') {
    await api.delete(`/api/v1/admin/buildings/${id}`, { data: { reason: payload.reason } })
    message.value = `${String(building.building_name)}已删除。`
  } else {
    const enabled = action === 'enable'
    await api.patch(`/api/v1/admin/buildings/${id}/enabled`, { enabled, reason: payload.reason })
    message.value = `${String(building.building_name)}已${enabled ? '启用' : '停用'}。`
  }
  closeBuildingLifecycle()
  await Promise.all([loadBuildings(), loadRooms()])
  if (action === 'delete') {
    if (Number(selectedBuilding.value?.id) === id) closeBuildingEditor()
    return
  }
  if (Number(selectedBuilding.value?.id) === id) {
    const refreshed = buildings.value.find(item => Number(item.id) === id)
    if (refreshed) selectedBuilding.value = refreshed
  }
}'''
    text = replace_once(text, old_lifecycle, new_lifecycle, 'lifecycle refresh')
    text = replace_once(text,
        "{{ buildingEditForm.enabled ? '正常开放' : '已停用' }} · 请使用楼栋卡片上的启用/停用按钮切换状态",
        "{{ selectedBuilding.enabled ? '正常开放' : '已停用' }}", 'status display')

    lifecycle_section = '''      <section v-if="selectedBuilding" class="building-lifecycle-section">
        <div class="building-lifecycle-head">
          <div><strong>状态与危险操作</strong><p>楼栋启用/停用与删除使用独立确认，不会随“保存楼栋信息”一起提交。</p></div>
          <span class="status-chip compact">{{ selectedBuilding.enabled ? '正常开放' : '已停用' }}</span>
        </div>
        <div class="building-lifecycle-actions">
          <button class="button secondary" type="button" :disabled="saving" @click="requestBuildingEnabled(selectedBuilding)">{{ selectedBuilding.enabled ? '停用楼栋' : '启用楼栋' }}</button>
          <button class="button danger" type="button" :disabled="saving || Number(selectedBuilding.room_count ?? 0) > 0" @click="requestBuildingDelete(selectedBuilding)">删除楼栋</button>
        </div>
        <p v-if="Number(selectedBuilding.room_count ?? 0) > 0" class="field-hint">删除仅允许完全没有寝室的空楼栋；当前楼栋仍有 {{ Number(selectedBuilding.room_count ?? 0) }} 间寝室。</p>
        <p v-else class="field-hint">空楼栋仍被选寝批次引用时，后端会拒绝删除并给出明确提示。</p>
      </section>
'''
    text = replace_once(text,
        '      </form>\n      <p v-if="buildingGenderNarrowing" class="modal-form-notice" role="note">',
        '      </form>\n' + lifecycle_section + '      <p v-if="buildingGenderNarrowing" class="modal-form-notice" role="note">',
        'modal lifecycle section')
    text = replace_once(text, '.modal-form-notice{margin:14px 0 0;',
        '.building-lifecycle-section{display:grid;gap:10px;margin-top:14px;padding:14px;border:1px solid var(--line);border-radius:14px;background:var(--surface-soft)}.building-lifecycle-head{display:flex;align-items:flex-start;justify-content:space-between;gap:14px}.building-lifecycle-head p{margin:3px 0 0;color:var(--muted);font-size:12px;line-height:1.5}.building-lifecycle-actions{display:flex;flex-wrap:wrap;gap:8px}.modal-form-notice{margin:14px 0 0;',
        'lifecycle styles')
    VIEW.write_text(text, encoding='utf-8')


def write_card() -> None:
    CARD.write_text('''<script setup lang="ts">
import type { DataObject } from '../../api/types'

const props = defineProps<{ building: DataObject }>()
const emit = defineEmits<{ edit: [building: DataObject] }>()

function genderText(value: unknown) { return ({ M: '男生楼', F: '女生楼', MIXED: '男女混合楼' } as Record<string, string>)[String(value)] ?? '男女混合楼' }
function educationScopeText(value: unknown) { return ({ UNDERGRADUATE_ONLY: '本科生', GRADUATE_ONLY: '研究生', MIXED: '培养层次混合' } as Record<string, string>)[String(value)] ?? '培养层次混合' }
function residentScopeText(value: unknown) { return ({ DOMESTIC_ONLY: '国内生', INTERNATIONAL_ONLY: '国际生', MIXED: '国内/国际混合' } as Record<string, string>)[String(value)] ?? '国内/国际混合' }
</script>

<template>
  <article class="dorm-building-card" role="button" tabindex="0" :aria-label="`编辑${String(building.building_name ?? '楼栋')}`" @click="emit('edit', props.building)" @keydown.enter="emit('edit', props.building)" @keydown.space.prevent="emit('edit', props.building)">
    <header class="dorm-building-card__header">
      <div class="dorm-building-card__title-row"><strong>{{ building.building_name }}</strong><span class="dorm-building-card__status" :class="{ 'is-disabled': !building.enabled }">{{ building.enabled ? '正常开放' : '已停用' }}</span></div>
      <small>{{ building.building_code }} · {{ building.campus_name }}</small>
      <div class="dorm-building-card__tags"><span>{{ genderText(building.gender_restriction) }}</span><span>{{ educationScopeText(building.education_level_scope) }}</span><span>{{ residentScopeText(building.resident_scope) }}</span></div>
    </header>
    <div class="dorm-building-card__metrics"><div><strong>{{ building.floor_count }}</strong><span>楼层</span></div><div><strong>{{ building.room_count }}</strong><span>寝室</span></div><div><strong>{{ building.bed_count }}</strong><span>床位</span></div><div><strong>{{ building.resident_count ?? 0 }}</strong><span>在住</span></div></div>
  </article>
</template>

<style scoped>
.dorm-building-card{display:flex;width:100%;min-width:0;max-width:100%;box-sizing:border-box;flex-direction:column;align-self:flex-start;gap:5px;padding:9px 10px;border:1px solid var(--line);border-radius:11px;background:var(--soft);cursor:pointer;transition:border-color .16s ease,box-shadow .16s ease}.dorm-building-card:hover{border-color:color-mix(in srgb,var(--primary) 30%,var(--line))}.dorm-building-card:focus-visible{outline:2px solid color-mix(in srgb,var(--primary) 45%,transparent);outline-offset:2px}.dorm-building-card__header{display:flex;min-width:0;flex-direction:column;gap:5px}.dorm-building-card__title-row{display:flex;align-items:center;gap:7px;min-width:0}.dorm-building-card__title-row strong{min-width:0;overflow:hidden;font-size:19px;line-height:1.18;text-overflow:ellipsis;white-space:nowrap}.dorm-building-card__header>small{overflow:hidden;color:var(--muted);font-size:13px;line-height:1.25;text-overflow:ellipsis;white-space:nowrap}.dorm-building-card__tags{display:flex;flex-wrap:wrap;gap:4px}.dorm-building-card__tags span{display:inline-flex;align-items:center;padding:2px 7px;border-radius:999px;background:#e8f8f2;color:#1e654f;font-size:12px;font-weight:700;line-height:1.2;white-space:nowrap}.dorm-building-card__status{display:inline-flex;flex:none;align-items:center;gap:4px;padding:2px 7px;border-radius:999px;background:#ecfdf3;color:#147d46;font-size:11px;font-weight:800;line-height:1.2;white-space:nowrap}.dorm-building-card__status::before{width:5px;height:5px;border-radius:50%;background:currentColor;content:''}.dorm-building-card__status.is-disabled{background:#f1f5f9;color:#64748b}.dorm-building-card__metrics{display:flex;flex-wrap:wrap;align-items:center;gap:4px 10px;padding-top:4px}.dorm-building-card__metrics>div{display:flex;flex:0 0 auto;align-items:baseline;gap:4px;min-width:0;padding:2px 0}.dorm-building-card__metrics strong{font-size:18px;line-height:1.1}.dorm-building-card__metrics span{color:var(--muted);font-size:13px;line-height:1.1}
</style>
''', encoding='utf-8')


def write_contract() -> None:
    TEST.write_text('''#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")

def require(path: str, *tokens: str) -> None:
    text = read(path)
    for token in tokens:
        assert token in text, f"{path} 缺少契约: {token}"

def forbid(path: str, *tokens: str) -> None:
    text = read(path)
    for token in tokens:
        assert token not in text, f"{path} 不应出现: {token}"

def main() -> None:
    require("backend-java/server/src/main/java/com/wust/dormitory/admin/BuildingManagementController.java", '@PatchMapping("/{buildingId}/enabled")', '@DeleteMapping("/{buildingId}")')
    require("backend-java/server/src/main/java/com/wust/dormitory/admin/BuildingManagementService.java", "BUILDING_DELETE_ROOM_CONFLICT", "BUILDING_DELETE_BATCH_CONFLICT", "BUILDING_DISABLE", "BUILDING_ENABLE", "BUILDING_DELETE")
    require("backend-java/server/src/main/resources/mapper/admin/BuildingManagementMapper.xml", 'id="countBatchBuildingReferences"', 'id="deleteEmptyFloors"', 'id="deleteBuilding"')
    require("frontend/src/components/admin/BuildingResourceCard.vue", "emit('edit', props.building)", "已停用")
    forbid("frontend/src/components/admin/BuildingResourceCard.vue", "toggle-enabled", "emit('delete'", "dorm-building-card__actions")
    require("frontend/src/views/admin/AdminDormitoryView.vue", "AppConfirmDialog", "roomCampusId", "roomCreateCampusId", "requestBuildingDelete", "requestBuildingEnabled", "/enabled", "状态与危险操作", "enabled: Boolean(selectedBuilding.value.enabled)")
    forbid("frontend/src/views/admin/AdminDormitoryView.vue", "window.confirm(", "window.alert(", "@toggle-enabled=", "@delete=", "楼栋卡片上的启用/停用按钮")
    require("frontend/src/components/admin/AdminRoomFilterBar.vue", "campuses:", "update:campus-id", "全部校区", "visibleBuildings")
    require("frontend/src/components/admin/DormitoryBedSelector.vue", "campusId", "campus_name", "校区", "buildingId", "roomId")
    require("backend-java/server/src/main/resources/mapper/admin/AdminResidencyAdjustmentMapper.xml", "campus.id AS campus_id", "campus.campus_name")
    require("backend-java/server/src/main/java/com/wust/dormitory/admin/BatchScopeService.java", "c.id AS campus_id", "c.campus_name")
    require("frontend/src/features/admin-batch/components/BatchScopeDialog.vue", "roomCampusFilter", "scopeCampusOptions", "全部校区", "selectAllVisibleRooms")
    require("frontend/src/views/admin/AdminBatchView.template.html", ':scope-rooms="scopeRooms"')
    require("backend-java/server/src/main/resources/mapper/bedconfirmation/BedConfirmationMapper.xml", "campus.id AS campus_id", "campus.campus_name")
    require("frontend/src/views/admin/AdminBedConfirmationView.vue", "campusFilter", "campusOptions", "全部校区", "buildingFilter")
    require("frontend/src/components/admin/RecipientSelector.vue", "campusFilter", "campusOptions", "全部校区", "buildingRows")
    require("frontend/src/components/admin/AnalyticsFilterBar.vue", "loadLocations", "visibleBuildings", "全部校区", "全部楼栋")
    print("building lifecycle + campus selector source contracts: OK")

if __name__ == "__main__":
    main()
''', encoding='utf-8')


def main() -> None:
    patch_view()
    write_card()
    write_contract()
    print('admin authz takeover finalization patch applied')

if __name__ == '__main__':
    main()
