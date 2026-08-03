#!/usr/bin/env python3
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()

FORBIDDEN_PARTS = {
    "docs",
    "docker",
    "records",
    "data",
    "deploy",
    "deployment",
    "k8s",
    "kubernetes",
    "helm",
    "nginx",
    "ansible",
    "terraform",
    ".idea",
    ".vscode",
    "mybatis-generator",
}
FORBIDDEN_SUFFIXES = {".sql", ".pem", ".key", ".p12", ".pfx", ".jks", ".crt"}
FORBIDDEN_NAMES = {
    ".env",
    "dockerfile",
    "docker-compose.yml",
    "docker-compose.yaml",
    "compose.yml",
    "compose.yaml",
}
FORBIDDEN_TEXT = (
    "武汉科技大学",
    "武科大",
    "黄家湖",
    "WUHAN UNIVERSITY OF SCIENCE AND TECHNOLOGY",
)
SECRET_PATTERNS = (
    re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"),
    re.compile(r"(?i)(?:password|passwd|secret|token|api[_-]?key)\s*[:=]\s*['\"]?(?!change-me|placeholder|example|\$\{|\*{3,})[^\s'\"]{12,}"),
    re.compile(r"gh[pousr]_[A-Za-z0-9]{30,}"),
)
REQUIRED_FILES = (
    "README.md",
    "AGENTS.md",
    ".env.example",
    ".github/workflows/public-ci.yml",
    "backend-java/pom.xml",
    "backend-java/model/src/main/resources/openapi-interface.yaml",
    "frontend/package.json",
)
SKIP_TEXT_SCAN = {
    Path("scripts/ci/validate_public_repository.py"),
}


def relative(path: Path) -> Path:
    return path.relative_to(ROOT)


def is_forbidden_path(path: Path) -> str | None:
    rel = relative(path)
    lowered = [part.lower() for part in rel.parts]
    if any(part in FORBIDDEN_PARTS for part in lowered):
        return "forbidden directory"
    if "db" in lowered and "migration" in lowered:
        return "database migration path"
    if len(lowered) >= 4 and lowered[-4:] == ["test", "resources", "db", lowered[-1]]:
        return "database test resource"
    if path.suffix.lower() in FORBIDDEN_SUFFIXES:
        return "forbidden file type"
    if path.name.lower() in FORBIDDEN_NAMES:
        return "forbidden configuration or deployment file"
    return None


def main() -> int:
    errors: list[str] = []
    java_count = 0
    vue_count = 0

    for required in REQUIRED_FILES:
        if not (ROOT / required).is_file():
            errors.append(f"missing required public file: {required}")

    for path in ROOT.rglob("*"):
        if ".git" in path.parts or not path.is_file():
            continue
        rel = relative(path)
        reason = is_forbidden_path(path)
        if reason:
            errors.append(f"{reason}: {rel.as_posix()}")
            continue
        if path.suffix == ".java":
            java_count += 1
        if path.suffix == ".vue":
            vue_count += 1
        if rel in SKIP_TEXT_SCAN:
            continue
        try:
            raw = path.read_bytes()
            if b"\x00" in raw[:8192]:
                continue
            text = raw.decode("utf-8")
        except (OSError, UnicodeDecodeError):
            continue
        for marker in FORBIDDEN_TEXT:
            if marker in text:
                errors.append(f"real institution marker in {rel.as_posix()}: {marker}")
        for pattern in SECRET_PATTERNS:
            if pattern.search(text):
                errors.append(f"possible secret in {rel.as_posix()}")
                break

    if java_count == 0:
        errors.append("no Java source files found")
    if vue_count == 0:
        errors.append("no Vue source files found")

    if errors:
        print("Public repository validation failed:", file=sys.stderr)
        for error in sorted(set(errors)):
            print(f"- {error}", file=sys.stderr)
        return 1

    print(f"Public repository validation passed: {java_count} Java files, {vue_count} Vue files")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
