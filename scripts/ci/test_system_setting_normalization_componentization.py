#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ADMIN_ROOT = ROOT / "backend-java" / "server" / "src" / "main" / "java" / "com" / "wust" / "dormitory" / "admin"
BASELINE = ROOT / "scripts" / "ci" / "backend_modularization_baseline.json"
SERVICE = ADMIN_ROOT / "SystemSettingService.java"
SUPPORT = ADMIN_ROOT / "WelcomeMessageNormalizationSupport.java"

errors: list[str] = []
service = SERVICE.read_text(encoding="utf-8") if SERVICE.exists() else ""
support = SUPPORT.read_text(encoding="utf-8") if SUPPORT.exists() else ""

if not SUPPORT.exists():
    errors.append("缺少 WelcomeMessageNormalizationSupport.java")
if SERVICE.exists() and len(service.splitlines()) > 300:
    errors.append(f"SystemSettingService 仍超过300行：{len(service.splitlines())}")
if SUPPORT.exists() and len(support.splitlines()) > 300:
    errors.append(f"WelcomeMessageNormalizationSupport 超过300行：{len(support.splitlines())}")

for method in (
    "normalizeLocaleMessages",
    "normalizeCountryMessages",
    "normalizeLocaleTag",
    "normalizeCountryCode",
):
    if f"WelcomeMessageNormalizationSupport.{method}" not in service:
        errors.append(f"SystemSettingService 未委托欢迎语归一化职责：{method}")
    if method not in support:
        errors.append(f"欢迎语归一化支持类缺少方法：{method}")

for token in (
    "WELCOME_LOCALE_INVALID",
    "WELCOME_LOCALE_DUPLICATED",
    "WELCOME_COUNTRY_INVALID",
    "WELCOME_COUNTRY_DUPLICATED",
    "WELCOME_COUNTRY_LIMIT_EXCEEDED",
):
    if token not in support:
        errors.append(f"欢迎语归一化支持类缺少校验语义：{token}")

baseline = BASELINE.read_text(encoding="utf-8") if BASELINE.exists() else ""
if "SystemSettingService.java" in baseline:
    errors.append("SystemSettingService 已降至阈值以下后必须从大型Java基线移除")

if errors:
    print("系统设置欢迎语归一化组件化契约失败：")
    for error in errors:
        print(f"- {error}")
    raise SystemExit(1)

print("system setting normalization componentization: OK")
