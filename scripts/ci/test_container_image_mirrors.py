#!/usr/bin/env python3
from __future__ import annotations

import os
import stat
import subprocess
import tempfile
import unittest
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PULL = ROOT / "scripts/ci/pull_image_with_fallback.sh"

IMAGES = (
    "m.daocloud.io/docker.io/library/node:22-bookworm",
    "m.daocloud.io/docker.io/library/maven:3.9.11-eclipse-temurin-21",
    "m.daocloud.io/docker.io/library/eclipse-temurin:21-jre-jammy",
    "m.daocloud.io/docker.io/library/mysql:8.4",
    "m.daocloud.io/docker.io/library/redis:7.4-alpine",
    "m.daocloud.io/docker.io/minio/minio:RELEASE.2025-09-07T16-13-09Z",
    "m.daocloud.io/docker.io/minio/mc:RELEASE.2025-08-13T08-35-41Z",
    "m.daocloud.io/docker.io/library/nginx:1.28-alpine",
)


def inspect_manifest(image: str) -> tuple[str, int, str]:
    try:
        result = subprocess.run(
            ["docker", "manifest", "inspect", image],
            text=True,
            capture_output=True,
            check=False,
            timeout=60,
        )
        return image, result.returncode, result.stdout + result.stderr
    except subprocess.TimeoutExpired as exc:
        return image, 124, f"timeout after 60s: {exc}"


def fake_docker(root: Path, *, mirror_fails: bool) -> tuple[dict[str, str], Path]:
    bin_dir = root / "bin"
    bin_dir.mkdir()
    log = root / "docker.log"
    fake = bin_dir / "docker"
    fake.write_text(
        """#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >> "${DOCKER_LOG}"
if [[ "$1 $2" == "image inspect" ]]; then exit 1; fi
if [[ "$1" == "pull" && "$2" == m.daocloud.io/* && "${MIRROR_FAILS:-0}" == "1" ]]; then exit 1; fi
exit 0
""",
        encoding="utf-8",
    )
    fake.chmod(fake.stat().st_mode | stat.S_IXUSR)
    env = os.environ.copy()
    env["PATH"] = f"{bin_dir}:{env['PATH']}"
    env["DOCKER_LOG"] = str(log)
    env["MIRROR_FAILS"] = "1" if mirror_fails else "0"
    return env, log


class ContainerMirrorTest(unittest.TestCase):
    def test_all_required_mirror_tags_have_manifests(self) -> None:
        failures: list[str] = []
        with ThreadPoolExecutor(max_workers=len(IMAGES)) as executor:
            futures = [executor.submit(inspect_manifest, image) for image in IMAGES]
            for future in as_completed(futures):
                image, code, output = future.result()
                if code != 0:
                    failures.append(f"{image}: exit={code}\n{output}")
        self.assertFalse(failures, "\n\n".join(failures))

    def test_fallback_pulls_official_and_tags_preferred_name(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            env, log = fake_docker(root, mirror_fails=True)
            mirror = "m.daocloud.io/docker.io/library/mysql:8.4"
            official = "docker.io/library/mysql:8.4"
            result = subprocess.run(
                ["bash", str(PULL), mirror],
                text=True,
                capture_output=True,
                check=False,
                env=env,
            )
            self.assertEqual(result.returncode, 0, result.stderr)
            calls = log.read_text(encoding="utf-8").splitlines()
            self.assertIn(f"pull {mirror}", calls)
            self.assertIn(f"pull {official}", calls)
            self.assertIn(f"tag {official} {mirror}", calls)

    def test_legacy_docker_hub_name_prefers_mainland_mirror_without_env_change(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            env, log = fake_docker(root, mirror_fails=False)
            legacy = "mysql:8.4"
            mirror = "m.daocloud.io/docker.io/library/mysql:8.4"
            result = subprocess.run(
                ["bash", str(PULL), legacy],
                text=True,
                capture_output=True,
                check=False,
                env=env,
            )
            self.assertEqual(result.returncode, 0, result.stderr)
            calls = log.read_text(encoding="utf-8").splitlines()
            self.assertIn(f"pull {mirror}", calls)
            self.assertIn(f"tag {mirror} {legacy}", calls)
            self.assertFalse(any(call == f"pull {legacy}" for call in calls))


if __name__ == "__main__":
    unittest.main()
