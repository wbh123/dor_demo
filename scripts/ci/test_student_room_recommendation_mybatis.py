#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MAPPER = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/student/mapper/StudentRoomRecommendationMapper.java"
XML = ROOT / "backend-java/server/src/main/resources/mapper/student/StudentRoomRecommendationMapper.xml"
SERVICE = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/student/StudentRoomRecommendationService.java"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def main() -> None:
    require(MAPPER.exists(), "StudentRoomRecommendationMapper.java missing")
    require(XML.exists(), "StudentRoomRecommendationMapper.xml missing")

    mapper = MAPPER.read_text(encoding="utf-8")
    xml = XML.read_text(encoding="utf-8")
    service = SERVICE.read_text(encoding="utf-8")
    normalized = " ".join(xml.upper().split())

    require("namespace=\"com.wust.dormitory.student.mapper.StudentRoomRecommendationMapper\"" in xml,
            "recommendation mapper namespace mismatch")
    for method in (
            "isBatchAccessible",
            "findBatchFeature",
            "findCandidateRooms",
            "findRoommateFeatures",
            "findAvailableBedTypes",
            "findAvailableBeds",
    ):
        require(method in mapper, f"mapper method missing: {method}")
        require(f'id="{method}"' in xml, f"mapper XML statement missing: {method}")

    require("WITH SCOPED_ROOMS AS" in normalized, "candidate query must build scoped rooms once")
    require("WITH SCOPED_BEDS AS" in normalized, "bed query must build scoped beds once")
    require("RESIDENT_STATS AS" in normalized, "candidate query must pre-aggregate resident statistics")
    require("FROM SCOPED_ROOMS SCOPED JOIN ROOM_ASSIGNMENT ASSIGNMENT" in normalized,
            "resident statistics must be restricted to current scoped rooms")
    require("LEFT JOIN ROOM_ASSIGNMENT OCCUPIED" in normalized,
            "bed availability must anti-join room_assignment directly")
    require("OCCUPIED.ROOM_ID = BED.ROOM_ID" in normalized,
            "occupied-bed anti-join must lead with room_id to match the residency composite index")
    require("OCCUPIED.ASSIGNMENT_STATUS = 'ACTIVE'" in normalized,
            "occupied-bed anti-join must filter active residencies in the join")
    require("OCCUPIED.BED_ID = BED.ID" in normalized,
            "occupied-bed anti-join must match bed_id after room/status")
    require("OCCUPIED_BEDS AS" not in normalized,
            "do not scan all active occupied beds into a global CTE")
    require("GROUP BY BED.ROOM_ID, BED.BED_TYPE" in normalized, "bed type counts must be grouped for all rooms")
    require("ASSIGNMENT.ROOM_ID IN" in normalized, "roommate features must be fetched in one room-id batch")
    require("BED.ROOM_ID IN" in normalized, "bed type counts must be fetched in one room-id batch")
    require("<FOREACH COLLECTION=\"ROOMIDS\"" in normalized, "room-id batch queries must use MyBatis foreach")

    require("StudentRoomRecommendationMapper" in service, "service must depend on recommendation mapper")
    require("policy.room(" not in service, "recommendation loop must not re-read each room")
    require("policy.activeResidentCount(" not in service, "recommendation loop must not count residents per room")
    require("policy.unknownBedResidentCount(" not in service, "recommendation loop must not count unknown beds per room")
    require("policy.availableBedCount(" not in service, "recommendation loop must not count available beds per room")
    require("policy.availableCapacity(" not in service, "recommendation loop must not calculate capacity with database reads")

    print("Student room recommendation MyBatis batching contract: OK")


if __name__ == "__main__":
    main()
