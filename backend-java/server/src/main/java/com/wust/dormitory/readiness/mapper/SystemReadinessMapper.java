package com.wust.dormitory.readiness.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface SystemReadinessMapper {
    int databaseProbe();

    String databaseVersion();

    Map<String, Object> resourceSummary();

    List<Long> resourceRoomIds();

    Map<String, Object> studentSummary();

    List<Map<String, Object>> studentIssueSamples(@Param("limit") int limit);

    List<Map<String, Object>> activeBatches();

    long participantCount(@Param("batchId") long batchId);

    long pendingParticipantCount(@Param("batchId") long batchId);
}
