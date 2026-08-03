import tempfile
import unittest
from pathlib import Path

from validate_public_repository import validation_errors


class PublicRepositoryValidatorTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)

    def tearDown(self):
        self.temp.cleanup()

    def write(self, relative: str, content: str = "x") -> Path:
        path = self.root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")
        return path

    def create_valid_repository(self) -> None:
        for relative in (
            "README.md",
            "AGENTS.md",
            "SECURITY.md",
            "backend-java/pom.xml",
            "backend-java/model/src/main/resources/openapi-interface.yaml",
            "frontend/package.json",
            "scripts/api/test_openapi_contract.py",
            "scripts/backend/test_phase1_source.py",
            "scripts/frontend/test_frontend_baseline.py",
        ):
            self.write(relative, "public placeholder")
        self.write("backend-java/server/src/main/java/example/App.java", "class App {}")
        self.write("frontend/src/main.ts", "export {}")

    def test_empty_repository_is_rejected(self):
        errors = validation_errors(self.root)
        self.assertTrue(any("backend-java/pom.xml" in error for error in errors))
        self.assertTrue(any("frontend/package.json" in error for error in errors))

    def test_minimal_public_source_is_accepted(self):
        self.create_valid_repository()
        self.assertEqual(validation_errors(self.root), [])

    def test_database_and_internal_documents_are_rejected(self):
        self.create_valid_repository()
        self.write("backend-java/server/src/main/resources/db/migration/V1__schema.sql", "CREATE TABLE student(id BIGINT);")
        self.write("backend-java/docs/database-dictionary.md", "internal")
        errors = validation_errors(self.root)
        self.assertTrue(any("forbidden path exists" in error for error in errors))
        self.assertTrue(any("forbidden database/data file" in error for error in errors))
        self.assertTrue(any("forbidden internal document" in error for error in errors))

    def test_institution_names_and_tokens_are_rejected(self):
        self.create_valid_repository()
        self.write("frontend/src/leak.ts", "const school = '武汉科技大学'; const token = 'ghp_123456789012345678901234567890';")
        errors = validation_errors(self.root)
        self.assertTrue(any("forbidden institution text" in error for error in errors))
        self.assertTrue(any("possible GitHub token" in error for error in errors))


if __name__ == "__main__":
    unittest.main()
