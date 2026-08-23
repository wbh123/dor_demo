#!/usr/bin/env python3
from __future__ import annotations

import os
import stat
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PREPARE = ROOT / "scripts/ci/prepare_gradle_distribution.sh"


class GradleDistributionMirrorTest(unittest.TestCase):
    def run_prepare(self, *, mirror_fails: bool) -> tuple[subprocess.CompletedProcess[str], list[str]]:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            wrapper = root / "gradle-wrapper.properties"
            wrapper.write_text(
                "distributionUrl=https\\://services.gradle.org/distributions/gradle-8.14.3-all.zip\n",
                encoding="utf-8",
            )
            bin_dir = root / "bin"
            bin_dir.mkdir()
            log = root / "commands.log"
            curl = bin_dir / "curl"
            curl.write_text(
                """#!/usr/bin/env bash
set -euo pipefail
url=""
out=""
while (( $# > 0 )); do
  case "$1" in
    http://*|https://*) url="$1" ;;
    -o) shift; out="$1" ;;
  esac
  shift
done
printf 'curl %s %s\n' "$url" "$out" >> "${COMMAND_LOG}"
if [[ "$url" == *mirrors.aliyun.com* && "${GRADLE_MIRROR_FAILS:-0}" == "1" ]]; then exit 55; fi
mkdir -p "$(dirname "$out")"
printf 'fake zip' > "$out"
""",
                encoding="utf-8",
            )
            curl.chmod(curl.stat().st_mode | stat.S_IXUSR)
            unzip = bin_dir / "unzip"
            unzip.write_text(
                """#!/usr/bin/env bash
set -euo pipefail
destination=""
while (( $# > 0 )); do
  if [[ "$1" == "-d" ]]; then shift; destination="$1"; fi
  shift
done
printf 'unzip %s\n' "$destination" >> "${COMMAND_LOG}"
mkdir -p "$destination/gradle-8.14.3/bin"
printf '#!/usr/bin/env bash\nexit 0\n' > "$destination/gradle-8.14.3/bin/gradle"
chmod +x "$destination/gradle-8.14.3/bin/gradle"
""",
                encoding="utf-8",
            )
            unzip.chmod(unzip.stat().st_mode | stat.S_IXUSR)
            env = os.environ.copy()
            env["PATH"] = f"{bin_dir}:{env['PATH']}"
            env["COMMAND_LOG"] = str(log)
            env["GRADLE_MIRROR_FAILS"] = "1" if mirror_fails else "0"
            env["GRADLE_USER_HOME"] = str(root / "gradle-cache")
            result = subprocess.run(
                ["bash", str(PREPARE), str(wrapper)],
                text=True,
                capture_output=True,
                check=False,
                env=env,
            )
            calls = log.read_text(encoding="utf-8").splitlines() if log.exists() else []
            return result, calls

    def test_aliyun_gradle_distribution_is_preferred(self) -> None:
        result, calls = self.run_prepare(mirror_fails=False)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("gradle-8.14.3/bin/gradle", result.stdout)
        self.assertTrue(any("https://mirrors.aliyun.com/gradle/distributions/v8.14.3/gradle-8.14.3-bin.zip" in call for call in calls))
        self.assertFalse(any("services.gradle.org" in call for call in calls))

    def test_official_gradle_distribution_is_fallback(self) -> None:
        result, calls = self.run_prepare(mirror_fails=True)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        curl_calls = [call for call in calls if call.startswith("curl ")]
        self.assertGreaterEqual(len(curl_calls), 2)
        self.assertIn("mirrors.aliyun.com", curl_calls[0])
        self.assertIn("services.gradle.org", curl_calls[1])

    def test_shell_syntax(self) -> None:
        result = subprocess.run(["bash", "-n", str(PREPARE)], capture_output=True, text=True, check=False)
        self.assertEqual(result.returncode, 0, result.stderr)


if __name__ == "__main__":
    unittest.main()
