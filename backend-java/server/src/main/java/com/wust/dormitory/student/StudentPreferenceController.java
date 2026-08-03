package com.wust.dormitory.student;

import com.wust.dormitory.common.response.ResponseFactory;
import com.wust.dormitory.model.dto.ObjectSuccessResponse;
import com.wust.dormitory.security.SecurityUsers;
import com.wust.dormitory.selection.SelectionPolicyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/student")
public class StudentPreferenceController {
    private final StudentPreferenceService preferenceService;
    private final SelectionPolicyService policyService;

    public StudentPreferenceController(StudentPreferenceService preferenceService, SelectionPolicyService policyService) {
        this.preferenceService = preferenceService; this.policyService = policyService;
    }
    @GetMapping("/preferences") public ResponseEntity<ObjectSuccessResponse> getPreferences() { return ResponseEntity.ok(ResponseFactory.object(preferenceService.questionnaire(SecurityUsers.requireStudent()))); }
    @PutMapping("/preferences") public ResponseEntity<ObjectSuccessResponse> savePreferences(@RequestBody Map<String,Object> answers) { return ResponseEntity.ok(ResponseFactory.object(preferenceService.save(answers,SecurityUsers.requireStudent()))); }
    @GetMapping("/batches/{batchId}/selection-readiness") public ResponseEntity<ObjectSuccessResponse> readiness(@PathVariable long batchId) { var user=SecurityUsers.requireStudent(); return ResponseEntity.ok(ResponseFactory.object(policyService.readiness(batchId,user.studentId()))); }
    @PostMapping("/batches/{batchId}/assignment/cancel") public ResponseEntity<ObjectSuccessResponse> cancel(@PathVariable long batchId) { var user=SecurityUsers.requireStudent(); return ResponseEntity.ok(ResponseFactory.object(policyService.cancelAssignment(batchId,user.studentId(),user))); }
}
