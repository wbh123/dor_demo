#!/usr/bin/env python3
from pathlib import Path
import re

path = Path("backend-java/server/src/main/java/com/wust/dormitory/admin/RoomManagementService.java")
text = path.read_text(encoding="utf-8")

import_anchor = "import com.wust.dormitory.audit.AuditService;"
import_replacement = """import com.wust.dormitory.admin.mapper.RoomCatalogMapper;
import com.wust.dormitory.admin.model.persistence.RoomCatalogRow;
import com.wust.dormitory.audit.AuditService;"""
if import_anchor not in text:
    raise RuntimeError("RoomManagementService import anchor not found")
text = text.replace(import_anchor, import_replacement, 1)

constructor_old = """    private final NamedParameterJdbcTemplate jdbc;
    private final AuditService auditService;

    public RoomManagementService(
            NamedParameterJdbcTemplate jdbc,
            AuditService auditService) {
        this.jdbc = jdbc;
        this.auditService = auditService;
    }
"""
constructor_new = """    private final NamedParameterJdbcTemplate jdbc;
    private final AuditService auditService;
    private final RoomCatalogMapper roomCatalogMapper;

    public RoomManagementService(
            NamedParameterJdbcTemplate jdbc,
            AuditService auditService,
            RoomCatalogMapper roomCatalogMapper) {
        this.jdbc = jdbc;
        this.auditService = auditService;
        this.roomCatalogMapper = roomCatalogMapper;
    }
"""
if constructor_old not in text:
    raise RuntimeError("RoomManagementService constructor anchor not found")
text = text.replace(constructor_old, constructor_new, 1)

pattern = re.compile(
    r"    public List<Map<String, Object>> rooms\(Long buildingId, String gender\) \{.*?"
    r"\n    \}\n\n    @Transactional\n    public long createBuilding",
    re.DOTALL,
)
replacement = """    public List<Map<String, Object>> rooms(Long buildingId, String gender) {
        return roomCatalogMapper.findRooms(buildingId, gender).stream()
                .map(RoomCatalogRow::asResponseMap)
                .toList();
    }

    @Transactional
    public long createBuilding"""
text, count = pattern.subn(replacement, text, count=1)
if count != 1:
    raise RuntimeError(f"expected one rooms method replacement, got {count}")

rooms_start = text.index("public List<Map<String, Object>> rooms")
rooms_end = text.index("@Transactional", rooms_start)
rooms_method = text[rooms_start:rooms_end]
if "jdbc." in rooms_method or "SELECT " in rooms_method:
    raise RuntimeError("rooms method still accesses JDBC or embedded SQL")
if "public long createBuilding" not in text or "public long createRoom" not in text:
    raise RuntimeError("advanced public room resource operations were lost")

path.write_text(text, encoding="utf-8")
print("RoomManagementService room catalog query migrated to MyBatis")
