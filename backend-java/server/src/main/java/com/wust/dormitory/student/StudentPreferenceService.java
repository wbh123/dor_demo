package com.wust.dormitory.student;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wust.dormitory.audit.AuditService;
import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.matching.MatchingService;
import com.wust.dormitory.security.CurrentUser;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class StudentPreferenceService {
    private static final String QUESTIONNAIRE_CODE = "SYSTEM-PREFERENCE-V1";
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final MatchingService matchingService;
    private final AuditService auditService;

    public StudentPreferenceService(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper,
                                    MatchingService matchingService, AuditService auditService) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.matchingService = matchingService;
        this.auditService = auditService;
    }

    public Map<String, Object> questionnaire(CurrentUser user) {
        long versionId = versionId();
        List<Map<String, Object>> questions = questions(versionId);
        Map<String, Object> answers = storedAnswers(user.studentId());
        return Map.of(
                "questionnaireCode", QUESTIONNAIRE_CODE,
                "questions", questions,
                "answers", answers,
                "completed", !answers.isEmpty());
    }

    @Transactional
    public Map<String, Object> save(Map<String, Object> answers, CurrentUser user) {
        long versionId = versionId();
        List<Map<String, Object>> questions = questionDefinitions(versionId);
        Map<String, Object> normalizedAnswers = new LinkedHashMap<>();
        Map<String, Object> features = new LinkedHashMap<>();
        for (Map<String, Object> question : questions) {
            String code = String.valueOf(question.get("question_code"));
            Object value = answers.get(code);
            if (((Number) question.get("required_flag")).intValue() == 1 && value == null) {
                throw new BusinessException("QUESTION_REQUIRED", "问卷题目未填写：" + code);
            }
            if (value != null) {
                normalizedAnswers.put(code, value);
                features.put(String.valueOf(question.get("feature_key")), normalize(value));
            }
        }
        Map<String, Object> normalizedFeatures = matchingService.normalizeAnswers(features);
        jdbc.update("""
                INSERT INTO student_preference_profile
                (student_id, questionnaire_version_id, answers_json, feature_vector_json, completed_at, version)
                VALUES (:studentId,:versionId,CAST(:answers AS JSON),CAST(:features AS JSON),CURRENT_TIMESTAMP(3),1)
                ON DUPLICATE KEY UPDATE questionnaire_version_id=VALUES(questionnaire_version_id),
                    answers_json=VALUES(answers_json), feature_vector_json=VALUES(feature_vector_json),
                    completed_at=VALUES(completed_at), version=version+1
                """, new MapSqlParameterSource().addValue("studentId", user.studentId()).addValue("versionId", versionId)
                .addValue("answers", json(normalizedAnswers)).addValue("features", json(normalizedFeatures)));
        auditService.success(user, "PREFERENCE_PROFILE_UPDATE", "STUDENT", user.studentId(),
                "更新跨批次个人偏好", null, Map.of("questionCount", normalizedAnswers.size()));
        return Map.of("completed", true, "questionCount", normalizedAnswers.size(), "featureCount", normalizedFeatures.size());
    }

    public boolean completed(long studentId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM student_preference_profile WHERE student_id=:studentId AND completed_at IS NOT NULL",
                Map.of("studentId", studentId), Integer.class);
        return count != null && count > 0;
    }

    public String featureJson(long studentId) {
        List<String> rows = jdbc.query("SELECT feature_vector_json FROM student_preference_profile WHERE student_id=:studentId",
                Map.of("studentId", studentId), (rs, rowNum) -> rs.getString(1));
        return rows.isEmpty() ? "{}" : rows.getFirst();
    }

    public Map<String, Object> storedAnswers(long studentId) {
        List<String> rows = jdbc.query("SELECT answers_json FROM student_preference_profile WHERE student_id=:studentId",
                Map.of("studentId", studentId), (rs, rowNum) -> rs.getString(1));
        if (rows.isEmpty() || rows.getFirst() == null) return Map.of();
        try { return objectMapper.readValue(rows.getFirst(), new TypeReference<Map<String, Object>>() { }); }
        catch (JsonProcessingException exception) { return Map.of(); }
    }

    @Transactional
    public void synchronizeFromBatch(long batchId, long studentId, Map<String, Object> answers,
                                     Map<String, Object> featureVector) {
        jdbc.update("""
                INSERT INTO student_preference_profile
                (student_id, questionnaire_version_id, answers_json, feature_vector_json, completed_at, version)
                SELECT :studentId, questionnaire_version_id, CAST(:answers AS JSON), CAST(:features AS JSON), CURRENT_TIMESTAMP(3), 1
                FROM selection_batch WHERE id=:batchId
                ON DUPLICATE KEY UPDATE questionnaire_version_id=VALUES(questionnaire_version_id),
                    answers_json=VALUES(answers_json), feature_vector_json=VALUES(feature_vector_json),
                    completed_at=VALUES(completed_at), version=version+1
                """, new MapSqlParameterSource().addValue("studentId", studentId).addValue("batchId", batchId)
                .addValue("answers", json(answers)).addValue("features", json(featureVector)));
    }

    private long versionId() {
        List<Long> ids = jdbc.query("SELECT id FROM questionnaire_version WHERE version_code=:code LIMIT 1",
                Map.of("code", QUESTIONNAIRE_CODE), (rs, rowNum) -> rs.getLong(1));
        if (ids.isEmpty()) throw new BusinessException("BUILTIN_QUESTIONNAIRE_MISSING", "系统内置个人偏好问卷不可用");
        return ids.getFirst();
    }

    private List<Map<String, Object>> questions(long versionId) {
        List<Map<String, Object>> questions = questionDefinitions(versionId);
        List<Map<String, Object>> options = jdbc.queryForList("""
                SELECT o.id,o.question_id,o.option_code,o.option_text,o.feature_value,o.sort_order
                FROM questionnaire_option o JOIN questionnaire_question q ON q.id=o.question_id
                WHERE q.questionnaire_version_id=:versionId AND o.enabled=1
                ORDER BY o.question_id,o.sort_order
                """, Map.of("versionId", versionId));
        Map<Object, List<Map<String, Object>>> grouped = new HashMap<>();
        options.forEach(option -> grouped.computeIfAbsent(option.get("question_id"), ignored -> new ArrayList<>()).add(option));
        questions.forEach(question -> question.put("options", grouped.getOrDefault(question.get("id"), List.of())));
        return questions;
    }

    private List<Map<String, Object>> questionDefinitions(long versionId) {
        return jdbc.queryForList("""
                SELECT q.id,q.question_code,q.question_text,q.question_type,q.feature_key,q.required_flag,q.sort_order
                FROM questionnaire_question q
                WHERE q.questionnaire_version_id=:versionId AND q.enabled=1
                ORDER BY q.sort_order
                """, Map.of("versionId", versionId));
    }

    private Object normalize(Object value) {
        if (value instanceof List<?> list) return list.isEmpty() ? null : list.getFirst();
        return value;
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new BusinessException("JSON_ERROR", "偏好数据序列化失败"); }
    }
}
