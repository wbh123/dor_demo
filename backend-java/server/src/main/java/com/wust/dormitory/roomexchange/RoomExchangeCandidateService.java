package com.wust.dormitory.roomexchange;

import com.wust.dormitory.common.error.BusinessException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class RoomExchangeCandidateService {
    private final RoomExchangeCandidateMapper mapper;

    public RoomExchangeCandidateService(RoomExchangeCandidateMapper mapper) {
        this.mapper = mapper;
    }

    public Map<String, Object> exactCandidate(
            long requesterStudentId,
            String studentNumber,
            String studentName,
            Long buildingId,
            String roomNumber) {
        String number = studentNumber == null ? "" : studentNumber.trim();
        String name = studentName == null ? "" : studentName.trim();
        String room = roomNumber == null ? "" : roomNumber.trim();
        if (!number.matches("\\d{12}") || name.isBlank() || buildingId == null || buildingId <= 0 || room.isBlank()) {
            throw new BusinessException(
                    "EXCHANGE_EXACT_QUERY_REQUIRED",
                    "请完整填写学号、姓名、楼栋和寝室号后再查询");
        }
        Map<String, Object> candidate = mapper.findExactCandidate(
                requesterStudentId,
                number,
                name,
                buildingId,
                room);
        if (candidate == null || candidate.isEmpty()) {
            throw new BusinessException(
                    "EXCHANGE_CANDIDATE_NOT_FOUND",
                    "未找到完全匹配且当前可交换的学生，请核对双方线下确认的信息");
        }
        return candidate;
    }

    public List<Map<String, Object>> buildings(long requesterStudentId) {
        return mapper.listCandidateBuildings(requesterStudentId);
    }
}
