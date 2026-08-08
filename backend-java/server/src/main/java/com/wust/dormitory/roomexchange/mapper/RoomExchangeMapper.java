package com.wust.dormitory.roomexchange.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface RoomExchangeMapper {
    List<Map<String, Object>> findCompatibleCandidates(
            @Param("studentId") long studentId,
            @Param("sourceGender") String sourceGender,
            @Param("sourceCategory") String sourceCategory,
            @Param("sourceRoomGender") String sourceRoomGender,
            @Param("sourceRoomScope") String sourceRoomScope,
            @Param("sourceRoomStatus") String sourceRoomStatus,
            @Param("studentNumberPattern") String studentNumberPattern);

    List<Map<String, Object>> findStudentRequests(@Param("studentId") long studentId);

    List<Map<String, Object>> findAdminRequests(
            @Param("status") String status,
            @Param("keyword") String keyword);

    Map<String, Object> findRequestView(@Param("exchangeId") long exchangeId);

    String findPolicyMode();

    Map<String, Object> lockRequest(@Param("exchangeId") long exchangeId);

    List<Map<String, Object>> lockActiveResidencies(
            @Param("firstStudentId") long firstStudentId,
            @Param("secondStudentId") long secondStudentId);

    Map<String, Object> findActiveResidency(@Param("studentId") long studentId);

    Map<String, Object> lockActiveResidency(@Param("studentId") long studentId);

    int insertRequest(Map<String, Object> request);

    int insertParticipantLock(
            @Param("exchangeId") long exchangeId,
            @Param("studentId") long studentId,
            @Param("role") String role);

    int deleteParticipantLocks(@Param("exchangeId") long exchangeId);

    int rejectByTarget(@Param("exchangeId") long exchangeId, @Param("reason") String reason);

    int acceptByTarget(
            @Param("exchangeId") long exchangeId,
            @Param("status") String status,
            @Param("reason") String reason);

    int approveRequest(
            @Param("exchangeId") long exchangeId,
            @Param("reviewedBy") long reviewedBy,
            @Param("reason") String reason);

    int rejectByAdmin(
            @Param("exchangeId") long exchangeId,
            @Param("reviewedBy") long reviewedBy,
            @Param("reason") String reason);

    int cancelRequest(@Param("exchangeId") long exchangeId, @Param("reason") String reason);

    int markExecuted(
            @Param("exchangeId") long exchangeId,
            @Param("initiatorResidencyId") long initiatorResidencyId,
            @Param("targetResidencyId") long targetResidencyId);

    int upsertPolicy(@Param("mode") String mode, @Param("updatedBy") long updatedBy);
}
