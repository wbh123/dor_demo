package com.wust.dormitory.admin;

import com.wust.dormitory.admin.mapper.AdminCatalogMapper;
import com.wust.dormitory.admin.mapper.AdminDashboardMapper;
import com.wust.dormitory.admin.mapper.BatchCatalogMapper;
import com.wust.dormitory.admin.mapper.BatchPreparationMapper;
import com.wust.dormitory.admin.mapper.MajorManagementMapper;
import com.wust.dormitory.admin.mapper.StudentAdminMapper;
import com.wust.dormitory.admin.model.persistence.AdminDashboardStatsRow;
import com.wust.dormitory.admin.model.persistence.BatchCatalogRow;
import com.wust.dormitory.admin.model.persistence.BuildingCatalogRow;
import com.wust.dormitory.admin.model.persistence.StudentCatalogRow;
import com.wust.dormitory.admin.model.query.StudentCatalogQuery;
import com.wust.dormitory.audit.AuditService;
import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminService {
    private final AuditService auditService;
    private final AdminCatalogMapper adminCatalogMapper;
    private final StudentAdminMapper studentAdminMapper;
    private final AdminDashboardMapper adminDashboardMapper;
    private final BatchCatalogMapper batchCatalogMapper;
    private final ReferenceDataCacheService referenceDataCacheService;
    private final MajorManagementMapper majorManagementMapper;
    private final BatchPreparationMapper batchPreparationMapper;

    public AdminService(
            AuditService auditService,
            AdminCatalogMapper adminCatalogMapper,
            StudentAdminMapper studentAdminMapper,
            AdminDashboardMapper adminDashboardMapper,
            BatchCatalogMapper batchCatalogMapper,
            ReferenceDataCacheService referenceDataCacheService,
            MajorManagementMapper majorManagementMapper,
            BatchPreparationMapper batchPreparationMapper) {
        this.auditService = auditService;
        this.adminCatalogMapper = adminCatalogMapper;
        this.studentAdminMapper = studentAdminMapper;
        this.adminDashboardMapper = adminDashboardMapper;
        this.batchCatalogMapper = batchCatalogMapper;
        this.referenceDataCacheService = referenceDataCacheService;
        this.majorManagementMapper = majorManagementMapper;
        this.batchPreparationMapper = batchPreparationMapper;
    }

    public Map<String, Object> dashboard() {
        AdminDashboardStatsRow stats = adminDashboardMapper.findStats();
        return stats.asResponseMap();
    }

    public List<Map<String, Object>> majors(Boolean enabled) {
        return referenceDataCacheService.majors(enabled);
    }

    @Transactional
    public long saveMajor(Long id, MajorCommand command, CurrentUser operator) {
        if (id == null) {
            Map<String, Object> values = majorValues(command);
            majorManagementMapper.insertMajor(values);
            Object key = values.get("id");
            if (!(key instanceof Number number)) {
                throw new IllegalStateException("专业创建成功但未返回编号");
            }
            long newId = number.longValue();
            referenceDataCacheService.invalidateMajors();
            auditService.success(operator, "MAJOR_CREATE", "MAJOR", newId, null, null, command);
            return newId;
        }
        Map<String, Object> before = majorManagementMapper.findMajor(id);
        if (before == null) {
            throw new BusinessException("MAJOR_NOT_FOUND", "专业不存在", HttpStatus.NOT_FOUND);
        }
        Map<String, Object> values = majorValues(command);
        values.put("id", id);
        majorManagementMapper.updateMajor(values);
        referenceDataCacheService.invalidateMajors();
        auditService.success(operator, "MAJOR_UPDATE", "MAJOR", id, null, before, command);
        return id;
    }

    public Map<String, Object> students(
            String keyword,
            String gender,
            Long majorId,
            int page,
            int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, size), 200);
        String keywordPattern = keyword == null || keyword.isBlank()
                ? null
                : "%" + keyword.trim() + "%";
        String genderFilter = gender == null || gender.isBlank() ? null : gender;
        StudentCatalogQuery query = new StudentCatalogQuery(
                keywordPattern,
                genderFilter,
                majorId,
                safeSize,
                (safePage - 1) * safeSize);
        int total = Math.toIntExact(studentAdminMapper.countStudents(query));
        List<Map<String, Object>> items = studentAdminMapper.findStudents(query).stream()
                .map(StudentCatalogRow::asResponseMap)
                .toList();
        return Map.of(
                "page", safePage,
                "size", safeSize,
                "total", total,
                "items", items);
    }

    public List<Map<String, Object>> buildings() {
        return adminCatalogMapper.findBuildings().stream()
                .map(BuildingCatalogRow::asResponseMap)
                .toList();
    }

    public List<Map<String, Object>> batches() {
        return batchCatalogMapper.findBatches().stream()
                .map(BatchCatalogRow::asResponseMap)
                .toList();
    }

    @Transactional
    public Map<String, Object> prepareBatch(long batchId, CurrentUser operator) {
        String status = batchPreparationMapper.findBatchStatus(batchId);
        if (status == null) {
            throw new BusinessException("BATCH_NOT_FOUND", "选寝批次不存在", HttpStatus.NOT_FOUND);
        }
        if (!"DRAFT".equals(status)) {
            throw new BusinessException("BATCH_STATUS_INVALID", "当前批次状态不允许执行该操作");
        }
        int students = batchPreparationMapper.insertEligibleStudents(batchId);
        int buildings = batchPreparationMapper.insertEnabledBuildings(batchId);
        Map<String, Object> result = Map.of(
                "addedStudents", students,
                "addedBuildings", buildings);
        auditService.success(operator, "BATCH_PREPARE", "SELECTION_BATCH", batchId,
                null, null, Map.of("studentRows", students, "buildingRows", buildings));
        return result;
    }

    private Map<String, Object> majorValues(MajorCommand command) {
        Map<String, Object> values = new HashMap<>();
        values.put("code", command.majorCode());
        values.put("name", command.majorName());
        values.put("enabled", command.enabled() ? 1 : 0);
        return values;
    }

    public record MajorCommand(String majorCode, String majorName, boolean enabled) {
    }
}
