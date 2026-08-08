package com.wust.dormitory.roomchange.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface RoomChangeMapper {
    List<Map<String, Object>> findCandidateRooms(
            @Param("currentRoomId") long currentRoomId,
            @Param("gender") String gender,
            @Param("category") String category);

    List<Map<String, Object>> findStudentRequests(@Param("studentId") long studentId);

    List<Map<String, Object>> findAdminRequests(
            @Param("status") String status,
            @Param("keyword") String keyword);

    String findPolicyMode();

    Map<String, Object> findRequest(@Param("requestId") long requestId);

    Map<String, Object> lockRequest(@Param("requestId") long requestId);

    Map<String, Object> findActiveResidency(@Param("studentId") long studentId);

    Map<String, Object> lockActiveResidency(@Param("studentId") long studentId);

    int countActiveRequests(@Param("studentId") long studentId);

    List<Long> lockActiveRequestIds(@Param("studentId") long studentId);

    int insertRequest(Map<String, Object> request);

    int approveRequest(
            @Param("requestId") long requestId,
            @Param("reviewedBy") long reviewedBy,
            @Param("reason") String reason);

    int rejectRequest(
            @Param("requestId") long requestId,
            @Param("reviewedBy") long reviewedBy,
            @Param("reason") String reason);

    int cancelRequest(
            @Param("requestId") long requestId,
            @Param("reason") String reason);

    int cancelActiveRequests(
            @Param("studentId") long studentId,
            @Param("reason") String reason,
            @Param("reviewedBy") long reviewedBy);

    int markExecuted(
            @Param("requestId") long requestId,
            @Param("residencyId") long residencyId);

    int markFailed(
            @Param("requestId") long requestId,
            @Param("reason") String reason);

    int upsertPolicy(
            @Param("mode") String mode,
            @Param("updatedBy") long updatedBy);

    int insertStudentNotification(
            @Param("studentId") long studentId,
            @Param("type") String type,
            @Param("requestId") long requestId);
}
