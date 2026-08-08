package com.wust.dormitory.roomexchange;

import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.residency.ResidencyPolicyService;
import com.wust.dormitory.roomexchange.mapper.RoomExchangeMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class RoomExchangeWorkflowSupport {
    private final RoomExchangeMapper mapper;
    private final ResidencyPolicyService residencyPolicy;

    public RoomExchangeWorkflowSupport(
            RoomExchangeMapper mapper,
            ResidencyPolicyService residencyPolicy) {
        this.mapper = mapper;
        this.residencyPolicy = residencyPolicy;
    }

    public List<Map<String, Object>> candidates(long studentId, String studentNumberPattern) {
        Map<String, Object> source = activeResidency(studentId, false);
        Map<String, Object> sourceStudent = residencyPolicy.student(studentId);
        Map<String, Object> sourceRoom = residencyPolicy.room(number(source.get("room_id")), false);
        return mapper.findCompatibleCandidates(
                studentId,
                String.valueOf(sourceStudent.get("gender")),
                String.valueOf(sourceStudent.get("student_category")),
                String.valueOf(sourceRoom.get("gender_restriction")),
                String.valueOf(sourceRoom.get("resident_scope")),
                String.valueOf(sourceRoom.get("operational_status")),
                studentNumberPattern);
    }

    public List<Map<String, Object>> lockActiveResidencies(long firstStudentId, long secondStudentId) {
        List<Map<String, Object>> rows = mapper.lockActiveResidencies(firstStudentId, secondStudentId);
        if (rows.size() != 2) {
            throw new BusinessException(
                    "ROOM_EXCHANGE_RESIDENCY_REQUIRED",
                    "双方都必须只有一条有效在住记录",
                    HttpStatus.CONFLICT);
        }
        return rows;
    }

    public Map<String, Object> activeResidency(long studentId, boolean forUpdate) {
        List<Map<String, Object>> rows = forUpdate
                ? mapper.lockActiveResidency(studentId)
                : mapper.findActiveResidency(studentId);
        if (rows.size() != 1) {
            throw new BusinessException(
                    "ROOM_EXCHANGE_RESIDENCY_REQUIRED",
                    "只有当前已入住且住宿记录正常的学生可以交换",
                    HttpStatus.CONFLICT);
        }
        return rows.getFirst();
    }

    public Map<String, Object> residencyOf(List<Map<String, Object>> rows, long studentId) {
        return rows.stream()
                .filter(row -> number(row.get("student_id")) == studentId)
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        "ROOM_EXCHANGE_RESIDENCY_REQUIRED",
                        "没有找到有效住宿记录",
                        HttpStatus.CONFLICT));
    }

    public void requireCompatible(
            long initiatorStudentId,
            Map<String, Object> initiatorResidency,
            long targetStudentId,
            Map<String, Object> targetResidency) {
        Map<String, Object> initiatorStudent = residencyPolicy.student(initiatorStudentId);
        Map<String, Object> targetStudent = residencyPolicy.student(targetStudentId);
        Map<String, Object> initiatorRoom = residencyPolicy.room(
                number(initiatorResidency.get("room_id")), true);
        Map<String, Object> targetRoom = residencyPolicy.room(
                number(targetResidency.get("room_id")), true);
        if (!compatible(initiatorStudent, initiatorRoom, targetStudent, targetRoom)) {
            throw new BusinessException(
                    "ROOM_EXCHANGE_POLICY_MISMATCH",
                    "双方不符合对方寝室的性别或学生类别要求",
                    HttpStatus.CONFLICT);
        }
    }

    public Map<String, Object> request(long exchangeId) {
        Map<String, Object> request = mapper.findRequestView(exchangeId);
        if (request == null) {
            throw new BusinessException(
                    "ROOM_EXCHANGE_NOT_FOUND",
                    "寝室交换申请不存在",
                    HttpStatus.NOT_FOUND);
        }
        return request;
    }

    public Map<String, Object> requestForUpdate(long exchangeId) {
        Map<String, Object> request = mapper.lockRequest(exchangeId);
        if (request == null) {
            throw new BusinessException(
                    "ROOM_EXCHANGE_NOT_FOUND",
                    "寝室交换申请不存在",
                    HttpStatus.NOT_FOUND);
        }
        return request;
    }

    public void lockParticipant(long exchangeId, long studentId, String role) {
        mapper.insertParticipantLock(exchangeId, studentId, role);
    }

    public void releaseParticipants(long exchangeId) {
        mapper.deleteParticipantLocks(exchangeId);
    }

    private boolean compatible(
            Map<String, Object> initiatorStudent,
            Map<String, Object> initiatorRoom,
            Map<String, Object> targetStudent,
            Map<String, Object> targetRoom) {
        return "ENABLED".equals(String.valueOf(initiatorRoom.get("operational_status")))
                && "ENABLED".equals(String.valueOf(targetRoom.get("operational_status")))
                && String.valueOf(initiatorStudent.get("gender"))
                    .equals(String.valueOf(targetRoom.get("gender_restriction")))
                && String.valueOf(targetStudent.get("gender"))
                    .equals(String.valueOf(initiatorRoom.get("gender_restriction")))
                && residencyPolicy.roomAllowsCategory(
                    String.valueOf(targetRoom.get("resident_scope")),
                    String.valueOf(initiatorStudent.get("student_category")), false)
                && residencyPolicy.roomAllowsCategory(
                    String.valueOf(initiatorRoom.get("resident_scope")),
                    String.valueOf(targetStudent.get("student_category")), false);
    }

    private long number(Object value) {
        return ((Number) value).longValue();
    }
}
