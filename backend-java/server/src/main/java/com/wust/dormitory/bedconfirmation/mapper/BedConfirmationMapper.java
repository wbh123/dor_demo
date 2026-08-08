package com.wust.dormitory.bedconfirmation.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface BedConfirmationMapper {
    Map<String, Object> findCurrentResidency(@Param("studentId") long studentId);

    Map<String, Object> lockCurrentResidency(@Param("studentId") long studentId);

    Map<String, Object> findRoomInfo(@Param("roomId") long roomId);

    Map<String, Object> lockRoomInfo(@Param("roomId") long roomId);

    List<Map<String, Object>> findRoomBeds(@Param("roomId") long roomId);

    Map<String, Object> lockBed(@Param("roomId") long roomId, @Param("bedId") long bedId);

    Map<String, Object> findPendingForResidency(@Param("residencyId") long residencyId);

    Map<String, Object> lockRequest(@Param("requestId") long requestId);

    Map<String, Object> findRequest(@Param("requestId") long requestId);

    List<Map<String, Object>> findRooms(@Param("keyword") String keyword);

    List<Map<String, Object>> findRoomStudents(@Param("roomId") long roomId);

    List<Long> lockPendingRequests(@Param("roomId") long roomId);

    List<Long> lockActiveAssignments(@Param("roomId") long roomId);

    List<Long> lockRoomBeds(@Param("roomId") long roomId);

    List<Map<String, Object>> findRoomApprovalCandidates(@Param("roomId") long roomId);

    void insertRequest(Map<String, Object> request);

    int cancelRequest(@Param("requestId") long requestId, @Param("reason") String reason);

    int assignBed(@Param("residencyId") long residencyId, @Param("bedId") long bedId);

    int approveRequest(
            @Param("requestId") long requestId,
            @Param("reviewedBy") long reviewedBy,
            @Param("reason") String reason);

    int rejectRequest(
            @Param("requestId") long requestId,
            @Param("reviewedBy") long reviewedBy,
            @Param("reason") String reason);

    void insertStudentNotification(Map<String, Object> notification);
}
