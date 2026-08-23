#!/usr/bin/env python3
from __future__ import annotations

import subprocess
import tempfile
import unittest
from pathlib import Path


class ProxyDynamicDnsTest(unittest.TestCase):
    def test_nginx_128_accepts_dynamic_docker_dns_upstream(self) -> None:
        config = """
events {}
http {
    upstream backend_pool {
        zone backend_pool 64k;
        resolver 127.0.0.11 valid=10s ipv6=off;
        resolver_timeout 5s;
        server backend:8080 resolve;
        keepalive 64;
    }
    server {
        listen 80;
        location / {
            proxy_pass http://backend_pool;
            proxy_http_version 1.1;
            proxy_set_header Connection "";
        }
    }
}
""".strip() + "\n"
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "nginx.conf"
            path.write_text(config, encoding="utf-8")
            completed = subprocess.run(
                [
                    "docker", "run", "--rm",
                    "-v", f"{path}:/etc/nginx/nginx.conf:ro",
                    "nginx:1.28-alpine", "nginx", "-t",
                ],
                text=True,
                capture_output=True,
                check=False,
            )
            self.assertEqual(completed.returncode, 0, completed.stdout + completed.stderr)


if __name__ == "__main__":
    unittest.main()
