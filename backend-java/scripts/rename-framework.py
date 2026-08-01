#!/usr/bin/env python3
"""按 JSON 配置一次性适配可复用 Java 多模块框架。"""

from __future__ import annotations

import argparse
import json
import shutil
from pathlib import Path
from typing import Any


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="按 JSON 配置适配 Java 多模块框架。")
    parser.add_argument(
        "--config",
        default="scripts/rename-framework.json",
        help="相对于工程根目录的 JSON 配置文件，默认 scripts/rename-framework.json",
    )
    parser.add_argument("--dry-run", action="store_true", help="仅显示将执行的修改，不写入文件")
    return parser.parse_args()


def require_relative(raw: str, field: str) -> Path:
    path = Path(raw)
    if path.is_absolute() or ".." in path.parts:
        raise ValueError(f"{field} 必须是工程根目录内的相对路径：{raw}")
    return path


def resolve_inside(root: Path, relative: Path, field: str) -> Path:
    candidate = (root / relative).resolve()
    try:
        candidate.relative_to(root.resolve())
    except ValueError as exc:
        raise ValueError(f"{field} 超出工程根目录：{relative.as_posix()}") from exc
    return candidate


def load_config(repo_root: Path, config_arg: str) -> tuple[Path, dict[str, Any]]:
    config_rel = require_relative(config_arg, "--config")
    config_path = resolve_inside(repo_root, config_rel, "--config")
    if not config_path.is_file():
        raise FileNotFoundError(f"配置文件不存在：{config_rel.as_posix()}")
    with config_path.open("r", encoding="utf-8") as file:
        config = json.load(file)
    if config.get("schema_version") != 1:
        raise ValueError("仅支持 schema_version=1")
    return config_path, config


def validate_config(repo_root: Path, config_path: Path, config: dict[str, Any]) -> dict[str, Any]:
    project_root_rel = require_relative(config.get("project_root", "."), "project_root")
    project_root = resolve_inside(repo_root, project_root_rel, "project_root")
    if not project_root.is_dir():
        raise NotADirectoryError(f"project_root 不存在：{project_root_rel.as_posix()}")

    text = config.get("text", {})
    roots = [require_relative(item, "text.roots") for item in text.get("roots", ["."])]
    exclude_dirs = set(text.get("exclude_dirs", [".git", "target", ".idea", ".vscode", "__pycache__"]))
    exclude_files = {
        require_relative(item, "text.exclude_files").as_posix()
        for item in text.get("exclude_files", [])
    }
    exclude_files.add(config_path.relative_to(repo_root).as_posix())
    include_extensions = set(text.get("include_extensions", []))
    include_names = set(text.get("include_names", []))

    replacements = config.get("replacements", [])
    if not isinstance(replacements, list) or not replacements:
        raise ValueError("replacements 必须是非空数组")

    normalized_replacements: list[tuple[str, str]] = []
    for index, item in enumerate(replacements):
        old = item.get("from")
        new = item.get("to")
        if not isinstance(old, str) or not old:
            raise ValueError(f"replacements[{index}].from 必须是非空字符串")
        if not isinstance(new, str):
            raise ValueError(f"replacements[{index}].to 必须是字符串")
        normalized_replacements.append((old, new))

    moves = []
    for index, item in enumerate(config.get("moves", [])):
        source = require_relative(item["source"], f"moves[{index}].source")
        target = require_relative(item["target"], f"moves[{index}].target")
        moves.append(
            {
                "source": source,
                "target": target,
                "required": bool(item.get("required", True)),
            }
        )

    return {
        "project_root": project_root,
        "roots": roots,
        "exclude_dirs": exclude_dirs,
        "exclude_files": exclude_files,
        "include_extensions": include_extensions,
        "include_names": include_names,
        "replacements": normalized_replacements,
        "moves": moves,
        "build": config.get("build", {}),
    }


def relative_display(repo_root: Path, path: Path) -> str:
    return path.resolve().relative_to(repo_root.resolve()).as_posix()


def iter_text_files(repo_root: Path, settings: dict[str, Any]) -> list[Path]:
    project_root: Path = settings["project_root"]
    found: set[Path] = set()

    for root_rel in settings["roots"]:
        root = resolve_inside(project_root, root_rel, "text.roots")
        if not root.exists():
            continue

        candidates = [root] if root.is_file() else root.rglob("*")
        for path in candidates:
            if not path.is_file():
                continue

            rel_project = path.relative_to(project_root)
            rel_repo = path.relative_to(repo_root)

            if any(part in settings["exclude_dirs"] for part in rel_project.parts):
                continue
            if rel_repo.as_posix() in settings["exclude_files"]:
                continue
            if settings["include_extensions"] or settings["include_names"]:
                if path.suffix not in settings["include_extensions"] and path.name not in settings["include_names"]:
                    continue

            found.add(path)

    return sorted(found, key=lambda item: item.as_posix())


def replace_file(path: Path, replacements: list[tuple[str, str]], dry_run: bool) -> bool:
    try:
        content = path.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        return False

    updated = content
    for old, new in replacements:
        updated = updated.replace(old, new)

    if updated == content:
        return False

    if not dry_run:
        path.write_text(updated, encoding="utf-8", newline="")
    return True


def cleanup_empty_parents(start: Path, stop: Path, dry_run: bool) -> None:
    current = start
    stop = stop.resolve()

    while current.resolve() != stop and current.exists():
        if any(current.iterdir()):
            break
        if dry_run:
            break
        current.rmdir()
        current = current.parent


def move_path(repo_root: Path, project_root: Path, move: dict[str, Any], dry_run: bool) -> str:
    source = resolve_inside(project_root, move["source"], "move.source")
    target = resolve_inside(project_root, move["target"], "move.target")

    if not source.exists():
        if target.exists():
            return f"已跳过已完成移动：{relative_display(repo_root, target)}"
        if move["required"]:
            raise FileNotFoundError(f"移动源不存在：{relative_display(repo_root, source)}")
        return f"已跳过可选移动：{move['source'].as_posix()}"

    if target.exists():
        raise FileExistsError(f"移动目标已存在：{relative_display(repo_root, target)}")

    if not dry_run:
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.move(str(source), str(target))
        cleanup_empty_parents(source.parent, project_root, dry_run=False)

    return f"已移动：{relative_display(repo_root, source)} -> {relative_display(repo_root, target)}"


def main() -> None:
    args = parse_args()
    repo_root = Path(__file__).resolve().parent.parent
    config_path, config = load_config(repo_root, args.config)
    settings = validate_config(repo_root, config_path, config)

    changed_files: list[str] = []
    for path in iter_text_files(repo_root, settings):
        if replace_file(path, settings["replacements"], args.dry_run):
            changed_files.append(relative_display(repo_root, path))
            prefix = "[预览] " if args.dry_run else ""
            print(f"{prefix}已更新文本：{changed_files[-1]}")

    for move in settings["moves"]:
        prefix = "[预览] " if args.dry_run else ""
        print(f"{prefix}{move_path(repo_root, settings['project_root'], move, args.dry_run)}")

    prefix = "[预览] " if args.dry_run else ""
    print(f"{prefix}适配完成，更新文本文件 {len(changed_files)} 个。")

    build = settings["build"]
    if build.get("command"):
        print(f"建议验证命令：{build['command']}")
    if build.get("jar"):
        print(f"预期可执行 JAR：{build['jar']}")


if __name__ == "__main__":
    main()
