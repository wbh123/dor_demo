#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f"missing patch target: {label}")
    return text.replace(old, new, 1)


def patch(path: str, transform) -> None:
    target = ROOT / path
    original = target.read_text(encoding="utf-8")
    updated = transform(original)
    if updated == original:
        raise SystemExit(f"patch produced no change: {path}")
    target.write_text(updated, encoding="utf-8")


def patch_layout(text: str) -> str:
    text = replace_once(
        text,
        "function nudgeUnit(unit:Unit,deltaX:number,deltaZ:number){if(!canEdit.value)return;updateUnit(unit.key,Math.min(MAX_X,Math.max(MIN_X,unit.x+deltaX)),Math.min(MAX_Z,Math.max(MIN_Z,unit.z+deltaZ)))}\n",
        "",
        "remove nudgeUnit",
    )
    text = replace_once(
        text,
        "function resetStandardLayout(){if(!canEdit.value)return;const placements=[[-2.35,-1.65],[2.35,-1.65],[-2.35,1.65],[2.35,1.65],[-.8,0],[.8,0]];units.value.forEach((unit,index)=>{const [x,z]=placements[index]??[0,Math.min(MAX_Z,-2.8+index*1.1)];updateUnit(unit.key,x,z,0)});message.value='已恢复标准2×2布局，请填写原因后保存。'}\n",
        "",
        "remove standard layout reset",
    )
    text = replace_once(
        text,
        "function typeLabel(type:UnitType){return{LOFT_BED_DESK:'上床下桌',BUNK:'上下铺',SINGLE_BED:'单人床'}[type]}\n",
        "function typeLabel(type:UnitType){return{LOFT_BED_DESK:'上床下桌',BUNK:'上下铺',SINGLE_BED:'单人床'}[type]}\nfunction typeActionDisabled(unit:Unit,type:UnitType){if(!canEdit.value)return true;if(unit.occupied&&type!==unit.originalType)return true;return unit.originalType==='BUNK'&&type!=='BUNK'}\n",
        "add type action guard",
    )
    text = replace_once(
        text,
        "<header class=\"section-head split-title\"><div><span class=\"eyebrow\">{{ subtitle('床位布局','ROOM LAYOUT') }}</span><h3>{{ roomLabel }}床位布局</h3><p>以左侧房门和右侧窗户为相对位置参照；有可视化编辑权限时可拖动、微调和旋转床位。</p></div><div class=\"button-row\"><button v-if=\"canEdit\" class=\"button ghost\" @click=\"resetStandardLayout\">恢复标准2×2布局</button><button class=\"button ghost\" @click=\"emit('close')\">关闭</button></div></header>",
        "<header class=\"section-head split-title\"><div><span class=\"eyebrow\">{{ subtitle('床位布局','ROOM LAYOUT') }}</span><h3>{{ roomLabel }}床位布局</h3><p>以左侧房门和右侧窗户为相对位置参照；有可视化编辑权限时可拖动、旋转并调整床型。</p></div><button class=\"button ghost\" @click=\"emit('close')\">关闭</button></header>",
        "layout header actions",
    )
    text = replace_once(
        text,
        "      <div class=\"layout-relative-reference\"><span><b>横向参照</b> 左侧靠门，右侧靠窗</span><span><b>纵向参照</b> 上方为房间前侧，下方为房间内侧</span></div>\n",
        "",
        "remove reference bubbles",
    )
    text = replace_once(
        text,
        "          <div class=\"layout-bed-content\"><strong>{{ unit.label }}</strong><span>{{ typeLabel(unit.unitType) }}</span><div v-if=\"canEdit\" class=\"layout-bed-type-actions\"><button type=\"button\" title=\"向房门侧移动\" @click=\"nudgeUnit(unit,-.5,0)\">靠门</button><button type=\"button\" title=\"向窗户侧移动\" @click=\"nudgeUnit(unit,.5,0)\">靠窗</button><button type=\"button\" title=\"向房间前侧移动\" @click=\"nudgeUnit(unit,0,-.5)\">前移</button><button type=\"button\" title=\"向房间内侧移动\" @click=\"nudgeUnit(unit,0,.5)\">后移</button><button type=\"button\" @click=\"rotate(unit)\">旋转90°</button></div></div>\n",
        "          <div class=\"layout-bed-content\"><strong>{{ unit.label }}</strong><span>{{ typeLabel(unit.unitType) }}</span><div v-if=\"canEdit\" class=\"layout-bed-type-actions\"><button type=\"button\" @pointerdown.stop @click.stop=\"rotate(unit)\">旋转90°</button><button type=\"button\" :class=\"{active:unit.unitType==='BUNK'}\" :disabled=\"typeActionDisabled(unit,'BUNK')\" @pointerdown.stop @click.stop=\"setType(unit,'BUNK')\">上下铺</button><button type=\"button\" :class=\"{active:unit.unitType==='LOFT_BED_DESK'}\" :disabled=\"typeActionDisabled(unit,'LOFT_BED_DESK')\" @pointerdown.stop @click.stop=\"setType(unit,'LOFT_BED_DESK')\">上床下桌</button><button type=\"button\" :class=\"{active:unit.unitType==='SINGLE_BED'}\" :disabled=\"typeActionDisabled(unit,'SINGLE_BED')\" @pointerdown.stop @click.stop=\"setType(unit,'SINGLE_BED')\">单人床</button></div></div>\n",
        "bed card actions",
    )
    text = replace_once(
        text,
        "      <div class=\"unit-list\"><article v-for=\"unit in units\" :key=\"`control-${unit.key}`\"><div><strong>{{ unit.label }}</strong><small>{{ unit.occupied?'当前有学生，床型锁定':canEdit?'空床可调整床型':'当前仅可查看床型' }}</small></div><div v-if=\"canEdit\" class=\"type-buttons\"><button v-for=\"type in (['LOFT_BED_DESK','BUNK','SINGLE_BED'] as UnitType[])\" :key=\"type\" type=\"button\" :class=\"{active:unit.unitType===type}\" :disabled=\"unit.occupied&&unit.originalType!==type\" @click=\"setType(unit,type)\">{{ typeLabel(type) }}</button></div></article></div>\n",
        "",
        "remove separate unit controls",
    )
    text = replace_once(
        text,
        ".capacity-summary span{color:var(--muted)}.layout-relative-reference{display:flex;gap:10px;flex-wrap:wrap;margin-top:10px}.layout-relative-reference span{padding:7px 10px;border:1px solid var(--line);border-radius:999px;color:var(--muted);background:var(--panel,#fff);font-size:12px}.layout-relative-reference b{color:var(--text);margin-right:5px}.layout-stage",
        ".capacity-summary span{color:var(--muted)}.layout-stage",
        "remove reference bubble styles",
    )
    text = replace_once(
        text,
        ".layout-unit{position:absolute;width:190px;min-height:112px;cursor:grab;user-select:none;touch-action:none}.layout-bed-surface{position:absolute;inset:0;border:2px solid #5684c9;border-radius:16px;background:#eef5ff;transform-origin:center}",
        ".layout-unit{position:absolute;width:190px;aspect-ratio:19/9;min-height:0;cursor:grab;user-select:none;touch-action:none}.layout-bed-surface{position:absolute;inset:0;border:2px solid #5684c9;border-radius:18px;background:#eef5ff;transform-origin:center}",
        "bed card ratio",
    )
    text = replace_once(
        text,
        ".layout-bed-content{position:relative;z-index:2;display:grid;place-items:center;gap:6px;min-height:112px;padding:12px}.layout-bed-type-actions{display:flex;justify-content:center;gap:4px;flex-wrap:wrap}.layout-bed-content button{border:0;border-radius:999px;padding:4px 7px;background:#fff;cursor:pointer;font-size:11px}.unit-list{display:grid;gap:10px}.unit-list article{display:flex;align-items:center;justify-content:space-between;gap:16px;padding:12px;border:1px solid var(--line);border-radius:14px}.unit-list article>div:first-child{display:grid;gap:4px}.unit-list small{color:var(--muted)}.type-buttons{display:flex;gap:7px;flex-wrap:wrap}.type-buttons button{padding:8px 11px;border:1px solid var(--line);border-radius:10px;background:var(--panel);cursor:pointer}.type-buttons button.active{border-color:#5684c9;background:#eef5ff}",
        ".layout-bed-content{position:relative;z-index:2;display:grid;grid-template-rows:auto auto auto;align-content:center;gap:3px;height:100%;padding:7px 9px;text-align:center}.layout-bed-content>strong{font-size:13px;line-height:1.1}.layout-bed-content>span{font-size:11px;color:var(--muted)}.layout-bed-type-actions{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:3px;width:100%}.layout-bed-content button{min-width:0;border:1px solid rgba(86,132,201,.28);border-radius:7px;padding:2px 3px;background:rgba(255,255,255,.94);cursor:pointer;font-size:9.5px;line-height:1.25;white-space:nowrap}.layout-bed-content button.active{border-color:#5684c9;background:#dfeeff;color:#24599c;font-weight:800}.layout-bed-content button:disabled{cursor:not-allowed;opacity:.48}",
        "bed card compact controls",
    )
    text = replace_once(
        text,
        ".layout-unit{width:140px;min-height:96px}.layout-bed-content{min-height:96px}.layout-boundary-label{font-size:10px}.unit-list article{align-items:flex-start;flex-direction:column}",
        ".layout-unit{width:152px}.layout-bed-content{padding:6px}.layout-bed-content button{font-size:8.5px}.layout-boundary-label{font-size:10px}",
        "mobile bed card",
    )
    return text


def patch_batch_template(text: str) -> str:
    text = replace_once(text, '<label class="separation-switch">', '<div class="separation-switch">', "separation wrapper open")
    text = replace_once(
        text,
        "        <div><strong>国内生与国际生分开选寝</strong><p>开启后仅允许国内生使用国内生专用宿舍、国际生使用国际生专用宿舍，混住宿舍不进入本批次。</p></div>\n      </label>",
        "        <div><strong>国内生与国际生分开选寝</strong><p>开启后仅允许国内生使用国内生专用宿舍、国际生使用国际生专用宿舍，混住宿舍不进入本批次。</p></div>\n      </div>",
        "separation wrapper close",
    )
    return text


def patch_batch_css(text: str) -> str:
    text = replace_once(
        text,
        ".mode-card{display:grid;gap:8px;padding:20px;border:1px solid var(--border);border-radius:16px;background:var(--surface);text-align:left;color:inherit}",
        ".mode-card{appearance:none;display:grid;align-content:start;width:100%;min-height:124px;gap:8px;padding:20px;border:1px solid var(--border);border-radius:16px;background:var(--surface);text-align:left;color:inherit;font:inherit;cursor:pointer}",
        "mode card reset",
    )
    text = replace_once(text, ".mode-card.disabled{opacity:.55}", ".mode-card.disabled{opacity:.55;cursor:not-allowed}", "disabled mode card")
    text = replace_once(
        text,
        ".separation-switch{display:flex;align-items:center;gap:14px;padding:16px;border:1px solid var(--border);border-radius:14px;background:var(--surface-soft)}",
        ".separation-switch{display:flex;align-items:center;gap:14px;min-width:0;padding:16px;border:1px solid var(--border);border-radius:14px;background:var(--surface-soft)}.separation-switch>div{min-width:0;flex:1}.separation-switch strong{display:block}",
        "separation layout",
    )
    text = replace_once(
        text,
        ".separation-switch>button{position:relative;width:50px;height:28px;border:0;border-radius:999px;background:#cbd5e1;flex:0 0 auto}",
        ".separation-switch>button{appearance:none;position:relative;width:50px;height:28px;border:0;border-radius:999px;background:#cbd5e1;flex:0 0 auto;cursor:pointer}",
        "separation switch reset",
    )
    text = replace_once(
        text,
        ".rule-summary{display:grid;gap:6px;padding:12px;border-radius:12px;background:var(--surface-soft)}",
        ".rule-summary{align-self:end;display:grid;align-content:center;gap:6px;min-height:88px;padding:12px;border-radius:12px;background:var(--surface-soft)}",
        "rule summary alignment",
    )
    text = replace_once(
        text,
        ".scope-grid{display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1fr);align-items:stretch;gap:16px;height:min(60vh,620px);min-height:360px}",
        ".scope-grid{display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1fr);align-items:start;gap:16px}",
        "scope grid height",
    )
    text = replace_once(
        text,
        ".scope-column{display:flex;flex-direction:column;justify-content:flex-start;gap:12px;min-width:0;min-height:0;padding:14px;border:1px solid var(--border);border-radius:14px;background:var(--surface-soft)}",
        ".scope-column{display:flex;flex-direction:column;justify-content:flex-start;align-self:start;gap:12px;min-width:0;padding:14px;border:1px solid var(--border);border-radius:14px;background:var(--surface-soft)}",
        "scope column alignment",
    )
    text = replace_once(
        text,
        ".scope-result-list{display:flex;flex:1 1 auto;min-height:0;flex-direction:column;gap:8px;overflow:auto;overscroll-behavior:contain;padding-right:4px}",
        ".scope-result-list{display:grid;gap:8px;min-height:160px;max-height:440px;overflow:auto;overscroll-behavior:contain;padding-right:4px}",
        "scope result scrolling",
    )
    text = replace_once(text, ".empty-state.compact{margin:auto 0;padding:20px 12px}", ".empty-state.compact{margin:0;padding:20px 12px}", "scope empty state")
    text = replace_once(
        text,
        "@media(max-width:900px){.scope-grid{grid-template-columns:1fr;height:auto;min-height:0}.scope-column{height:min(52vh,480px)}}",
        "@media(max-width:900px){.scope-grid{grid-template-columns:1fr}.scope-result-list{max-height:320px}}",
        "scope mobile layout",
    )
    return text


def patch_modal(text: str) -> str:
    text = replace_once(
        text,
        "export type AppModalSize = 'default' | 'wide' | 'large' | 'fullscreen'",
        "export type AppModalSize = 'compact' | 'default' | 'wide' | 'large' | 'fullscreen'",
        "modal compact type",
    )
    text = replace_once(text, ".app-modal-backdrop{position:fixed", ".app-modal-backdrop{box-sizing:border-box;position:fixed", "backdrop box sizing")
    text = replace_once(text, ".app-modal-surface{display:flex", ".app-modal-surface{box-sizing:border-box;display:flex", "surface box sizing")
    text = replace_once(text, "width:min(680px,100%)", "width:min(720px,100%)", "default modal width")
    text = replace_once(
        text,
        ".app-modal--wide{width:min(960px,100%)}",
        ".app-modal--compact{width:min(560px,100%)}.app-modal--wide{width:min(980px,100%)}",
        "compact and wide modal widths",
    )
    text = replace_once(text, ".app-modal--large{width:min(1240px,100%);", ".app-modal--large{width:min(1180px,100%);", "large modal width")
    text = replace_once(text, ".app-modal-header{display:flex", ".app-modal-header{box-sizing:border-box;display:flex", "header box sizing")
    text = replace_once(text, ".app-modal-body{position:relative;", ".app-modal-body{box-sizing:border-box;position:relative;width:100%;min-width:0;", "body stable sizing")
    text = replace_once(text, ".app-modal-footer{display:flex;", ".app-modal-footer{box-sizing:border-box;display:flex;flex-wrap:wrap;", "footer stable sizing")
    text = replace_once(
        text,
        ".app-modal-surface,.app-modal--wide,.app-modal--large{width:100%;",
        ".app-modal-surface,.app-modal--compact,.app-modal--wide,.app-modal--large{width:100%;",
        "mobile compact modal",
    )
    return text


def patch_confirm(text: str) -> str:
    return replace_once(text, '    size="default"', '    size="compact"', "confirmation compact size")


patch("frontend/src/components/admin/RoomLayoutEditor.vue", patch_layout)
patch("frontend/src/views/admin/AdminBatchView.template.html", patch_batch_template)
patch("frontend/src/views/admin/AdminBatchView.css", patch_batch_css)
patch("frontend/src/components/modal/AppModal.vue", patch_modal)
patch("frontend/src/components/modal/AppConfirmDialog.vue", patch_confirm)
print("Applied requested UI layout restoration")
