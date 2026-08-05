package com.wust.dormitory.student;

import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.matching.BatchRecommendationPolicyService;
import com.wust.dormitory.matching.MatchingService;
import com.wust.dormitory.matching.RecommendationSampler;
import com.wust.dormitory.matching.RecommendationStrategy;
import com.wust.dormitory.residency.ResidencyPolicyService;
import com.wust.dormitory.security.CurrentUser;
import com.wust.dormitory.subscription.FeatureAccessService;
import com.wust.dormitory.subscription.FeatureCodes;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.stream.Collectors;

@Service
public class StudentRoomRecommendationService {
    static final String ALGORITHM_VERSION = "room-recommendation-v2";

    private final NamedParameterJdbcTemplate jdbc;
    private final MatchingService matchingService;
    private final ResidencyPolicyService policy;
    private final StudentPreferenceService preferenceService;
    private final FeatureAccessService featureAccessService;
    private final BatchRecommendationPolicyService recommendationPolicyService;
    private final SecureRandom secureRandom;

    public StudentRoomRecommendationService(
            NamedParameterJdbcTemplate jdbc,
            MatchingService matchingService,
            ResidencyPolicyService policy,
            StudentPreferenceService preferenceService,
            FeatureAccessService featureAccessService,
            BatchRecommendationPolicyService recommendationPolicyService) {
        this(
                jdbc,
                matchingService,
                policy,
                preferenceService,
                featureAccessService,
                recommendationPolicyService,
                new SecureRandom());
    }

    StudentRoomRecommendationService(
            NamedParameterJdbcTemplate jdbc,
            MatchingService matchingService,
            ResidencyPolicyService policy,
            StudentPreferenceService preferenceService,
            FeatureAccessService featureAccessService,
            BatchRecommendationPolicyService recommendationPolicyService,
            SecureRandom secureRandom) {
        this.jdbc = jdbc;
        this.matchingService = matchingService;
        this.policy = policy;
        this.preferenceService = preferenceService;
        this.featureAccessService = featureAccessService;
        this.recommendationPolicyService = recommendationPolicyService;
        this.secureRandom = secureRandom;
    }

    public List<Map<String, Object>> rooms(long batchId, CurrentUser user) {
        requireAccessibleBatch(batchId, user.studentId());
        Map<String, Object> batch = policy.batch(batchId);
        BatchRecommendationPolicyService.Policy recommendationPolicy =
                recommendationPolicyService.forBatch(batchId);
        Map<String, Object> student = policy.student(user.studentId());
        String feature = featureJson(batchId, user.studentId());
        String mode = String.valueOf(batch.get("selection_mode"));
        List<Map<String, Object>> result = new ArrayList<>();

        for (Long roomId : policy.roomIdsForBatch(batchId)) {
            Map<String, Object> room = policy.room(roomId, false);
            try {
                policy.requireStudentEligibleForRoom(student, batch, room);
                policy.requireRoomLockedByBatch(batchId, roomId);
            } catch (BusinessException ignored) {
                continue;
            }
            int activeResidents = policy.activeResidentCount(roomId);
            int unknownBedResidents = policy.unknownBedResidentCount(roomId);
            int available = "BED".equals(mode)
                    ? (unknownBedResidents == 0 ? policy.availableBedCount(batchId, roomId) : 0)
                    : policy.availableCapacity(roomId);
            if (available <= 0) {
                continue;
            }

            List<String> roommateFeatures = jdbc.query("""
                    SELECT COALESCE(sf.feature_vector_json, spp.feature_vector_json) AS feature_vector_json
                    FROM room_assignment ra
                    LEFT JOIN student_feature sf ON sf.student_id=ra.student_id AND sf.batch_id=:batchId
                    LEFT JOIN student_preference_profile spp ON spp.student_id=ra.student_id
                    WHERE ra.room_id=:roomId AND ra.assignment_status='ACTIVE'
                      AND COALESCE(sf.feature_vector_json, spp.feature_vector_json) IS NOT NULL
                    ORDER BY ra.assigned_at
                    """, new MapSqlParameterSource().addValue("batchId", batchId).addValue("roomId", roomId),
                    (rs, rowNum) -> rs.getString(1));
            int missingPreferenceCount = activeResidents - roommateFeatures.size();
            boolean recommendationEnabled = featureAccessService.has(FeatureCodes.P2_ROOM_RECOMMENDATION);
            MatchingService.MatchResult match = recommendationEnabled
                    ? matchingService.roomScore(batchId, feature, roommateFeatures)
                    : matchingService.roomScore("", List.of());
            Map<String, Object> view = new LinkedHashMap<>(room);
            view.put("selectionMode", mode);
            view.put("allowedRecommendationStrategies", recommendationPolicy.allowedNames());
            view.put("defaultRecommendationStrategy", recommendationPolicy.defaultStrategy().name());
            view.put("activeResidentCount", activeResidents);
            view.put("confirmedBedCount", activeResidents - unknownBedResidents);
            view.put("unconfirmedBedCount", unknownBedResidents);
            view.put("bedMappingComplete", unknownBedResidents == 0);
            view.put("availableCount", available);
            view.put("matchScore", match.score());
            view.put("matches", match.matches());
            view.put("warnings", match.warnings());
            view.put("recommendationReasons", match.recommendationReasons());
            view.put("conflictReasons", match.conflictReasons());
            view.put("dimensionCount", match.dimensionCount());
            view.put("preferenceCompleted", preferenceService.completed(user.studentId()));
            view.put("missingPreferenceCount", Math.max(0, missingPreferenceCount));
            view.put("recommendationEnabled", recommendationEnabled);
            Map<String, Integer> bedTypes = availableBedTypes(batchId, roomId);
            view.put("availableLoftBedCount", bedTypes.getOrDefault("LOFT_BED_DESK", 0));
            view.put("availableBunkUpperCount", bedTypes.getOrDefault("BUNK_UPPER", 0));
            view.put("availableBunkLowerCount", bedTypes.getOrDefault("BUNK_LOWER", 0));
            String bedWarning = bedPreferenceWarning(feature, bedTypes);
            if (bedWarning != null) {
                List<String> warnings = new ArrayList<>(match.conflictReasons());
                warnings.add(bedWarning);
                view.put("conflictReasons", warnings.stream().distinct().limit(7).toList());
            }
            view.put("selectionHint", "ROOM".equals(mode)
                    ? "选择后仅确定寝室，具体床位由寝室成员自行协商"
                    : "进入寝室后选择当前批次范围内的真实可用床位");
            result.add(view);
        }
        result.sort(Comparator
                .comparingDouble((Map<String, Object> room) -> -score(room))
                .thenComparing(this::stableRoomKey));
        return result;
    }

    public Map<String, Object> room(long batchId, long roomId, CurrentUser user) {
        return rooms(batchId, user).stream()
                .filter(room -> ((Number) room.get("id")).longValue() == roomId)
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        "ROOM_NOT_CANDIDATE",
                        "该寝室当前不符合你的选择条件或已经没有剩余名额",
                        HttpStatus.FORBIDDEN));
    }

    /**
     * 旧接口在公开验证阶段仅作为真正随机策略的薄适配层；统一接口稳定后删除。
     */
    public Map<String, Object> randomRecommendation(long batchId, CurrentUser user) {
        return recommend(batchId, RecommendationStrategy.TRUE_RANDOM, user);
    }

    public Map<String, Object> recommend(
            long batchId,
            RecommendationStrategy strategy,
            CurrentUser user) {
        BatchRecommendationPolicyService.Policy recommendationPolicy =
                recommendationPolicyService.forBatch(batchId);
        recommendationPolicy.requireAllowed(strategy);
        featureAccessService.require(strategy.requiredFeatureCode());
        Map<String, Object> batch = policy.batch(batchId);
        List<Map<String, Object>> candidates = rooms(batchId, user);
        if (candidates.isEmpty()) {
            throw new BusinessException(
                    "NO_AVAILABLE_ROOM",
                    "当前没有符合条件的可用寝室",
                    HttpStatus.CONFLICT);
        }

        long seed = secureRandom.nextLong();
        SplittableRandom random = new SplittableRandom(seed);
        List<RecommendationSampler.Candidate<Map<String, Object>>> legalCandidates = candidates.stream()
                .map(room -> new RecommendationSampler.Candidate<>(room, score(room), stableRoomKey(room)))
                .toList();
        RecommendationSampler.Candidate<Map<String, Object>> selected = switch (strategy) {
            case BEST_MATCH -> RecommendationSampler.bestMatch(legalCandidates);
            case TRUE_RANDOM -> RecommendationSampler.trueRandom(legalCandidates, random);
            case MATCH_WEIGHTED_RANDOM -> RecommendationSampler.weightedRandom(
                    legalCandidates,
                    random,
                    recommendationPolicy.baseWeight(),
                    recommendationPolicy.temperature());
        };
        Map<String, Object> selectedRoom = selected.value();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("strategy", strategy.name());
        response.put("strategyName", strategy.displayName());
        response.put("algorithmVersion", ALGORITHM_VERSION);
        response.put("candidateVersion", candidateVersion(candidates));
        response.put("candidateCount", candidates.size());
        response.put("seedDigest", seedDigest(seed));
        response.put("selectionMode", String.valueOf(batch.get("selection_mode")));
        response.put("room", selectedRoom);
        response.put("matchScore", selected.score());

        if (featureAccessService.has(FeatureCodes.P2_RECOMMENDATION_EXPLANATION)) {
            response.put("explanation", explanation(strategy));
        }
        if ("BED".equals(String.valueOf(batch.get("selection_mode")))) {
            response.put("bed", selectBed(batchId, selectedRoom, strategy, random));
        }
        return response;
    }

    private Map<String, Object> selectBed(
            long batchId,
            Map<String, Object> room,
            RecommendationStrategy strategy,
            SplittableRandom random) {
        long roomId = ((Number) room.get("id")).longValue();
        List<Map<String, Object>> beds = jdbc.queryForList("""
                SELECT target_bed.id, target_bed.bed_code,
                       target_bed.bed_type, target_bed.position_index
                FROM bed target_bed
                JOIN room target_room ON target_room.id=target_bed.room_id
                JOIN dormitory_floor target_floor ON target_floor.id=target_room.floor_id
                WHERE target_bed.room_id=:roomId
                  AND target_bed.operational_status='ENABLED'
                  AND (
                      EXISTS (
                          SELECT 1 FROM batch_bed_scope scope
                          WHERE scope.batch_id=:batchId AND scope.bed_id=target_bed.id
                      )
                      OR EXISTS (
                          SELECT 1 FROM batch_room_scope scope
                          WHERE scope.batch_id=:batchId AND scope.room_id=target_room.id
                      )
                      OR EXISTS (
                          SELECT 1 FROM batch_building_scope scope
                          WHERE scope.batch_id=:batchId
                            AND scope.building_id=target_floor.building_id
                      )
                  )
                  AND NOT EXISTS (
                      SELECT 1 FROM room_assignment ra
                      WHERE ra.bed_id=target_bed.id AND ra.assignment_status='ACTIVE'
                  )
                ORDER BY target_bed.position_index, target_bed.id
                """, new MapSqlParameterSource()
                .addValue("roomId", roomId)
                .addValue("batchId", batchId));
        if (beds.isEmpty()) {
            throw new BusinessException(
                    "NO_AVAILABLE_BED",
                    "推荐寝室当前没有属于本批次的真实可用床位",
                    HttpStatus.CONFLICT);
        }
        if (strategy == RecommendationStrategy.TRUE_RANDOM) {
            return beds.get(random.nextInt(beds.size()));
        }
        return beds.getFirst();
    }

    private String explanation(RecommendationStrategy strategy) {
        return switch (strategy) {
            case BEST_MATCH -> "在全部合法候选中选择匹配分最高的寝室，分数相同时按稳定业务顺序选择";
            case TRUE_RANDOM -> "先过滤性别、学生类别、批次范围、容量和床位状态，再对合法寝室等概率随机";
            case MATCH_WEIGHTED_RANDOM -> "全部合法寝室保留非零概率，匹配分越高，抽中概率越高";
        };
    }

    private String candidateVersion(List<Map<String, Object>> candidates) {
        String material = candidates.stream()
                .map(room -> stableRoomKey(room)
                        + ':' + String.valueOf(room.getOrDefault("state_version", 0))
                        + ':' + String.valueOf(room.getOrDefault("availableCount", 0)))
                .sorted()
                .collect(Collectors.joining("|"));
        return digest(material.getBytes(StandardCharsets.UTF_8)).substring(0, 24);
    }

    private String seedDigest(long seed) {
        return digest(ByteBuffer.allocate(Long.BYTES).putLong(seed).array()).substring(0, 24);
    }

    private String digest(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private double score(Map<String, Object> room) {
        Object value = room.get("matchScore");
        return value instanceof Number number ? number.doubleValue() : 0.0d;
    }

    private String stableRoomKey(Map<String, Object> room) {
        return String.valueOf(room.getOrDefault("building_code", room.getOrDefault("building_name", "")))
                + '/' + String.valueOf(room.getOrDefault("floor_number", ""))
                + '/' + String.valueOf(room.getOrDefault("room_number", ""))
                + '/' + String.format("%020d", ((Number) room.get("id")).longValue());
    }

    private void requireAccessibleBatch(long batchId, long studentId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM selection_batch batch
                JOIN batch_student_eligibility eligibility
                  ON eligibility.batch_id=batch.id
                 AND eligibility.student_id=:studentId
                WHERE batch.id=:batchId
                  AND eligibility.eligibility_status='ELIGIBLE'
                  AND batch.batch_status IN ('PUBLISHED','OPEN','PAUSED')
                """, new MapSqlParameterSource()
                .addValue("batchId", batchId)
                .addValue("studentId", studentId), Integer.class);
        if (count == null || count != 1) {
            throw new BusinessException(
                    "BATCH_NOT_ACCESSIBLE",
                    "当前选寝活动不可访问",
                    HttpStatus.FORBIDDEN);
        }
    }

    private String featureJson(long batchId, long studentId) {
        List<String> rows = jdbc.query("""
                SELECT feature_vector_json
                FROM student_feature
                WHERE batch_id=:batchId AND student_id=:studentId
                """, new MapSqlParameterSource()
                .addValue("batchId", batchId)
                .addValue("studentId", studentId),
                (rs, rowNum) -> rs.getString(1));
        return rows.isEmpty() ? preferenceService.featureJson(studentId) : rows.getFirst();
    }

    private Map<String, Integer> availableBedTypes(long batchId, long roomId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT b.bed_type, COUNT(*) AS amount
                FROM bed b
                WHERE b.room_id=:roomId AND b.operational_status='ENABLED'
                  AND NOT EXISTS (SELECT 1 FROM room_assignment ra WHERE ra.bed_id=b.id AND ra.assignment_status='ACTIVE')
                  AND (EXISTS (SELECT 1 FROM batch_bed_scope s WHERE s.batch_id=:batchId AND s.bed_id=b.id)
                    OR EXISTS (SELECT 1 FROM batch_room_scope s WHERE s.batch_id=:batchId AND s.room_id=:roomId)
                    OR EXISTS (SELECT 1 FROM batch_building_scope bs JOIN dormitory_floor f ON f.building_id=bs.building_id
                               JOIN room r ON r.floor_id=f.id WHERE bs.batch_id=:batchId AND r.id=:roomId))
                GROUP BY b.bed_type
                """, Map.of("batchId", batchId, "roomId", roomId));
        Map<String, Integer> result = new LinkedHashMap<>();
        rows.forEach(row -> result.put(
                String.valueOf(row.get("bed_type")),
                ((Number) row.get("amount")).intValue()));
        return result;
    }

    private String bedPreferenceWarning(String featureJson, Map<String, Integer> counts) {
        if (featureJson == null || featureJson.isBlank()) {
            return null;
        }
        String preference;
        try {
            preference = String.valueOf(new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(featureJson)
                    .path("bedPreference")
                    .asText(""));
        } catch (Exception ignored) {
            return null;
        }
        int loft = counts.getOrDefault("LOFT_BED_DESK", 0);
        int bunk = counts.getOrDefault("BUNK_UPPER", 0) + counts.getOrDefault("BUNK_LOWER", 0);
        if ("LOFT_BED_DESK".equals(preference) && loft == 0 && bunk > 0) {
            return "仅剩上下铺";
        }
        if (("BUNK_UPPER".equals(preference) || "BUNK_LOWER".equals(preference))
                && bunk == 0 && loft > 0) {
            return "仅剩上床下桌";
        }
        return null;
    }
}
