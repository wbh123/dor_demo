from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]
OPENAPI_DIR = ROOT / "backend-java/model/src/main/resources"
RELATIVE_YAML_REF_PATTERN = re.compile(
    r"\$ref:\s*['\"]?([^'\"\s#]+\.ya?ml)(?:#[^'\"\s]*)?['\"]?"
)


class OpenApiRelativeReferenceIntegrityTest(unittest.TestCase):
    def test_all_relative_yaml_refs_point_to_existing_files(self):
        missing = []
        for spec in OPENAPI_DIR.rglob("*.yaml"):
            source = spec.read_text(encoding="utf-8")
            for ref in RELATIVE_YAML_REF_PATTERN.findall(source):
                if "://" in ref:
                    continue
                target = (spec.parent / ref).resolve()
                if not target.is_file():
                    missing.append(f"{spec.relative_to(ROOT)} -> {ref}")

        self.assertEqual(
            [],
            sorted(missing),
            "OpenAPI存在指向缺失文件的相对引用：\n" + "\n".join(sorted(missing)),
        )


if __name__ == "__main__":
    unittest.main()
