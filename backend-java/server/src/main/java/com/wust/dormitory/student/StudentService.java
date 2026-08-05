package com.wust.dormitory.student;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wust.dormitory.audit.AuditService;
import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.matching.MatchingService;
import com.wust.dormitory.realtime.RoomEventHub;
import com.wust.dormitory.security.CurrentUser;
import com.wust.dormitory.selection.BedHoldService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class StudentService {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final MatchingService matchingService;
    private final BedHoldService holdService;
    private final RoomEventHub eventHub;
    private final AuditService auditService;
    private final StudentPreferenceService preferenceService;

    public StudentService(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper,
                          MatchingService matchingService, BedHoldService holdService,
                          RoomEventHub eventHub, AuditService auditService,
                          StudentPreferenceService preferenceService) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.matchingService = matchingService;
        this.holdService = holdService;
        this.eventHub = eventHub;
        this.auditService = auditService;
        this.preferenceService = preferenceService;
    }

    public Map<String, Object> profile(CurrentUser user) {
        return one("""
                SELECT s.id, s.student_number, s.student_name, s.gender,
                       m.id AS major_id, m.major_code, m.major_name
                FROM student s JOIN major m ON m.id=s.major_id
                WHERE s.id=:studentId
                """, Map.of("studentId", user.studentId()), "STUDENT_NOT_FOUND", "学生档案不存在");
    }

    public List<Map<String, Object>> batches(CurrentUser user) {
        return jdbc.queryForList("""
                SELECT sb.id, sb.batch_code, sb.batch_name, sb.batch_status,
                       sb.start_at, sb.end_at, sb.hold_duration_seconds,
                       sb.allow_team, sb.team_min_size, sb.team_max_size,
                       sb.allow_student_random, e.eligibility_status,
                       (EXISTS(SELECT 1 FROM questionnaire_answer qa
                              WHERE qa.batch_id=sb.id AND qa.student_id=:studentId)
                        OR EXISTS(SELECT 1 FROM student_preference_profile spp
                              WHERE spp.student_id=:studentId AND spp.completed_at IS NOT NULL)) AS questionnaire_started,
                       EXISTS(SELECT 1 FROM bed_assignment ba
                              WHERE ba.batch_id=sb.id AND ba.student_id=:studentId) AS assigned
                FROM batch_student_eligibility e
                JOIN selection_batch sb ON sb.id=e.batch_id
                WHERE e.student_id=:studentId
                ORDER BY sb.start_at DESC
                """, Map.of("studentId", user.studentId()));
    }

    public Map<String, Object> questionnaire(long batchId, CurrentUser user) {
        Map<String, Object> batch = accessibleBatch(batchId, user.studentId(), Set.of("PUBLISHED", "OPEN", "PAUSED"));
        long versionId = ((Number) batch.get("questionnaire_version_id")).longValue();
        List<Map<String, Object>> questions = jdbc.queryForList("""
                SELECT q.id, q.question_code, q.question_text, q.question_type,
                       q.feature_key, q.required_flag, q.sort_order
                FROM questionnaire_question q
                WHERE q.questionnaire_version_id=:versionId AND q.enabled=1
                ORDER BY q.sort_order
                """, Map.of("versionId", versionId));
        List<Map<String, Object>> options = jdbc.queryForList("""
                SELECT o.id, o.question_id, o.option_code, o.option_text,
                       o.feature_value, o.sort_order
                FROM questionnaire_option o JOIN questionnaire_question q ON q.id=o.question_id
                WHERE q.questionnaire_version_id=:versionId AND o.enabled=1
                ORDER BY o.question_id, o.sort_order
                """, Map.of("versionId", versionId));
        Map<Object, List<Map<String, Object>>> optionsByQuestion = new HashMap<>();
        options.forEach(option -> optionsByQuestion.computeIfAbsent(option.get("question_id"), ignored -> new ArrayList<>()).add(option));
        questions.forEach(question -> question.put("options", optionsByQuestion.getOrDefault(question.get("id"), List.of())));
        List<Map<String, Object>> answers = jdbc.queryForList("""
                SELECT question_id, answer_json FROM questionnaire_answer
                WHERE batch_id=:batchId AND student_id=:studentId
                """, new MapSqlParameterSource().addValue("batchId", batchId).addValue("studentId", user.studentId()));
        Map<String, Object> profileAnswers = answers.isEmpty()
                ? preferenceService.storedAnswers(user.studentId()) : Map.of();
        return Map.of("batch", batch, "questions", questions, "answers", answers,
                "profileAnswers", profileAnswers, "preferenceCompleted", preferenceService.completed(user.studentId()));
    }

    @Transactional
    public void submitQuestionnaire(long batchId, Map<String, Object> answers, CurrentUser user) {
        Map<String, Object> batch = accessibleBatch(batchId, user.studentId(), Set.of("PUBLISHED", "OPEN"));
        long versionId = ((Number) batch.get("questionnaire_version_id")).longValue();
        List<Map<String, Object>> questions = jdbc.queryForList("""
                SELECT id, question_code, feature_key, required_flag FROM questionnaire_question
                WHERE questionnaire_version_id=:versionId AND enabled=1
                """, Map.of("versionId", versionId));
        Map<String, Object> featureVector = new LinkedHashMap<>();
        for (Map<String, Object> question : questions) {
            String questionCode = String.valueOf(question.get("question_code"));
            Object value = answers.get(questionCode);
            if (((Number) question.get("required_flag")).intValue() == 1 && value == null) {
                throw new BusinessException("QUESTION_REQUIRED", "问卷题目未填写：" + questionCode);
            }
            if (value == null) {
                continue;
            }
            long questionId = ((Number) question.get("id")).longValue();
            jdbc.update("""
                    INSERT INTO questionnaire_answer
                    (batch_id, questionnaire_version_id, student_id, question_id,
                     answer_json, submitted_at)
                    VALUES (:batchId, :versionId, :studentId, :questionId,
                            CAST(:answer AS JSON), CURRENT_TIMESTAMP(3))
                    ON DUPLICATE KEY UPDATE answer_json=VALUES(answer_json),
                                            submitted_at=VALUES(submitted_at), version=version+1
                    """, new MapSqlParameterSource().addValue("batchId", batchId)
                    .addValue("versionId", versionId).addValue("studentId", user.studentId())
                    .addValue("questionId", questionId).addValue("answer", json(value)));
            featureVector.put(String.valueOf(question.get("feature_key")), normalize(value));
        }
        String featureJson = json(matchingService.normalizeAnswers(featureVector));
        jdbc.update("""
                INSERT INTO student_feature
                (batch_id, student_id, algorithm_version, feature_vector_json,
                 explanation_tags_json, calculated_at, source_answer_version)
                VALUES (:batchId, :studentId, 'feature-v1', CAST(:feature AS JSON),
                        JSON_ARRAY('问卷已完成'), CURRENT_TIMESTAMP(3), 1)
                ON DUPLICATE KEY UPDATE feature_vector_json=VALUES(feature_vector_json),
                    explanation_tags_json=VALUES(explanation_tags_json),
                    calculated_at=VALUES(calculated_at), source_answer_version=source_answer_version+1
                """, new MapSqlParameterSource().addValue("batchId", batchId)
                .addValue("studentId", user.studentId()).addValue("feature", featureJson));
        preferenceService.synchronizeFromBatch(batchId, user.studentId(), answers,
                matchingService.normalizeAnswers(featureVector));
        auditService.success(user, "QUESTIONNAIRE_SUBMIT", "SELECTION_BATCH", batchId,
                null, null, Map.of("questionCount", featureVector.size()));
    }

    public List<Map<String, Object>> rooms(long batchId, CurrentUser user) {
        Map<String, Object> student = profile(user);
        accessibleBatch(batchId, user.studentId(), Set.of("PUBLISHED", "OPEN", "PAUSED"));
        String feature = featureJson(batchId, user.studentId());
        List<Map<String, Object>> rooms = jdbc.queryForList("""
                SELECT r.id, b.building_name, f.floor_number, r.room_number,
                       r.room_type, r.capacity, r.gender_restriction, r.state_version,
                       COUNT(bed.id) AS bed_count,
                       SUM(bed.operational_status='ENABLED') AS enabled_bed_count,
                       COUNT(a.id) AS assigned_count
                FROM room r JOIN dormitory_floor f ON f.id=r.floor_id
                JOIN dormitory_building b ON b.id=f.building_id
                JOIN bed ON bed.room_id=r.id
                LEFT JOIN bed_assignment a ON a.batch_id=:batchId AND a.bed_id=bed.id
                WHERE r.operational_status='ENABLED' AND bed.operational_status='ENABLED'
                  AND r.gender_restriction=:gender
                  AND (
                    EXISTS (SELECT 1 FROM batch_room_scope rs WHERE rs.batch_id=:batchId AND rs.room_id=r.id)
                    OR EXISTS (SELECT 1 FROM batch_building_scope bs WHERE bs.batch_id=:batchId AND bs.building_id=b.id)
                  )
                GROUP BY r.id, b.building_name, f.floor_number, r.room_number,
                         r.room_type, r.capacity, r.gender_restriction, r.state_version
                HAVING COUNT(a.id) < COUNT(bed.id)
                ORDER BY b.building_name, f.floor_number, r.room_number
                """, new MapSqlParameterSource().addValue("batchId", batchId).addValue("gender", student.get("gender")));
        for (Map<String, Object> room : rooms) {
            long roomId = ((Number) room.get("id")).longValue();
            List<String> roommateFeatures = jdbc.query("""
                    SELECT sf.feature_vector_json
                    FROM bed_assignment a JOIN bed ON bed.id=a.bed_id
                    JOIN student_feature sf ON sf.batch_id=a.batch_id AND sf.student_id=a.student_id
                    WHERE a.batch_id=:batchId AND bed.room_id=:roomId
                    """, new MapSqlParameterSource().addValue("batchId", batchId).addValue("roomId", roomId),
                    (rs, rowNum) -> rs.getString(1));
            MatchingService.MatchResult match = matchingService.roomScore(feature, roommateFeatures);
            room.put("availableCount", ((Number) room.get("enabled_bed_count")).intValue() - ((Number) room.get("assigned_count")).intValue());
            room.put("matchScore", match.score());
            room.put("matches", match.matches());
            room.put("warnings", match.warnings());
        }
        rooms.sort(Comparator.comparingDouble(room -> -((Number) room.get("matchScore")).doubleValue()));
        return rooms;
    }

    public Map<String, Object> room(long batchId, long roomId, CurrentUser user) {
        Map<String, Object> student = profile(user);
        accessibleBatch(batchId, user.studentId(), Set.of("PUBLISHED", "OPEN", "PAUSED"));
        Map<String, Object> room = one("""
                SELECT r.id, b.building_name, f.floor_number, r.room_number,
                       r.room_type, r.capacity, r.gender_restriction, r.state_version, r.remark
                FROM room r JOIN dormitory_floor f ON f.id=r.floor_id
                JOIN dormitory_building b ON b.id=f.building_id
                WHERE r.id=:roomId AND r.operational_status='ENABLED'
                """, Map.of("roomId", roomId), "ROOM_NOT_FOUND", "房间不存在或不可用");
        if (!student.get("gender").equals(room.get("gender_restriction"))) {
            throw new BusinessException("ROOM_GENDER_MISMATCH", "不能访问其他性别的宿舍", HttpStatus.FORBIDDEN);
        }
        ensureRoomInScope(batchId, roomId);
        List<Map<String, Object>> beds = jdbc.queryForList("""
                SELECT bed.id, bed.bed_code, bed.bed_type, bed.position_index,
                       bed.operational_status, a.id AS assignment_id
                FROM bed LEFT JOIN bed_assignment a ON a.batch_id=:batchId AND a.bed_id=bed.id
                WHERE bed.room_id=:roomId ORDER BY bed.position_index
                """, new MapSqlParameterSource().addValue("batchId", batchId).addValue("roomId", roomId));
        beds.forEach(bed -> {
            long bedId = ((Number) bed.get("id")).longValue();
            String hold = holdService.current(batchId, bedId);
            String status;
            if (!"ENABLED".equals(String.valueOf(bed.get("operational_status")))) {
                status = "DISABLED";
            } else if (bed.get("assignment_id") != null) {
                status = "ASSIGNED";
            } else if (hold != null) {
                status = hold.startsWith("S:" + user.studentId() + ":") ? "HELD_BY_ME" : "HELD";
            } else {
                status = "AVAILABLE";
            }
            bed.put("status", status);
            bed.remove("assignment_id");
        });
        return Map.of("room", room, "beds", beds);
    }

    public BedHoldService.HoldResult hold(long batchId, long bedId, CurrentUser user) {
        Map<String, Object> context = selectableBed(batchId, bedId, user.studentId());
        Duration ttl = Duration.ofSeconds(((Number) context.get("hold_duration_seconds")).longValue());
        BedHoldService.HoldResult result = holdService.hold(batchId, bedId, user.studentId(), ttl);
        eventHub.publish(batchId, ((Number) context.get("room_id")).longValue(), "BED_HELD", Map.of("bedId", bedId));
        return result;
    }

    public void release(long batchId, long bedId, String token, CurrentUser user) {
        long roomId = roomIdForBed(bedId);
        holdService.releaseStudent(batchId, bedId, user.studentId(), token);
        eventHub.publish(batchId, roomId, "BED_RELEASED", Map.of("bedId", bedId));
    }

    @Transactional
    public Map<String, Object> confirm(long batchId, long bedId, String token, CurrentUser user) {
        Map<String, Object> context = selectableBed(batchId, bedId, user.studentId());
        if (!holdService.validateStudent(batchId, bedId, user.studentId(), token)) {
            throw new BusinessException("HOLD_TOKEN_INVALID", "临时占用已失效，请重新选择床位", HttpStatus.CONFLICT);
        }
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO bed_assignment
                (batch_id, student_id, bed_id, assignment_method, assignment_status, assigned_at)
                VALUES (:batchId, :studentId, :bedId, 'SELF_SELECT', 'ACTIVE', CURRENT_TIMESTAMP(3))
                """, new MapSqlParameterSource().addValue("batchId", batchId)
                .addValue("studentId", user.studentId()).addValue("bedId", bedId), keyHolder, new String[]{"id"});
        long assignmentId = keyHolder.getKey().longValue();
        jdbc.update("""
                INSERT INTO assignment_history
                (assignment_id, batch_id, student_id, bed_id, event_type,
                 assignment_method, reason, current_data, occurred_at)
                VALUES (:assignmentId, :batchId, :studentId, :bedId, 'CREATED',
                        'SELF_SELECT', '学生自主选寝', CAST(:data AS JSON), CURRENT_TIMESTAMP(3))
                """, new MapSqlParameterSource().addValue("assignmentId", assignmentId)
                .addValue("batchId", batchId).addValue("studentId", user.studentId())
                .addValue("bedId", bedId).addValue("data", json(Map.of("bedId", bedId))));
        long roomId = ((Number) context.get("room_id")).longValue();
        afterCommit(() -> {
            holdService.releaseStudent(batchId, bedId, user.studentId(), token);
            eventHub.publish(batchId, roomId, "BED_ASSIGNED", Map.of("bedId", bedId));
        });
        auditService.success(user, "BED_ASSIGN_SELF", "BED_ASSIGNMENT", assignmentId,
                null, null, Map.of("batchId", batchId, "bedId", bedId));
        return assignment(batchId, user);
    }

    public Map<String, Object> randomRecommendation(long batchId, CurrentUser user) {
        List<Map<String, Object>> ranked = rooms(batchId, user);
        for (Map<String, Object> candidate : ranked) {
            long roomId = ((Number) candidate.get("id")).longValue();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> beds = (List<Map<String, Object>>) room(batchId, roomId, user).get("beds");
            for (Map<String, Object> bed : beds) {
                if ("AVAILABLE".equals(bed.get("status"))) {
                    return Map.of("room", candidate, "bed", bed,
                            "explanation", "从高匹配房间中选择当前可用床位，确认前不会形成最终分配");
                }
            }
        }
        throw new BusinessException("NO_AVAILABLE_BED", "当前没有符合条件的可用床位", HttpStatus.CONFLICT);
    }

    public Map<String, Object> assignment(long batchId, CurrentUser user) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT a.id, a.assignment_method, a.assigned_at,
                       COALESCE(actual_bed.id, selected_bed.id) AS bed_id,
                       COALESCE(actual_bed.bed_code, selected_bed.bed_code) AS bed_code,
                       COALESCE(actual_bed.bed_type, selected_bed.bed_type) AS bed_type,
                       r.id AS room_id, r.room_number, b.building_name, f.floor_number
                FROM bed_assignment a
                JOIN bed selected_bed ON selected_bed.id=a.bed_id
                LEFT JOIN room_assignment current_residency
                  ON current_residency.batch_id=a.batch_id
                 AND current_residency.student_id=a.student_id
                 AND current_residency.assignment_status='ACTIVE'
                LEFT JOIN bed actual_bed ON actual_bed.id=current_residency.bed_id
                JOIN room r ON r.id=COALESCE(actual_bed.room_id, selected_bed.room_id)
                JOIN dormitory_floor f ON f.id=r.floor_id
                JOIN dormitory_building b ON b.id=f.building_id
                WHERE a.batch_id=:batchId AND a.student_id=:studentId
                  AND a.assignment_status='ACTIVE'
                ORDER BY a.assigned_at DESC, a.id DESC
                """, new MapSqlParameterSource().addValue("batchId", batchId).addValue("studentId", user.studentId()));
        return rows.isEmpty() ? Map.of("assigned", false) : Map.of("assigned", true, "assignment", rows.getFirst());
    }

    @Transactional
    public Map<String, Object> createTeam(long batchId, String teamName, CurrentUser user) {
        Map<String, Object> batch = accessibleBatch(batchId, user.studentId(), Set.of("PUBLISHED", "OPEN"));
        if (((Number) batch.get("allow_team")).intValue() != 1) {
            throw new BusinessException("TEAM_DISABLED", "当前批次不允许组队选寝");
        }
        if (count("SELECT COUNT(*) FROM selection_team_member WHERE batch_id=:batchId AND student_id=:studentId AND active_marker=1",
                new MapSqlParameterSource().addValue("batchId", batchId).addValue("studentId", user.studentId())) > 0) {
            throw new BusinessException("TEAM_ALREADY_JOINED", "你已经在一个有效队伍中");
        }
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        String code = "T" + batchId + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        jdbc.update("""
                INSERT INTO selection_team
                (batch_id, team_code, team_name, leader_student_id, team_status)
                VALUES (:batchId, :code, :name, :studentId, 'FORMING')
                """, new MapSqlParameterSource().addValue("batchId", batchId).addValue("code", code)
                .addValue("name", teamName).addValue("studentId", user.studentId()), keyHolder, new String[]{"id"});
        long teamId = keyHolder.getKey().longValue();
        jdbc.update("""
                INSERT INTO selection_team_member
                (team_id, batch_id, student_id, member_role, member_status, joined_at)
                VALUES (:teamId, :batchId, :studentId, 'LEADER', 'JOINED', CURRENT_TIMESTAMP(3))
                """, new MapSqlParameterSource().addValue("teamId", teamId).addValue("batchId", batchId)
                .addValue("studentId", user.studentId()));
        auditService.success(user, "TEAM_CREATE", "SELECTION_TEAM", teamId, null, null, Map.of("teamCode", code));
        return Map.of("teamId", teamId, "teamCode", code);
    }

    @Transactional
    public Map<String, Object> invite(long teamId, String studentNumber, CurrentUser user) {
        Map<String, Object> team = leaderTeam(teamId, user.studentId(), "FORMING");
        Map<String, Object> inviter = profile(user);
        Map<String, Object> invitee = one("""
                SELECT s.id, s.gender FROM student s
                JOIN batch_student_eligibility e ON e.student_id=s.id AND e.batch_id=:batchId
                WHERE s.student_number=:studentNumber AND e.eligibility_status='ELIGIBLE'
                """, new MapSqlParameterSource().addValue("batchId", team.get("batch_id"))
                .addValue("studentNumber", studentNumber), "INVITEE_NOT_ELIGIBLE", "被邀请学生不存在或没有批次资格");
        if (!inviter.get("gender").equals(invitee.get("gender"))) {
            throw new BusinessException("TEAM_GENDER_MISMATCH", "队伍成员性别必须一致");
        }
        long inviteeId = ((Number) invitee.get("id")).longValue();
        String token = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO selection_team_member
                (team_id, batch_id, student_id, member_role, member_status)
                VALUES (:teamId, :batchId, :studentId, 'MEMBER', 'INVITED')
                """, new MapSqlParameterSource().addValue("teamId", teamId)
                .addValue("batchId", team.get("batch_id")).addValue("studentId", inviteeId));
        jdbc.update("""
                INSERT INTO team_invitation
                (team_id, inviter_student_id, invitee_student_id, invitation_status,
                 invitation_token, expires_at)
                VALUES (:teamId, :inviterId, :inviteeId, 'PENDING', :token,
                        DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 24 HOUR))
                """, new MapSqlParameterSource().addValue("teamId", teamId)
                .addValue("inviterId", user.studentId()).addValue("inviteeId", inviteeId).addValue("token", token));
        return Map.of("invitationToken", token);
    }

    @Transactional
    public void respondInvitation(String token, boolean accepted, CurrentUser user) {
        Map<String, Object> invitation = one("""
                SELECT i.*, t.batch_id FROM team_invitation i JOIN selection_team t ON t.id=i.team_id
                WHERE i.invitation_token=:token AND i.invitee_student_id=:studentId
                  AND i.invitation_status='PENDING' AND i.expires_at>CURRENT_TIMESTAMP(3)
                """, new MapSqlParameterSource().addValue("token", token).addValue("studentId", user.studentId()),
                "INVITATION_INVALID", "邀请不存在、已处理或已过期");
        String invitationStatus = accepted ? "ACCEPTED" : "REJECTED";
        String memberStatus = accepted ? "JOINED" : "REJECTED";
        jdbc.update("UPDATE team_invitation SET invitation_status=:status, responded_at=CURRENT_TIMESTAMP(3) WHERE id=:id",
                new MapSqlParameterSource().addValue("status", invitationStatus).addValue("id", invitation.get("id")));
        jdbc.update("""
                UPDATE selection_team_member SET member_status=:status,
                    joined_at=CASE WHEN :status='JOINED' THEN CURRENT_TIMESTAMP(3) ELSE joined_at END
                WHERE team_id=:teamId AND student_id=:studentId
                """, new MapSqlParameterSource().addValue("status", memberStatus)
                .addValue("teamId", invitation.get("team_id")).addValue("studentId", user.studentId()));
    }

    @Transactional
    public void lockTeam(long teamId, CurrentUser user) {
        Map<String, Object> team = leaderTeam(teamId, user.studentId(), "FORMING");
        long batchId = ((Number) team.get("batch_id")).longValue();
        int members = count("SELECT COUNT(*) FROM selection_team_member WHERE team_id=:teamId AND member_status='JOINED'", Map.of("teamId", teamId));
        Map<String, Object> batch = one("SELECT team_min_size, team_max_size FROM selection_batch WHERE id=:id",
                Map.of("id", batchId), "BATCH_NOT_FOUND", "批次不存在");
        if (members < ((Number) batch.get("team_min_size")).intValue() || members > ((Number) batch.get("team_max_size")).intValue()) {
            throw new BusinessException("TEAM_SIZE_INVALID", "队伍人数不符合批次规则");
        }
        jdbc.update("UPDATE selection_team SET team_status='LOCKED', locked_at=CURRENT_TIMESTAMP(3) WHERE id=:id", Map.of("id", teamId));
        jdbc.update("UPDATE selection_team_member SET member_status='LOCKED' WHERE team_id=:id AND member_status='JOINED'", Map.of("id", teamId));
    }

    public List<Map<String, Object>> teams(CurrentUser user) {
        return jdbc.queryForList("""
                SELECT t.id, t.batch_id, t.team_code, t.team_name, t.team_status,
                       t.leader_student_id, tm.member_role, tm.member_status,
                       (SELECT COUNT(*) FROM selection_team_member x
                        WHERE x.team_id=t.id AND x.member_status IN ('JOINED','LOCKED')) AS member_count
                FROM selection_team_member tm JOIN selection_team t ON t.id=tm.team_id
                WHERE tm.student_id=:studentId AND tm.active_marker=1
                ORDER BY t.created_at DESC
                """, Map.of("studentId", user.studentId()));
    }

    public List<Map<String, Object>> invitations(CurrentUser user) {
        return jdbc.queryForList("""
                SELECT i.invitation_token, i.expires_at, t.team_name, t.team_code,
                       s.student_name AS inviter_name
                FROM team_invitation i JOIN selection_team t ON t.id=i.team_id
                JOIN student s ON s.id=i.inviter_student_id
                WHERE i.invitee_student_id=:studentId AND i.invitation_status='PENDING'
                  AND i.expires_at>CURRENT_TIMESTAMP(3)
                ORDER BY i.created_at DESC
                """, Map.of("studentId", user.studentId()));
    }

    public BedHoldService.HoldResult holdTeam(long batchId, long teamId, List<Long> bedIds, CurrentUser user) {
        Map<String, Object> team = leaderTeam(teamId, user.studentId(), "LOCKED");
        if (((Number) team.get("batch_id")).longValue() != batchId) {
            throw new BusinessException("TEAM_BATCH_MISMATCH", "队伍不属于当前批次");
        }
        int members = count("SELECT COUNT(*) FROM selection_team_member WHERE team_id=:id AND member_status='LOCKED'", Map.of("id", teamId));
        if (members != bedIds.size()) {
            throw new BusinessException("TEAM_BED_COUNT_MISMATCH", "所选床位数量必须等于锁定成员数量");
        }
        List<Map<String, Object>> beds = jdbc.queryForList("""
                SELECT bed.id, bed.room_id, r.gender_restriction,
                       EXISTS(SELECT 1 FROM bed_assignment a WHERE a.batch_id=:batchId AND a.bed_id=bed.id) AS assigned
                FROM bed JOIN room r ON r.id=bed.room_id WHERE bed.id IN (:bedIds)
                  AND bed.operational_status='ENABLED' AND r.operational_status='ENABLED'
                """, new MapSqlParameterSource().addValue("batchId", batchId).addValue("bedIds", bedIds));
        if (beds.size() != bedIds.size() || beds.stream().map(row -> row.get("room_id")).distinct().count() != 1
                || beds.stream().anyMatch(row -> ((Number) row.get("assigned")).intValue() == 1)) {
            throw new BusinessException("TEAM_BEDS_INVALID", "队伍床位必须属于同一房间且全部可用");
        }
        long roomId = ((Number) beds.getFirst().get("room_id")).longValue();
        ensureRoomInScope(batchId, roomId);
        String gender = String.valueOf(profile(user).get("gender"));
        if (beds.stream().anyMatch(row -> !gender.equals(row.get("gender_restriction")))) {
            throw new BusinessException("ROOM_GENDER_MISMATCH", "队伍性别与房间不一致");
        }
        Map<String, Object> batch = accessibleBatch(batchId, user.studentId(), Set.of("OPEN"));
        BedHoldService.HoldResult result = holdService.holdTeam(batchId, bedIds, teamId,
                Duration.ofSeconds(((Number) batch.get("hold_duration_seconds")).longValue()));
        eventHub.publish(batchId, roomId, "TEAM_BEDS_HELD", Map.of("bedIds", bedIds));
        return result;
    }

    @Transactional
    public void confirmTeam(long batchId, long teamId, List<Long> bedIds, String token, CurrentUser user) {
        Map<String, Object> team = leaderTeam(teamId, user.studentId(), "LOCKED");
        if (((Number) team.get("batch_id")).longValue() != batchId) {
            throw new BusinessException("TEAM_BATCH_MISMATCH", "队伍不属于当前批次");
        }
        List<Long> memberIds = jdbc.query("""
                SELECT student_id FROM selection_team_member
                WHERE team_id=:teamId AND member_status='LOCKED'
                ORDER BY member_role='LEADER' DESC, id
                """, Map.of("teamId", teamId), (rs, rowNum) -> rs.getLong(1));
        if (memberIds.size() != bedIds.size()) {
            throw new BusinessException("TEAM_BED_COUNT_MISMATCH", "队伍成员和床位数量不一致");
        }
        for (Long bedId : bedIds) {
            if (!holdService.validateTeam(batchId, bedId, teamId, token)) {
                throw new BusinessException("HOLD_TOKEN_INVALID", "队伍临时占用已失效", HttpStatus.CONFLICT);
            }
        }
        long roomId = roomIdForBed(bedIds.getFirst());
        for (int index = 0; index < memberIds.size(); index++) {
            long studentId = memberIds.get(index);
            long bedId = bedIds.get(index);
            GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
            jdbc.update("""
                    INSERT INTO bed_assignment
                    (batch_id, student_id, bed_id, team_id, assignment_method,
                     assignment_status, assigned_at)
                    VALUES (:batchId, :studentId, :bedId, :teamId, 'TEAM_SELECT',
                            'ACTIVE', CURRENT_TIMESTAMP(3))
                    """, new MapSqlParameterSource().addValue("batchId", batchId)
                    .addValue("studentId", studentId).addValue("bedId", bedId).addValue("teamId", teamId),
                    keyHolder, new String[]{"id"});
            jdbc.update("""
                    INSERT INTO assignment_history
                    (assignment_id, batch_id, student_id, bed_id, event_type,
                     assignment_method, reason, occurred_at)
                    VALUES (:assignmentId, :batchId, :studentId, :bedId, 'CREATED',
                            'TEAM_SELECT', '队伍整体选寝', CURRENT_TIMESTAMP(3))
                    """, new MapSqlParameterSource().addValue("assignmentId", keyHolder.getKey().longValue())
                    .addValue("batchId", batchId).addValue("studentId", studentId).addValue("bedId", bedId));
        }
        jdbc.update("UPDATE selection_team SET team_status='COMPLETED' WHERE id=:id", Map.of("id", teamId));
        afterCommit(() -> {
            holdService.releaseTeam(batchId, bedIds, teamId, token);
            eventHub.publish(batchId, roomId, "TEAM_ASSIGNED", Map.of("bedIds", bedIds));
        });
        auditService.success(user, "TEAM_ASSIGN", "SELECTION_TEAM", teamId, null, null,
                Map.of("batchId", batchId, "bedIds", bedIds));
    }

    private Map<String, Object> selectableBed(long batchId, long bedId, long studentId) {
        Map<String, Object> batch = accessibleBatch(batchId, studentId, Set.of("OPEN"));
        if (count("SELECT COUNT(*) FROM bed_assignment WHERE batch_id=:batchId AND student_id=:studentId",
                new MapSqlParameterSource().addValue("batchId", batchId).addValue("studentId", studentId)) > 0) {
            throw new BusinessException("STUDENT_ALREADY_ASSIGNED", "你已经完成选寝", HttpStatus.CONFLICT);
        }
        Map<String, Object> bed = one("""
                SELECT bed.id, bed.room_id, bed.operational_status, r.gender_restriction,
                       r.operational_status AS room_status, s.gender,
                       EXISTS(SELECT 1 FROM bed_assignment a WHERE a.batch_id=:batchId AND a.bed_id=bed.id) AS assigned
                FROM bed JOIN room r ON r.id=bed.room_id JOIN student s ON s.id=:studentId
                WHERE bed.id=:bedId
                """, new MapSqlParameterSource().addValue("batchId", batchId)
                .addValue("studentId", studentId).addValue("bedId", bedId), "BED_NOT_FOUND", "床位不存在");
        if (!"ENABLED".equals(bed.get("operational_status")) || !"ENABLED".equals(bed.get("room_status"))
                || ((Number) bed.get("assigned")).intValue() == 1) {
            throw new BusinessException("BED_NOT_AVAILABLE", "床位不可用或已经分配", HttpStatus.CONFLICT);
        }
        if (!bed.get("gender").equals(bed.get("gender_restriction"))) {
            throw new BusinessException("ROOM_GENDER_MISMATCH", "学生性别与房间不一致", HttpStatus.FORBIDDEN);
        }
        ensureRoomInScope(batchId, ((Number) bed.get("room_id")).longValue());
        bed.putAll(batch);
        return bed;
    }

    private Map<String, Object> accessibleBatch(long batchId, long studentId, Set<String> statuses) {
        Map<String, Object> batch = one("""
                SELECT sb.*, e.eligibility_status
                FROM selection_batch sb JOIN batch_student_eligibility e ON e.batch_id=sb.id
                WHERE sb.id=:batchId AND e.student_id=:studentId
                """, new MapSqlParameterSource().addValue("batchId", batchId).addValue("studentId", studentId),
                "BATCH_NOT_ACCESSIBLE", "选寝批次不存在或你没有资格");
        if (!"ELIGIBLE".equals(batch.get("eligibility_status")) || !statuses.contains(String.valueOf(batch.get("batch_status")))) {
            throw new BusinessException("BATCH_NOT_ACCESSIBLE", "当前批次状态或资格不允许该操作", HttpStatus.FORBIDDEN);
        }
        return batch;
    }

    private void ensureRoomInScope(long batchId, long roomId) {
        int count = count("""
                SELECT COUNT(*) FROM room r JOIN dormitory_floor f ON f.id=r.floor_id
                WHERE r.id=:roomId AND (
                    EXISTS (SELECT 1 FROM batch_room_scope rs WHERE rs.batch_id=:batchId AND rs.room_id=r.id)
                    OR EXISTS (SELECT 1 FROM batch_building_scope bs WHERE bs.batch_id=:batchId AND bs.building_id=f.building_id)
                )
                """, new MapSqlParameterSource().addValue("batchId", batchId).addValue("roomId", roomId));
        if (count == 0) {
            throw new BusinessException("ROOM_OUT_OF_SCOPE", "房间不在当前批次可选范围", HttpStatus.FORBIDDEN);
        }
    }

    private Map<String, Object> leaderTeam(long teamId, long studentId, String status) {
        Map<String, Object> team = one("""
                SELECT * FROM selection_team WHERE id=:teamId AND leader_student_id=:studentId
                """, new MapSqlParameterSource().addValue("teamId", teamId).addValue("studentId", studentId),
                "TEAM_NOT_FOUND", "队伍不存在或你不是队长");
        if (!status.equals(team.get("team_status"))) {
            throw new BusinessException("TEAM_STATUS_INVALID", "当前队伍状态不允许该操作");
        }
        return team;
    }

    private String featureJson(long batchId, long studentId) {
        List<String> values = jdbc.query("""
                SELECT feature_vector_json FROM student_feature
                WHERE batch_id=:batchId AND student_id=:studentId
                """, new MapSqlParameterSource().addValue("batchId", batchId).addValue("studentId", studentId),
                (rs, rowNum) -> rs.getString(1));
        return values.isEmpty() ? null : values.getFirst();
    }

    private Object normalize(Object value) {
        if (value instanceof String string && string.matches("^\\d{2}:\\d{2}$")) {
            LocalTime time = LocalTime.parse(string);
            return time.getHour() * 60 + time.getMinute();
        }
        if (value instanceof Map<?, ?> map && map.containsKey("value")) {
            return map.get("value");
        }
        return value;
    }

    private long roomIdForBed(long bedId) {
        return ((Number) one("SELECT room_id FROM bed WHERE id=:id", Map.of("id", bedId),
                "BED_NOT_FOUND", "床位不存在").get("room_id")).longValue();
    }

    private int count(String sql, MapSqlParameterSource parameters) {
        Integer value = jdbc.queryForObject(sql, parameters, Integer.class);
        return value == null ? 0 : value;
    }

    private int count(String sql, Map<String, ?> parameters) {
        Integer value = jdbc.queryForObject(sql, parameters, Integer.class);
        return value == null ? 0 : value;
    }

    private Map<String, Object> one(String sql, Map<String, ?> parameters, String code, String message) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, parameters);
        if (rows.isEmpty()) {
            throw new BusinessException(code, message, HttpStatus.NOT_FOUND);
        }
        return rows.getFirst();
    }

    private Map<String, Object> one(String sql, MapSqlParameterSource parameters, String code, String message) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, parameters);
        if (rows.isEmpty()) {
            throw new BusinessException(code, message, HttpStatus.NOT_FOUND);
        }
        return rows.getFirst();
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("JSON_ERROR", "数据序列化失败", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private void afterCommit(Runnable runnable) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    runnable.run();
                }
            });
        } else {
            runnable.run();
        }
    }
}
