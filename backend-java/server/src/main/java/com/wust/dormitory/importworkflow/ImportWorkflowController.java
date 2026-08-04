package com.wust.dormitory.importworkflow;

import com.wust.dormitory.common.response.ResponseFactory;
import com.wust.dormitory.model.api.ImportWorkflowApi;
import com.wust.dormitory.model.dto.ListSuccessResponse;
import com.wust.dormitory.model.dto.ObjectSuccessResponse;
import com.wust.dormitory.security.CurrentUser;
import com.wust.dormitory.security.SecurityUsers;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
public class ImportWorkflowController implements ImportWorkflowApi {
    private final ImportWorkflowService service;

    public ImportWorkflowController(ImportWorkflowService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> previewImportTask(
            String idempotencyKey,
            String type,
            MultipartFile file) {
        SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.object(service.preview(file, type, idempotencyKey)));
    }

    @Override
    public ResponseEntity<ListSuccessResponse> listImportTasks() {
        SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.list(service.listTasks()));
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> getImportTask(UUID taskId) {
        SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.object(service.getTask(taskId.toString())));
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> commitImportTask(UUID taskId) {
        CurrentUser operator = SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.object(service.commitTask(taskId.toString(), operator)));
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> rollbackImportTask(UUID taskId) {
        CurrentUser operator = SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.object(service.rollbackTask(taskId.toString(), operator)));
    }

    @Override
    public ResponseEntity<Resource> exportImportTaskErrors(UUID taskId) {
        SecurityUsers.requireAdmin();
        Resource resource = new ByteArrayResource(service.errorsCsv(taskId.toString()));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("import-errors-" + taskId + ".csv")
                .build());
        return ResponseEntity.ok().headers(headers).body(resource);
    }
}
