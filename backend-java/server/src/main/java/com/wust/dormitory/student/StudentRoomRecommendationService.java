package com.wust.dormitory.student;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.matching.BatchRecommendationPolicyService;
import com.wust.dormitory.matching.MatchingSchemeService;
import com.wust.dormitory.matching.MatchingService;
import com.wust.dormitory.matching.RecommendationSampler;
import com.wust.dormitory.matching.RecommendationStrategy;
import com.wust.dormitory.residency.ResidencyPolicyService;
import com.wust.dormitory.security.CurrentUser;
import com.wust.dormitory.student.mapper.StudentRoomRecommendationMapper;
import com.wust.dormitory.student.model.persistence.AvailableBedRow;
import com.wust.dormitory.student.model.persistence.AvailableBedTypeRow;
import com.wust.dormitory.student.model.persistence.RoomRecommendationCandidateRow;
import com.wust.dormitory.student.model.persistence.RoommateFeatureRow;
import com.wust.dormitory.subscription.FeatureAccessService;
import com.wust.dormitory.subscription.FeatureCodes;
import org.springframework.http.HttpStatus;
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

    private final StudentRoomRecommendationMapper mapper;
    private final MatchingService matchingService;
    private final ResidencyPolicyService policy;
    private final StudentPreferenceService preferenceService;
    private final FeatureAccessService featureAccessService;
    private final BatchRecommendationPolicyService recommendationPolicyService;
    private final SecureRandom secureRandom;

    public StudentRoomRecommendationService(
            StudentRoomRecommendationMapper mapper,
            MatchingService matchingService,
            ResidencyPolicyService policy,
            StudentPreferenceService preferenceService,
            FeatureAccessService featureAccessService,
            BatchRecommendationPolicyService recommendationPolicyService) {
        this(mapper, matchingService, policy, preferenceService, featureAccessService,
                recommendationPolicyService, new SecureRandom());
    }

    StudentRoomRecommendationService(
            StudentRoomRecommendationMapper mapper,
            MatchingService matchingService,
            ResidencyPolicyService policy,
            StudentPreferenceService preferenceService,
            FeatureAccessService featureAccessService,
            BatchRecommendationPolicyService recommendationPolicyService,
            SecureRandom secureRandom) {
        this.mapper = mapper;
        this.matchingService = matchingService;
        this.policy = policy;
        this.preferenceService = preferenceService;
        this.featureAccessService = featureAccessService;
        this.recommendationPolicyService = recommendationPolicyService;
        this.secureRandom = secureRandom;
    }

    public List<Map<String, Object>> rooms(long batchId, CurrentUser user) {
        return candidateRooms(batchId, null, user, null, null);
    }

    public Map<String, Object> room(long batchId, long roomId, CurrentUser user) {
        return candidateRooms(batchId, roomId, user, null, null).stream()
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

    public Map<String, Object> recommend(long batchId, RecommendationStrategy strategy, CurrentUser user) {
        BatchRecommendationPolicyService.Policy recommendationPolicy = recommendationPolicyService.forBatch(batchId);
        recommendationPolicy.requireAllowed(strategy);
        featureAccessService.require(strategy.requiredFeatureCode());
        Map<String, Object> batch = policy.batch(batchId);
        List<Map<String, Object>> candidates = candidateRooms(
                batchId, null, user, batch, recommendationPolicy);
        if (candidates.isEmpty()) {
            throw new BusinessException("NO_AVAILABLE_ROOM", "当前没有符合条件的可用寝室", HttpStatus.CONFLICT);
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
                    legalCandidates, random, recommendationPolicy.baseWeight(), recommendationPolicy.temperature());
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

    private List<Map<String, Object>> candidateRooms(
            long batchId,
            Long requestedRoomId,
            CurrentUser user,
            Map<String, Object> preloadedBatch,
            BatchRecommendationPolicyService.Policy preloadedRecommendationPolicy) {
        requireAccessibleBatch(batchId, user.studentId());
        Map<String, Object> batch = preloadedBatch == null ? policy.batch(batchId) : preloadedBatch;
        Map<String, Object> student = policy.student(user.studentId());
        BatchRecommendationPolicyService.Policy recommendationPolicy = preloadedRecommendationPolicy == null
                ? recommendationPolicyService.forBatch(batchId)
                : preloadedRecommendationPolicy;
        String feature = featureJson(batchId, user.studentId());
        String mode = String.valueOf(batch.get("selection_mode"));

        List<RoomRecommendationCandidateRow> eligible = mapper.findCandidateRooms(batchId, requestedRoomId).stream()
                .filter(row -> isEligible(student, batch, row))
                .filter(row -> availableCount(mode, row) > 0)
                .toList();
        if (eligible.isEmpty()) return List.of();

        List<Long> roomIds = eligible.stream().map(RoomRecommendationCandidateRow::id).toList();
        Map<Long, List<String>> roommateFeatures = roommateFeatures(batchId, roomIds);
        Map<Long, Map<String, Integer>> bedTypes = availableBedTypes(batchId, roomIds);
        boolean recommendationEnabled = featureAccessService.has(FeatureCodes.P2_ROOM_RECOMMENDATION);
        MatchingSchemeService.Policy matchingPolicy = recommendationEnabled
                ? matchingService.policyForBatch(batchId)
                : null;
        boolean preferenceCompleted = preferenceService.completed(user.studentId());
        String bedPreference = bedPreference(feature);

        List<Map<String, Object>> result = new ArrayList<>(eligible.size());
        for (RoomRecommendationCandidateRow row : eligible) {
            List<String> features = roommateFeatures.getOrDefault(row.id(), List.of());
            MatchingService.MatchResult match = recommendationEnabled
                    ? matchingService.roomScore(matchingPolicy, feature, features)
                    : matchingService.roomScore("", List.of());
            Map<String, Integer> counts = bedTypes.getOrDefault(row.id(), Map.of());
            Map<String, Object> view = new LinkedHashMap<>(row.toRoomMap());
            int activeResidents = row.activeResidents();
            int unknownBedResidents = row.unknownBedResidents();
            view.put("selectionMode", mode);
            view.put("allowedRecommendationStrategies", recommendationPolicy.allowedNames());
            view.put("defaultRecommendationStrategy", recommendationPolicy.defaultStrategy().name());
            view.put("activeResidentCount", activeResidents);
            view.put("confirmedBedCount", activeResidents - unknownBedResidents);
            view.put("unconfirmedBedCount", unknownBedResidents);
            view.put("bedMappingComplete", unknownBedResidents == 0);
            view.put("availableCount", availableCount(mode, row));
            view.put("matchScore", match.score());
            view.put("matches", match.matches());
            view.put("warnings", match.warnings());
            view.put("recommendationReasons", match.recommendationReasons());
            view.put("conflictReasons", match.conflictReasons());
            view.put("dimensionCount", match.dimensionCount());
            view.put("preferenceCompleted", preferenceCompleted);
            view.put("missingPreferenceCount", Math.max(0, activeResidents - features.size()));
            view.put("recommendationEnabled", recommendationEnabled);
            view.put("availableLoftBedCount", counts.getOrDefault("LOFT_BED_DESK", 0));
            view.put("availableBunkUpperCount", counts.getOrDefault("BUNK_UPPER", 0));
            view.put("availableBunkLowerCount", counts.getOrDefault("BUNK_LOWER", 0));
            addBedWarning(view, match, bedPreference, counts);
            view.put("selectionHint", "ROOM".equals(mode)
                    ? "选择后仅确定寝室，具体床位由寝室成员自行协商"
                    : "进入寝室后选择当前批次范围内的真实可用床位");
            result.add(view);
        }
        result.sort(Comparator.comparingDouble((Map<String, Object> room) -> -score(room))
                .thenComparing(this::stableRoomKey));
        return result;
    }

    private boolean isEligible(
            Map<String, Object> student,
            Map<String, Object> batch,
            RoomRecommendationCandidateRow row) {
        try {
            policy.requireStudentEligibleForRoom(student, batch, row.toRoomMap());
            return true;
        } catch (BusinessException ignored) {
            return false;
        }
    }

    private int availableCount(String mode, RoomRecommendationCandidateRow row) {
        if ("BED".equals(mode)) {
            return row.unknownBedResidents() == 0 ? row.availableBeds() : 0;
        }
        return Math.max(0, (row.capacity() == null ? 0 : row.capacity()) - row.activeResidents());
    }

    private Map<Long, List<String>> roommateFeatures(long batchId, List<Long> roomIds) {
        Map<Long, List<String>> grouped = new LinkedHashMap<>();
        for (RoommateFeatureRow row : mapper.findRoommateFeatures(batchId, roomIds)) {
            grouped.computeIfAbsent(row.roomId(), ignored -> new ArrayList<>()).add(row.featureVectorJson());
        }
        return grouped;
    }

    private Map<Long, Map<String, Integer>> availableBedTypes(long batchId, List<Long> roomIds) {
        Map<Long, Map<String, Integer>> grouped = new LinkedHashMap<>();
        for (AvailableBedTypeRow row : mapper.findAvailableBedTypes(batchId, roomIds)) {
            grouped.computeIfAbsent(row.roomId(), ignored -> new LinkedHashMap<>()).put(row.bedType(), row.count());
        }
        return grouped;
    }

    private Map<String, Object> selectBed(
            long batchId,
            Map<String, Object> room,
            RecommendationStrategy strategy,
            SplittableRandom random) {
        long roomId = ((Number) room.get("id")).longValue();
        List<AvailableBedRow> beds = mapper.findAvailableBeds(batchId, roomId);
        if (beds.isEmpty()) {
            throw new BusinessException(
                    "NO_AVAILABLE_BED",
                    "推荐寝室当前没有属于本批次的真实可用床位",
                    HttpStatus.CONFLICT);
        }
        AvailableBedRow selected = strategy == RecommendationStrategy.TRUE_RANDOM
                ? beds.get(random.nextInt(beds.size()))
                : beds.getFirst();
        return selected.toMap();
    }

    private void requireAccessibleBatch(long batchId, long studentId) {
        if (!mapper.isBatchAccessible(batchId, studentId)) {
            throw new BusinessException("BATCH_NOT_ACCESSIBLE", "当前选寝活动不可访问", HttpStatus.FORBIDDEN);
        }
    }

    private String featureJson(long batchId, long studentId) {
        String feature = mapper.findBatchFeature(batchId, studentId);
        return feature == null || feature.isBlank() ? preferenceService.featureJson(studentId) : feature;
    }

    private String bedPreference(String featureJson) {
        if (featureJson == null || featureJson.isBlank()) return "";
        try {
            return new ObjectMapper().readTree(featureJson).path("bedPreference").asText("");
        } catch (Exception ignored) {
            return "";
        }
    }

    private void addBedWarning(
            Map<String, Object> view,
            MatchingService.MatchResult match,
            String preference,
            Map<String, Integer> counts) {
        int loft = counts.getOrDefault("LOFT_BED_DESK", 0);
        int bunk = counts.getOrDefault("BUNK_UPPER", 0) + counts.getOrDefault("BUNK_LOWER", 0);
        String warning = null;
        if ("LOFT_BED_DESK".equals(preference) && loft == 0 && bunk > 0) {
            warning = "仅剩上下铺";
        } else if (("BUNK_UPPER".equals(preference) || "BUNK_LOWER".equals(preference))
                && bunk == 0 && loft > 0) {
            warning = "仅剩上床下桌";
        }
        if (warning != null) {
            List<String> warnings = new ArrayList<>(match.conflictReasons());
            warnings.add(warning);
            view.put("conflictReasons", warnings.stream().distinct().limit(7).toList());
        }
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
                .map(room -> stableRoomKey(room) + ':' + room.getOrDefault("state_version", 0)
                        + ':' + room.getOrDefault("availableCount", 0))
                .sorted().collect(Collectors.joining("|"));
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
                + '/' + room.getOrDefault("floor_number", "")
                + '/' + room.getOrDefault("room_number", "")
                + '/' + String.format("%020d", ((Number) room.get("id")).longValue());
    }
}
