package com.wust.dormitory.waitlist;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wust.dormitory.audit.AuditService;
import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.residency.ResidencyPolicyService;
import com.wust.dormitory.residency.ResidencyService;
import com.wust.dormitory.security.CurrentUser;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class WaitlistService {
    private static final String SETTING_KEY = "WAITLIST_POLICY";
    private static final Set<String> PRIORITY_MODES =
            Set.of("PRIORITY_THEN_FIFO", "FIFO");
    private static final Set<String> ACTIVE_ENTRY_STATUSES =
            Set.of("WAITING", "OFFERED");

    private final NamedParameterJdbcTemplate jdbc;
    private final ResidencyPolicyService residencyPolicy;
    private final ResidencyService residencyService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public WaitlistService(
            NamedParameterJdbcTemplate jdbc,
            ResidencyPolicyService residencyPolicy,
            ResidencyService residencyService,
            AuditService auditService,
            ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.residencyPolicy = residencyPolicy;
        this.residencyService = residencyService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> policy() {
        WaitlistPolicy value = currentPolicy();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", value.enabled());
        result.put("offerTtlMinutes", value.offerTtlMinutes());
        result.put("priorityMode", value.priorityMode());
        result.put("scanBatchSize", value.scanBatchSize());
        return result;
    }

    public List<Map<String, Object>> candidates(long studentId) {
        requireEnabled();
        requireNoActiveResidency(studentId, false);
        Map<String, Object> student = residencyPolicy.student(studentId);
        return query("""
                SELECT room.id AS room_id, room.room_number, room.room_type,
                       room.capacity, room.gender_restriction, room.resident_scope,
                       building.building_name, floor.floor_number,
                       bed.id AS bed_id, bed.bed_code, bed.bed_type,
                       (SELECT COUNT(*) FROM room_assignment current_residency
                         WHERE current_residency.room_id=room.id
                           AND current_residency.assignment_status='ACTIVE')
                           AS active_resident_count,
                       CASE WHEN bed.id IS NULL THEN 0 ELSE
                         (SELECT COUNT(*) FROM room_assignment bed_residency
                           WHERE bed_residency.bed_id=bed.id
                             AND bed_residency.assignment_status='ACTIVE')
                       END AS bed_occupied
                FROM room
                JOIN dormitory_floor floor ON floor.id=room.floor_id
                JOIN dormitory_building building ON building.id=floor.building_id
                LEFT JOIN bed
                  ON bed.room_id=room.id AND bed.operational_status='ENABLED'
                WHERE room.operational_status='ENABLED'
                ORDER BY building.building_code, floor.floor_number,
                         room.room_number, bed.position_index
                """).stream()
                .filter(row -> compatible(
                        student,
                        residencyPolicy.room(longValue(row.get("room_id")), false)))
                .peek(row -> row.put(
                        "available_capacity",
                        Math.max(0,
                                intValue(row.get("capacity"))
                                        - intValue(row.get("active_resident_count")))))
                .toList();
    }

    public List<Map<String, Object>> listMy(long studentId) {
        return listEntries("ALL", "", studentId);
    }

    public List<Map<String, Object>> listAdmin(String status, String keyword) {
        return listEntries(
                status == null || status.isBlank() ? "ALL" : status,
                keyword == null ? "" : keyword.trim(),
                null);
    }

    @Transactional
    public Map<String, Object> join(
            long studentId,
            long targetRoomId,
            Long targetBedId,
            String reason,
            CurrentUser user) {
        requireEnabled();
        lockStudent(studentId);
        requireNoActiveResidency(studentId, true);
        Map<String, Object> student = residencyPolicy.student(studentId);
        Map<String, Object> room = residencyPolicy.room(targetRoomId, true);
        requireCompatible(student, room);
        validateTargetBed(targetRoomId, targetBedId);

        try {
            GeneratedKeyHolder keys = new GeneratedKeyHolder();
            jdbc.update("""
                    INSERT INTO waitlist_entry
                    (student_id, target_room_id, target_bed_id,
                     priority_score, entry_status, reason, joined_at)
                    VALUES
                    (:studentId,:roomId,:bedId,0,'WAITING',:reason,
                     CURRENT_TIMESTAMP(3))
                    """, new MapSqlParameterSource()
                    .addValue("studentId", studentId)
                    .addValue("roomId", targetRoomId)
                    .addValue("bedId", targetBedId)
                    .addValue("reason", requireReason(reason)),
                    keys,
                    new String[]{"id"});
            long entryId = keys.getKey().longValue();
            Map<String, Object> after = entry(entryId);
            audit(user, "WAITLIST_JOIN", "WAITLIST_ENTRY", entryId,
                    reason, null, after);
            return after;
        } catch (DataIntegrityViolationException exception) {
            throw conflict(
                    "WAITLIST_ACTIVE_EXISTS",
                    "你已经有一条进行中的候补记录");
        }
    }

    @Transactional
    public Map<String, Object> withdraw(
            long entryId,
            long studentId,
            String reason,
            CurrentUser user) {
        Map<String, Object> before = lockEntry(entryId);
        if (longValue(before.get("student_id")) != studentId) {
            throw notFound("WAITLIST_ENTRY_NOT_FOUND", "候补记录不存在");
        }
        requireEntryStatus(before, ACTIVE_ENTRY_STATUSES);
        String normalizedReason = requireReason(reason);
        jdbc.update("""
                UPDATE waitlist_offer
                SET offer_status='CANCELLED', responded_at=CURRENT_TIMESTAMP(3),
                    response_reason=:reason, updated_at=CURRENT_TIMESTAMP(3)
                WHERE entry_id=:entryId AND offer_status='ACTIVE'
                """, Map.of("entryId", entryId, "reason", normalizedReason));
        jdbc.update("""
                UPDATE waitlist_entry
                SET entry_status='WITHDRAWN', withdrawn_at=CURRENT_TIMESTAMP(3),
                    exit_reason=:reason, updated_at=CURRENT_TIMESTAMP(3)
                WHERE id=:entryId
                """, Map.of("entryId", entryId, "reason", normalizedReason));
        Map<String, Object> after = entry(entryId);
        audit(user, "WAITLIST_WITHDRAW", "WAITLIST_ENTRY", entryId,
                normalizedReason, before, after);
        return after;
    }

    @Transactional
    public Map<String, Object> accept(
            long offerId,
            long studentId,
            String reason,
            CurrentUser user) {
        Map<String, Object> offer = lockOffer(offerId);
        requireOfferOwner(offer, studentId);
        requireActiveOffer(offer);
        Map<String, Object> waitlistEntry =
                lockEntry(longValue(offer.get("entry_id")));
        lockStudent(studentId);
        requireNoActiveResidency(studentId, true);

        long roomId = longValue(offer.get("room_id"));
        Long bedId = nullableLong(offer.get("bed_id"));
        Map<String, Object> room = residencyPolicy.room(roomId, true);
        requireCompatible(residencyPolicy.student(studentId), room);
        requireResourceAvailable(roomId, bedId);

        String normalizedReason = requireReason(reason);
        Map<String, Object> residency = residencyService.assign(
                studentId,
                roomId,
                bedId,
                null,
                null,
                "DIRECT",
                "WAITLIST_ASSIGNMENT",
                normalizedReason,
                user);
        jdbc.update("""
                UPDATE waitlist_offer
                SET offer_status='ASSIGNED', responded_at=CURRENT_TIMESTAMP(3),
                    response_reason=:reason,
                    assigned_residency_id=:residencyId,
                    updated_at=CURRENT_TIMESTAMP(3)
                WHERE id=:offerId
                """, new MapSqlParameterSource()
                .addValue("offerId", offerId)
                .addValue("reason", normalizedReason)
                .addValue("residencyId", residency.get("id")));
        jdbc.update("""
                UPDATE waitlist_entry
                SET entry_status='ASSIGNED', assigned_at=CURRENT_TIMESTAMP(3),
                    updated_at=CURRENT_TIMESTAMP(3)
                WHERE id=:entryId
                """, Map.of("entryId", waitlistEntry.get("id")));
        notifyStudent(
                studentId,
                "WAITLIST_ASSIGNED",
                "notification.waitlistAssigned.title",
                "notification.waitlistAssigned.message",
                notificationParameters(waitlistEntry, offer));
        Map<String, Object> after = entry(longValue(waitlistEntry.get("id")));
        audit(user, "WAITLIST_ACCEPT", "WAITLIST_OFFER", offerId,
                normalizedReason, offer, after);
        return after;
    }

    @Transactional
    public Map<String, Object> reject(
            long offerId,
            long studentId,
            String reason,
            CurrentUser user) {
        Map<String, Object> offer = lockOffer(offerId);
        requireOfferOwner(offer, studentId);
        requireActiveOffer(offer);
        Map<String, Object> waitlistEntry =
                lockEntry(longValue(offer.get("entry_id")));
        String normalizedReason = requireReason(reason);
        jdbc.update("""
                UPDATE waitlist_offer
                SET offer_status='REJECTED', responded_at=CURRENT_TIMESTAMP(3),
                    response_reason=:reason, updated_at=CURRENT_TIMESTAMP(3)
                WHERE id=:offerId
                """, Map.of("offerId", offerId, "reason", normalizedReason));
        jdbc.update("""
                UPDATE waitlist_entry
                SET entry_status='WITHDRAWN', withdrawn_at=CURRENT_TIMESTAMP(3),
                    exit_reason=:reason, updated_at=CURRENT_TIMESTAMP(3)
                WHERE id=:entryId
                """, Map.of(
                        "entryId", waitlistEntry.get("id"),
                        "reason", normalizedReason));
        Map<String, Object> after = entry(longValue(waitlistEntry.get("id")));
        audit(user, "WAITLIST_REJECT", "WAITLIST_OFFER", offerId,
                normalizedReason, offer, after);
        return after;
    }

    @Transactional
    public Map<String, Object> updateSettings(
            boolean enabled,
            int offerTtlMinutes,
            String priorityMode,
            int scanBatchSize,
            String reason,
            CurrentUser admin) {
        WaitlistPolicy next = validatePolicy(
                enabled, offerTtlMinutes, priorityMode, scanBatchSize);
        String normalizedReason = requireReason(reason);
        Map<String, Object> before = policy();
        jdbc.update("""
                INSERT INTO system_setting
                (setting_key, setting_value, version, updated_by)
                VALUES (:settingKey,:value,0,:updatedBy)
                ON DUPLICATE KEY UPDATE
                    setting_value=VALUES(setting_value),
                    version=version+1,
                    updated_by=VALUES(updated_by),
                    updated_at=CURRENT_TIMESTAMP(3)
                """, new MapSqlParameterSource()
                .addValue("settingKey", SETTING_KEY)
                .addValue("value", serializePolicy(next))
                .addValue("updatedBy", admin.userId()));
        Map<String, Object> after = policy();
        audit(admin, "WAITLIST_POLICY_UPDATE", "SYSTEM_SETTING", 0L,
                normalizedReason, before, after);
        return after;
    }

    @Transactional
    public Map<String, Object> updatePriority(
            long entryId,
            int priorityScore,
            String reason,
            CurrentUser admin) {
        if (priorityScore < -100000 || priorityScore > 100000) {
            throw new BusinessException(
                    "WAITLIST_PRIORITY_INVALID",
                    "候补优先分必须在-100000至100000之间");
        }
        Map<String, Object> before = lockEntry(entryId);
        requireEntryStatus(before, Set.of("WAITING"));
        String normalizedReason = requireReason(reason);
        jdbc.update("""
                UPDATE waitlist_entry
                SET priority_score=:score, priority_reason=:reason,
                    priority_updated_by=:updatedBy,
                    updated_at=CURRENT_TIMESTAMP(3)
                WHERE id=:entryId
                """, new MapSqlParameterSource()
                .addValue("entryId", entryId)
                .addValue("score", priorityScore)
                .addValue("reason", normalizedReason)
                .addValue("updatedBy", admin.userId()));
        Map<String, Object> after = entry(entryId);
        audit(admin, "WAITLIST_PRIORITY_UPDATE", "WAITLIST_ENTRY", entryId,
                normalizedReason, before, after);
        return after;
    }

    @Transactional
    public Map<String, Object> createOffer(
            long entryId,
            String reason,
            CurrentUser operator) {
        WaitlistPolicy policy = requireEnabled();
        Map<String, Object> waitlistEntry = lockEntry(entryId);
        requireEntryStatus(waitlistEntry, Set.of("WAITING"));
        long studentId = longValue(waitlistEntry.get("student_id"));
        lockStudent(studentId);
        requireNoActiveResidency(studentId, true);

        long roomId = longValue(waitlistEntry.get("target_room_id"));
        Long bedId = nullableLong(waitlistEntry.get("target_bed_id"));
        Map<String, Object> room = residencyPolicy.room(roomId, true);
        requireCompatible(residencyPolicy.student(studentId), room);
        requireResourceAvailable(roomId, bedId);
        String normalizedReason = requireReason(reason);

        try {
            GeneratedKeyHolder keys = new GeneratedKeyHolder();
            jdbc.update("""
                    INSERT INTO waitlist_offer
                    (entry_id, student_id, room_id, bed_id,
                     offer_status, offered_at, expires_at,
                     generated_by, generation_reason)
                    VALUES
                    (:entryId,:studentId,:roomId,:bedId,
                     'ACTIVE',CURRENT_TIMESTAMP(3),
                     TIMESTAMPADD(MINUTE,:ttl,CURRENT_TIMESTAMP(3)),
                     :generatedBy,:reason)
                    """, new MapSqlParameterSource()
                    .addValue("entryId", entryId)
                    .addValue("studentId", studentId)
                    .addValue("roomId", roomId)
                    .addValue("bedId", bedId)
                    .addValue("ttl", policy.offerTtlMinutes())
                    .addValue("generatedBy",
                            operator == null ? null : operator.userId())
                    .addValue("reason", normalizedReason),
                    keys,
                    new String[]{"id"});
            long offerId = keys.getKey().longValue();
            jdbc.update("""
                    UPDATE waitlist_entry
                    SET entry_status='OFFERED', updated_at=CURRENT_TIMESTAMP(3)
                    WHERE id=:entryId
                    """, Map.of("entryId", entryId));
            Map<String, Object> offer = offer(offerId);
            notifyStudent(
                    studentId,
                    "WAITLIST_OFFERED",
                    "notification.waitlistOffered.title",
                    "notification.waitlistOffered.message",
                    notificationParameters(waitlistEntry, offer));
            audit(operator, "WAITLIST_OFFER_CREATE", "WAITLIST_OFFER", offerId,
                    normalizedReason, waitlistEntry, offer);
            return offer;
        } catch (DataIntegrityViolationException exception) {
            throw conflict(
                    "WAITLIST_RESOURCE_UNAVAILABLE",
                    "该学生或候补资源已经存在活动邀请");
        }
    }

    @Transactional
    public Map<String, Object> directAssign(
            long entryId,
            String reason,
            CurrentUser admin) {
        Map<String, Object> waitlistEntry = lockEntry(entryId);
        requireEntryStatus(waitlistEntry, Set.of("WAITING"));
        long studentId = longValue(waitlistEntry.get("student_id"));
        lockStudent(studentId);
        requireNoActiveResidency(studentId, true);

        long roomId = longValue(waitlistEntry.get("target_room_id"));
        Long bedId = nullableLong(waitlistEntry.get("target_bed_id"));
        Map<String, Object> room = residencyPolicy.room(roomId, true);
        requireCompatible(residencyPolicy.student(studentId), room);
        requireResourceAvailable(roomId, bedId);
        String normalizedReason = requireReason(reason);

        Map<String, Object> residency = residencyService.assign(
                studentId,
                roomId,
                bedId,
                null,
                null,
                "DIRECT",
                "WAITLIST_ASSIGNMENT",
                normalizedReason,
                admin);
        jdbc.update("""
                UPDATE waitlist_entry
                SET entry_status='ASSIGNED', assigned_at=CURRENT_TIMESTAMP(3),
                    updated_at=CURRENT_TIMESTAMP(3)
                WHERE id=:entryId
                """, Map.of("entryId", entryId));
        jdbc.update("""
                INSERT INTO waitlist_offer
                (entry_id, student_id, room_id, bed_id,
                 offer_status, offered_at, expires_at,
                 responded_at, response_reason,
                 generated_by, generation_reason, assigned_residency_id)
                VALUES
                (:entryId,:studentId,:roomId,:bedId,
                 'ASSIGNED',CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3),
                 CURRENT_TIMESTAMP(3),:reason,:generatedBy,:reason,:residencyId)
                """, new MapSqlParameterSource()
                .addValue("entryId", entryId)
                .addValue("studentId", studentId)
                .addValue("roomId", roomId)
                .addValue("bedId", bedId)
                .addValue("reason", normalizedReason)
                .addValue("generatedBy", admin.userId())
                .addValue("residencyId", residency.get("id")));
        notifyStudent(
                studentId,
                "WAITLIST_ASSIGNED",
                "notification.waitlistAssigned.title",
                "notification.waitlistAssigned.message",
                Map.of(
                        "entryId", entryId,
                        "roomId", roomId,
                        "bedId", bedId == null ? "" : bedId));
        Map<String, Object> after = entry(entryId);
        audit(admin, "WAITLIST_DIRECT_ASSIGN", "WAITLIST_ENTRY", entryId,
                normalizedReason, waitlistEntry, after);
        return after;
    }

    @Transactional
    public int expireOffers() {
        List<Map<String, Object>> expired = query("""
                SELECT id, entry_id, student_id, room_id, bed_id, expires_at
                FROM waitlist_offer
                WHERE offer_status='ACTIVE'
                  AND expires_at<=CURRENT_TIMESTAMP(3)
                ORDER BY id FOR UPDATE
                """);
        for (Map<String, Object> offer : expired) {
            long offerId = longValue(offer.get("id"));
            long entryId = longValue(offer.get("entry_id"));
            jdbc.update("""
                    UPDATE waitlist_offer
                    SET offer_status='EXPIRED', responded_at=CURRENT_TIMESTAMP(3),
                        response_reason='邀请已超时', updated_at=CURRENT_TIMESTAMP(3)
                    WHERE id=:offerId AND offer_status='ACTIVE'
                    """, Map.of("offerId", offerId));
            jdbc.update("""
                    UPDATE waitlist_entry
                    SET entry_status='EXPIRED', exit_reason='邀请已超时',
                        updated_at=CURRENT_TIMESTAMP(3)
                    WHERE id=:entryId AND entry_status='OFFERED'
                    """, Map.of("entryId", entryId));
            notifyStudent(
                    longValue(offer.get("student_id")),
                    "WAITLIST_EXPIRED",
                    "notification.waitlistExpired.title",
                    "notification.waitlistExpired.message",
                    notificationParameters(Map.of("id", entryId), offer));
        }
        return expired.size();
    }

    @Transactional
    public Map<String, Integer> scanAvailableResources(CurrentUser operator) {
        WaitlistPolicy policy = currentPolicy();
        if (!policy.enabled()) {
            return scanResult(0, 0, 0, 0);
        }
        String orderBy = "FIFO".equals(policy.priorityMode())
                ? "joined_at ASC, id ASC"
                : "priority_score DESC, joined_at ASC, id ASC";
        List<Long> entryIds = jdbc.query(
                "SELECT id FROM waitlist_entry WHERE entry_status='WAITING' "
                        + "ORDER BY " + orderBy + " LIMIT :limit",
                Map.of("limit", policy.scanBatchSize()),
                (resultSet, rowNumber) -> resultSet.getLong(1));
        int offered = 0;
        int skipped = 0;
        for (Long entryId : entryIds) {
            try {
                createOffer(entryId, "系统检测到候补资源可用", operator);
                offered++;
            } catch (BusinessException | DataIntegrityViolationException exception) {
                skipped++;
            }
        }
        Map<String, Integer> result =
                scanResult(0, entryIds.size(), offered, skipped);
        if (operator != null) {
            audit(operator, "WAITLIST_SCAN", "WAITLIST_ENTRY", 0L,
                    "管理员手动触发候补扫描", null, result);
        }
        return result;
    }

    private Map<String, Integer> scanResult(
            int expired,
            int scanned,
            int offered,
            int skipped) {
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("expired", expired);
        result.put("scanned", scanned);
        result.put("offered", offered);
        result.put("skipped", skipped);
        return result;
    }

    private List<Map<String, Object>> listEntries(
            String status,
            String keyword,
            Long studentId) {
        return jdbc.queryForList("""
                SELECT entry.*,
                       student.student_number, student.student_name,
                       building.building_name, floor.floor_number,
                       room.room_number, room.room_type,
                       bed.bed_code, bed.bed_type,
                       latest_offer.id AS offer_id,
                       latest_offer.offer_status,
                       latest_offer.offered_at,
                       latest_offer.expires_at,
                       latest_offer.responded_at,
                       latest_offer.response_reason,
                       latest_offer.assigned_residency_id
                FROM waitlist_entry entry
                JOIN student ON student.id=entry.student_id
                JOIN room ON room.id=entry.target_room_id
                JOIN dormitory_floor floor ON floor.id=room.floor_id
                JOIN dormitory_building building ON building.id=floor.building_id
                LEFT JOIN bed ON bed.id=entry.target_bed_id
                LEFT JOIN waitlist_offer latest_offer
                  ON latest_offer.id=(
                      SELECT MAX(candidate_offer.id)
                      FROM waitlist_offer candidate_offer
                      WHERE candidate_offer.entry_id=entry.id)
                WHERE (:studentId IS NULL OR entry.student_id=:studentId)
                  AND (:status='ALL' OR entry.entry_status=:status)
                  AND (:keyword=''
                       OR student.student_number LIKE CONCAT('%',:keyword,'%')
                       OR student.student_name LIKE CONCAT('%',:keyword,'%')
                       OR building.building_name LIKE CONCAT('%',:keyword,'%')
                       OR room.room_number LIKE CONCAT('%',:keyword,'%'))
                ORDER BY CASE WHEN entry.entry_status='OFFERED' THEN 0
                              WHEN entry.entry_status='WAITING' THEN 1 ELSE 2 END,
                         entry.priority_score DESC, entry.joined_at ASC
                """, new MapSqlParameterSource()
                .addValue("studentId", studentId)
                .addValue("status", status)
                .addValue("keyword", keyword));
    }

    private Map<String, Object> entry(long entryId) {
        return listEntries("ALL", "", null).stream()
                .filter(row -> longValue(row.get("id")) == entryId)
                .findFirst()
                .orElseThrow(() -> notFound(
                        "WAITLIST_ENTRY_NOT_FOUND", "候补记录不存在"));
    }

    private Map<String, Object> offer(long offerId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT * FROM waitlist_offer WHERE id=:offerId
                """, Map.of("offerId", offerId));
        if (rows.isEmpty()) {
            throw notFound("WAITLIST_OFFER_NOT_FOUND", "候补邀请不存在");
        }
        return rows.getFirst();
    }

    private Map<String, Object> lockEntry(long entryId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT * FROM waitlist_entry WHERE id=:entryId FOR UPDATE
                """, Map.of("entryId", entryId));
        if (rows.isEmpty()) {
            throw notFound("WAITLIST_ENTRY_NOT_FOUND", "候补记录不存在");
        }
        return rows.getFirst();
    }

    private Map<String, Object> lockOffer(long offerId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT * FROM waitlist_offer WHERE id=:offerId FOR UPDATE
                """, Map.of("offerId", offerId));
        if (rows.isEmpty()) {
            throw notFound("WAITLIST_OFFER_NOT_FOUND", "候补邀请不存在");
        }
        return rows.getFirst();
    }

    private void requireActiveOffer(Map<String, Object> offer) {
        if (!"ACTIVE".equals(String.valueOf(offer.get("offer_status")))) {
            throw conflict(
                    "WAITLIST_OFFER_STATE_INVALID",
                    "候补邀请当前状态不可处理");
        }
        if (!deadline(offer.get("expires_at")).isAfter(Instant.now())) {
            throw conflict("WAITLIST_OFFER_EXPIRED", "候补邀请已经超时");
        }
    }

    private Instant deadline(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        String text = String.valueOf(value).trim().replace(' ', 'T');
        return Instant.parse(text.endsWith("Z") ? text : text + "Z");
    }

    private void requireOfferOwner(Map<String, Object> offer, long studentId) {
        if (longValue(offer.get("student_id")) != studentId) {
            throw notFound("WAITLIST_OFFER_NOT_FOUND", "候补邀请不存在");
        }
    }

    private void requireEntryStatus(
            Map<String, Object> waitlistEntry,
            Set<String> allowed) {
        if (!allowed.contains(String.valueOf(waitlistEntry.get("entry_status")))) {
            throw conflict(
                    "WAITLIST_ENTRY_STATE_INVALID",
                    "候补记录当前状态不可处理");
        }
    }

    private void lockStudent(long studentId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id FROM student WHERE id=:studentId FOR UPDATE
                """, Map.of("studentId", studentId));
        if (rows.isEmpty()) {
            throw notFound("STUDENT_NOT_FOUND", "学生不存在");
        }
    }

    private void requireNoActiveResidency(long studentId, boolean lock) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id FROM room_assignment
                WHERE student_id=:studentId AND assignment_status='ACTIVE'
                """ + (lock ? " FOR UPDATE" : ""),
                Map.of("studentId", studentId));
        if (!rows.isEmpty()) {
            throw conflict(
                    "WAITLIST_RESIDENCY_CONFLICT",
                    "已有有效住宿记录的学生不能参加候补");
        }
    }

    private void validateTargetBed(long roomId, Long bedId) {
        if (bedId == null) return;
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, operational_status FROM bed
                WHERE id=:bedId AND room_id=:roomId
                """, new MapSqlParameterSource()
                .addValue("bedId", bedId)
                .addValue("roomId", roomId));
        if (rows.isEmpty()
                || !"ENABLED".equals(String.valueOf(
                        rows.getFirst().get("operational_status")))) {
            throw new BusinessException(
                    "WAITLIST_TARGET_INVALID",
                    "目标床位不存在或不可用");
        }
    }

    private void requireResourceAvailable(long roomId, Long bedId) {
        if (bedId == null) {
            residencyPolicy.requireRoomCapacity(roomId, 1);
        } else {
            residencyPolicy.requireAvailableBed(roomId, bedId);
        }
    }

    private void requireCompatible(
            Map<String, Object> student,
            Map<String, Object> room) {
        if (!compatible(student, room)) {
            throw conflict(
                    "WAITLIST_TARGET_INVALID",
                    "学生不符合目标寝室的性别或学生类别要求");
        }
    }

    private boolean compatible(
            Map<String, Object> student,
            Map<String, Object> room) {
        return "ENABLED".equals(String.valueOf(room.get("operational_status")))
                && String.valueOf(student.get("gender"))
                        .equals(String.valueOf(room.get("gender_restriction")))
                && residencyPolicy.roomAllowsCategory(
                        String.valueOf(room.get("resident_scope")),
                        String.valueOf(student.get("student_category")),
                        false);
    }

    private WaitlistPolicy requireEnabled() {
        WaitlistPolicy value = currentPolicy();
        if (!value.enabled()) {
            throw conflict("WAITLIST_DISABLED", "学校当前未开放候补补位");
        }
        return value;
    }

    private WaitlistPolicy currentPolicy() {
        List<String> rows = jdbc.query("""
                SELECT setting_value FROM system_setting
                WHERE setting_key=:settingKey
                """, Map.of("settingKey", SETTING_KEY),
                (resultSet, rowNumber) -> resultSet.getString(1));
        if (rows.isEmpty() || rows.getFirst() == null || rows.getFirst().isBlank()) {
            return defaultPolicy();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(
                    rows.getFirst(),
                    new TypeReference<Map<String, Object>>() { });
            return validatePolicy(
                    Boolean.parseBoolean(String.valueOf(
                            parsed.getOrDefault("enabled", false))),
                    intValue(parsed.getOrDefault("offerTtlMinutes", 30)),
                    String.valueOf(parsed.getOrDefault(
                            "priorityMode", "PRIORITY_THEN_FIFO")),
                    intValue(parsed.getOrDefault("scanBatchSize", 50)));
        } catch (JsonProcessingException | BusinessException exception) {
            return defaultPolicy();
        }
    }

    private WaitlistPolicy defaultPolicy() {
        return new WaitlistPolicy(false, 30, "PRIORITY_THEN_FIFO", 50);
    }

    private WaitlistPolicy validatePolicy(
            boolean enabled,
            int offerTtlMinutes,
            String priorityMode,
            int scanBatchSize) {
        if (offerTtlMinutes < 5 || offerTtlMinutes > 1440
                || scanBatchSize < 1 || scanBatchSize > 500
                || !PRIORITY_MODES.contains(priorityMode)) {
            throw new BusinessException(
                    "WAITLIST_POLICY_INVALID",
                    "候补策略无效：邀请有效期5至1440分钟，扫描批量1至500");
        }
        return new WaitlistPolicy(
                enabled, offerTtlMinutes, priorityMode, scanBatchSize);
    }

    private String serializePolicy(WaitlistPolicy policy) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "enabled", policy.enabled(),
                    "offerTtlMinutes", policy.offerTtlMinutes(),
                    "priorityMode", policy.priorityMode(),
                    "scanBatchSize", policy.scanBatchSize()));
        } catch (JsonProcessingException exception) {
            throw new BusinessException(
                    "WAITLIST_POLICY_INVALID",
                    "候补策略序列化失败");
        }
    }

    private void notifyStudent(
            long studentId,
            String type,
            String titleKey,
            String messageKey,
            Map<String, Object> parameters) {
        try {
            jdbc.update("""
                    INSERT INTO student_notification
                    (student_id, notification_type, title_key,
                     message_key, parameters_json)
                    VALUES
                    (:studentId,:type,:titleKey,:messageKey,:parameters)
                    """, new MapSqlParameterSource()
                    .addValue("studentId", studentId)
                    .addValue("type", type)
                    .addValue("titleKey", titleKey)
                    .addValue("messageKey", messageKey)
                    .addValue("parameters",
                            objectMapper.writeValueAsString(parameters)));
        } catch (JsonProcessingException exception) {
            throw new BusinessException(
                    "WAITLIST_NOTIFICATION_ERROR",
                    "候补通知生成失败",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private Map<String, Object> notificationParameters(
            Map<String, Object> waitlistEntry,
            Map<String, Object> offer) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("entryId", waitlistEntry.get("id"));
        result.put("offerId", offer.get("id"));
        result.put("roomId", offer.get("room_id"));
        result.put("bedId", offer.get("bed_id") == null ? "" : offer.get("bed_id"));
        result.put("expiresAt",
                offer.get("expires_at") == null ? "" : offer.get("expires_at"));
        return result;
    }

    private void audit(
            CurrentUser user,
            String action,
            String resource,
            long resourceId,
            String reason,
            Object before,
            Object after) {
        if (user != null) {
            auditService.success(
                    user,
                    action,
                    resource,
                    resourceId,
                    reason == null ? "" : reason.trim(),
                    before,
                    after);
        }
    }

    private List<Map<String, Object>> query(String sql) {
        return jdbc.queryForList(sql, Map.of());
    }

    private BusinessException notFound(String code, String message) {
        return new BusinessException(code, message, HttpStatus.NOT_FOUND);
    }

    private BusinessException conflict(String code, String message) {
        return new BusinessException(code, message, HttpStatus.CONFLICT);
    }

    private String requireReason(String reason) {
        String normalized = reason == null ? "" : reason.trim();
        if (normalized.isEmpty() || normalized.length() > 500) {
            throw new BusinessException(
                    "WAITLIST_REASON_INVALID",
                    "原因必须填写且不能超过500个字符");
        }
        return normalized;
    }

    private long longValue(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private Long nullableLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private int intValue(Object value) {
        return value == null ? 0 : ((Number) value).intValue();
    }

    private record WaitlistPolicy(
            boolean enabled,
            int offerTtlMinutes,
            String priorityMode,
            int scanBatchSize) { }
}
