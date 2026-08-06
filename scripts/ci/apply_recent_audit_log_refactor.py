#!/usr/bin/env python3
from pathlib import Path
import re

controller_path = Path("backend-java/server/src/main/java/com/wust/dormitory/admin/AdminController.java")
controller = controller_path.read_text(encoding="utf-8")

import_anchor = "import com.wust.dormitory.allocation.AssignmentQueryService;"
import_replacement = """import com.wust.dormitory.allocation.AssignmentQueryService;
import com.wust.dormitory.audit.RecentAuditLogQueryService;"""
if import_anchor not in controller:
    raise RuntimeError("AdminController audit import anchor not found")
controller = controller.replace(import_anchor, import_replacement, 1)

field_anchor = "    private final AssignmentExportService exportService;"
field_replacement = """    private final AssignmentExportService exportService;
    private final RecentAuditLogQueryService recentAuditLogQueryService;"""
if field_anchor not in controller:
    raise RuntimeError("AdminController field anchor not found")
controller = controller.replace(field_anchor, field_replacement, 1)

constructor_anchor = """            AssignmentQueryService assignmentQueryService,
            AssignmentAdjustmentService adjustmentService,
            AssignmentExportService exportService) {"""
constructor_replacement = """            AssignmentQueryService assignmentQueryService,
            AssignmentAdjustmentService adjustmentService,
            AssignmentExportService exportService,
            RecentAuditLogQueryService recentAuditLogQueryService) {"""
if constructor_anchor not in controller:
    raise RuntimeError("AdminController constructor anchor not found")
controller = controller.replace(constructor_anchor, constructor_replacement, 1)

assignment_anchor = """        this.adjustmentService = adjustmentService;
        this.exportService = exportService;
    }"""
assignment_replacement = """        this.adjustmentService = adjustmentService;
        this.exportService = exportService;
        this.recentAuditLogQueryService = recentAuditLogQueryService;
    }"""
if assignment_anchor not in controller:
    raise RuntimeError("AdminController assignment anchor not found")
controller = controller.replace(assignment_anchor, assignment_replacement, 1)

endpoint_old = """        return ResponseEntity.ok(ResponseFactory.list(
                adminService.auditLogs(limit == null ? 100 : limit)));"""
endpoint_new = """        return ResponseEntity.ok(ResponseFactory.list(
                recentAuditLogQueryService.list(limit == null ? 100 : limit)));"""
if endpoint_old not in controller:
    raise RuntimeError("AdminController audit endpoint anchor not found")
controller = controller.replace(endpoint_old, endpoint_new, 1)
controller_path.write_text(controller, encoding="utf-8")

service_path = Path("backend-java/server/src/main/java/com/wust/dormitory/admin/AdminService.java")
service = service_path.read_text(encoding="utf-8")
audit_method = re.compile(
    r"\n    public List<Map<String, Object>> auditLogs\(int limit\) \{.*?\n    \}\n",
    re.DOTALL,
)
service, count = audit_method.subn("\n", service, count=1)
if count != 1:
    raise RuntimeError(f"expected one AdminService.auditLogs removal, got {count}")
service_path.write_text(service, encoding="utf-8")

if "adminService.auditLogs" in controller:
    raise RuntimeError("AdminController still calls AdminService.auditLogs")
if "public List<Map<String, Object>> auditLogs" in service:
    raise RuntimeError("AdminService.auditLogs still exists")
print("Recent audit log query moved to dedicated MyBatis service")
