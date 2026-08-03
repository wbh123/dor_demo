#!/usr/bin/env python3
from __future__ import annotations

from dataclasses import dataclass

DEFAULT_SCENARIO = "realistic"
VALID_SCENARIOS = ("clean", "realistic")


@dataclass(frozen=True)
class ResetScenario:
    name: str
    source_file: str
    ready_marker: str
    includes_operational_state: bool


_SCENARIOS = {
    "clean": ResetScenario(
        name="clean",
        source_file="1000_students_clean.sql",
        ready_marker="CLEAN_1000_READY",
        includes_operational_state=False,
    ),
    "realistic": ResetScenario(
        name="realistic",
        source_file="1000_students_realistic_mixed_state.sql",
        ready_marker="REALISTIC_1000_READY",
        includes_operational_state=True,
    ),
}


def resolve_scenario(value: str | None) -> ResetScenario:
    normalized = (value or DEFAULT_SCENARIO).strip().lower()
    try:
        return _SCENARIOS[normalized]
    except KeyError as exception:
        allowed = ", ".join(VALID_SCENARIOS)
        raise ValueError(f"不支持的数据场景：{value!r}，只能选择 {allowed}") from exception
