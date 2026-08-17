package com.wust.dormitory.readiness;

import com.wust.dormitory.readiness.mapper.SystemReadinessMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class ResourceReadinessChecker implements ReadinessChecker {
    private final SystemReadinessMapper mapper;

    public ResourceReadinessChecker(SystemReadinessMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String category() {
        return "RESOURCE";
    }

    @Override
    public boolean critical() {
        return true;
    }

    @Override
    public List<ReadinessCheckResult> check(ReadinessContext context) {
        Map<String, Object> data = mapper.resourceSummary();
        long campuses = number(data, "campuses");
        long buildings = number(data, "buildings");
        long rooms = number(data, "rooms");
        long beds = number(data, "validBeds");
        long occupied = number(data, "occupiedBeds");
        long remaining = Math.max(0, beds - occupied);
        long noBeds = number(data, "roomsWithoutBeds");
        long zeroEnabled = number(data, "enabledRoomsWithoutBeds");
        long mismatch = number(data, "capacityMismatchRooms");
        long invalidRelations = number(data, "invalidRelations");
        List<ReadinessCheckResult> results = new ArrayList<>();
        boolean empty = campuses == 0 || buildings == 0 || rooms == 0 || beds == 0;
        results.add(ReadinessCheckResult.of("RESOURCE_BASELINE", category(), "宿舍资源基础数据",
                empty ? ReadinessSeverity.ERROR : ReadinessSeverity.PASS, empty,
                empty ? "FAILED" : "PASSED",
                empty ? "校区、楼栋、房间或有效床位存在空数据，暂不适合开放。" : "宿舍资源基础数据已就绪。",
                Map.of("campuses", campuses, "buildings", buildings, "rooms", rooms, "validBeds", beds),
                empty ? "补全校区、楼栋、房间和床位数据" : null, "/admin/dormitories", context.checkedAt()));
        long integrityIssues = noBeds + zeroEnabled + mismatch + invalidRelations;
        results.add(ReadinessCheckResult.of("RESOURCE_INTEGRITY", category(), "宿舍资源完整性",
                integrityIssues == 0 ? ReadinessSeverity.PASS : ReadinessSeverity.ERROR,
                integrityIssues > 0,
                integrityIssues == 0 ? "PASSED" : "FAILED",
                integrityIssues == 0 ? "未发现房间容量、床位和层级关联异常。" : "发现 " + integrityIssues + " 项宿舍资源完整性异常。",
                Map.of("roomsWithoutBeds", noBeds, "enabledRoomsWithoutBeds", zeroEnabled,
                        "capacityMismatchRooms", mismatch, "invalidRelations", invalidRelations),
                integrityIssues == 0 ? null : "前往宿舍资源管理修正异常房间", "/admin/dormitories", context.checkedAt()));
        results.add(ReadinessCheckResult.of("BED_CAPACITY", category(), "当前床位容量", ReadinessSeverity.INFO,
                false, "INFO", "当前有效床位 " + beds + " 个，正式占用 " + occupied + " 个，剩余 " + remaining + " 个。",
                Map.of("availableBeds", beds, "occupiedBeds", occupied, "remainingBeds", remaining),
                null, "/admin/residencies", context.checkedAt()));
        return results;
    }

    private long number(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value instanceof Number number ? number.longValue() : 0L;
    }
}
