#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SERVICE = (ROOT / "backend-java/server/src/main/java/com/wust/dormitory/student/StudentService.java").read_text(encoding="utf-8")
VIEW = (ROOT / "frontend/src/views/student/RoomDetailView.vue").read_text(encoding="utf-8")

for marker in (
        "public BedHoldService.HoldResult hold(",
        "public void release(",
        "public Map<String, Object> confirm(",
):
    start = SERVICE.index(marker)
    end = SERVICE.find("\n    public ", start + len(marker))
    if end < 0:
        end = SERVICE.find("\n    private ", start + len(marker))
    body = SERVICE[start:end]
    for forbidden in ("StudentRoomSelectionBootstrapService", "recommendation", "rooms("):
        if forbidden in body:
            raise AssertionError(f"床位写热路径 {marker} 不得触发重型读取：{forbidden}")

for function_name in ("createHold", "releaseHold", "confirmSelection"):
    marker = f"async function {function_name}"
    start = VIEW.index(marker)
    end = VIEW.find("\nasync function ", start + len(marker))
    if end < 0:
        end = VIEW.find("\nfunction ", start + len(marker))
    body = VIEW[start:end]
    if "load(false)" in body or "/selection/bootstrap" in body:
        raise AssertionError(f"前端 {function_name} 成功写路径不得同步追加 bootstrap 读取")

print("Bed selection hot-path contract: OK")
