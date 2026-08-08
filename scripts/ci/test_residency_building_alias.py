#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
xml = (
    root
    / "backend-java/server/src/main/resources/mapper/residency/ResidencyMapper.xml"
).read_text(encoding="utf-8")

if "db.building_id" in xml:
    raise SystemExit("ResidencyMapper仍读取不存在的db.building_id字段")
if xml.count("db.id AS building_id") < 2:
    raise SystemExit("当前住宿与管理员在住列表未统一投影building_id")

print("Residency building alias validation passed")
