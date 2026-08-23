#!/usr/bin/env python3
from __future__ import annotations

import subprocess
import tempfile
import unittest
from pathlib import Path


class AndroidSdkManagerMirrorTest(unittest.TestCase):
    def test_sdkmanager_installs_android_36_with_google_download_host_blocked(self) -> None:
        dockerfile = r'''FROM eclipse-temurin:21-jdk-jammy
ARG TOOLS_VERSION=15859902
ARG TOOLS_SHA256=4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583
ENV DEBIAN_FRONTEND=noninteractive \
    ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    SDK_TEST_BASE_URL=https://mirrors.cloud.tencent.com/AndroidSDK/ \
    PATH=/opt/android-sdk/cmdline-tools/latest/bin:/opt/android-sdk/platform-tools:${PATH}
RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl unzip \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /opt/android-sdk/cmdline-tools \
    && curl -fL --retry 3 "https://mirrors.cloud.tencent.com/AndroidSDK/commandlinetools-linux-${TOOLS_VERSION}_latest.zip" -o /tmp/android-cli.zip \
    && echo "${TOOLS_SHA256}  /tmp/android-cli.zip" | sha256sum -c - \
    && unzip -q /tmp/android-cli.zip -d /tmp/android-cli \
    && mv /tmp/android-cli/cmdline-tools /opt/android-sdk/cmdline-tools/latest \
    && rm -rf /tmp/android-cli /tmp/android-cli.zip \
    && yes | sdkmanager --sdk_root=/opt/android-sdk --licenses >/dev/null \
    && sdkmanager --sdk_root=/opt/android-sdk "platforms;android-36" \
    && test -f /opt/android-sdk/platforms/android-36/android.jar
'''
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / "Dockerfile").write_text(dockerfile, encoding="utf-8")
            result = subprocess.run(
                [
                    "docker", "build", "--pull",
                    "--add-host", "dl.google.com:127.0.0.1",
                    "--add-host", "dl-ssl.google.com:127.0.0.1",
                    "-t", "wust-android-sdk-mirror:test", ".",
                ],
                cwd=root,
                text=True,
                capture_output=True,
                check=False,
            )
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)


if __name__ == "__main__":
    unittest.main()
