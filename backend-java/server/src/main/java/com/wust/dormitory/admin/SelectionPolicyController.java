package com.wust.dormitory.admin;

import com.wust.dormitory.common.response.ResponseFactory;
import com.wust.dormitory.model.dto.ObjectSuccessResponse;
import com.wust.dormitory.security.SecurityUsers;
import com.wust.dormitory.selection.SelectionPolicyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/settings/selection-policy")
public class SelectionPolicyController {
    private final SelectionPolicyService service;
    public SelectionPolicyController(SelectionPolicyService service) { this.service=service; }
    @GetMapping public ResponseEntity<ObjectSuccessResponse> get() { SecurityUsers.requireAdmin(); return ResponseEntity.ok(ResponseFactory.object(service.policy())); }
    @PutMapping public ResponseEntity<ObjectSuccessResponse> update(@RequestBody UpdateRequest request) { return ResponseEntity.ok(ResponseFactory.object(service.update(request.allowWithoutQuestionnaire(),request.allowStudentReselect(),request.reason(),SecurityUsers.requireAdmin()))); }
    public record UpdateRequest(boolean allowWithoutQuestionnaire, boolean allowStudentReselect, String reason) { }
}
