#!/usr/bin/env python3
from __future__ import annotations

import urllib.request
import unittest


class AndroidSdkMirrorTest(unittest.TestCase):
    def assert_reachable(self, url: str) -> None:
        request = urllib.request.Request(url, method="HEAD", headers={"User-Agent": "wust-deploy-ci/1.0"})
        with urllib.request.urlopen(request, timeout=30) as response:
            self.assertLess(response.status, 400, url)

    def test_tencent_android_repository_metadata_is_reachable(self) -> None:
        self.assert_reachable("https://mirrors.cloud.tencent.com/AndroidSDK/repository2-1.xml")

    def test_tencent_current_command_line_tools_is_reachable(self) -> None:
        self.assert_reachable(
            "https://mirrors.cloud.tencent.com/AndroidSDK/commandlinetools-linux-15859902_latest.zip"
        )


if __name__ == "__main__":
    unittest.main()
