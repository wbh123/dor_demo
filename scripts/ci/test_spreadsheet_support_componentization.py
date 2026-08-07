#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ADMIN_ROOT = ROOT / "backend-java" / "server" / "src" / "main" / "java" / "com" / "wust" / "dormitory" / "admin"
BASELINE = ROOT / "scripts" / "ci" / "backend_modularization_baseline.json"
MAIN = ADMIN_ROOT / "SpreadsheetSupport.java"
TEXT = ADMIN_ROOT / "SpreadsheetTextSupport.java"

errors: list[str] = []

if not TEXT.exists():
    errors.append("缺少 SpreadsheetTextSupport.java")

main_source = MAIN.read_text(encoding="utf-8") if MAIN.exists() else ""
text_source = TEXT.read_text(encoding="utf-8") if TEXT.exists() else ""

if MAIN.exists() and len(main_source.splitlines()) > 300:
    errors.append(f"SpreadsheetSupport 仍超过300行：{len(main_source.splitlines())}")
if TEXT.exists() and len(text_source.splitlines()) > 300:
    errors.append(f"SpreadsheetTextSupport 超过300行：{len(text_source.splitlines())}")

for token in (
    "SpreadsheetTextSupport.readCsv",
    "SpreadsheetTextSupport.csvLine",
    "SpreadsheetTextSupport.map",
):
    if token not in main_source:
        errors.append(f"SpreadsheetSupport 未委托文本职责：{token}")
if not any(
    token in main_source
    for token in (
        "SpreadsheetTextSupport::normalizeHeader",
        "SpreadsheetTextSupport.normalizeHeader",
    )
):
    errors.append("SpreadsheetSupport 未委托文本职责：SpreadsheetTextSupport.normalizeHeader")

for method in ("readCsv", "parseCsv", "normalizeHeader", "csvLine"):
    if f"private static" in main_source and f" {method}(" in main_source:
        errors.append(f"SpreadsheetSupport 仍保留文本方法：{method}")

for token in ("readCsv", "parseCsv", "normalizeHeader", "csvLine", "map"):
    if token not in text_source:
        errors.append(f"SpreadsheetTextSupport 缺少职责：{token}")

baseline = BASELINE.read_text(encoding="utf-8") if BASELINE.exists() else ""
if "SpreadsheetSupport.java" in baseline:
    errors.append("SpreadsheetSupport 已降至阈值以下后必须从大型Java基线移除")

if errors:
    print("表格支持组件化契约失败：")
    for error in errors:
        print(f"- {error}")
    raise SystemExit(1)

print("spreadsheet support componentization: OK")
