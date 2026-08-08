package com.wust.dormitory.roomchange;

import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.residency.ResidencyPolicyService;
import com.wust.dormitory.roomchange.mapper.RoomChangeMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class RoomChangeWorkflowSupport {
    private final RoomChangeMapper mapper;
    private final ResidencyPolicyService policy;

    public RoomChangeWorkflowSupport(RoomChangeMapper mapper, ResidencyPolicyService policy) {
        this.mapper = mapper;
        this.policy = policy;
    }

    public Map<String, Object> activeResidency(long studentId, boolean lock) {
        Map<String, Object> residency = lock
                ? mapper.lockActiveResidency(studentId)
                : mapper.findActiveResidency(studentId);
        if (residency == null) {
            throw new BusinessException(
                    "ROOM_CHANGE_RESIDENCY_REQUIRED",
                    "只有当前已入住学生可以申请换寝",
                    HttpStatus.CONFLICT);
        }
        return residency;
    }

    public void requireNoActiveRequest(long studentId) {
        if (mapper.countActiveRequests(studentId) > 0) {
            throw new BusinessException(
                    "ROOM_CHANGE_REQUEST_ACTIVE",
                    "你已经有一条待处理换寝申请",
                    HttpStatus.CONFLICT);
        }
    }

    public void validateTarget(long studentId, long roomId, Long bedId, long sourceRoomId) {
        if (roomId == sourceRoomId) {
            throw new BusinessException("ROOM_CHANGE_SAME_ROOM", "目标寝室不能与当前寝室相同");
        }
        Map<String, Object> student = policy.student(studentId);
        Map<String, Object> room = policy.room(roomId, true);
        Map<String, Object> batch = Map.of("separate_student_categories", 0, "selection_mode", "DIRECT");
        policy.requireStudentEligibleForRoom(student, batch, room);
        policy.requireRoomCapacity(roomId, 1);
        if (bedId != null) policy.requireAvailableBed(roomId, bedId);
    }

    public Map<String, Object> request(long requestId) {
        Map<String, Object> request = mapper.findRequest(requestId);
        if (request == null) {
            throw new BusinessException("ROOM_CHANGE_REQUEST_NOT_FOUND", "换寝申请不存在", HttpStatus.NOT_FOUND);
        }
        return request;
    }

    public Map<String, Object> requestForUpdate(long requestId) {
        Map<String, Object> request = mapper.lockRequest(requestId);
        if (request == null) {
            throw new BusinessException("ROOM_CHANGE_REQUEST_NOT_FOUND", "换寝申请不存在", HttpStatus.NOT_FOUND);
        }
        return request;
    }

    public void notifyStudent(long studentId, String type, long requestId) {
        mapper.insertStudentNotification(studentId, type, requestId);
    }
}
