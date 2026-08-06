package com.wust.dormitory.admin.model.persistence;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

public record BatchCatalogRow(
        Long id,
        String batchCode,
        String batchName,
        String batchStatus,
        String selectionMode,
        Boolean separateStudentCategories,
        Long questionnaireVersionId,
        Long matchingWeightSchemeId,
        Long ruleTemplateId,
        LocalDateTime startAt,
        LocalDateTime endAt,
        Integer holdDurationSeconds,
        Integer holdRenewalLimit,
        Boolean allowTeam,
        Integer teamMinSize,
        Integer teamMaxSize,
        Boolean allowStudentRandom,
        String unselectedStrategy,
        String ruleVersion,
        Long createdBy,
        LocalDateTime publishedAt,
        LocalDateTime finishedAt,
        Integer version,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Long eligibleCount,
        Long assignedCount,
        Long bedAssignedCount,
        Long roomAssignedCount,
        Long lockedRoomCount,
        Long unconfirmedBedResidentCount) {

    public Map<String, Object> asResponseMap() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", id);
        response.put("batch_code", batchCode);
        response.put("batch_name", batchName);
        response.put("batch_status", batchStatus);
        response.put("selection_mode", selectionMode);
        response.put("separate_student_categories", separateStudentCategories);
        response.put("questionnaire_version_id", questionnaireVersionId);
        response.put("matching_weight_scheme_id", matchingWeightSchemeId);
        response.put("rule_template_id", ruleTemplateId);
        response.put("start_at", startAt);
        response.put("end_at", endAt);
        response.put("hold_duration_seconds", holdDurationSeconds);
        response.put("hold_renewal_limit", holdRenewalLimit);
        response.put("allow_team", allowTeam);
        response.put("team_min_size", teamMinSize);
        response.put("team_max_size", teamMaxSize);
        response.put("allow_student_random", allowStudentRandom);
        response.put("unselected_strategy", unselectedStrategy);
        response.put("rule_version", ruleVersion);
        response.put("created_by", createdBy);
        response.put("published_at", publishedAt);
        response.put("finished_at", finishedAt);
        response.put("version", version);
        response.put("created_at", createdAt);
        response.put("updated_at", updatedAt);
        response.put("eligible_count", eligibleCount);
        response.put("assigned_count", assignedCount);
        response.put("bed_assigned_count", bedAssignedCount);
        response.put("room_assigned_count", roomAssignedCount);
        response.put("locked_room_count", lockedRoomCount);
        response.put("unconfirmed_bed_resident_count", unconfirmedBedResidentCount);
        return response;
    }
}
