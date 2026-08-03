package com.wust.dormitory.admin;

import com.wust.dormitory.common.response.ResponseFactory;
import com.wust.dormitory.model.api.BatchScopeApi;
import com.wust.dormitory.model.dto.BatchScopeUpdateRequest;
import com.wust.dormitory.model.dto.ObjectSuccessResponse;
import com.wust.dormitory.security.SecurityUsers;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BatchScopeController implements BatchScopeApi {
    private final BatchScopeService batchScopeService;

    public BatchScopeController(BatchScopeService batchScopeService) {
        this.batchScopeService = batchScopeService;
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> getBatchScope(Long batchId) {
        SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.object(batchScopeService.get(batchId)));
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> updateBatchScope(
            Long batchId,
            BatchScopeUpdateRequest request) {
        return ResponseEntity.ok(ResponseFactory.object(batchScopeService.update(
                batchId,
                new BatchScopeService.UpdateCommand(
                        request.getStudentIds(),
                        request.getRoomIds()),
                SecurityUsers.requireAdmin())));
    }
}
