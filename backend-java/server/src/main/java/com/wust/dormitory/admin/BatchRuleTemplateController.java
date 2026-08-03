package com.wust.dormitory.admin;

import com.wust.dormitory.common.response.ResponseFactory;
import com.wust.dormitory.model.api.BatchRuleTemplateApi;
import com.wust.dormitory.model.dto.BatchRuleTemplateCreateRequest;
import com.wust.dormitory.model.dto.BatchRuleTemplateRevisionRequest;
import com.wust.dormitory.model.dto.ListSuccessResponse;
import com.wust.dormitory.model.dto.ObjectSuccessResponse;
import com.wust.dormitory.security.SecurityUsers;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BatchRuleTemplateController implements BatchRuleTemplateApi {
    private final BatchRuleTemplateService templateService;

    public BatchRuleTemplateController(BatchRuleTemplateService templateService) {
        this.templateService = templateService;
    }

    @Override
    public ResponseEntity<ListSuccessResponse> listBatchRuleTemplates() {
        SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.list(templateService.list()));
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> createBatchRuleTemplate(
            BatchRuleTemplateCreateRequest request) {
        BatchRuleTemplateService.CreateCommand command =
                new BatchRuleTemplateService.CreateCommand(
                        request.getRuleCode(),
                        request.getRuleName(),
                        request.getHoldDurationSeconds(),
                        request.getHoldRenewalLimit(),
                        request.getAllowTeam(),
                        request.getTeamMinSize(),
                        request.getTeamMaxSize(),
                        request.getAllowStudentRandom(),
                        request.getUnselectedStrategy().getValue(),
                        request.getRuleVersion(),
                        request.getEnabled(),
                        request.getMakeDefault(),
                        request.getChangeReason());
        return ResponseEntity.ok(ResponseFactory.object(
                templateService.create(command, SecurityUsers.requireAdmin())));
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> createBatchRuleTemplateRevision(
            Long templateId,
            BatchRuleTemplateRevisionRequest request) {
        BatchRuleTemplateService.RevisionCommand command =
                new BatchRuleTemplateService.RevisionCommand(
                        request.getRuleName(),
                        request.getHoldDurationSeconds(),
                        request.getHoldRenewalLimit(),
                        request.getAllowTeam(),
                        request.getTeamMinSize(),
                        request.getTeamMaxSize(),
                        request.getAllowStudentRandom(),
                        request.getUnselectedStrategy().getValue(),
                        request.getRuleVersion(),
                        request.getEnabled(),
                        request.getMakeDefault(),
                        request.getExpectedVersion(),
                        request.getChangeReason());
        return ResponseEntity.ok(ResponseFactory.object(
                templateService.createRevision(
                        templateId,
                        command,
                        SecurityUsers.requireAdmin())));
    }
}
