package com.wust.dormitory.admin;

import com.wust.dormitory.allocation.AdminAllocationService;
import com.wust.dormitory.allocation.AssignmentAdjustmentService;
import com.wust.dormitory.allocation.AssignmentQueryService;
import com.wust.dormitory.common.response.ResponseFactory;
import com.wust.dormitory.matching.MatchingSchemeService;
import com.wust.dormitory.model.api.AdminApi;
import com.wust.dormitory.model.dto.AllocationCommitRequest;
import com.wust.dormitory.model.dto.AssignmentAdjustmentRequest;
import com.wust.dormitory.model.dto.BatchRequest;
import com.wust.dormitory.model.dto.ListSuccessResponse;
import com.wust.dormitory.model.dto.MajorRequest;
import com.wust.dormitory.model.dto.MatchingWeightSchemeCreateRequest;
import com.wust.dormitory.model.dto.MatchingWeightSchemeRevisionRequest;
import com.wust.dormitory.model.dto.ObjectSuccessResponse;
import com.wust.dormitory.model.dto.RoomBedLayoutItem;
import com.wust.dormitory.model.dto.RoomBedLayoutRequest;
import com.wust.dormitory.model.dto.RoomRequest;
import com.wust.dormitory.model.dto.StudentRequest;
import com.wust.dormitory.model.dto.VoidSuccessResponse;
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
import java.util.Map;

@RestController
public class AdminController implements AdminApi {
    private final AdminService adminService;
    private final RoomManagementService roomManagementService;
    private final RoomLayoutService roomLayoutService;
    private final MatchingSchemeService matchingSchemeService;
    private final BatchLifecycleService batchLifecycleService;
    private final AdminAllocationService allocationService;
    private final AssignmentQueryService assignmentQueryService;
    private final AssignmentAdjustmentService adjustmentService;
    private final AssignmentExportService exportService;
    private final BatchRuleValidator batchRuleValidator;

    public AdminController(
            AdminService adminService,
            RoomManagementService roomManagementService,
            RoomLayoutService roomLayoutService,
            MatchingSchemeService matchingSchemeService,
            BatchLifecycleService batchLifecycleService,
            AdminAllocationService allocationService,
            AssignmentQueryService assignmentQueryService,
            AssignmentAdjustmentService adjustmentService,
            AssignmentExportService exportService,
            BatchRuleValidator batchRuleValidator) {
        this.adminService = adminService;
        this.roomManagementService = roomManagementService;
        this.roomLayoutService = roomLayoutService;
        this.matchingSchemeService = matchingSchemeService;
        this.batchLifecycleService = batchLifecycleService;
        this.allocationService = allocationService;
        this.assignmentQueryService = assignmentQueryService;
        this.adjustmentService = adjustmentService;
        this.exportService = exportService;
        this.batchRuleValidator = batchRuleValidator;
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
            String keyword, String gender, Long majorId, Integer page, Integer size) {
        return ResponseEntity.ok(ResponseFactory.object(adminService.students(
                keyword,
                gender,
                majorId,
                page == null ? 1 : page,
                size == null ? 20 : size)));
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> createStudent(StudentRequest request) {
        long id = adminService.saveStudent(null, studentCommand(request), SecurityUsers.requireAdmin());
        return ResponseEntity.ok(ResponseFactory.object(Map.of("id", id)));
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> updateStudent(Long id, StudentRequest request) {
        adminService.saveStudent(id, studentCommand(request), SecurityUsers.requireAdmin());
        return ResponseEntity.ok(ResponseFactory.object(Map.of("id", id)));
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> importStudents(List<StudentRequest> requests) {
        List<AdminService.StudentCommand> commands = requests.stream()
                .map(this::studentCommand)
                .toList();
        return ResponseEntity.ok(ResponseFactory.object(
                adminService.importStudents(commands, SecurityUsers.requireAdmin())));
    }

    @Override
    public ResponseEntity<ListSuccessResponse> listBuildings() {
        return ResponseEntity.ok(ResponseFactory.list(adminService.buildings()));
    }

    @Override
    public ResponseEntity<ListSuccessResponse> listRooms(Long buildingId, String gender) {
        return ResponseEntity.ok(ResponseFactory.list(
                roomManagementService.rooms(buildingId, gender)));
    }

    @Override
    public ResponseEntity<VoidSuccessResponse> updateRoom(Long roomId, RoomRequest request) {
        roomManagementService.updateRoom(roomId, new RoomManagementService.RoomCommand(
                request.getCapacity(),
                request.getGender(),
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
                request.getExpectedRoomVersion(),
                request.getReason(),
                beds);
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
                Boolean.TRUE.equals(request.getActivate()),
                request.getExpectedVersion(),
                request.getReason());
        return ResponseEntity.ok(ResponseFactory.object(
                matchingSchemeService.createRevision(
                        schemeId,
                        command,
                        SecurityUsers.requireAdmin())));
    }

    @Override
    public ResponseEntity<ListSuccessResponse> listBatches() {
        return ResponseEntity.ok(ResponseFactory.list(adminService.batches()));
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> createBatch(BatchRequest request) {
        AdminService.BatchCommand command = new AdminService.BatchCommand(
                request.getBatchCode(),
                request.getBatchName(),
                toLocalDateTime(request.getStartAt()),
                toLocalDateTime(request.getEndAt()),
                request.getHoldDurationSeconds(),
                request.getAllowTeam(),
                request.getTeamMaxSize(),
                request.getAllowStudentRandom()
        );
        batchRuleValidator.validate(command);
        long id = adminService.createBatch(command, SecurityUsers.requireAdmin());
        return ResponseEntity.ok(ResponseFactory.object(Map.of("id", id)));
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
                targetStatus.toUpperCase(),
                SecurityUsers.requireAdmin()
        );
        return ResponseEntity.ok(ResponseFactory.empty());
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> previewAllocation(Long batchId, Long randomSeed) {
        SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.object(
                allocationService.preview(
                        batchId,
                        randomSeed == null ? 20260801L : randomSeed
                )));
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
                        SecurityUsers.requireAdmin()
                )));
    }

    @Override
    public ResponseEntity<ListSuccessResponse> listAssignments(Long batchId, String keyword) {
        SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.list(
                assignmentQueryService.list(batchId, keyword)
        ));
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
                        SecurityUsers.requireAdmin()
                )));
    }

    @Override
    public ResponseEntity<ListSuccessResponse> listAuditLogs(Integer limit) {
        SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.list(
                adminService.auditLogs(limit == null ? 100 : limit)
        ));
    }

    @Override
    public ResponseEntity<Resource> exportAssignments(Long batchId) {
        SecurityUsers.requireAdmin();
        Resource resource = exportService.exportCsv(batchId);
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=assignments-" + batchId + ".csv"
                )
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
                request.getEnabled()
        );
    }

    private AdminService.StudentCommand studentCommand(StudentRequest request) {
        return new AdminService.StudentCommand(
                request.getStudentNumber(),
                request.getStudentName().trim(),
                request.getGender(),
                request.getMajorId()
        );
    }

    private LocalDateTime toLocalDateTime(Date value) {
        return value.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
    }
}
