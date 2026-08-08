package com.wust.dormitory.importworkflow;

import com.wust.dormitory.admin.RoomImportService;
import com.wust.dormitory.admin.StudentAdminService;
import com.wust.dormitory.admin.StudentImportRowMapper;
import com.wust.dormitory.audit.AuditService;
import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.importworkflow.mapper.ImportMutationSnapshotMapper;
import com.wust.dormitory.importworkflow.mapper.ImportRollbackMapper;
import com.wust.dormitory.security.CurrentUser;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class ImportMutationService {
    private static final List<String> STUDENT_STATE_FIELDS = List.of(
            "studentId", "studentNumber", "studentName", "gender", "majorId",
            "nationalityCode", "studentCategory", "enrollmentSource", "phoneNumber",
            "degreeLevel", "gradeYear", "userId", "username", "displayName",
            "accountStatus", "passwordHash", "userType");
    private static final List<String> ROOM_STATE_FIELDS = List.of(
            "roomId", "floorId", "roomNumber", "roomType", "capacity",
            "genderRestriction", "residentScope", "operationalStatus", "remark", "stateVersion");

    private final StudentAdminService studentAdminService;
    private final StudentImportRowMapper studentImportRowMapper;
    private final RoomImportService roomImportService;
    private final ImportMutationSnapshotMapper snapshotMapper;
    private final ImportRollbackMapper rollbackMapper;
    private final AuditService auditService;

    public ImportMutationService(
            StudentAdminService studentAdminService,
            StudentImportRowMapper studentImportRowMapper,
            RoomImportService roomImportService,
            ImportMutationSnapshotMapper snapshotMapper,
            ImportRollbackMapper rollbackMapper,
            AuditService auditService) {
        this.studentAdminService = studentAdminService;
        this.studentImportRowMapper = studentImportRowMapper;
        this.roomImportService = roomImportService;
        this.snapshotMapper = snapshotMapper;
        this.rollbackMapper = rollbackMapper;
        this.auditService = auditService;
    }

    public void validateRow(String importType, Map<String, String> row) {
        switch (importType) {
            case "STUDENT" -> validateStudentCommand(studentImportRowMapper.map(row));
            case "ROOM" -> roomImportService.validateRowForImport(row);
            default -> throw new BusinessException("IMPORT_TYPE_INVALID", "导入类型只支持 STUDENT 或 ROOM");
        }
    }

    @Transactional
    public List<ImportJournalEntry> applyTask(
            String importType,
            List<Map<String, String>> rows,
            CurrentUser operator) {
        List<ImportJournalEntry> journal = new ArrayList<>();
        if ("STUDENT".equals(importType)) {
            for (Map<String, String> row : rows) {
                StudentAdminService.StudentCommand command = studentImportRowMapper.map(row)
                        .withEnrollmentSource("BATCH_IMPORT");
                validateStudentCommand(command);
                Map<String, Object> before = studentSnapshotByNumber(command.studentNumber());
                Long existingId = before.isEmpty() ? null : number(before.get("studentId"));
                long studentId = studentAdminService.saveStudent(existingId, command, operator);
                Map<String, Object> after = studentSnapshot(studentId);
                journal.add(new ImportJournalEntry(
                        existingId == null ? "STUDENT_CREATE" : "STUDENT_UPDATE",
                        studentId,
                        before,
                        after,
                        Map.of("studentNumber", command.studentNumber())));
            }
        } else if ("ROOM".equals(importType)) {
            for (Map<String, String> row : rows) {
                RoomImportService.RoomApplyResult applied = roomImportService.applyRow(row);
                Map<String, Object> metadata = new LinkedHashMap<>();
                if (applied.createdFloorId() != null) metadata.put("createdFloorId", applied.createdFloorId());
                if (applied.createdBuildingId() != null) metadata.put("createdBuildingId", applied.createdBuildingId());
                journal.add(new ImportJournalEntry(
                        applied.roomCreated() ? "ROOM_CREATE" : "ROOM_UPDATE",
                        applied.roomId(),
                        normalizeRoomState(applied.beforeState()),
                        normalizeRoomState(applied.afterState()),
                        metadata));
            }
        } else {
            throw new BusinessException("IMPORT_TYPE_INVALID", "导入类型只支持 STUDENT 或 ROOM");
        }
        auditService.success(
                operator, "IMPORT_TASK_COMMIT", "IMPORT_TASK", null,
                "导入任务正式提交", null,
                Map.of("type", importType, "rows", rows.size(), "mutations", journal.size()));
        return List.copyOf(journal);
    }

    @Transactional
    public void rollbackJournal(List<ImportJournalEntry> journal, CurrentUser operator) {
        List<ImportJournalEntry> reversed = new ArrayList<>(journal == null ? List.of() : journal);
        Collections.reverse(reversed);
        for (ImportJournalEntry entry : reversed) {
            switch (entry.action()) {
                case "STUDENT_CREATE" -> rollbackStudentCreate(entry);
                case "STUDENT_UPDATE" -> rollbackStudentUpdate(entry);
                case "ROOM_CREATE" -> rollbackRoomCreate(entry);
                case "ROOM_UPDATE" -> rollbackRoomUpdate(entry);
                default -> throw rollbackConflict("撤销日志包含未知操作：" + entry.action(), null);
            }
        }
        auditService.success(
                operator, "IMPORT_TASK_ROLLBACK", "IMPORT_TASK", null,
                "导入任务回滚", null, Map.of("mutations", reversed.size()));
    }

    private void rollbackStudentCreate(ImportJournalEntry entry) {
        Map<String, Object> current = studentSnapshot(entry.entityId());
        ensureSnapshotMatches("学生", current, entry.afterState(), STUDENT_STATE_FIELDS);
        try {
            rollbackMapper.deleteStudentUser(entry.entityId());
            if (rollbackMapper.deleteStudent(entry.entityId()) != 1) {
                throw rollbackConflict("导入新增的学生已经不存在", null);
            }
        } catch (DataAccessException exception) {
            throw rollbackConflict("导入新增的学生已被批次、队伍或住宿记录引用，不能自动回滚", exception);
        }
    }

    private void rollbackStudentUpdate(ImportJournalEntry entry) {
        Map<String, Object> current = studentSnapshot(entry.entityId());
        ensureSnapshotMatches("学生", current, entry.afterState(), STUDENT_STATE_FIELDS);
        Map<String, Object> restore = new LinkedHashMap<>(entry.beforeState());
        restore.put("studentId", entry.entityId());
        rollbackMapper.restoreStudent(restore);
        rollbackMapper.restoreUser(restore);
    }

    private void rollbackRoomCreate(ImportJournalEntry entry) {
        Map<String, Object> current = roomSnapshot(entry.entityId());
        ensureSnapshotMatches("宿舍", current, entry.afterState(), ROOM_STATE_FIELDS);
        ensureRoomHasNoResidents(entry.entityId());
        try {
            rollbackMapper.deleteRoomBeds(entry.entityId());
            if (rollbackMapper.deleteRoom(entry.entityId()) != 1) {
                throw rollbackConflict("导入新增的宿舍已经不存在", null);
            }
            Long createdFloorId = nullableNumber(entry.metadata().get("createdFloorId"));
            if (createdFloorId != null) rollbackMapper.deleteFloorIfEmpty(createdFloorId);
            Long createdBuildingId = nullableNumber(entry.metadata().get("createdBuildingId"));
            if (createdBuildingId != null) rollbackMapper.deleteBuildingIfEmpty(createdBuildingId);
        } catch (DataAccessException exception) {
            throw rollbackConflict("导入新增的宿舍或床位已被批次、住宿记录引用，不能自动回滚", exception);
        }
    }

    private void rollbackRoomUpdate(ImportJournalEntry entry) {
        Map<String, Object> current = roomSnapshot(entry.entityId());
        ensureSnapshotMatches("宿舍", current, entry.afterState(), ROOM_STATE_FIELDS);
        ensureRoomHasNoResidents(entry.entityId());
        Map<String, Object> restore = new LinkedHashMap<>(entry.beforeState());
        restore.put("roomId", entry.entityId());
        rollbackMapper.restoreRoom(restore);
    }

    private void ensureRoomHasNoResidents(long roomId) {
        if (snapshotMapper.countActiveResidents(roomId) > 0) {
            throw rollbackConflict("宿舍已经有在住学生，不能自动回滚", null);
        }
    }

    private Map<String, Object> studentSnapshotByNumber(String studentNumber) {
        return copyOrEmpty(snapshotMapper.findStudentSnapshotByNumber(studentNumber));
    }

    private Map<String, Object> studentSnapshot(long studentId) {
        Map<String, Object> snapshot = snapshotMapper.findStudentSnapshot(studentId);
        if (snapshot == null) throw rollbackConflict("学生记录不存在", null);
        return new LinkedHashMap<>(snapshot);
    }

    private Map<String, Object> roomSnapshot(long roomId) {
        Map<String, Object> snapshot = snapshotMapper.findRoomSnapshot(roomId);
        if (snapshot == null) throw rollbackConflict("宿舍记录不存在", null);
        return new LinkedHashMap<>(snapshot);
    }

    private Map<String, Object> copyOrEmpty(Map<String, Object> source) {
        return source == null ? Map.of() : new LinkedHashMap<>(source);
    }

    private Map<String, Object> normalizeRoomState(Map<String, Object> source) {
        if (source == null || source.isEmpty()) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("roomId", first(source, "roomId", "id"));
        result.put("floorId", first(source, "floorId", "floor_id"));
        result.put("roomNumber", first(source, "roomNumber", "room_number"));
        result.put("roomType", first(source, "roomType", "room_type"));
        result.put("capacity", source.get("capacity"));
        result.put("genderRestriction", first(source, "genderRestriction", "gender_restriction"));
        result.put("residentScope", first(source, "residentScope", "resident_scope"));
        result.put("operationalStatus", first(source, "operationalStatus", "operational_status"));
        result.put("remark", source.get("remark"));
        result.put("stateVersion", first(source, "stateVersion", "state_version"));
        return result;
    }

    private Object first(Map<String, Object> source, String preferred, String fallback) {
        return source.containsKey(preferred) ? source.get(preferred) : source.get(fallback);
    }

    private void validateStudentCommand(StudentAdminService.StudentCommand command) {
        if (command.studentNumber() == null || !command.studentNumber().matches("^\\d{12}$")) {
            throw new BusinessException("STUDENT_NUMBER_INVALID", "学号必须为12位数字");
        }
        if (command.studentName() == null || command.studentName().isBlank()) {
            throw new BusinessException("STUDENT_NAME_REQUIRED", "学生姓名不能为空");
        }
        if (!List.of("M", "F").contains(command.gender())) {
            throw new BusinessException("STUDENT_GENDER_INVALID", "学生性别必须为男或女");
        }
        if (command.phoneNumber() != null && !command.phoneNumber().matches("^\\+?[0-9][0-9 -]{5,30}$")) {
            throw new BusinessException("PHONE_NUMBER_INVALID", "手机号码格式不正确");
        }
        if (command.gradeYear() != null && (command.gradeYear() < 2000 || command.gradeYear() > 2100)) {
            throw new BusinessException("GRADE_YEAR_INVALID", "年级必须为2000至2100之间的年份");
        }
    }

    private void ensureSnapshotMatches(
            String label,
            Map<String, Object> current,
            Map<String, Object> expected,
            List<String> fields) {
        for (String field : fields) {
            if (!sameValue(current.get(field), expected.get(field))) {
                throw rollbackConflict(label + "在导入后已被再次修改，不能覆盖后续变更；冲突字段：" + field, null);
            }
        }
    }

    private boolean sameValue(Object left, Object right) {
        if (Objects.equals(left, right)) return true;
        return left != null && right != null && String.valueOf(left).equals(String.valueOf(right));
    }

    private long number(Object value) {
        Long result = nullableNumber(value);
        if (result == null) throw new IllegalArgumentException("数值不能为空");
        return result;
    }

    private Long nullableNumber(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.longValue();
        return Long.valueOf(String.valueOf(value));
    }

    private BusinessException rollbackConflict(String message, Throwable cause) {
        BusinessException exception = new BusinessException(
                "IMPORT_ROLLBACK_CONFLICT", message, HttpStatus.CONFLICT);
        if (cause != null) exception.initCause(cause);
        return exception;
    }
}
