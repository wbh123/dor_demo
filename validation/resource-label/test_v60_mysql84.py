from __future__ import annotations

import os
import threading
from pathlib import Path

import pymysql
from pymysql.err import IntegrityError, OperationalError

ROOT = Path(__file__).resolve().parent
V60 = ROOT / "V60__add_resource_label_replacement_lifecycle.sql"
DB = os.environ.get("MYSQL_DATABASE", "label_test")
HOST = os.environ.get("MYSQL_HOST", "127.0.0.1")
PORT = int(os.environ.get("MYSQL_PORT", "3306"))
USER = os.environ.get("MYSQL_USER", "root")
PASSWORD = os.environ.get("MYSQL_PASSWORD", "root-test-password")


def connect(*, autocommit: bool = True):
    return pymysql.connect(
        host=HOST,
        port=PORT,
        user=USER,
        password=PASSWORD,
        database=DB,
        charset="utf8mb4",
        autocommit=autocommit,
    )


def execute_script(connection, sql: str) -> None:
    cleaned = "\n".join(line for line in sql.splitlines() if not line.lstrip().startswith("--"))
    with connection.cursor() as cursor:
        for statement in cleaned.split(";"):
            if statement.strip():
                cursor.execute(statement.strip())


def expect_integrity(sql: str, args=None) -> None:
    try:
        with connect() as connection, connection.cursor() as cursor:
            cursor.execute(sql, args)
    except IntegrityError:
        return
    raise AssertionError(f"expected MySQL integrity failure: {sql}")


def scalar(sql: str, args=None):
    with connect() as connection, connection.cursor() as cursor:
        cursor.execute(sql, args)
        row = cursor.fetchone()
        return row[0]


def state(public_code: str) -> str:
    return str(scalar("SELECT label_state FROM resource_label WHERE public_code=%s", (public_code,)))


def setup_schema() -> None:
    with connect() as connection:
        execute_script(
            connection,
            """
            SET FOREIGN_KEY_CHECKS=0;
            DROP TABLE IF EXISTS resource_label_replacement;
            DROP TABLE IF EXISTS resource_label_audit;
            DROP TABLE IF EXISTS resource_label;
            DROP TABLE IF EXISTS resource_label_batch;
            DROP TABLE IF EXISTS app_user;
            SET FOREIGN_KEY_CHECKS=1;

            CREATE TABLE app_user (
                id BIGINT NOT NULL,
                username VARCHAR(64) NOT NULL,
                display_name VARCHAR(128) NULL,
                PRIMARY KEY (id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

            CREATE TABLE resource_label_batch (
                id BIGINT NOT NULL AUTO_INCREMENT,
                batch_name VARCHAR(160) NOT NULL,
                source_type VARCHAR(16) NOT NULL,
                label_count INT NOT NULL DEFAULT 0,
                batch_status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
                created_by BIGINT NULL,
                created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                closed_at DATETIME(3) NULL,
                PRIMARY KEY (id),
                CONSTRAINT fk_resource_label_batch_creator FOREIGN KEY (created_by) REFERENCES app_user(id) ON DELETE SET NULL,
                CONSTRAINT ck_resource_label_batch_source CHECK (source_type IN ('GENERATED','PREPRINTED')),
                CONSTRAINT ck_resource_label_batch_status CHECK (batch_status IN ('ACTIVE','CLOSED')),
                CONSTRAINT ck_resource_label_batch_count CHECK (label_count >= 0)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

            CREATE TABLE resource_label (
                id BIGINT NOT NULL AUTO_INCREMENT,
                public_code CHAR(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
                resource_type VARCHAR(16) NULL,
                resource_id BIGINT NULL,
                label_state VARCHAR(16) NOT NULL DEFAULT 'UNBOUND',
                source_type VARCHAR(16) NOT NULL,
                batch_id BIGINT NULL,
                bound_by BIGINT NULL,
                bound_at DATETIME(3) NULL,
                revoked_by BIGINT NULL,
                revoked_at DATETIME(3) NULL,
                revoke_reason VARCHAR(500) NULL,
                active_resource_marker VARCHAR(96) GENERATED ALWAYS AS (
                    CASE WHEN label_state='BOUND' AND resource_id IS NOT NULL
                         THEN CONCAT(resource_type, ':', CAST(resource_id AS CHAR)) ELSE NULL END
                ) STORED,
                created_by BIGINT NULL,
                created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
                PRIMARY KEY (id),
                UNIQUE KEY uk_resource_label_public_code (public_code),
                UNIQUE KEY uk_resource_label_active_resource (active_resource_marker),
                CONSTRAINT fk_resource_label_batch FOREIGN KEY (batch_id) REFERENCES resource_label_batch(id) ON DELETE SET NULL,
                CONSTRAINT fk_resource_label_bound_by FOREIGN KEY (bound_by) REFERENCES app_user(id) ON DELETE SET NULL,
                CONSTRAINT fk_resource_label_revoked_by FOREIGN KEY (revoked_by) REFERENCES app_user(id) ON DELETE SET NULL,
                CONSTRAINT fk_resource_label_created_by FOREIGN KEY (created_by) REFERENCES app_user(id) ON DELETE SET NULL,
                CONSTRAINT ck_resource_label_code CHECK (public_code REGEXP '^[0-9]{8}$'),
                CONSTRAINT ck_resource_label_type CHECK (resource_type IS NULL OR resource_type IN ('BUILDING','FLOOR','ROOM','BED')),
                CONSTRAINT ck_resource_label_state CHECK (label_state IN ('UNBOUND','BOUND','REVOKED')),
                CONSTRAINT ck_resource_label_source CHECK (source_type IN ('GENERATED','PREPRINTED')),
                CONSTRAINT ck_resource_label_binding CHECK (
                    (label_state='UNBOUND' AND resource_type IS NULL AND resource_id IS NULL)
                    OR (label_state='BOUND' AND resource_type IS NOT NULL AND resource_id IS NOT NULL)
                    OR label_state='REVOKED'
                )
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

            CREATE TABLE resource_label_audit (
                id BIGINT NOT NULL AUTO_INCREMENT,
                label_id BIGINT NOT NULL,
                action_type VARCHAR(24) NOT NULL,
                previous_resource_type VARCHAR(16) NULL,
                previous_resource_id BIGINT NULL,
                next_resource_type VARCHAR(16) NULL,
                next_resource_id BIGINT NULL,
                operator_user_id BIGINT NULL,
                reason VARCHAR(500) NULL,
                occurred_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                PRIMARY KEY (id),
                CONSTRAINT fk_resource_label_audit_label FOREIGN KEY (label_id) REFERENCES resource_label(id) ON DELETE RESTRICT,
                CONSTRAINT fk_resource_label_audit_operator FOREIGN KEY (operator_user_id) REFERENCES app_user(id) ON DELETE SET NULL,
                CONSTRAINT ck_resource_label_audit_action CHECK (action_type IN ('GENERATE','BIND','REBIND','REVOKE','PRINT'))
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
            """,
        )
        execute_script(connection, V60.read_text(encoding="utf-8"))
        with connection.cursor() as cursor:
            cursor.execute("INSERT INTO app_user(id, username, display_name) VALUES (9, 'label-admin', 'Label Admin')")


def insert_label(label_id: int, code: str, state_name: str, resource_id: int | None, source: str = "PREPRINTED") -> None:
    resource_type = "ROOM" if resource_id is not None else None
    bound_by = 9 if state_name == "BOUND" else None
    with connect() as connection, connection.cursor() as cursor:
        cursor.execute(
            """
            INSERT INTO resource_label
            (id, public_code, resource_type, resource_id, label_state, source_type, bound_by, bound_at, created_by)
            VALUES (%s,%s,%s,%s,%s,%s,%s,IF(%s='BOUND',CURRENT_TIMESTAMP(3),NULL),9)
            """,
            (label_id, code, resource_type, resource_id, state_name, source, bound_by, state_name),
        )


def test_schema_contracts() -> None:
    insert_label(1, "10000001", "BOUND", 305, "GENERATED")
    insert_label(2, "20000002", "UNBOUND", None)
    insert_label(3, "30000003", "UNBOUND", None)

    with connect() as connection, connection.cursor() as cursor:
        cursor.execute(
            "INSERT INTO resource_label_audit(label_id, action_type, operator_user_id, reason) VALUES (1,'REPLACE',9,'replacement')"
        )

    expect_integrity("INSERT INTO resource_label_audit(label_id, action_type) VALUES (1,'INVALID')")
    expect_integrity("UPDATE resource_label SET label_state='REPLACED' WHERE id=1")
    expect_integrity(
        "INSERT INTO resource_label_replacement(old_label_id,new_label_id,reason_code,operator_user_id) VALUES (1,2,'INVALID',9)"
    )
    expect_integrity(
        "INSERT INTO resource_label_replacement(old_label_id,new_label_id,reason_code,operator_user_id) VALUES (1,1,'DAMAGED',9)"
    )

    with connect() as connection, connection.cursor() as cursor:
        cursor.execute(
            "INSERT INTO resource_label_replacement(old_label_id,new_label_id,reason_code,operator_user_id) VALUES (1,2,'DAMAGED',9)"
        )

    expect_integrity(
        "INSERT INTO resource_label_replacement(old_label_id,new_label_id,reason_code,operator_user_id) VALUES (1,3,'LOST',9)"
    )
    expect_integrity(
        "INSERT INTO resource_label_replacement(old_label_id,new_label_id,reason_code,operator_user_id) VALUES (3,2,'OUTDATED',9)"
    )
    expect_integrity("DELETE FROM resource_label WHERE id=1")
    expect_integrity("DELETE FROM resource_label WHERE id=2")

    with connect() as connection, connection.cursor() as cursor:
        cursor.execute("DELETE FROM app_user WHERE id=9")
        cursor.execute("SELECT operator_user_id FROM resource_label_replacement WHERE old_label_id=1")
        assert cursor.fetchone()[0] is None, "operator FK must SET NULL without deleting history"
        cursor.execute("INSERT INTO app_user(id, username, display_name) VALUES (9,'label-admin','Label Admin')")


def path_exists(from_label_id: int, target_label_id: int) -> bool:
    # Exact SQL shape used by ResourceLabelMapper.replacementPathExists.
    sql = """
        WITH RECURSIVE replacement_chain AS (
            SELECT old_label_id, new_label_id
            FROM resource_label_replacement
            WHERE old_label_id=%s
            UNION DISTINCT
            SELECT relation.old_label_id, relation.new_label_id
            FROM resource_label_replacement relation
            JOIN replacement_chain chain_row ON relation.old_label_id=chain_row.new_label_id
        )
        SELECT EXISTS(
            SELECT 1 FROM replacement_chain WHERE new_label_id=%s
        )
    """
    return bool(scalar(sql, (from_label_id, target_label_id)))


def test_recursive_guard_and_corrupt_cycle_termination() -> None:
    with connect() as connection, connection.cursor() as cursor:
        cursor.execute("DELETE FROM resource_label_replacement")
    for label_id, code in [(11, "11000011"), (12, "12000012"), (13, "13000013")]:
        insert_label(label_id, code, "UNBOUND", None)
    with connect() as connection, connection.cursor() as cursor:
        cursor.execute(
            "INSERT INTO resource_label_replacement(old_label_id,new_label_id,reason_code,operator_user_id) VALUES (11,12,'OUTDATED',9)"
        )
        cursor.execute(
            "INSERT INTO resource_label_replacement(old_label_id,new_label_id,reason_code,operator_user_id) VALUES (12,13,'OUTDATED',9)"
        )
    assert path_exists(11, 13), "A->B->C must be discoverable"
    assert not path_exists(13, 11), "reverse path must not exist before a cycle"
    # A proposed C->A replacement must be rejected by the service because A already reaches C.
    assert path_exists(11, 13), "cycle-prevention query must detect proposed C->A"

    # Simulate legacy/manual corruption and prove UNION DISTINCT terminates instead of recursing forever.
    with connect() as connection, connection.cursor() as cursor:
        cursor.execute("DELETE FROM resource_label_replacement")
        cursor.execute(
            "INSERT INTO resource_label_replacement(old_label_id,new_label_id,reason_code,operator_user_id) VALUES (11,12,'OUTDATED',9)"
        )
        cursor.execute(
            "INSERT INTO resource_label_replacement(old_label_id,new_label_id,reason_code,operator_user_id) VALUES (12,11,'OUTDATED',9)"
        )
    assert path_exists(11, 12)
    assert path_exists(12, 11)


def test_atomic_success_and_failure_rollback() -> None:
    with connect() as connection, connection.cursor() as cursor:
        cursor.execute("DELETE FROM resource_label_replacement")
    insert_label(21, "21000021", "BOUND", 405, "GENERATED")
    insert_label(22, "22000022", "UNBOUND", None)
    insert_label(23, "23000023", "UNBOUND", None)
    with connect() as connection, connection.cursor() as cursor:
        cursor.execute(
            "INSERT INTO resource_label_replacement(old_label_id,new_label_id,reason_code,operator_user_id) VALUES (23,22,'OUTDATED',9)"
        )

    connection = connect(autocommit=False)
    try:
        with connection.cursor() as cursor:
            cursor.execute(
                "SELECT id FROM resource_label WHERE public_code IN (%s,%s) ORDER BY public_code FOR UPDATE",
                ("21000021", "22000022"),
            )
            cursor.fetchall()
            cursor.execute(
                "UPDATE resource_label SET label_state='REVOKED', revoked_by=9, revoked_at=CURRENT_TIMESTAMP(3), revoke_reason='damaged' WHERE public_code=%s AND label_state='BOUND'",
                ("21000021",),
            )
            assert cursor.rowcount == 1
            cursor.execute(
                "UPDATE resource_label SET resource_type='ROOM',resource_id=405,label_state='BOUND',bound_by=9,bound_at=CURRENT_TIMESTAMP(3) WHERE public_code=%s AND label_state='UNBOUND'",
                ("22000022",),
            )
            assert cursor.rowcount == 1, "old REVOKED marker must free the active resource uniqueness slot"
            try:
                cursor.execute(
                    "INSERT INTO resource_label_replacement(old_label_id,new_label_id,reason_code,operator_user_id) VALUES (21,22,'DAMAGED',9)"
                )
            except IntegrityError:
                connection.rollback()
            else:
                raise AssertionError("occupied new-label relation must reject replacement")
    finally:
        connection.close()

    assert state("21000021") == "BOUND", "old revoke must roll back"
    assert state("22000022") == "UNBOUND", "new bind must roll back"
    assert scalar("SELECT COUNT(*) FROM resource_label_replacement") == 1

    with connect() as connection, connection.cursor() as cursor:
        cursor.execute("DELETE FROM resource_label_replacement")
    insert_label(24, "24000024", "BOUND", 406, "GENERATED")
    insert_label(25, "25000025", "UNBOUND", None)
    connection = connect(autocommit=False)
    try:
        with connection.cursor() as cursor:
            cursor.execute(
                "SELECT id FROM resource_label WHERE public_code IN (%s,%s) ORDER BY public_code FOR UPDATE",
                ("24000024", "25000025"),
            )
            cursor.fetchall()
            cursor.execute(
                "UPDATE resource_label SET label_state='REVOKED',revoked_by=9,revoked_at=CURRENT_TIMESTAMP(3),revoke_reason='damaged' WHERE id=24 AND label_state='BOUND'"
            )
            cursor.execute(
                "UPDATE resource_label SET resource_type='ROOM',resource_id=406,label_state='BOUND',bound_by=9,bound_at=CURRENT_TIMESTAMP(3) WHERE id=25 AND label_state='UNBOUND'"
            )
            cursor.execute(
                "INSERT INTO resource_label_replacement(old_label_id,new_label_id,reason_code,operator_user_id) VALUES (24,25,'DAMAGED',9)"
            )
        connection.commit()
    finally:
        connection.close()
    assert state("24000024") == "REVOKED"
    assert state("25000025") == "BOUND"
    assert scalar("SELECT COUNT(*) FROM resource_label_replacement WHERE old_label_id=24 AND new_label_id=25") == 1


def test_concurrent_duplicate_replacement() -> None:
    with connect() as connection, connection.cursor() as cursor:
        cursor.execute("DELETE FROM resource_label_replacement")
    insert_label(31, "31000031", "BOUND", 501, "GENERATED")
    insert_label(32, "32000032", "UNBOUND", None)

    barrier = threading.Barrier(2)
    committed = []
    rejected = []

    def worker() -> None:
        connection = connect(autocommit=False)
        try:
            barrier.wait(timeout=10)
            with connection.cursor() as cursor:
                cursor.execute(
                    "SELECT id FROM resource_label WHERE public_code IN (%s,%s) ORDER BY public_code FOR UPDATE",
                    ("31000031", "32000032"),
                )
                cursor.fetchall()
                cursor.execute("SELECT COUNT(*) FROM resource_label_replacement WHERE old_label_id=31")
                if cursor.fetchone()[0]:
                    connection.rollback()
                    rejected.append(1)
                    return
                cursor.execute(
                    "INSERT INTO resource_label_replacement(old_label_id,new_label_id,reason_code,operator_user_id) VALUES (31,32,'DAMAGED',9)"
                )
                connection.commit()
                committed.append(1)
        except (IntegrityError, OperationalError):
            connection.rollback()
            rejected.append(1)
        finally:
            connection.close()

    threads = [threading.Thread(target=worker), threading.Thread(target=worker)]
    for thread in threads:
        thread.start()
    for thread in threads:
        thread.join(timeout=20)
        assert not thread.is_alive(), "concurrency validation thread timed out"

    assert len(committed) == 1, f"exactly one replacement must commit, got {len(committed)}"
    assert len(rejected) == 1, f"exactly one replacement must be rejected, got {len(rejected)}"
    assert scalar("SELECT COUNT(*) FROM resource_label_replacement WHERE old_label_id=31") == 1


def main() -> None:
    setup_schema()
    test_schema_contracts()
    test_recursive_guard_and_corrupt_cycle_termination()
    test_atomic_success_and_failure_rollback()
    test_concurrent_duplicate_replacement()
    print("[PASS] V60 resource-label replacement lifecycle validated on MySQL 8.4")


if __name__ == "__main__":
    main()
