package com.wust.dormitory.importworkflow;

import com.wust.dormitory.common.response.ResponseFactory;
import com.wust.dormitory.model.dto.ListSuccessResponse;
import com.wust.dormitory.model.dto.ObjectSuccessResponse;
import com.wust.dormitory.security.CurrentUser;
import com.wust.dormitory.security.SecurityUsers;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin/import-tasks")
public class ImportWorkflowController {
    private final ImportWorkflowService service;

    public ImportWorkflowController(ImportWorkflowService service) {
        this.service = service;
    }

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ObjectSuccessResponse> preview(
            @RequestPart("file") MultipartFile file,
            @RequestParam("type") String type,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.object(service.preview(file, type, idempotencyKey)));
    }

    @GetMapping
    public ResponseEntity<ListSuccessResponse> listTasks() {
        SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.list(service.listTasks()));
    }

    @GetMapping("/{taskId}")
    public ResponseEntity<ObjectSuccessResponse> getTask(@PathVariable String taskId) {
        SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.object(service.getTask(taskId)));
    }

    @PostMapping("/{taskId}/commit")
    public ResponseEntity<ObjectSuccessResponse> commit(@PathVariable String taskId) {
        CurrentUser operator = SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.object(service.commitTask(taskId, operator)));
    }

    @PostMapping("/{taskId}/rollback")
    public ResponseEntity<ObjectSuccessResponse> rollback(@PathVariable String taskId) {
        CurrentUser operator = SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.object(service.rollbackTask(taskId, operator)));
    }

    @GetMapping(value = "/{taskId}/errors.csv", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<byte[]> errorsCsv(@PathVariable String taskId) {
        SecurityUsers.requireAdmin();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("import-errors-" + taskId + ".csv")
                .build());
        return ResponseEntity.ok().headers(headers).body(service.errorsCsv(taskId));
    }
}
