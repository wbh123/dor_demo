#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def p(name: str) -> Path:
    return ROOT / name


def read(name: str) -> str:
    return p(name).read_text(encoding="utf-8")


def write(name: str, content: str) -> None:
    p(name).write_text(content, encoding="utf-8")


def replace_once(name: str, old: str, new: str) -> None:
    content = read(name)
    if old not in content:
        raise RuntimeError(f"source fragment not found in {name}: {old[:160]!r}")
    write(name, content.replace(old, new, 1))


# ---------------------------------------------------------------------------
# Platform feature authorization: batch selection and title-line alignment.
# ---------------------------------------------------------------------------
name = "frontend/src/views/platform/PlatformFeaturesView.vue"
replace_once(
    name,
    "const confirmReason = ref('')",
    "const confirmReason = ref('')\nconst batchSelection = ref<string[]>([])\nconst batchTarget = ref<FeatureTargetState | null>(null)\nconst batchReason = ref('')\nconst batchSaving = ref(false)",
)
replace_once(
    name,
    "const enabledCount = computed(() => features.value.filter((feature) => feature.effectiveEnabled).length)",
    "const enabledCount = computed(() => features.value.filter((feature) => feature.effectiveEnabled).length)\nconst batchSelectableFeatures = computed(() => filteredFeatures.value.filter((feature) => feature.enabledInProgram))",
)
replace_once(
    name,
    "  try { features.value = await platformApi.featureEntitlements(true) }",
    "  try {\n    features.value = await platformApi.featureEntitlements(true)\n    batchSelection.value = batchSelection.value.filter((code) => features.value.some((feature) => feature.featureCode === code && feature.enabledInProgram))\n  }",
)
replace_once(
    name,
    "async function restoreDefault(feature: FeatureEntitlement) {\n  confirmFeature.value = feature\n  confirmTarget.value = 'INHERIT'\n  confirmReason.value = ''\n}\n",
    "async function restoreDefault(feature: FeatureEntitlement) {\n  confirmFeature.value = feature\n  confirmTarget.value = 'INHERIT'\n  confirmReason.value = ''\n}\nfunction toggleBatchSelection(featureCode: string) {\n  batchSelection.value = batchSelection.value.includes(featureCode)\n    ? batchSelection.value.filter((code) => code !== featureCode)\n    : [...batchSelection.value, featureCode]\n}\nfunction selectCurrentResults() {\n  batchSelection.value = [...new Set([...batchSelection.value, ...batchSelectableFeatures.value.map((feature) => feature.featureCode)])]\n}\nfunction openBatchChange(target: FeatureTargetState) {\n  if (!batchSelection.value.length) return\n  batchTarget.value = target\n  batchReason.value = ''\n  error.value = ''; success.value = ''\n}\nasync function saveBatchChange() {\n  if (!batchTarget.value || !batchSelection.value.length || !batchReason.value.trim()) return\n  batchSaving.value = true\n  try {\n    const updated = await platformApi.setFeatureStates(\n      batchSelection.value.map((featureCode) => ({ featureCode, targetState: batchTarget.value as FeatureTargetState })),\n      batchReason.value.trim(),\n    )\n    const replacements = new Map(updated.map((feature) => [feature.featureCode, feature]))\n    features.value = features.value.map((feature) => replacements.get(feature.featureCode) ?? feature)\n    success.value = `已批量${batchTarget.value === 'ENABLED' ? '开启' : batchTarget.value === 'DISABLED' ? '关闭' : '恢复'}${updated.length}项功能。`\n    batchSelection.value = []\n    batchTarget.value = null\n    batchReason.value = ''\n  } catch (cause) { error.value = cause instanceof Error ? cause.message : '批量功能授权失败' }\n  finally { batchSaving.value = false }\n}\n",
)
replace_once(
    name,
    "    </section>\n\n    <div class=\"permission-groups\">",
    "    </section>\n\n    <section class=\"panel batch-controls\">\n      <div><strong>批量功能授权</strong><span>已选择 {{ batchSelection.length }} 项；仅对当前筛选结果中的已实现功能生效。</span></div>\n      <div class=\"batch-control-actions\"><button class=\"secondary\" type=\"button\" @click=\"selectCurrentResults\">选择当前结果</button><button class=\"secondary\" type=\"button\" :disabled=\"!batchSelection.length\" @click=\"batchSelection = []\">清空选择</button><button type=\"button\" :disabled=\"!batchSelection.length\" @click=\"openBatchChange('ENABLED')\">批量开启</button><button class=\"danger-action\" type=\"button\" :disabled=\"!batchSelection.length\" @click=\"openBatchChange('DISABLED')\">批量关闭</button></div>\n    </section>\n\n    <div class=\"permission-groups\">",
)
replace_once(
    name,
    "        <header><div><span class=\"class-badge\" :class=\"`class-${group.permissionClass}`\">{{ permissionTitle(group.permissionClass) }}</span><h2>{{ group.module }}</h2><p>{{ permissionDescription(group.permissionClass) }}</p></div><span>{{ group.features.filter((item) => item.effectiveEnabled).length }} / {{ group.features.length }} 已开启</span></header>",
    "        <header><div class=\"permission-heading-line\"><div class=\"permission-heading-title\"><span class=\"class-badge\" :class=\"`class-${group.permissionClass}`\">{{ permissionTitle(group.permissionClass) }}</span><h2>{{ group.module }}</h2></div><span class=\"enabled-summary\">{{ group.features.filter((item) => item.effectiveEnabled).length }} / {{ group.features.length }} 已开启</span></div><p>{{ permissionDescription(group.permissionClass) }}</p></header>",
)
replace_once(
    name,
    "          <article v-for=\"feature in group.features\" :key=\"feature.featureCode\" :class=\"{ disabled: !feature.effectiveEnabled, unavailable: !feature.enabledInProgram }\">\n            <div class=\"feature-info\">",
    "          <article v-for=\"feature in group.features\" :key=\"feature.featureCode\" :class=\"{ disabled: !feature.effectiveEnabled, unavailable: !feature.enabledInProgram }\">\n            <label class=\"batch-feature-check\" :title=\"feature.enabledInProgram ? '加入批量操作' : '当前程序尚未实现'\"><input type=\"checkbox\" :disabled=\"!feature.enabledInProgram\" :checked=\"batchSelection.includes(feature.featureCode)\" @change=\"toggleBatchSelection(feature.featureCode)\" /></label>\n            <div class=\"feature-info\">",
)
replace_once(
    name,
    "    <div v-if=\"confirmFeature\" class=\"modal-backdrop\"",
    "    <div v-if=\"batchTarget\" class=\"modal-backdrop\" @click.self=\"batchTarget = null\"><section class=\"dialog\"><h2>{{ batchTarget === 'ENABLED' ? '批量开启功能' : batchTarget === 'DISABLED' ? '批量关闭功能' : '批量恢复默认设置' }}</h2><p>本次将调整 {{ batchSelection.length }} 项功能，保存后立即影响当前学校。</p><label><span>变更原因</span><textarea v-model.trim=\"batchReason\" rows=\"4\" maxlength=\"500\" placeholder=\"说明本次批量授权的业务原因\" /></label><div><button class=\"secondary\" type=\"button\" @click=\"batchTarget = null\">取消</button><button type=\"button\" :disabled=\"!batchReason.trim() || batchSaving\" @click=\"saveBatchChange\">{{ batchSaving ? '正在保存…' : '确认批量保存' }}</button></div></section></div>\n\n    <div v-if=\"confirmFeature\" class=\"modal-backdrop\"",
)
content = read(name)
content = content.replace(
    ".permission-group>header{display:flex;justify-content:space-between;align-items:flex-start;gap:18px;margin-bottom:16px}.permission-group header h2{margin:9px 0 4px;font-size:1.1rem}.permission-group header p,.permission-group header>span{margin:0;color:#69758b;font-size:.75rem}",
    ".permission-group>header{display:grid;gap:5px;margin-bottom:16px}.permission-heading-line{display:flex;align-items:center;justify-content:space-between;gap:18px}.permission-heading-title{display:flex;align-items:center;gap:9px;min-width:0}.permission-group header h2{margin:0;font-size:1.1rem}.permission-group header p,.enabled-summary{margin:0;color:#69758b;font-size:.75rem}.enabled-summary{margin-left:auto;white-space:nowrap}",
)
content = content.replace(
    ".feature-list article{display:flex;justify-content:space-between;gap:14px;padding:15px;",
    ".feature-list article{display:grid;grid-template-columns:auto minmax(0,1fr) auto;align-items:flex-start;gap:12px;padding:15px;",
)
content = content.replace(
    "</style>",
    ".batch-controls{display:flex;align-items:center;justify-content:space-between;gap:18px}.batch-controls>div:first-child{display:grid;gap:4px}.batch-controls span{color:#69758b;font-size:.75rem}.batch-control-actions{display:flex;gap:8px;flex-wrap:wrap;justify-content:flex-end}.batch-control-actions button{padding:9px 12px;border:0;border-radius:10px;color:#fff;background:#1d5dd8;cursor:pointer}.batch-control-actions .secondary{color:#315c9e;background:#edf3ff}.batch-control-actions .danger-action{background:#b4233a}.batch-feature-check{padding-top:2px}.batch-feature-check input{width:17px;height:17px}.dialog label{display:grid;gap:7px}.dialog textarea{padding:10px;border:1px solid #d7dfeb;border-radius:10px;resize:vertical}@media(max-width:760px){.batch-controls,.permission-heading-line{align-items:flex-start;flex-direction:column}.batch-control-actions{justify-content:flex-start}.enabled-summary{margin-left:0}}\n</style>",
)
write(name, content)

# ---------------------------------------------------------------------------
# Student list: formal residency and pending declaration in one query.
# ---------------------------------------------------------------------------
name = "backend-java/server/src/main/java/com/wust/dormitory/admin/StudentAdminService.java"
replace_once(
    name,
    "                       m.major_code, m.major_name, u.account_status,\n                       EXISTS(\n                           SELECT 1 FROM room_assignment ra\n                           WHERE ra.student_id=s.id AND ra.assignment_status='ACTIVE'\n                       ) AS currently_resident\n                FROM student s\n                JOIN major m ON m.id=s.major_id\n                LEFT JOIN app_user u ON u.student_id=s.id",
    "                       m.major_code, m.major_name, u.account_status,\n                       (active_ra.id IS NOT NULL) AS currently_resident,\n                       active_ra.id AS current_residency_id,\n                       current_building.building_name AS current_building_name,\n                       current_room.room_number AS current_room_number,\n                       current_bed.bed_code AS current_bed_code,\n                       current_bed.bed_type AS current_bed_type,\n                       pending_request.request_status AS selection_review_status,\n                       declared_bed.bed_code AS declared_bed_code,\n                       declared_bed.bed_type AS declared_bed_type\n                FROM student s\n                JOIN major m ON m.id=s.major_id\n                LEFT JOIN app_user u ON u.student_id=s.id\n                LEFT JOIN room_assignment active_ra ON active_ra.id=(\n                    SELECT ra.id FROM room_assignment ra\n                    WHERE ra.student_id=s.id AND ra.assignment_status='ACTIVE'\n                    ORDER BY ra.assigned_at DESC, ra.id DESC LIMIT 1\n                )\n                LEFT JOIN room current_room ON current_room.id=active_ra.room_id\n                LEFT JOIN dormitory_floor current_floor ON current_floor.id=current_room.floor_id\n                LEFT JOIN dormitory_building current_building ON current_building.id=current_floor.building_id\n                LEFT JOIN bed current_bed ON current_bed.id=active_ra.bed_id\n                LEFT JOIN bed_confirmation_request pending_request ON pending_request.id=(\n                    SELECT request.id FROM bed_confirmation_request request\n                    WHERE request.residency_id=active_ra.id AND request.request_status='PENDING'\n                    ORDER BY request.submitted_at DESC, request.id DESC LIMIT 1\n                )\n                LEFT JOIN bed declared_bed ON declared_bed.id=pending_request.declared_bed_id",
)

# Include scene placement fields in the accommodation adjustment context.
name = "backend-java/server/src/main/java/com/wust/dormitory/admin/AdminStudentResidencyAdjustmentService.java"
replace_once(
    name,
    "                SELECT bed.id AS bed_id, bed.room_id, bed.bed_code, bed.bed_type,\n                       room.room_number, room.capacity, room.resident_scope,",
    "                SELECT bed.id AS bed_id, bed.room_id, bed.bed_code, bed.bed_type,\n                       bed.position_index, bed.layout_x, bed.layout_z, bed.rotation_degrees,\n                       bed.operational_status, room.room_number, room.capacity, room.resident_scope,",
)

# Student administration: visual room/bed selector and direct accommodation columns.
name = "frontend/src/views/admin/AdminDataView.vue"
replace_once(
    name,
    "import TransientNotice from '../../components/common/TransientNotice.vue'",
    "import TransientNotice from '../../components/common/TransientNotice.vue'\nimport RoomBedScene3D from '../../components/student/RoomBedScene3D.vue'",
)
replace_once(
    name,
    "const placementSaving = ref(false)",
    "const placementSaving = ref(false)\nconst placementRoomId = ref(0)",
)
replace_once(
    name,
    "const placementBeds = computed(() => (placementTarget.value?.availableBeds ?? []) as DataObject[])\nconst currentResidency = computed(() => (placementTarget.value?.currentResidency ?? {}) as DataObject)",
    "const placementBeds = computed(() => (placementTarget.value?.availableBeds ?? []) as DataObject[])\nconst placementRooms = computed(() => [...new Map(placementBeds.value.map((bed) => [Number(bed.room_id), { roomId: Number(bed.room_id), label: `${bed.building_name} ${bed.room_number} · ${bed.floor_number}层` }])).values()])\nconst placementRoomBeds = computed(() => placementBeds.value.filter((bed) => Number(bed.room_id) === placementRoomId.value))\nconst placementSceneBeds = computed(() => placementRoomBeds.value.map((bed) => ({ ...bed, id: Number(bed.bed_id), operational_status: 'ENABLED' })))\nconst currentResidency = computed(() => (placementTarget.value?.currentResidency ?? {}) as DataObject)",
)
replace_once(
    name,
    "    placementTarget.value = (response.data.data ?? {}) as DataObject\n    placementForm.bedId = placementBeds.value.length ? Number(placementBeds.value[0].bed_id) : 0",
    "    placementTarget.value = (response.data.data ?? {}) as DataObject\n    placementRoomId.value = placementBeds.value.length ? Number(placementBeds.value[0].room_id) : 0\n    placementForm.bedId = placementRoomBeds.value.length ? Number(placementRoomBeds.value[0].bed_id) : 0",
)
replace_once(
    name,
    "  placementTarget.value = null\n  placementForm.bedId = 0",
    "  placementTarget.value = null\n  placementRoomId.value = 0\n  placementForm.bedId = 0",
)
replace_once(
    name,
    "function closePlacementAfterSave() {\n  placementTarget.value = null\n  placementForm.bedId = 0",
    "function choosePlacementRoom() {\n  placementForm.bedId = placementRoomBeds.value.length ? Number(placementRoomBeds.value[0].bed_id) : 0\n}\nfunction selectPlacementBed(bed: DataObject) {\n  placementForm.bedId = Number(bed.id)\n}\nfunction closePlacementAfterSave() {\n  placementTarget.value = null\n  placementRoomId.value = 0\n  placementForm.bedId = 0",
)
old_table = "<div v-else class=\"table-wrap\"><table><thead><tr><th>学生</th><th>专业与年级</th><th>类别</th><th>联系方式</th><th>录入方式</th><th>操作</th></tr></thead><tbody><tr v-for=\"student in students\" :key=\"String(student.id)\"><td><strong>{{ student.student_name }}</strong><small>{{ student.student_number }}</small></td><td>{{ student.major_name }}<small>{{ degreeText(student.degree_level) }} · {{ student.grade_year || '年级未填写' }}</small></td><td>{{ categoryText(student.student_category) }}<small>{{ countryLabel(String(student.nationality_code ?? 'CN')) }}</small></td><td>{{ student.phone_number || '未填写' }}</td><td>{{ sourceText(student.enrollment_source) }}</td><td><div class=\"button-row compact-actions\"><button class=\"button ghost small\" @click=\"editStudent(student)\">编辑</button><button class=\"button secondary small\" :disabled=\"placementLoadingId !== null\" @click=\"openPlacement(student)\">{{ placementLoadingId === Number(student.id) ? '读取中…' : '修改寝室/床位' }}</button><button class=\"button ghost small\" @click=\"openReset(student, 'password')\">重置密码</button><button class=\"button ghost small danger-text\" @click=\"openReset(student, 'state')\">完全重置</button></div></td></tr></tbody></table></div>"
new_table = "<div v-else class=\"table-wrap\"><table><thead><tr><th>学生</th><th>专业与年级</th><th>类别</th><th>住宿状态</th><th>宿舍与床位</th><th>联系方式</th><th>录入方式</th><th>操作</th></tr></thead><tbody><tr v-for=\"student in students\" :key=\"String(student.id)\"><td><strong>{{ student.student_name }}</strong><small>{{ student.student_number }}</small></td><td>{{ student.major_name }}<small>{{ degreeText(student.degree_level) }} · {{ student.grade_year || '年级未填写' }}</small></td><td>{{ categoryText(student.student_category) }}<small>{{ countryLabel(String(student.nationality_code ?? 'CN')) }}</small></td><td><span class=\"status-chip compact\" :class=\"{ warning: student.selection_review_status === 'PENDING' }\">{{ student.selection_review_status === 'PENDING' ? '待审核' : student.currently_resident ? '已入住' : '未入住' }}</span></td><td><template v-if=\"student.currently_resident\"><strong>{{ student.current_building_name }} {{ student.current_room_number }}</strong><small>正式床位：{{ student.current_bed_code ? `${student.current_bed_code} · ${bedTypeLabel(student.current_bed_type)}` : '待确认' }}</small><small v-if=\"student.selection_review_status === 'PENDING'\">学生已选择：{{ student.declared_bed_code }} · {{ bedTypeLabel(student.declared_bed_type) }}</small></template><span v-else>暂无住宿</span></td><td>{{ student.phone_number || '未填写' }}</td><td>{{ sourceText(student.enrollment_source) }}</td><td><div class=\"button-row compact-actions\"><button class=\"button ghost small\" @click=\"editStudent(student)\">编辑</button><button class=\"button secondary small\" :disabled=\"placementLoadingId !== null\" @click=\"openPlacement(student)\">{{ placementLoadingId === Number(student.id) ? '读取中…' : '修改寝室/床位' }}</button><button class=\"button ghost small\" @click=\"openReset(student, 'password')\">重置密码</button><button class=\"button ghost small danger-text\" @click=\"openReset(student, 'state')\">完全重置</button></div></td></tr></tbody></table></div>"
replace_once(name, old_table, new_table)
old_placement = "<form class=\"form-stack\" @submit.prevent=\"savePlacement\"><label><span>目标寝室与床位</span><select v-model.number=\"placementForm.bedId\" class=\"input\" required><option v-for=\"bed in placementBeds\" :key=\"String(bed.bed_id)\" :value=\"Number(bed.bed_id)\">{{ bed.display_name }} · {{ bedTypeLabel(bed.bed_type) }}</option></select></label><label><span>调整原因</span>"
new_placement = "<form class=\"form-stack\" @submit.prevent=\"savePlacement\"><label><span>筛选目标寝室</span><select v-model.number=\"placementRoomId\" class=\"input\" required @change=\"choosePlacementRoom\"><option v-for=\"room in placementRooms\" :key=\"room.roomId\" :value=\"room.roomId\">{{ room.label }}</option></select></label><div class=\"placement-scene\"><RoomBedScene3D :beds=\"placementSceneBeds\" :selected-bed-ids=\"placementForm.bedId ? [placementForm.bedId] : []\" @select=\"selectPlacementBed\" /><div class=\"placement-bed-summary\"><button v-for=\"bed in placementRoomBeds\" :key=\"String(bed.bed_id)\" type=\"button\" class=\"bed-choice-button\" :class=\"{ selected: placementForm.bedId === Number(bed.bed_id) }\" @click=\"placementForm.bedId = Number(bed.bed_id)\"><strong>{{ bed.bed_code }}</strong><span>{{ bedTypeLabel(bed.bed_type) }}</span></button></div></div><label><span>调整原因</span>"
replace_once(name, old_placement, new_placement)
content = read(name).replace(
    "</style>",
    ".placement-scene{display:grid;gap:12px}.placement-scene :deep(.room-bed-scene){min-height:430px}.placement-bed-summary{display:grid;grid-template-columns:repeat(auto-fit,minmax(110px,1fr));gap:8px}.bed-choice-button{display:grid;gap:4px;padding:11px;border:1px solid var(--line);border-radius:12px;color:inherit;background:var(--panel,#fff);text-align:left;cursor:pointer}.bed-choice-button span{color:var(--muted);font-size:12px}.bed-choice-button.selected{border-color:var(--primary);box-shadow:0 0 0 3px color-mix(in srgb,var(--primary) 14%,transparent)}\n</style>",
)
write(name, content)

# ---------------------------------------------------------------------------
# Batch publishing: direct confirmation after valid scope/preflight.
# ---------------------------------------------------------------------------
name = "frontend/src/views/admin/AdminBatchView.vue"
replace_once(
    name,
    "const publishAfterScope = ref(false)",
    "const publishAfterScope = ref(false)\nconst publishConfirmation = ref<DataObject | null>(null)\nconst publishing = ref(false)",
)
replace_once(
    name,
    "    if (continuePublish) {\n      await publishBatch(batch)\n    }",
    "    if (continuePublish) {\n      await preparePublish(batch)\n    }",
)
start = read(name)
old = "async function publishBatch(batch: DataObject) {\n  error.value = ''\n  try {\n    const response = await api.get<ObjectSuccessResponse>(\n      `/api/v1/admin/batches/${Number(batch.id)}/room-preflight`,\n    )\n    roomPreflight.value = (response.data.data ?? {}) as DataObject\n    preflightBatch.value = batch\n    if (!Boolean(roomPreflight.value.publishable)) {\n      error.value =\n        Number(roomPreflight.value.roomCount ?? 0) === 0\n          ? '当前批次尚未选择宿舍，请重新配置参与范围。'\n          : '发布前检查未通过，请处理阻断宿舍后重试。'\n      return\n    }\n    await api.post(`/api/v1/admin/batches/${Number(batch.id)}/status/PUBLISHED`)\n    message.value = '批次已发布。'\n    roomPreflight.value = null\n    preflightBatch.value = null\n    await load()\n  } catch (reason) {\n    error.value = reason instanceof Error ? reason.message : '批次发布失败'\n  }\n}\n"
new = "async function preparePublish(batch: DataObject) {\n  error.value = ''\n  publishConfirmation.value = null\n  if (Number(batch.eligible_count ?? 0) === 0) {\n    await openScope(batch, true)\n    return\n  }\n  try {\n    const response = await api.get<ObjectSuccessResponse>(\n      `/api/v1/admin/batches/${Number(batch.id)}/room-preflight`,\n    )\n    roomPreflight.value = (response.data.data ?? {}) as DataObject\n    preflightBatch.value = batch\n    if (Number(roomPreflight.value.roomCount ?? 0) === 0) {\n      roomPreflight.value = null\n      preflightBatch.value = null\n      await openScope(batch, true)\n      return\n    }\n    if (!Boolean(roomPreflight.value.publishable)) {\n      error.value = '发布前检查未通过，请处理阻断宿舍后重试。'\n      return\n    }\n    publishConfirmation.value = batch\n  } catch (reason) {\n    error.value = reason instanceof Error ? reason.message : '批次发布预检失败'\n  }\n}\n\nasync function confirmPublish() {\n  const batch = publishConfirmation.value\n  if (!batch || publishing.value) return\n  publishing.value = true\n  error.value = ''\n  try {\n    await api.post(`/api/v1/admin/batches/${Number(batch.id)}/status/PUBLISHED`)\n    message.value = '批次已发布。'\n    publishConfirmation.value = null\n    roomPreflight.value = null\n    preflightBatch.value = null\n    await load()\n  } catch (reason) {\n    error.value = reason instanceof Error ? reason.message : '批次发布失败'\n  } finally {\n    publishing.value = false\n  }\n}\n"
if old not in start:
    raise RuntimeError("publishBatch source not found")
write(name, start.replace(old, new, 1))
replace_once(
    name,
    "  if (target === 'PUBLISHED') {\n    await openScope(batch, true)\n    return\n  }",
    "  if (target === 'PUBLISHED') {\n    await preparePublish(batch)\n    return\n  }",
)
replace_once(
    name,
    "<div class=\"button-row\"><button class=\"button ghost small\" :disabled=\"scopeSaving\" @click=\"closeScope\">关闭</button><button class=\"button primary\" :disabled=\"scopeSaving\" @click=\"saveScope\">",
    "<div class=\"button-row scope-floating-actions\"><button class=\"button ghost small\" :disabled=\"scopeSaving\" @click=\"closeScope\">关闭</button><button class=\"button primary\" :disabled=\"scopeSaving\" @click=\"saveScope\">",
)
replace_once(
    name,
    "    <div v-if=\"copyDialog\" class=\"modal-overlay\"",
    "    <div v-if=\"publishConfirmation\" class=\"modal-overlay publish-confirmation-overlay\" @click.self=\"publishConfirmation = null\"><section class=\"modal-card publish-confirmation-dialog\"><span class=\"eyebrow\">READY TO PUBLISH</span><h3>{{ publishConfirmation.batch_name }} 已完成发布准备</h3><p>参与学生范围和宿舍范围均已设置，宿舍预检已通过。可以直接发布，无需再次进入范围设置。</p><div class=\"publish-confirmation-facts\"><span>参与学生 {{ publishConfirmation.eligible_count ?? 0 }} 人</span><span>宿舍 {{ roomPreflight?.roomCount ?? 0 }} 间</span><span>可用容量 {{ roomPreflight?.availableCapacity ?? 0 }}</span></div><div class=\"button-row dialog-actions\"><button class=\"button ghost\" type=\"button\" :disabled=\"publishing\" @click=\"publishConfirmation = null\">暂不发布</button><button class=\"button primary\" type=\"button\" :disabled=\"publishing\" @click=\"confirmPublish\">{{ publishing ? '正在发布…' : '直接发布' }}</button></div></section></div>\n\n    <div v-if=\"copyDialog\" class=\"modal-overlay\"",
)
replace_once(
    name,
    "<div v-if=\"allocationPreview\" class=\"modal-overlay\"",
    "<div v-if=\"allocationPreview\" class=\"modal-overlay allocation-overlay\"",
)
content = read(name)
content = content.replace(
    ".scope-dialog { width: min(1180px, calc(100vw - 32px)); max-height: calc(100vh - 32px); overflow: auto; padding: 24px; }",
    ".scope-dialog { position:relative; width: min(1180px, calc(100vw - 32px)); max-height: calc(100vh - 32px); overflow: auto; padding: 24px; }.scope-floating-actions{position:absolute;top:18px;right:22px;z-index:5}.scope-sticky-header{padding-right:250px!important}",
)
content = content.replace(
    ".preflight-dialog, .allocation-dialog { width: min(980px, calc(100vw - 32px)); max-height: calc(100vh - 32px); overflow: auto; padding: 24px; }",
    ".preflight-dialog, .allocation-dialog { width: min(980px, calc(100vw - 32px)); max-height: calc(100vh - 32px); overflow: auto; padding: 28px; background:var(--panel,#fff); box-shadow:0 28px 80px rgba(8,25,53,.28); }.allocation-overlay{padding:24px;background:rgba(8,22,47,.76);backdrop-filter:blur(8px)}.publish-confirmation-dialog{width:min(560px,calc(100vw - 32px));padding:26px}.publish-confirmation-dialog p{color:var(--muted);line-height:1.7}.publish-confirmation-facts{display:flex;gap:8px;flex-wrap:wrap;margin:16px 0}.publish-confirmation-facts span{padding:7px 10px;border-radius:999px;color:#315c9e;background:#edf3ff;font-size:12px;font-weight:700}.publish-confirmation-dialog .dialog-actions{justify-content:flex-end}",
)
content = content.replace(
    "  .scope-sticky-header { top: -24px; flex-direction: column; }",
    "  .scope-sticky-header { top: -24px; padding-right:24px!important; flex-direction: column; }.scope-floating-actions{position:static} .allocation-overlay{padding:10px}",
)
write(name, content)

# ---------------------------------------------------------------------------
# Expand key English translations after fixing locale direction.
# ---------------------------------------------------------------------------
name = "frontend/src/i18n/locales/en-US.ts"
replace_once(
    name,
    "  系统运行正常: 'System is healthy',\n  系统需要检查: 'System requires attention',",
    "  系统运行正常: 'System is healthy',\n  系统需要检查: 'System requires attention',\n  在住与床位核查: 'Residency and bed review',\n  在住名单与管理员确认: 'Residency records and admin confirmation',\n  学生申报核查: 'Student bed declarations',\n  实际床位核查: 'Actual bed review',\n  候补管理: 'Waitlist management',\n  候补补位: 'Waitlist placement',\n  换寝管理: 'Room change management',\n  申请换寝: 'Request room change',\n  导入质量: 'Import quality',\n  运营与健康: 'Operations and health',\n  异常工作台: 'Exception workbench',\n  修改密码: 'Change password',\n  修改手机号码: 'Edit mobile number',\n  进入个人偏好设置: 'Open preference settings',\n  学生列表: 'Student list',\n  住宿状态: 'Residency status',\n  宿舍与床位: 'Room and bed',\n  已入住: 'Resident',\n  未入住: 'Not resident',\n  待审核: 'Pending review',\n  正式床位: 'Confirmed bed',\n  学生已选择: 'Student selection',\n  批量功能授权: 'Batch feature authorization',\n  批量开启: 'Enable selected',\n  批量关闭: 'Disable selected',\n  保存参与范围: 'Save participation scope',\n  直接发布: 'Publish now',",
)
replace_once(
    name,
    "  'FAILED STUDENTS': '未分配学生',",
    "  'FAILED STUDENTS': '未分配学生',\n  'RESIDENCY AND BED REVIEW': '在住与床位核查',\n  'ACTUAL BED REVIEW': '实际床位核查',\n  'ACCOUNT SECURITY': '账号安全',\n  'READY TO PUBLISH': '发布准备完成',",
)

# Remove one-shot files in the generated commit.
p("scripts/ci/apply_ui_business_closure_round2.py").unlink()
p(".github/workflows/agent-ui-business-closure-round2.yml").unlink()
