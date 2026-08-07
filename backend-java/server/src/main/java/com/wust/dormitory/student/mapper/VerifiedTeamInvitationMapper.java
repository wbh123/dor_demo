package com.wust.dormitory.student.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

@Mapper
public interface VerifiedTeamInvitationMapper {
    Map<String, Object> findEligibleInvitee(
            @Param("batchId") long batchId,
            @Param("studentNumber") String studentNumber,
            @Param("studentName") String studentName);

    Map<String, Object> findLeaderTeamForUpdate(
            @Param("batchId") long batchId,
            @Param("studentId") long studentId);

    Map<String, Object> findInvitationGuards(
            @Param("teamId") long teamId,
            @Param("batchId") long batchId,
            @Param("inviteeStudentId") long inviteeStudentId);

    int upsertInvitedMember(
            @Param("teamId") long teamId,
            @Param("batchId") long batchId,
            @Param("studentId") long studentId);

    int insertInvitation(
            @Param("teamId") long teamId,
            @Param("inviterId") long inviterId,
            @Param("inviteeId") long inviteeId,
            @Param("token") String token);

    int hasPendingInvitation(
            @Param("teamId") long teamId,
            @Param("studentId") long studentId);

    Map<String, Object> findPendingInvitationForUpdate(
            @Param("teamId") long teamId,
            @Param("inviteeStudentId") long inviteeStudentId);

    int cancelInvitation(@Param("invitationId") long invitationId);

    int removeInvitedMember(
            @Param("teamId") long teamId,
            @Param("studentId") long studentId);

    int insertCancellationNotification(
            @Param("studentId") long studentId,
            @Param("parameters") String parameters);
}
