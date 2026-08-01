#!/usr/bin/env python3
from __future__ import annotations

import re
import unittest
from pathlib import Path
from urllib.parse import unquote

import yaml

REPO_ROOT = Path(__file__).resolve().parents[2]
MODEL_RESOURCES = REPO_ROOT / "backend-java/model/src/main/resources"
SERVER_JAVA = REPO_ROOT / "backend-java/server/src/main/java"
MASTER_SPEC = MODEL_RESOURCES / "openapi-interface.yaml"


def resolve_pointer(document: object, fragment: str) -> object:
    if not fragment or fragment == "#":
        return document
    if not fragment.startswith("#/"):
        raise AssertionError(f"不支持的JSON Pointer：{fragment}")
    current = document
    for raw_part in fragment[2:].split("/"):
        part = unquote(raw_part).replace("~1", "/").replace("~0", "~")
        if not isinstance(current, dict) or part not in current:
            raise AssertionError(f"引用片段不存在：{fragment}，缺失：{part}")
        current = current[part]
    return current


class OpenApiContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.master = yaml.safe_load(MASTER_SPEC.read_text(encoding="utf-8"))

    def test_master_spec_contains_phase1_domains(self) -> None:
        paths = self.master["paths"]
        self.assertGreaterEqual(len(paths), 35)
        for prefix in (
            "/api/v1/auth/",
            "/api/v1/admin/",
            "/api/v1/student/",
            "/api/v1/realtime/",
        ):
            self.assertTrue(any(path.startswith(prefix) for path in paths), prefix)

    def test_all_master_references_resolve(self) -> None:
        for path, item in self.master["paths"].items():
            reference = item.get("$ref")
            self.assertIsNotNone(reference, f"主契约路径必须使用$ref：{path}")
            file_part, fragment = reference.split("#", 1)
            target = (MASTER_SPEC.parent / file_part).resolve()
            self.assertTrue(target.is_file(), f"契约文件不存在：{target}")
            document = yaml.safe_load(target.read_text(encoding="utf-8"))
            resolve_pointer(document, "#" + fragment)

    def test_operation_ids_are_unique(self) -> None:
        operation_ids: list[str] = []
        for item in self.master["paths"].values():
            file_part, fragment = item["$ref"].split("#", 1)
            target = MASTER_SPEC.parent / file_part
            document = yaml.safe_load(target.read_text(encoding="utf-8"))
            path_item = resolve_pointer(document, "#" + fragment)
            for method, operation in path_item.items():
                if method.lower() in {"get", "post", "put", "patch", "delete"}:
                    operation_ids.append(operation["operationId"])
        self.assertEqual(len(operation_ids), len(set(operation_ids)))

    def test_controllers_only_implement_generated_interfaces(self) -> None:
        expectations = {
            "auth/AuthController.java": "implements AuthApi",
            "admin/AdminController.java": "implements AdminApi",
            "student/StudentController.java": "implements StudentApi",
            "realtime/RealtimeController.java": "implements RealtimeApi",
        }
        forbidden = re.compile(
            r"@(GetMapping|PostMapping|PutMapping|PatchMapping|DeleteMapping|RequestMapping)"
        )
        for relative, expected in expectations.items():
            path = SERVER_JAVA / "com/wust/dormitory" / relative
            content = path.read_text(encoding="utf-8")
            self.assertIn(expected, content)
            self.assertNotRegex(content, forbidden)
            self.assertNotRegex(content, r"public\s+record\s+\w+Request")
            self.assertIn("com.wust.dormitory.model.api", content)

    def test_example_contract_and_controller_are_removed(self) -> None:
        self.assertFalse((MODEL_RESOURCES / "example/example.yaml").exists())
        self.assertFalse(
            (
                SERVER_JAVA
                / "com/wust/dormitory/controller/ExampleController.java"
            ).exists()
        )

    def test_no_handwritten_public_api_response_model(self) -> None:
        self.assertFalse(
            (
                SERVER_JAVA
                / "com/wust/dormitory/common/api/ApiResponse.java"
            ).exists()
        )
        handler = (
            SERVER_JAVA
            / "com/wust/dormitory/common/error/GlobalExceptionHandler.java"
        ).read_text(encoding="utf-8")
        self.assertIn("com.wust.dormitory.model.dto.ErrorResponse", handler)

    def test_model_generator_is_interface_only(self) -> None:
        pom = (REPO_ROOT / "backend-java/model/pom.xml").read_text(encoding="utf-8")
        self.assertIn("<interfaceOnly>true</interfaceOnly>", pom)
        self.assertIn("<useTags>true</useTags>", pom)
        self.assertIn("<requestMappingMode>none</requestMappingMode>", pom)
        self.assertIn("<skipDefaultInterface>true</skipDefaultInterface>", pom)


if __name__ == "__main__":
    unittest.main()
