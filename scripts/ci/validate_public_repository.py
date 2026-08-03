#!/usr/bin/env python3
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()

FORBIDDEN_PARTS = {
    "docs", "docker", "records", "data", "http", "deploy", "deployment",
    "k8s", "kubernetes", "helm", "nginx", "ansible", "terraform",
    ".idea", ".vscode", "mybatis-generator",
}
FORBIDDEN_SUFFIXES = {
    ".sql", ".http", ".pem", ".key", ".p12", ".pfx", ".jks", ".crt",
}
FORBIDDEN_NAMES = {
    ".env", "dockerfile", "docker-compose.yml", "docker-compose.yaml",
    "compose.yml", "compose.yaml",
}
FORBIDDEN_TEXT = (
    "武汉科技大学", "武科大", "黄家湖", "城安智序",
    "WUHAN UNIVERSITY OF SCIENCE AND TECHNOLOGY",
)
HIGH_CONFIDENCE_SECRETS = (
    re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"),
    re.compile(r"gh[pousr]_[A-Za-z0-9]{30,}"),
    re.compile(r"AKIA[0-9A-Z]{16}"),
    re.compile(r"xox[baprs]-[A-Za-z0-9-]{20,}"),
)
REQUIRED_FILES = (
    "README.md", "AGENTS.md", "SECURITY.md", ".env.example",
    ".github/workflows/public-ci.yml", "backend-java/pom.xml",
    "backend-java/model/src/main/resources/openapi-interface.yaml",
    "frontend/package.json",
    "scripts/ci/validate_system_contracts.py",
    "scripts/ci/run_policy.sh",
    "scripts/ci/run_contracts.sh",
    "scripts/ci/run_backend.sh",
    "scripts/ci/run_frontend.sh",
    "scripts/ci/run_all.sh",
)
SKIP_TEXT_SCAN = {Path("scripts/ci/validate_public_repository.py")}
ALLOWED_MARKDOWN = {
    Path("README.md"), Path("AGENTS.md"), Path("SECURITY.md"),
    Path("CONTRIBUTING.md"), Path("CODE_OF_CONDUCT.md"), Path("LICENSE.md"),
}


def relative(path: Path) -> Path:
    return path.relative_to(ROOT)


def forbidden_reason(path: Path) -> str | None:
    rel = relative(path)
    lowered = [part.lower() for part in rel.parts]
    if any(part in FORBIDDEN_PARTS for part in lowered):
        return "forbidden directory"
    if "db" in lowered and "migration" in lowered:
        return "database migration path"
    if path.suffix.lower() in FORBIDDEN_SUFFIXES:
        return "forbidden file type"
    if path.name.lower() in FORBIDDEN_NAMES:
        return "forbidden configuration or deployment file"
    if path.suffix.lower() == ".md" and rel not in ALLOWED_MARKDOWN:
        return "internal documentation"
    if "scripts" in lowered and "ci" not in lowered:
        return "non-CI script"
    return None


def main() -> int:
    errors: list[str] = []
    java_count = 0
    vue_count = 0
    test_count = 0

    for required in REQUIRED_FILES:
        if not (ROOT / required).is_file():
            errors.append(f"missing required public file: {required}")

    for path in ROOT.rglob("*"):
        if ".git" in path.parts or not path.is_file():
            continue
        rel = relative(path)
        reason = forbidden_reason(path)
        if reason:
            errors.append(f"{reason}: {rel.as_posix()}")
            continue
        java_count += int(path.suffix == ".java")
        vue_count += int(path.suffix == ".vue")
        test_count += int(path.name.endswith("Test.java"))
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
        for pattern in HIGH_CONFIDENCE_SECRETS:
            if pattern.search(text):
                errors.append(f"high-confidence secret in {rel.as_posix()}")
                break
        if path.suffix == ".sh":
            if not text.startswith("#!/usr/bin/env bash"):
                errors.append(f"CI shell script lacks portable bash shebang: {rel.as_posix()}")
            if "set -euo pipefail" not in text:
                errors.append(f"CI shell script lacks strict mode: {rel.as_posix()}")

    if java_count == 0:
        errors.append("no Java source files found")
    if vue_count == 0:
        errors.append("no Vue source files found")
    if test_count < 6:
        errors.append(f"core Java regression test count too low: {test_count}")

    if errors:
        print("Public repository validation failed:", file=sys.stderr)
        for error in sorted(set(errors)):
            print(f"- {error}", file=sys.stderr)
        return 1

    print(
        "Public repository validation passed: "
        f"{java_count} Java files, {vue_count} Vue files, {test_count} test classes"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
