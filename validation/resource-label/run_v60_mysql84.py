from __future__ import annotations

import importlib.util
from pathlib import Path

from pymysql.err import IntegrityError, OperationalError

MODULE_PATH = Path(__file__).with_name("test_v60_mysql84.py")
spec = importlib.util.spec_from_file_location("resource_label_v60_validation", MODULE_PATH)
if spec is None or spec.loader is None:
    raise RuntimeError(f"cannot load validation module: {MODULE_PATH}")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

EXPECTED_CONSTRAINT_CODES = {1062, 1451, 1452, 3819}


def expect_constraint_failure(sql: str, args=None) -> None:
    try:
        with module.connect() as connection, connection.cursor() as cursor:
            cursor.execute(sql, args)
    except (IntegrityError, OperationalError) as exc:
        code = int(exc.args[0]) if exc.args else -1
        if code in EXPECTED_CONSTRAINT_CODES:
            return
        raise
    raise AssertionError(f"expected MySQL constraint failure: {sql}")


module.expect_integrity = expect_constraint_failure
module.main()
