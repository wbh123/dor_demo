package com.wust.dormitory.admin;

import com.wust.dormitory.allocation.AdminAllocationService;
import com.wust.dormitory.allocation.AssignmentAdjustmentService;
import com.wust.dormitory.allocation.AssignmentQueryService;
import com.wust.dormitory.audit.RecentAuditLogQueryService;
import com.wust.dormitory.common.response.ResponseFactory;
import com.wust.dormitory.matching.MatchingSchemeService;
import com.wust.dormitory.model.api.AdminApi;
import com.wust.dormitory.model.dto.AdminBedConfirmationRequest;
import com.wust.dormitory.model.dto.AllocationCommitRequest;
import com.wust.dormitory.model.dto.AssignmentAdjustmentRequest;
import com.wust.dormitory.model.dto.BatchCopyRequest;
import com.wust.dormitory.model.dto.BatchEnrollmentRequest;
import com.wust.dormitory.model.dto.BatchRequest;
import com.wust.dormitory.model.dto.BuildingRequest;
import com.wust.dormitory.model.dto.DirectResidencyAssignmentRequest;
import com.wust.dormitory.model.dto.ListSuccessResponse;
import com.wust.dormitory.model.dto.MajorRequest;
import com.wust.dormitory.model.dto.MatchingWeightSchemeCreateRequest;
import com.wust.dormitory.model.dto.MatchingWeightSchemeRevisionRequest;
import com.wust.dormitory.model.dto.ObjectSuccessResponse;
import com.wust.dormitory.model.dto.ResidencyEndRequest;
import com.wust.dormitory.model.dto.RoomBedLayoutItem;
import com.wust.dormitory.model.dto.RoomBedLayoutRequest;
import com.wust.dormitory.model.dto.RoomCreateRequest;
import com.wust.dormitory.model.dto.RoomRequest;
import com.wust.dormitory.model.dto.StudentRequest;
import com.wust.dormitory.model.dto.VoidSuccessResponse;
import com.wust.dormitory.residency.BatchCapacityService;
import com.wust.dormitory.residency.BatchRoomLockService;
import com.wust.dormitory.residency.ResidencyService;
import com.wust.dormitory.security.CurrentUser;
import com.wust.dormitory.security.SecurityUsers;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
public class AdminController implements AdminApi {
    private final AdminService adminService;
    private final StudentAdminService studentAdminService;
    private final ResidencyService residencyService;
    private final BatchCapacityService batchCapacityService;
    private final BatchRoomLockService batchRoomLockService;
    private final RoomManagementService roomManagementService;
    private final RoomLayoutService roomLayoutService;
    private final MatchingSchemeService matchingSchemeService;
    private final BatchCreationService batchCreationService;
    private final BatchLifecycleService batchLifecycleService;
    private final BatchCopyService batchCopyService;
    private final AdminAllocationService allocationService;
    private final AssignmentQueryService assignmentQueryService;
    private final AssignmentAdjustmentService adjustmentService;
    private final AssignmentExportService exportService;
    private final RecentAuditLogQueryService recentAuditLogQueryService;

    public AdminController(
            AdminService adminService,
            StudentAdminService studentAdminService,
            ResidencyService residencyService,
            BatchCapacityService batchCapacityService,
            BatchRoomLockService batchRoomLockService,
            RoomManagementService roomManagementService,
            RoomLayoutService roomLayoutService,
            MatchingSchemeService matchingSchemeService,
            BatchCreationService batchCreationService,
            BatchLifecycleService batchLifecycleService,
            BatchCopyService batchCopyService,
            AdminAllocationService allocationService,
            AssignmentQueryService assignmentQueryService,
            AssignmentAdjustmentService adjustmentService,
            AssignmentExportService exportService,
            RecentAuditLogQueryService recentAuditLogQueryService) {
        this.adminService = adminService;
        this.studentAdminService = studentAdminService;
        this.residencyService = residencyService;
        this.batchCapacityService = batchCapacityService;
        this.batchRoomLockService = batchRoomLockService;
        this.roomManagementService = roomManagementService;
        this.roomLayoutService = roomLayoutService;
        this.matchingSchemeService = matchingSchemeService;
        this.batchCreationService = batchCreationService;
        this.batchLifecycleService = batchLifecycleService;
        this.batchCopyService = batchCopyService;
        this.allocationService = allocationService;
        this.assignmentQueryService = assignmentQueryService;
        this.adjustmentService = adjustmentService;
        this.exportService = exportService;
        this.recentAuditLogQueryService = recentAuditLogQueryService;
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> getAdminDashboard() {
        return ResponseEntity.ok(ResponseFactory.object(adminService.dashboard()));
    }

    @Override
    public ResponseEntity<ListSuccessResponse> listMajors(Boolean enabled) {
        return ResponseEntity.ok(ResponseFactory.list(adminService.majors(enabled)));
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> createMajor(MajorRequest request) {
        long id = adminService.saveMajor(null, majorCommand(request), SecurityUsers.requireAdmin());
        return ResponseEntity.ok(ResponseFactory.object(Map.of("id", id)));
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> updateMajor(Long id, MajorRequest request) {
        adminService.saveMajor(id, majorCommand(request), SecurityUsers.requireAdmin());
        return ResponseEntity.ok(ResponseFactory.object(Map.of("id", id)));
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> listStudents(
            String keyword,
            String gender,
            Long majorId,
            String studentCategory,
            String enrollmentSource,
            Integer page,
            Integer size) {
        return ResponseEntity.ok(ResponseFactory.object(studentAdminService.students(
                keyword, gender, majorId, studentCategory, enrollmentSource,
                page == null ? 1 : page,
                size == null ? 20 : size)));
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> createStudent(StudentRequest request) {
        long id = studentAdminService.saveStudent(
                null, studentCommand(request), SecurityUsers.requireAdmin());
        return ResponseEntity.ok(ResponseFactory.object(Map.of("id", id)));
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> updateStudent(Long id, StudentRequest request) {
        studentAdminService.saveStudent(id, studentCommand(request), SecurityUsers.requireAdmin());
        return ResponseEntity.ok(ResponseFactory.object(Map.of("id", id)));
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> importStudents(List<StudentRequest> requests) {
        List<StudentAdminService.StudentCommand> commands = requests.stream()
                .map(this::studentCommand)
                .toList();
        return ResponseEntity.ok(ResponseFactory.object(
                studentAdminService.importStudents(commands, SecurityUsers.requireAdmin())));
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> directlyAssignStudent(
            Long studentId,
            DirectResidencyAssignmentRequest request) {
        CurrentUser operator = SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.object(residencyService.assign(
                studentId,
                request.getRoomId(),
                request.getBedId(),
                null,
                null,
                "DIRECT",
                request.getBedId() == null ? "DIRECT_ROOM" : "DIRECT_BED",
                request.getReason(),
                operator)));
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> previewStudentBatchCapacity(
            Long batchId,
            Long studentId) {
        SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.object(
                batchCapacityService.preview(batchId, studentId)));
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> enrollStudentIntoBatch(
            Long batchId,
            Long studentId,
            BatchEnrollmentRequest request) {
        return ResponseEntity.ok(ResponseFactory.object(batchCapacityService.enroll(
                batchId,
                studentId,
                "ADMIN_MANUAL",
                request.getReason(),
                SecurityUsers.requireAdmin())));
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> listResidencies(
            Long roomId,
            String keyword,
            String bedMappingStatus) {
        SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.object(
                residencyService.list(roomId, keyword, bedMappingStatus)));
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> confirmResidencyBedByAdmin(
            Long residencyId,
            AdminBedConfirmationRequest request) {
        return ResponseEntity.ok(ResponseFactory.object(residencyService.confirmBed(
                residencyId,
                request.getBedId(),
                request.getReason(),
                SecurityUsers.requireAdmin())));
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> endResidency(
            Long residencyId,
            ResidencyEndRequest request) {
        return ResponseEntity.ok(ResponseFactory.object(residencyService.end(
                residencyId,
                request.getReason(),
                SecurityUsers.requireAdmin())));
    }

    @Override
    public ResponseEntity<ListSuccessResponse> listBuildings() {
        return ResponseEntity.ok(ResponseFactory.list(roomManagementService.buildings()));
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> createBuilding(BuildingRequest request) {
        long id = roomManagementService.createBuilding(
                new RoomManagementService.BuildingCommand(
                        request.getBuildingCode(),
                        request.getBuildingName(),
                        request.getGender().getValue(),
                        request.getEducationLevelScope().getValue(),
                        request.getResidentScope().getValue(),
                        request.getFloorCount(),
                        request.getReason()),
                SecurityUsers.requireAdmin());
        return ResponseEntity.ok(ResponseFactory.object(Map.of("id", id)));
    }

    @Override
    public ResponseEntity<ListSuccessResponse> listRooms(Long buildingId, String gender) {
        return ResponseEntity.ok(ResponseFactory.list(
                roomManagementService.rooms(buildingId, gender)));
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> createRoom(RoomCreateRequest request) {
        long id = roomManagementService.createRoom(
                new RoomManagementService.RoomCreateCommand(
                        request.getBuildingId(),
                        request.getFloorNumber(),
                        request.getRoomNumber(),
                        request.getCapacity(),
                        request.getGender().getValue(),
                        request.getEducationLevelScope().getValue(),
                        request.getResidentScope().getValue(),
                        request.getOperationalStatus().getValue(),
                        request.getRemark(),
                        request.getReason()),
                SecurityUsers.requireAdmin());
        return ResponseEntity.ok(ResponseFactory.object(Map.of("id", id)));
    }

    @Override
    public ResponseEntity<VoidSuccessResponse> updateRoom(Long roomId, RoomRequest request) {
        roomManagementService.updateRoom(roomId, new RoomManagementService.RoomCommand(
                request.getCapacity(),
                request.getGender(),
                request.getEducationLevelScope().getValue(),
                request.getResidentScope().getValue(),
                request.getOperationalStatus(),
                request.getRemark(),
                request.getReason()), SecurityUsers.requireAdmin());
        return ResponseEntity.ok(ResponseFactory.empty());
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> getRoomBedLayout(Long roomId) {
        SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.object(roomLayoutService.getLayout(roomId)));
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> updateRoomBedLayout(
            Long roomId,
            RoomBedLayoutRequest request) {
        List<RoomLayoutService.LayoutItem> beds = request.getBeds().stream()
                .map(this::layoutItem)
                .toList();
        RoomLayoutService.LayoutCommand command = new RoomLayoutService.LayoutCommand(
                request.getExpectedRoomVersion(), request.getReason(), beds);
        return ResponseEntity.ok(ResponseFactory.object(
                roomLayoutService.updateLayout(roomId, command, SecurityUsers.requireAdmin())));
    }

    @Override
    public ResponseEntity<ListSuccessResponse> listMatchingWeightSchemes() {
        SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.list(matchingSchemeService.list()));
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> createMatchingWeightScheme(
            MatchingWeightSchemeCreateRequest request) {
        MatchingSchemeService.CreateCommand command = new MatchingSchemeService.CreateCommand(
                request.getSchemeCode(),
                request.getSchemeName(),
                request.getAlgorithmVersion(),
                request.getWeights(),
                request.getConflictRules(),
                request.getAllowedRecommendationStrategies(),
                request.getDefaultRecommendationStrategy(),
                request.getWeightedRandomBaseWeight(),
                request.getWeightedRandomTemperature(),
                Boolean.TRUE.equals(request.getActivate()),
                request.getReason());
        return ResponseEntity.ok(ResponseFactory.object(
                matchingSchemeService.create(command, SecurityUsers.requireAdmin())));
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> createMatchingWeightSchemeRevision(
            Long schemeId,
            MatchingWeightSchemeRevisionRequest request) {
        MatchingSchemeService.RevisionCommand command = new MatchingSchemeService.RevisionCommand(
                request.getSchemeName(),
                request.getAlgorithmVersion(),
                request.getWeights(),
                request.getConflictRules(),
                request.getAllowedRecommendationStrategies(),
                request.getDefaultRecommendationStrategy(),
                request.getWeightedRandomBaseWeight(),
                request.getWeightedRandomTemperature(),
                Boolean.TRUE.equals(request.getActivate()),
                request.getExpectedVersion(),
                request.getReason());
        return ResponseEntity.ok(ResponseFactory.object(
                matchingSchemeService.createRevision(
                        schemeId, command, SecurityUsers.requireAdmin())));
    }

    @Override
    public ResponseEntity<ListSuccessResponse> listBatches() {
        return ResponseEntity.ok(ResponseFactory.list(adminService.batches()));
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> createBatch(BatchRequest request) {
        BatchCreationService.CreateCommand command = new BatchCreationService.CreateCommand(
                request.getBatchCode(),
                request.getBatchName(),
                toLocalDateTime(request.getStartAt()),
                toLocalDateTime(request.getEndAt()),
                request.getRuleTemplateId(),
                request.getSelectionMode().getValue(),
                Boolean.TRUE.equals(request.getSeparateStudentCategories()));
        return ResponseEntity.ok(ResponseFactory.object(
                batchCreationService.create(command, SecurityUsers.requireAdmin())));
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> previewBatchRoomAvailability(Long batchId) {
        SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.object(batchRoomLockService.preview(batchId)));
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> copyBatch(Long batchId, BatchCopyRequest request) {
        BatchCopyService.CopyCommand command = new BatchCopyService.CopyCommand(
                request.getBatchCode(),
                request.getBatchName(),
                toLocalDateTime(request.getStartAt()),
                toLocalDateTime(request.getEndAt()),
                request.getReason());
        return ResponseEntity.ok(ResponseFactory.object(
                batchCopyService.copy(batchId, command, SecurityUsers.requireAdmin())));
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> prepareBatch(Long batchId) {
        return ResponseEntity.ok(ResponseFactory.object(
                adminService.prepareBatch(batchId, SecurityUsers.requireAdmin())));
    }

    @Override
    public ResponseEntity<VoidSuccessResponse> changeBatchStatus(Long batchId, String targetStatus) {
        batchLifecycleService.changeStatus(
                batchId,
                targetStatus.toUpperCase(Locale.ROOT),
                SecurityUsers.requireAdmin());
        return ResponseEntity.ok(ResponseFactory.empty());
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> previewAllocation(Long batchId, Long randomSeed) {
        SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.object(
                allocationService.preview(batchId, randomSeed == null ? 20260801L : randomSeed)));
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> commitAllocation(
            Long batchId,
            AllocationCommitRequest request) {
        return ResponseEntity.ok(ResponseFactory.object(
                allocationService.commit(
                        batchId,
                        request.getRandomSeed(),
                        request.getIdempotencyKey(),
                        SecurityUsers.requireAdmin())));
    }

    @Override
    public ResponseEntity<ListSuccessResponse> listAssignments(Long batchId, String keyword) {
        SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.list(
                assignmentQueryService.list(batchId, keyword)));
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> adjustAssignment(
            Long assignmentId,
            AssignmentAdjustmentRequest request) {
        return ResponseEntity.ok(ResponseFactory.object(
                adjustmentService.adjust(
                        assignmentId,
                        request.getNewBedId(),
                        request.getReason(),
                        SecurityUsers.requireAdmin())));
    }

    @Override
    public ResponseEntity<ListSuccessResponse> listAuditLogs(Integer limit) {
        SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.list(
                recentAuditLogQueryService.list(limit == null ? 100 : limit)));
    }

    @Override
    public ResponseEntity<Resource> exportAssignments(Long batchId) {
        SecurityUsers.requireAdmin();
        Resource resource = exportService.exportCsv(batchId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=assignments-" + batchId + ".csv")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(resource);
    }

    private RoomLayoutService.LayoutItem layoutItem(RoomBedLayoutItem item) {
        return new RoomLayoutService.LayoutItem(
                item.getBedId(),
                item.getBedType().getValue(),
                item.getLayoutX(),
                item.getLayoutZ(),
                item.getRotationDegrees().getValue());
    }

    private AdminService.MajorCommand majorCommand(MajorRequest request) {
        return new AdminService.MajorCommand(
                request.getMajorCode().trim(),
                request.getMajorName().trim(),
                request.getEnabled());
    }

    private StudentAdminService.StudentCommand studentCommand(StudentRequest request) {
        String nationalityCode = request.getNationalityCode();
        if (nationalityCode == null || nationalityCode.isBlank()) nationalityCode = "CN";
        String phoneNumber = request.getPhoneNumber();
        if (phoneNumber != null) {
            phoneNumber = phoneNumber.trim();
            if (phoneNumber.isEmpty()) phoneNumber = null;
        }
        String enrollmentSource = request.getEnrollmentSource() == null
                ? "ADMIN_MANUAL"
                : request.getEnrollmentSource().getValue();
        return new StudentAdminService.StudentCommand(
                request.getStudentNumber(),
                request.getStudentName().trim(),
                request.getGender(),
                request.getMajorId(),
                nationalityCode.trim().toUpperCase(Locale.ROOT),
                request.getStudentCategory().getValue(),
                enrollmentSource,
                phoneNumber,
                request.getDegreeLevel() == null ? null : request.getDegreeLevel().getValue(),
                request.getGradeYear());
    }

    private LocalDateTime toLocalDateTime(Date value) {
        return value.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }
}
