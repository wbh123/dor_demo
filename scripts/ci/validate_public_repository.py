#!/usr/bin/env python3
"""Validate that a public repository snapshot contains code but no private data."""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

REQUIRED_PATHS = {
    "README.md",
    "AGENTS.md",
    "SECURITY.md",
    "backend-java/pom.xml",
    "backend-java/model/src/main/resources/openapi-interface.yaml",
    "frontend/package.json",
    "scripts/api/test_openapi_contract.py",
    "scripts/backend/test_phase1_source.py",
    "scripts/frontend/test_frontend_baseline.py",
}

FORBIDDEN_PATHS = {
    "docs",
    "records",
    "backend-java/docs",
    "backend-java/server/src/main/resources/db/migration",
    "backend-java/server/src/test/resources/db",
    "local-ci-logs",
    "data",
}

FORBIDDEN_FILES = {
    "开发.md",
    "甲方.md",
    "docker/README.md",
}

FORBIDDEN_DATA_SUFFIXES = {
    ".sql",
    ".dump",
    ".bak",
    ".sqlite",
    ".sqlite3",
    ".db",
}
DOCUMENT_SUFFIXES = {".md", ".rst", ".adoc", ".pdf", ".docx", ".pptx", ".xlsx"}
ALLOWED_ROOT_DOCUMENTS = {
    "README.md",
    "AGENTS.md",
    "SECURITY.md",
    "CONTRIBUTING.md",
    "CODE_OF_CONDUCT.md",
    "LICENSE",
    "LICENSE.md",
}
FORBIDDEN_TEXT = (
    "武汉科技大学",
    "武科大",
    "黄家湖",
    "WUHAN UNIVERSITY OF SCIENCE AND TECHNOLOGY",
)
SECRET_PATTERNS = {
    "private key": re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----"),
    "GitHub token": re.compile(r"\b(?:ghp|github_pat)_[A-Za-z0-9_]{20,}\b"),
    "OpenAI key": re.compile(r"\bsk-(?:proj-)?[A-Za-z0-9_-]{20,}\b"),
}


def is_probably_text(path: Path) -> bool:
    try:
        return b"\x00" not in path.read_bytes()[:8192]
    except OSError:
        return False


def validation_errors(root: Path) -> list[str]:
    root = root.resolve()
    errors: list[str] = []

    for relative in sorted(REQUIRED_PATHS):
        if not (root / relative).is_file():
            errors.append(f"required public file is missing: {relative}")

    for relative in sorted(FORBIDDEN_PATHS | FORBIDDEN_FILES):
        if (root / relative).exists():
            errors.append(f"forbidden path exists: {relative}")

    for path in root.rglob("*"):
        if not path.is_file() or ".git" in path.parts:
            continue
        relative = path.relative_to(root)
        suffix = path.suffix.lower()
        if suffix in FORBIDDEN_DATA_SUFFIXES:
            errors.append(f"forbidden database/data file: {relative}")
        if suffix in DOCUMENT_SUFFIXES:
            allowed_root_document = relative.parent == Path(".") and path.name in ALLOWED_ROOT_DOCUMENTS
            if not allowed_root_document:
                errors.append(f"forbidden internal document: {relative}")
        if not is_probably_text(path):
            continue
        try:
            text = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue
        for forbidden in FORBIDDEN_TEXT:
            if forbidden in text:
                errors.append(f"forbidden institution text in {relative}: {forbidden}")
        for label, pattern in SECRET_PATTERNS.items():
            if pattern.search(text):
                errors.append(f"possible {label} in {relative}")

    return sorted(set(errors))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("root", nargs="?", type=Path, default=Path.cwd())
    args = parser.parse_args()
    errors = validation_errors(args.root)
    if not errors:
        print("Public repository policy validation passed.")
        return 0
    print("Public repository policy validation failed:", file=sys.stderr)
    for error in errors:
        print(f"- {error}", file=sys.stderr)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
