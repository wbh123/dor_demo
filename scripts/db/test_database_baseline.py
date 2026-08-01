#!/usr/bin/env python3
from __future__ import annotations

import importlib.util
import re
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

import yaml

REPO_ROOT = Path(__file__).resolve().parents[2]


def load_module(name: str, path: Path):
    spec = importlib.util.spec_from_file_location(name, path)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"无法加载模块：{path}")
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
    return module


class DatabaseBaselineTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.generator = load_module(
            "generate_development_data",
            REPO_ROOT / "scripts/db/generate_development_data.py",
        )
        cls.freezer = load_module(
            "build_frozen_baseline",
            REPO_ROOT / "scripts/db/build_frozen_baseline.py",
        )
        cls.migration_dir = (
            REPO_ROOT / "backend-java/server/src/main/resources/db/migration"
        )
        cls.seed_path = (
            REPO_ROOT
            / "backend-java/server/src/test/resources/db/dev-migration"
            / "R__development_test_data.sql"
        )

    def test_student_numbers_gender_counts_and_major_references(self) -> None:
        students = self.generator.build_students()
        self.assertEqual(len(students), 520)
        self.assertEqual(sum(student.gender == "M" for student in students), 260)
        self.assertEqual(sum(student.gender == "F" for student in students), 260)
        numbers = [student.student_number for student in students]
        self.assertEqual(len(set(numbers)), 520)
        self.assertTrue(all(re.fullmatch(r"2026\d{8}", number) for number in numbers))
        self.assertEqual({student.major_id for student in students}, {1, 2, 3, 4, 5})

    def test_current_room_mix_and_bed_capacity(self) -> None:
        buildings = self.generator.build_buildings()
        floors = self.generator.build_floors(buildings)
        rooms = self.generator.build_rooms(buildings, floors)
        beds = self.generator.build_beds(rooms)

        self.assertEqual(len(buildings), 8)
        self.assertEqual(len(floors), 32)
        self.assertEqual(len(rooms), 144)
        self.assertEqual(
            sum(
                room.gender == "M"
                and room.room_type == "FIVE_PERSON"
                and room.capacity == 5
                for room in rooms
            ),
            64,
        )
        self.assertEqual(
            sum(
                room.gender == "F"
                and room.room_type == "FOUR_PERSON"
                and room.capacity == 4
                for room in rooms
            ),
            80,
        )
        self.assertTrue(all(room.gender in {"M", "F"} for room in rooms))

        self.assertEqual(len(beds), 640)
        self.assertEqual(sum(bed.gender == "M" for bed in beds), 320)
        self.assertEqual(sum(bed.gender == "F" for bed in beds), 320)
        self.assertGreaterEqual(320, 260)

    def test_male_and_female_room_layouts(self) -> None:
        buildings = self.generator.build_buildings()
        floors = self.generator.build_floors(buildings)
        rooms = self.generator.build_rooms(buildings, floors)
        beds = self.generator.build_beds(rooms)
        rooms_by_id = {room.id: room for room in rooms}
        beds_by_room: dict[int, list[object]] = {}
        for bed in beds:
            beds_by_room.setdefault(bed.room_id, []).append(bed)

        for room_id, room_beds in beds_by_room.items():
            room = rooms_by_id[room_id]
            if room.gender == "M":
                self.assertEqual(len(room_beds), 5)
                self.assertEqual(
                    sum(bed.bed_type == "LOFT_BED_DESK" for bed in room_beds), 3
                )
                self.assertEqual(
                    sum(bed.bed_type == "BUNK_UPPER" for bed in room_beds), 1
                )
                self.assertEqual(
                    sum(bed.bed_type == "BUNK_LOWER" for bed in room_beds), 1
                )
                bunk_frames = {
                    bed.frame_id
                    for bed in room_beds
                    if bed.bed_type in {"BUNK_UPPER", "BUNK_LOWER"}
                }
                self.assertEqual(len(bunk_frames), 1)
                self.assertNotIn(None, bunk_frames)
            else:
                self.assertEqual(len(room_beds), 4)
                self.assertTrue(
                    all(bed.bed_type == "LOFT_BED_DESK" for bed in room_beds)
                )
                self.assertTrue(all(bed.frame_id is None for bed in room_beds))

    def test_room_model_supports_future_four_and_five_person_mix(self) -> None:
        schema = "\n".join(
            path.read_text(encoding="utf-8")
            for path in self.freezer.discover_migrations(self.migration_dir)
        )
        self.assertIn(
            "room_type IN ('FOUR_PERSON','FIVE_PERSON','SIX_PERSON','OTHER')",
            schema,
        )
        self.assertIn("capacity SMALLINT NOT NULL", schema)
        self.assertIn("gender_restriction IN ('M','F')", schema)
        self.assertIn("CREATE TABLE batch_room_scope", schema)
        self.assertIn("UNIQUE KEY uk_batch_room_scope (batch_id, room_id)", schema)

    def test_student_schema_is_minimal_and_major_is_normalized(self) -> None:
        migration = (
            self.migration_dir / "V3__normalize_major_and_minimize_student.sql"
        ).read_text(encoding="utf-8")
        self.assertIn("CREATE TABLE major", migration)
        self.assertIn("major_code VARCHAR(32) NOT NULL", migration)
        self.assertIn("major_name VARCHAR(128) NOT NULL", migration)
        self.assertIn("ADD COLUMN major_id BIGINT NULL", migration)
        self.assertIn("ADD CONSTRAINT fk_student_major", migration)
        self.assertIn("ADD COLUMN student_id BIGINT NULL", migration)
        self.assertIn("ADD CONSTRAINT fk_app_user_student", migration)
        self.assertIn("DROP TABLE organization", migration)
        for column in (
            "user_id",
            "organization_id",
            "campus_id",
            "grade_year",
            "major_name",
            "class_name",
            "housing_eligibility",
            "profile_status",
            "data_source",
            "version",
        ):
            self.assertRegex(migration, rf"DROP COLUMN {column}\b")

    def test_committed_seed_is_reproducible_and_uses_major_table(self) -> None:
        generated = self.generator.generate_sql()
        committed = self.seed_path.read_text(encoding="utf-8")
        self.assertEqual(generated, committed)
        self.assertIn("INSERT INTO major", committed)
        self.assertNotIn("INSERT INTO organization", committed)
        self.assertNotIn("class_name", committed)
        self.assertNotIn("major_name_value", committed)
        self.assertNotIn("data_source", committed)

    def test_schema_contains_phase1_core_constraints(self) -> None:
        schema = "\n".join(
            path.read_text(encoding="utf-8")
            for path in self.freezer.discover_migrations(self.migration_dir)
        )
        required_tables = {
            "major",
            "app_user",
            "student",
            "campus",
            "dormitory_building",
            "dormitory_floor",
            "room",
            "bed_frame",
            "bed",
            "selection_batch",
            "batch_student_eligibility",
            "questionnaire_version",
            "questionnaire_answer",
            "student_feature",
            "selection_team",
            "selection_team_member",
            "bed_assignment",
            "assignment_history",
            "allocation_run",
            "allocation_run_result",
            "audit_log",
        }
        actual_tables = set(
            re.findall(r"^CREATE TABLE ([a-z_]+) \(", schema, flags=re.MULTILINE)
        )
        self.assertTrue(required_tables.issubset(actual_tables))
        self.assertIn(
            "UNIQUE KEY uk_assignment_batch_student (batch_id, student_id)",
            schema,
        )
        self.assertIn(
            "UNIQUE KEY uk_assignment_batch_bed (batch_id, bed_id)",
            schema,
        )
        self.assertIn(
            "UNIQUE KEY uk_active_team_member (batch_id, student_id, active_marker)",
            schema,
        )
        self.assertIn(
            "CONSTRAINT ck_student_number CHECK (student_number REGEXP '^[0-9]{12}$')",
            schema,
        )

    def test_freezer_builds_versioned_migrations_without_test_data(self) -> None:
        migrations = self.freezer.discover_migrations(self.migration_dir)
        baseline = self.freezer.build_baseline(migrations)
        self.assertIn("V1__create_phase1_schema.sql", baseline)
        self.assertIn("V2__enforce_fixed_room_gender.sql", baseline)
        self.assertIn("V3__normalize_major_and_minimize_student.sql", baseline)
        self.assertNotIn("INSERT INTO student", baseline)
        self.assertNotIn("R__development_test_data.sql", baseline)

        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / "schema.sql"
            output.write_text(baseline, encoding="utf-8")
            self.assertTrue(output.read_text(encoding="utf-8").startswith("-- ==="))

    def test_flyway_configuration_and_dependencies(self) -> None:
        application = yaml.safe_load(
            (
                REPO_ROOT
                / "backend-java/starter/src/main/resources/application.yaml"
            ).read_text(encoding="utf-8")
        )
        flyway = application["spring"]["flyway"]
        self.assertTrue(flyway["enabled"])
        self.assertTrue(flyway["validate-on-migrate"])
        self.assertFalse(flyway["baseline-on-migrate"])
        self.assertTrue(flyway["clean-disabled"])
        self.assertEqual(
            flyway["locations"],
            "${WUST_DORMITORY_FLYWAY_LOCATIONS:classpath:db/migration}",
        )

        pom_path = REPO_ROOT / "backend-java/server/pom.xml"
        ET.parse(pom_path)
        pom = pom_path.read_text(encoding="utf-8")
        self.assertIn("<artifactId>spring-boot-starter-flyway</artifactId>", pom)
        self.assertIn("<artifactId>flyway-mysql</artifactId>", pom)

    def test_mybatis_generator_targets_phase1_tables(self) -> None:
        config_path = (
            REPO_ROOT
            / "backend-java/server/src/main/resources/mybatis-generator"
            / "generatorConfig.xml"
        )
        ET.parse(config_path)
        config = config_path.read_text(encoding="utf-8")
        self.assertNotIn('tableName="example_table"', config)
        for table_name in (
            "major",
            "student",
            "dormitory_building",
            "room",
            "bed",
            "selection_batch",
            "bed_assignment",
            "allocation_run",
        ):
            self.assertIn(f'tableName="{table_name}"', config)

    def test_seed_file_is_development_only(self) -> None:
        self.assertIn(
            "src/test/resources/db/dev-migration",
            self.seed_path.as_posix(),
        )
        application = (
            REPO_ROOT
            / "backend-java/starter/src/main/resources/application.yaml"
        ).read_text(encoding="utf-8")
        self.assertNotIn("db/dev-migration", application)

    def test_freezer_orders_numeric_versions(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            for name in (
                "V10__ten.sql",
                "V2__two.sql",
                "V1_1__one_one.sql",
                "V1__one.sql",
            ):
                (directory / name).write_text(f"-- {name}\n", encoding="utf-8")
            names = [
                path.name for path in self.freezer.discover_migrations(directory)
            ]
            self.assertEqual(
                names,
                [
                    "V1__one.sql",
                    "V1_1__one_one.sql",
                    "V2__two.sql",
                    "V10__ten.sql",
                ],
            )


if __name__ == "__main__":
    unittest.main()
