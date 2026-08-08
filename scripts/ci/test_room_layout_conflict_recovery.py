from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
view = (ROOT / 'frontend/src/components/admin/RoomLayoutEditor.vue').read_text(encoding='utf-8')

assert 'ApiRequestError' in view, 'RoomLayoutEditor must inspect stable backend error codes'
assert 'ROOM_LAYOUT_VERSION_CONFLICT' in view, '409 version conflict must have dedicated recovery flow'
assert '房间信息已发生变化，已加载最新布局，请确认后重新保存' in view
assert '重新加载失败' in view, 'a failed conflict reload must remain visible instead of reporting success'
save_start = view.index('async function save')
save_body = view[save_start:save_start + 3000]
assert 'const previousReason' in save_body or 'const savedReason' in save_body, 'operator reason must be preserved across conflict reload'
assert 'await load()' in save_body, 'conflict recovery must reload the latest room layout'
assert 'reloadError' in save_body, 'conflict recovery must distinguish a successful reload from a failed reload'
print('Room layout conflict recovery contract passed')
